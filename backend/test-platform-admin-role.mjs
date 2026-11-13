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
    if (sql.includes("CREATE TABLE IF NOT EXISTS fynx_platform_owner")) {
      return { rows: [], rowCount: 0 };
    }
    if (sql.includes("fynx_platform_owner_bootstrap")) {
      // Before first signup the candidate query returns no row; a later
      // request can seed the first account without restarting the backend.
      if (firstUserExists && persistedOwnerId === null) persistedOwnerId = ownerId;
      return { rows: [], rowCount: 0 };
    }
    if (sql.includes("SELECT owner_user_id FROM fynx_platform_owner")) {
      return {
        rows: persistedOwnerId === null ? [] : [{ owner_user_id: persistedOwnerId }],
        rowCount: persistedOwnerId === null ? 0 : 1
      };
    }
    if (sql.includes("SELECT 1 FROM fynx_admin_roles")) {
      return params[0] === adminId ? { rows: [{}], rowCount: 1 } : { rows: [], rowCount: 0 };
    }
    throw new Error("Unexpected role-resolver SQL: " + sql);
  }
};

const resolve = createPlatformAdminRoleResolver(pool);
assert.equal(await resolve("18"), null, "an empty deployment must not invent an Owner or grant an ordinary account access");
assert.equal(persistedOwnerId, null, "empty user table must leave the Owner singleton unseeded");

firstUserExists = true;
assert.equal(await resolve(ownerId), "OWNER", "the first registered user must be seeded after a cold empty deployment");
assert.equal(await resolve(adminId), "ADMIN", "an assigned administrator must retain ADMIN access");
assert.equal(await resolve("18"), null, "ordinary accounts must not receive an administrative role");
assert.equal(await resolve("1"), null, "lowest account ID must not override a different persisted Owner");
assert.equal(await resolve("0"), null, "zero is not a valid user ID");
assert.equal(await resolve("01"), null, "non-canonical numeric identities must be rejected");
assert.equal(await resolve("9007199254740993 OR 1=1"), null, "malformed identities must be rejected before SQL");

assert.equal(
  queries.filter(q => q.sql.includes("CREATE TABLE IF NOT EXISTS fynx_platform_owner")).length,
  1,
  "Owner schema must initialize once per resolver"
);
assert.equal(
  queries.filter(q => q.sql.includes("fynx_platform_owner_bootstrap")).length,
  2,
  "legacy bootstrap must retry while no user exists, then stop after the singleton is created"
);
assert.equal(
  queries.filter(q => q.sql.includes("SELECT owner_user_id FROM fynx_platform_owner")).length,
  7,
  "role checks must read persisted Owner identity before deciding Owner/Admin"
);
console.log("Platform Owner resolver passed: cold empty deployment, first-user bootstrap, persisted identity, BIGINT-safe IDs and Admin/non-admin access.");
