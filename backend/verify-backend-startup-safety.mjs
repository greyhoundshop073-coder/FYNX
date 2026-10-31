import fs from "node:fs";
import assert from "node:assert/strict";

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), "utf8");
const server = read("./server.js");
const shop = read("./marketplaceShopProfiles.js");

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
assert.ok(shop.includes("b.blocker_id = $2 AND b.blocked_id = u.id") && shop.includes("b.blocker_id = u.id AND b.blocked_id = $2"), "public shop lookup must respect blocks in both directions");

const publicSerializerStart = shop.indexOf("const publicProfile =");
const privateSerializerStart = shop.indexOf("const privateProfile =");
assert.ok(publicSerializerStart >= 0 && privateSerializerStart > publicSerializerStart, "public and private shop serializers must be separate");
assert.ok(!shop.slice(publicSerializerStart, privateSerializerStart).includes("contactPhone"), "public shop profile must not expose seller contact phone");
assert.ok(shop.includes("contactPhone: row.contact_phone || ''"), "private My Shop profile must retain the seller contact phone");
assert.ok(shop.includes("return res.json({ shop: publicProfile(result.rows[0]) });"), "public seller lookup must use the public serializer");

console.log("Backend startup safety and Marketplace My Shop verification passed.");
