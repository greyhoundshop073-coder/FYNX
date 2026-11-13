import assert from "node:assert/strict";
import { createAdminAuthMiddleware } from "./adminRoutes.js";

const SECRET = "fynx-admin-auth-session-version-test-secret";

async function invoke({ token = "valid", claims = { sub: "42", sv: 4 }, row = { session_version: 4 }, databaseError = null } = {}) {
  let queryCount = 0;
  let nextCount = 0;
  const pool = {
    async query(sql, params) {
      queryCount += 1;
      assert.equal(sql, "SELECT session_version FROM users WHERE id=$1");
      assert.deepEqual(params, [claims.sub]);
      if (databaseError) throw databaseError;
      return { rows: row ? [row] : [] };
    }
  };
  const jwt = {
    verify(receivedToken, receivedSecret) {
      assert.equal(receivedSecret, SECRET);
      if (receivedToken === "invalid") throw new Error("invalid token");
      return claims;
    }
  };
  const middleware = createAdminAuthMiddleware({ pool, jwt, jwtSecret: SECRET });
  const req = { get: name => name.toLowerCase() === "authorization" && token ? `Bearer ${token}` : "" };
  const res = {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; }
  };
  await middleware(req, res, () => { nextCount += 1; });
  return { res, req, queryCount, nextCount };
}

const accepted = await invoke();
assert.equal(accepted.nextCount, 1, "a valid current session must pass Admin auth");
assert.equal(accepted.queryCount, 1, "Admin auth must consult the current session version");

const revoked = await invoke({ claims: { sub: "42", sv: 3 }, row: { session_version: 4 } });
assert.equal(revoked.res.statusCode, 401, "a password-reset-revoked Admin token must be rejected");
assert.equal(revoked.res.body?.error, "session expired; sign in again");
assert.equal(revoked.nextCount, 0, "a revoked token must never reach role or Admin handlers");

const deleted = await invoke({ row: null });
assert.equal(deleted.res.statusCode, 401, "deleted accounts must not retain Admin access");
assert.equal(deleted.nextCount, 0);

const unavailable = await invoke({ databaseError: new Error("database unavailable") });
assert.equal(unavailable.res.statusCode, 503, "session verification failures must fail closed");
assert.equal(unavailable.nextCount, 0);

const invalid = await invoke({ token: "invalid" });
assert.equal(invalid.res.statusCode, 401, "invalid JWTs must be rejected before database access");
assert.equal(invalid.queryCount, 0);

const missing = await invoke({ token: "" });
assert.equal(missing.res.statusCode, 401, "missing bearer credentials must be rejected");
assert.equal(missing.queryCount, 0);

console.log("ADMIN_AUTH_SESSION_VERSION=GREEN (current, revoked, deleted, invalid, missing and database-unavailable cases)");
