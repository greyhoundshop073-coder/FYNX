import crypto from 'node:crypto';
import bcrypt from 'bcryptjs';
import pg from 'pg';
import { ensureBuyerWalletSchema } from './buyerWallet.js';
import { ensureMarketplaceSettlementSchema } from './marketplaceSettlement.js';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 4,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;

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

const walletSpendingApproved = () =>
  process.env.FYNX_WALLET_PROVIDER_APPROVED === 'true' &&
  process.env.FYNX_WALLET_CUSTODY_APPROVED === 'true' &&
  process.env.FYNX_WALLET_COMPLIANCE_APPROVED === 'true' &&
  process.env.FYNX_WALLET_SPENDING_ENABLED === 'true' &&
  Boolean(process.env.PAYSTACK_SECRET_KEY);

const parseUuid = (value) =>
  typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;

export async function refundBuyerWalletMarketplaceOrder(client, {
  buyerId, orderId, amount, currency = 'NGN'
}) {
  const numericAmount = Number(amount);
  const normalizedCurrency = String(currency || '').trim().toUpperCase();
  if (!buyerId || !orderId || !Number.isFinite(numericAmount) || numericAmount <= 0 || normalizedCurrency !== 'NGN') {
    throw Object.assign(new Error('Wallet refund amount, owner or currency is invalid'), { code: 'WALLET_REFUND_INVALID' });
  }

  const reference = `FYNX-WALLET-ORDER-REFUND-${String(orderId)}`;
  const idempotencyKey = reference;
  const existing = (await client.query(
    'SELECT id,status,reference FROM buyer_wallet_transactions WHERE idempotency_key=$1 FOR UPDATE',
    [idempotencyKey]
  )).rows[0];
  if (existing) {
    if (existing.status !== 'SUCCEEDED') {
      throw Object.assign(new Error('Wallet refund transaction requires reconciliation'), { code: 'WALLET_REFUND_PENDING' });
    }
    return { transactionId: String(existing.id), reference: String(existing.reference), idempotent: true };
  }

  const wallet = (await client.query(`
    SELECT id,status
      FROM buyer_wallets
     WHERE user_id=$1 AND currency='NGN'
     FOR UPDATE
  `, [buyerId])).rows[0];
  if (!wallet || wallet.status === 'CLOSED') {
    throw Object.assign(new Error('Buyer Wallet is unavailable for the Marketplace refund'), { code: 'WALLET_REFUND_UNAVAILABLE' });
  }

  const escrowBalance = (await client.query(`
    SELECT COALESCE(SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END),0)::numeric(18,2) AS balance
      FROM buyer_wallet_ledger_entries e
      JOIN buyer_wallet_transactions t ON t.id=e.transaction_id
     WHERE e.wallet_id=$1 AND e.account_code='MARKETPLACE_ESCROW' AND t.status='SUCCEEDED'
  `, [wallet.id])).rows[0];
  if (Number(escrowBalance?.balance || 0) < numericAmount) {
    throw Object.assign(new Error('Wallet Marketplace escrow does not contain the refundable amount'), { code: 'WALLET_REFUND_ESCROW_MISMATCH' });
  }

  const transactionId = crypto.randomUUID();
  await client.query(`
    INSERT INTO buyer_wallet_transactions
      (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata)
    VALUES ($1,$2,$3,'REFUND','PENDING',$4,0,'NGN',$5,$6,'Marketplace order refund',$7::jsonb)
  `, [
    transactionId, wallet.id, buyerId, numericAmount.toFixed(2), reference, idempotencyKey,
    JSON.stringify({ source: 'marketplace_protection', orderId: String(orderId), destination: 'FYNX_WALLET' })
  ]);
  await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [transactionId]);
  await client.query(`
    INSERT INTO buyer_wallet_ledger_entries
      (transaction_id,wallet_id,entry_key,account_code,side,amount,currency)
    VALUES
      ($1,$2,$3,'MARKETPLACE_ESCROW','DEBIT',$4,'NGN'),
      ($1,$2,$5,'AVAILABLE','CREDIT',$4,'NGN')
  `, [
    transactionId, wallet.id,
    'WALLET-ORDER-REFUND-ESCROW-' + orderId,
    numericAmount.toFixed(2),
    'WALLET-ORDER-REFUND-AVAILABLE-' + orderId
  ]);
  await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [transactionId]);
  return { transactionId, reference, idempotent: false };
}

export function registerBuyerWalletMarketplaceRoutes({ app }) {
  if (!pool) return;

  app.post('/api/wallet/marketplace-orders/:id/pay', auth, async (req, res) => {
    const orderId = parseUuid(req.params.id);
    const idempotencyKey = typeof req.body?.idempotencyKey === 'string' ? req.body.idempotencyKey.trim() : '';
    const pin = typeof req.body?.pin === 'string' ? req.body.pin.trim() : '';
    if (!orderId || !/^[A-Za-z0-9_-]{8,100}$/.test(idempotencyKey) || !/^\\d{6}$/.test(pin)) {
      return res.status(400).json({ error: 'valid Marketplace order, payment reference and six-digit Wallet PIN are required' });
    }
    if (!walletSpendingApproved()) {
      return res.status(503).json({ error: 'FYNX Wallet Marketplace payments are not enabled for this provider environment yet' });
    }

    try {
      await ensureBuyerWalletSchema();
      await ensureMarketplaceSettlementSchema();
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const order = (await client.query(`
          SELECT id,buyer_id,seller_id,total_amount,buyer_total,seller_net_amount,
                 marketplace_fee,currency,status,payment_reference,payment_method
            FROM marketplace_orders
           WHERE id=$1
           FOR UPDATE
        `, [orderId])).rows[0];
        if (!order) {
          await client.query('ROLLBACK');
          return res.status(404).json({ error: 'Marketplace order not found' });
        }
        if (String(order.buyer_id) !== String(req.user.sub)) {
          await client.query('ROLLBACK');
          return res.status(403).json({ error: 'this Marketplace order does not belong to your account' });
        }

        const prior = (await client.query(`
          SELECT id,status,amount,reference,transaction_type,metadata
            FROM buyer_wallet_transactions
           WHERE idempotency_key=$1 AND user_id=$2
           LIMIT 1
        `, [idempotencyKey, req.user.sub])).rows[0];
        if (prior) {
          const priorOrderId = String(prior.metadata?.orderId || '');
          if (priorOrderId !== String(order.id) || prior.transaction_type !== 'MARKETPLACE_PAYMENT') {
            await client.query('ROLLBACK');
            return res.status(409).json({ error: 'Wallet payment reference has already been used for another operation' });
          }
          if (prior.status === 'SUCCEEDED' && order.status === 'PAID' && order.payment_method === 'FYNX_WALLET') {
            await client.query('COMMIT');
            return res.json({ status: 'PAID', orderId: String(order.id), reference: prior.reference, amount: Number(prior.amount), idempotent: true });
          }
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'previous Wallet payment request requires reconciliation before retry' });
        }

        if (order.status !== 'PAYMENT_PENDING' || String(order.payment_reference || '').trim()) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'this order already has a payment attempt or is no longer awaiting payment' });
        }
        if (String(order.currency || '').toUpperCase() !== 'NGN') {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'FYNX Wallet payments currently support NGN Marketplace orders only' });
        }
        const orderTotal = Number(order.total_amount);
        const buyerTotal = Number(order.buyer_total ?? order.total_amount);
        const sellerNet = Number(order.seller_net_amount);
        const marketplaceFee = Number(order.marketplace_fee);
        if (!Number.isFinite(orderTotal) || !Number.isFinite(buyerTotal) ||
            !Number.isFinite(sellerNet) || !Number.isFinite(marketplaceFee) ||
            orderTotal <= 0 || Math.round(buyerTotal * 100) !== Math.round(orderTotal * 100) ||
            Math.round((sellerNet + marketplaceFee) * 100) !== Math.round(orderTotal * 100)) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'Marketplace order accounting does not reconcile for Wallet payment' });
        }

        const safety = (await client.query(
          'SELECT account_status FROM fynx_account_safety WHERE user_id=$1 LIMIT 1',
          [req.user.sub]
        )).rows[0];
        if (['LOCKED','LIMITED'].includes(String(safety?.account_status || 'ACTIVE').toUpperCase())) {
          await client.query('ROLLBACK');
          return res.status(403).json({ error: 'your FYNX account is currently restricted from Marketplace payments' });
        }

        const wallet = (await client.query(`
          SELECT id,status,withdrawal_pin_hash,withdrawal_pin_failed_attempts,withdrawal_pin_locked_until
            FROM buyer_wallets
           WHERE user_id=$1 AND currency='NGN'
           FOR UPDATE
        `, [req.user.sub])).rows[0];
        if (!wallet || wallet.status !== 'ACTIVE') {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'an active NGN FYNX Wallet is required for Marketplace payment' });
        }
        if (!wallet.withdrawal_pin_hash) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'set your Wallet security PIN before paying for Marketplace orders' });
        }
        const lockedUntil = wallet.withdrawal_pin_locked_until ? new Date(wallet.withdrawal_pin_locked_until).getTime() : 0;
        if (lockedUntil > Date.now()) {
          await client.query('ROLLBACK');
          return res.status(423).json({ error: 'Wallet payments are temporarily locked after repeated incorrect PIN attempts' });
        }
        if (!(await bcrypt.compare(pin, wallet.withdrawal_pin_hash))) {
          const previous = lockedUntil > 0 && lockedUntil <= Date.now() ? 0 : Number(wallet.withdrawal_pin_failed_attempts || 0);
          const attempts = previous + 1;
          await client.query(`
            UPDATE buyer_wallets
               SET withdrawal_pin_failed_attempts=$1,
                   withdrawal_pin_locked_until=CASE WHEN $1 >= 5 THEN NOW() + INTERVAL '15 minutes' ELSE NULL END,
                   updated_at=NOW()
             WHERE id=$2
          `, [attempts, wallet.id]);
          await client.query('COMMIT');
          return res.status(403).json({
            error: attempts >= 5
              ? 'Wallet PIN attempts exceeded; Wallet money movement is locked for 15 minutes'
              : 'Wallet security PIN is incorrect'
          });
        }
        await client.query(
          'UPDATE buyer_wallets SET withdrawal_pin_failed_attempts=0,withdrawal_pin_locked_until=NULL WHERE id=$1',
          [wallet.id]
        );

        const available = (await client.query(`
          SELECT COALESCE(SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END),0)::numeric(18,2) AS balance
            FROM buyer_wallet_ledger_entries e
            JOIN buyer_wallet_transactions t ON t.id=e.transaction_id
           WHERE e.wallet_id=$1 AND e.account_code='AVAILABLE' AND t.status='SUCCEEDED'
        `, [wallet.id])).rows[0];
        if (Number(available?.balance || 0) < orderTotal) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'your cleared Wallet balance is not enough to pay for this order' });
        }

        const transactionId = crypto.randomUUID();
        const reference = 'fwmp_' + crypto.randomUUID().replace(/-/g, '');
        await client.query(`
          INSERT INTO buyer_wallet_transactions
            (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata)
          VALUES ($1,$2,$3,'MARKETPLACE_PAYMENT','PENDING',$4,0,'NGN',$5,$6,'Marketplace order payment',$7::jsonb)
        `, [
          transactionId, wallet.id, req.user.sub, orderTotal.toFixed(2), reference, idempotencyKey,
          JSON.stringify({ orderId: String(order.id), paymentMethod: 'FYNX_WALLET', provider: 'internal_wallet' })
        ]);
        await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [transactionId]);
        await client.query(`
          INSERT INTO buyer_wallet_ledger_entries
            (transaction_id,wallet_id,entry_key,account_code,side,amount,currency)
          VALUES
            ($1,$2,$3,'AVAILABLE','DEBIT',$4,'NGN'),
            ($1,$2,$5,'MARKETPLACE_ESCROW','CREDIT',$4,'NGN')
        `, [
          transactionId, wallet.id,
          'WALLET-MARKETPLACE-AVAILABLE-' + transactionId,
          orderTotal.toFixed(2),
          'WALLET-MARKETPLACE-ESCROW-' + transactionId
        ]);
        await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [transactionId]);

        await client.query(`
          UPDATE marketplace_orders
             SET status='PAID',payment_method='FYNX_WALLET',payment_reference=$2,
                 payment_provider_fee=0,updated_at=NOW()
           WHERE id=$1 AND status='PAYMENT_PENDING'
        `, [order.id, reference]);
        await client.query(`
          INSERT INTO marketplace_order_events
            (order_id,actor_id,event_type,from_status,to_status,metadata)
          VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)
        `, [
          order.id, req.user.sub,
          JSON.stringify({
            reference,
            provider: 'fynx_wallet',
            source: 'wallet',
            amount: orderTotal,
            currency: 'NGN',
            providerFee: 0,
            walletTransactionId: transactionId
          })
        ]);
        await client.query('COMMIT');
        return res.status(200).json({
          status: 'PAID',
          orderId: String(order.id),
          reference,
          amount: orderTotal,
          currency: 'NGN',
          walletTransactionId: transactionId
        });
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        if (error?.code === '23505') return res.status(409).json({ error: 'Wallet payment was already submitted; refresh the order to confirm its status' });
        throw error;
      } finally {
        client.release();
      }
    } catch (error) {
      console.error('buyer wallet Marketplace payment', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500)
        .json({ error: 'Marketplace Wallet payment could not be completed' });
    }
  });
}

export async function closeBuyerWalletMarketplacePool() {
  if (pool) await pool.end();
}
