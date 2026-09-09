import crypto from 'node:crypto';

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
      try {
        await client.query('BEGIN');
        const matched = await reconcileRefundEvent(client, eventName, event.data || {});
        await client.query('COMMIT');
        return res.status(200).json({ received: true, matched, refundEvent: eventName });
      } catch (error) {
        try { await client.query('ROLLBACK'); } catch {}
        console.error('marketplace Paystack refund webhook', error);
        return res.status(500).json({ error: 'refund webhook processing failed' });
      } finally { client.release(); }
    }
    if (eventName !== 'charge.success') return res.status(200).json({ received: true, ignored: true });
    const transaction = event.data || {};
    const reference = typeof transaction.reference === 'string' ? transaction.reference.trim() : '';
    if (!reference || !/^[A-Za-z0-9_.=-]{8,100}$/.test(reference)) return res.status(400).json({ error: 'invalid payment reference' });
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const result = await client.query(`SELECT id,buyer_id,total_amount,currency,status,payment_reference FROM marketplace_orders WHERE payment_reference=$1 FOR UPDATE`, [reference]);
      const order = result.rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(200).json({ received: true, matched: false }); }
      const expectedAmount = amountSubunit(order.total_amount, order.currency);
      const paidAmount = Number(transaction.amount);
      const paidCurrency = String(transaction.currency || '').toUpperCase();
      const metadataOrderId = String(transaction.metadata?.orderId || transaction.metadata?.order_id || '');
      const valid = transaction.status === 'success' && expectedAmount !== null && expectedAmount === paidAmount && paidCurrency === String(order.currency).toUpperCase() && metadataOrderId === String(order.id) && String(transaction.reference) === String(order.payment_reference);
      if (!valid) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'payment data does not match order' }); }
      if (order.status === 'PAYMENT_PENDING') {
        await client.query(`UPDATE marketplace_orders SET status='PAID',updated_at=NOW() WHERE id=$1`, [order.id]);
        await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)`, [order.id, order.buyer_id, JSON.stringify({ reference, provider: 'paystack', source: 'webhook', amount: paidAmount, currency: paidCurrency })]);
      }
      await client.query('COMMIT');
      return res.status(200).json({ received: true, matched: true, status: order.status === 'PAYMENT_PENDING' ? 'PAID' : order.status });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace Paystack webhook', error);
      return res.status(500).json({ error: 'webhook processing failed' });
    } finally { client.release(); }
  });
}
