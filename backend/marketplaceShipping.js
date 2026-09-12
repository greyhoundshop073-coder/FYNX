export function registerMarketplaceShippingRoutes({ app, pool, auth }) {
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_method TEXT NOT NULL DEFAULT 'SELLER_ARRANGED';
        ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_note TEXT NOT NULL DEFAULT '';
        ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS additional_item_shipping_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (additional_item_shipping_fee >= 0);
        ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_fee_cap NUMERIC(14,2);
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_method TEXT NOT NULL DEFAULT 'SELLER_ARRANGED';
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_note TEXT NOT NULL DEFAULT '';
        ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_fee_policy TEXT NOT NULL DEFAULT 'FLAT_BASE';
      `).catch((error) => { schemaPromise = undefined; throw error; });
    }
    return schemaPromise;
  };

  const positiveInt = (value) => {
    const n = Number(value);
    return Number.isInteger(n) && n > 0 ? n : null;
  };
  const method = (value) => {
    const normalized = typeof value === 'string' ? value.trim().toUpperCase() : '';
    return normalized === 'DELIVERY' || normalized === 'PICKUP' ? normalized : null;
  };
  const money = (value) => {
    const n = Number(value);
    return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : 0;
  };

  // FYNX does not require a shipping company. The seller may arrange delivery
  // through a local courier, dispatch rider, transport company, or another
  // lawful carrier. The order stores the shipping method and fee policy so a
  // future carrier integration can replace the provider without changing the
  // protected-payment lifecycle.
  const calculate = (listing, quantity, fulfillmentMethod) => {
    if (fulfillmentMethod === 'PICKUP') {
      return {
        fee: 0,
        method: 'PICKUP',
        feePolicy: 'NO_SHIPPING_FOR_PICKUP',
        note: '',
        provider: 'BUYER_SELLER_HANDOVER'
      };
    }
    const base = money(listing.delivery_fee);
    const additional = money(listing.additional_item_shipping_fee);
    const cap = listing.shipping_fee_cap == null ? null : money(listing.shipping_fee_cap);
    let fee = base + Math.max(0, quantity - 1) * additional;
    if (cap != null) fee = Math.min(fee, cap);
    return {
      fee: Math.round(fee * 100) / 100,
      method: 'DELIVERY',
      feePolicy: additional > 0 ? (cap != null ? 'BASE_PLUS_ADDITIONAL_CAPPED' : 'BASE_PLUS_ADDITIONAL') : 'FLAT_BASE',
      note: typeof listing.shipping_note === 'string' ? listing.shipping_note : '',
      provider: typeof listing.shipping_method === 'string' && listing.shipping_method ? listing.shipping_method : 'SELLER_ARRANGED'
    };
  };

  app.post('/api/marketplace/shipping/options', auth, async (req, res) => {
    const listingId = positiveInt(req.body?.listingId);
    const quantity = positiveInt(req.body?.quantity);
    const fulfillmentMethod = method(req.body?.fulfillmentMethod);
    if (!listingId || !quantity || !fulfillmentMethod) {
      return res.status(400).json({ error: 'listing, quantity and fulfillment method are required' });
    }

    const client = await pool.connect();
    try {
      await ensureSchema();
      const listing = (await client.query(`
        SELECT id,seller_id,delivery_available,pickup_available,delivery_fee,
               shipping_method,shipping_note,additional_item_shipping_fee,shipping_fee_cap
        FROM marketplace_listings WHERE id=$1 AND active=TRUE LIMIT 1
      `, [listingId])).rows[0];
      if (!listing) return res.status(404).json({ error: 'listing not found or no longer available' });
      if (fulfillmentMethod === 'DELIVERY' && !Boolean(listing.delivery_available)) {
        return res.status(409).json({ error: 'delivery is not available for this listing' });
      }
      if (fulfillmentMethod === 'PICKUP' && !Boolean(listing.pickup_available)) {
        return res.status(409).json({ error: 'pickup is not available for this listing' });
      }

      const selected = calculate(listing, quantity, fulfillmentMethod);
      return res.json({
        options: [{
          id: selected.method === 'PICKUP' ? 'pickup' : 'seller-arranged-delivery',
          fulfillmentMethod: selected.method,
          provider: selected.provider,
          fee: selected.fee,
          currency: null,
          feePolicy: selected.feePolicy,
          note: selected.note,
          carrierRequired: false,
          buyerProtection: 'FYNX payment remains protected until the order reaches the required completion state'
        }]
      });
    } catch (error) {
      console.error('[marketplace-shipping-options]', error);
      return res.status(500).json({ error: 'shipping options could not be prepared' });
    } finally {
      client.release();
    }
  });

  return { calculate };
}
