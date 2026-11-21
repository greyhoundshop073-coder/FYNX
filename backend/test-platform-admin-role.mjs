import assert from "node:assert/strict";
import { createPlatformAdminRoleResolver } from "./platformAdminRole.js";

const ownerId = "9007199254740993";
const adminId = "17";
const queries = [];
let firstUserExists = false;
let persistedOwnerId = null;

const pool = {
  async query(sql, params = []) {
    queries.push({ sql, params });
    if (sql.includes("CREATE TABLE IF NOT EXISTS fynx_platform_owner")) return { rows: [], rowCount: 0 };
    if (sql.includes("fynx_platform_owner_bootstrap")) {
      if (firstUserExists && persistedOwnerId === null) persistedOwnerId = ownerId;
      return { rows: [], rowCount: 0 };
    }
    if (sql.includes("SELECT owner_user_id FROM fynx_platform_owner")) {
      return { rows: persistedOwnerId === null ? [] : [{ owner_user_id: persistedOwnerId }], rowCount: persistedOwnerId === null ? 0 : 1 };
    }
    if (sql.includes("SELECT 1 FROM fynx_admin_roles")) {
      return params[0] === adminId ? { rows: [{}], rowCount: 1 } : { rows: [], rowCount: 0 };
    }
    throw new Error("Unexpected role-resolver SQL: " + sql);
  }
};

const resolve = createPlatformAdminRoleResolver(pool);
assert.equal(await resolve("18"), null);
assert.equal(persistedOwnerId, null);
firstUserExists = true;
assert.equal(await resolve(ownerId), "OWNER");
assert.equal(await resolve(adminId), "ADMIN");
assert.equal(await resolve("18"), null);
assert.equal(await resolve("1"), null, "a changed lowest-ID user must not inherit Owner");
assert.equal(await resolve("0"), null);
assert.equal(await resolve("01"), null);
assert.equal(await resolve("9007199254740993 OR 1=1"), null);
const beforeInvalid = queries.length;
assert.equal(await resolve("9223372036854775808"), null);
assert.equal(await resolve(9007199254740992), null);
assert.equal(queries.length, beforeInvalid, "invalid IDs must be rejected before SQL");
assert.equal(queries.filter(q => q.sql.includes("CREATE TABLE IF NOT EXISTS fynx_platform_owner")).length, 1);
assert.equal(queries.filter(q => q.sql.includes("fynx_platform_owner_bootstrap")).length, 2);
console.log("PLATFORM_OWNER_RESOLVER=GREEN (bootstrap, persistence, role separation and BIGINT validation)");
