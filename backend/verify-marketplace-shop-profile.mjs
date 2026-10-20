import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const shop = read('./marketplaceShopProfiles.js');
const server = read('./server.js');
const seller = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt');
const storefront = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerStoreDialog.kt');
const client = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceClient.kt');
const social = read('./socialRoutes.js');
const shipping = read('./marketplaceShipping.js');
const shippingUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceShippingSettingsCard.kt');
const savedProductsUi = read('../app/src/main/java/com/fynx/app/ui/FynxSavedMarketplaceProductsPanel.kt');
const marketplacePanel = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt');
const appNavigation = read('../app/src/main/java/com/fynx/app/ui/FynxApp.kt');

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
  ['shop profile module does not create duplicate listings or orders', !shop.includes('CREATE TABLE IF NOT EXISTS marketplace_listings') && !shop.includes('CREATE TABLE IF NOT EXISTS marketplace_orders')],
  ['Seller Center exposes an edit action for each existing listing', seller.includes('Icons.Default.Edit') && seller.includes('Icon(Icons.Default.Edit, "Edit listing")') && seller.includes('Text("Save changes")')],
  ['listing editor uses the existing authenticated seller-owned update endpoint', client.includes('FynxBackendClient.postJson(context, "/api/marketplace/listings/$id/update"') && social.includes('app.post("/api/marketplace/listings/:id/update", auth') && social.includes('WHERE id = $1 AND seller_id = $2 FOR UPDATE')],
  ['listing edits validate price and stock and preserve other listing fields', client.includes('price <= 0.0') && client.includes('quantity < 0') && seller.includes('current.copy(') && seller.includes('quantity = quantity')],
  ['Seller Center exposes delivery and pickup availability controls', shippingUi.includes('Switch(checked = deliveryEnabled') && shippingUi.includes('Switch(checked = pickupEnabled')],
  ['shipping client sends and reads saved fulfillment availability', client.includes('.put("deliveryAvailable", settings.deliveryAvailable)') && client.includes('.put("pickupAvailable", settings.pickupAvailable)') && client.includes('s.optBoolean("deliveryAvailable", settings.deliveryAvailable)') && client.includes('s.optBoolean("pickupAvailable", settings.pickupAvailable)')],
  ['authenticated seller shipping update persists fulfillment availability and preserves at least one method', shipping.includes("app.patch('/api/marketplace/listings/:id/shipping', auth") && shipping.includes('delivery_available=$7,pickup_available=$8') && shipping.includes('if (!deliveryAvailable && !pickupAvailable)') && shipping.includes('at least one fulfillment method must remain enabled')],
  ['checkout still rejects disabled fulfillment methods', shipping.includes("fulfillmentMethod === 'DELIVERY' && !Boolean(listing.delivery_available)") && shipping.includes("fulfillmentMethod === 'PICKUP' && !Boolean(listing.pickup_available)")],
  ['public seller storefront queries the selected seller even for the signed-in seller', social.includes('if (!seller) where.push("l.seller_id <> $1")') && storefront.includes('FynxRemoteSocialClient.listings(context, seller = username.removePrefix("@").trim())')],
  ['Saved Products has its own persistent table and authenticated read/save/remove endpoints', social.includes('CREATE TABLE IF NOT EXISTS marketplace_saved_products') && social.includes('app.get("/api/marketplace/saved-products", auth') && social.includes('app.post("/api/marketplace/listings/:id/saved", auth') && social.includes('app.delete("/api/marketplace/listings/:id/saved", auth')],
  ['Marketplace product details expose save and remove actions using the existing remote client', marketplacePanel.includes('FynxRemoteSocialClient.saveMarketplaceProduct') && marketplacePanel.includes('FynxRemoteSocialClient.unsaveMarketplaceProduct') && marketplacePanel.includes('Remove from Saved Products')],
  ['Saved Products screen loads saved products and supports removal', savedProductsUi.includes('FynxRemoteSocialClient.savedMarketplaceProducts(context)') && savedProductsUi.includes('FynxRemoteSocialClient.unsaveMarketplaceProduct(context, product.id)')],
  ['Saved Products is navigable separately from Saved Posts and opens the canonical listing', appNavigation.includes('"Saved Products" -> FynxSavedMarketplaceProductsPanel(onOpenListing = { listingId -> marketplaceListingId = listingId; selected = "Marketplace" })') && appNavigation.includes('Triple("Saved Products", "Saved Marketplace Products", Icons.Default.BookmarkBorder)') && savedProductsUi.includes('Text("View product")') && savedProductsUi.includes('onOpenListing(product.id)')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Marketplace shop profile verification passed: ${checks.length}/${checks.length}`);
