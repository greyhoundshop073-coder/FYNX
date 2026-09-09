import crypto from 'node:crypto';
import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const PAYSTACK_SECRET_KEY = process.env.PAYSTACK_SECRET_KEY || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 4, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 15_000, query_timeout: 20_000, keepAlive: true }) : null;
let schemaPromise;

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
  } catch { return res.status(401).json({ error: 'invalid or expired token' }); }
}

function uuid(value) { return typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null; }

async function ensureProtectionSchema() {
  if (!pool) return;
  if (!schemaPromise) {
    schemaPromise = (async () => {
      const tables = await pool.query(`SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('marketplace_protection_cases','marketplace_order_disputes')`);
      const names = new Set(tables.rows.map(row => row.table_name));
      if (!names.has('marketplace_protection_cases')) return;
      if (names.has('marketplace_order_disputes')) {
        await pool.query(`ALTER TABLE marketplace_protection_cases ADD COLUMN IF NOT EXISTS dispute_id UUID;
          DO $$ BEGIN ALTER TABLE marketplace_protection_cases ADD CONSTRAINT marketplace_protection_cases_dispute_fk FOREIGN KEY (dispute_id) REFERENCES marketplace_order_disputes(id) ON DELETE SET NULL; EXCEPTION WHEN duplicate_object THEN NULL; END $$;
          CREATE INDEX IF NOT EXISTS marketplace_protection_cases_dispute_idx ON marketplace_protection_cases (dispute_id);`);
      }
      await pool.query(`CREATE TABLE IF NOT EXISTS marketplace_protection_audit (
        id BIGSERIAL PRIMARY KEY,
        case_id UUID NOT NULL REFERENCES marketplace_protection_cases(id) ON DELETE CASCADE,
        order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
        actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
        action TEXT NOT NULL,
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
        CREATE INDEX IF NOT EXISTS marketplace_protection_audit_case_idx ON marketplace_protection_audit (case_id, created_at ASC);`);
    })().catch(error => { schemaPromise = undefined; throw error; });
  }
  return schemaPromise;
}

async function reconcileOpenDisputes() {
  if (!pool) return;
  const client = await pool.connect();
  try {
    const tables = await client.query(`SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('marketplace_order_disputes','marketplace_protection_cases')`);
    const names = new Set(tables.rows.map(row => row.table_name));
    if (!names.has('marketplace_order_disputes') || !names.has('marketplace_protection_cases')) return;
    const columns = await client.query(`SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='marketplace_protection_cases' AND column_name='dispute_id'`);
    if (!columns.rowCount) return;
    const disputes = await client.query(`SELECT d.id,d.order_id,d.opened_by,d.reason,d.details,d.status,o.buyer_id,o.seller_id
      FROM marketplace_order_disputes d JOIN marketplace_orders o ON o.id=d.order_id
      WHERE d.status IN ('OPEN','UNDER_REVIEW') AND NOT EXISTS (SELECT 1 FROM marketplace_protection_cases c WHERE c.dispute_id=d.id)
      ORDER BY d.created_at ASC LIMIT 100`);
    for (const dispute of disputes.rows) {
      const role = String(dispute.buyer_id) === String(dispute.opened_by) ? 'BUYER' : String(dispute.seller_id) === String(dispute.opened_by) ? 'SELLER' : null;
      if (!role) continue;
      try {
        await client.query('BEGIN');
        const inserted = await client.query(`INSERT INTO marketplace_protection_cases (id,order_id,opened_by,role,case_type,reason,details,status,idempotency_key,dispute_id)
          VALUES ($1,$2,$3,$4,'DISPUTE',$5,$6,CASE WHEN $7 IN ('OPEN','UNDER_REVIEW') THEN $7 ELSE 'OPEN' END,$8,$9)
          ON CONFLICT (idempotency_key) DO NOTHING RETURNING id`, [crypto.randomUUID(), dispute.order_id, dispute.opened_by, role, String(dispute.reason), String(dispute.details || ''), dispute.status, `FYNX-DISPUTE-${dispute.id}`, dispute.id]);
        if (inserted.rows[0]) await client.query(`INSERT INTO marketplace_protection_audit (case_id,order_id,actor_id,action,metadata) VALUES ($1,$2,$3,'DISPUTE_LINKED',$4::jsonb)`, [inserted.rows[0].id, dispute.order_id, dispute.opened_by, JSON.stringify({ disputeId: String(dispute.id), reason: dispute.reason })]);
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK').catch(() => {}); console.error('marketplace protection dispute reconciliation', error); }
    }
  } catch (error) { console.error('marketplace protection dispute scan', error); }
  finally { client.release(); }
}

async function requireAdmin(userId) {
  if (!pool) return false;
  const first = (await pool.query('SELECT id FROM users ORDER BY id ASC LIMIT 1')).rows[0];
  if (first && String(first.id) === String(userId)) return true;
  return (await pool.query('SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1', [userId])).rowCount > 0;
}

async function refundPaystack(reference, amount, currency) {
  if (!PAYSTACK_SECRET_KEY) throw Object.assign(new Error('refund provider is not configured yet'), { code: 'PAYSTACK_NOT_CONFIGURED' });
  if (String(currency).toUpperCase() !== 'NGN') throw Object.assign(new Error('Paystack marketplace refunds currently support NGN only'), { code: 'REFUND_CURRENCY_UNSUPPORTED' });
  const response = await fetch('https://api.paystack.co/refund', { method: 'POST', headers: { Authorization: `Bearer ${PAYSTACK_SECRET_KEY}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ transaction: reference, amount: Math.round(Number(amount) * 100) }) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data?.status !== true) throw new Error(String(data?.message || `Paystack refund failed (${response.status})`));
  return data;
}

export function registerMarketplaceProtectionResolutionRoutes({ app }) {
  if (!pool) return;
  void ensureProtectionSchema().catch(error => console.error('marketplace protection schema', error));
  const reconcileTimer = setInterval(() => { void reconcileOpenDisputes(); }, 15_000);
  reconcileTimer.unref();
  void reconcileOpenDisputes();

  app.get('/api/admin/marketplace/protection/cases', auth, async (req, res) => {
    try {
      await ensureProtectionSchema(); await reconcileOpenDisputes();
      if (!(await requireAdmin(req.user.sub))) return res.status(403).json({ error: 'administrator access required' });
      const status = typeof req.query?.status === 'string' ? req.query.status.trim().toUpperCase() : '';
      const params = status ? [status] : [];
      const result = await pool.query(`SELECT c.id,c.order_id,c.dispute_id,c.opened_by,c.role,c.case_type,c.reason,c.details,c.status,c.created_at,c.updated_at,
        o.buyer_id,o.seller_id,o.total_amount,o.currency,o.status AS order_status,o.payment_reference
        FROM marketplace_protection_cases c JOIN marketplace_orders o ON o.id=c.order_id ${status ? 'WHERE c.status=$1' : ''} ORDER BY c.created_at ASC LIMIT 100`, params);
      return res.json({ cases: result.rows.map(row => ({ ...row, id: String(row.id), orderId: String(row.order_id), disputeId: row.dispute_id ? String(row.dispute_id) : null, openedBy: String(row.opened_by), buyerId: String(row.buyer_id), sellerId: String(row.seller_id), totalAmount: Number(row.total_amount) })) });
    } catch (error) { console.error('marketplace protection admin list', error); return res.status(500).json({ error: 'protection cases unavailable' }); }
  });

  app.post('/api/admin/marketplace/protection/cases/:id/resolve', auth, async (req, res) => {
    const caseId = uuid(req.params.id);
    const resolution = typeof req.body?.resolution === 'string' ? req.body.resolution.trim().toUpperCase() : '';
    const note = typeof req.body?.note === 'string' ? req.body.note.trim().slice(0, 2000) : '';
    if (!caseId) return res.status(400).json({ error: 'invalid case id' });
    if (!['BUYER','SELLER','CANCEL'].includes(resolution)) return res.status(400).json({ error: 'resolution must be BUYER, SELLER or CANCEL' });
    try {
      await ensureProtectionSchema(); await reconcileOpenDisputes();
      if (!(await requireAdmin(req.user.sub))) return res.status(403).json({ error: 'administrator access required' });
      const client = await pool.connect();
      let refundJob = null;
      try {
        await client.query('BEGIN');
        const row = (await client.query(`SELECT c.*,o.buyer_id,o.seller_id,o.total_amount,o.currency,o.status AS order_status,o.payment_reference,o.quantity,o.listing_id,e.id AS escrow_id,e.status AS escrow_status
          FROM marketplace_protection_cases c JOIN marketplace_orders o ON o.id=c.order_id LEFT JOIN marketplace_escrows e ON e.order_id=c.order_id WHERE c.id=$1 FOR UPDATE`, [caseId])).rows[0];
        if (!row) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'protection case not found' }); }
        if (!['OPEN','UNDER_REVIEW'].includes(row.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'protection case is already resolved' }); }
        if (!row.escrow_id) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order escrow is not initialized' }); }

        if (resolution === 'BUYER') {
          if (!row.payment_reference) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'payment reference is required before refund' }); }
          if (!PAYSTACK_SECRET_KEY) { await client.query('ROLLBACK'); return res.status(503).json({ error: 'refund provider is not configured yet' }); }
          const key = `REFUND-${row.order_id}`;
          const existing = (await client.query('SELECT * FROM marketplace_financial_operations WHERE idempotency_key=$1 FOR UPDATE', [key])).rows[0];
          if (existing?.status === 'SUCCEEDED') { await client.query(`UPDATE marketplace_protection_cases SET status='REFUNDED',updated_at=NOW() WHERE id=$1`, [caseId]); await client.query('COMMIT'); return res.json({ ok: true, idempotent: true, caseId: String(caseId), status: 'REFUNDED', operation: existing }); }
          if (existing?.status === 'PENDING') { await client.query('COMMIT'); return res.status(202).json({ ok: true, pending: true, caseId: String(caseId), status: 'REFUND_PENDING', operation: existing }); }
          if (existing?.status === 'FAILED') { await client.query('COMMIT'); return res.status(409).json({ error: 'previous refund attempt failed; provider status must be reconciled before retry', protectionActive: true, caseId: String(caseId), operation: existing }); }
          const operationId = crypto.randomUUID();
          refundJob = { operationId, orderId: String(row.order_id), caseId: String(caseId), reference: String(row.payment_reference), amount: Number(row.total_amount), currency: String(row.currency), quantity: Number(row.quantity), listingId: String(row.listing_id), disputeId: row.dispute_id ? String(row.dispute_id) : null };
          await client.query(`INSERT INTO marketplace_financial_operations (id,order_id,operation_type,idempotency_key,status,provider,amount,currency,metadata) VALUES ($1,$2,'REFUND',$3,'PENDING','paystack',$4,$5,$6::jsonb)`, [operationId, row.order_id, key, row.total_amount, row.currency, JSON.stringify({ paymentReference: row.payment_reference, caseId: String(caseId), note })]);
          await client.query(`UPDATE marketplace_escrows SET status='REFUND_PENDING',updated_at=NOW() WHERE id=$1 AND status IN ('HELD','DISPUTED','RELEASE_ELIGIBLE')`, [row.escrow_id]);
          await client.query(`UPDATE marketplace_protection_cases SET status='UNDER_REVIEW',updated_at=NOW() WHERE id=$1`, [caseId]);
        } else {
          const caseStatus = resolution === 'SELLER' ? 'RESOLVED_SELLER' : 'CANCELLED';
          const escrowStatus = resolution === 'SELLER' ? 'RELEASE_ELIGIBLE' : 'CANCELLED';
          await client.query(`UPDATE marketplace_protection_cases SET status=$1,updated_at=NOW() WHERE id=$2`, [caseStatus, caseId]);
          if (row.dispute_id) await client.query(`UPDATE marketplace_order_disputes SET status=$1,resolution_notes=$2,updated_at=NOW() WHERE id=$3`, [resolution === 'SELLER' ? 'RESOLVED_SELLER' : 'CANCELLED', note, row.dispute_id]);
          if (resolution === 'SELLER') {
            await client.query(`UPDATE marketplace_orders SET status='COMPLETED',completed_at=COALESCE(completed_at,NOW()),updated_at=NOW() WHERE id=$1 AND status='DISPUTED'`, [row.order_id]);
            await client.query(`UPDATE marketplace_listings SET quantity=GREATEST(0,quantity-$1),reserved_quantity=GREATEST(0,reserved_quantity-$1),active=CASE WHEN quantity-$1 <= 0 THEN FALSE ELSE active END,updated_at=NOW() WHERE id=$2`, [row.quantity,row.listing_id]);
          } else {
            await client.query(`UPDATE marketplace_orders SET status='CANCELLED',cancelled_at=COALESCE(cancelled_at,NOW()),updated_at=NOW() WHERE id=$1 AND status='DISPUTED'`, [row.order_id]);
            await client.query(`UPDATE marketplace_listings SET reserved_quantity=GREATEST(0,reserved_quantity-$1),updated_at=NOW() WHERE id=$2`, [row.quantity,row.listing_id]);
          }
          await client.query(`UPDATE marketplace_escrows SET status=$1,release_eligible_at=CASE WHEN $1='RELEASE_ELIGIBLE' THEN COALESCE(release_eligible_at,NOW()) ELSE release_eligible_at END,updated_at=NOW() WHERE id=$2 AND status='DISPUTED'`, [escrowStatus,row.escrow_id]);
        }
        await client.query(`INSERT INTO marketplace_protection_audit (case_id,order_id,actor_id,action,metadata) VALUES ($1,$2,$3,$4,$5::jsonb)`, [caseId,row.order_id,req.user.sub,`RESOLVED_${resolution}`,JSON.stringify({ note, disputeId: row.dispute_id ? String(row.dispute_id) : null })]);
        await client.query('COMMIT');
      } catch (error) { await client.query('ROLLBACK').catch(() => {}); throw error; }
      finally { client.release(); }

      if (refundJob) {
        try {
          const provider = await refundPaystack(refundJob.reference, refundJob.amount, refundJob.currency);
          const providerReference = provider?.data?.id || provider?.data?.refund_reference || provider?.data?.transaction?.reference || null;
          await pool.query(`UPDATE marketplace_financial_operations SET status='PENDING',provider_reference=COALESCE($1,provider_reference),failure_reason=NULL,metadata=jsonb_set(COALESCE(metadata,'{}'::jsonb),'{providerStatus}','"pending"'::jsonb),updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [providerReference ? String(providerReference) : null, refundJob.operationId]);
          return res.status(202).json({ ok: true, pending: true, caseId: refundJob.caseId, status: 'REFUND_PENDING', providerReference: providerReference ? String(providerReference) : null });
        } catch (error) {
          await pool.query(`UPDATE marketplace_financial_operations SET status='FAILED',failure_reason=$1,updated_at=NOW() WHERE id=$2 AND status='PENDING'`, [String(error?.message || error).slice(0,2000),refundJob.operationId]);
          await pool.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE order_id=$1 AND status='REFUND_PENDING'`, [refundJob.orderId]);
          return res.status(502).json({ error: 'refund processing failed; protection remains active' });
        }
      }
      return res.status(200).json({ ok: true, caseId: String(caseId), status: resolution === 'SELLER' ? 'RESOLVED_SELLER' : 'CANCELLED' });
    } catch (error) { console.error('marketplace protection resolution', error); return res.status(error?.code === 'PAYSTACK_NOT_CONFIGURED' ? 503 : 500).json({ error: 'protection resolution failed' }); }
  });
}
