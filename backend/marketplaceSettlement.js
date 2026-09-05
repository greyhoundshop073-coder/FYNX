import crypto from 'node:crypto';
import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 8 }) : null;

let schemaPromise;

const parseUuid = (value) => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;
const amountSubunit = (value, currency) => {
  const normalized = String(currency || '').trim().toUpperCase();
  if (!['NGN', 'USD'].includes(normalized)) return null;
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount <= 0) return null;
  return Math.round(amount * 100);
};

function auth(req, res, next) {
  const header = req.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
  try {
    const parts = token.split('.');
    if (parts.length !== 3) throw new Error('invalid token');
    const [encodedHeader, encodedPayload, encodedSignature] = parts;
    const signature = crypto.createHmac('sha256', JWT_SECRET).update(`${encodedHeader}.${encodedPayload}`).digest('base64url');
    if (signature.length !== encodedSignature.length || !crypto.timingSafeEqual(Buffer.from(signature), Buffer.from(encodedSignature))) throw new Error('invalid token');
    const payload = JSON.parse(Buffer.from(encodedPayload, 'base64url').toString('utf8'));
    if (!payload?.sub || payload.exp && Number(payload.exp) <= Math.floor(Date.now() / 1000)) throw new Error('expired token');
    req.user = payload;
    return next();
  } catch {
    return res.status(401).json({ error: 'invalid or expired token' });
  }
}

async function ensureSchema() {
  if (!pool) throw Object.assign(new Error('DATABASE_URL is not configured'), { code: 'DATABASE_NOT_CONFIGURED' });
  if (!schemaPromise) {
    schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS marketplace_payout_accounts (
        id UUID PRIMARY KEY,
        seller_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
        provider TEXT NOT NULL DEFAULT 'paystack',
        recipient_code TEXT NOT NULL UNIQUE,
        bank_code TEXT NOT NULL,
        bank_name TEXT NOT NULL DEFAULT '',
        account_name TEXT NOT NULL DEFAULT '',
        account_last4 TEXT NOT NULL DEFAULT '',
        currency TEXT NOT NULL DEFAULT 'NGN',
        verified BOOLEAN NOT NULL DEFAULT FALSE,
        active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS marketplace_payout_accounts_seller_idx ON marketplace_payout_accounts (seller_id, active);

      CREATE TABLE IF NOT EXISTS marketplace_escrows (
        id UUID PRIMARY KEY,
        order_id UUID NOT NULL UNIQUE REFERENCES marketplace_orders(id) ON DELETE CASCADE,
        buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
        currency TEXT NOT NULL,
        status TEXT NOT NULL CHECK (status IN ('HELD','RELEASE_ELIGIBLE','RELEASE_PENDING','RELEASED','REFUND_PENDING','REFUNDED','DISPUTED','CANCELLED')),
        held_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        release_eligible_at TIMESTAMPTZ,
        released_at TIMESTAMPTZ,
        refunded_at TIMESTAMPTZ,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS marketplace_escrows_seller_status_idx ON marketplace_escrows (seller_id, status, updated_at DESC);
      CREATE INDEX IF NOT EXISTS marketplace_escrows_buyer_status_idx ON marketplace_escrows (buyer_id, status, updated_at DESC);

      CREATE TABLE IF NOT EXISTS marketplace_ledger_entries (
        id BIGSERIAL PRIMARY KEY,
        escrow_id UUID NOT NULL REFERENCES marketplace_escrows(id) ON DELETE CASCADE,
        order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
        account TEXT NOT NULL,
        entry_type TEXT NOT NULL CHECK (entry_type IN ('HOLD','RELEASE','REFUND','FEE','REVERSAL')),
        amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
        currency TEXT NOT NULL,
        idempotency_key TEXT NOT NULL UNIQUE,
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS marketplace_ledger_order_idx ON marketplace_ledger_entries (order_id, created_at ASC);
      CREATE INDEX IF NOT EXISTS marketplace_ledger_escrow_idx ON marketplace_ledger_entries (escrow_id, created_at ASC);

      CREATE TABLE IF NOT EXISTS marketplace_financial_operations (
        id UUID PRIMARY KEY,
        order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
        operation_type TEXT NOT NULL CHECK (operation_type IN ('ESCROW_HOLD','PAYOUT_RELEASE','REFUND')),
        idempotency_key TEXT NOT NULL UNIQUE,
        status TEXT NOT NULL CHECK (status IN ('PENDING','SUCCEEDED','FAILED','BLOCKED')),
        provider TEXT NOT NULL DEFAULT 'paystack',
        provider_reference TEXT,
        amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
        currency TEXT NOT NULL,
        failure_reason TEXT,
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS marketplace_fin_ops_order_idx ON marketplace_financial_operations (order_id, created_at DESC);
      CREATE UNIQUE INDEX IF NOT EXISTS marketplace_fin_ops_provider_ref_idx ON marketplace_financial_operations (provider_reference) WHERE provider_reference IS NOT NULL;

      CREATE OR REPLACE FUNCTION fynx_marketplace_sync_escrow() RETURNS trigger AS $$
      BEGIN
        IF NEW.status = 'PAID' AND (TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM NEW.status) THEN
          INSERT INTO marketplace_escrows (id,order_id,buyer_id,seller_id,amount,currency,status,held_at,updated_at)
          VALUES (gen_random_uuid(),NEW.id,NEW.buyer_id,NEW.seller_id,NEW.total_amount,NEW.currency,'HELD',NOW(),NOW())
          ON CONFLICT (order_id) DO NOTHING;
          INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata)
          SELECT e.id,NEW.id,'marketplace_escrow','HOLD',NEW.total_amount,NEW.currency,'ESCROW-HOLD-' || NEW.id,jsonb_build_object('source','payment_confirmation')
          FROM marketplace_escrows e
          WHERE e.order_id=NEW.id
          ON CONFLICT (idempotency_key) DO NOTHING;
        ELSIF NEW.status = 'DISPUTED' AND OLD.status IS DISTINCT FROM NEW.status THEN
          UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE order_id=NEW.id AND status IN ('HELD','RELEASE_ELIGIBLE','RELEASE_PENDING');
        ELSIF NEW.status = 'COMPLETED' AND OLD.status IS DISTINCT FROM NEW.status THEN
          UPDATE marketplace_escrows SET status='RELEASE_ELIGIBLE',release_eligible_at=COALESCE(release_eligible_at,NOW()),updated_at=NOW() WHERE order_id=NEW.id AND status='HELD';
        ELSIF NEW.status = 'REFUNDED' AND OLD.status IS DISTINCT FROM NEW.status THEN
          UPDATE marketplace_escrows SET status='REFUNDED',refunded_at=COALESCE(refunded_at,NOW()),updated_at=NOW() WHERE order_id=NEW.id AND status IN ('HELD','DISPUTED','REFUND_PENDING');
        ELSIF NEW.status = 'CANCELLED' AND OLD.status IS DISTINCT FROM NEW.status THEN
          UPDATE marketplace_escrows SET status='CANCELLED',updated_at=NOW() WHERE order_id=NEW.id AND status IN ('HELD','DISPUTED');
        END IF;
        RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;

      DROP TRIGGER IF EXISTS marketplace_order_escrow_sync ON marketplace_orders;
      CREATE TRIGGER marketplace_order_escrow_sync
      AFTER INSERT OR UPDATE OF status ON marketplace_orders
      FOR EACH ROW EXECUTE FUNCTION fynx_marketplace_sync_escrow();
    `).catch((error) => {
      schemaPromise = undefined;
      throw error;
    });
  }
  return schemaPromise;
}

function publicPayoutAccount(row) {
  return {
    id: String(row.id),
    provider: row.provider,
    recipientCode: row.recipient_code,
    bankCode: row.bank_code,
    bankName: row.bank_name,
    accountName: row.account_name,
    accountLast4: row.account_last4,
    currency: row.currency,
    verified: Boolean(row.verified),
    active: Boolean(row.active),
    updatedAt: row.updated_at
  };
}

export function registerMarketplaceSettlementRoutes({ app }) {
  app.get('/api/marketplace/settlement/order/:id', auth, async (req, res) => {
    const orderId = parseUuid(req.params.id);
    if (!orderId) return res.status(400).json({ error: 'invalid order id' });
    try {
      await ensureSchema();
      const orderResult = await pool.query('SELECT id,buyer_id,seller_id,total_amount,currency,status FROM marketplace_orders WHERE id=$1', [orderId]);
      const order = orderResult.rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (String(order.buyer_id) !== String(req.user.sub) && String(order.seller_id) !== String(req.user.sub)) return res.status(403).json({ error: 'order unavailable' });
      const escrow = (await pool.query('SELECT * FROM marketplace_escrows WHERE order_id=$1', [orderId])).rows[0] || null;
      const operations = await pool.query('SELECT operation_type,idempotency_key,status,provider,provider_reference,amount,currency,failure_reason,created_at,updated_at FROM marketplace_financial_operations WHERE order_id=$1 ORDER BY created_at DESC', [orderId]);
      const ledger = await pool.query('SELECT account,entry_type,amount,currency,idempotency_key,metadata,created_at FROM marketplace_ledger_entries WHERE order_id=$1 ORDER BY created_at ASC', [orderId]);
      return res.json({
        orderId: String(order.id), orderStatus: order.status,
        protection: { enabled: true, funds: escrow ? escrow.status : 'NOT_INITIALIZED', payoutBlockedUntilCompletion: true, disputeBlocksPayout: true },
        escrow: escrow ? { id: String(escrow.id), amount: Number(escrow.amount), currency: escrow.currency, status: escrow.status, heldAt: escrow.held_at, releaseEligibleAt: escrow.release_eligible_at, releasedAt: escrow.released_at, refundedAt: escrow.refunded_at } : null,
        operations: operations.rows,
        ledger: ledger.rows
      });
    } catch (error) {
      console.error('marketplace settlement status', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500).json({ error: 'settlement status unavailable' });
    }
  });

  app.get('/api/marketplace/settlement/payout-account', auth, async (req, res) => {
    try {
      await ensureSchema();
      const row = (await pool.query('SELECT * FROM marketplace_payout_accounts WHERE seller_id=$1', [req.user.sub])).rows[0];
      return res.json({ payoutAccount: row ? publicPayoutAccount(row) : null });
    } catch (error) {
      console.error('marketplace payout account lookup', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500).json({ error: 'payout account unavailable' });
    }
  });

  app.post('/api/marketplace/settlement/payout-account', auth, async (req, res) => {
    const bankCode = typeof req.body?.bankCode === 'string' ? req.body.bankCode.trim() : '';
    const accountNumber = typeof req.body?.accountNumber === 'string' ? req.body.accountNumber.replace(/\D/g, '') : '';
    const accountName = typeof req.body?.accountName === 'string' ? req.body.accountName.trim().slice(0, 160) : '';
    const bankName = typeof req.body?.bankName === 'string' ? req.body.bankName.trim().slice(0, 120) : '';
    if (!/^[0-9]{3,10}$/.test(bankCode) || !/^[0-9]{6,20}$/.test(accountNumber) || accountName.length < 2) return res.status(400).json({ error: 'valid bank code, account number and account name are required' });
    try {
      await ensureSchema();
      const secret = process.env.PAYSTACK_SECRET_KEY || '';
      if (!secret) return res.status(503).json({ error: 'payout provider is not configured yet' });
      const resolveResponse = await fetch(`https://api.paystack.co/bank/resolve?account_number=${encodeURIComponent(accountNumber)}&bank_code=${encodeURIComponent(bankCode)}`, { headers: { Authorization: `Bearer ${secret}` } });
      const resolved = await resolveResponse.json().catch(() => ({}));
      if (!resolveResponse.ok || resolved?.status !== true || !resolved?.data?.account_name) return res.status(422).json({ error: 'bank account could not be verified' });
      const verifiedName = String(resolved.data.account_name).trim();
      const recipientResponse = await fetch('https://api.paystack.co/transferrecipient', {
        method: 'POST',
        headers: { Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'nuban', name: verifiedName, account_number: accountNumber, bank_code: bankCode, currency: 'NGN', description: `FYNX seller ${req.user.sub}` })
      });
      const recipient = await recipientResponse.json().catch(() => ({}));
      if (!recipientResponse.ok || recipient?.status !== true || !recipient?.data?.recipient_code) return res.status(502).json({ error: 'payout recipient creation failed' });
      const recipientCode = String(recipient.data.recipient_code);
      const last4 = accountNumber.slice(-4);
      const id = crypto.randomUUID();
      const result = await pool.query(`
        INSERT INTO marketplace_payout_accounts (id,seller_id,provider,recipient_code,bank_code,bank_name,account_name,account_last4,currency,verified,active,updated_at)
        VALUES ($1,$2,'paystack',$3,$4,$5,$6,$7,'NGN',TRUE,TRUE,NOW())
        ON CONFLICT (seller_id) DO UPDATE SET recipient_code=EXCLUDED.recipient_code,bank_code=EXCLUDED.bank_code,bank_name=EXCLUDED.bank_name,account_name=EXCLUDED.account_name,account_last4=EXCLUDED.account_last4,currency=EXCLUDED.currency,verified=TRUE,active=TRUE,updated_at=NOW()
        RETURNING *
      `, [id, req.user.sub, recipientCode, bankCode, bankName || String(recipient.data.details?.bank_name || ''), verifiedName, last4]);
      return res.status(200).json({ payoutAccount: publicPayoutAccount(result.rows[0]) });
    } catch (error) {
      console.error('marketplace payout account setup', error);
      return res.status(502).json({ error: 'payout account setup failed' });
    }
  });

  app.post('/api/marketplace/settlement/release/:id', auth, async (req, res) => {
    const orderId = parseUuid(req.params.id);
    if (!orderId) return res.status(400).json({ error: 'invalid order id' });
    try {
      await ensureSchema();
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [orderId])).rows[0];
        if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
        if (String(order.seller_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can request payout release' }); }
        const escrow = (await client.query('SELECT * FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE', [orderId])).rows[0];
        if (!escrow) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'escrow has not been initialized' }); }
        if (order.status === 'DISPUTED' || escrow.status === 'DISPUTED') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'payout is blocked while the order is disputed' }); }
        if (order.status !== 'COMPLETED' || escrow.status !== 'RELEASE_ELIGIBLE') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'payout is not yet eligible; buyer completion is required' }); }
        const payoutAccount = (await client.query('SELECT * FROM marketplace_payout_accounts WHERE seller_id=$1 AND active=TRUE AND verified=TRUE', [order.seller_id])).rows[0];
        if (!payoutAccount) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'verified seller payout account is required' }); }
        const key = `PAYOUT-${order.id}`;
        const existing = (await client.query('SELECT * FROM marketplace_financial_operations WHERE idempotency_key=$1 FOR UPDATE', [key])).rows[0];
        if (existing) { await client.query('COMMIT'); return res.json({ operation: existing, idempotent: true }); }
        const operationId = crypto.randomUUID();
        const operation = await client.query(`INSERT INTO marketplace_financial_operations (id,order_id,operation_type,idempotency_key,status,provider,amount,currency,metadata) VALUES ($1,$2,'PAYOUT_RELEASE',$3,'PENDING','paystack',$4,$5,$6::jsonb) RETURNING *`, [operationId,order.id,key,order.total_amount,order.currency,JSON.stringify({ recipientCode: payoutAccount.recipient_code })]);
        await client.query(`UPDATE marketplace_escrows SET status='RELEASE_PENDING',updated_at=NOW() WHERE id=$1`, [escrow.id]);
        await client.query('COMMIT');
        return res.status(202).json({ operation: operation.rows[0], message: 'payout release queued; provider transfer will be executed by the settlement worker' });
      } catch (error) {
        try { await client.query('ROLLBACK'); } catch {}
        throw error;
      } finally { client.release(); }
    } catch (error) {
      console.error('marketplace payout release', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500).json({ error: 'payout release request failed' });
    }
  });
}
