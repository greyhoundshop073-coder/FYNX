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
      SELECT
        u.id,
        u.username,
        u.display_name,
        COUNT(o.id) FILTER (WHERE o.status = 'COMPLETED')::INTEGER AS successful_orders,
        COUNT(o.id) FILTER (WHERE o.status IN ('PAYMENT_PENDING','PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED','CANCELLED','REFUNDED'))::INTEGER AS total_orders,
        COUNT(o.id) FILTER (WHERE o.status = 'COMPLETED' AND o.completed_at >= NOW() - INTERVAL '30 days')::INTEGER AS successful_orders_30d,
        COUNT(o.id) FILTER (WHERE o.status IN ('PAYMENT_PENDING','PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED','CANCELLED','REFUNDED') AND o.created_at >= NOW() - INTERVAL '30 days')::INTEGER AS total_orders_30d,
        COUNT(r.id)::INTEGER AS review_count,
        COALESCE(AVG(r.rating), 0)::NUMERIC(4,2) AS average_rating,
        COUNT(r.id) FILTER (WHERE r.rating >= 4)::INTEGER AS positive_reviews
      FROM users u
      LEFT JOIN marketplace_orders o ON o.seller_id = u.id
      LEFT JOIN marketplace_seller_reviews r ON r.seller_id = u.id
      WHERE u.username = $1
      GROUP BY u.id, u.username, u.display_name
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
    if (successful >= 500 && completionRate >= 95 && positiveRating >= 95) tier = 'ELITE SELLER';
    else if (successful >= 100 && completionRate >= 90 && positiveRating >= 90) tier = 'TOP SELLER';
    else if (successful >= 20 && completionRate >= 85 && positiveRating >= 85) tier = 'TRUSTED SELLER';
    else if (successful >= 5 && completionRate >= 80) tier = 'RISING SELLER';

    return {
      sellerId: String(row.id),
      username: row.username,
      displayName: row.display_name,
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
}
