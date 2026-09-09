import crypto from 'node:crypto';
import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 4, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 15_000, query_timeout: 20_000, keepAlive: true }) : null;

function auth(req, res, next) {
  const header = req.get('authorization') || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
  try {
    const parts = token.split('.');
    if (parts.length !== 3) throw new Error('invalid token');
    const [head, body, signature] = parts;
    const expected = crypto.createHmac('sha256', JWT_SECRET).update(`${head}.${body}`).digest('base64url');
    if (expected.length !== signature.length || !crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signature))) throw new Error('invalid token');
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
    if (!payload?.sub || (payload.exp && Number(payload.exp) <= Math.floor(Date.now() / 1000))) throw new Error('expired token');
    req.user = payload;
    return next();
  } catch {
    return res.status(401).json({ error: 'invalid or expired token' });
  }
}

function uuid(value) {
  return typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;
}

async function requireAdmin(userId) {
  if (!pool) return false;
  const first = (await pool.query('SELECT id FROM users ORDER BY id ASC LIMIT 1')).rows[0];
  if (first && String(first.id) === String(userId)) return true;
  return (await pool.query('SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1', [userId])).rowCount > 0;
}

async function refundPaystack(reference, amount, currency) {
  if (!PAYSTACK_SECRET_KEY) throw Object.assign(new Error('payout provider is not configured yet'), { code: 'PAYSTACK_NOT_CONFIGURED' });
  if (String(currency).toUpperCase() !== 'NGN') throw Object.assign(new Error('Paystack marketplace refunds currently support NGN only'), { code: 'REFUND_CURRENCY_UNSUPPORTED' });
  const response = await fetch('https://api.paystack.co/refund', {
    method: 'POST',
    headers: { Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ transaction: reference, amount: Math.round(Number(amount) * 100) })
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data?.status !== true) throw new Error(String(data?.message || `Paystack refund failed (${response.status})`));
  return data;
}

export function registerMarketplaceProtectionResolutionRoutes({ app }) {
  if (!pool) return;

  app.get('/api/admin/marketplace/protection/cases', auth, async (req, res) => {
    try {
      if (!(await requireAdmin(req.user.sub))) return res.status(403).json({ error: 'administrator access required' });
      const status = typeof req.query?.status === 'string' ? req.query.status.trim().toUpperCase() : '';
      const params = [];
      const where = status ? `WHERE c.status=$1` : '';
      if (status) params.push(status);
      const result = await pool.query(`
        SELECT c.id,c.order_id,c.opened_by,c.role,c.case_type,c.reason,c.details,c.status,c.created_at,c.updated_at,
               o.buyer_id,o.seller_id,o.total_amount,o.currency,o.status AS order_status,o.payment_reference
        FROM marketplace_protection_cases c
        JOIN marketplace_orders o ON o.id=c.order_id
        ${where}
        ORDER BY c.created_at ASC
        LIMIT 100`, params);
      return res.json({ cases: result.rows.map(row => ({ ...row, id: String(row.id), orderId: String(row.order_id), openedBy: String(row.opened_by), buyerId: String(row.buyer_id), sellerId: String(row.seller_id), totalAmount: Number(row.total_amount) })) });
    } catch (error) {
      console.error('marketplace protection admin list', error);
      return res.status(500).json({ error: 'protection cases unavailable' });
    }
  });

  app.post('/api/admin/marketplace/protection/cases/:id/resolve', auth, async (req, res) => {
    const caseId = uuid(req.params.id);
    const resolution = typeof req.body?.resolution === 'string' ? req.body.resolution.trim().toUpperCase() : '';
    const note = typeof req.body?.note === 'string' ? req.body.note.trim().slice(0, 2000) : '';
    if (!caseId) return res.status(400).json({ error: 'invalid case id' });
    if (!['BUYER','SELLER','CANCEL'].includes(resolution)) return res.status(400).json({ error: 'resolution must be BUYER, SELLER or CANCEL' });
    try {
      if (!(await requireAdmin(req.user.sub))) return res.status(403).json({ error: 'administrator access required' });
      const client = await pool.connect();
      let refundJob = null;
      try {
        await client.query('BEGIN');
        const row = (await client.query(`
          SELECT c.*,o.buyer_id,o.seller_id,o.total_amount,o.currency,o.status AS order_status,o.payment_reference,
                 e.id AS escrow_id,e.status AS escrow_status
          FROM marketplace_protection_cases c
          JOIN marketplace_orders o ON o.id=c.order_id
          LEFT JOIN marketplace_escrows e ON e.order_id=c.order_id
          WHERE c.id=$1 FOR UPDATE`, [caseId])).rows[0];
        if (!row) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'protection case not found' }); }
        if (!['OPEN','UNDER_REVIEW'].includes(row.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'protection case is already resolved' }); }
        if (!row.escrow_id) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order escrow is not initialized' }); }

        if (resolution === 'BUYER') {
          if (!row.payment_reference) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'payment reference is required before refund' }); }
          if (!PAYSTACK_SECRET_KEY) { await client.query('ROLLBACK'); return res.status(503).json({ error: 'refund provider is not configured yet' }); }
          const key = `REFUND-${row.order_id}`;
          const existing = (await client.query('SELECT * FROM marketplace_financial_operations WHERE idempotency_key=$1 FOR UPDATE', [key])).rows[0];
          if (existing?.status === 'SUCCEEDED') {
            await client.query(`UPDATE marketplace_protection_cases SET status='REFUNDED',updated_at=NOW() WHERE id=$1`, [caseId]);
            await client.query('COMMIT');
            return res.json({ ok: true, idempotent: true, caseId: String(caseId), status: 'REFUNDED', operation: existing });
          }
          if (existing?.status === 'PENDING') {
            await client.query('COMMIT');
            return res.status(202).json({ ok: true, pending: true, caseId: String(caseId), operation: existing });
          }
          const operationId = crypto.randomUUID();
          refundJob = { operationId, orderId: String(row.order_id), caseId: String(caseId), reference: String(row.payment_reference), amount: Number(row.total_amount), currency: String(row.currency) };
          await client.query(`INSERT INTO marketplace_financial_operations (id,order_id,operation_type,idempotency_key,status,provider,amount,currency,metadata) VALUES ($1,$2,'REFUND',$3,'PENDING','paystack',$4,$5,$6::jsonb)`, [operationId, row.order_id, key, row.total_amount, row.currency, JSON.stringify({ paymentReference: row.payment_reference, caseId: String(caseId), note })]);
          await client.query(`UPDATE marketplace_escrows SET status='REFUND_PENDING',updated_at=NOW() WHERE id=$1 AND status IN ('HELD','DISPUTED','RELEASE_ELIGIBLE')`, [row.escrow_id]);
          await client.query(`UPDATE marketplace_protection_cases SET status='UNDER_REVIEW',updated_at=NOW() WHERE id=$1`, [caseId]);
        } else {
          const caseStatus = resolution === 'SELLER' ? 'RESOLVED_SELLER' : 'CANCELLED';
          const escrowStatus = resolution === 'SELLER' ? 'RELEASE_ELIGIBLE' : 'CANCELLED';
          await client.query(`UPDATE marketplace_protection_cases SET status=$1,updated_at=NOW() WHERE id=$2`, [caseStatus, caseId]);
          await client.query(`UPDATE marketplace_escrows SET status=$1,release_eligible_at=CASE WHEN $1='RELEASE_ELIGIBLE' THEN COALESCE(release_eligible_at,NOW()) ELSE release_eligible_at END,updated_at=NOW() WHERE id=$2 AND status='DISPUTED'`, [escrowStatus, row.escrow_id]);
        }
        await client.query(`INSERT INTO marketplace_protection_audit (case_id,order_id,actor_id,action,metadata) VALUES ($1,$2,$3,$4,$5::jsonb)`, [caseId, row.order_id, req.user.sub, `RESOLVED_${resolution}`, JSON.stringify({ note })]);
        await client.query('COMMIT');
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        throw error;
      } finally { client.release(); }

      if (refundJob) {
        try {
          await refundPaystack(refundJob.reference, refundJob.amount, refundJob.currency);
          const finishClient = await pool.connect();
          try {
            await finishClient.query('BEGIN');
            await finishClient.query(`UPDATE marketplace_financial_operations SET status='SUCCEEDED',provider_reference=$1,failure_reason=NULL,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [refundJob.reference, refundJob.operationId]);
            await finishClient.query(`UPDATE marketplace_orders SET status='REFUNDED',updated_at=NOW() WHERE id=$1 AND status NOT IN ('COMPLETED','CANCELLED','REFUNDED')`, [refundJob.orderId]);
            await finishClient.query(`UPDATE marketplace_protection_cases SET status='REFUNDED',updated_at=NOW() WHERE id=$1`, [refundJob.caseId]);
            await finishClient.query(`UPDATE marketplace_escrows SET status='REFUNDED',refunded_at=NOW(),updated_at=NOW() WHERE order_id=$1 AND status='REFUND_PENDING'`, [refundJob.orderId]);
            await finishClient.query('COMMIT');
          } catch (error) {
            await finishClient.query('ROLLBACK').catch(() => {});
            throw error;
          } finally { finishClient.release(); }
          return res.status(200).json({ ok: true, caseId: refundJob.caseId, status: 'REFUNDED' });
        } catch (error) {
          await pool.query(`UPDATE marketplace_financial_operations SET status='FAILED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [String(error?.message || error).slice(0, 2000), refundJob.operationId]);
          await pool.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE order_id=$1 AND status='REFUND_PENDING'`, [refundJob.orderId]);
          return res.status(502).json({ error: 'refund processing failed; protection remains active' });
        }
      }
      return res.status(200).json({ ok: true, caseId: String(caseId), status: resolution === 'SELLER' ? 'RESOLVED_SELLER' : 'CANCELLED' });
    } catch (error) {
      console.error('marketplace protection resolution', error);
      return res.status(error?.code === 'PAYSTACK_NOT_CONFIGURED' ? 503 : 500).json({ error: 'protection resolution failed' });
    }
  });
}
