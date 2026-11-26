import assert from 'node:assert/strict';
import { registerMarketplaceShopProfileRoutes } from './marketplaceShopProfiles.js';

const routes = new Map();
const app = {
  get(path, ...handlers) { routes.set(`GET ${path}`, handlers.at(-1)); },
  patch(path, ...handlers) { routes.set(`PATCH ${path}`, handlers.at(-1)); }
};

let savedImages = { logo_media_id: '41', cover_media_id: '42' };
let lastInsert = null;
const pool = {
  async query(sql, params = []) {
    if (sql.includes('CREATE TABLE IF NOT EXISTS marketplace_shop_profiles') ||
        sql.includes('ALTER TABLE marketplace_shop_profiles')) return { rows: [] };
    if (sql.includes('SELECT id, mime_type FROM message_media')) {
      return { rows: params[0].map((id) => ({ id, mime_type: 'image/jpeg' })) };
    }
    if (sql.includes('INSERT INTO marketplace_shop_profiles')) {
      lastInsert = { sql, params };
      const [sellerId, shopName, description, location, contactPhone, logoId, coverId, hasLogo, hasCover] = params;
      if (hasLogo) savedImages.logo_media_id = logoId;
      if (hasCover) savedImages.cover_media_id = coverId;
      return {
        rows: [{
          shop_name: shopName, description, location, contact_phone: contactPhone,
          logo_media_id: savedImages.logo_media_id,
          cover_media_id: savedImages.cover_media_id,
          updated_at: '2026-11-23T10:00:00.000Z'
        }]
      };
    }
    if (sql.includes('SELECT username, display_name FROM users')) {
      return { rows: [{ username: 'seller', display_name: 'Seller' }] };
    }
    throw new Error(`Unexpected query in shop-profile test: ${sql.slice(0, 100)}`);
  }
};

registerMarketplaceShopProfileRoutes({ app, pool, auth: (_req, _res, next) => next() });
const patch = routes.get('PATCH /api/marketplace/my-shop');
assert.equal(typeof patch, 'function', 'shop profile PATCH route must register');

async function save(body) {
  const response = {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; }
  };
  await patch({ body, user: { sub: '7' } }, response);
  assert.equal(response.statusCode, 200, JSON.stringify(response.body));
  assert.ok(response.body?.shop, 'shop profile response must contain the saved shop');
  return response;
}

await save({
  shopName: 'Seller Shop',
  description: 'Shop details changed without touching branding',
  location: 'Port Harcourt',
  contactPhone: ''
});
assert.equal(lastInsert.params[7], false, 'omitted logo must not be treated as a removal');
assert.equal(lastInsert.params[8], false, 'omitted cover must not be treated as a removal');
assert.equal(savedImages.logo_media_id, '41', 'omitting logo must preserve the existing logo');
assert.equal(savedImages.cover_media_id, '42', 'omitting cover must preserve the existing cover');
assert.match(lastInsert.sql, /CASE WHEN \$8 THEN EXCLUDED\.logo_media_id ELSE marketplace_shop_profiles\.logo_media_id END/);
assert.match(lastInsert.sql, /CASE WHEN \$9 THEN EXCLUDED\.cover_media_id ELSE marketplace_shop_profiles\.cover_media_id END/);

await save({
  shopName: 'Seller Shop',
  description: 'Remove branding explicitly',
  location: 'Port Harcourt',
  contactPhone: '',
  logoMediaId: null,
  coverMediaId: null
});
assert.equal(lastInsert.params[7], true, 'explicit null logo must request removal');
assert.equal(lastInsert.params[8], true, 'explicit null cover must request removal');
assert.equal(lastInsert.params[5], null);
assert.equal(lastInsert.params[6], null);
assert.equal(savedImages.logo_media_id, null);
assert.equal(savedImages.cover_media_id, null);

await save({
  shopName: 'Seller Shop',
  description: 'Replace only the logo',
  location: 'Port Harcourt',
  contactPhone: '',
  logoMediaId: '77'
});
assert.deepEqual(lastInsert.params.slice(5), ['77', null, true, false]);
assert.equal(savedImages.logo_media_id, '77');
assert.equal(savedImages.cover_media_id, null, 'omitted cover remains unchanged');

console.log('Marketplace shop profile PATCH tests passed: preserve omitted images, explicit removal, and single-image replacement.');

// Run privacy and saved-product authorization regressions in the same CI test invocation.
await import("./verify-marketplace-shop-privacy.mjs");
await import("./test-marketplace-saved-product-routes.mjs");
