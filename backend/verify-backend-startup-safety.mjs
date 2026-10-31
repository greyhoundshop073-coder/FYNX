import fs from "node:fs";
import assert from "node:assert/strict";

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), "utf8");
const server = read("./server.js");
const shop = read("./marketplaceShopProfiles.js");
const initStart = server.indexOf("async function initDatabase()");
const initEnd = server.indexOf("\n}\n\nfunction requireConfig", initStart);
assert.ok(initStart >= 0 && initEnd > initStart, "database initialization function must remain discoverable");
const init = server.slice(initStart, initEnd);
const queryStart = init.indexOf("await pool.query(`");
const queryEnd = init.indexOf("\n  `);", queryStart);
assert.ok(queryStart >= 0 && queryEnd > queryStart, "schema DDL must run as a parameter-free query batch");
const initialSchemaQuery = init.slice(queryStart, queryEnd);
assert.ok(!initialSchemaQuery.includes("$1"), "schema DDL batch must not contain bound placeholders");
assert.ok(init.includes('await pool.query("UPDATE users SET verified = TRUE WHERE lower(username) = $1", [officialFynxUsername]);'),
  "official-account verification must use its own parameterized query");
assert.ok(!init.includes("UPDATE users SET verified = FALSE"),
  "startup must never reset every user's verification flag");
assert.ok(server.includes("max: Number(process.env.DB_POOL_MAX || 10),"),
  "primary database pool must have a conservative default cap");
assert.ok(server.includes("min: 0,"),
  "primary database pool must allow idle connections to be released");
assert.ok(server.includes('import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";'),
  "My Shop route module must be imported by the deployed server");
assert.ok(server.includes("if (pool) registerMarketplaceShopProfileRoutes({ app, pool, auth });"),
  "My Shop routes must be registered only when the database pool exists");
assert.ok(server.includes("DO $fnx$ BEGIN") && server.includes("END $fnx$;"),
  "the idempotent message-media foreign-key guard must retain a valid dollar-quote delimiter");

for (const route of [
  "app.get('/api/marketplace/my-shop', auth",
  "app.patch('/api/marketplace/my-shop', auth",
  "app.get('/api/marketplace/shops/:username', auth"
]) assert.ok(shop.includes(route), `missing authenticated shop route: ${route}`);
assert.ok(shop.includes("seller_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE"),
  "shop identity must remain owned by exactly one seller account");
assert.ok(shop.includes("ON CONFLICT (seller_id) DO UPDATE SET"),
  "shop updates must upsert against the authenticated seller key");
const conflictUpdate = shop.split("ON CONFLICT (seller_id) DO UPDATE SET")[1]?.split("RETURNING")[0] || "";
assert.ok(conflictUpdate, "shop upsert update clause must exist");
assert.ok(!conflictUpdate.includes("seller_id ="), "shop update must not permit ownership reassignment");
assert.ok(shop.includes("b.blocker_id = $2 AND b.blocked_id = u.id")
  && shop.includes("b.blocker_id = u.id AND b.blocked_id = $2"),
  "public shop details must respect blocks in both directions");

console.log("Backend startup safety and Marketplace My Shop verification passed.");
