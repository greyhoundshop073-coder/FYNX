const express = require('express');

function registerMarketplaceShippingTimeline(app, pool, requireAuth) {
  const router = express.Router();

  router.get('/orders/:id/timeline', requireAuth, async (req, res) => {
    const id = String(req.params.id || '').trim();
    if (!id) return res.status(400).json({ error: 'invalid order id' });
    try {
      const result = await pool.query(`
        SELECT e.id, e.event_type, e.from_status, e.to_status, e.metadata, e.created_at,
               o.buyer_id, o.seller_id
        FROM marketplace_order_events e
        JOIN marketplace_orders o ON o.id = e.order_id
        WHERE e.order_id = $1 AND (o.buyer_id = $2 OR o.seller_id = $2)
        ORDER BY e.created_at ASC, e.id ASC
      `, [id, req.user.sub]);
      if (!result.rows.length) return res.status(404).json({ error: 'order not found' });
      return res.json({ orderId: id, events: result.rows.map(row => ({
        id: String(row.id),
        eventType: row.event_type,
        fromStatus: row.from_status,
        toStatus: row.to_status,
        metadata: row.metadata || {},
        createdAt: row.created_at
      })) });
    } catch (error) {
      console.error('marketplace timeline failed', error);
      return res.status(500).json({ error: 'unable to load order timeline' });
    }
  });

  app.use('/api/marketplace', router);
}

module.exports = { registerMarketplaceShippingTimeline };
