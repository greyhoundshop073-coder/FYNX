import crypto from 'node:crypto';

export function registerMarketplaceReputationRoutes({ app, pool, auth }) {
  let schemaPromise;

  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        CREATE TABLE IF NOT EXISTS marketplace_seller_reviews (
          id UUID PRIMARY KEY,
          order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
          seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
          comment TEXT NOT NULL DEFAULT '',
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          UNIQUE (order_id, buyer_id)
        );
        CREATE INDEX IF NOT EXISTS marketplace_seller_reviews_seller_idx ON marketplace_seller_reviews (seller_id, created_at DESC);
      `).catch((error) => {
        schemaPromise = undefined;
        throw error;
      });
    }
    return schemaPromise;
  };

  const parseSeller = (value) => {
    if (typeof value !== 'string') return '';
    return value.trim().toLowerCase().replace(/^@+/, '').slice(0, 32);
  };

  const reputation = async (sellerUsername) => {
    await ensureSchema();
    const result = await pool.query(`
      WITH order_stats AS (
        SELECT
          u.id AS seller_id,
          u.username,
          u.display_name,
          COUNT(o.id) FILTER (WHERE o.status = 'COMPLETED')::INTEGER AS successful_orders,
          COUNT(o.id)::INTEGER AS total_orders,
          COUNT(o.id) FILTER (WHERE o.status = 'COMPLETED' AND o.completed_at >= NOW() - INTERVAL '30 days')::INTEGER AS successful_orders_30d,
          COUNT(o.id) FILTER (WHERE o.created_at >= NOW() - INTERVAL '30 days')::INTEGER AS total_orders_30d
        FROM users u
        LEFT JOIN marketplace_orders o ON o.seller_id = u.id
        GROUP BY u.id, u.username, u.display_name
      ),
      review_stats AS (
        SELECT
          seller_id,
          COUNT(*)::INTEGER AS review_count,
          COALESCE(AVG(rating), 0)::NUMERIC(4,2) AS average_rating,
          COUNT(*) FILTER (WHERE rating >= 4)::INTEGER AS positive_reviews
        FROM marketplace_seller_reviews
        GROUP BY seller_id
      ),
      ranked AS (
        SELECT
          s.*,
          COALESCE(r.review_count, 0)::INTEGER AS review_count,
          COALESCE(r.average_rating, 0)::NUMERIC(4,2) AS average_rating,
          COALESCE(r.positive_reviews, 0)::INTEGER AS positive_reviews,
          RANK() OVER (
            ORDER BY
              s.successful_orders DESC,
              CASE WHEN s.total_orders > 0 THEN s.successful_orders::NUMERIC / s.total_orders ELSE 0 END DESC,
              s.seller_id ASC
          )::INTEGER AS seller_rank,
          COUNT(*) OVER ()::INTEGER AS seller_count
        FROM order_stats s
        LEFT JOIN review_stats r ON r.seller_id = s.seller_id
      )
      SELECT * FROM ranked WHERE username = $1
    `, [sellerUsername]);
    const row = result.rows[0];
    if (!row) return null;

    const successful = Number(row.successful_orders || 0);
    const total = Number(row.total_orders || 0);
    const successful30d = Number(row.successful_orders_30d || 0);
    const total30d = Number(row.total_orders_30d || 0);
    const reviews = Number(row.review_count || 0);
    const positive = Number(row.positive_reviews || 0);
    const completionRate = total > 0 ? (successful / total) * 100 : 0;
    const completionRate30d = total30d > 0 ? (successful30d / total30d) * 100 : 0;
    const positiveRating = reviews > 0 ? (positive / reviews) * 100 : 0;

    let tier = 'NEW SELLER';
    if (successful >= 500 && completionRate >= 95 && (reviews === 0 || positiveRating >= 95)) tier = 'ELITE SELLER';
    else if (successful >= 100 && completionRate >= 90 && (reviews === 0 || positiveRating >= 90)) tier = 'TOP SELLER';
    else if (successful >= 20 && completionRate >= 85 && (reviews === 0 || positiveRating >= 85)) tier = 'TRUSTED SELLER';
    else if (successful >= 5 && completionRate >= 80) tier = 'RISING SELLER';

    return {
      sellerId: String(row.seller_id),
      username: row.username,
      displayName: row.display_name,
      rank: Number(row.seller_rank),
      sellerCount: Number(row.seller_count),
      successfulSales: successful,
      totalOrders: total,
      completionRate: Number(completionRate.toFixed(2)),
      successfulSales30d: successful30d,
      totalOrders30d: total30d,
      completionRate30d: Number(completionRate30d.toFixed(2)),
      reviewCount: reviews,
      averageRating: Number(Number(row.average_rating || 0).toFixed(2)),
      positiveRating: Number(positiveRating.toFixed(2)),
      tier
    };
  };

  app.get('/api/marketplace/sellers/:username/reputation', auth, async (req, res) => {
    try {
      const username = parseSeller(req.params.username);
      if (!/^[a-z0-9_]{3,32}$/.test(username)) return res.status(400).json({ error: 'invalid seller username' });
      const data = await reputation(username);
      if (!data) return res.status(404).json({ error: 'seller not found' });
      return res.json({ reputation: data });
    } catch (error) {
      console.error('marketplace seller reputation', error);
      return res.status(500).json({ error: 'seller reputation lookup failed' });
    }
  });

  app.post('/api/marketplace/orders/:id/review', auth, async (req, res) => {
    try {
      await ensureSchema();
      const rating = Number(req.body?.rating);
      const comment = typeof req.body?.comment === 'string' ? req.body.comment.trim().slice(0, 1000) : '';
      if (!Number.isInteger(rating) || rating < 1 || rating > 5) return res.status(400).json({ error: 'rating must be 1-5' });
      const orderResult = await pool.query(`SELECT id, buyer_id, seller_id, status FROM marketplace_orders WHERE id = $1`, [req.params.id]);
      const order = orderResult.rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (String(order.buyer_id) !== String(req.user.sub)) return res.status(403).json({ error: 'only the buyer can review this order' });
      if (order.status !== 'COMPLETED') return res.status(409).json({ error: 'only completed orders can be reviewed' });
      const id = crypto.randomUUID();
      const result = await pool.query(`
        INSERT INTO marketplace_seller_reviews (id, order_id, seller_id, buyer_id, rating, comment)
        VALUES ($1,$2,$3,$4,$5,$6)
        ON CONFLICT (order_id, buyer_id) DO NOTHING
        RETURNING id, rating, comment, created_at
      `, [id, order.id, order.seller_id, order.buyer_id, rating, comment]);
      if (!result.rows[0]) return res.status(409).json({ error: 'this order has already been reviewed' });
      return res.status(201).json({ review: result.rows[0] });
    } catch (error) {
      console.error('marketplace seller review', error);
      return res.status(500).json({ error: 'seller review failed' });
    }
  });

  const paystackSecret = () => process.env.PAYSTACK_SECRET_KEY || '';

  const paystackRequest = async (path, options = {}) => {
    const secret = paystackSecret();
    if (!secret) throw Object.assign(new Error('PAYSTACK_SECRET_KEY is not configured'), { code: 'PAYSTACK_NOT_CONFIGURED' });
    const response = await fetch(`https://api.paystack.co${path}`, {
      ...options,
      headers: {
        Authorization: `Bearer ${secret}`,
        'Content-Type': 'application/json',
        ...(options.headers || {})
      }
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data?.status !== true) {
      const message = data?.message || `Paystack request failed (${response.status})`;
      throw Object.assign(new Error(message), { code: 'PAYSTACK_REQUEST_FAILED', status: response.status });
    }
    return data;
  };

  const parseOrderId = (value) => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;

  const amountSubunit = (amount, currency) => {
    const normalized = String(currency || '').trim().toUpperCase();
    if (!['NGN', 'USD'].includes(normalized)) return null;
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) return null;
    return Math.round(value * 100);
  };

  app.post('/api/marketplace/orders/:id/payment', auth, async (req, res) => {
    const orderId = parseOrderId(req.params.id);
    const email = typeof req.body?.email === 'string' ? req.body.email.trim().toLowerCase() : '';
    if (!orderId || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return res.status(400).json({ error: 'valid order id and customer email are required' });
    try {
      const orderResult = await pool.query(`SELECT id,buyer_id,total_amount,currency,status,payment_reference FROM marketplace_orders WHERE id=$1`, [orderId]);
      const order = orderResult.rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (String(order.buyer_id) !== String(req.user.sub)) return res.status(403).json({ error: 'only the buyer can pay this order' });
      if (order.status !== 'PAYMENT_PENDING') return res.status(409).json({ error: 'this order is not awaiting payment' });
      const amount = amountSubunit(order.total_amount, order.currency);
      if (!amount) return res.status(400).json({ error: 'unsupported marketplace payment currency or amount' });

      const reference = `FYNX-${order.id}-${Date.now()}`;
      const payload = {
        email,
        amount: String(amount),
        currency: String(order.currency).toUpperCase(),
        reference,
        metadata: {
          orderId: String(order.id),
          buyerId: String(order.buyer_id),
          purpose: 'FYNX_MARKETPLACE_ORDER'
        }
      };
      if (process.env.PAYSTACK_CALLBACK_URL) payload.callback_url = process.env.PAYSTACK_CALLBACK_URL;

      const data = await paystackRequest('/transaction/initialize', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      await pool.query('UPDATE marketplace_orders SET payment_reference=$1,updated_at=NOW() WHERE id=$2 AND buyer_id=$3 AND status=\'PAYMENT_PENDING\'', [reference, order.id, req.user.sub]);
      return res.status(201).json({
        orderId: String(order.id),
        reference,
        authorizationUrl: data.data.authorization_url,
        accessCode: data.data.access_code,
        amountSubunit: amount,
        currency: String(order.currency).toUpperCase()
      });
    } catch (error) {
      if (error?.code === 'PAYSTACK_NOT_CONFIGURED') return res.status(503).json({ error: 'marketplace payments are not configured yet' });
      console.error('marketplace payment initialize', error);
      return res.status(502).json({ error: 'payment initialization failed' });
    }
  });

  app.get('/api/marketplace/payments/verify/:reference', auth, async (req, res) => {
    const reference = typeof req.params.reference === 'string' ? req.params.reference.trim() : '';
    if (!/^[A-Za-z0-9_.=-]{8,100}$/.test(reference)) return res.status(400).json({ error: 'invalid payment reference' });
    try {
      const orderResult = await pool.query(`SELECT id,buyer_id,listing_id,quantity,total_amount,currency,status,payment_reference FROM marketplace_orders WHERE payment_reference=$1`, [reference]);
      const order = orderResult.rows[0];
      if (!order) return res.status(404).json({ error: 'payment order not found' });
      if (String(order.buyer_id) !== String(req.user.sub)) return res.status(403).json({ error: 'payment unavailable' });

      const data = await paystackRequest(`/transaction/verify/${encodeURIComponent(reference)}`);
      const transaction = data.data || {};
      const expectedAmount = amountSubunit(order.total_amount, order.currency);
      const paidAmount = Number(transaction.amount);
      const paidCurrency = String(transaction.currency || '').toUpperCase();
      const metadataOrderId = String(transaction.metadata?.orderId || transaction.metadata?.order_id || '');
      const valid = transaction.status === 'success' && expectedAmount === paidAmount && paidCurrency === String(order.currency).toUpperCase() && metadataOrderId === String(order.id);
      if (!valid) return res.status(409).json({ error: 'payment could not be verified', status: transaction.status || 'unknown' });

      if (order.status === 'PAYMENT_PENDING') {
        const client = await pool.connect();
        try {
          await client.query('BEGIN');
          const locked = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [order.id])).rows[0];
          if (!locked) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
          if (String(locked.buyer_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'payment unavailable' }); }
          if (locked.status === 'PAYMENT_PENDING') {
            await client.query(`UPDATE marketplace_orders SET status='PAID',updated_at=NOW() WHERE id=$1`, [order.id]);
            await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'PAYMENT_CONFIRMED',$3,'PAID',$4::jsonb)`, [order.id, req.user.sub, locked.status, JSON.stringify({ reference, provider: 'paystack', amount: paidAmount, currency: paidCurrency })]);
          }
          await client.query('COMMIT');
        } catch (error) {
          try { await client.query('ROLLBACK'); } catch {}
          throw error;
        } finally { client.release(); }
      }
      const updated = (await pool.query('SELECT * FROM marketplace_orders WHERE id=$1', [order.id])).rows[0];
      return res.json({ verified: true, order: { id: String(updated.id), status: updated.status, paymentReference: updated.payment_reference, totalAmount: Number(updated.total_amount), currency: updated.currency } });
    } catch (error) {
      if (error?.code === 'PAYSTACK_NOT_CONFIGURED') return res.status(503).json({ error: 'marketplace payments are not configured yet' });
      console.error('marketplace payment verify', error);
      return res.status(502).json({ error: 'payment verification failed' });
    }
  });
}