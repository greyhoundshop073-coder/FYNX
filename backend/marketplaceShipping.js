export const NIGERIA_STATES = Object.freeze([
  'Abia', 'Adamawa', 'Akwa Ibom', 'Anambra', 'Bauchi', 'Bayelsa', 'Benue',
  'Borno', 'Cross River', 'Delta', 'Ebonyi', 'Edo', 'Ekiti', 'Enugu', 'Gombe',
  'Imo', 'Jigawa', 'Kaduna', 'Kano', 'Katsina', 'Kebbi', 'Kogi', 'Kwara',
  'Lagos', 'Nasarawa', 'Niger', 'Ogun', 'Ondo', 'Osun', 'Oyo', 'Plateau',
  'Rivers', 'Sokoto', 'Taraba', 'Yobe', 'Zamfara', 'FCT'
]);

function normalizedStateKey(value) {
  return typeof value === 'string'
    ? value.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toUpperCase().replace(/[^A-Z0-9]+/g, ' ').trim()
    : '';
}

export function normalizeNigerianState(value) {
  const key = normalizedStateKey(value);
  if (!key) return null;
  if (['ABUJA', 'F C T', 'FEDERAL CAPITAL TERRITORY', 'FEDERAL CAPITAL TERRITORY FCT'].includes(key)) return 'FCT';
  return NIGERIA_STATES.find((state) => normalizedStateKey(state) === key) || null;
}

export function normalizeNigerianCity(value) {
  return typeof value === 'string' ? value.trim().replace(/\s+/g, ' ').slice(0, 120).toUpperCase() : '';
}

export function matchMarketplaceShippingRate(rates, destinationState, destinationCity) {
  const state = normalizeNigerianState(destinationState);
  if (!state || !Array.isArray(rates)) return null;
  const city = normalizeNigerianCity(destinationCity);
  const matches = rates.filter((row) => normalizeNigerianState(row.destination_state ?? row.destinationState) === state);
  if (city) {
    const exactCity = matches.find((row) => normalizeNigerianCity(row.destination_city ?? row.destinationCity) === city);
    if (exactCity) return exactCity;
  }
  return matches.find((row) => !normalizeNigerianCity(row.destination_city ?? row.destinationCity)) || null;
}

export function calculateMarketplaceRouteShipping(listing, rate, quantity, fulfillmentMethod, address = {}) {
  if (fulfillmentMethod === 'PICKUP') {
    return { fee: 0, method: 'PICKUP', feePolicy: 'NO_SHIPPING_FOR_PICKUP', note: '', provider: 'BUYER_SELLER_HANDOVER', origin: null, destination: null, routeId: null };
  }
  if (!rate || !Number.isInteger(quantity) || quantity < 1) {
    return { available: false, error: 'seller has not configured a delivery price for this destination', code: 'DELIVERY_ROUTE_UNPRICED' };
  }
  const money = (value) => {
    const n = Number(value);
    return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : 0;
  };
  const base = money(rate.delivery_fee ?? rate.deliveryFee);
  const additional = money(rate.additional_item_fee ?? rate.additionalItemFee);
  const rawCap = rate.fee_cap ?? rate.feeCap;
  const cap = rawCap == null || rawCap === '' ? null : money(rawCap);
  let fee = base + Math.max(0, quantity - 1) * additional;
  if (cap != null) fee = Math.min(fee, cap);
  const currency = String(rate.currency || listing.currency || '').trim().toUpperCase();
  const destinationState = normalizeNigerianState(address.state);
  const destinationCity = normalizeNigerianCity(address.city);
  const rateSnapshot = {
    id: rate.id == null ? null : String(rate.id),
    destinationState,
    destinationCity: normalizeNigerianCity(rate.destination_city ?? rate.destinationCity),
    baseFee: base,
    additionalItemFee: additional,
    feeCap: cap,
    currency
  };
  return {
    fee: Math.round(fee * 100) / 100,
    method: 'DELIVERY',
    feePolicy: additional > 0 ? (cap != null ? 'ROUTE_BASE_PLUS_ADDITIONAL_CAPPED' : 'ROUTE_BASE_PLUS_ADDITIONAL') : 'ROUTE_FLAT_BASE',
    note: typeof listing.shipping_note === 'string' ? listing.shipping_note : '',
    provider: typeof listing.shipping_method === 'string' && listing.shipping_method ? listing.shipping_method : 'SELLER_ARRANGED',
    origin: { country: 'Nigeria', state: normalizeNigerianState(listing.shipping_origin_state), city: normalizeNigerianCity(listing.shipping_origin_city) },
    destination: { country: 'Nigeria', state: destinationState, city: destinationCity },
    routeId: rate.id == null ? null : String(rate.id),
    currency,
    rateSnapshot
  };
}

export async function resolveMarketplaceShipping(client, listing, quantity, fulfillmentMethod, address = {}) {
  if (fulfillmentMethod === 'PICKUP') return calculateMarketplaceShipping(listing, quantity, fulfillmentMethod);
  const country = typeof address?.country === 'string' ? address.country.trim().toUpperCase() : '';
  const nigeria = ['NIGERIA', 'NG', 'NGA'].includes(country) || Boolean(normalizeNigerianState(address?.state));
  if (!nigeria) return calculateMarketplaceShipping(listing, quantity, fulfillmentMethod);

  const originState = normalizeNigerianState(listing.shipping_origin_state);
  const originCity = normalizeNigerianCity(listing.shipping_origin_city);
  if (!originState || !originCity) {
    return { available: false, error: 'seller must set a dispatch city and state before Nigerian delivery can be priced', code: 'SELLER_DISPATCH_ORIGIN_REQUIRED' };
  }
  const destinationState = normalizeNigerianState(address?.state);
  const destinationCity = normalizeNigerianCity(address?.city);
  if (!destinationState || !destinationCity) {
    return { available: false, error: 'enter a valid Nigerian destination state and city or LGA to calculate delivery', code: 'DELIVERY_DESTINATION_REQUIRED' };
  }
  const result = await client.query(
    'SELECT id,destination_state,destination_city,delivery_fee,additional_item_fee,fee_cap,currency FROM marketplace_shipping_rates WHERE listing_id=$1 ORDER BY id ASC',
    [listing.id]
  );
  const rate = matchMarketplaceShippingRate(result.rows, destinationState, destinationCity);
  if (!rate) {
    return { available: false, error: 'seller has not set a delivery price for this destination yet; contact the seller or choose pickup', code: 'DELIVERY_ROUTE_UNPRICED' };
  }
  const listingCurrency = String(listing.currency || '').trim().toUpperCase();
  if (String(rate.currency || '').trim().toUpperCase() !== listingCurrency) {
    return { available: false, error: 'seller must update this route price to match the product currency before checkout', code: 'DELIVERY_ROUTE_CURRENCY_MISMATCH' };
  }
  return calculateMarketplaceRouteShipping(listing, rate, quantity, fulfillmentMethod, address);
}

export function calculateMarketplaceShipping(listing, quantity, fulfillmentMethod) {
  const money = (value) => {
    const n = Number(value);
    return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) / 100 : 0;
  };
  if (fulfillmentMethod === 'PICKUP') {
    return { fee: 0, method: 'PICKUP', feePolicy: 'NO_SHIPPING_FOR_PICKUP', note: '', provider: 'BUYER_SELLER_HANDOVER' };
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
}

export async function ensureMarketplaceShippingSchema(pool) {
  await pool.query(`
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_method TEXT NOT NULL DEFAULT 'SELLER_ARRANGED';
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_origin_city TEXT NOT NULL DEFAULT '';
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_origin_state TEXT NOT NULL DEFAULT '';
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_note TEXT NOT NULL DEFAULT '';
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS additional_item_shipping_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (additional_item_shipping_fee >= 0);
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_fee_cap NUMERIC(14,2);
    ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_method TEXT NOT NULL DEFAULT 'SELLER_ARRANGED';
    ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_note TEXT NOT NULL DEFAULT '';
    ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_fee_policy TEXT NOT NULL DEFAULT 'FLAT_BASE';
    ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS checkout_quote_id UUID;
    CREATE TABLE IF NOT EXISTS marketplace_shipping_coverage (
      id BIGSERIAL PRIMARY KEY,
      listing_id BIGINT NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
      country TEXT NOT NULL,
      state TEXT NOT NULL DEFAULT '',
      city TEXT NOT NULL DEFAULT '',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE(listing_id, country, state, city)
    );
    CREATE INDEX IF NOT EXISTS idx_marketplace_shipping_coverage_listing ON marketplace_shipping_coverage(listing_id);
    CREATE TABLE IF NOT EXISTS marketplace_shipping_rates (
      id BIGSERIAL PRIMARY KEY,
      listing_id BIGINT NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
      destination_state TEXT NOT NULL,
      destination_city TEXT NOT NULL DEFAULT '',
      delivery_fee NUMERIC(14,2) NOT NULL CHECK (delivery_fee >= 0),
      additional_item_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (additional_item_fee >= 0),
      fee_cap NUMERIC(14,2),
      currency TEXT NOT NULL CHECK (currency IN ('NGN','USD')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE(listing_id, destination_state, destination_city)
    );
    CREATE INDEX IF NOT EXISTS idx_marketplace_shipping_rates_listing ON marketplace_shipping_rates(listing_id, destination_state);
    CREATE TABLE IF NOT EXISTS marketplace_checkout_quotes (
      id UUID PRIMARY KEY,
      buyer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      quote_type TEXT NOT NULL CHECK (quote_type IN ('SINGLE','MULTI')),
      payload JSONB NOT NULL,
      expires_at TIMESTAMPTZ NOT NULL,
      consumed_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS marketplace_checkout_quotes_buyer_idx ON marketplace_checkout_quotes(buyer_id, expires_at);
  `);
}

function normalizeText(value, max = 120) {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

function normalizeShippingMethod(value) {
  const normalized = normalizeText(value, 64).toUpperCase();
  return normalized || 'SELLER_ARRANGED';
}

function normalizeMoney(value) {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0 || n > 1000000000) return null;
  return Math.round(n * 100) / 100;
}

function normalizeCoverageRow(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const country = normalizeText(value.country, 100);
  if (!country) return null;
  return { country: country.toUpperCase(), state: normalizeText(value.state, 100).toUpperCase(), city: normalizeText(value.city, 100).toUpperCase() };
}

export async function isMarketplaceDestinationCovered(client, listingId, address) {
  const rows = (await client.query('SELECT country,state,city FROM marketplace_shipping_coverage WHERE listing_id=$1 ORDER BY id ASC', [listingId])).rows;
  if (!rows.length) return true;
  const countryRaw = normalizeText(address?.country, 100).toUpperCase();
  const state = normalizeNigerianState(address?.state) || normalizeText(address?.state, 100).toUpperCase();
  const country = ['NG', 'NGA'].includes(countryRaw) ? 'NIGERIA' : (countryRaw || (normalizeNigerianState(address?.state) ? 'NIGERIA' : ''));
  const city = normalizeNigerianCity(address?.city);
  if (!country && !state) return false;
  return rows.some((row) => {
    const rowCountryRaw = String(row.country || '').toUpperCase();
    const rowCountry = ['NG', 'NGA'].includes(rowCountryRaw) ? 'NIGERIA' : rowCountryRaw;
    const rowState = normalizeNigerianState(row.state) || String(row.state || '').toUpperCase();
    const rowCity = normalizeNigerianCity(row.city);
    return rowCountry === country && (!rowState || rowState === state) && (!rowCity || rowCity === city);
  });
}

export function registerMarketplaceShippingRoutes({ app, pool, auth }) {
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = ensureMarketplaceShippingSchema(pool).catch((error) => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };
  const positiveInt = (value) => { const n = Number(value); return Number.isInteger(n) && n > 0 ? n : null; };
  const method = (value) => { const normalized = typeof value === 'string' ? value.trim().toUpperCase() : ''; return normalized === 'DELIVERY' || normalized === 'PICKUP' ? normalized : null; };

  app.get('/api/marketplace/listings/:id/shipping', auth, async (req, res) => {
    const listingId = positiveInt(req.params?.id);
    if (!listingId) return res.status(400).json({ error: 'invalid listing id' });
    const client = await pool.connect();
    try {
      await ensureSchema();
      const listing = (await client.query(`SELECT id,seller_id,shipping_method,shipping_note,delivery_fee,additional_item_shipping_fee,shipping_fee_cap,delivery_available,pickup_available FROM marketplace_listings WHERE id=$1 LIMIT 1`, [listingId])).rows[0];
      if (!listing) return res.status(404).json({ error: 'listing not found' });
      if (String(listing.seller_id) !== String(req.user.sub)) return res.status(403).json({ error: 'only the listing seller can manage shipping settings' });
      const coverage = (await client.query('SELECT country,state,city FROM marketplace_shipping_coverage WHERE listing_id=$1 ORDER BY id ASC', [listingId])).rows;
      return res.json({ shipping: { method: listing.shipping_method, note: listing.shipping_note, baseFee: Number(listing.delivery_fee || 0), additionalItemFee: Number(listing.additional_item_shipping_fee || 0), feeCap: listing.shipping_fee_cap == null ? null : Number(listing.shipping_fee_cap), deliveryAvailable: Boolean(listing.delivery_available), pickupAvailable: Boolean(listing.pickup_available), coverage: coverage.map((row) => ({ country: row.country, state: row.state, city: row.city })) } });
    } catch (error) { console.error('[marketplace-shipping-get]', error); return res.status(500).json({ error: 'shipping settings could not be loaded' }); }
    finally { client.release(); }
  });

  app.patch('/api/marketplace/listings/:id/shipping', auth, async (req, res) => {
    const listingId = positiveInt(req.params?.id);
    if (!listingId) return res.status(400).json({ error: 'invalid listing id' });
    const client = await pool.connect();
    try {
      await ensureSchema();
      await client.query('BEGIN');
      const listing = (await client.query('SELECT id,seller_id,delivery_available,pickup_available FROM marketplace_listings WHERE id=$1 FOR UPDATE', [listingId])).rows[0];
      if (!listing) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'listing not found' }); }
      if (String(listing.seller_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the listing seller can manage shipping settings' }); }
      const shippingMethod = normalizeShippingMethod(req.body?.method);
      const note = normalizeText(req.body?.note, 500);
      const baseFee = normalizeMoney(req.body?.baseFee);
      const additionalItemFee = normalizeMoney(req.body?.additionalItemFee);
      const feeCap = req.body?.feeCap == null || req.body?.feeCap === '' ? null : normalizeMoney(req.body?.feeCap);
      const deliveryAvailable = typeof req.body?.deliveryAvailable === 'boolean' ? req.body.deliveryAvailable : Boolean(listing.delivery_available);
      const pickupAvailable = typeof req.body?.pickupAvailable === 'boolean' ? req.body.pickupAvailable : Boolean(listing.pickup_available);
      if (baseFee == null || additionalItemFee == null || (req.body?.feeCap != null && req.body?.feeCap !== '' && feeCap == null)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'shipping fees must be valid non-negative amounts' }); }
      if (feeCap != null && feeCap < baseFee) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'shipping fee cap cannot be below the base delivery fee' }); }
      if (!deliveryAvailable && !pickupAvailable) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'at least one fulfillment method must remain enabled' }); }
      const inputCoverage = Array.isArray(req.body?.coverage) ? req.body.coverage : [];
      const coverage = inputCoverage.map(normalizeCoverageRow);
      if (coverage.some((row) => !row)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'each coverage entry requires a country' }); }
      const uniqueCoverage = [];
      const seen = new Set();
      for (const row of coverage) { const key = `${row.country}|${row.state}|${row.city}`; if (!seen.has(key)) { seen.add(key); uniqueCoverage.push(row); } }
      if (uniqueCoverage.length > 100) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'too many delivery coverage entries' }); }
      await client.query(`UPDATE marketplace_listings SET shipping_method=$2,shipping_note=$3,delivery_fee=$4,additional_item_shipping_fee=$5,shipping_fee_cap=$6,delivery_available=$7,pickup_available=$8,updated_at=NOW() WHERE id=$1`, [listingId, shippingMethod, note, baseFee, additionalItemFee, feeCap, deliveryAvailable, pickupAvailable]);
      await client.query('DELETE FROM marketplace_shipping_coverage WHERE listing_id=$1', [listingId]);
      for (const row of uniqueCoverage) await client.query('INSERT INTO marketplace_shipping_coverage(listing_id,country,state,city) VALUES($1,$2,$3,$4)', [listingId, row.country, row.state, row.city]);
      await client.query('COMMIT');
      return res.json({ ok: true, shipping: { method: shippingMethod, note, baseFee, additionalItemFee, feeCap, deliveryAvailable, pickupAvailable, coverage: uniqueCoverage } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('[marketplace-shipping-update]', error); return res.status(500).json({ error: 'shipping settings could not be saved' }); }
    finally { client.release(); }
  });

  app.get('/api/marketplace/listings/:id/shipping/routes', auth, async (req, res) => {
    const listingId = positiveInt(req.params?.id);
    if (!listingId) return res.status(400).json({ error: 'invalid listing id' });
    const client = await pool.connect();
    try {
      await ensureSchema();
      const listing = (await client.query('SELECT id,seller_id,currency,shipping_origin_city,shipping_origin_state FROM marketplace_listings WHERE id=$1 LIMIT 1', [listingId])).rows[0];
      if (!listing) return res.status(404).json({ error: 'listing not found' });
      if (String(listing.seller_id) !== String(req.user.sub)) return res.status(403).json({ error: 'only the listing seller can manage route prices' });
      const rates = (await client.query('SELECT id,destination_state,destination_city,delivery_fee,additional_item_fee,fee_cap,currency FROM marketplace_shipping_rates WHERE listing_id=$1 ORDER BY destination_state,destination_city', [listingId])).rows;
      return res.json({ routes: {
        originState: listing.shipping_origin_state || '',
        originCity: listing.shipping_origin_city || '',
        currency: String(listing.currency || '').toUpperCase(),
        rates: rates.map((row) => ({ id: String(row.id), destinationState: row.destination_state, destinationCity: row.destination_city, fee: Number(row.delivery_fee), additionalItemFee: Number(row.additional_item_fee || 0), feeCap: row.fee_cap == null ? null : Number(row.fee_cap), currency: row.currency }))
      }});
    } catch (error) { console.error('[marketplace-shipping-routes-get]', error); return res.status(500).json({ error: 'delivery route prices could not be loaded' }); }
    finally { client.release(); }
  });

  app.patch('/api/marketplace/listings/:id/shipping/routes', auth, async (req, res) => {
    const listingId = positiveInt(req.params?.id);
    if (!listingId) return res.status(400).json({ error: 'invalid listing id' });
    const client = await pool.connect();
    try {
      await ensureSchema();
      await client.query('BEGIN');
      const listing = (await client.query('SELECT id,seller_id,currency FROM marketplace_listings WHERE id=$1 FOR UPDATE', [listingId])).rows[0];
      if (!listing) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'listing not found' }); }
      if (String(listing.seller_id) !== String(req.user.sub)) { await client.query('ROLLBACK'); return res.status(403).json({ error: 'only the listing seller can manage route prices' }); }
      const currency = String(listing.currency || '').trim().toUpperCase();
      if (!['NGN','USD'].includes(currency)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'route pricing requires an active NGN or USD listing currency', code: 'UNSUPPORTED_MARKETPLACE_CURRENCY' }); }
      const originState = normalizeNigerianState(req.body?.originState);
      const originCity = normalizeNigerianCity(req.body?.originCity);
      const inputRates = req.body?.rates;
      if (!originState || !originCity) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'select a valid dispatch state and enter its city or LGA' }); }
      if (!Array.isArray(inputRates) || inputRates.length > 100) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'delivery routes must be an array of at most 100 entries' }); }
      const routes = [];
      const seen = new Set();
      for (const value of inputRates) {
        if (!value || typeof value !== 'object' || Array.isArray(value)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'each delivery route must be a valid object' }); }
        const destinationState = normalizeNigerianState(value.destinationState);
        const destinationCity = normalizeNigerianCity(value.destinationCity);
        const fee = normalizeMoney(value.fee);
        const additionalItemFee = value.additionalItemFee == null || value.additionalItemFee === '' ? 0 : normalizeMoney(value.additionalItemFee);
        const feeCap = value.feeCap == null || value.feeCap === '' ? null : normalizeMoney(value.feeCap);
        if (!destinationState || fee == null || additionalItemFee == null || (value.feeCap != null && value.feeCap !== '' && feeCap == null) || (feeCap != null && feeCap < fee)) {
          await client.query('ROLLBACK');
          return res.status(400).json({ error: 'each route needs a valid Nigerian destination state, fee and optional valid extra-item fee/cap' });
        }
        const key = destinationState + '|' + destinationCity;
        if (seen.has(key)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'a destination state/city route can only be configured once', code: 'DUPLICATE_DELIVERY_ROUTE' }); }
        seen.add(key);
        routes.push({ destinationState, destinationCity, fee, additionalItemFee, feeCap });
      }
      await client.query('UPDATE marketplace_listings SET shipping_origin_state=$2,shipping_origin_city=$3,updated_at=NOW() WHERE id=$1', [listingId, originState, originCity]);
      await client.query('DELETE FROM marketplace_shipping_rates WHERE listing_id=$1', [listingId]);
      for (const route of routes) {
        await client.query(
          'INSERT INTO marketplace_shipping_rates(listing_id,destination_state,destination_city,delivery_fee,additional_item_fee,fee_cap,currency,updated_at) VALUES($1,$2,$3,$4,$5,$6,$7,NOW())',
          [listingId, route.destinationState, route.destinationCity, route.fee, route.additionalItemFee, route.feeCap, currency]
        );
      }
      await client.query('COMMIT');
      return res.json({ ok: true, routes: { originState, originCity, currency, rates: routes.map((route) => ({ ...route, currency })) } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('[marketplace-shipping-routes-update]', error); return res.status(500).json({ error: 'delivery route prices could not be saved' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/shipping/options', auth, async (req, res) => {
    const listingId = positiveInt(req.body?.listingId); const quantity = positiveInt(req.body?.quantity); const fulfillmentMethod = method(req.body?.fulfillmentMethod);
    if (!listingId || !quantity || !fulfillmentMethod) return res.status(400).json({ error: 'listing, quantity and fulfillment method are required' });
    const client = await pool.connect();
    try {
      await ensureSchema();
      const listing = (await client.query(`SELECT id,seller_id,currency,shipping_origin_city,shipping_origin_state,delivery_available,pickup_available,delivery_fee,shipping_method,shipping_note,additional_item_shipping_fee,shipping_fee_cap FROM marketplace_listings WHERE id=$1 AND active=TRUE LIMIT 1`, [listingId])).rows[0];
      if (!listing) return res.status(404).json({ error: 'listing not found or no longer available' });
      if (fulfillmentMethod === 'DELIVERY' && !Boolean(listing.delivery_available)) return res.status(409).json({ error: 'delivery is not available for this listing' });
      if (fulfillmentMethod === 'PICKUP' && !Boolean(listing.pickup_available)) return res.status(409).json({ error: 'pickup is not available for this listing' });
      const address = req.body?.shippingAddress && typeof req.body.shippingAddress === 'object' ? req.body.shippingAddress : {};
      if (fulfillmentMethod === 'DELIVERY' && (!address.city || !address.state || !address.country)) {
        return res.status(400).json({ error: 'delivery options require the buyer destination city, state and country', code: 'DELIVERY_DESTINATION_REQUIRED' });
      }
      if (fulfillmentMethod === 'DELIVERY' && !(await isMarketplaceDestinationCovered(client, listing.id, address))) {
        return res.status(409).json({ error: 'seller does not currently deliver to this destination', code: 'DESTINATION_NOT_COVERED' });
      }
      const selected = await resolveMarketplaceShipping(client, listing, quantity, fulfillmentMethod, address);
      if (selected?.available === false) return res.status(409).json({ error: selected.error, code: selected.code });
      return res.json({ options: [{ id: selected.method === 'PICKUP' ? 'pickup' : (selected.routeId || 'seller-arranged-delivery'), fulfillmentMethod: selected.method, provider: selected.provider, fee: selected.fee, currency: selected.currency || String(listing.currency || '').toUpperCase(), feePolicy: selected.feePolicy, note: selected.note, origin: selected.origin || null, destination: selected.destination || null, carrierRequired: false, buyerProtection: 'FYNX payment remains protected until the order reaches the required completion state' }] });
    } catch (error) { console.error('[marketplace-shipping-options]', error); return res.status(500).json({ error: 'shipping options could not be prepared' }); }
    finally { client.release(); }
  });
  return { calculate: calculateMarketplaceShipping };
}
