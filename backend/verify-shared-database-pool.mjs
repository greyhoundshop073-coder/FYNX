import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const directory = path.dirname(fileURLToPath(import.meta.url));
const sharedModules = [
  "server.js", "adminRoutes.js", "followRoutes.js", "businessRoutes.js", "presencePrivacy.js",
  "monetizationRoutes.js", "notificationDevices.js", "aiConversationRoutes.js",
  "socialHardening.js", "marketplaceProtection.js", "marketplacePayoutRetry.js",
  "statusManagementRoutes.js", "groupContentRoutes.js", "marketplaceMediaPrivacy.js",
  "peopleResponseHardening.js", "groupMembershipRoutes.js", "marketplaceSettlement.js",
  "marketplaceInspectionExpiry.js", "marketplaceSettlementWorker.js", "fynxAiToolRegistry.js",
  "marketplaceProtectionResolution.js", "marketplaceAdvertising.js", "statusInteractionRoutes.js",
  "mediaPrivacy.js", "r6gIntegrationRoutes.js", "marketplaceBatch2FinalHardening.js",
  "groupRoutes.js", "groupInviteRoutes.js", "notificationPreferences.js", "privacyRoutes.js", "profileRoutes.js",
  "marketplacePaymentExpiry.js"
];

for (const file of sharedModules) {
  const source = await readFile(path.join(directory, file), "utf8");
  assert.match(source, /import \{ getFynxDatabasePool \} from ["']\.\/databasePool\.js["'];/, file + " must use shared database pool module");
  assert.match(source, /const pool = getFynxDatabasePool\(\);/, file + " must use the shared pool instance");
  assert.doesNotMatch(source, /const\s+pool\s*=\s*DATABASE_URL\s*\?\s*new\s+Pool\(/, file + " must not create its own database pool");
}

const runtimeFiles = (await readdir(directory)).filter((name) => name.endsWith(".js"));
for (const file of runtimeFiles) {
  const source = await readFile(path.join(directory, file), "utf8");
  if (file === "databasePool.js" || file === "backgroundJobs.js") continue;
  assert.doesNotMatch(source, /new\s+(?:pg\.)?Pool\s*\(/, file + " must not create a separate PostgreSQL pool");
}

const backgroundJobs = await readFile(path.join(directory, "backgroundJobs.js"), "utf8");
assert.ok(!backgroundJobs.startsWith('import { getFynxDatabasePool }'), "shared pool import must remain lazy when jobs are disabled");
assert.match(backgroundJobs, /const usesSharedPool = connectionString === \(process\.env\.DATABASE_URL \|\| ""\);/);
assert.ok(backgroundJobs.includes('poolPromise = import("./databasePool.js").then(({ getFynxDatabasePool }) => getFynxDatabasePool());'), "default jobs must lazily load the shared pool");
assert.match(backgroundJobs, /if \(pool && !usesSharedPool\) await pool\.end\(\)/,
  "stopping the job queue must not close the shared application pool");
assert.match(backgroundJobs, /new pg\.Pool\(/,
  "explicit alternate connection strings must remain supported for isolated tests/tools");

const previousUrl = process.env.DATABASE_URL;
const previousMax = process.env.DB_POOL_MAX;
process.env.DATABASE_URL = "postgres://fynx_test:fynx_test@127.0.0.1:5432/fynx_test";
process.env.DB_POOL_MAX = "7";
const { getFynxDatabasePool, getFynxDatabasePoolConfig } = await import("./databasePool.js");
const first = getFynxDatabasePool();
const second = getFynxDatabasePool();
assert.ok(first, "configured database URL must create a pool");
assert.equal(first, second, "all consumers must receive one shared pool");
assert.equal(getFynxDatabasePoolConfig().max, 7, "DB_POOL_MAX must control the shared pool bound");
assert.equal(first.options.max, 7);
assert.equal(first.options.min, 0);
await first.end();
if (previousUrl === undefined) delete process.env.DATABASE_URL; else process.env.DATABASE_URL = previousUrl;
if (previousMax === undefined) delete process.env.DB_POOL_MAX; else process.env.DB_POOL_MAX = previousMax;

console.log("SHARED DATABASE POOL CONTRACT: GREEN");
console.log(`Verified ${sharedModules.length} shared-pool runtime modules, bounded pool settings, and queue shutdown ownership.`);
