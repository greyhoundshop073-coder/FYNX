import crypto from 'node:crypto';
import { inspectTrustSafetyText } from './trustSafety.js';
import { calculateMarketplaceShipping, ensureMarketplaceShippingSchema, isMarketplaceDestinationCovered } from './marketplaceShipping.js';

export function registerMarketplaceCheckoutRoutes({ app, pool, auth }) {
  const parsePositiveInt = (value) => { const n = Number(value); return Number.isInteger(n) && n > 0 ? n : null; };
  const normalizeMethod = (value) => { const method = typeof value === 'string' ? value.trim().toUpperCase() : ''; return method === 'DELIVERY' || method === 'PICKUP' ? method : null; };
  const normalizeCurrency = (value) => { const currency = typeof value === 'string' ? value.trim().toUpperCase() : ''; return /^[A-Z]{3}$/.test(currency) ? currency : null; };
  const feeConfig = () => {
    const modeValue = String(process.env.FYNX_MARKETPLACE_FEE_MODE || 'SELLER').toUpperCase();
    const mode = ['ZERO', 'BUYER', 'SELLER', 'SPLIT'].includes(modeValue) ? modeValue : 'SELLER';
    const bps = Math.min(10000, Math.max(0, Number.parseInt(process.env.FYNX_MARKETPLACE_FEE_BPS || '500', 10) || 0));
    const fixed = Math.max(0, Number(process.env.FYNX_MARKETPLACE_FEE_FIXED || '0') || 0);
    const buyerShare = Math.min(10000, Math.max(0, Number.parseInt(process.env.FYNX_MARKETPLACE_FEE_BUYER_SHARE_BPS || '5000', 10) || 0));
    const version = String(process.env.FYNX_MARKETPLACE_FEE_POLICY_VERSION || '1').trim().slice(0, 64) || '1';
    const providerFeePayer = String(process.env.FYNX_MARKETPLACE_PROVIDER_FEE_PAYER || 'FYNX').toUpperCase();
    if (providerFeePayer !== 'FYNX') throw new Error('unsupported marketplace provider fee payer');
    return { mode, bps, fixed, buyerShare, version, providerFeePayer };
  };
  const normalizeAddress = (value) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
    const name = typeof value.name === 'string' ? value.name.trim().slice(0, 120) : '';
    const phone = typeof value.phone === 'string' ? value.phone.trim().slice(0, 40) : '';
    const address = typeof value.address === 'string' ? value.address.trim().slice(0, 500) : '';
    if (!name || !phone || !address) return null;
    return { name, phone, address, city: typeof value.city === 'string' ? value.city.trim().slice(0, 100) : '', state: typeof value.state === 'string' ? value.state.trim().slice(0, 100) : '', country: typeof value.country === 'string' ? value.country.trim().slice(0, 100) : '' };
  };
  app.post('/api/marketplace/checkout/quote', auth, async (req, res) => {
    const listingId = parsePositiveInt(req.body?.listingId); const quantity = parsePositiveInt(req.body?.quantity); const fulfillmentMethod = normalizeMethod(req.body?.fulfillmentMethod);
    if (!listingId || !quantity || !fulfillmentMethod) return res.status(400).json({ error: 'listing, quantity and fulfillment method are required' });
    const shippingAddress = fulfillmentMethod === 'DELIVERY' ? normalizeAddress(req.body?.shippingAddress) : null;
    if (fulfillmentMethod === 'DELIVERY' && !shippingAddress) return res.status(400).json({ error: 'delivery requires name, phone and address' });
    const client = await pool.connect();
    try {
      await ensureMarketplaceShippingSchema(pool);
      const safety = (await client.query('SELECT marketplace_safety,account_status FROM fynx_account_safety WHERE user_id=$1 LIMIT 1', [req.user.sub])).rows[0] || { marketplace_safety: true, account_status: 'ACTIVE' };
      if (String(safety.account_status) === 'LOCKED') return res.status(403).json({ error: 'account is locked', code: 'ACCOUNT_LOCKED' });
      if (String(safety.account_status) === 'LIMITED') return res.status(403).json({ error: 'account is temporarily limited from marketplace purchases', code: 'ACCOUNT_LIMITED' });
      await client.query('BEGIN');
      const listing = (await client.query(`SELECT l.*,u.username AS seller_username,u.display_name AS seller_display_name FROM marketplace_listings l JOIN users u ON u.id=l.seller_id WHERE l.id=$1 AND l.active=TRUE FOR UPDATE`, [listingId])).rows[0];
      if (!listing) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'listing not found or no longer available' }); }
      if (String(listing.seller_id) === String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'you cannot purchase your own listing' }); }
      const blocked = await client.query('SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1', [req.user.sub, listing.seller_id]);
      if (blocked.rowCount) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'listing unavailable' }); }
      if (Boolean(safety.marketplace_safety)) {
        const safetyResult = inspectTrustSafetyText([listing.title, listing.description, listing.location].filter(Boolean).join(' '));
        if (safetyResult.shouldBlock) { await client.query('ROLLBACK'); return res.status(422).json({ error: 'listing blocked by marketplace safety protection', code: 'SAFETY_BLOCK' }); }
      }
      const currency = normalizeCurrency(listing.currency);
      if (!currency || !['NGN', 'USD'].includes(currency)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'listing currency is invalid or not active', code: 'INVALID_CURRENCY' }); }
      const available = Number(listing.quantity) - Number(listing.reserved_quantity || 0);
      if (quantity > available) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'requested quantity is not available', availableQuantity: Math.max(0, available) }); }
      if (fulfillmentMethod === 'DELIVERY' && !Boolean(listing.delivery_available)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is not available for this listing' }); }
      if (fulfillmentMethod === 'PICKUP' && !Boolean(listing.pickup_available)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'pickup is not available for this listing' }); }
      if (fulfillmentMethod === 'DELIVERY' && !(await isMarketplaceDestinationCovered(client, listing.id, shippingAddress))) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'seller does not currently deliver to this destination', code: 'DESTINATION_NOT_COVERED' });
      }
      const unitPrice = Number(listing.price); const productSubtotal = Math.round(unitPrice * quantity * 100) / 100;
      const shipping = calculateMarketplaceShipping(listing, quantity, fulfillmentMethod); const deliveryFee = shipping.fee;
      const config = feeConfig(); const rawFee = Math.round((productSubtotal * (config.bps / 10000) + config.fixed) * 100) / 100; const marketplaceFee = Math.max(0, rawFee);
      const buyerShare = config.mode === 'BUYER' ? 1 : config.mode === 'SELLER' ? 0 : config.mode === 'SPLIT' ? config.buyerShare / 10000 : 0;
      const marketplaceFeeBuyer = Math.round(marketplaceFee * buyerShare * 100) / 100; const marketplaceFeeSeller = Math.round((marketplaceFee - marketplaceFeeBuyer) * 100) / 100;
      const discountAmount = 0; const buyerTotal = Math.round((productSubtotal + deliveryFee + marketplaceFeeBuyer - discountAmount) * 100) / 100; const sellerNetAmount = Math.round((productSubtotal + deliveryFee - marketplaceFeeSeller) * 100) / 100;
      if (!(buyerTotal > 0) || sellerNetAmount < 0) { await client.query('ROLLBACK'); return res.status(500).json({ error: 'checkout total calculation failed' }); }
      const quoteId = crypto.randomUUID(); const expiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString();
      await client.query('COMMIT');
      return res.json({ quote: { id: quoteId, expiresAt, listingId: String(listing.id), listingUpdatedAt: listing.updated_at || null, sellerId: String(listing.seller_id), sellerUsername: listing.seller_username, sellerDisplayName: listing.seller_display_name, productTitle: listing.title, quantity, unitPrice, currency, fulfillmentMethod, shippingAddress, shippingProvider: shipping.provider, shippingFeePolicy: shipping.feePolicy, shippingNote: shipping.note, subtotal: productSubtotal, deliveryFee, marketplaceFee, marketplaceFeeBuyer, marketplaceFeeSeller, discountAmount, total: buyerTotal, feePolicy: config.mode, feePolicyVersion: config.version, providerFeePayer: config.providerFeePayer, discount: { amount: 0, code: null, status: 'NOT_APPLIED' }, protection: { payment: 'PROTECTED', payout: 'RELEASED_AFTER_BUYER_CONFIRMATION' } } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('marketplace checkout quote', error); return res.status(500).json({ error: 'checkout quote could not be prepared' }); }
    finally { client.release(); }
  });

  // Multi-product quote foundation. This deliberately stops before reservation/payment:
  // every cart line is validated and priced independently, while the buyer still gets
  // one authoritative aggregate quote. The later order stage will create protected
  // child orders from this same contract.
  app.post('/api/marketplace/checkout/multi-quote', auth, async (req, res) => {
    const items = Array.isArray(req.body?.items) ? req.body.items : [];
    if (!items.length) return res.status(400).json({ error: 'at least one cart item is required' });
    if (items.length > 20) return res.status(400).json({ error: 'a multi-product checkout can contain at most 20 different products' });

    const normalizedItems = items.map((item) => {
      const listingId = parsePositiveInt(item?.listingId);
      const quantity = parsePositiveInt(item?.quantity);
      const fulfillmentMethod = normalizeMethod(item?.fulfillmentMethod);
      const shippingAddress = fulfillmentMethod === 'DELIVERY' ? normalizeAddress(item?.shippingAddress) : null;
      return { listingId, quantity, fulfillmentMethod, shippingAddress };
    });
    if (normalizedItems.some((item) => !item.listingId || !item.quantity || !item.fulfillmentMethod)) {
      return res.status(400).json({ error: 'every cart item requires listing, quantity and fulfillment method' });
    }
    if (new Set(normalizedItems.map((item) => item.listingId)).size !== normalizedItems.length) {
      return res.status(400).json({ error: 'a listing may appear only once in a multi-product checkout' });
    }
    if (normalizedItems.some((item) => item.fulfillmentMethod === 'DELIVERY' && !item.shippingAddress)) {
      return res.status(400).json({ error: 'delivery items require name, phone and address' });
    }

    const client = await pool.connect();
    try {
      await ensureMarketplaceShippingSchema(pool);
      const safety = (await client.query('SELECT marketplace_safety,account_status FROM fynx_account_safety WHERE user_id=$1 LIMIT 1', [req.user.sub])).rows[0] || { marketplace_safety: true, account_status: 'ACTIVE' };
      if (String(safety.account_status) === 'LOCKED') return res.status(403).json({ error: 'account is locked', code: 'ACCOUNT_LOCKED' });
      if (String(safety.account_status) === 'LIMITED') return res.status(403).json({ error: 'account is temporarily limited from marketplace purchases', code: 'ACCOUNT_LIMITED' });

      await client.query('BEGIN');
      const quotedItems = [];
      let checkoutCurrency = null;
      let subtotal = 0;
      let deliveryFee = 0;
      let marketplaceFee = 0;
      let marketplaceFeeBuyer = 0;
      let marketplaceFeeSeller = 0;
      const config = feeConfig();

      for (const item of normalizedItems) {
        const listing = (await client.query(
          'SELECT l.*,u.username AS seller_username,u.display_name AS seller_display_name FROM marketplace_listings l JOIN users u ON u.id=l.seller_id WHERE l.id=$1 AND l.active=TRUE FOR UPDATE',
          [item.listingId]
        )).rows[0];
        if (!listing) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'one or more listings are no longer available' }); }
        if (String(listing.seller_id) === String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'you cannot purchase your own listing' }); }

        const blocked = await client.query(
          'SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1',
          [req.user.sub, listing.seller_id]
        );
        if (blocked.rowCount) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'one or more listings are unavailable' }); }

        if (Boolean(safety.marketplace_safety)) {
          const safetyResult = inspectTrustSafetyText([listing.title, listing.description, listing.location].filter(Boolean).join(' '));
          if (safetyResult.shouldBlock) { await client.query('ROLLBACK'); return res.status(422).json({ error: 'one or more listings are blocked by marketplace safety protection', code: 'SAFETY_BLOCK' }); }
        }

        const currency = normalizeCurrency(listing.currency);
        if (!currency || !['NGN', 'USD'].includes(currency)) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'all Marketplace checkout items must use an active NGN or USD currency', code: 'INVALID_CURRENCY' });
        }
        if (checkoutCurrency && checkoutCurrency !== currency) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'a multi-product checkout cannot mix NGN and USD', code: 'MIXED_CURRENCIES' });
        }
        checkoutCurrency = currency;

        const available = Number(listing.quantity) - Number(listing.reserved_quantity || 0);
        if (item.quantity > available) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'requested quantity is not available for one or more listings', code: 'INSUFFICIENT_STOCK', availableQuantity: Math.max(0, available), listingId: String(listing.id) });
        }
        if (item.fulfillmentMethod === 'DELIVERY' && !Boolean(listing.delivery_available)) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'delivery is not available for one or more listings', listingId: String(listing.id) });
        }
        if (item.fulfillmentMethod === 'PICKUP' && !Boolean(listing.pickup_available)) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'pickup is not available for one or more listings', listingId: String(listing.id) });
        }
        if (item.fulfillmentMethod === 'DELIVERY' && !(await isMarketplaceDestinationCovered(client, listing.id, item.shippingAddress))) {
          await client.query('ROLLBACK');
          return res.status(409).json({ error: 'one or more sellers do not currently deliver to the requested destination', code: 'DESTINATION_NOT_COVERED', listingId: String(listing.id) });
        }

        const unitPrice = Number(listing.price);
        const productSubtotal = Math.round(unitPrice * item.quantity * 100) / 100;
        const shipping = calculateMarketplaceShipping(listing, item.quantity, item.fulfillmentMethod);
        const lineDeliveryFee = shipping.fee;
        const rawFee = Math.round((productSubtotal * (config.bps / 10000) + config.fixed) * 100) / 100;
        const lineMarketplaceFee = Math.max(0, rawFee);
        const buyerShare = config.mode === 'BUYER' ? 1 : config.mode === 'SELLER' ? 0 : config.mode === 'SPLIT' ? config.buyerShare / 10000 : 0;
        const lineFeeBuyer = Math.round(lineMarketplaceFee * buyerShare * 100) / 100;
        const lineFeeSeller = Math.round((lineMarketplaceFee - lineFeeBuyer) * 100) / 100;
        const lineBuyerTotal = Math.round((productSubtotal + lineDeliveryFee + lineFeeBuyer) * 100) / 100;
        const lineSellerNet = Math.round((productSubtotal + lineDeliveryFee - lineFeeSeller) * 100) / 100;
        if (!(lineBuyerTotal > 0) || lineSellerNet < 0) {
          await client.query('ROLLBACK');
          return res.status(500).json({ error: 'multi-product checkout total calculation failed', listingId: String(listing.id) });
        }

        subtotal += productSubtotal;
        deliveryFee += lineDeliveryFee;
        marketplaceFee += lineMarketplaceFee;
        marketplaceFeeBuyer += lineFeeBuyer;
        marketplaceFeeSeller += lineFeeSeller;
        quotedItems.push({
          listingId: String(listing.id),
          sellerId: String(listing.seller_id),
          sellerUsername: listing.seller_username,
          sellerDisplayName: listing.seller_display_name,
          productTitle: listing.title,
          quantity: item.quantity,
          unitPrice,
          currency,
          fulfillmentMethod: item.fulfillmentMethod,
          shippingAddress: item.shippingAddress,
          shippingProvider: shipping.provider,
          shippingFeePolicy: shipping.feePolicy,
          shippingNote: shipping.note,
          subtotal: productSubtotal,
          deliveryFee: lineDeliveryFee,
          marketplaceFee: lineMarketplaceFee,
          marketplaceFeeBuyer: lineFeeBuyer,
          marketplaceFeeSeller: lineFeeSeller,
          total: lineBuyerTotal,
          sellerNetAmount: lineSellerNet
        });
      }

      const total = Math.round((subtotal + deliveryFee + marketplaceFeeBuyer) * 100) / 100;
      if (!(total > 0)) {
        await client.query('ROLLBACK');
        return res.status(500).json({ error: 'multi-product checkout total calculation failed' });
      }
      const quoteId = crypto.randomUUID();
      const expiresAt = new Date(Date.now() + 5 * 60 * 1000).toISOString();
      await client.query('COMMIT');
      return res.json({
        quote: {
          id: quoteId,
          expiresAt,
          itemCount: quotedItems.length,
          currency: checkoutCurrency,
          items: quotedItems,
          subtotal: Math.round(subtotal * 100) / 100,
          deliveryFee: Math.round(deliveryFee * 100) / 100,
          marketplaceFee: Math.round(marketplaceFee * 100) / 100,
          marketplaceFeeBuyer: Math.round(marketplaceFeeBuyer * 100) / 100,
          marketplaceFeeSeller: Math.round(marketplaceFeeSeller * 100) / 100,
          discountAmount: 0,
          total,
          feePolicy: config.mode,
          feePolicyVersion: config.version,
          providerFeePayer: config.providerFeePayer,
          protection: { payment: 'PROTECTED', payout: 'RELEASED_AFTER_BUYER_CONFIRMATION' }
        }
      });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('marketplace multi-product checkout quote', error);
      return res.status(500).json({ error: 'multi-product checkout quote could not be prepared' });
    } finally {
      client.release();
    }
  });
}
