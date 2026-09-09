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

export function registerMarketplacePaystackWebhook({ app, pool }) {
  app.post('/api/marketplace/payments/paystack/webhook', async (req, res) => {
    const secret = process.env.PAYSTACK_SECRET_KEY || '';
    const signature = req.get('x-paystack-signature') || '';
    const rawBody = Buffer.isBuffer(req.rawBody) ? req.rawBody : null;
    if (!secret || !rawBody || !safeEqualHex(crypto.createHmac('sha512', secret).update(rawBody).digest('hex'), signature)) {
      return res.status(401).json({ error: 'invalid webhook signature' });
    }

    let event;
    try {
      event = JSON.parse(rawBody.toString('utf8'));
    } catch {
      return res.status(400).json({ error: 'invalid webhook payload' });
    }

    if (event?.event !== 'charge.success') return res.status(200).json({ received: true, ignored: true });

    const transaction = event.data || {};
    const reference = typeof transaction.reference === 'string' ? transaction.reference.trim() : '';
    if (!reference || !/^[A-Za-z0-9_.=-]{8,100}$/.test(reference)) return res.status(400).json({ error: 'invalid payment reference' });

    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const result = await client.query(`
        SELECT id,buyer_id,total_amount,currency,status,payment_reference
        FROM marketplace_orders
        WHERE payment_reference=$1
        FOR UPDATE
      `, [reference]);
      const order = result.rows[0];
      if (!order) {
        await client.query('ROLLBACK');
        return res.status(200).json({ received: true, matched: false });
      }

      const expectedAmount = amountSubunit(order.total_amount, order.currency);
      const paidAmount = Number(transaction.amount);
      const paidCurrency = String(transaction.currency || '').toUpperCase();
      const metadataOrderId = String(transaction.metadata?.orderId || transaction.metadata?.order_id || '');
      const valid = transaction.status === 'success'
        && expectedAmount !== null
        && expectedAmount === paidAmount
        && paidCurrency === String(order.currency).toUpperCase()
        && metadataOrderId === String(order.id)
        && String(transaction.reference) === String(order.payment_reference);

      if (!valid) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: 'payment data does not match order' });
      }

      if (order.status === 'PAYMENT_PENDING') {
        await client.query(`UPDATE marketplace_orders SET status='PAID',updated_at=NOW() WHERE id=$1`, [order.id]);
        await client.query(`
          INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata)
          VALUES ($1,$2,'PAYMENT_CONFIRMED','PAYMENT_PENDING','PAID',$3::jsonb)
        `, [order.id, order.buyer_id, JSON.stringify({ reference, provider: 'paystack', source: 'webhook', amount: paidAmount, currency: paidCurrency })]);
      }

      await client.query('COMMIT');
      return res.status(200).json({ received: true, matched: true, status: order.status === 'PAYMENT_PENDING' ? 'PAID' : order.status });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace Paystack webhook', error);
      return res.status(500).json({ error: 'webhook processing failed' });
    } finally {
      client.release();
    }
  });
}
