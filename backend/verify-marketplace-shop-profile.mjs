import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const shop = read('./marketplaceShopProfiles.js');
const server = read('./server.js');
const seller = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt');
const storefront = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerStoreDialog.kt');

const checks = [
  ['shop profile table is idempotent and keyed to the seller account', shop.includes('CREATE TABLE IF NOT EXISTS marketplace_shop_profiles') && shop.includes('seller_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE')],
  ['shop fields have database length guards', ['length(shop_name) <= 120', 'length(description) <= 800', 'length(location) <= 160', 'length(contact_phone) <= 40'].every((field) => shop.includes(field))],
  ['private shop read requires authentication and scopes to the current account', shop.includes("app.get('/api/marketplace/my-shop', auth") && shop.includes('WHERE u.id = $1') && shop.includes('req.user.sub')],
  ['shop edits require authentication and validate the shop name', shop.includes("app.patch('/api/marketplace/my-shop', auth") && shop.includes('shopName.length < 2') && shop.includes('req.body?.shopName')],
  ['shop profile upsert cannot change seller ownership', shop.includes('ON CONFLICT (seller_id) DO UPDATE') && !/ON CONFLICT\s*\(seller_id\)[\s\S]{0,400}seller_id\s*=/.test(shop)],
  ['public shop lookup validates username and returns only shop identity fields', shop.includes("app.get('/api/marketplace/shops/:username', auth") && shop.includes('^[a-zA-Z0-9_.-]{1,40}$') && shop.includes('WHERE lower(u.username) = lower($1)')],
  ['shop profile routes are registered in the canonical server', server.includes('registerMarketplaceShopProfileRoutes') && server.includes('if (pool) registerMarketplaceShopProfileRoutes({ app, pool, auth })')],
  ['Seller Center loads and saves persistent shop identity', seller.includes('/api/marketplace/my-shop') && seller.includes('patchJson(context, "/api/marketplace/my-shop"') && seller.includes('Save shop details')],
  ['personal sellers can create shop identity without Business Account', seller.includes('A Business Account is not required.') && !shop.includes('business_profile_id')],
  ['public storefront prefers persistent shop name and falls back to existing listing name', storefront.includes('/api/marketplace/shops/') && storefront.includes('shop.optString("shopName").ifBlank { storeName }')],
  ['shop profile module does not create duplicate listings or orders', !shop.includes('CREATE TABLE IF NOT EXISTS marketplace_listings') && !shop.includes('CREATE TABLE IF NOT EXISTS marketplace_orders')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Marketplace shop profile verification passed: ${checks.length}/${checks.length}`);
