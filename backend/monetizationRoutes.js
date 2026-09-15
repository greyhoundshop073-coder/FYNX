import pg from "pg";
import jwt from "jsonwebtoken";

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
}
