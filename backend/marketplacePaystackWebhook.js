import crypto from 'node:crypto';
import { confirmMarketplacePayment } from './marketplacePaymentState.js';

function safeEqualHex(actual, expected) {
  if (!actual || !expected) return false;
  const actualBuffer = Buffer.from(actual, 'hex');
  const expectedBuffer = Buffer.from(expected, 'hex');
  return actualBuffer.length === expectedBuffer.length && actualBuffer.length > 0 && crypto.timingSafeEqual(actualBuffer, expectedBuffer);
}

function amountSubunit(amount, currency) {
  const normalized = String(currency || '').trim().toUpperCase();
  if (!['NGN', 'USD'].includes(normalized)) return null;
  const value = Number(amount);
  if (!Number.isFinite(value) || value <= 0) return null;
  return Math.round(value * 100);
}

async function reconcileRefundEvent(client, eventName, refund) {
  const transactionReference = typeof refund?.transaction_reference === 'string' ? refund.transaction_reference.trim() : '';
  if (!transactionReference) return false;
  const operationResult = await client.query(`
    SELECT f.id,f.order_id,f.status,f.amount,f.currency,f.metadata,o.payment_reference,o.status AS order_status,o.quantity,o.listing_id
    FROM marketplace_financial_operations f JOIN marketplace_orders o ON o.id=f.order_id
    WHERE f.operation_type='REFUND' AND o.payment_reference=$1
    ORDER BY f.created_at DESC LIMIT 1 FOR UPDATE
  `, [transactionReference]);
  const operation = operationResult.rows[0];
  if (!operation) return false;
  const status = String(refund?.status || '').toLowerCase();
  const providerReference = refund?.refund_reference || refund?.id || null;
  const metadata = { ...(operation.metadata || {}), refundEvent: eventName, refundStatus: status, transactionReference, refundReference: providerReference };
  if (eventName === 'refund.processed') {
    await client.query(`UPDATE marketplace_financial_operations SET status='SUCCEEDED',provider_reference=COALESCE($1,provider_reference),failure_reason=NULL,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3`, [providerReference ? String(providerReference) : null, JSON.stringify(metadata), operation.id]);
    await client.query(`UPDATE marketplace_orders SET status='REFUNDED',updated_at=NOW() WHERE id=$1 AND status NOT IN ('COMPLETED','REFUNDED')`, [operation.order_id]);
    const caseId = metadata.caseId ? String(metadata.caseId) : null;
    if (caseId) await client.query(`UPDATE marketplace_protection_cases SET status='REFUNDED',updated_at=NOW() WHERE id=$1 AND status IN ('OPEN','UNDER_REVIEW')`, [caseId]);
    await client.query(`UPDATE marketplace_escrows SET status='REFUNDED',refunded_at=COALESCE(refunded_at,NOW()),updated_at=NOW() WHERE order_id=$1 AND status IN ('REFUND_PENDING','DISPUTED')`, [operation.order_id]);
    if (Number.isInteger(Number(operation.quantity)) && operation.listing_id) await client.query(`UPDATE marketplace_listings SET reserved_quantity=GREATEST(0,reserved_quantity-$1),updated_at=NOW() WHERE id=$2`, [Number(operation.quantity), operation.listing_id]);
    const escrow = (await client.query('SELECT id,amount,currency FROM marketplace_escrows WHERE order_id=$1', [operation.order_id])).rows[0];
    if (escrow) await client.query(`INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata) VALUES ($1,$2,'buyer_refund','REFUND',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`, [escrow.id, operation.order_id, escrow.amount, escrow.currency, `REFUND-${operation.order_id}`, JSON.stringify({ provider: 'paystack', reference: transactionReference, caseId })]);
  } else if (eventName === 'refund.failed') {
    await client.query(`UPDATE marketplace_financial_operations SET status='FAILED',provider_reference=COALESCE($1,provider_reference),failure_reason=$2,metadata=$3::jsonb,updated_at=NOW() WHERE id=$4`, [providerReference ? String(providerReference) : null, String(refund?.reason || refund?.message || 'Paystack refund failed').slice(0, 2000), JSON.stringify(metadata), operation.id]);
    await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE order_id=$1 AND status='REFUND_PENDING'`, [operation.order_id]);
    const caseId = metadata.caseId ? String(metadata.caseId) : null;
    if (caseId) await client.query(`UPDATE marketplace_protection_cases SET status='UNDER_REVIEW',updated_at=NOW() WHERE id=$1 AND status <> 'REFUNDED'`, [caseId]);
  } else {
    await client.query(`UPDATE marketplace_financial_operations SET status='PENDING',provider_reference=COALESCE($1,provider_reference),failure_reason=NULL,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status IN ('PENDING','FAILED')`, [providerReference ? String(providerReference) : null, JSON.stringify(metadata), operation.id]);
    await client.query(`UPDATE marketplace_escrows SET status='REFUND_PENDING',updated_at=NOW() WHERE order_id=$1 AND status IN ('DISPUTED','HELD','RELEASE_ELIGIBLE')`, [operation.order_id]);
  }
  return true;
}

async function reconcileTransferEvent(client, eventName, transfer) {
  const reference = typeof transfer?.reference === 'string' ? transfer.reference.trim() : '';
  const transferCode = typeof transfer?.transfer_code === 'string' ? transfer.transfer_code.trim() : '';
  if (!reference && !transferCode) return false;
  const operationResult = await client.query(`
    SELECT f.id,f.order_id,f.status,f.amount,f.currency,f.provider_reference,f.metadata,o.seller_id,o.status AS order_status
    FROM marketplace_financial_operations f JOIN marketplace_orders o ON o.id=f.order_id
    WHERE f.operation_type='PAYOUT_RELEASE' AND (f.provider_reference=$1 OR f.provider_reference=$2)
    ORDER BY f.created_at DESC LIMIT 1 FOR UPDATE
  `, [reference || null, transferCode || null]);
  const operation = operationResult.rows[0];
  if (!operation) return false;
  const providerStatus = String(transfer?.status || '').toLowerCase();
  const eventReference = reference || transferCode;
  const metadata = { ...(operation.metadata || {}), transferEvent: eventName, transferStatus: providerStatus, transferReference: reference || null, transferCode: transferCode || null, transferUpdatedAt: new Date().toISOString() };
  const providerAmount = Number(transfer?.amount), expectedAmount = amountSubunit(operation.amount, operation.currency), providerCurrency = String(transfer?.currency || '').toUpperCase();
  if (!(Number.isFinite(providerAmount) && expectedAmount !== null && providerAmount === expectedAmount) || providerCurrency !== String(operation.currency || '').toUpperCase()) {
    await client.query(`UPDATE marketplace_financial_operations SET status='BLOCKED',failure_reason=$1,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, ['Paystack transfer webhook amount or currency does not match payout operation', JSON.stringify({ ...metadata, providerAmount, expectedAmount, providerCurrency }), operation.id]);
    await client.query(`UPDATE marketplace_escrows SET status='DISPUTED',updated_at=NOW() WHERE order_id=$1 AND status='RELEASE_PENDING'`, [operation.order_id]);
    return true;
  }
  if (eventName === 'transfer.success') {
    await client.query(`UPDATE marketplace_financial_operations SET status='SUCCEEDED',provider_reference=COALESCE($1,provider_reference),failure_reason=NULL,metadata=$2::jsonb,updated_at=NOW() WHERE id=$3 AND status='PENDING'`, [eventReference || null, JSON.stringify(metadata), operation.id]);
    const escrow = (await client.query(`SELECT id,amount,currency,status FROM marketplace_escrows WHERE order_id=$1 FOR UPDATE`, [operation.order_id])).rows[0];
    if (escrow?.status === 'RELEASE_PENDING') {
      await client.query(`UPDATE marketplace_escrows SET status='RELEASED',released_at=COALESCE(released_at,NOW()),updated_at=NOW() WHERE id=$1 AND status='RELEASE_PENDING'`, [escrow.id]);
      await client.query(`INSERT INTO marketplace_ledger_entries (escrow_id,order_id,account,entry_type,amount,currency,idempotency_key,metadata) VALUES ($1,$2,'seller_payout','RELEASE',$3,$4,$5,$6::jsonb) ON CONFLICT (idempotency_key) DO NOTHING`, [escrow.id, operation.order_id, escrow.amount, escrow.currency, `PAYOUT-RELEASE-${operation.order_id}`, JSON.stringify({ provider: 'paystack', providerReference: eventReference, providerStatus })]);
    }
  } else if (eventName === 'transfer.failed' || eventName === 'transfer.reversed') {
    await client.query(`UPDATE marketplace_financial_operations SET status='FAILED',provider_reference=COALESCE($1,provider_reference),failure_reason=$2,metadata=$3::jsonb,updated_at=NOW() WHERE id=$4 AND status='PENDING'`, [eventReference || null, `Paystack transfer ${eventName === 'transfer.reversed' ? 'reversed' : 'failed'}`, JSON.stringify(metadata), operation.id]);
    await client.query(`UPDATE marketplace_escrows SET status='RELEASE_ELIGIBLE',updated_at=NOW() WHERE order_id=$1 AND status='RELEASE_PENDING'`, [operation.order_id]);
  }
  return true;
}

export function registerMarketplacePaystackWebhook({ app, pool }) {
  app.post('/api/marketplace/payments/paystack/webhook', async (req, res) => {
    const secret = process.env.PAYSTACK_SECRET_KEY || '';
    const signature = req.get('x-paystack-signature') || '';
    const rawBody = Buffer.isBuffer(req.rawBody) ? req.rawBody : null;
    if (!secret || !rawBody || !safeEqualHex(crypto.createHmac('sha512', secret).update(rawBody).digest('hex'), signature)) return res.status(401).json({ error: 'invalid webhook signature' });
    let event;
    try { event = JSON.parse(rawBody.toString('utf8')); } catch { return res.status(400).json({ error: 'invalid webhook payload' }); }
    const eventName = typeof event?.event === 'string' ? event.event.trim().toLowerCase() : '';
    if (eventName.startsWith('refund.')) {
      const client = await pool.connect();
      try { await client.query('BEGIN'); const matched = await reconcileRefundEvent(client, eventName, event.data || {}); await client.query('COMMIT'); return res.status(200).json({ received: true, matched, refundEvent: eventName }); }
      catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace Paystack refund webhook', error); return res.status(500).json({ error: 'refund webhook processing failed' }); }
      finally { client.release(); }
    }
    if (eventName.startsWith('transfer.')) {
      const client = await pool.connect();
      try { await client.query('BEGIN'); const matched = await reconcileTransferEvent(client, eventName, event.data || {}); await client.query('COMMIT'); return res.status(200).json({ received: true, matched, transferEvent: eventName }); }
      catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace Paystack transfer webhook', error); return res.status(500).json({ error: 'transfer webhook processing failed' }); }
      finally { client.release(); }
    }
    if (eventName !== 'charge.success') return res.status(200).json({ received: true, ignored: true });
    const transaction = event.data || {}, reference = typeof transaction.reference === 'string' ? transaction.reference.trim() : '';
    if (!reference || !/^[A-Za-z0-9_.=-]{8,100}$/.test(reference)) return res.status(400).json({ error: 'invalid payment reference' });
    const expectedAmount = amountSubunit(transaction.amount, transaction.currency), paidAmount = Number(transaction.amount), paidCurrency = String(transaction.currency || '').toUpperCase(), metadataOrderId = String(transaction.metadata?.orderId || transaction.metadata?.order_id || '');
    if (transaction.status !== 'success' || expectedAmount !== paidAmount || !metadataOrderId || !String(transaction.reference)) return res.status(400).json({ error: 'payment data does not match order' });
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const orderResult = await client.query(`SELECT id,buyer_id,total_amount,currency,status,payment_reference FROM marketplace_orders WHERE payment_reference=$1`, [reference]);
      const order = orderResult.rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(200).json({ received: true, matched: false }); }
      const orderExpectedAmount = amountSubunit(order.total_amount, order.currency);
      if (metadataOrderId !== String(order.id) || String(transaction.reference) !== String(order.payment_reference) || orderExpectedAmount !== paidAmount || paidCurrency !== String(order.currency).toUpperCase()) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'payment data does not match order' }); }
      const result = await confirmMarketplacePayment(client, { orderId: order.id, reference, paidAmount, paidCurrency, source: 'webhook' });
      await client.query('COMMIT');
      return res.status(200).json({ received: true, matched: true, status: result.status, idempotent: result.idempotent });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      if (error?.code === 'PAYMENT_DATA_MISMATCH' || error?.code === 'PAYMENT_STATE_CONFLICT') return res.status(400).json({ error: error.message });
      console.error('marketplace Paystack webhook', error); return res.status(500).json({ error: 'webhook processing failed' });
    } finally { client.release(); }
  });
}
