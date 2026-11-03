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

export function walletMarketplaceSpendingApproved(env = process.env) {
  // A test-mode balance is simulated and must never be debited for a real order.
  if (env.FYNX_WALLET_TEST_MODE_ENABLED === 'true') return false;
  return env.FYNX_WALLET_PROVIDER_APPROVED === 'true' &&
    env.FYNX_WALLET_CUSTODY_APPROVED === 'true' &&
    env.FYNX_WALLET_COMPLIANCE_APPROVED === 'true' &&
    env.FYNX_WALLET_SPENDING_ENABLED === 'true' &&
    Boolean(env.PAYSTACK_SECRET_KEY);
}

export function isValidWalletMarketplacePin(value) {
  return typeof value === 'string' && /^\d{6}$/.test(value);
}

const walletSpendingApproved = () => walletMarketplaceSpendingApproved();


export function buildWalletMarketplaceCheckoutAllocations(group, children) {
  const invalid = (message) => { throw Object.assign(new Error(message), { code: 'WALLET_CHECKOUT_GROUP_INVALID' }); };
  const cents = (value) => {
    if (value == null || value === '') return null;
    const numeric = Number(value);
    return Number.isFinite(numeric) && numeric >= 0 ? Math.round(numeric * 100) : null;
  };
  const currency = String(group?.currency || '').trim().toUpperCase();
  if (currency !== 'NGN') invalid('FYNX Wallet multi-product payments currently support NGN only');
  if (String(group?.status || '').toUpperCase() !== 'PAYMENT_PENDING') invalid('checkout group is not awaiting payment');
  if (!Array.isArray(children) || children.length < 1 || children.length > 20) invalid('checkout group must contain between 1 and 20 orders');
  const buyerId = String(group?.buyer_id || '');
  const groupSubtotal = cents(group?.subtotal);
  const groupDelivery = cents(group?.delivery_fee);
  const groupFee = cents(group?.marketplace_fee);
  const groupTotal = cents(group?.buyer_total);
  if (!buyerId || ![groupSubtotal, groupDelivery, groupFee, groupTotal].every(Number.isSafeInteger) || groupTotal <= 0) invalid('checkout group totals are invalid');
  const seenOrders = new Set();
  const lines = children.map((child) => {
    const orderId = String(child?.id || '');
    const childBuyerId = String(child?.buyer_id || '');
    const childCurrency = String(child?.currency || '').trim().toUpperCase();
    const status = String(child?.status || '').trim().toUpperCase();
    if (!orderId || seenOrders.has(orderId) || childBuyerId !== buyerId || childCurrency !== 'NGN' ||
        status !== 'PAYMENT_PENDING' || String(child?.payment_reference || '').trim()) invalid('checkout child order identity, owner, currency or payment state is invalid');
    seenOrders.add(orderId);
    const productSubtotal = cents(child.product_subtotal);
    const deliveryFee = cents(child.delivery_fee);
    const marketplaceFee = cents(child.marketplace_fee);
    const buyerFee = cents(child.marketplace_fee_buyer);
    const sellerFee = cents(child.marketplace_fee_seller);
    const sellerNet = cents(child.seller_net_amount);
    const buyerTotal = cents(child.buyer_total);
    const total = cents(child.total_amount);
    if (![productSubtotal, deliveryFee, marketplaceFee, buyerFee, sellerFee, sellerNet, buyerTotal, total].every(Number.isSafeInteger) ||
        productSubtotal <= 0 || total <= 0 || buyerTotal !== total ||
        buyerFee + sellerFee !== marketplaceFee || sellerNet + marketplaceFee !== total) invalid('checkout child order financial values do not reconcile');
    return { orderId, amountCents: total, productSubtotalCents: productSubtotal, deliveryFeeCents: deliveryFee, buyerFeeCents: buyerFee };
  });
  const sum = (key) => lines.reduce((total, line) => total + line[key], 0);
  if (sum('productSubtotalCents') !== groupSubtotal || sum('deliveryFeeCents') !== groupDelivery ||
      sum('buyerFeeCents') !== groupFee || sum('amountCents') !== groupTotal) invalid('checkout group totals do not equal its protected child orders');
  return { currency, totalCents: groupTotal, children: lines };
}

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

  const order = (await client.query(`
    SELECT buyer_id,payment_method,status,total_amount,currency
      FROM marketplace_orders
     WHERE id=$1
     FOR UPDATE
  `, [orderId])).rows[0];
  if (!order || String(order.buyer_id) !== String(buyerId) ||
      String(order.payment_method || '').toUpperCase() !== 'FYNX_WALLET' ||
      String(order.currency || '').toUpperCase() !== normalizedCurrency ||
      Math.round(Number(order.total_amount) * 100) !== Math.round(numericAmount * 100)) {
    throw Object.assign(new Error('Marketplace order does not match the Wallet refund owner or amount'), { code: 'WALLET_REFUND_ORDER_MISMATCH' });
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

  const originalPayment = (await client.query(`
    SELECT id,amount,currency
      FROM buyer_wallet_transactions
     WHERE wallet_id=$1 AND user_id=$2 AND transaction_type='MARKETPLACE_PAYMENT'
       AND status='SUCCEEDED' AND metadata->>'orderId'=$3
     FOR UPDATE
  `, [wallet.id, buyerId, String(orderId)])).rows[0];
  if (!originalPayment || String(originalPayment.currency).toUpperCase() !== normalizedCurrency ||
      Math.round(Number(originalPayment.amount) * 100) !== Math.round(numericAmount * 100)) {
    throw Object.assign(new Error('Original Wallet payment does not reconcile with this Marketplace order'), { code: 'WALLET_REFUND_PAYMENT_MISMATCH' });
  }

  const escrowBalance = (await client.query(`
    SELECT COALESCE(SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END),0)::numeric(18,2) AS balance
      FROM buyer_wallet_ledger_entries e
     WHERE e.wallet_id=$1 AND e.transaction_id=$2 AND e.account_code='MARKETPLACE_ESCROW'
  `, [wallet.id, originalPayment.id])).rows[0];
  if (Number(escrowBalance?.balance || 0) !== numericAmount) {
    throw Object.assign(new Error('Wallet Marketplace order escrow does not contain the exact refundable amount'), { code: 'WALLET_REFUND_ESCROW_MISMATCH' });
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

export async function settleBuyerWalletMarketplaceOrder(client, orderId) {
  const order = (await client.query(`
    SELECT id,buyer_id,payment_method,total_amount,currency
      FROM marketplace_orders
     WHERE id=$1
     FOR UPDATE
  `, [orderId])).rows[0];
  if (!order || String(order.payment_method || '').toUpperCase() !== 'FYNX_WALLET') {
    return { handled: false };
  }
  const amount = Number(order.total_amount);
  const currency = String(order.currency || '').trim().toUpperCase();
  if (!Number.isFinite(amount) || amount <= 0 || currency !== 'NGN') {
    throw Object.assign(new Error('Wallet-funded Marketplace order amount or currency is invalid'), { code: 'WALLET_ORDER_SETTLEMENT_INVALID' });
  }

  const idempotencyKey = `WALLET-MARKETPLACE-SETTLE-${String(order.id)}`;
  const existing = (await client.query(
    'SELECT id,status,reference FROM buyer_wallet_transactions WHERE idempotency_key=$1 FOR UPDATE',
    [idempotencyKey]
  )).rows[0];
  if (existing) {
    if (existing.status !== 'SUCCEEDED') {
      throw Object.assign(new Error('Wallet Marketplace settlement requires reconciliation'), { code: 'WALLET_ORDER_SETTLEMENT_PENDING' });
    }
    return { handled: true, idempotent: true, transactionId: String(existing.id) };
  }

  // A persisted Wallet payment proves these tables already exist. Avoid running
  // schema DDL on a separate pool connection inside the seller-payout transaction.
  const wallet = (await client.query(`
    SELECT id,status
      FROM buyer_wallets
     WHERE user_id=$1 AND currency='NGN'
     FOR UPDATE
  `, [order.buyer_id])).rows[0];
  if (!wallet) throw Object.assign(new Error('Buyer Wallet is missing for a Wallet-funded order'), { code: 'WALLET_ORDER_SETTLEMENT_WALLET_MISSING' });

  const originalPayment = (await client.query(`
    SELECT id,amount,currency
      FROM buyer_wallet_transactions
     WHERE wallet_id=$1 AND user_id=$2 AND transaction_type='MARKETPLACE_PAYMENT'
       AND status='SUCCEEDED' AND metadata->>'orderId'=$3
     FOR UPDATE
  `, [wallet.id, order.buyer_id, String(order.id)])).rows[0];
  if (!originalPayment || String(originalPayment.currency).toUpperCase() !== currency ||
      Math.round(Number(originalPayment.amount) * 100) !== Math.round(amount * 100)) {
    throw Object.assign(new Error('Original buyer Wallet payment does not reconcile with seller payout order'), { code: 'WALLET_ORDER_SETTLEMENT_PAYMENT_MISMATCH' });
  }
  const escrowBalance = (await client.query(`
    SELECT COALESCE(SUM(CASE WHEN side='CREDIT' THEN amount ELSE -amount END),0)::numeric(18,2) AS balance
      FROM buyer_wallet_ledger_entries
     WHERE wallet_id=$1 AND transaction_id=$2 AND account_code='MARKETPLACE_ESCROW'
  `, [wallet.id, originalPayment.id])).rows[0];
  if (Number(escrowBalance?.balance || 0) !== amount) {
    throw Object.assign(new Error('Buyer Wallet escrow does not reconcile to the exact Marketplace order payout'), { code: 'WALLET_ORDER_SETTLEMENT_ESCROW_MISMATCH' });
  }

  const transactionId = crypto.randomUUID();
  const reference = `FYNX-WALLET-ORDER-SETTLED-${String(order.id)}`;
  await client.query(`
    INSERT INTO buyer_wallet_transactions
      (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata)
    VALUES ($1,$2,$3,'ADJUSTMENT','PENDING',$4,0,'NGN',$5,$6,'Wallet-funded Marketplace order settled',$7::jsonb)
  `, [
    transactionId, wallet.id, order.buyer_id, amount.toFixed(2), reference, idempotencyKey,
    JSON.stringify({ internal: true, orderId: String(order.id), source: 'marketplace_seller_payout', paymentMethod: 'FYNX_WALLET' })
  ]);
  await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [transactionId]);
  await client.query(`
    INSERT INTO buyer_wallet_ledger_entries
      (transaction_id,wallet_id,entry_key,account_code,side,amount,currency)
    VALUES
      ($1,$2,$3,'MARKETPLACE_ESCROW','DEBIT',$4,'NGN'),
      ($1,$2,$5,'EXTERNAL_BANK','CREDIT',$4,'NGN')
  `, [
    transactionId, wallet.id,
    'WALLET-MARKETPLACE-SETTLED-ESCROW-' + order.id,
    amount.toFixed(2),
    'WALLET-MARKETPLACE-SETTLED-EXTERNAL-' + order.id
  ]);
  await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [transactionId]);
  return { handled: true, idempotent: false, transactionId };
}

export function registerBuyerWalletMarketplaceRoutes({ app }) {
  app.post('/api/wallet/marketplace-orders/:id/pay', auth, async (req, res) => {
    const orderId = parseUuid(req.params.id);
    const idempotencyKey = typeof req.body?.idempotencyKey === 'string' ? req.body.idempotencyKey.trim() : '';
    const pin = typeof req.body?.pin === 'string' ? req.body.pin.trim() : '';
    if (!orderId || !/^[A-Za-z0-9_-]{8,100}$/.test(idempotencyKey) || !isValidWalletMarketplacePin(pin)) {
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

  app.post('/api/wallet/marketplace-checkout-groups/:id/pay', auth, async (req, res) => {
    const checkoutId = parseUuid(req.params.id);
    const idempotencyKey = typeof req.body?.idempotencyKey === 'string' ? req.body.idempotencyKey.trim() : '';
    const pin = typeof req.body?.pin === 'string' ? req.body.pin.trim() : '';
    if (!checkoutId || !/^[A-Za-z0-9_-]{8,100}$/.test(idempotencyKey) || !isValidWalletMarketplacePin(pin)) {
      return res.status(400).json({ error: 'valid checkout id, payment reference and six-digit Wallet PIN are required' });
    }
    if (!walletSpendingApproved()) return res.status(503).json({ error: 'FYNX Wallet Marketplace payments are not enabled for this provider environment yet' });
    try {
      await ensureBuyerWalletSchema();
      await ensureMarketplaceSettlementSchema();
      await pool.query("ALTER TABLE marketplace_checkout_groups ADD COLUMN IF NOT EXISTS payment_method TEXT, ADD COLUMN IF NOT EXISTS wallet_payment_reference TEXT, ADD COLUMN IF NOT EXISTS wallet_idempotency_key TEXT");
      await pool.query("CREATE UNIQUE INDEX IF NOT EXISTS marketplace_checkout_groups_wallet_reference_idx ON marketplace_checkout_groups(wallet_payment_reference) WHERE wallet_payment_reference IS NOT NULL");
      await pool.query("CREATE UNIQUE INDEX IF NOT EXISTS marketplace_checkout_groups_wallet_idempotency_idx ON marketplace_checkout_groups(buyer_id,wallet_idempotency_key) WHERE wallet_idempotency_key IS NOT NULL");
      const client = await pool.connect();
      try {
        await client.query('BEGIN');
        const group = (await client.query(
          "SELECT id,buyer_id,subtotal,delivery_fee,marketplace_fee,buyer_total,currency,status,payment_reference,payment_method,wallet_payment_reference,wallet_idempotency_key FROM marketplace_checkout_groups WHERE id=$1 AND buyer_id=$2 FOR UPDATE",
          [checkoutId, req.user.sub]
        )).rows[0];
        if (!group) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'checkout group not found' }); }
        const children = (await client.query(
          "SELECT o.id,o.buyer_id,o.seller_id,o.product_subtotal,o.delivery_fee,o.marketplace_fee,o.marketplace_fee_buyer,o.marketplace_fee_seller,o.seller_net_amount,o.buyer_total,o.total_amount,o.currency,o.status,o.payment_method,o.payment_reference FROM marketplace_checkout_group_items gi JOIN marketplace_orders o ON o.id=gi.order_id WHERE gi.checkout_id=$1 ORDER BY o.id ASC FOR UPDATE OF o",
          [group.id]
        )).rows;
        if (!children.length) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'checkout group has no protected orders' }); }
        if (group.status === 'PAID') {
          const alreadyWalletPaid = group.payment_method === 'FYNX_WALLET' && Boolean(group.wallet_payment_reference) &&
            children.every((child) => child.status === 'PAID' && child.payment_method === 'FYNX_WALLET');
          if (!alreadyWalletPaid) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'checkout group has already been paid by another method or requires reconciliation' }); }
          const posted = (await client.query(
            "SELECT COUNT(*)::int AS transaction_count,COUNT(DISTINCT metadata->>'orderId')::int AS order_count,COALESCE(SUM(amount),0)::numeric(18,2) AS total FROM buyer_wallet_transactions WHERE user_id=$1 AND transaction_type='MARKETPLACE_PAYMENT' AND status='SUCCEEDED' AND metadata->>'checkoutGroupId'=$2 AND metadata->>'paymentMethod'='FYNX_WALLET'",
            [req.user.sub, String(group.id)]
          )).rows[0];
          if (Number(posted.transaction_count) !== children.length || Number(posted.order_count) !== children.length ||
              Math.round(Number(posted.total) * 100) !== Math.round(Number(group.buyer_total) * 100)) {
            await client.query('ROLLBACK');
            return res.status(409).json({ error: 'previous Wallet checkout payment requires reconciliation before retry' });
          }
          await client.query('COMMIT');
          return res.json({
            status: 'PAID', checkoutId: String(group.id), reference: String(group.wallet_payment_reference),
            amount: Number(group.buyer_total), currency: 'NGN', childOrderCount: children.length, idempotent: true
          });
        }
        if (group.status !== 'PAYMENT_PENDING') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'checkout group is no longer awaiting payment' }); }
        if (String(group.payment_reference || '').trim() || group.wallet_payment_reference || group.wallet_idempotency_key) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'another payment attempt already exists for this checkout group' });
        }
        let allocation;
        try { allocation = buildWalletMarketplaceCheckoutAllocations(group, children); }
        catch {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'checkout group accounting, currency or child-order state does not reconcile for Wallet payment' });
        }
        const safety = (await client.query('SELECT account_status FROM fynx_account_safety WHERE user_id=$1 LIMIT 1', [req.user.sub])).rows[0];
        if (['LOCKED', 'LIMITED'].includes(String(safety?.account_status || 'ACTIVE').toUpperCase())) {
          await client.query('ROLLBACK');
          return res.status(403).json({ error: 'your FYNX account is currently restricted from Marketplace payments' });
        }
        const wallet = (await client.query(
          "SELECT id,status,withdrawal_pin_hash,withdrawal_pin_failed_attempts,withdrawal_pin_locked_until FROM buyer_wallets WHERE user_id=$1 AND currency='NGN' FOR UPDATE",
          [req.user.sub]
        )).rows[0];
        if (!wallet || wallet.status !== 'ACTIVE') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'an active NGN FYNX Wallet is required for Marketplace checkout' }); }
        if (!wallet.withdrawal_pin_hash) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'set your Wallet security PIN before paying for Marketplace checkout' }); }
        const lockedUntil = wallet.withdrawal_pin_locked_until ? new Date(wallet.withdrawal_pin_locked_until).getTime() : 0;
        if (lockedUntil > Date.now()) { await client.query('ROLLBACK'); return res.status(423).json({ error: 'Wallet payments are temporarily locked after repeated incorrect PIN attempts' }); }
        if (!(await bcrypt.compare(pin, wallet.withdrawal_pin_hash))) {
          const previous = lockedUntil > 0 && lockedUntil <= Date.now() ? 0 : Number(wallet.withdrawal_pin_failed_attempts || 0);
          const attempts = previous + 1;
          await client.query(
            "UPDATE buyer_wallets SET withdrawal_pin_failed_attempts=$1,withdrawal_pin_locked_until=CASE WHEN $1 >= 5 THEN NOW() + INTERVAL '15 minutes' ELSE NULL END,updated_at=NOW() WHERE id=$2",
            [attempts, wallet.id]
          );
          await client.query('COMMIT');
          return res.status(403).json({ error: attempts >= 5 ? 'Wallet PIN attempts exceeded; Wallet money movement is locked for 15 minutes' : 'Wallet security PIN is incorrect' });
        }
        await client.query('UPDATE buyer_wallets SET withdrawal_pin_failed_attempts=0,withdrawal_pin_locked_until=NULL WHERE id=$1', [wallet.id]);
        const available = (await client.query(
          "SELECT COALESCE(SUM(CASE WHEN e.side='CREDIT' THEN e.amount ELSE -e.amount END),0)::numeric(18,2) AS balance FROM buyer_wallet_ledger_entries e JOIN buyer_wallet_transactions t ON t.id=e.transaction_id WHERE e.wallet_id=$1 AND e.account_code='AVAILABLE' AND t.status='SUCCEEDED'",
          [wallet.id]
        )).rows[0];
        if (Math.round(Number(available?.balance || 0) * 100) < allocation.totalCents) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'your cleared Wallet balance is not enough to pay for this cart' });
        }
        const groupReference = 'fwmpg_' + crypto.randomUUID().replace(/-/g, '');
        for (const child of allocation.children) {
          const transactionId = crypto.randomUUID();
          const childReference = 'fwmp_' + crypto.randomUUID().replace(/-/g, '');
          const childIdempotencyKey = 'FWX-GROUP-' + String(group.id) + '-' + child.orderId;
          const amount = (child.amountCents / 100).toFixed(2);
          await client.query(
            "INSERT INTO buyer_wallet_transactions (id,wallet_id,user_id,transaction_type,status,amount,fee,currency,reference,idempotency_key,description,metadata) VALUES ($1,$2,$3,'MARKETPLACE_PAYMENT','PENDING',$4,0,'NGN',$5,$6,'Marketplace cart order payment',$7::jsonb)",
            [transactionId, wallet.id, req.user.sub, amount, childReference, childIdempotencyKey, JSON.stringify({
              orderId: child.orderId, checkoutGroupId: String(group.id), paymentMethod: 'FYNX_WALLET', provider: 'internal_wallet'
            })]
          );
          await client.query("UPDATE buyer_wallet_transactions SET status='PROCESSING' WHERE id=$1", [transactionId]);
          await client.query(
            "INSERT INTO buyer_wallet_ledger_entries (transaction_id,wallet_id,entry_key,account_code,side,amount,currency) VALUES ($1,$2,$3,'AVAILABLE','DEBIT',$4,'NGN'),($1,$2,$5,'MARKETPLACE_ESCROW','CREDIT',$4,'NGN')",
            [transactionId, wallet.id, 'WALLET-MARKETPLACE-GROUP-AVAILABLE-' + transactionId, amount, 'WALLET-MARKETPLACE-GROUP-ESCROW-' + transactionId]
          );
          await client.query("UPDATE buyer_wallet_transactions SET status='SUCCEEDED' WHERE id=$1", [transactionId]);
          const paidOrder = await client.query(
            "UPDATE marketplace_orders SET status='PAID',payment_method='FYNX_WALLET',payment_reference=$2,payment_provider_fee=0,updated_at=NOW() WHERE id=$1 AND buyer_id=$3 AND status='PAYMENT_PENDING' AND COALESCE(payment_reference,'')='' RETURNING id",
            [child.orderId, childReference, req.user.sub]
          );
          if (!paidOrder.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'one of the protected cart orders is no longer awaiting payment' }); }
          await client.query(
            "INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)",
            [child.orderId, req.user.sub, JSON.stringify({
              reference: childReference, provider: 'fynx_wallet', source: 'wallet-checkout-group',
              checkoutGroupId: String(group.id), amount: child.amountCents / 100, currency: 'NGN',
              providerFee: 0, walletTransactionId: transactionId
            })]
          );
        }
        const paidGroup = await client.query(
          "UPDATE marketplace_checkout_groups SET status='PAID',payment_method='FYNX_WALLET',wallet_payment_reference=$2,wallet_idempotency_key=$3,updated_at=NOW() WHERE id=$1 AND buyer_id=$4 AND status='PAYMENT_PENDING' AND COALESCE(payment_reference,'')='' AND wallet_payment_reference IS NULL RETURNING id",
          [group.id, groupReference, idempotencyKey, req.user.sub]
        );
        if (!paidGroup.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'Wallet checkout could not be committed; refresh the checkout before retrying' }); }
        await client.query('COMMIT');
        return res.status(200).json({
          status: 'PAID', checkoutId: String(group.id), reference: groupReference,
          amount: allocation.totalCents / 100, currency: 'NGN', childOrderCount: allocation.children.length, idempotent: false
        });
      } catch (error) {
        await client.query('ROLLBACK').catch(() => {});
        if (error?.code === '23505') return res.status(409).json({ error: 'Wallet checkout payment was already submitted; refresh the cart to confirm its status' });
        throw error;
      } finally { client.release(); }
    } catch (error) {
      console.error('buyer Wallet Marketplace checkout-group payment', error);
      return res.status(error?.code === 'DATABASE_NOT_CONFIGURED' ? 503 : 500).json({ error: 'Marketplace Wallet checkout could not be completed' });
    }
  });

}

export async function closeBuyerWalletMarketplacePool() {
  if (pool) await pool.end();
}
