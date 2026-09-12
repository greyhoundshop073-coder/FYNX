import crypto from 'node:crypto';
import { inspectTrustSafetyText } from './trustSafety.js';
import { calculateMarketplaceShipping, ensureMarketplaceShippingSchema, isMarketplaceDestinationCovered } from './marketplaceShipping.js';

export function registerMarketplaceCheckoutOrderRoutes({ app, pool, auth }) {
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = ensureMarketplaceShippingSchema(pool).catch((error) => { schemaPromise = undefined; throw error; });
    }
    return schemaPromise;
  };
  const positiveInt = (value) => { const n = Number(value); return Number.isInteger(n) && n > 0 ? n : null; };
  const uuid = (value) => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;
  const clean = (value, max = 200) => typeof value === 'string' ? value.trim().slice(0, max) : '';
  const feeConfig = () => {
    const mode = ['ZERO', 'BUYER', 'SELLER', 'SPLIT'].includes(String(process.env.FYNX_MARKETPLACE_FEE_MODE || 'ZERO').toUpperCase()) ? String(process.env.FYNX_MARKETPLACE_FEE_MODE || 'ZERO').toUpperCase() : 'ZERO';
    const bps = Math.min(10000, Math.max(0, Number.parseInt(process.env.FYNX_MARKETPLACE_FEE_BPS || '0', 10) || 0));
    const fixed = Math.max(0, Number(process.env.FYNX_MARKETPLACE_FEE_FIXED || '0') || 0);
    const buyerShare = Math.min(10000, Math.max(0, Number.parseInt(process.env.FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS || '5000', 10) || 0));
    const version = String(process.env.FYNX_MARKETPLACE_FEE_POLICY_VERSION || '1').trim().slice(0, 64) || '1';
    return { mode, bps, fixed, buyerShare, version };
  };

  app.post('/api/marketplace/checkout/order', auth, async (req, res) => {
    const listingId = positiveInt(req.body?.listingId); const quantity = positiveInt(req.body?.quantity); const orderId = uuid(req.body?.orderId) || crypto.randomUUID();
    const method = clean(req.body?.fulfillmentMethod, 20).toUpperCase(); const buyerNote = clean(req.body?.buyerNote, 1000);
    const address = { name: clean(req.body?.name, 120), phone: clean(req.body?.phone, 40), address: clean(req.body?.address, 300), city: clean(req.body?.city, 120), state: clean(req.body?.state, 120), country: clean(req.body?.country, 120) };
    if (!listingId || !quantity) return res.status(400).json({ error: 'valid listingId and quantity are required' });
    if (!['DELIVERY', 'PICKUP'].includes(method)) return res.status(400).json({ error: 'choose delivery or pickup' });
    if (method === 'DELIVERY' && (!address.name || !address.phone || !address.address)) return res.status(400).json({ error: 'name, phone and delivery address are required for delivery' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const safety = (await client.query(`SELECT marketplace_safety,account_status FROM fynx_account_safety WHERE user_id=$1 LIMIT 1`, [req.user.sub])).rows[0] || { marketplace_safety: true, account_status: 'ACTIVE' };
      if (String(safety.account_status) === 'LOCKED') { await client.query('ROLLBACK'); return res.status(403).json({ error: 'account is locked', code: 'ACCOUNT_LOCKED' }); }
      if (String(safety.account_status) === 'LIMITED') { await client.query('ROLLBACK'); return res.status(403).json({ error: 'account is temporarily limited from marketplace purchases', code: 'ACCOUNT_LIMITED' }); }
      const existing = (await client.query(`SELECT * FROM marketplace_orders WHERE id=$1 AND buyer_id=$2 LIMIT 1`, [orderId, req.user.sub])).rows[0];
      if (existing) { await client.query('ROLLBACK'); return res.status(200).json({ order: { id: String(existing.id), status: existing.status }, idempotent: true }); }
      const listing = (await client.query(`SELECT l.*,u.username AS seller_username,u.display_name AS seller_display_name FROM marketplace_listings l JOIN users u ON u.id=l.seller_id WHERE l.id=$1 AND l.active=TRUE FOR UPDATE`, [listingId])).rows[0];
      if (!listing) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'listing not found or no longer available' }); }
      if (String(listing.seller_id) === String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'you cannot purchase your own listing' }); }
      const blocked = await client.query(`SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1`, [req.user.sub, listing.seller_id]);
      if (blocked.rowCount) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'listing unavailable' }); }
      if (safety.marketplace_safety) { const safetyResult = inspectTrustSafetyText([listing.title, listing.description, listing.location].filter(Boolean).join(' ')); if (safetyResult.shouldBlock) { await client.query('ROLLBACK'); return res.status(422).json({ error: 'listing blocked by marketplace safety protection', code: 'SAFETY_BLOCK' }); } }
      if (method === 'DELIVERY' && !listing.delivery_available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is no longer available for this listing' }); }
      if (method === 'PICKUP' && !listing.pickup_available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup is no longer available for this listing' }); }
      if (method === 'DELIVERY' && !(await isMarketplaceDestinationCovered(client, listing.id, address))) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'seller does not currently deliver to this destination', code: 'DESTINATION_NOT_COVERED' }); }
      const available = Number(listing.quantity) - Number(listing.reserved_quantity || 0);
      if (quantity > available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'requested quantity is not available', code: 'INSUFFICIENT_STOCK' }); }
      const unitPrice = Number(listing.price); const productSubtotal = Math.round(unitPrice * quantity * 100) / 100;
      const shipping = calculateMarketplaceShipping(listing, quantity, method); const deliveryFee = shipping.fee;
      const config = feeConfig(); const totalFee = Math.max(0, Math.round((productSubtotal * (config.bps / 10000) + config.fixed) * 100) / 100);
      const buyerShare = config.mode === 'BUYER' ? 1 : config.mode === 'SELLER' ? 0 : config.mode === 'SPLIT' ? config.buyerShare / 10000 : 0;
      const marketplaceFeeBuyer = Math.round(totalFee * buyerShare * 100) / 100; const marketplaceFeeSeller = Math.round((totalFee - marketplaceFeeBuyer) * 100) / 100;
      const buyerTotal = Math.round((productSubtotal + deliveryFee + marketplaceFeeBuyer) * 100) / 100; const sellerNetAmount = Math.round((productSubtotal + deliveryFee - marketplaceFeeSeller) * 100) / 100;
      if (!(buyerTotal > 0) || sellerNetAmount < 0) { await client.query('ROLLBACK'); return res.status(500).json({ error: 'marketplace accounting calculation failed' }); }
      const shippingAddress = method === 'DELIVERY' ? address : null;
      const snapshot = { listingId: String(listing.id), sellerId: String(listing.seller_id), sellerUsername: listing.seller_username, sellerDisplayName: listing.seller_display_name, storeName: listing.store_name, title: listing.title, description: listing.description, price: unitPrice, currency: listing.currency, category: listing.category, condition: listing.condition, location: listing.location, deliveryAvailable: Boolean(listing.delivery_available), pickupAvailable: Boolean(listing.pickup_available), deliveryFee, shippingProvider: shipping.provider, shippingFeePolicy: shipping.feePolicy, shippingNote: shipping.note, productSubtotal, marketplaceFee: totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, paymentProviderFee: 0, discountAmount: 0, buyerTotal, sellerNetAmount, feePolicy: config.mode, feePolicyVersion: config.version, providerFeePayer: 'FYNX', fulfillmentMethod: method, shippingAddress, buyerNote, mediaIds: Array.isArray(listing.media_ids) ? listing.media_ids.map(String) : [] };
      const inserted = await client.query(`INSERT INTO marketplace_orders (id,buyer_id,seller_id,listing_id,quantity,unit_price,delivery_fee,product_subtotal,marketplace_fee,marketplace_fee_buyer,marketplace_fee_seller,payment_provider_fee,discount_amount,buyer_total,seller_net_amount,fee_policy,fee_policy_version,provider_fee_payer,total_amount,currency,product_snapshot,status,fulfillment_method,shipping_address,buyer_note,shipping_method,shipping_note,shipping_fee_policy) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,0,0,$12,$13,$14,$15,'FYNX',$12,$16,$17::jsonb,'PAYMENT_PENDING',$18,$19::jsonb,$20,$21,$22,$23) RETURNING *`, [orderId, req.user.sub, listing.seller_id, listing.id, quantity, unitPrice, deliveryFee, productSubtotal, totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, buyerTotal, sellerNetAmount, config.mode, config.version, listing.currency, JSON.stringify(snapshot), method, shippingAddress ? JSON.stringify(shippingAddress) : null, buyerNote, shipping.provider, shipping.note, shipping.feePolicy]);
      await client.query(`UPDATE marketplace_listings SET reserved_quantity=reserved_quantity+$1,updated_at=NOW() WHERE id=$2`, [quantity, listing.id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'CHECKOUT_CONFIRMED',NULL,'PAYMENT_PENDING',$3::jsonb)`, [orderId, req.user.sub, JSON.stringify({ quantity, fulfillmentMethod: method, shippingProvider: shipping.provider, shippingFeePolicy: shipping.feePolicy, protected: true, buyerTotal, currency: listing.currency })]);
      await client.query('COMMIT');
      const row = inserted.rows[0];
      return res.status(201).json({ order: { id: String(row.id), buyerId: String(row.buyer_id), sellerId: String(row.seller_id), listingId: String(row.listing_id), quantity: Number(row.quantity), unitPrice: Number(row.unit_price), deliveryFee: Number(row.delivery_fee), productSubtotal: Number(row.product_subtotal), marketplaceFee: Number(row.marketplace_fee), marketplaceFeeBuyer: Number(row.marketplace_fee_buyer), marketplaceFeeSeller: Number(row.marketplace_fee_seller), discountAmount: Number(row.discount_amount), buyerTotal: Number(row.buyer_total), totalAmount: Number(row.total_amount), currency: row.currency, product: row.product_snapshot, status: row.status, fulfillmentMethod: row.fulfillment_method, shippingAddress: row.shipping_address, buyerNote: row.buyer_note, shippingProvider: row.shipping_method, shippingNote: row.shipping_note, shippingFeePolicy: row.shipping_fee_policy } });
    } catch (error) { await client.query('ROLLBACK').catch(() => {}); console.error('[marketplace-checkout-order]', error); return res.status(500).json({ error: 'could not create protected checkout order' }); }
    finally { client.release(); }
  });
}
