import crypto from 'node:crypto';
import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 4 }) : null;

let schemaPromise;

function parseUuid(value) {
  return typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;
}

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
    if (!payload?.sub || (payload.exp && Number(payload.exp) <= Math.floor(Date.now() / 1000))) throw new Error('expired token');
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
      CREATE TABLE IF NOT EXISTS marketplace_protection_cases (
        id UUID PRIMARY KEY,
        order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
        opened_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        role TEXT NOT NULL CHECK (role IN ('BUYER','SELLER')),
        case_type TEXT NOT NULL CHECK (case_type IN ('DISPUTE','REFUND_REQUEST')),
        reason TEXT NOT NULL,
        details TEXT NOT NULL DEFAULT '',
        status TEXT NOT NULL CHECK (status IN ('OPEN','UNDER_REVIEW','RESOLVED_BUYER','RESOLVED_SELLER','REFUNDED','CANCELLED')),
        idempotency_key TEXT NOT NULL UNIQUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS marketplace_protection_cases_order_idx ON marketplace_protection_cases (order_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS marketplace_protection_cases_status_idx ON marketplace_protection_cases (status, updated_at DESC);

      CREATE TABLE IF NOT EXISTS marketplace_protection_audit (
        id BIGSERIAL PRIMARY KEY,
        case_id UUID NOT NULL REFERENCES marketplace_protection_cases(id) ON DELETE CASCADE,
        order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
        actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
        action TEXT NOT NULL,
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS marketplace_protection_audit_case_idx ON marketplace_protection_audit (case_id, created_at ASC);
    `).catch((error) => {
      schemaPromise = undefined;
      throw error;
    });
  }
  return schemaPromise;
}

async function getOrderAndEscrow(client, orderId) {
  const order = (await client.query('SELECT id,buyer_id,seller_id,total_amount,currency,status FROM marketplace_orders WHERE id=$1 FOR UPDATE', [orderId])).rows[0];
  if (!order) return null;
  const escrow = (await client.query('SELECT * FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE', [orderId])).rows[0] || null;
  return { order, escrow };
}

export function registerMarketplaceProtectionRoutes({ app }) {
  app.post('/api/marketplace/protection/order/:id/dispute', auth, async (req, res) => {
    await createCase(req, res, 'DISPUTE');
  });

  app.post('/api/marketplace/protection/order/:id/refund-request', auth, async (req, res) => {
    await createCase(req, res, 'REFUND_REQUEST');
  });

  app.get('/api/marketplace/protection/order/:id/cases', auth, async (req, res) => {
    const orderId = parseUuid(req.params.id);
    if (!orderId) return res.status(400).json({ error: 'invalid order id' });
    try {
      await ensureSchema();
      const order = (await pool.query('SELECT buyer_id,seller_id FROM marketplace_orders WHERE id=$1', [orderId])).rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (String(order.buyer_id) !== String(req.user.sub) && String(order.seller_id) !== String(req.user.sub)) return res.status(403).json({ error: 'order unavailable' });
      const cases = await pool.query('SELECT id,case_type,reason,details,status,created_at,updated_at FROM marketplace_protection_cases WHERE order_id=$1 ORDER BY created_at DESC', [orderId]);
      return res.json({ cases: cases.rows });
    } catch (error) {
      console.error('marketplace protection case lookup', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500).json({ error: 'protection cases unavailable' });
    }
  });
}

async function createCase(req, res, caseType) {
  const orderId = parseUuid(req.params.id);
  const reason = typeof req.body?.reason === 'string' ? req.body.reason.trim().slice(0, 160) : '';
  const details = typeof req.body?.details === 'string' ? req.body.details.trim().slice(0, 2000) : '';
  const suppliedKey = typeof req.get('idempotency-key') === 'string' ? req.get('idempotency-key').trim().slice(0, 160) : '';
  const idempotencyKey = suppliedKey || `FYNX-${caseType}-${orderId}-${req.user.sub}`;
  if (!orderId) return res.status(400).json({ error: 'invalid order id' });
  if (reason.length < 3) return res.status(400).json({ error: 'a short reason is required' });
  try {
    await ensureSchema();
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const record = await getOrderAndEscrow(client, orderId);
      if (!record) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      const { order, escrow } = record;
      const isBuyer = String(order.buyer_id) === String(req.user.sub);
      const isSeller = String(order.seller_id) === String(req.user.sub);
      if (!isBuyer && !isSeller) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'order unavailable' }); }
      if (caseType === 'REFUND_REQUEST' && !isBuyer) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can request a refund' }); }
      if (!escrow || !['HELD','RELEASE_ELIGIBLE','DISPUTED'].includes(escrow.status)) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'this order is no longer eligible for a protection request' });
      }

      const existing = (await client.query('SELECT id,case_type,reason,details,status,created_at,updated_at FROM marketplace_protection_cases WHERE idempotency_key=$1', [idempotencyKey])).rows[0];
      if (existing) {
        await client.query('ROLLBACK');
        return res.json({ case: existing, idempotent: true });
      }

      const caseId = crypto.randomUUID();
      const role = isBuyer ? 'BUYER' : 'SELLER';
      const inserted = (await client.query(`
        INSERT INTO marketplace_protection_cases (id,order_id,opened_by,role,case_type,reason,details,status,idempotency_key)
        VALUES ($1,$2,$3,$4,$5,$6,$7,'OPEN',$8)
        RETURNING id,case_type,reason,details,status,created_at,updated_at
      `, [caseId, orderId, req.user.sub, role, caseType, reason, details, idempotencyKey])).rows[0];

      await client.query(`
        INSERT INTO marketplace_protection_audit (case_id,order_id,actor_id,action,metadata)
        VALUES ($1,$2,$3,$4,$5::jsonb)
      `, [caseId, orderId, req.user.sub, caseType === 'DISPUTE' ? 'DISPUTE_OPENED' : 'REFUND_REQUESTED', JSON.stringify({ role, reason })]);

      // A protection request immediately prevents payout release while the case is reviewed.
      await client.query(`
        UPDATE marketplace_escrows
        SET status='DISPUTED', updated_at=NOW()
        WHERE order_id=$1 AND status IN ('HELD','RELEASE_ELIGIBLE')
      `, [orderId]);

      await client.query('COMMIT');
      return res.status(201).json({ case: inserted, protection: { funds: 'DISPUTED', payoutBlocked: true } });
    } catch (error) {
      await client.query('ROLLBACK');
      if (error?.code === '23505') {
        const existing = (await client.query('SELECT id,case_type,reason,details,status,created_at,updated_at FROM marketplace_protection_cases WHERE idempotency_key=$1', [idempotencyKey])).rows[0];
        if (existing) return res.json({ case: existing, idempotent: true });
      }
      throw error;
    } finally {
      client.release();
    }
  } catch (error) {
    console.error('marketplace protection request', error);
    return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500).json({ error: 'protection request unavailable' });
  }
}
