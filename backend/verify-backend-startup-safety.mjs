import fs from "node:fs";
import assert from "node:assert/strict";

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), "utf8");
const server = read("./server.js");
const shop = read("./marketplaceShopProfiles.js");
const initStart = server.indexOf("async function initDatabase()");
const initEnd = server.indexOf("\n}\n\nfunction requireConfig", initStart);
assert.ok(initStart >= 0 && initEnd > initStart, "database initialization function must remain discoverable");
const init = server.slice(initStart, initEnd);
const initialSchemaQuery = init.match(/await pool\\.query\\(\\x60([\\s\\S]*?)\\x60\\s*\\);/);
assert.ok(initialSchemaQuery, "schema DDL must run as a parameter-free query batch");
assert.doesNotMatch(initialSchemaQuery[1], /\\$1/, "schema DDL batch must not contain bound placeholders");
assert.match(init, /await pool\\.query\\("UPDATE users SET verified = TRUE WHERE lower\\(username\\) = \\$1", \\[officialFynxUsername\\]\\);/,
  "official-account verification must use its own parameterized query");
assert.doesNotMatch(init, /UPDATE users SET verified = FALSE/,
  "startup must never reset every user's verification flag");
assert.match(server, /max: Number\\(process\\.env\\.DB_POOL_MAX \\|\\| 10\\)/,
  "primary database pool must have a conservative default cap");
assert.match(server, /min: 0,/,
  "primary database pool must allow idle connections to be released");
assert.match(server, /import \\{ registerMarketplaceShopProfileRoutes \\} from "\\.\\/marketplaceShopProfiles\\.js";/,
  "My Shop route module must be imported by the deployed server");
assert.match(server, /if \\(pool\\) registerMarketplaceShopProfileRoutes\\(\\{ app, pool, auth \\}\\);/,
  "My Shop routes must be registered only when the database pool exists");

for (const route of [
  "app.get('/api/marketplace/my-shop', auth",
  "app.patch('/api/marketplace/my-shop', auth",
  "app.get('/api/marketplace/shops/:username', auth"
]) assert.ok(shop.includes(route), `missing authenticated shop route: ${route}`);
assert.ok(shop.includes("seller_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE"),
  "shop identity must remain owned by exactly one seller account");
assert.ok(shop.includes("ON CONFLICT (seller_id) DO UPDATE SET"),
  "shop updates must upsert against the authenticated seller key");
assert.ok(!/ON CONFLICT\\s*\\(seller_id\\)[\\s\\S]{0,400}seller_id\\s*=/.test(shop),
  "shop update must not permit ownership reassignment");
assert.ok(shop.includes("b.blocker_id = $2 AND b.blocked_id = u.id")
  && shop.includes("b.blocker_id = u.id AND b.blocked_id = $2"),
  "public shop details must respect blocks in both directions");

console.log("Backend startup safety and Marketplace My Shop verification passed.");
