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
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_note TEXT NOT NULL DEFAULT '';
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS additional_item_shipping_fee NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (additional_item_shipping_fee >= 0);
    ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS shipping_fee_cap NUMERIC(14,2);
    ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_method TEXT NOT NULL DEFAULT 'SELLER_ARRANGED';
    ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_note TEXT NOT NULL DEFAULT '';
    ALTER TABLE marketplace_orders ADD COLUMN IF NOT EXISTS shipping_fee_policy TEXT NOT NULL DEFAULT 'FLAT_BASE';
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
  const country = normalizeText(address?.country, 100).toUpperCase();
  const state = normalizeText(address?.state, 100).toUpperCase();
  const city = normalizeText(address?.city, 100).toUpperCase();
  if (!country) return false;
  return rows.some((row) => String(row.country).toUpperCase() === country && (!row.state || String(row.state).toUpperCase() === state) && (!row.city || String(row.city).toUpperCase() === city));
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
      if (baseFee == null || additionalItemFee == null || (req.body?.feeCap != null && req.body?.feeCap !== '' && feeCap == null)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'shipping fees must be valid non-negative amounts' }); }
      if (feeCap != null && feeCap < baseFee) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'shipping fee cap cannot be below the base delivery fee' }); }
      if (!Boolean(listing.delivery_available) && (baseFee > 0 || additionalItemFee > 0 || feeCap != null)) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'delivery is disabled for this listing' }); }
      const inputCoverage = Array.isArray(req.body?.coverage) ? req.body.coverage : [];
      const coverage = inputCoverage.map(normalizeCoverageRow);
      if (coverage.some((row) => !row)) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'each coverage entry requires a country' }); }
      const uniqueCoverage = [];
      const seen = new Set();
      for (const row of coverage) { const key = `${row.country}|${row.state}|${row.city}`; if (!seen.has(key)) { seen.add(key); uniqueCoverage.push(row); } }
      if (uniqueCoverage.length > 100) { await client.query('ROLLBACK'); return res.status(400).json({ error: 'too many delivery coverage entries' }); }
      await client.query(`UPDATE marketplace_listings SET shipping_method=$2,shipping_note=$3,delivery_fee=$4,additional_item_shipping_fee=$5,shipping_fee_cap=$6,updated_at=NOW() WHERE id=$1`, [listingId, shippingMethod, note, baseFee, additionalItemFee, feeCap]);
      await client.query('DELETE FROM marketplace_shipping_coverage WHERE listing_id=$1', [listingId]);
      for (const row of uniqueCoverage) await client.query('INSERT INTO marketplace_shipping_coverage(listing_id,country,state,city) VALUES($1,$2,$3,$4)', [listingId, row.country, row.state, row.city]);
      await client.query('COMMIT');
      return res.json({ ok: true, shipping: { method: shippingMethod, note, baseFee, additionalItemFee, feeCap, coverage: uniqueCoverage } });
    } catch (error) { try { await client.query('ROLLBACK'); } catch {} console.error('[marketplace-shipping-update]', error); return res.status(500).json({ error: 'shipping settings could not be saved' }); }
    finally { client.release(); }
  });

  app.post('/api/marketplace/shipping/options', auth, async (req, res) => {
    const listingId = positiveInt(req.body?.listingId); const quantity = positiveInt(req.body?.quantity); const fulfillmentMethod = method(req.body?.fulfillmentMethod);
    if (!listingId || !quantity || !fulfillmentMethod) return res.status(400).json({ error: 'listing, quantity and fulfillment method are required' });
    const client = await pool.connect();
    try {
      await ensureSchema();
      const listing = (await client.query(`SELECT id,seller_id,delivery_available,pickup_available,delivery_fee,shipping_method,shipping_note,additional_item_shipping_fee,shipping_fee_cap FROM marketplace_listings WHERE id=$1 AND active=TRUE LIMIT 1`, [listingId])).rows[0];
      if (!listing) return res.status(404).json({ error: 'listing not found or no longer available' });
      if (fulfillmentMethod === 'DELIVERY' && !Boolean(listing.delivery_available)) return res.status(409).json({ error: 'delivery is not available for this listing' });
      if (fulfillmentMethod === 'PICKUP' && !Boolean(listing.pickup_available)) return res.status(409).json({ error: 'pickup is not available for this listing' });
      const selected = calculateMarketplaceShipping(listing, quantity, fulfillmentMethod);
      return res.json({ options: [{ id: selected.method === 'PICKUP' ? 'pickup' : 'seller-arranged-delivery', fulfillmentMethod: selected.method, provider: selected.provider, fee: selected.fee, currency: null, feePolicy: selected.feePolicy, note: selected.note, carrierRequired: false, buyerProtection: 'FYNX payment remains protected until the order reaches the required completion state' }] });
    } catch (error) { console.error('[marketplace-shipping-options]', error); return res.status(500).json({ error: 'shipping options could not be prepared' }); }
    finally { client.release(); }
  });
  return { calculate: calculateMarketplaceShipping };
}
