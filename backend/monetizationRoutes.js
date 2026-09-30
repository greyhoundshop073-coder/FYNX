import pg from "pg";
import jwt from "jsonwebtoken";
import crypto from "node:crypto";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: 4,
  min: 0,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 5000,
  statement_timeout: 10000,
  query_timeout: 12000,
  keepAlive: true
}) : null;

let schemaPromise;
async function ensureSchema() {
  if (!pool) return;
  if (!schemaPromise) schemaPromise = pool.query(`
    CREATE TABLE IF NOT EXISTS fynx_business_entitlements (
      user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
      plan TEXT NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE','BUSINESS','CREATOR')),
      active BOOLEAN NOT NULL DEFAULT TRUE,
      expires_at TIMESTAMPTZ,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS fynx_business_entitlements_plan_idx ON fynx_business_entitlements(plan,active,expires_at);
    CREATE OR REPLACE FUNCTION fynx_sync_revenue_from_marketplace_ledger() RETURNS trigger AS $fn$
    BEGIN
      IF NEW.account='fynx_marketplace_fee' AND NEW.entry_type='FEE' THEN
        INSERT INTO fynx_revenue_transactions(id,source,source_key,order_id,amount,currency,status,metadata)
        VALUES(gen_random_uuid(),'MARKETPLACE_FEE',NEW.idempotency_key,NEW.order_id,NEW.amount,NEW.currency,'SETTLED',jsonb_build_object('ledgerId',NEW.id,'account',NEW.account,'entryType',NEW.entry_type))
        ON CONFLICT(source_key) DO NOTHING;
      ELSIF NEW.account='buyer_refund' AND NEW.entry_type='REFUND' THEN
        UPDATE fynx_revenue_transactions
        SET status='REFUNDED',updated_at=NOW(),metadata=metadata || jsonb_build_object('refundLedgerId',NEW.id)
        WHERE order_id=NEW.order_id AND source='MARKETPLACE_FEE' AND status='SETTLED';
      END IF;
      RETURN NEW;
    END;
    $fn$ LANGUAGE plpgsql;
    DO $$
    BEGIN
      IF to_regclass('public.marketplace_ledger_entries') IS NOT NULL AND to_regclass('public.fynx_revenue_transactions') IS NOT NULL THEN
        DROP TRIGGER IF EXISTS marketplace_revenue_ledger_sync ON marketplace_ledger_entries;
        CREATE TRIGGER marketplace_revenue_ledger_sync AFTER INSERT ON marketplace_ledger_entries FOR EACH ROW EXECUTE FUNCTION fynx_sync_revenue_from_marketplace_ledger();
      END IF;
    END $$;
  `).catch(error => { schemaPromise = undefined; throw error; });
  return schemaPromise;
}

function safeEqualHex(actual, expected) {
  if (!actual || !expected || !/^[0-9a-f]{128}$/i.test(actual) || !/^[0-9a-f]{128}$/i.test(expected)) return false;
  return crypto.timingSafeEqual(Buffer.from(actual, 'hex'), Buffer.from(expected, 'hex'));
}

function authenticate(req, res) {
  if (!JWT_SECRET) { res.status(503).json({ error: "service unavailable" }); return null; }
  const header = req.get("authorization") || "";
  if (!header.startsWith("Bearer ")) { res.status(401).json({ error: "authentication required" }); return null; }
  try {
    const payload = jwt.verify(header.slice(7).trim(), JWT_SECRET);
    if (!payload?.sub) throw new Error("invalid token");
    return Number(payload.sub);
  } catch { res.status(401).json({ error: "invalid or expired token" }); return null; }
}

async function adminRole(userId) {
  const first = (await pool.query("SELECT id FROM users ORDER BY id ASC LIMIT 1")).rows[0];
  if (first && String(first.id) === String(userId)) return "OWNER";
  const result = await pool.query("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1", [userId]);
  return result.rowCount ? "ADMIN" : null;
}

export function registerMonetizationRoutes({ app }) {
  if (!app || !pool) return;
  void ensureSchema().catch(error => console.error("[fynx-monetization] schema initialization failed", error));
  app.use("/api/monetization", async (_req, _res, next) => {
    try { await ensureSchema(); next(); } catch { next(new Error("monetization service unavailable")); }
  });

  app.get("/api/monetization/entitlements", async (req, res) => {
    const userId = authenticate(req, res); if (!userId) return;
    try {
      await ensureSchema();
      await pool.query("UPDATE fynx_business_entitlements SET active=FALSE,updated_at=NOW() WHERE user_id=$1 AND active=TRUE AND expires_at IS NOT NULL AND expires_at<=NOW()", [userId]);
      const [ai, business] = await Promise.all([
        pool.query("SELECT plan,active,updated_at FROM fynx_ai_entitlements WHERE user_id=$1", [userId]),
        pool.query("SELECT plan,active,expires_at,updated_at FROM fynx_business_entitlements WHERE user_id=$1", [userId])
      ]);
      return res.json({
        ai: ai.rows[0] || { plan: "FREE", active: true },
        business: business.rows[0] || { plan: "FREE", active: true, expires_at: null },
        chargingEnabled: false,
        source: "server entitlement state; no client-side payment authority"
      });
    } catch (error) { console.error("[fynx-monetization] entitlements", error); return res.status(500).json({ error: "entitlements unavailable" }); }
  });

  app.get("/api/admin/monetization/reconciliation", async (req, res) => {
    const userId = authenticate(req, res); if (!userId) return;
    try {
      await ensureSchema();
      const role = await adminRole(userId);
      if (!role) return res.status(403).json({ error: "administrator access required", code: "ADMIN_REQUIRED" });
      const [ledger, revenue, escrow] = await Promise.all([
        pool.query(`SELECT currency,
          COALESCE(SUM(amount) FILTER (WHERE account='fynx_marketplace_fee' AND entry_type='FEE'),0)::numeric(14,2) AS marketplace_fees,
          COALESCE(SUM(amount) FILTER (WHERE account='seller_payout' AND entry_type='RELEASE'),0)::numeric(14,2) AS seller_payouts,
          COALESCE(SUM(amount) FILTER (WHERE account='buyer_refund' AND entry_type='REFUND'),0)::numeric(14,2) AS buyer_refunds
          FROM marketplace_ledger_entries GROUP BY currency ORDER BY currency`),
        pool.query(`SELECT currency,
          COALESCE(SUM(amount) FILTER (WHERE source='MARKETPLACE_FEE' AND status='SETTLED'),0)::numeric(14,2) AS revenue_settled,
          COALESCE(SUM(amount) FILTER (WHERE source='MARKETPLACE_FEE' AND status='REFUNDED'),0)::numeric(14,2) AS revenue_refunded,
          COUNT(*) FILTER (WHERE source='MARKETPLACE_FEE')::int AS revenue_transactions
          FROM fynx_revenue_transactions GROUP BY currency ORDER BY currency`),
        pool.query(`SELECT currency,status,COALESCE(SUM(amount),0)::numeric(14,2) AS protected_amount,COUNT(*)::int AS escrows
          FROM marketplace_escrows GROUP BY currency,status ORDER BY currency,status`)
      ]);
      const byCurrency = new Map();
      for (const row of ledger.rows) byCurrency.set(row.currency, { currency: row.currency, ...row });
      for (const row of revenue.rows) {
        const item = byCurrency.get(row.currency) || { currency: row.currency, marketplace_fees: "0", seller_payouts: "0", buyer_refunds: "0" };
        Object.assign(item, row); byCurrency.set(row.currency, item);
      }
      return res.json({
        role,
        currencies: [...byCurrency.values()],
        protectedEscrow: escrow.rows,
        invariants: {
          revenueSource: "fynx_marketplace_fee ledger FEE entries",
          protectedFundsExcluded: true,
          sellerPayoutSource: "seller_payout RELEASE ledger entries",
          refundSync: "buyer_refund REFUND updates marketplace fee revenue to REFUNDED",
          idempotency: "ledger idempotency_key -> revenue source_key"
        }
      });
    } catch (error) { console.error("[fynx-monetization] reconciliation", error); return res.status(500).json({ error: "reconciliation unavailable" }); }
  });

  app.get("/api/admin/monetization/business-entitlements", async (req, res) => {
    const userId = authenticate(req, res); if (!userId) return;
    try {
      await ensureSchema();
      const role = await adminRole(userId);
      if (!role) return res.status(403).json({ error: "administrator access required", code: "ADMIN_REQUIRED" });
      const result = await pool.query("SELECT plan,active,COUNT(*)::int AS users FROM fynx_business_entitlements GROUP BY plan,active ORDER BY plan,active");
      return res.json({ entitlements: result.rows, chargingEnabled: false });
    } catch (error) { console.error("[fynx-monetization] business report", error); return res.status(500).json({ error: "business entitlement report unavailable" }); }
  });

  const CHARGING_ENABLED = String(process.env.FYNX_MONETIZATION_CHARGING_ENABLED || '').toLowerCase() === 'true';
  const PLAN_DURATION_DAYS = Number(process.env.FYNX_MONETIZATION_PLAN_DURATION_DAYS || 30);
  const PLAN_CURRENCY = 'NGN';

  function paidPlanCatalog() {
    const business = Number(process.env.FYNX_BUSINESS_PLAN_AMOUNT_KOBO || 0);
    const creator = Number(process.env.FYNX_CREATOR_PLAN_AMOUNT_KOBO || 0);
    return {
      BUSINESS: { plan: 'BUSINESS', amountKobo: Number.isSafeInteger(business) && business > 0 ? business : null, currency: PLAN_CURRENCY },
      CREATOR: { plan: 'CREATOR', amountKobo: Number.isSafeInteger(creator) && creator > 0 ? creator : null, currency: PLAN_CURRENCY }
    };
  }

  async function ensurePaidPlanSchema() {
    await ensureSchema();
    await pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_plan_payments (
        id UUID PRIMARY KEY,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        plan TEXT NOT NULL CHECK (plan IN ('BUSINESS','CREATOR')),
        amount_kobo BIGINT NOT NULL CHECK (amount_kobo > 0),
        currency TEXT NOT NULL DEFAULT 'NGN',
        reference TEXT NOT NULL UNIQUE,
        authorization_url TEXT,
        access_code TEXT,
        provider_transaction_id BIGINT,
        status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','FAILED','REVERSED')),
        paid_at TIMESTAMPTZ,
        entitlement_expires_at TIMESTAMPTZ,
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      ALTER TABLE fynx_plan_payments ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
      UPDATE fynx_plan_payments SET expires_at=created_at + INTERVAL '30 minutes' WHERE expires_at IS NULL AND status='PENDING';
      CREATE INDEX IF NOT EXISTS fynx_plan_payments_user_idx ON fynx_plan_payments(user_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_plan_payments_status_idx ON fynx_plan_payments(status, updated_at);
    `);
  }

  function validPaidPlan(plan) {
    return plan === 'BUSINESS' || plan === 'CREATOR';
  }

  function validPlanDuration() {
    return Number.isInteger(PLAN_DURATION_DAYS) && PLAN_DURATION_DAYS >= 1 && PLAN_DURATION_DAYS <= 3660;
  }

  async function paystackRequest(path, options = {}) {
    const secret = process.env.PAYSTACK_SECRET_KEY || '';
    if (!secret) throw Object.assign(new Error('PAYSTACK_SECRET_KEY is not configured'), { code: 'PAYSTACK_NOT_CONFIGURED' });
    const response = await fetch(`https://api.paystack.co${path}`, {
      ...options,
      headers: { Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json', ...(options.headers || {}) }
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data?.status !== true) throw Object.assign(new Error(data?.message || `Paystack request failed (${response.status})`), { code: 'PAYSTACK_REQUEST_FAILED', status: response.status });
    return data;
  }

  async function applyVerifiedPlanPayment(client, payment, transaction, source) {
    const paidAmount = Number(transaction?.amount);
    const paidCurrency = String(transaction?.currency || '').trim().toUpperCase();
    const providerReference = String(transaction?.reference || '').trim();
    if (transaction?.status !== 'success' || providerReference !== payment.reference || paidAmount !== Number(payment.amount_kobo) || paidCurrency !== String(payment.currency).toUpperCase()) {
      return { verified: false, reason: 'payment data does not match the server-owned plan payment' };
    }

    const metadataUserId = String(transaction?.metadata?.userId || transaction?.metadata?.user_id || '');
    const metadataPlan = String(transaction?.metadata?.plan || '').toUpperCase();
    const metadataPurpose = String(transaction?.metadata?.purpose || '');
    if (metadataUserId !== String(payment.user_id) || metadataPlan !== String(payment.plan) || metadataPurpose !== 'FYNX_PAID_PLAN') {
      return { verified: false, reason: 'payment metadata does not match the server-owned plan payment' };
    }

    const locked = (await client.query('SELECT * FROM fynx_plan_payments WHERE id=$1 FOR UPDATE', [payment.id])).rows[0];
    if (!locked) return { verified: false, reason: 'plan payment not found' };
    if (locked.status === 'PAID') return { verified: true, idempotent: true, expiresAt: locked.entitlement_expires_at };

    const current = (await client.query('SELECT plan,active,expires_at FROM fynx_business_entitlements WHERE user_id=$1 FOR UPDATE', [payment.user_id])).rows[0];
    const now = Date.now();
    const currentExpiry = current?.expires_at ? new Date(current.expires_at).getTime() : 0;
    const base = current && current.plan === payment.plan && current.active && Number.isFinite(currentExpiry) && currentExpiry > now ? new Date(currentExpiry) : new Date(now);
    const expiresAt = new Date(base.getTime() + PLAN_DURATION_DAYS * 24 * 60 * 60 * 1000);

    await client.query(`
      INSERT INTO fynx_business_entitlements(user_id,plan,active,expires_at,updated_at)
      VALUES($1,$2,TRUE,$3,NOW())
      ON CONFLICT(user_id) DO UPDATE SET plan=EXCLUDED.plan,active=TRUE,expires_at=EXCLUDED.expires_at,updated_at=NOW()
    `, [payment.user_id, payment.plan, expiresAt]);
    await client.query(`
      UPDATE fynx_plan_payments
      SET status='PAID',provider_transaction_id=$1,paid_at=COALESCE($2,NOW()),entitlement_expires_at=$3,metadata=metadata || $4::jsonb,updated_at=NOW()
      WHERE id=$5
    `, [
      Number.isSafeInteger(Number(transaction?.id)) ? Number(transaction.id) : null,
      transaction?.paid_at || transaction?.paidAt || null,
      expiresAt,
      JSON.stringify({ source, providerStatus: transaction?.status, providerReference }),
      payment.id
    ]);
    return { verified: true, idempotent: false, expiresAt };
  }

  app.get('/api/monetization/plans', async (req, res) => {
    const userId = authenticate(req, res); if (!userId) return;
    try {
      await ensurePaidPlanSchema();
      const catalog = paidPlanCatalog();
      return res.json({
        chargingEnabled: CHARGING_ENABLED,
        durationDays: PLAN_DURATION_DAYS,
        currency: PLAN_CURRENCY,
        plans: Object.values(catalog).map(item => ({ plan: item.plan, amountKobo: item.amountKobo, currency: item.currency, configured: item.amountKobo !== null }))
      });
    } catch (error) { console.error('[fynx-monetization] plan catalog', error); return res.status(500).json({ error: 'paid plan catalog unavailable' }); }
  });

  app.post('/api/monetization/plans/:plan/payment', async (req, res) => {
    const userId = authenticate(req, res); if (!userId) return;
    const plan = String(req.params.plan || '').trim().toUpperCase();
    if (!validPaidPlan(plan)) return res.status(400).json({ error: 'unsupported paid plan' });
    if (!CHARGING_ENABLED) return res.status(503).json({ error: 'paid plans are not currently enabled', chargingEnabled: false });
    if (!validPlanDuration()) return res.status(503).json({ error: 'paid plan duration is not configured safely' });

    try {
      await ensurePaidPlanSchema();
      const config = paidPlanCatalog()[plan];
      if (!config?.amountKobo) return res.status(503).json({ error: 'paid plan price is not configured' });
      const emailRow = (await pool.query('SELECT email FROM users WHERE id=$1', [userId])).rows[0];
      const email = String(req.body?.email || emailRow?.email || '').trim().toLowerCase();
      if (!/^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$/.test(email)) return res.status(400).json({ error: 'valid payment email is required' });

      const pending = (await pool.query(`
        SELECT id,plan,amount_kobo,currency,reference,authorization_url,access_code,status
        FROM fynx_plan_payments
        WHERE user_id=$1 AND plan=$2 AND status='PENDING' AND expires_at>NOW()
        ORDER BY created_at DESC LIMIT 1
      `, [userId, plan])).rows[0];
      if (pending?.authorization_url) return res.json({ paymentId: pending.id, plan, reference: pending.reference, authorizationUrl: pending.authorization_url, accessCode: pending.access_code, amountKobo: Number(pending.amount_kobo), currency: pending.currency, idempotent: true });

      const paymentId = crypto.randomUUID();
      const reference = `FYNX-PLAN-${plan}-${paymentId}`;
      const payload = {
        email,
        amount: String(config.amountKobo),
        currency: PLAN_CURRENCY,
        reference,
        metadata: { purpose: 'FYNX_PAID_PLAN', userId: String(userId), plan, paymentId }
      };
      if (process.env.PAYSTACK_CALLBACK_URL) payload.callback_url = process.env.PAYSTACK_CALLBACK_URL;
      const data = await paystackRequest('/transaction/initialize', { method: 'POST', body: JSON.stringify(payload) });
      const providerReference = String(data.data?.reference || '').trim();
      const providerAmount = Number(data.data?.amount);
      const providerCurrency = String(data.data?.currency || '').trim().toUpperCase();
      const authorizationUrl = String(data.data?.authorization_url || '');
      const accessCode = data.data?.access_code ? String(data.data.access_code) : null;
      if (!authorizationUrl || providerReference !== reference || providerAmount !== config.amountKobo || providerCurrency !== PLAN_CURRENCY) return res.status(502).json({ error: 'payment provider returned data that does not match the protected plan price' });

      const inserted = await pool.query(`
        INSERT INTO fynx_plan_payments(id,user_id,plan,amount_kobo,currency,reference,authorization_url,access_code,metadata,expires_at)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb,NOW()+INTERVAL '30 minutes')
        ON CONFLICT(reference) DO NOTHING
        RETURNING id,plan,amount_kobo,currency,reference,authorization_url,access_code,status
      `, [paymentId, userId, plan, config.amountKobo, PLAN_CURRENCY, reference, authorizationUrl, accessCode, JSON.stringify({ purpose: 'FYNX_PAID_PLAN', userId: String(userId), plan })]);
      const row = inserted.rows[0];
      if (!row) return res.status(409).json({ error: 'paid plan payment already exists; retry the request' });
      return res.status(201).json({ paymentId: row.id, plan: row.plan, reference: row.reference, authorizationUrl: row.authorization_url, accessCode: row.access_code, amountKobo: Number(row.amount_kobo), currency: row.currency });
    } catch (error) {
      if (error?.code === 'PAYSTACK_NOT_CONFIGURED') return res.status(503).json({ error: 'paid plan payment provider is not configured yet' });
      console.error('[fynx-monetization] plan payment initialize', error);
      return res.status(502).json({ error: 'paid plan payment initialization failed' });
    }
  });

  app.post('/api/monetization/plans/payment/verify', async (req, res) => {
    const userId = authenticate(req, res); if (!userId) return;
    const reference = typeof req.body?.reference === 'string' ? req.body.reference.trim() : '';
    if (!/^[A-Za-z0-9_.=-]{8,150}$/.test(reference)) return res.status(400).json({ error: 'invalid paid plan payment reference' });
    try {
      await ensurePaidPlanSchema();
      const payment = (await pool.query('SELECT * FROM fynx_plan_payments WHERE reference=$1 AND user_id=$2', [reference, userId])).rows[0];
      if (!payment) return res.status(404).json({ error: 'paid plan payment not found' });
      if (payment.status === 'PAID') return res.json({ verified: true, idempotent: true, plan: payment.plan, expiresAt: payment.entitlement_expires_at });
      const data = await paystackRequest(`/transaction/verify/${encodeURIComponent(reference)}`);
      const transaction = data.data || {};
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const result = await applyVerifiedPlanPayment(client, payment, transaction, 'verification');
        if (!result.verified) { await client.query('ROLLBACK'); return res.status(409).json({ error: result.reason }); }
        await client.query('COMMIT');
        return res.json({ verified: true, idempotent: result.idempotent, plan: payment.plan, expiresAt: result.expiresAt });
      } catch (error) {
        try { await client.query('ROLLBACK'); } catch {}
        throw error;
      } finally { client.release(); }
    } catch (error) {
      if (error?.code === 'PAYSTACK_NOT_CONFIGURED') return res.status(503).json({ error: 'paid plan payment provider is not configured yet' });
      console.error('[fynx-monetization] plan payment verify', error);
      return res.status(502).json({ error: 'paid plan payment verification failed' });
    }
  });

  app.post('/api/monetization/paystack/webhook', async (req, res) => {
    const secret = process.env.PAYSTACK_SECRET_KEY || '';
    const signature = req.get('x-paystack-signature') || '';
    const rawBody = Buffer.isBuffer(req.rawBody) ? req.rawBody : null;
    if (!secret || !rawBody || !safeEqualHex(crypto.createHmac('sha512', secret).update(rawBody).digest('hex'), signature)) return res.status(401).json({ error: 'invalid webhook signature' });
    let event;
    try { event = JSON.parse(rawBody.toString('utf8')); } catch { return res.status(400).json({ error: 'invalid webhook payload' }); }
    const eventName = String(event?.event || '').toLowerCase();
    if (eventName === 'charge.failed') {
      const failedReference = typeof event?.data?.reference === 'string' ? event.data.reference.trim() : '';
      if (!failedReference) return res.status(400).json({ error: 'invalid payment reference' });
      try {
        await ensurePaidPlanSchema();
        const result = await pool.query("UPDATE fynx_plan_payments SET status='FAILED',updated_at=NOW(),metadata=metadata || $1::jsonb WHERE reference=$2 AND status='PENDING' RETURNING id", [JSON.stringify({ source: 'webhook', providerStatus: event?.data?.status || 'failed' }), failedReference]);
        return res.status(200).json({ received: true, matched: result.rowCount > 0, failed: result.rowCount > 0 });
      } catch (error) {
        console.error('[fynx-monetization] failed payment webhook', error);
        return res.status(500).json({ error: 'failed payment webhook processing failed' });
      }
    }
    if (eventName !== 'charge.success') return res.status(200).json({ received: true, ignored: true });
    const transaction = event.data || {};
    const reference = typeof transaction.reference === 'string' ? transaction.reference.trim() : '';
    if (!reference) return res.status(400).json({ error: 'invalid payment reference' });
    try {
      await ensurePaidPlanSchema();
      const payment = (await pool.query('SELECT * FROM fynx_plan_payments WHERE reference=$1', [reference])).rows[0];
      if (!payment) return res.status(200).json({ received: true, matched: false });
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const result = await applyVerifiedPlanPayment(client, payment, transaction, 'webhook');
        if (!result.verified) { await client.query('ROLLBACK'); return res.status(400).json({ error: result.reason }); }
        await client.query('COMMIT');
        return res.status(200).json({ received: true, matched: true, idempotent: result.idempotent });
      } catch (error) {
        try { await client.query('ROLLBACK'); } catch {}
        throw error;
      } finally { client.release(); }
    } catch (error) {
      console.error('[fynx-monetization] paystack webhook', error);
      return res.status(500).json({ error: 'paid plan webhook processing failed' });
    }
  });

}
