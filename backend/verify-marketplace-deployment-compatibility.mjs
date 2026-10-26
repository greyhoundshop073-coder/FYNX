import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";
import { registerMarketplaceListingLifecycleRoutes } from "./marketplaceListingLifecycleRoutes.js";

const read = (name) => readFile(new URL(name, import.meta.url), "utf8");
const [server, bootstrap, packageJson, shop, listings, schema, social, discovery] = await Promise.all([
  read("./server.js"),
  read("./serverBootstrap.js"),
  read("./package.json"),
  read("./marketplaceShopProfiles.js"),
  read("./marketplaceListingLifecycleRoutes.js"),
  read("./marketplaceSchema.js"),
  read("./socialRoutes.js"),
  read("./discoveryRoutes.js")
]);

assert.ok(server.includes('app.get("/version"'), "backend must expose a version route for deployment provenance");
assert.ok(server.includes("RAILWAY_GIT_COMMIT_SHA") && server.includes("RAILWAY_GIT_BRANCH")
  && server.includes("RAILWAY_ENVIRONMENT_NAME") && server.includes("RAILWAY_DEPLOYMENT_ID"),
  "/version must report safe Railway deployment identifiers");
assert.ok(server.includes('res.setHeader("Cache-Control", "no-store")'), "/version must not be cached");
assert.ok(!/DATABASE_URL|JWT_SECRET|OPENAI_API_KEY|accessToken|password/i.test(
  server.slice(server.indexOf('app.get("/version"'), server.indexOf('app.get("/api/presence"'))
), "/version must not expose backend secrets");

assert.ok(bootstrap.includes('import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";'),
  "runtime bootstrap must import the persistent shop profile module");
assert.ok(bootstrap.includes('import { registerMarketplaceListingLifecycleRoutes } from "./marketplaceListingLifecycleRoutes.js";'),
  "runtime bootstrap must import Saved Products and listing lifecycle routes");
assert.ok(bootstrap.includes("registerMarketplaceShopProfileRoutes({ app, pool, auth })"),
  "runtime bootstrap must register My Shop routes");
assert.ok(bootstrap.includes("registerMarketplaceListingLifecycleRoutes({ app, pool, auth })"),
  "runtime bootstrap must register Saved Products and listing reactivation");
assert.ok(bootstrap.includes('if (!source.includes(contract)) throw new Error('),
  "runtime bootstrap must fail fast if an injected route contract is missing");
assert.ok(packageJson.includes("verify-marketplace-deployment-compatibility.mjs")
  && packageJson.includes("verify-deployment-provenance.mjs"),
  "prestart must execute compatibility and provenance contracts");

assert.ok(schema.includes('"  reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),"')
  && schema.includes("ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0"),
  "shared Marketplace schema must provide reserved stock before discovery queries run");
assert.ok(social.includes("l.quantity - COALESCE(l.reserved_quantity, 0) > 0")
  && social.includes("AS available_quantity"),
  "Marketplace browse and seller inventory must expose only unreserved stock alongside total quantity");
assert.ok(discovery.includes("l.quantity - COALESCE(l.reserved_quantity, 0) > 0")
  && discovery.includes("AS available_quantity")
  && discovery.includes("Number(row.available_quantity) <= 0"),
  "Marketplace discovery and product details must enforce available stock consistently");
assert.ok(discovery.includes("to_regclass('marketplace_orders') IS NOT NULL AS ready")
  && discovery.includes("const orderScoreSql = orderTableReady")
  && discovery.includes(': "0";'),
  "Marketplace discovery must work before the first order initializes the orders table");
const exactListingStart = discovery.indexOf('app.get("/api/marketplace/listing/:id"');
const exactListingEnd = discovery.indexOf("  // Home Batch 4:", exactListingStart);
assert.ok(exactListingStart >= 0 && exactListingEnd > exactListingStart
  && discovery.slice(exactListingStart, exactListingEnd).includes("await ensureSchema();"),
  "direct product-detail deep links must initialize the shared Marketplace schema");
assert.ok(discovery.includes("b.blocker_id=$1 AND b.blocked_id=l.seller_id")
  && discovery.includes("b.blocker_id=l.seller_id AND b.blocked_id=$1")
  && discovery.includes("[listingId, req.user.sub]"),
  "Marketplace discovery and listing details must enforce two-way block privacy");
assert.ok(social.includes("marketplace_price_watches w JOIN marketplace_listings l")
  && social.includes("b.blocker_id = l.seller_id AND b.blocked_id = $1"),
  "Marketplace price-watch state and creation must respect two-way blocks");
assert.ok(social.includes("SELECT id, seller_id, price, reserved_quantity FROM marketplace_listings WHERE id = $1 AND seller_id = $2 FOR UPDATE")
  && social.includes("if (quantity < reservedQuantity)")
  && social.includes("stock cannot be lower than units already reserved"),
  "seller quantity edits must not reduce total stock below units reserved by orders");
assert.ok(social.includes("Number.isInteger(quantity) || quantity <= 0"),
  "new Marketplace listings must start with positive stock");

for (const [name, moduleSource, route, method] of [
  ["My Shop read", shop, "/api/marketplace/my-shop", "get"],
  ["My Shop edit", shop, "/api/marketplace/my-shop", "patch"],
  ["public shop", shop, "/api/marketplace/shops/:username", "get"],
  ["Saved Products", listings, "/api/marketplace/saved-products", "get"],
  ["saved product state", listings, "/api/marketplace/listings/:id/saved", "get"],
  ["save product", listings, "/api/marketplace/listings/:id/saved", "post"],
  ["remove saved product", listings, "/api/marketplace/listings/:id/saved", "delete"],
  ["seller listing reactivation", listings, "/api/marketplace/listings/:id/reactivate", "post"]
]) {
  const singleQuotedRoute = "app." + method + "(\x27" + route + "\x27, auth";
  const doubleQuotedRoute = "app." + method + "(\"" + route + "\", auth";
  assert.ok(moduleSource.includes(singleQuotedRoute) || moduleSource.includes(doubleQuotedRoute), name + " must require auth middleware");
}
assert.ok(shop.includes("Object.prototype.hasOwnProperty.call(body, field)"),
  "shop PATCH must distinguish omitted fields from explicit empty values");
assert.ok(shop.includes("COALESCE($2, marketplace_shop_profiles.shop_name)")
  && shop.includes("COALESCE($3, marketplace_shop_profiles.description)")
  && shop.includes("COALESCE($4, marketplace_shop_profiles.location)")
  && shop.includes("COALESCE($5, marketplace_shop_profiles.contact_phone)"),
  "partial shop edits must preserve every field the caller omitted");
assert.ok(shop.includes("at least one shop field must be provided")
  && shop.includes("shop name is required when creating a shop"),
  "shop PATCH must reject empty updates and require a name for first-time shop creation");

assert.ok(shop.includes("seller_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE"),
  "shop identity must be keyed to the authenticated seller");
const ownerUpsertStart = shop.indexOf("ON CONFLICT (seller_id) DO UPDATE");
const ownerUpsertEnd = shop.indexOf("RETURNING shop_name", ownerUpsertStart);
assert.ok(ownerUpsertStart >= 0 && ownerUpsertEnd > ownerUpsertStart && !shop.slice(ownerUpsertStart, ownerUpsertEnd).includes("seller_id ="),
  "shop edits cannot change shop ownership");
assert.ok(shop.includes("NOT EXISTS") && shop.includes("SELECT 1 FROM blocks b")
  && shop.includes("b.blocker_id = $2 AND b.blocked_id = u.id")
  && shop.includes("b.blocker_id = u.id AND b.blocked_id = $2"),
  "public shop data must respect two-way block privacy");

assert.ok(listings.includes("ALTER TABLE marketplace_listings")
  && listings.includes("ADD COLUMN IF NOT EXISTS reserved_quantity")
  && listings.includes("CREATE TABLE IF NOT EXISTS marketplace_saved_products"),
  "listing lifecycle schema must be safe to initialize repeatedly");
assert.ok(listings.includes("WHERE sp.user_id = $1") && listings.includes("ORDER BY sp.created_at DESC"),
  "Saved Products must remain scoped to the authenticated user");
assert.ok(listings.includes("JOIN marketplace_shop_profiles shop") && listings.includes("AS media_types"),
  "Saved Products responses must contain shop identity and media type fields expected by Android");
assert.ok(listings.includes("b.blocker_id = $1 AND b.blocked_id = l.seller_id")
  && listings.includes("b.blocked_id = $1 AND b.blocker_id = l.seller_id"),
  "saved product listing and save actions must respect two-way blocks");
assert.ok(listings.includes("SELECT id, seller_id, quantity, reserved_quantity")
  && listings.includes("seller_id = $2 FOR UPDATE") && listings.includes("availableQuantity < 1")
  && listings.includes("res.status(409)"),
  "reactivation must lock seller-owned inventory and reject stock fully reserved by active orders");
assert.ok(listings.includes("ON CONFLICT (user_id, listing_id) DO NOTHING")
  && listings.includes("DELETE FROM marketplace_saved_products WHERE user_id = $1 AND listing_id = $2"),
  "save/remove actions must be idempotent and user-scoped");
assert.ok(schema.includes("export function ensureMarketplaceSchemaForPool"),
  "the lifecycle routes must reuse the shared Marketplace schema contract");
for (const path of [
  "/api/marketplace/saved-products",
  "/api/marketplace/listings/:id/saved",
  "/api/marketplace/listings/:id/reactivate",
  "/api/marketplace/my-shop",
  "/api/marketplace/shops/:username"
]) assert.ok(!social.includes(path), "new runtime route " + path + " must not duplicate a route in the existing social module");

const registered = [];
const app = {
  get: (path, ...handlers) => registered.push({ method: "get", path, handlers }),
  post: (path, ...handlers) => registered.push({ method: "post", path, handlers }),
  patch: (path, ...handlers) => registered.push({ method: "patch", path, handlers }),
  delete: (path, ...handlers) => registered.push({ method: "delete", path, handlers })
};
const auth = (_req, _res, next) => next?.();
const pool = { query: async () => { throw new Error("route registration test must not access a database"); } };
registerMarketplaceShopProfileRoutes({ app, pool, auth });
registerMarketplaceListingLifecycleRoutes({ app, pool, auth });
for (const expected of [
  ["get", "/api/marketplace/my-shop"],
  ["patch", "/api/marketplace/my-shop"],
  ["get", "/api/marketplace/shops/:username"],
  ["get", "/api/marketplace/saved-products"],
  ["get", "/api/marketplace/listings/:id/saved"],
  ["post", "/api/marketplace/listings/:id/saved"],
  ["delete", "/api/marketplace/listings/:id/saved"],
  ["post", "/api/marketplace/listings/:id/reactivate"]
]) {
  const route = registered.find((candidate) => candidate.method === expected[0] && candidate.path === expected[1]);
  assert.ok(route, "registration missing " + expected[0].toUpperCase() + " " + expected[1]);
  assert.equal(route.handlers[0], auth, expected[1] + " must start with auth middleware");
}
console.log("Railway Marketplace deploy compatibility passed: " + registered.length + " authenticated route registrations plus provenance");
