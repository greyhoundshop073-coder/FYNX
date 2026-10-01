import crypto from 'node:crypto';
import pg from 'pg';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 4, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 15_000, query_timeout: 20_000, keepAlive: true }) : null;

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
  } catch { return res.status(401).json({ error: 'invalid or expired token' }); }
}

function parseUuid(value) { return typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null; }

export function registerMarketplacePayoutRetryRoutes({ app }) {
  app.post('/api/marketplace/settlement/retry/:id', auth, async (req, res) => {
    const orderId = parseUuid(req.params.id);
    if (!orderId) return res.status(400).json({ error: 'invalid order id' });
    if (!pool) return res.status(503).json({ error: 'settlement database is not configured' });
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const order = (await client.query(`SELECT id,seller_id,seller_net_amount,total_amount,currency,marketplace_fee,marketplace_fee_buyer,marketplace_fee_seller,fee_policy,fee_policy_version FROM marketplace_orders WHERE id=$1 FOR UPDATE`, [orderId])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (String(order.seller_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can retry this payout' }); }

      const escrow = (await client.query(`SELECT * FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE`, [orderId])).rows[0];
      if (!escrow || escrow.status !== 'RELEASE_ELIGIBLE') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'payout is not currently eligible for retry' }); }

      const payoutAccount = (await client.query(`SELECT recipient_code,verified,active,currency FROM marketplace_payout_accounts WHERE seller_id=$1 FOR UPDATE`, [req.user.sub])).rows[0];
      if (!payoutAccount || !payoutAccount.verified || !payoutAccount.active || !payoutAccount.recipient_code) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'verified active payout account is required' }); }
      if (String(payoutAccount.currency).toUpperCase() !== String(order.currency).toUpperCase()) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'payout account currency does not match the order' }); }

      const failed = (await client.query(`SELECT * FROM marketplace_financial_operations WHERE order_id=$1 AND operation_type='PAYOUT_RELEASE' AND status='FAILED' ORDER BY updated_at DESC LIMIT 1`, [orderId])).rows[0];
      if (!failed) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'no failed payout is available for retry' }); }
      const key = `PAYOUT-RETRY-${failed.id}`;
      const existing = (await client.query(`SELECT * FROM marketplace_financial_operations WHERE idempotency_key=$1 FOR UPDATE`, [key])).rows[0];
      if (existing) { await client.query('COMMIT'); return res.json({ operation: existing, idempotent: true }); }

      const operationId = crypto.randomUUID();
      const sellerNetAmount = Number(order.seller_net_amount ?? order.total_amount);
      const protectedAmount = Number(escrow.amount);
      const marketplaceFee = Number(order.marketplace_fee || 0);
      if (!Number.isFinite(sellerNetAmount) || !Number.isFinite(protectedAmount) || !Number.isFinite(marketplaceFee) || sellerNetAmount <= 0 || marketplaceFee < 0 || Math.round((sellerNetAmount + marketplaceFee) * 100) / 100 !== protectedAmount) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'payout accounting does not reconcile with protected funds' }); }

      const metadata = { recipientCode: payoutAccount.recipient_code, escrowAmount: protectedAmount, sellerNetAmount, marketplaceFee, marketplaceFeeBuyer: Number(order.marketplace_fee_buyer || 0), marketplaceFeeSeller: Number(order.marketplace_fee_seller || 0), feePolicy: order.fee_policy, feePolicyVersion: order.fee_policy_version, retryOf: String(failed.id) };
      const created = (await client.query(`INSERT INTO marketplace_financial_operations (id,order_id,operation_type,idempotency_key,status,provider,amount,currency,metadata) VALUES ($1,$2,'PAYOUT_RELEASE',$3,'PENDING','paystack',$4,$5,$6::jsonb) RETURNING *`, [operationId, order.id, key, sellerNetAmount, order.currency, JSON.stringify(metadata)])).rows[0];
      await client.query(`UPDATE marketplace_escrows SET status='RELEASE_PENDING',updated_at=NOW() WHERE id=$1 AND status='RELEASE_ELIGIBLE'`, [escrow.id]);
      await client.query('COMMIT');

      const jobs = globalThis.__fynxBackgroundJobs;
      if (jobs?.enabled && typeof jobs.enqueue === 'function') await jobs.enqueue('marketplace.payout.release', { operationId: String(created.id) }, { idempotencyKey: `RELEASE-${String(created.id)}`, maxAttempts: 5 });
      return res.status(202).json({ operation: created, queued: Boolean(jobs?.enabled) });
    } catch (error) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('marketplace payout retry', error);
      return res.status(500).json({ error: 'payout retry unavailable' });
    } finally { client.release(); }
  });
}

export async function closeMarketplacePayoutRetry() { if (pool) await pool.end(); }
