import crypto from 'node:crypto';
import { inspectTrustSafetyText } from './trustSafety.js';
import { ensureMarketplaceShippingSchema } from './marketplaceShipping.js';

export function registerMarketplaceCheckoutOrderRoutes({ app, pool, auth }) {
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = ensureMarketplaceShippingSchema(pool).catch((error) => { schemaPromise = undefined; throw error; });
    }
    return schemaPromise;
  };
  const checkoutAddressHash = (address) => crypto.createHash('sha256').update(JSON.stringify({
    name: String(address?.name || '').trim(),
    phone: String(address?.phone || '').trim(),
    address: String(address?.address || '').trim(),
    city: String(address?.city || '').trim(),
    state: String(address?.state || '').trim(),
    country: String(address?.country || '').trim()
  })).digest('hex');
  const positiveInt = (value) => { const n = Number(value); return Number.isInteger(n) && n > 0 ? n : null; };
  const uuid = (value) => typeof value === 'string' && /^[0-9a-f-]{36}$/i.test(value.trim()) ? value.trim() : null;
  const clean = (value, max = 200) => typeof value === 'string' ? value.trim().slice(0, max) : '';
  const normalizeCurrency = (value) => { const currency = clean(value, 3).toUpperCase(); return /^[A-Z]{3}$/.test(currency) ? currency : null; };
  const paystackConfigured = () => typeof process.env.PAYSTACK_SECRET_KEY === 'string' && process.env.PAYSTACK_SECRET_KEY.trim().length > 0;
  const feeConfig = () => {
    const modeValue = String(process.env.FYNX_MARKETPLACE_FEE_MODE || 'SELLER').toUpperCase();
    const mode = ['ZERO', 'BUYER', 'SELLER', 'SPLIT'].includes(modeValue) ? modeValue : 'SELLER';
    const bps = Math.min(10000, Math.max(0, Number.parseInt(process.env.FYNX_MARKETPLACE_FEE_BPS || '500', 10) || 0));
    const fixed = Math.max(0, Number(process.env.FYNX_MARKETPLACE_FEE_FIXED || '0') || 0);
    const buyerShare = Math.min(10000, Math.max(0, Number.parseInt(process.env.FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS || '5000', 10) || 0));
    const version = String(process.env.FYNX_MARKETPLACE_FEE_POLICY_VERSION || '1').trim().slice(0, 64) || '1';
    return { mode, bps, fixed, buyerShare, version };
  };

  const publicCheckoutOrder = (row) => ({
    id: String(row.id),
    buyerId: String(row.buyer_id),
    sellerId: String(row.seller_id),
    listingId: String(row.listing_id),
    quantity: Number(row.quantity),
    unitPrice: Number(row.unit_price),
    deliveryFee: Number(row.delivery_fee),
    productSubtotal: Number(row.product_subtotal),
    marketplaceFee: Number(row.marketplace_fee),
    marketplaceFeeBuyer: Number(row.marketplace_fee_buyer),
    marketplaceFeeSeller: Number(row.marketplace_fee_seller),
    discountAmount: Number(row.discount_amount),
    buyerTotal: Number(row.buyer_total),
    totalAmount: Number(row.total_amount),
    currency: row.currency,
    product: row.product_snapshot,
    status: row.status,
    fulfillmentMethod: row.fulfillment_method,
    shippingAddress: row.shipping_address,
    buyerNote: row.buyer_note,
    shippingProvider: row.shipping_method,
    shippingNote: row.shipping_note,
    shippingFeePolicy: row.shipping_fee_policy
  });

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
      if (existing) {
        const sameCoreRequest =
          String(existing.listing_id) === String(listingId) &&
          Number(existing.quantity) === Number(quantity) &&
          String(existing.fulfillment_method).toUpperCase() === method;
        const existingAddress = existing.shipping_address || {};
        const sameDeliveryAddress = method !== 'DELIVERY' || (
          String(existingAddress.name || '') === address.name &&
          String(existingAddress.phone || '') === address.phone &&
          String(existingAddress.address || '') === address.address &&
          String(existingAddress.city || '') === address.city &&
          String(existingAddress.state || '') === address.state &&
          String(existingAddress.country || '') === address.country
        );
        if (!sameCoreRequest || !sameDeliveryAddress) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'order id is already bound to a different checkout request', code: 'ORDER_ID_REUSE_CONFLICT' });
        }
        await client.query('ROLLBACK');
        return res.status(200).json({ order: publicCheckoutOrder(existing), idempotent: true });
      }
      if (!paystackConfigured()) {
        await client.query('ROLLBACK');
        return res.status(503).json({ error: 'marketplace payments are not configured yet', code: 'PAYMENT_PROVIDER_NOT_CONFIGURED' });
      }
      const listing = (await client.query(`SELECT l.*,u.username AS seller_username,u.display_name AS seller_display_name FROM marketplace_listings l JOIN users u ON u.id=l.seller_id WHERE l.id=$1 AND l.active=TRUE FOR UPDATE`, [listingId])).rows[0];
      if (!listing) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'listing not found or no longer available' }); }
      if (String(listing.seller_id) === String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'you cannot purchase your own listing' }); }
      const blocked = await client.query(`SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1`, [req.user.sub, listing.seller_id]);
      if (blocked.rowCount) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'listing unavailable' }); }
      if (safety.marketplace_safety) { const safetyResult = inspectTrustSafetyText([listing.title, listing.description, listing.location].filter(Boolean).join(' ')); if (safetyResult.shouldBlock) { await client.query('ROLLBACK'); return res.status(422).json({ error: 'listing blocked by marketplace safety protection', code: 'SAFETY_BLOCK' }); } }
      const currency = normalizeCurrency(listing.currency);
      if (!currency) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'listing currency is invalid or not configured', code: 'INVALID_CURRENCY' }); }
      if (!['NGN','USD'].includes(currency)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'this Marketplace currency is not active yet', code: 'UNSUPPORTED_MARKETPLACE_CURRENCY' }); }
      if (method === 'DELIVERY' && !listing.delivery_available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is no longer available for this listing' }); }
      if (method === 'PICKUP' && !listing.pickup_available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup is no longer available for this listing' }); }
      const available = Number(listing.quantity) - Number(listing.reserved_quantity || 0);
      if (quantity > available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'requested quantity is not available', code: 'INSUFFICIENT_STOCK' }); }
      const quoteId = uuid(req.body?.quoteId);
      if (!quoteId) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'review the checkout total again before creating an order', code: 'CHECKOUT_QUOTE_REQUIRED' }); }
      const quoteRow = (await client.query(
        "SELECT payload FROM marketplace_checkout_quotes WHERE id=$1 AND buyer_id=$2 AND quote_type='SINGLE' AND consumed_at IS NULL AND expires_at>NOW() FOR UPDATE",
        [quoteId, req.user.sub]
      )).rows[0];
      const acceptedQuote = quoteRow?.payload;
      if (!acceptedQuote ||
          String(acceptedQuote.listingId) !== String(listing.id) ||
          String(acceptedQuote.sellerId) !== String(listing.seller_id) ||
          Number(acceptedQuote.quantity) !== quantity ||
          String(acceptedQuote.fulfillmentMethod || '').toUpperCase() !== method ||
          String(acceptedQuote.currency || '').toUpperCase() !== currency ||
          (method === 'DELIVERY' && acceptedQuote.shippingAddressHash !== checkoutAddressHash(address))) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'the reviewed checkout quote is missing, expired or no longer matches this order; review the exact total again', code: 'CHECKOUT_QUOTE_MISMATCH' });
      }
      const unitPrice = Number(acceptedQuote.unitPrice);
      const productSubtotal = Number(acceptedQuote.subtotal);
      const deliveryFee = Number(acceptedQuote.deliveryFee);
      const totalFee = Number(acceptedQuote.marketplaceFee);
      const marketplaceFeeBuyer = Number(acceptedQuote.marketplaceFeeBuyer);
      const marketplaceFeeSeller = Number(acceptedQuote.marketplaceFeeSeller);
      const buyerTotal = Number(acceptedQuote.total);
      const sellerNetAmount = Number(acceptedQuote.sellerNetAmount);
      const discountAmount = Number(acceptedQuote.discountAmount || 0);
      const feeMode = String(acceptedQuote.feePolicy || 'SELLER');
      const feeVersion = String(acceptedQuote.feePolicyVersion || '1');
      const shipping = {
        fee: deliveryFee,
        provider: String(acceptedQuote.shippingProvider || 'SELLER_ARRANGED'),
        feePolicy: String(acceptedQuote.shippingFeePolicy || 'FLAT_BASE'),
        note: String(acceptedQuote.shippingNote || ''),
        origin: acceptedQuote.shippingOrigin || null,
        destination: acceptedQuote.shippingDestination || null,
        routeId: acceptedQuote.shippingRouteId || null,
        rateSnapshot: acceptedQuote.shippingRate || null
      };
      const expectedSubtotal = Math.round(Number(acceptedQuote.unitPrice) * quantity * 100) / 100;
      if (![unitPrice, productSubtotal, deliveryFee, totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, buyerTotal, sellerNetAmount, discountAmount].every(Number.isFinite) ||
          unitPrice <= 0 || productSubtotal <= 0 || deliveryFee < 0 || totalFee < 0 || marketplaceFeeBuyer < 0 ||
          marketplaceFeeSeller < 0 || buyerTotal <= 0 || sellerNetAmount < 0 ||
          Math.abs(expectedSubtotal - productSubtotal) > 0.02 ||
          Math.abs(buyerTotal - (productSubtotal + deliveryFee + marketplaceFeeBuyer - discountAmount)) > 0.02) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'the saved checkout quote contains inconsistent amounts; review checkout again', code: 'CHECKOUT_QUOTE_INVALID' });
      }
      const shippingAddress = method === 'DELIVERY' ? address : null;
      const snapshot = { listingId: String(listing.id), sellerId: String(listing.seller_id), sellerUsername: listing.seller_username, sellerDisplayName: listing.seller_display_name, storeName: listing.store_name, title: listing.title, description: listing.description, price: unitPrice, currency, category: listing.category, condition: listing.condition, location: listing.location, deliveryAvailable: Boolean(listing.delivery_available), pickupAvailable: Boolean(listing.pickup_available), deliveryFee, shippingProvider: shipping.provider, shippingFeePolicy: shipping.feePolicy, shippingNote: shipping.note, shippingOrigin: shipping.origin, shippingDestination: shipping.destination, shippingRouteId: shipping.routeId, shippingRate: shipping.rateSnapshot, checkoutQuoteId: quoteId, productSubtotal, marketplaceFee: totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, paymentProviderFee: 0, discountAmount, buyerTotal, sellerNetAmount, feePolicy: feeMode, feePolicyVersion: feeVersion, providerFeePayer: 'FYNX', fulfillmentMethod: method, shippingAddress, buyerNote, mediaIds: Array.isArray(listing.media_ids) ? listing.media_ids.map(String) : [] };
      const inserted = await client.query(`INSERT INTO marketplace_orders (id,buyer_id,seller_id,listing_id,quantity,unit_price,delivery_fee,product_subtotal,marketplace_fee,marketplace_fee_buyer,marketplace_fee_seller,payment_provider_fee,discount_amount,buyer_total,seller_net_amount,fee_policy,fee_policy_version,provider_fee_payer,total_amount,currency,product_snapshot,status,fulfillment_method,shipping_address,buyer_note,shipping_method,shipping_note,shipping_fee_policy,checkout_quote_id) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,0,0,$12,$13,$14,$15,'FYNX',$12,$16,$17::jsonb,'PAYMENT_PENDING',$18,$19::jsonb,$20,$21,$22,$23,$24) RETURNING *`, [orderId, req.user.sub, listing.seller_id, listing.id, quantity, unitPrice, deliveryFee, productSubtotal, totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, buyerTotal, sellerNetAmount, feeMode, feeVersion, currency, JSON.stringify(snapshot), method, shippingAddress ? JSON.stringify(shippingAddress) : null, buyerNote, shipping.provider, shipping.note, shipping.feePolicy, quoteId]);
      await client.query(`UPDATE marketplace_listings SET reserved_quantity=reserved_quantity+$1,updated_at=NOW() WHERE id=$2`, [quantity, listing.id]);
      await client.query(`INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,'CHECKOUT_CONFIRMED',NULL,'PAYMENT_PENDING',$3::jsonb)`, [orderId, req.user.sub, JSON.stringify({ quantity, fulfillmentMethod: method, shippingProvider: shipping.provider, shippingFeePolicy: shipping.feePolicy, shippingOrigin: shipping.origin, shippingDestination: shipping.destination, checkoutQuoteId: quoteId, protected: true, buyerTotal, currency })]);
      await client.query('UPDATE marketplace_checkout_quotes SET consumed_at=NOW() WHERE id=$1 AND consumed_at IS NULL', [quoteId]);
      await client.query('COMMIT');
      const row = inserted.rows[0];
      return res.status(201).json({ order: publicCheckoutOrder(row) });
    } catch (error) { await client.query('ROLLBACK').catch(() => {}); console.error('[marketplace-checkout-order]', error); return res.status(500).json({ error: 'could not create protected checkout order' }); }
    finally { client.release(); }
  });
  app.post('/api/marketplace/checkout/multi-order', auth, async (req, res) => {
    const rawItems = Array.isArray(req.body?.items) ? req.body.items : [];
    const checkoutId = uuid(req.body?.checkoutId);
    if (!checkoutId) return res.status(400).json({ error: 'review the cart total before creating protected checkout', code: 'CHECKOUT_QUOTE_REQUIRED' });
    if (!rawItems.length) return res.status(400).json({ error: 'at least one cart item is required' });
    if (rawItems.length > 20) return res.status(400).json({ error: 'a multi-product checkout supports at most 20 different products' });
    const items = rawItems.map((item) => ({ listingId: positiveInt(item?.listingId), quantity: positiveInt(item?.quantity), method: clean(item?.fulfillmentMethod, 20).toUpperCase(), address: { name: clean(item?.name, 120), phone: clean(item?.phone, 40), address: clean(item?.address, 300), city: clean(item?.city, 120), state: clean(item?.state, 120), country: clean(item?.country, 120) } }));
    if (items.some((item) => !item.listingId || !item.quantity || !['DELIVERY', 'PICKUP'].includes(item.method))) return res.status(400).json({ error: 'every cart item requires a valid listing, quantity and fulfillment method' });
    if (items.some((item) => item.method === 'DELIVERY' && (!item.address.name || !item.address.phone || !item.address.address))) return res.status(400).json({ error: 'each delivery item requires name, phone and delivery address' });
    if (new Set(items.map((item) => item.listingId)).size !== items.length) return res.status(409).json({ error: 'a listing may appear only once in a multi-product checkout', code: 'DUPLICATE_LISTING' });
    const client = await pool.connect();
    try {
      await ensureSchema(); await client.query('BEGIN');
      const safety = (await client.query('SELECT marketplace_safety,account_status FROM fynx_account_safety WHERE user_id=$1 LIMIT 1', [req.user.sub])).rows[0] || { marketplace_safety: true, account_status: 'ACTIVE' };
      if (String(safety.account_status) === 'LOCKED') { await client.query('ROLLBACK'); return res.status(403).json({ error: 'account is locked', code: 'ACCOUNT_LOCKED' }); }
      if (String(safety.account_status) === 'LIMITED') { await client.query('ROLLBACK'); return res.status(403).json({ error: 'account is temporarily limited from marketplace purchases', code: 'ACCOUNT_LIMITED' }); }
      await client.query('CREATE TABLE IF NOT EXISTS marketplace_checkout_groups (id UUID PRIMARY KEY,buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,currency TEXT NOT NULL,subtotal NUMERIC(14,2) NOT NULL CHECK (subtotal >= 0),delivery_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (delivery_fee >= 0),marketplace_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (marketplace_fee >= 0),buyer_total NUMERIC(14,2) NOT NULL CHECK (buyer_total > 0),status TEXT NOT NULL CHECK (status IN (\'PAYMENT_PENDING\',\'PAID\',\'CANCELLED\',\'REFUNDED\')),created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW())');
      await client.query('CREATE TABLE IF NOT EXISTS marketplace_checkout_group_items (checkout_id UUID NOT NULL REFERENCES marketplace_checkout_groups(id) ON DELETE CASCADE,order_id UUID NOT NULL UNIQUE REFERENCES marketplace_orders(id) ON DELETE CASCADE,listing_id BIGINT NOT NULL REFERENCES marketplace_listings(id) ON DELETE RESTRICT,quantity INTEGER NOT NULL CHECK (quantity > 0),line_total NUMERIC(14,2) NOT NULL CHECK (line_total > 0),PRIMARY KEY (checkout_id,order_id))');
      await client.query('CREATE INDEX IF NOT EXISTS marketplace_checkout_groups_buyer_idx ON marketplace_checkout_groups (buyer_id, created_at DESC)');
      await client.query('CREATE INDEX IF NOT EXISTS marketplace_checkout_group_items_checkout_idx ON marketplace_checkout_group_items (checkout_id)');
      // Serialize requests that reuse the same checkout UUID, including when no row exists yet.
      // The transaction-scoped lock closes the race before the unique group row can be inserted.
      await client.query("SELECT pg_advisory_xact_lock(hashtext('fynx-marketplace-checkout:' || $1::text))", [checkoutId]);
      const existing = (await client.query('SELECT id,buyer_id,currency,subtotal,delivery_fee,marketplace_fee,buyer_total,status FROM marketplace_checkout_groups WHERE id=$1 FOR UPDATE', [checkoutId])).rows[0];
      if (existing && String(existing.buyer_id) !== String(req.user.sub)) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'checkout id is already in use', code: 'CHECKOUT_ID_REUSE_CONFLICT' });
      }
      if (existing) {
        const children = await client.query('SELECT gi.listing_id AS group_listing_id,gi.quantity AS group_quantity,o.* FROM marketplace_orders o JOIN marketplace_checkout_group_items gi ON gi.order_id=o.id WHERE gi.checkout_id=$1 ORDER BY gi.listing_id ASC', [checkoutId]);
        const requestedItems = [...items].sort((a, b) => a.listingId - b.listingId);
        const existingItems = children.rows;
        const requestedBuyerNote = clean(req.body?.buyerNote, 1000);
        const sameAddress = (stored, requested) => {
          const address = stored || {};
          return ['name', 'phone', 'address', 'city', 'state', 'country'].every((key) =>
            String(address[key] || '') === String(requested[key] || ''));
        };
        const sameRequest = requestedItems.length === existingItems.length
          && requestedItems.every((item, index) => {
            const order = existingItems[index];
            return Number(order.group_listing_id) === item.listingId
              && Number(order.group_quantity) === item.quantity
              && String(order.fulfillment_method || '').toUpperCase() === item.method
              && String(order.buyer_note || '') === requestedBuyerNote
              && (item.method !== 'DELIVERY' || sameAddress(order.shipping_address, item.address));
          });
        if (!sameRequest) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'checkout id is already bound to a different cart request', code: 'CHECKOUT_ID_REUSE_CONFLICT' });
        }
        await client.query('ROLLBACK'); return res.status(200).json({ checkout: existing, orders: children.rows.map(publicCheckoutOrder), idempotent: true });
      }
      if (!paystackConfigured()) {
        await client.query('ROLLBACK');
        return res.status(503).json({ error: 'marketplace payments are not configured yet', code: 'PAYMENT_PROVIDER_NOT_CONFIGURED' });
      }
      const quoteRow = (await client.query(
        "SELECT payload FROM marketplace_checkout_quotes WHERE id=$1 AND buyer_id=$2 AND quote_type='MULTI' AND consumed_at IS NULL AND expires_at>NOW() FOR UPDATE",
        [checkoutId, req.user.sub]
      )).rows[0];
      const acceptedQuote = quoteRow?.payload;
      const quotedItems = Array.isArray(acceptedQuote?.items) ? acceptedQuote.items : [];
      const quotedById = new Map(quotedItems.map((item) => [String(item.listingId), item]));
      if (!acceptedQuote || quotedItems.length !== items.length || String(acceptedQuote.id) !== checkoutId) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'the reviewed cart quote is missing or expired; review the cart total again', code: 'CHECKOUT_QUOTE_MISMATCH' });
      }
      const sortedItems = [...items].sort((a, b) => a.listingId - b.listingId);
      const lines = []; let checkoutCurrency = null;
      for (const item of sortedItems) {
        const listing = (await client.query('SELECT l.*,u.username AS seller_username,u.display_name AS seller_display_name FROM marketplace_listings l JOIN users u ON u.id=l.seller_id WHERE l.id=$1 AND l.active=TRUE FOR UPDATE', [item.listingId])).rows[0];
        if (!listing) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'one or more listings are no longer available' }); }
        if (String(listing.seller_id) === String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'you cannot purchase your own listing' }); }
        const blocked = await client.query('SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1', [req.user.sub, listing.seller_id]);
        if (blocked.rowCount) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'one or more listings are unavailable' }); }
        if (safety.marketplace_safety) { const safetyResult = inspectTrustSafetyText([listing.title, listing.description, listing.location].filter(Boolean).join(' ')); if (safetyResult.shouldBlock) { await client.query('ROLLBACK'); return res.status(422).json({ error: 'one or more listings are blocked by marketplace safety protection', code: 'SAFETY_BLOCK' }); } }
        const currency = normalizeCurrency(listing.currency);
        if (!['NGN','USD'].includes(currency)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'all selected Marketplace currencies must be active NGN or USD', code: 'UNSUPPORTED_MARKETPLACE_CURRENCY' }); }
        if (checkoutCurrency && checkoutCurrency !== currency) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'all products in one checkout must use the same currency', code: 'MIXED_CURRENCIES' }); }
        checkoutCurrency = currency;
        if (item.method === 'DELIVERY' && !listing.delivery_available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is no longer available for one of the selected listings' }); }
        if (item.method === 'PICKUP' && !listing.pickup_available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup is no longer available for one of the selected listings' }); }
        const quoted = quotedById.get(String(listing.id));
        if (!quoted || Number(quoted.quantity) !== item.quantity ||
            String(quoted.fulfillmentMethod || '').toUpperCase() !== item.method ||
            String(quoted.sellerId) !== String(listing.seller_id) ||
            String(quoted.currency || '').toUpperCase() !== currency ||
            (item.method === 'DELIVERY' && quoted.shippingAddressHash !== checkoutAddressHash(item.address))) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'a cart item no longer matches the reviewed quote; review the exact total again', code: 'CHECKOUT_QUOTE_MISMATCH', listingId: String(listing.id) });
        }
        const available = Number(listing.quantity) - Number(listing.reserved_quantity || 0);
        if (item.quantity > available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'requested quantity is not available for one of the selected products', code: 'INSUFFICIENT_STOCK' }); }
        const unitPrice = Number(quoted.unitPrice);
        const productSubtotal = Number(quoted.subtotal);
        const shipping = {
          fee: Number(quoted.deliveryFee),
          provider: String(quoted.shippingProvider || 'SELLER_ARRANGED'),
          feePolicy: String(quoted.shippingFeePolicy || 'FLAT_BASE'),
          note: String(quoted.shippingNote || ''),
          origin: quoted.shippingOrigin || null,
          destination: quoted.shippingDestination || null,
          routeId: quoted.shippingRouteId || null,
          rateSnapshot: quoted.shippingRate || null
        };
        const totalFee = Number(quoted.marketplaceFee);
        const marketplaceFeeBuyer = Number(quoted.marketplaceFeeBuyer);
        const marketplaceFeeSeller = Number(quoted.marketplaceFeeSeller);
        const buyerTotal = Number(quoted.total);
        const sellerNetAmount = Number(quoted.sellerNetAmount);
        const feeMode = String(acceptedQuote.feePolicy || 'SELLER');
        const feeVersion = String(acceptedQuote.feePolicyVersion || '1');
        if (![unitPrice, productSubtotal, shipping.fee, totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, buyerTotal, sellerNetAmount].every(Number.isFinite) ||
            unitPrice <= 0 || productSubtotal <= 0 || shipping.fee < 0 || totalFee < 0 || marketplaceFeeBuyer < 0 || marketplaceFeeSeller < 0 || buyerTotal <= 0 || sellerNetAmount < 0 ||
            Math.abs(productSubtotal - Math.round(unitPrice * item.quantity * 100) / 100) > 0.02 ||
            Math.abs(buyerTotal - (productSubtotal + shipping.fee + marketplaceFeeBuyer)) > 0.02) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'a saved cart quote contains inconsistent amounts; review checkout again', code: 'CHECKOUT_QUOTE_INVALID' });
        }
        lines.push({ item, listing, quoted, currency, unitPrice, productSubtotal, shipping, totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, buyerTotal, sellerNetAmount, feeMode, feeVersion });
      }
      const subtotal = Math.round(lines.reduce((sum, line) => sum + line.productSubtotal, 0) * 100) / 100;
      const deliveryFee = Math.round(lines.reduce((sum, line) => sum + line.shipping.fee, 0) * 100) / 100;
      const marketplaceFee = Math.round(lines.reduce((sum, line) => sum + line.marketplaceFeeBuyer, 0) * 100) / 100;
      const buyerTotal = Math.round(lines.reduce((sum, line) => sum + line.buyerTotal, 0) * 100) / 100;
      const insertedGroup = (await client.query('INSERT INTO marketplace_checkout_groups (id,buyer_id,currency,subtotal,delivery_fee,marketplace_fee,buyer_total,status) VALUES ($1,$2,$3,$4,$5,$6,$7,\'PAYMENT_PENDING\') RETURNING *', [checkoutId, req.user.sub, checkoutCurrency, subtotal, deliveryFee, marketplaceFee, buyerTotal])).rows[0];
      const orders = [];
      for (const line of lines) {
        const { item, listing, shipping, currency, unitPrice, productSubtotal, totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, buyerTotal: lineBuyerTotal, sellerNetAmount, feeMode, feeVersion } = line;
        const orderId = crypto.randomUUID(); const shippingAddress = item.method === 'DELIVERY' ? item.address : null; const buyerNote = clean(req.body?.buyerNote, 1000);
        const snapshot = { listingId: String(listing.id), sellerId: String(listing.seller_id), sellerUsername: listing.seller_username, sellerDisplayName: listing.seller_display_name, storeName: listing.store_name, title: listing.title, description: listing.description, price: unitPrice, currency, category: listing.category, condition: listing.condition, location: listing.location, deliveryAvailable: Boolean(listing.delivery_available), pickupAvailable: Boolean(listing.pickup_available), deliveryFee: shipping.fee, shippingProvider: shipping.provider, shippingFeePolicy: shipping.feePolicy, shippingNote: shipping.note, shippingOrigin: shipping.origin, shippingDestination: shipping.destination, shippingRouteId: shipping.routeId, shippingRate: shipping.rateSnapshot, checkoutQuoteId: checkoutId, productSubtotal, marketplaceFee: totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, paymentProviderFee: 0, discountAmount: 0, buyerTotal: lineBuyerTotal, sellerNetAmount, feePolicy: feeMode, feePolicyVersion: feeVersion, providerFeePayer: 'FYNX', fulfillmentMethod: item.method, shippingAddress, buyerNote, mediaIds: Array.isArray(listing.media_ids) ? listing.media_ids.map(String) : [] };
        const inserted = (await client.query('INSERT INTO marketplace_orders (id,buyer_id,seller_id,listing_id,quantity,unit_price,delivery_fee,product_subtotal,marketplace_fee,marketplace_fee_buyer,marketplace_fee_seller,payment_provider_fee,discount_amount,buyer_total,seller_net_amount,fee_policy,fee_policy_version,provider_fee_payer,total_amount,currency,product_snapshot,status,fulfillment_method,shipping_address,buyer_note,shipping_method,shipping_note,shipping_fee_policy,checkout_quote_id) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,0,0,$12,$13,$14,$15,\'FYNX\',$12,$16,$17::jsonb,\'PAYMENT_PENDING\',$18,$19::jsonb,$20,$21,$22,$23,$24) RETURNING *', [orderId, req.user.sub, listing.seller_id, listing.id, item.quantity, unitPrice, shipping.fee, productSubtotal, totalFee, marketplaceFeeBuyer, marketplaceFeeSeller, lineBuyerTotal, sellerNetAmount, feeMode, feeVersion, currency, JSON.stringify(snapshot), item.method, shippingAddress ? JSON.stringify(shippingAddress) : null, buyerNote, shipping.provider, shipping.note, shipping.feePolicy, checkoutId])).rows[0];
        await client.query('UPDATE marketplace_listings SET reserved_quantity=reserved_quantity+$1,updated_at=NOW() WHERE id=$2', [item.quantity, listing.id]);
        await client.query('INSERT INTO marketplace_checkout_group_items (checkout_id,order_id,listing_id,quantity,line_total) VALUES ($1,$2,$3,$4,$5)', [checkoutId, orderId, listing.id, item.quantity, lineBuyerTotal]);
        await client.query('INSERT INTO marketplace_order_events (order_id,actor_id,event_type,from_status,to_status,metadata) VALUES ($1,$2,\'CHECKOUT_CONFIRMED\',NULL,\'PAYMENT_PENDING\',$3::jsonb)', [orderId, req.user.sub, JSON.stringify({ quantity: item.quantity, fulfillmentMethod: item.method, shippingProvider: shipping.provider, shippingFeePolicy: shipping.feePolicy, shippingOrigin: shipping.origin, shippingDestination: shipping.destination, checkoutQuoteId: checkoutId, protected: true, buyerTotal: lineBuyerTotal, currency, checkoutId })]);
        orders.push(publicCheckoutOrder(inserted));
      }
      const quotedTotal = Number(acceptedQuote.total);
      if (!Number.isFinite(quotedTotal) || Math.abs(quotedTotal - buyerTotal) > 0.02 ||
          Math.abs(Number(acceptedQuote.subtotal) - subtotal) > 0.02 ||
          Math.abs(Number(acceptedQuote.deliveryFee) - deliveryFee) > 0.02) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'the protected cart totals do not match the quote you reviewed', code: 'CHECKOUT_QUOTE_TOTAL_MISMATCH' });
      }
      await client.query('UPDATE marketplace_checkout_quotes SET consumed_at=NOW() WHERE id=$1 AND consumed_at IS NULL', [checkoutId]);
      await client.query('COMMIT');
      return res.status(201).json({ checkout: insertedGroup, orders });
    } catch (error) { await client.query('ROLLBACK').catch(() => {}); console.error('[marketplace-multi-checkout-order]', error); return res.status(500).json({ error: 'could not create multi-product protected checkout' }); }
    finally { client.release(); }
  });

}
