import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  NIGERIA_STATES,
  normalizeNigerianState,
  normalizeNigerianCity,
  matchMarketplaceShippingRate,
  calculateMarketplaceRouteShipping,
  resolveMarketplaceShipping
} from './marketplaceShipping.js';

const checks = [];
function check(name, fn) {
  try {
    fn();
    checks.push([name, true]);
  } catch (error) {
    checks.push([name, false, error?.message || String(error)]);
  }
}
async function checkAsync(name, fn) {
  try {
    await fn();
    checks.push([name, true]);
  } catch (error) {
    checks.push([name, false, error?.message || String(error)]);
  }
}

check('Nigeria state list contains the 36 states and FCT', () => {
  assert.equal(NIGERIA_STATES.length, 37);
  assert.equal(new Set(NIGERIA_STATES).size, 37);
  assert.ok(NIGERIA_STATES.includes('FCT'));
});
check('FCT aliases normalize consistently', () => {
  assert.equal(normalizeNigerianState('Abuja'), 'FCT');
  assert.equal(normalizeNigerianState('Federal Capital Territory'), 'FCT');
  assert.equal(normalizeNigerianState('FCT'), 'FCT');
});
check('state matching is case and punctuation insensitive', () => {
  assert.equal(normalizeNigerianState(' rivers '), 'Rivers');
  assert.equal(normalizeNigerianState('AKWA-IBOM'), 'Akwa Ibom');
  assert.equal(normalizeNigerianState('not a state'), null);
});
check('city matching normalizes spaces and case', () => {
  assert.equal(normalizeNigerianCity(' Port   Harcourt '), 'PORT HARCOURT');
});
const rates = [
  { id: 1, destination_state: 'Rivers', destination_city: '', delivery_fee: 8000, additional_item_fee: 1000, fee_cap: null, currency: 'NGN' },
  { id: 2, destination_state: 'Rivers', destination_city: 'Port Harcourt', delivery_fee: 4500, additional_item_fee: 500, fee_cap: 5500, currency: 'NGN' },
  { id: 3, destination_state: 'Lagos', destination_city: '', delivery_fee: 3000, additional_item_fee: 0, fee_cap: null, currency: 'NGN' }
];
check('exact city route takes precedence over the state-wide price', () => {
  assert.equal(matchMarketplaceShippingRate(rates, 'RIVERS', ' port   harcourt ').id, 2);
});
check('state-wide route is used when no exact city price exists', () => {
  assert.equal(matchMarketplaceShippingRate(rates, 'Rivers', 'Bonny').id, 1);
});
check('an unsupported state has no route price', () => {
  assert.equal(matchMarketplaceShippingRate(rates, 'Ekiti', 'Ado-Ekiti'), null);
});
check('extra-item fees and caps are applied to the seller-set route', () => {
  const result = calculateMarketplaceRouteShipping(
    { currency: 'NGN', shipping_method: 'SELLER_ARRANGED', shipping_note: 'Seller-arranged dispatch', shipping_origin_state: 'Anambra', shipping_origin_city: 'Onitsha' },
    { ...rates[1], delivery_fee: 2500, additional_item_fee: 500, fee_cap: 3000 },
    3,
    'DELIVERY',
    { state: 'Rivers', city: 'Port Harcourt' }
  );
  assert.equal(result.fee, 3000);
  assert.equal(result.feePolicy, 'ROUTE_BASE_PLUS_ADDITIONAL_CAPPED');
  assert.equal(result.origin.city, 'ONITSHA');
  assert.equal(result.destination.city, 'PORT HARCOURT');
  assert.equal(result.provider, 'SELLER_ARRANGED');
});
check('pickup always remains zero-fee and does not require a route', () => {
  const result = calculateMarketplaceRouteShipping({}, null, 2, 'PICKUP', {});
  assert.equal(result.fee, 0);
  assert.equal(result.method, 'PICKUP');
});
await checkAsync('a missing destination route blocks Nigerian delivery without guessing a fee', async () => {
  const client = { query: async () => ({ rows: [] }) };
  const result = await resolveMarketplaceShipping(
    client,
    { id: 19, currency: 'NGN', shipping_origin_state: 'Anambra', shipping_origin_city: 'Onitsha' },
    1,
    'DELIVERY',
    { country: 'Nigeria', state: 'Rivers', city: 'Port Harcourt' }
  );
  assert.equal(result.available, false);
  assert.equal(result.code, 'DELIVERY_ROUTE_UNPRICED');
});
await checkAsync('route resolution selects a configured city fee and keeps the product currency', async () => {
  const client = { query: async () => ({ rows: [rates[1]] }) };
  const result = await resolveMarketplaceShipping(
    client,
    { id: 19, currency: 'NGN', shipping_origin_state: 'Anambra', shipping_origin_city: 'Onitsha' },
    2,
    'DELIVERY',
    { country: 'Nigeria', state: 'Rivers', city: 'Port Harcourt' }
  );
  assert.equal(result.fee, 5000);
  assert.equal(result.currency, 'NGN');
  assert.equal(result.routeId, '2');
  assert.deepEqual(result.rateSnapshot, {
    id: '2',
    destinationState: 'Rivers',
    destinationCity: 'PORT HARCOURT',
    baseFee: 4500,
    additionalItemFee: 500,
    feeCap: 5500,
    currency: 'NGN'
  });
});
await checkAsync('a route fee in a different currency is rejected', async () => {
  const client = { query: async () => ({ rows: [{ ...rates[1], currency: 'USD' }] }) };
  const result = await resolveMarketplaceShipping(
    client,
    { id: 19, currency: 'NGN', shipping_origin_state: 'Anambra', shipping_origin_city: 'Onitsha' },
    1,
    'DELIVERY',
    { country: 'Nigeria', state: 'Rivers', city: 'Port Harcourt' }
  );
  assert.equal(result.available, false);
  assert.equal(result.code, 'DELIVERY_ROUTE_CURRENCY_MISMATCH');
});
await checkAsync('checkout quote is stored and single-order request binds its saved quote', async () => {
  const quote = await readFile('./marketplaceCheckout.js', 'utf8');
  const order = await readFile('./marketplaceCheckoutOrder.js', 'utf8');
  const android = await readFile('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt', 'utf8');
  assert.match(quote, /INSERT INTO marketplace_checkout_quotes/);
  assert.match(quote, /shippingRate: shipping\.rateSnapshot/);
  assert.match(order, /checkout_quote_id/);
  assert.match(order, /shippingRate: shipping\.rateSnapshot/);
  assert.match(order, /CHECKOUT_QUOTE_MISMATCH/);
  assert.match(order, /acceptedQuote\.deliveryFee/);
  assert.match(order, /consumed_at=NOW\(\)/);
  assert.doesNotMatch(order, /calculateMarketplaceShipping/);
  assert.match(android, /put\("quoteId", expectedQuote\.id\)/);
  assert.match(android, /quoteId = q\.id/);
  assert.match(android, /NigerianStatePicker/);
});
await checkAsync('multi-seller checkout consumes its saved quote without repricing delivery', async () => {
  const quote = await readFile('./marketplaceCheckout.js', 'utf8');
  const order = await readFile('./marketplaceCheckoutOrder.js', 'utf8');
  assert.ok(quote.includes("VALUES($1,$2,'MULTI'"), "multi quote insert stores MULTI quote type");
  assert.match(order, /quote_type='MULTI'/);
  assert.match(order, /quotedById\.get/);
  assert.match(order, /CHECKOUT_QUOTE_TOTAL_MISMATCH/);
});
await checkAsync('seller route settings are authenticated and validated on the server', async () => {
  const shipping = await readFile('./marketplaceShipping.js', 'utf8');
  const settings = await readFile('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceRouteRatesSection.kt', 'utf8');
  const settingsCard = await readFile('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceShippingSettingsCard.kt', 'utf8');
  const architecture = await readFile('../docs/FYNX_MARKETPLACE_MASTER_ARCHITECTURE.md', 'utf8');
  assert.match(shipping, /app\.get\('\/api\/marketplace\/listings\/:id\/shipping\/routes'/);
  assert.match(shipping, /app\.patch\('\/api\/marketplace\/listings\/:id\/shipping\/routes'/);
  assert.match(shipping, /only the listing seller can manage route prices/);
  assert.match(settings, /Nigeria delivery routes/);
  assert.match(settings, /Save route prices/);
  assert.match(settings, /FCT/);
  assert.match(settingsCard, /FynxMarketplaceRouteRatesSection/);
  assert.match(architecture, /Nigeria route pricing and accepted-quote integrity/);
});

let failed = 0;
for (const [name, ok, detail] of checks) {
  console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}${detail ? ` — ${detail}` : ''}`);
  if (!ok) failed += 1;
}
console.log(`Marketplace Nigeria route pricing: ${checks.length - failed}/${checks.length} checks passed`);
if (failed) process.exitCode = 1;
