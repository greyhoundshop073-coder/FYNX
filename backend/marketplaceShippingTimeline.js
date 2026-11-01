export function registerMarketplaceShippingTimeline({ app, pool, auth, ensureSchema }) {
  app.get('/api/marketplace/orders/:id/timeline', auth, async (req, res) => {
    const id = typeof req.params?.id === 'string' ? req.params.id.trim() : '';
    if (!/^[0-9a-f-]{36}$/i.test(id)) return res.status(400).json({ error: 'invalid order id' });
    try {
      await ensureSchema();
      const order = await pool.query(
        'SELECT id FROM marketplace_orders WHERE id=$1 AND (buyer_id=$2 OR seller_id=$2) LIMIT 1',
        [id, req.user.sub]
      );
      if (!order.rows.length) return res.status(404).json({ error: 'order not found' });
      const result = await pool.query(
        'SELECT id,event_type,from_status,to_status,metadata,created_at FROM marketplace_order_events WHERE order_id=$1 ORDER BY created_at ASC,id ASC',
        [id]
      );
      const deliveryEvidence = await pool.query(
        'SELECT evidence_type,value,created_at FROM marketplace_delivery_evidence WHERE order_id=$1 ORDER BY created_at ASC',
        [id]
      );
      return res.json({
        orderId: id,
        deliveryEvidence: deliveryEvidence.rows.map(row => ({
          type: row.evidence_type,
          value: row.value,
          createdAt: row.created_at
        })),
        events: result.rows.map(row => ({
          id: String(row.id),
          eventType: row.event_type,
          fromStatus: row.from_status,
          toStatus: row.to_status,
          metadata: row.metadata || {},
          createdAt: row.created_at
        }))
      });
    } catch (error) {
      console.error('marketplace shipping timeline', error);
      return res.status(500).json({ error: 'unable to load order timeline' });
    }
  });
}
