function amountSubunit(amount, currency) {
  const normalized = String(currency || '').trim().toUpperCase();
  if (!['NGN', 'USD'].includes(normalized)) return null;
  const value = Number(amount);
  if (!Number.isFinite(value) || value <= 0) return null;
  return Math.round(value * 100);
}

export async function confirmMarketplacePayment(client, {
  orderId,
  buyerId = null,
  reference,
  paidAmount,
  paidCurrency,
  source
}) {
  const result = await client.query(`
    SELECT id,buyer_id,total_amount,currency,status,payment_reference
    FROM marketplace_orders
    WHERE id=$1
    FOR UPDATE
  `, [orderId]);
  const order = result.rows[0];
  if (!order) throw Object.assign(new Error('payment order not found'), { code: 'PAYMENT_ORDER_NOT_FOUND' });
  if (buyerId !== null && String(order.buyer_id) !== String(buyerId)) {
    throw Object.assign(new Error('payment unavailable'), { code: 'PAYMENT_FORBIDDEN' });
  }

  const expectedAmount = amountSubunit(order.total_amount, order.currency);
  const normalizedCurrency = String(paidCurrency || '').toUpperCase();
  const valid = Boolean(reference)
    && String(reference) === String(order.payment_reference)
    && expectedAmount !== null
    && Number(paidAmount) === expectedAmount
    && normalizedCurrency === String(order.currency || '').toUpperCase();
  if (!valid) throw Object.assign(new Error('payment data does not match order'), { code: 'PAYMENT_DATA_MISMATCH' });

  if (order.status === 'PAYMENT_PENDING') {
    await client.query(`
      UPDATE marketplace_orders
      SET status='PAID',updated_at=NOW()
      WHERE id=$1 AND status='PAYMENT_PENDING'
    `, [order.id]);
    await client.query(`
      INSERT INTO marketplace_order_events
        (order_id,actor_id,event_type,from_status,to_status,metadata)
      VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)
    `, [order.id, order.buyer_id, JSON.stringify({
      reference,
      provider: 'paystack',
      source: source || 'unknown',
      amount: Number(paidAmount),
      currency: normalizedCurrency
    })]);
    return { status: 'PAID', idempotent: false };
  }

  if (order.status === 'PAID') return { status: 'PAID', idempotent: true };
  throw Object.assign(new Error(`payment cannot confirm order from status ${order.status}`), { code: 'PAYMENT_STATE_CONFLICT' });
}
