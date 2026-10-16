import crypto from 'node:crypto';
import { registerMarketplaceShippingTimeline } from './marketplaceShippingTimeline.js';
import { isMarketplaceDestinationCovered } from './marketplaceShipping.js';

export function registerMarketplaceCompletionRoutes({ app, pool, auth }) {
  let schemaPromise;

  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_method TEXT NOT NULL DEFAULT 'DELIVERY' CHECK (fulfillment_method IN ('DELIVERY','PICKUP'));
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_address JSONB;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS buyer_note TEXT NOT NULL DEFAULT '';
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS pickup_handover_at TIMESTAMPTZ;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS pickup_handover_by BIGINT;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_status TEXT;
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS fulfillment_status_updated_at TIMESTAMPTZ;
        UPDATE marketplace_orders
        SET fulfillment_status = CASE
          WHEN status = 'COMPLETED' THEN 'COMPLETED'
          WHEN status = 'INSPECTION' THEN 'INSPECTION'
          WHEN status = 'SHIPPED' AND fulfillment_method = 'PICKUP' THEN 'PICKUP_COMPLETED'
          WHEN status = 'SHIPPED' THEN 'DISPATCHED'
          WHEN status = 'PAID' THEN 'PAID'
          ELSE 'PENDING'
        END,
        fulfillment_status_updated_at = COALESCE(fulfillment_status_updated_at, updated_at, NOW())
        WHERE fulfillment_status IS NULL;
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status SET DEFAULT 'PENDING';
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status SET NOT NULL;
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status_updated_at SET DEFAULT NOW();
        ALTER TABLE marketplace_orders ALTER COLUMN fulfillment_status_updated_at SET NOT NULL;
        CREATE INDEX IF NOT EXISTS marketplace_orders_fulfillment_idx ON marketplace_orders (seller_id, status, updated_at DESC);
        CREATE INDEX IF NOT EXISTS marketplace_orders_fulfillment_status_idx ON marketplace_orders (seller_id, fulfillment_status, updated_at DESC);
        CREATE TABLE IF NOT EXISTS marketplace_delivery_assignments (
          order_id UUID PRIMARY KEY REFERENCES marketplace_orders(id) ON DELETE CASCADE,
          seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          driver_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
          driver_type TEXT NOT NULL DEFAULT 'FYNX_ACCOUNT' CHECK (driver_type IN ('FYNX_ACCOUNT','SELLER_LOCAL')),
          local_driver_name TEXT,
          local_driver_phone TEXT,
          pickup_point TEXT,
          pickup_instructions TEXT,
          vehicle_type TEXT,
          vehicle_color TEXT,
          vehicle_plate TEXT,
          vehicle_note TEXT,
          assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          accepted_at TIMESTAMPTZ,
          handover_at TIMESTAMPTZ,
          delivered_at TIMESTAMPTZ,
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          CHECK (seller_id <> driver_id)
        );
        ALTER TABLE marketplace_delivery_assignments ALTER COLUMN driver_id DROP NOT NULL;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS driver_type TEXT NOT NULL DEFAULT 'FYNX_ACCOUNT';
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS local_driver_name TEXT;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS local_driver_phone TEXT;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS pickup_point TEXT;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS pickup_instructions TEXT;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS vehicle_type TEXT;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS vehicle_color TEXT;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS vehicle_plate TEXT;
        ALTER TABLE marketplace_delivery_assignments ADD COLUMN IF NOT EXISTS vehicle_note TEXT;
        CREATE INDEX IF NOT EXISTS marketplace_delivery_assignments_driver_idx ON marketplace_delivery_assignments (driver_id, updated_at DESC);
        CREATE TABLE IF NOT EXISTS fynx_admin_roles (
          user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
          role TEXT NOT NULL CHECK (role IN ('ADMIN')),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE TABLE IF NOT EXISTS marketplace_approved_drivers (
          user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
          approved_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          active BOOLEAN NOT NULL DEFAULT TRUE,
          approved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS marketplace_approved_drivers_active_idx ON marketplace_approved_drivers (active, user_id);
        CREATE TABLE IF NOT EXISTS marketplace_delivery_evidence (
          id BIGSERIAL PRIMARY KEY,
          order_id UUID NOT NULL REFERENCES marketplace_orders(id) ON DELETE CASCADE,
          submitted_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          evidence_type TEXT NOT NULL CHECK (evidence_type IN ('HANDOVER_NOTE','DELIVERY_NOTE','RECIPIENT_NAME','PHOTO_REFERENCE')),
          value TEXT NOT NULL CHECK (length(value) BETWEEN 1 AND 1000),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS marketplace_delivery_evidence_order_idx ON marketplace_delivery_evidence (order_id, created_at ASC);
      `).catch((error) => {
        schemaPromise = undefined;
        throw error;
      });
    }
    return schemaPromise;
  };

  registerMarketplaceShippingTimeline({ app, pool, auth, ensureSchema });

  const parseUuid = (value) => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;
  const isSeller = (order, userId) => String(order.seller_id) === String(userId);
  const isBuyer = (order, userId) => String(order.buyer_id) === String(userId);
  const hasMarketplaceAdminAccess = async (db, userId) => {
    const owner = await db.query('SELECT id FROM users ORDER BY id ASC LIMIT 1');
    if (owner.rows[0] && String(owner.rows[0].id) === String(userId)) return true;
    const admin = await db.query("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 AND role='ADMIN' LIMIT 1", [userId]);
    return admin.rowCount > 0;
  };
  const DELIVERY_TRANSITIONS = new Map([
    ['PAID->PREPARING', true],
    ['PREPARING->DISPATCHED', true],
    ['DISPATCHED->IN_TRANSIT', true],
    ['IN_TRANSIT->DELIVERED', true],
    ['DISPATCHED->FAILED_DELIVERY', true],
    ['IN_TRANSIT->FAILED_DELIVERY', true],
    ['FAILED_DELIVERY->IN_TRANSIT', true],
    ['FAILED_DELIVERY->RETURNED', true]
  ]);
  const PICKUP_TRANSITIONS = new Map([
    ['PAID->READY_FOR_PICKUP', true]
  ]);

  const canAdvanceFulfillment = (from, to, method) => {
    if (from === to) return false;
    if (method === 'PICKUP') return Boolean(PICKUP_TRANSITIONS.get(`${from}->${to}`));
    return Boolean(DELIVERY_TRANSITIONS.get(`${from}->${to}`));
  };

  const hasActiveProtectionCase = async (client, orderId) => (await client.query(`
    SELECT 1 FROM marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')
    UNION ALL
    SELECT 1 FROM marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')
    LIMIT 1
  `, [orderId])).rowCount > 0;

  const recordFulfillmentEvent = async (client, orderId, actorId, eventType, fromState, toState, metadata = {}) => {
    await client.query(
      `INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,$3,$4,$5,$6::jsonb)`,
      [orderId, actorId, eventType, fromState, toState, JSON.stringify(metadata)]
    );
  };

  app.get('/api/marketplace/media/:id', auth, async (req, res) => {
    try {
      const mediaId = Number(req.params.id);
      if (!Number.isInteger(mediaId) || mediaId < 1) return res.status(400).json({ error: 'invalid media id' });
      const result = await pool.query(`
        SELECT mm.mime_type, mm.data
        FROM message_media mm
        WHERE mm.id = $1
          AND (
            EXISTS (
              SELECT 1 FROM marketplace_listings ml
              WHERE ml.active = TRUE
                AND EXISTS (
                  SELECT 1
                  FROM regexp_split_to_table(regexp_replace(CAST(ml.media_ids AS TEXT), '[^0-9]+', ',', 'g'), ',') AS listing_media_id
                  WHERE listing_media_id = CAST(mm.id AS TEXT)
                )
            )
            OR EXISTS (
              SELECT 1 FROM marketplace_orders mo
              WHERE (mo.buyer_id = $2 OR mo.seller_id = $2)
                AND EXISTS (
                  SELECT 1
                  FROM jsonb_array_elements_text(COALESCE(mo.product_snapshot->'mediaIds', '[]'::jsonb)) AS order_media_id
                  WHERE order_media_id = CAST(mm.id AS TEXT)
                )
            )
          )
        LIMIT 1`, [mediaId, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: 'marketplace media not found' });
      res.set('Cache-Control', 'private, max-age=3600');
      res.type(result.rows[0].mime_type);
      return res.send(result.rows[0].data);
    } catch (error) {
      console.error('marketplace media fetch', error);
      return res.status(500).json({ error: 'marketplace media fetch failed' });
    }
  });

  app.get('/api/marketplace/seller/orders', auth, async (req, res) => {
    try {
      await ensureSchema();
      const status = typeof req.query?.status === 'string' ? req.query.status.trim().toUpperCase() : '';
      const allowed = new Set(['PAYMENT_PENDING','PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED','CANCELLED','REFUNDED']);
      const params = [req.user.sub];
      let where = 'o.seller_id = $1';
      if (status && allowed.has(status)) { params.push(status); where += ` AND o.status = $${params.length}`; }
      const result = await pool.query(`SELECT o.*, u.username AS buyer_username, u.display_name AS buyer_display_name, da.driver_id AS delivery_driver_id, da.driver_type AS delivery_driver_type, da.local_driver_name, da.local_driver_phone, da.pickup_point, da.pickup_instructions, da.vehicle_type, da.vehicle_color, da.vehicle_plate, da.vehicle_note, da.accepted_at AS delivery_driver_accepted_at, da.handover_at AS delivery_driver_handover_at, da.delivered_at AS delivery_driver_delivered_at, COALESCE(ad.active,FALSE) AS delivery_driver_approval_active FROM marketplace_orders o JOIN users u ON u.id = o.buyer_id LEFT JOIN marketplace_delivery_assignments da ON da.order_id=o.id LEFT JOIN marketplace_approved_drivers ad ON ad.user_id=da.driver_id WHERE ${where} ORDER BY o.created_at DESC LIMIT 100`, params);
      return res.json({ orders: result.rows.map((row) => ({
        id: String(row.id), buyerId: String(row.buyer_id), buyerUsername: row.buyer_username, buyerDisplayName: row.buyer_display_name,
        listingId: String(row.listing_id), quantity: Number(row.quantity), unitPrice: Number(row.unit_price), deliveryFee: Number(row.delivery_fee),
        totalAmount: Number(row.total_amount), currency: row.currency, product: row.product_snapshot, status: row.status,
        fulfillmentStatus: row.fulfillment_status, fulfillmentStatusUpdatedAt: row.fulfillment_status_updated_at,
        fulfillmentMethod: row.fulfillment_method, shippingAddress: row.shipping_address, buyerNote: row.buyer_note, driverId: row.delivery_driver_id ? String(row.delivery_driver_id) : null, driverType: row.delivery_driver_type || null, localDriverName: row.local_driver_name || null, localDriverPhone: row.local_driver_phone || null, pickupPoint: row.pickup_point || null, pickupInstructions: row.pickup_instructions || null, vehicleType: row.vehicle_type || null, vehicleColor: row.vehicle_color || null, vehiclePlate: row.vehicle_plate || null, vehicleNote: row.vehicle_note || null, driverApprovalActive: row.delivery_driver_approval_active === true, driverAcceptedAt: row.delivery_driver_accepted_at, driverHandoverAt: row.delivery_driver_handover_at, driverDeliveredAt: row.delivery_driver_delivered_at,
        paymentReference: row.payment_reference, trackingReference: row.tracking_reference, shippedAt: row.shipped_at,
        pickupHandoverAt: row.pickup_handover_at, pickupHandoverBy: row.pickup_handover_by ? String(row.pickup_handover_by) : null,
        deliveredAt: row.delivered_at, inspectionDeadline: row.inspection_deadline, completedAt: row.completed_at,
        createdAt: row.created_at, updatedAt: row.updated_at
      })) });
    } catch (error) { console.error('marketplace seller orders', error); return res.status(500).json({ error: 'seller order lookup failed' }); }
  });

  app.post('/api/marketplace/orders/:id/fulfillment', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const method = typeof req.body?.method === 'string' ? req.body.method.trim().toUpperCase() : '';
    const address = req.body?.shippingAddress && typeof req.body.shippingAddress === 'object' && !Array.isArray(req.body.shippingAddress) ? req.body.shippingAddress : null;
    const note = typeof req.body?.buyerNote === 'string' ? req.body.buyerNote.trim().slice(0, 1000) : '';
    if (!id || !['DELIVERY','PICKUP'].includes(method)) return res.status(400).json({ error: 'valid order id and fulfillment method are required' });
    if (method === 'DELIVERY' && (!address || typeof address.name !== 'string' || typeof address.phone !== 'string' || typeof address.address !== 'string' || !address.address.trim())) return res.status(400).json({ error: 'delivery requires name, phone and address' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isBuyer(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can choose fulfillment' }); }
      if (!['PAYMENT_PENDING','PAID'].includes(order.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'fulfillment can only be selected before shipping' }); }
      const storedMethod = String(order.fulfillment_method || '').trim().toUpperCase();
      if (storedMethod && storedMethod !== method) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'the protected checkout fulfillment method cannot be changed after order creation', code: 'FULFILLMENT_METHOD_LOCKED' });
      }
      const snapshot = order.product_snapshot && typeof order.product_snapshot === 'object' ? order.product_snapshot : {};
      if (method === 'DELIVERY' && !Boolean(snapshot.deliveryAvailable)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is not available for this listing' }); }
      if (method === 'PICKUP' && !Boolean(snapshot.pickupAvailable)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup is not available for this listing' }); }
      const safeAddress = method === 'DELIVERY' ? { name: String(address.name).trim().slice(0, 120), phone: String(address.phone).trim().slice(0, 40), address: String(address.address).trim().slice(0, 500), city: typeof address.city === 'string' ? address.city.trim().slice(0, 100) : '', state: typeof address.state === 'string' ? address.state.trim().slice(0, 100) : '', country: typeof address.country === 'string' ? address.country.trim().slice(0, 100) : '' } : null;
      if (method === 'DELIVERY' && !(await isMarketplaceDestinationCovered(client, order.listing_id, safeAddress))) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'seller does not currently deliver to this destination', code: 'DESTINATION_NOT_COVERED' }); }
      const updated = await client.query(`UPDATE marketplace_orders SET fulfillment_method=$1,shipping_address=$2::jsonb,buyer_note=$3,updated_at=NOW() WHERE id=$4 RETURNING *`, [method, safeAddress ? JSON.stringify(safeAddress) : null, note, id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'FULFILLMENT_SELECTED', order.fulfillment_status || 'PENDING', order.fulfillment_status || 'PENDING', { method });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentMethod: updated.rows[0].fulfillment_method, fulfillmentStatus: updated.rows[0].fulfillment_status } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace fulfillment', error); return res.status(500).json({ error: 'fulfillment selection failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/fulfillment-progress', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const requested = typeof req.body?.state === 'string' ? req.body.state.trim().toUpperCase() : '';
    const note = typeof req.body?.note === 'string' ? req.body.note.trim().slice(0, 500) : '';
    const allowedStates = new Set(['PREPARING','DISPATCHED','IN_TRANSIT','DELIVERED','READY_FOR_PICKUP','FAILED_DELIVERY','RETURNED']);
    if (!id || !allowedStates.has(requested)) return res.status(400).json({ error: 'valid order id and fulfillment state are required' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can update fulfillment progress' }); }
      let localArrivalAssignment = null;
      if (requested === 'DELIVERED') {
        const assignment = (await client.query('SELECT * FROM marketplace_delivery_assignments WHERE order_id=$1 FOR UPDATE', [id])).rows[0];
        if (assignment?.driver_type !== 'SELLER_LOCAL' && assignment) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'FYNX-driver deliveries must be marked delivered by the assigned driver with evidence' }); }
        if (assignment?.driver_type === 'SELLER_LOCAL') {
          if (!assignment.handover_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'record the local driver handover before reporting arrival' }); }
          if (assignment.delivered_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'local driver arrival has already been reported' }); }
          localArrivalAssignment = assignment;
        }
      }
      if (order.status !== 'PAID' && order.status !== 'SHIPPED') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order is not ready for fulfillment progress' }); }
      const current = order.fulfillment_status || (order.status === 'SHIPPED' ? (order.fulfillment_method === 'PICKUP' ? 'PICKUP_COMPLETED' : 'DISPATCHED') : 'PAID');
      if (!canAdvanceFulfillment(current, requested, order.fulfillment_method)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `invalid fulfillment transition from ${current} to ${requested}` }); }
      if (['FAILED_DELIVERY','RETURNED'].includes(requested) && await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      if (requested === 'RETURNED' && current !== 'FAILED_DELIVERY') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'return requires a failed delivery state first' }); }
      const updated = await client.query(`UPDATE marketplace_orders SET fulfillment_status=$1,fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [requested, id]);
      if (localArrivalAssignment) {
        await client.query('UPDATE marketplace_delivery_assignments SET delivered_at=NOW(),updated_at=NOW() WHERE order_id=$1', [id]);
        await client.query("INSERT INTO marketplace_delivery_evidence (order_id,submitted_by,evidence_type,value) VALUES ($1,$2,'DELIVERY_NOTE',$3)", [id, req.user.sub, note || 'Seller reported the local driver has arrived at the agreed pickup point.']);
      }
      await recordFulfillmentEvent(client, id, req.user.sub, localArrivalAssignment ? 'LOCAL_DRIVER_ARRIVAL_REPORTED' : `FULFILLMENT_${requested}`, current, requested, { fulfillmentMethod: order.fulfillment_method, note: note || null, deliveryMode: localArrivalAssignment ? 'SELLER_LOCAL' : undefined });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, fulfillmentStatusUpdatedAt: updated.rows[0].fulfillment_status_updated_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace fulfillment progress', error); return res.status(500).json({ error: 'fulfillment progress update failed' }); }
    finally { client.release(); }
  });

  app.get('/api/admin/marketplace/drivers', auth, async (req, res) => {
    try {
      await ensureSchema();
      if (!(await hasMarketplaceAdminAccess(pool, req.user.sub))) return res.status(403).json({ error: 'administrator access required' });
      const result = await pool.query(`
        SELECT d.user_id, u.username, u.display_name, d.active, d.approved_by, d.approved_at, d.updated_at
        FROM marketplace_approved_drivers d JOIN users u ON u.id=d.user_id
        ORDER BY d.active DESC, d.updated_at DESC LIMIT 200
      `);
      return res.json({ drivers: result.rows.map(row => ({
        userId: String(row.user_id), username: row.username, displayName: row.display_name,
        active: row.active, approvedBy: String(row.approved_by), approvedAt: row.approved_at, updatedAt: row.updated_at
      })) });
    } catch (error) { console.error('marketplace approved driver list', error); return res.status(500).json({ error: 'approved driver list unavailable' }); }
  });

  app.post('/api/admin/marketplace/drivers/:userId/approval', auth, async (req, res) => {
    const userId = Number(req.params.userId);
    const active = req.body?.active;
    if (!Number.isSafeInteger(userId) || userId < 1 || typeof active !== 'boolean') {
      return res.status(400).json({ error: 'valid user id and boolean active value are required' });
    }
    const client = await pool.connect();
    try {
      await ensureSchema();
      await client.query('BEGIN');
      if (!(await hasMarketplaceAdminAccess(client, req.user.sub))) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'administrator access required' }); }
      const target = await client.query('SELECT id FROM users WHERE id=$1', [userId]);
      if (!target.rowCount) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'driver account not found' }); }
      if (String(userId) === String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'an administrator cannot approve their own driver account' }); }
      if (!active) {
        // Match the order-first lock order used by handover/delivery so revocation cannot race custody transfer.
        await client.query(\`
          SELECT o.id
          FROM marketplace_orders o
          JOIN marketplace_delivery_assignments a ON a.order_id=o.id
          WHERE a.driver_id=$1 AND o.status IN ('PAID','SHIPPED','INSPECTION')
          ORDER BY o.id
          FOR UPDATE OF o
        \`, [userId]);
        const custody = await client.query(\`
          SELECT a.order_id
          FROM marketplace_delivery_assignments a
          JOIN marketplace_orders o ON o.id=a.order_id
          WHERE a.driver_id=$1 AND a.handover_at IS NOT NULL AND a.delivered_at IS NULL
            AND o.status IN ('PAID','SHIPPED','INSPECTION')
          LIMIT 1
        \`, [userId]);
        if (custody.rowCount) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'driver has an order in active custody; resolve or complete that delivery before revoking approval', code: 'DRIVER_ACTIVE_CUSTODY' });
        }
      }
      const result = await client.query(\`
        INSERT INTO marketplace_approved_drivers (user_id, approved_by, active, approved_at, updated_at)
        VALUES ($1,$2,$3,NOW(),NOW())
        ON CONFLICT (user_id) DO UPDATE SET active=EXCLUDED.active, approved_by=EXCLUDED.approved_by,
          approved_at=CASE WHEN EXCLUDED.active THEN NOW() ELSE marketplace_approved_drivers.approved_at END,
          updated_at=NOW()
        RETURNING user_id, active, approved_by, approved_at, updated_at
      \`, [userId, req.user.sub, active]);
      await client.query('COMMIT');
      return res.json({ driver: { userId: String(result.rows[0].user_id), active: result.rows[0].active, approvedBy: String(result.rows[0].approved_by), approvedAt: result.rows[0].approved_at, updatedAt: result.rows[0].updated_at } });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace driver approval', error);
      return res.status(500).json({ error: 'driver approval update failed' });
    } finally { client.release(); }
  });

  app.get('/api/marketplace/driver/assignments', auth, async (req, res) => {
    try {
      await ensureSchema();
      const result = await pool.query(`
        SELECT a.order_id, a.seller_id, a.driver_id, a.assigned_at, a.accepted_at, a.handover_at, a.delivered_at,
               o.status, o.fulfillment_status, o.fulfillment_method, o.currency, o.total_amount,
               o.product_snapshot, o.quantity, o.shipping_address, o.buyer_note, o.tracking_reference,
               seller.username AS seller_username, buyer.display_name AS buyer_display_name, buyer.username AS buyer_username
        FROM marketplace_delivery_assignments a
        JOIN marketplace_orders o ON o.id = a.order_id
        JOIN users seller ON seller.id = a.seller_id
        JOIN users buyer ON buyer.id = o.buyer_id
        JOIN marketplace_approved_drivers approved_driver ON approved_driver.user_id=a.driver_id AND approved_driver.active=TRUE
        WHERE a.driver_id = $1 AND o.fulfillment_method = 'DELIVERY'
          AND o.status IN ('PAID','SHIPPED','INSPECTION')
        ORDER BY a.updated_at DESC LIMIT 100
      `, [req.user.sub]);
      const ids = result.rows.map(row => row.order_id);
      const evidence = ids.length ? await pool.query(`
        SELECT order_id, evidence_type, value, created_at
        FROM marketplace_delivery_evidence
        WHERE order_id = ANY($1::uuid[])
        ORDER BY created_at ASC
      `, [ids]) : { rows: [] };
      const byOrder = new Map();
      for (const row of evidence.rows) {
        const key = String(row.order_id);
        if (!byOrder.has(key)) byOrder.set(key, []);
        byOrder.get(key).push({ type: row.evidence_type, value: row.value, createdAt: row.created_at });
      }
      return res.json({ assignments: result.rows.map(row => ({
        orderId: String(row.order_id), sellerId: String(row.seller_id), driverId: String(row.driver_id),
        assignedAt: row.assigned_at, acceptedAt: row.accepted_at, handoverAt: row.handover_at, deliveredAt: row.delivered_at,
        status: row.status, fulfillmentStatus: row.fulfillment_status, currency: row.currency,
        totalAmount: Number(row.total_amount), quantity: Number(row.quantity),
        product: row.product_snapshot, shippingAddress: row.accepted_at ? row.shipping_address : null, buyerNote: row.accepted_at ? row.buyer_note : '',
        trackingReference: row.tracking_reference, sellerUsername: row.seller_username,
        buyerName: row.accepted_at ? (row.buyer_display_name || row.buyer_username) : '', evidence: byOrder.get(String(row.order_id)) || []
      })) });
    } catch (error) {
      console.error('marketplace driver assignments', error);
      return res.status(500).json({ error: 'driver assignments could not load' });
    }
  });

  app.get('/api/marketplace/orders/:id/delivery-details', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    if (!id) return res.status(400).json({ error: 'invalid order id' });
    try {
      await ensureSchema();
      const order = (await pool.query('SELECT * FROM marketplace_orders WHERE id=$1', [id])).rows[0];
      if (!order) return res.status(404).json({ error: 'order not found' });
      if (!isBuyer(order, req.user.sub) && !isSeller(order, req.user.sub)) return res.status(403).json({ error: 'only the buyer or seller can view delivery details' });
      const assignment = (await pool.query(`SELECT a.*, u.username AS driver_username, u.display_name AS driver_display_name
        FROM marketplace_delivery_assignments a LEFT JOIN users u ON u.id=a.driver_id WHERE a.order_id=$1`, [id])).rows[0];
      if (!assignment) return res.json({ assignment: null });
      const localDriver = (assignment.driver_type || 'FYNX_ACCOUNT') === 'SELLER_LOCAL';
      return res.json({ assignment: {
        driverType: localDriver ? 'SELLER_LOCAL' : 'FYNX_ACCOUNT',
        driverName: localDriver ? assignment.local_driver_name : (assignment.driver_display_name || assignment.driver_username || 'FYNX driver'),
        driverPhone: localDriver ? assignment.local_driver_phone : null,
        pickupPoint: assignment.pickup_point,
        pickupInstructions: assignment.pickup_instructions,
        vehicleType: assignment.vehicle_type,
        vehicleColor: assignment.vehicle_color,
        vehiclePlate: assignment.vehicle_plate,
        vehicleNote: assignment.vehicle_note,
        assignedAt: assignment.assigned_at,
        handoverAt: assignment.handover_at,
        arrivalReportedAt: assignment.delivered_at
      } });
    } catch (error) { console.error('marketplace delivery details', error); return res.status(500).json({ error: 'delivery details could not load' }); }
  });

  app.post('/api/marketplace/orders/:id/driver-assignment', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const driverType = typeof req.body?.driverType === 'string' ? req.body.driverType.trim().toUpperCase() : 'FYNX_ACCOUNT';
    const requestedDriverId = Number(req.body?.driverId);
    const driverUsername = typeof req.body?.driverUsername === 'string' ? req.body.driverUsername.trim().replace(/^@/, '').toLowerCase() : '';
    const localDriverName = typeof req.body?.localDriverName === 'string' ? req.body.localDriverName.trim().slice(0, 120) : '';
    const localDriverPhone = typeof req.body?.localDriverPhone === 'string' ? req.body.localDriverPhone.trim().slice(0, 40) : '';
    const pickupPoint = typeof req.body?.pickupPoint === 'string' ? req.body.pickupPoint.trim().slice(0, 300) : '';
    const pickupInstructions = typeof req.body?.pickupInstructions === 'string' ? req.body.pickupInstructions.trim().slice(0, 500) : '';
    const vehicleType = typeof req.body?.vehicleType === 'string' ? req.body.vehicleType.trim().slice(0, 80) : '';
    const vehicleColor = typeof req.body?.vehicleColor === 'string' ? req.body.vehicleColor.trim().slice(0, 80) : '';
    const vehiclePlate = typeof req.body?.vehiclePlate === 'string' ? req.body.vehiclePlate.trim().slice(0, 40) : '';
    const vehicleNote = typeof req.body?.vehicleNote === 'string' ? req.body.vehicleNote.trim().slice(0, 300) : '';
    const validFynxDriver = (Number.isSafeInteger(requestedDriverId) && requestedDriverId > 0) || /^[a-z0-9_.]{2,32}$/.test(driverUsername);
    if (!id || !['FYNX_ACCOUNT','SELLER_LOCAL'].includes(driverType)) return res.status(400).json({ error: 'choose a valid delivery driver type' });
    if (driverType === 'FYNX_ACCOUNT' && !validFynxDriver) return res.status(400).json({ error: 'valid order id and approved FYNX driver username or id are required' });
    if (driverType === 'SELLER_LOCAL' && (!localDriverName || !localDriverPhone || !pickupPoint)) return res.status(400).json({ error: 'local driver name, phone, and agreed pickup point are required' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can assign a delivery driver' }); }
      if (order.fulfillment_method !== 'DELIVERY') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'driver assignment is only available for delivery orders' }); }
      if (!['PAID','SHIPPED'].includes(order.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'driver can only be assigned to a paid order' }); }
      let driverId = null;
      if (driverType === 'FYNX_ACCOUNT') {
        const driver = (await client.query(
          driverUsername
            ? 'SELECT u.id FROM users u JOIN marketplace_approved_drivers ad ON ad.user_id=u.id AND ad.active=TRUE WHERE lower(u.username)=$1'
            : 'SELECT u.id FROM users u JOIN marketplace_approved_drivers ad ON ad.user_id=u.id AND ad.active=TRUE WHERE u.id=$1',
          driverUsername ? [driverUsername] : [requestedDriverId]
        )).rows[0];
        if (!driver) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'driver account not found or not approved by FYNX' }); }
        driverId = Number(driver.id);
        if (String(driverId) === String(order.seller_id) || String(driverId) === String(order.buyer_id)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'buyer and seller cannot be assigned as the delivery driver' }); }
      }
      const prior = (await client.query('SELECT * FROM marketplace_delivery_assignments WHERE order_id=$1 FOR UPDATE', [id])).rows[0];
      if (prior && (prior.handover_at || prior.delivered_at)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'driver assignment is locked after physical custody handover' }); }
      let reassignedAfterRevocation = false;
      if (prior?.driver_type === 'FYNX_ACCOUNT' && prior.accepted_at) {
        const priorDriverActive = await client.query('SELECT 1 FROM marketplace_approved_drivers WHERE user_id=$1 AND active=TRUE', [prior.driver_id]);
        if (priorDriverActive.rowCount || String(prior.driver_id) === String(driverId)) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'driver assignment is locked after acceptance unless FYNX has revoked the prior driver approval before custody handover' });
        }
        reassignedAfterRevocation = true;
      }
      if (prior && String(prior.driver_id || '') !== String(driverId || '')) {
        await client.query('DELETE FROM marketplace_delivery_evidence WHERE order_id=$1', [id]);
      }
      const assignment = await client.query(`
        INSERT INTO marketplace_delivery_assignments
          (order_id,seller_id,driver_id,driver_type,local_driver_name,local_driver_phone,pickup_point,pickup_instructions,vehicle_type,vehicle_color,vehicle_plate,vehicle_note,assigned_at,accepted_at,handover_at,delivered_at,updated_at)
        VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,NOW(),NULL,NULL,NULL,NOW())
        ON CONFLICT (order_id) DO UPDATE SET driver_id=EXCLUDED.driver_id,driver_type=EXCLUDED.driver_type,local_driver_name=EXCLUDED.local_driver_name,local_driver_phone=EXCLUDED.local_driver_phone,pickup_point=EXCLUDED.pickup_point,pickup_instructions=EXCLUDED.pickup_instructions,vehicle_type=EXCLUDED.vehicle_type,vehicle_color=EXCLUDED.vehicle_color,vehicle_plate=EXCLUDED.vehicle_plate,vehicle_note=EXCLUDED.vehicle_note,assigned_at=NOW(),accepted_at=NULL,handover_at=NULL,delivered_at=NULL,updated_at=NOW()
        RETURNING order_id,driver_id,driver_type,local_driver_name,local_driver_phone,pickup_point,pickup_instructions,vehicle_type,vehicle_color,vehicle_plate,vehicle_note,assigned_at,accepted_at,handover_at,delivered_at
      `, [id, order.seller_id, driverId, driverType, driverType === 'SELLER_LOCAL' ? localDriverName : null, driverType === 'SELLER_LOCAL' ? localDriverPhone : null, driverType === 'SELLER_LOCAL' ? pickupPoint : null, driverType === 'SELLER_LOCAL' ? pickupInstructions || null : null, driverType === 'SELLER_LOCAL' ? vehicleType || null : null, driverType === 'SELLER_LOCAL' ? vehicleColor || null : null, driverType === 'SELLER_LOCAL' ? vehiclePlate || null : null, driverType === 'SELLER_LOCAL' ? vehicleNote || null : null]);
      await recordFulfillmentEvent(client, id, req.user.sub, driverType === 'SELLER_LOCAL' ? 'LOCAL_DRIVER_ASSIGNED' : (reassignedAfterRevocation ? 'DELIVERY_DRIVER_REASSIGNED_AFTER_REVOCATION' : 'DELIVERY_DRIVER_ASSIGNED'), order.fulfillment_status || 'PAID', order.fulfillment_status || 'PAID', { driverType, driverId: driverId ? String(driverId) : null, previousDriverId: prior?.driver_id ? String(prior.driver_id) : null, reassignedAfterRevocation, pickupPoint: driverType === 'SELLER_LOCAL' ? pickupPoint : null, vehicleType: vehicleType || null, vehicleColor: vehicleColor || null, vehiclePlate: vehiclePlate || null });
      await client.query('COMMIT');
      return res.json({ assignment: { orderId: String(assignment.rows[0].order_id), driverId: assignment.rows[0].driver_id ? String(assignment.rows[0].driver_id) : null, driverType: assignment.rows[0].driver_type, localDriverName: assignment.rows[0].local_driver_name, localDriverPhone: assignment.rows[0].local_driver_phone, pickupPoint: assignment.rows[0].pickup_point, pickupInstructions: assignment.rows[0].pickup_instructions, vehicleType: assignment.rows[0].vehicle_type, vehicleColor: assignment.rows[0].vehicle_color, vehiclePlate: assignment.rows[0].vehicle_plate, vehicleNote: assignment.rows[0].vehicle_note, assignedAt: assignment.rows[0].assigned_at, acceptedAt: assignment.rows[0].accepted_at, handoverAt: assignment.rows[0].handover_at, deliveredAt: assignment.rows[0].delivered_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace driver assignment', error); return res.status(500).json({ error: 'driver assignment failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/driver-acceptance', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      const assignment = (await client.query('SELECT * FROM marketplace_delivery_assignments WHERE order_id=$1 FOR UPDATE', [id])).rows[0];
      if (!assignment) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'no delivery driver is assigned' }); }
      if ((assignment.driver_type || 'FYNX_ACCOUNT') !== 'FYNX_ACCOUNT') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'seller-local drivers do not use FYNX account acceptance' }); }
      if (String(assignment.driver_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the assigned driver can accept this delivery' }); }
      const approved = await client.query('SELECT 1 FROM marketplace_approved_drivers WHERE user_id=$1 AND active=TRUE', [req.user.sub]);
      if (!approved.rowCount) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'driver approval is inactive; contact FYNX support' }); }
      if (!['PAID','SHIPPED'].includes(order.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order is not available for driver acceptance' }); }
      if (assignment.accepted_at) { await client.query('COMMIT'); return res.json({ accepted: true, acceptedAt: assignment.accepted_at, idempotent: true }); }
      const updated = await client.query('UPDATE marketplace_delivery_assignments SET accepted_at=NOW(),updated_at=NOW() WHERE order_id=$1 RETURNING accepted_at', [id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'DELIVERY_DRIVER_ACCEPTED', order.fulfillment_status || 'PAID', order.fulfillment_status || 'PAID', {});
      await client.query('COMMIT');
      return res.json({ accepted: true, acceptedAt: updated.rows[0].accepted_at });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace driver acceptance', error); return res.status(500).json({ error: 'driver acceptance failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/driver-handover', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const note = typeof req.body?.note === 'string' ? req.body.note.trim().slice(0, 1000) : '';
    if (!id || !note) return res.status(400).json({ error: 'valid order id and handover note are required' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can hand the order to the driver' }); }
      if (order.status !== 'SHIPPED' || order.fulfillment_method !== 'DELIVERY') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'only a shipped delivery order can be handed to a driver' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const assignment = (await client.query('SELECT * FROM marketplace_delivery_assignments WHERE order_id=$1 FOR UPDATE', [id])).rows[0];
      if (!assignment) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'assign a delivery driver first' }); }
      const localDriver = (assignment.driver_type || 'FYNX_ACCOUNT') === 'SELLER_LOCAL';
      if (localDriver) {
        if (!assignment.local_driver_name || !assignment.local_driver_phone || !assignment.pickup_point) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'local driver contact and agreed pickup point are required before handover' }); }
      } else {
        const approvedDriver = await client.query('SELECT 1 FROM marketplace_approved_drivers WHERE user_id=$1 AND active=TRUE', [assignment.driver_id]);
        if (!approvedDriver.rowCount) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'assigned driver approval is inactive; reassign to an active approved driver before handover', code: 'DRIVER_APPROVAL_INACTIVE' }); }
        if (!assignment.accepted_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'driver must accept the assignment before handover' }); }
      }
      if (assignment.handover_at) { await client.query('COMMIT'); return res.json({ handedOver: true, handoverAt: assignment.handover_at, idempotent: true }); }
      const updated = await client.query('UPDATE marketplace_delivery_assignments SET handover_at=NOW(),updated_at=NOW() WHERE order_id=$1 RETURNING handover_at', [id]);
      await client.query("INSERT INTO marketplace_delivery_evidence (order_id,submitted_by,evidence_type,value) VALUES ($1,$2,'HANDOVER_NOTE',$3)", [id, req.user.sub, note]);
      await recordFulfillmentEvent(client, id, req.user.sub, localDriver ? 'LOCAL_DRIVER_CUSTODY_HANDED_OVER' : 'DELIVERY_CUSTODY_HANDED_OVER', order.fulfillment_status || 'DISPATCHED', order.fulfillment_status || 'DISPATCHED', { driverType: localDriver ? 'SELLER_LOCAL' : 'FYNX_ACCOUNT', driverId: assignment.driver_id ? String(assignment.driver_id) : null, localDriverName: assignment.local_driver_name || null, pickupPoint: assignment.pickup_point || null, note });
      await client.query('COMMIT');
      return res.json({ handedOver: true, handoverAt: updated.rows[0].handover_at, driverId: assignment.driver_id ? String(assignment.driver_id) : null, driverType: localDriver ? 'SELLER_LOCAL' : 'FYNX_ACCOUNT' });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace driver handover', error); return res.status(500).json({ error: 'driver handover failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/driver-delivery', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const recipientName = typeof req.body?.recipientName === 'string' ? req.body.recipientName.trim().slice(0, 120) : '';
    const deliveryNote = typeof req.body?.deliveryNote === 'string' ? req.body.deliveryNote.trim().slice(0, 1000) : '';
    const photoReference = typeof req.body?.photoReference === 'string' ? req.body.photoReference.trim().slice(0, 1000) : '';
    if (!id || !recipientName || (!deliveryNote && !photoReference)) return res.status(400).json({ error: 'recipient name and delivery note or photo reference are required' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      const assignment = (await client.query('SELECT * FROM marketplace_delivery_assignments WHERE order_id=$1 FOR UPDATE', [id])).rows[0];
      if (!assignment || (assignment.driver_type || 'FYNX_ACCOUNT') !== 'FYNX_ACCOUNT' || String(assignment.driver_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the assigned FYNX driver can confirm delivery' }); }
      const approved = await client.query('SELECT 1 FROM marketplace_approved_drivers WHERE user_id=$1 AND active=TRUE', [req.user.sub]);
      if (!approved.rowCount) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'driver approval is inactive; contact FYNX support' }); }
      if (order.status !== 'SHIPPED' || order.fulfillment_method !== 'DELIVERY' || !assignment.handover_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery requires a shipped order and recorded custody handover' }); }
      if (assignment.delivered_at) { await client.query('COMMIT'); return res.json({ delivered: true, deliveredAt: assignment.delivered_at, idempotent: true }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const updated = await client.query("UPDATE marketplace_delivery_assignments SET delivered_at=NOW(),updated_at=NOW() WHERE order_id=$1 RETURNING delivered_at", [id]);
      await client.query("INSERT INTO marketplace_delivery_evidence (order_id,submitted_by,evidence_type,value) VALUES ($1,$2,'RECIPIENT_NAME',$3)", [id, req.user.sub, recipientName]);
      if (deliveryNote) await client.query("INSERT INTO marketplace_delivery_evidence (order_id,submitted_by,evidence_type,value) VALUES ($1,$2,'DELIVERY_NOTE',$3)", [id, req.user.sub, deliveryNote]);
      if (photoReference) await client.query("INSERT INTO marketplace_delivery_evidence (order_id,submitted_by,evidence_type,value) VALUES ($1,$2,'PHOTO_REFERENCE',$3)", [id, req.user.sub, photoReference]);
      const current = order.fulfillment_status || 'DISPATCHED';
      await client.query("UPDATE marketplace_orders SET fulfillment_status='DELIVERED',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$1", [id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'DRIVER_DELIVERY_REPORTED', current, 'DELIVERED', { recipientName, hasPhotoReference: Boolean(photoReference), hasDeliveryNote: Boolean(deliveryNote) });
      await client.query('COMMIT');
      return res.json({ delivered: true, deliveredAt: updated.rows[0].delivered_at, awaitingBuyerConfirmation: true });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace driver delivery', error); return res.status(500).json({ error: 'driver delivery confirmation failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/media/:id', auth, async (req, res) => {
    return res.status(405).json({ error: 'method not allowed' });
  });

  app.post('/api/marketplace/orders/:id/ship', auth, async (req, res) => {
    const id = parseUuid(req.params.id);
    const tracking = typeof req.body?.trackingReference === 'string' ? req.body.trackingReference.trim().slice(0, 160) : '';
    if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can ship this order' }); }
      if (order.status !== 'PAID') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'only paid orders can be shipped' }); }
      if (order.fulfillment_method === 'PICKUP') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup orders require seller handover confirmation' }); }
      if (order.fulfillment_method === 'DELIVERY' && !order.shipping_address) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'buyer delivery details are missing' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const current = order.fulfillment_status || 'PAID';
      if (!['PAID','PREPARING'].includes(current)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `order cannot be dispatched from ${current}` }); }
      const updated = await client.query(`UPDATE marketplace_orders SET status='SHIPPED',tracking_reference=$1,shipped_at=NOW(),fulfillment_status='DISPATCHED',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [tracking || null, id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'ORDER_SHIPPED', current, 'DISPATCHED', { trackingReference: tracking || null, fulfillmentMethod: order.fulfillment_method });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, trackingReference: updated.rows[0].tracking_reference, shippedAt: updated.rows[0].shipped_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace ship', error); return res.status(500).json({ error: 'order shipping failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/pickup-handover', auth, async (req, res) => {
    const id = parseUuid(req.params.id); if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isSeller(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the seller can confirm pickup handover' }); }
      if (order.status !== 'PAID') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'only paid orders can be handed over for pickup' }); }
      if (order.fulfillment_method !== 'PICKUP') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup handover is only for pickup orders' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const current = order.fulfillment_status || 'PAID';
      if (!['PAID','READY_FOR_PICKUP'].includes(current)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `pickup cannot be handed over from ${current}` }); }
      const updated = await client.query(`UPDATE marketplace_orders SET status='SHIPPED',pickup_handover_at=NOW(),pickup_handover_by=$1,shipped_at=NOW(),fulfillment_status='PICKUP_COMPLETED',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 AND status='PAID' RETURNING *`, [req.user.sub, id]);
      if (!updated.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order changed before pickup handover' }); }
      await recordFulfillmentEvent(client, id, req.user.sub, 'PICKUP_HANDOVER_CONFIRMED', current, 'PICKUP_COMPLETED', { fulfillmentMethod: 'PICKUP' });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, pickupHandoverAt: updated.rows[0].pickup_handover_at } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace pickup handover', error); return res.status(500).json({ error: 'pickup handover failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/confirm-delivery', auth, async (req, res) => {
    const id = parseUuid(req.params.id); if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isBuyer(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can confirm delivery' }); }
      const driverAssignment = (await client.query('SELECT delivered_at FROM marketplace_delivery_assignments WHERE order_id=$1', [id])).rows[0];
      if (driverAssignment && !driverAssignment.delivered_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'assigned driver has not recorded delivery evidence yet' }); }
      if (!['SHIPPED','DELIVERED'].includes(order.status)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order is not ready for delivery confirmation' }); }
      if (order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup handover must be confirmed by the seller first' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const current = order.fulfillment_status || (order.fulfillment_method === 'PICKUP' ? 'PICKUP_COMPLETED' : 'DISPATCHED');
      if (!['DELIVERED','DISPATCHED','PICKUP_COMPLETED'].includes(current)) { await client.query('ROLLBACK'); return res.status(409).json({ error: `order is not ready for buyer receipt from ${current}` }); }
      const deadline = new Date(Date.now() + 48 * 60 * 60 * 1000);
      const updated = await client.query(`UPDATE marketplace_orders SET status='INSPECTION',delivered_at=COALESCE(delivered_at,NOW()),inspection_deadline=$1,fulfillment_status='INSPECTION',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$2 RETURNING *`, [deadline.toISOString(), id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'BUYER_RECEIVED', current, 'INSPECTION', { inspectionHours: 48, fulfillmentMethod: order.fulfillment_method });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, inspectionDeadline: updated.rows[0].inspection_deadline } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace confirm delivery', error); return res.status(500).json({ error: 'delivery confirmation failed' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/orders/:id/complete', auth, async (req, res) => {
    const id = parseUuid(req.params.id); if (!id) return res.status(400).json({ error: 'invalid order id' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const order = (await client.query('SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE', [id])).rows[0];
      if (!order) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'order not found' }); }
      if (!isBuyer(order, req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the buyer can complete this order' }); }
      if (order.status !== 'INSPECTION') { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order is not in inspection' }); }
      if (await hasActiveProtectionCase(client, id)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order has an active protection case or dispute' }); }
      const receivedItemMatchesOrder = req.body?.receivedItemMatchesOrder === true;
      const quantityMatchesOrder = req.body?.quantityMatchesOrder === true;
      if (!receivedItemMatchesOrder || !quantityMatchesOrder) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: 'buyer must confirm that the received item and quantity match the protected order', code: 'ORDER_MATCH_CONFIRMATION_REQUIRED' });
      }
      const updated = await client.query(`UPDATE marketplace_orders SET status='COMPLETED',completed_at=NOW(),fulfillment_status='COMPLETED',fulfillment_status_updated_at=NOW(),updated_at=NOW() WHERE id=$1 AND status='INSPECTION' RETURNING *`, [id]);
      if (!updated.rows[0]) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'order changed before completion' }); }
      await client.query('UPDATE marketplace_listings SET quantity=GREATEST(0,quantity-$1),reserved_quantity=GREATEST(0,reserved_quantity-$1),active=CASE WHEN quantity-$1 <= 0 THEN FALSE ELSE active END,updated_at=NOW() WHERE id=$2', [order.quantity, order.listing_id]);
      await recordFulfillmentEvent(client, id, req.user.sub, 'ORDER_COMPLETED', 'INSPECTION', 'COMPLETED', { payout: 'eligible_for_release', fulfillmentMethod: order.fulfillment_method, receivedItemMatchesOrder: true, quantityMatchesOrder: true });
      await client.query('COMMIT');
      return res.json({ order: { id: String(updated.rows[0].id), status: updated.rows[0].status, fulfillmentStatus: updated.rows[0].fulfillment_status, completedAt: updated.rows[0].completed_at }, payout: { status: 'eligible_for_release' } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace complete', error); return res.status(500).json({ error: 'order completion failed' }); }
    finally { client.release(); }
  });
}
