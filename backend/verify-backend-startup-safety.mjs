import fs from "node:fs";
import assert from "node:assert/strict";
import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";
import { registerMarketplaceListingLifecycleRoutes } from "./marketplaceListingLifecycleRoutes.js";

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), "utf8");
const server = read("./server.js");
assert.ok(server.includes("ml.media_ids @> to_jsonb(ARRAY[mm.id::bigint])") && server.includes("ml.media_ids @> to_jsonb(ARRAY[mm.id::text])"), "public Marketplace listing media must support numeric JSON IDs and legacy string IDs");
assert.ok(server.includes("JOIN marketplace_listings ml ON ml.id=o.listing_id") && server.includes("(o.buyer_id=$2 OR o.seller_id=$2)"), "historical order photos remain visible only to buyer/seller even after listing deactivation");
const shop = read("./marketplaceShopProfiles.js");
const listings = read("./marketplaceListingLifecycleRoutes.js");

const initStart = server.indexOf("async function initDatabase()");
const initEnd = server.indexOf("\n}\n\nfunction requireConfig", initStart);
assert.ok(initStart >= 0 && initEnd > initStart, "database initializer must remain present");
const init = server.slice(initStart, initEnd);
const queryStart = init.indexOf("await pool.query(`");
const queryEnd = init.indexOf("\n  `);", queryStart);
assert.ok(queryStart >= 0 && queryEnd > queryStart, "schema setup query must be present");
const schemaQuery = init.slice(queryStart, queryEnd);
assert.ok(!schemaQuery.includes("$1"), "schema DDL batch must not use bound placeholders");
assert.ok(!schemaQuery.includes("UPDATE users SET verified = FALSE"), "startup must never clear every user verification flag");
assert.ok(init.includes('await pool.query("UPDATE users SET verified = TRUE WHERE lower(username) = $1", [officialFynxUsername]);'), "official account verification must be a separate parameterized query");
assert.ok(server.includes('import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";'), "deployed server must import My Shop routes");
assert.ok(server.includes("if (pool) registerMarketplaceShopProfileRoutes({ app, pool, auth });"), "deployed server must register My Shop routes only when a pool exists");
assert.ok(server.includes('import { registerMarketplaceListingLifecycleRoutes } from "./marketplaceListingLifecycleRoutes.js";'), "deployed server must import Saved Products and listing lifecycle routes");
assert.ok(server.includes("if (pool) registerMarketplaceListingLifecycleRoutes({ app, pool, auth });"), "deployed server must register Saved Products and listing lifecycle routes only when a pool exists");
for (const endpoint of [
  'app.get("/api/marketplace/saved-products", auth',
  'app.get("/api/marketplace/listings/:id/saved", auth',
  'app.post("/api/marketplace/listings/:id/saved", auth',
  'app.delete("/api/marketplace/listings/:id/saved", auth',
  'app.post("/api/marketplace/listings/:id/reactivate", auth'
]) assert.ok(listings.includes(endpoint), `missing authenticated Marketplace lifecycle route: ${endpoint}`);
assert.ok(listings.includes("CREATE TABLE IF NOT EXISTS marketplace_saved_products"), "Saved Products schema must be created idempotently");
assert.ok(listings.includes("ADD COLUMN IF NOT EXISTS reserved_quantity"), "listing reactivation must account for reserved stock");

const dollar = String.fromCharCode(36, 36);
assert.ok(init.includes("DO "+dollar+" BEGIN") && init.includes("END "+dollar+";"), "foreign-key DO block must retain valid PostgreSQL dollar quoting");

for (const route of [
  "app.get('/api/marketplace/my-shop', auth",
  "app.patch('/api/marketplace/my-shop', auth",
  "app.get('/api/marketplace/shops/:username', auth"
]) assert.ok(shop.includes(route), `missing authenticated My Shop route: ${route}`);
assert.ok(shop.includes("seller_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE"), "shop profile must be keyed to its seller account");
assert.ok(shop.includes("ON CONFLICT (seller_id) DO UPDATE SET"), "shop profile save must be idempotent per seller");
const updateClause = shop.split("ON CONFLICT (seller_id) DO UPDATE SET")[1]?.split("RETURNING")[0] || "";
assert.ok(updateClause && !updateClause.includes("seller_id ="), "shop profile update must not reassign ownership");
assert.ok(shop.includes("COALESCE($2, marketplace_shop_profiles.shop_name)") &&
  shop.includes("COALESCE($3, marketplace_shop_profiles.description)") &&
  shop.includes("COALESCE($4, marketplace_shop_profiles.location)") &&
  shop.includes("COALESCE($5, marketplace_shop_profiles.contact_phone)"),
  "partial My Shop updates must preserve fields omitted from the request");
assert.ok(shop.includes("WHERE $2 IS NOT NULL OR EXISTS ("), "a new shop must supply a shop name before the upsert can create a row");
assert.ok(shop.includes("at least one shop field must be supplied"), "empty My Shop patches must be rejected");
assert.ok(shop.includes("shop name is required to create a shop"), "partial updates must not create unnamed shops");
assert.ok(shop.includes("shop profile fields must be text"), "My Shop fields must reject non-text values");
assert.ok(shop.includes("b.blocker_id = $2 AND b.blocked_id = u.id") && shop.includes("b.blocker_id = u.id AND b.blocked_id = $2"), "public shop lookup must respect blocks in both directions");

const publicSerializerStart = shop.indexOf("const publicProfile =");
const privateSerializerStart = shop.indexOf("const privateProfile =");
assert.ok(publicSerializerStart >= 0 && privateSerializerStart > publicSerializerStart, "public and private shop serializers must be separate");
assert.ok(!shop.slice(publicSerializerStart, privateSerializerStart).includes("contactPhone"), "public shop profile must not expose seller contact phone");
assert.ok(shop.includes("contactPhone: row.contact_phone || ''"), "private My Shop profile must retain the seller contact phone");
assert.ok(shop.includes("return res.json({ shop: publicProfile(result.rows[0]) });"), "public seller lookup must use the public serializer");

// Exercise the partial-update handler with a mocked pool to prove omitted values
// are passed as NULL for SQL COALESCE rather than being overwritten with empty text.
const routes = new Map();
const app = {
  get(path, ...handlers) { routes.set(`GET ${path}`, handlers.at(-1)); },
  post(path, ...handlers) { routes.set(`POST ${path}`, handlers.at(-1)); },
  patch(path, ...handlers) { routes.set(`PATCH ${path}`, handlers.at(-1)); },
  delete(path, ...handlers) { routes.set(`DELETE ${path}`, handlers.at(-1)); }
};
let capturedPatchParameters = null;
const pool = {
  async query(sql, parameters = []) {
    if (sql.includes("CREATE TABLE IF NOT EXISTS marketplace_shop_profiles")) return { rows: [] };
    if (sql.includes("INSERT INTO marketplace_shop_profiles")) {
      capturedPatchParameters = parameters;
      return { rows: [{
        shop_name: "Original Shop",
        description: "Updated details",
        location: "Lagos",
        contact_phone: "08000000000",
        updated_at: "2026-10-31T00:00:00.000Z"
      }] };
    }
    if (sql.includes("SELECT username, display_name FROM users")) {
      return { rows: [{ username: "seller", display_name: "Seller" }] };
    }
    throw new Error("Unexpected query in My Shop route verification");
  }
};
registerMarketplaceShopProfileRoutes({ app, pool, auth: (_req, _res, next) => next() });
registerMarketplaceListingLifecycleRoutes({ app, pool, auth: (_req, _res, next) => next() });
for (const route of [
  "GET /api/marketplace/saved-products",
  "GET /api/marketplace/listings/:id/saved",
  "POST /api/marketplace/listings/:id/saved",
  "DELETE /api/marketplace/listings/:id/saved",
  "POST /api/marketplace/listings/:id/reactivate"
]) assert.equal(typeof routes.get(route), "function", `missing registered Marketplace lifecycle handler: ${route}`);
const response = {
  statusCode: 200,
  body: null,
  status(code) { this.statusCode = code; return this; },
  json(body) { this.body = body; return this; }
};
const patchHandler = routes.get("PATCH /api/marketplace/my-shop");
assert.equal(typeof patchHandler, "function", "authenticated My Shop patch handler must be registered");
await patchHandler({ user: { sub: "42" }, body: { description: "Updated details" } }, response);
assert.equal(response.statusCode, 200, "a valid partial shop update must succeed");
assert.deepEqual(capturedPatchParameters, ["42", null, "Updated details", null, null], "omitted shop fields must remain NULL for SQL preservation");
assert.equal(response.body.shop.shopName, "Original Shop", "partial edit must preserve the existing shop name");
assert.equal(response.body.shop.location, "Lagos", "partial edit must preserve the existing location");
assert.equal(response.body.shop.contactPhone, "08000000000", "private response must preserve the seller's contact phone");

const emptyResponse = {
  statusCode: 200,
  body: null,
  status(code) { this.statusCode = code; return this; },
  json(body) { this.body = body; return this; }
};
await patchHandler({ user: { sub: "42" }, body: {} }, emptyResponse);
assert.equal(emptyResponse.statusCode, 400, "empty My Shop patches must be rejected");

console.log("Backend startup safety and Marketplace My Shop verification passed.");
