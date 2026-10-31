import fs from "node:fs";
import assert from "node:assert/strict";

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), "utf8");
const server = read("./server.js");
const shop = read("./marketplaceShopProfiles.js");
const databasePool = read("./databasePool.js");
const scalability = read("./scalability.js");
const backgroundJobs = read("./backgroundJobs.js");
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
assert.ok(databasePool.includes("DB_POOL_MAX") && databasePool.includes("max: maximumConnections"),
  "backend modules must share a centrally bounded PostgreSQL pool");
assert.ok(databasePool.includes("min: 0,"),
  "the shared pool must allow idle connections to be released");
assert.ok(server.includes('import { pool } from "./databasePool.js";'),
  "the HTTP API must use the shared PostgreSQL pool");
assert.ok(scalability.includes("installFailureRecovery({server,pool,logger:console})"),
  "database failure recovery must receive the live shared pool");
assert.ok(scalability.includes("sharedPool:pool"),
  "background job workers must reuse the shared PostgreSQL pool");
assert.ok(backgroundJobs.includes("if (pool && ownsPool) await pool.end();"),
  "background worker shutdown must not close the shared pool");
const pooledModules = [
  "mediaPrivacy.js","followRoutes.js","businessRoutes.js","socialHardening.js","presencePrivacy.js",
  "monetizationRoutes.js","groupContentRoutes.js","notificationDevices.js","r6gIntegrationRoutes.js",
  "aiConversationRoutes.js","marketplaceProtection.js","marketplacePayoutRetry.js","statusManagementRoutes.js",
  "statusInteractionRoutes.js","marketplaceMediaPrivacy.js","peopleResponseHardening.js","groupMembershipRoutes.js",
  "marketplaceAdvertising.js","marketplaceSettlement.js","marketplaceInspectionExpiry.js",
  "marketplaceSettlementWorker.js","marketplaceProtectionResolution.js","marketplacePaymentExpiry.js",
  "marketplaceBatch2FinalHardening.js","fynxAiToolRegistry.js"
];
for (const file of pooledModules) {
  const source = read("./" + file);
  assert.ok(source.includes('import { pool } from "./databasePool.js";'), `${file} must use the shared pool`);
  assert.ok(!source.includes("new Pool("), `${file} must not create a private PostgreSQL pool`);
}
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
