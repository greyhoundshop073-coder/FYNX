import assert from "node:assert/strict";
import { createAdminAuthMiddleware } from "./adminRoutes.js";

const SECRET = "fynx-admin-auth-session-version-test-secret";
async function invoke({ token = "valid", claims = { sub: "42", sv: 4 }, row = { session_version: 4 }, databaseError = null } = {}) {
  let queryCount = 0, nextCount = 0;
  const pool = { async query(sql, params) {
    queryCount += 1;
    assert.equal(sql, "SELECT session_version FROM users WHERE id=$1");
    assert.deepEqual(params, [claims.sub]);
    if (databaseError) throw databaseError;
    return { rows: row ? [row] : [] };
  }};
  const jwt = { verify(receivedToken, receivedSecret) {
    assert.equal(receivedSecret, SECRET);
    if (receivedToken === "invalid") throw new Error("invalid token");
    return claims;
  }};
  const middleware = createAdminAuthMiddleware({ pool, jwtApi: jwt, jwtSecret: SECRET });
  const req = { get: name => name.toLowerCase() === "authorization" && token ? `Bearer ${token}` : "" };
  const res = { statusCode: 200, body: null, status(code) { this.statusCode = code; return this; }, json(body) { this.body = body; return this; }};
  await middleware(req, res, () => { nextCount += 1; });
  return { res, req, queryCount, nextCount };
}
const accepted = await invoke();
assert.equal(accepted.nextCount, 1);
assert.equal(accepted.queryCount, 1);
const revoked = await invoke({ claims: { sub: "42", sv: 3 }, row: { session_version: 4 } });
assert.equal(revoked.res.statusCode, 401);
assert.equal(revoked.res.body?.error, "session expired; sign in again");
assert.equal(revoked.nextCount, 0);
const deleted = await invoke({ row: null });
assert.equal(deleted.res.statusCode, 401);
assert.equal(deleted.nextCount, 0);
const unavailable = await invoke({ databaseError: new Error("database unavailable") });
assert.equal(unavailable.res.statusCode, 503);
assert.equal(unavailable.nextCount, 0);
const invalid = await invoke({ token: "invalid" });
assert.equal(invalid.res.statusCode, 401);
assert.equal(invalid.queryCount, 0);
const missing = await invoke({ token: "" });
assert.equal(missing.res.statusCode, 401);
assert.equal(missing.queryCount, 0);
const outOfRange = await invoke({ claims: { sub: "9223372036854775808", sv: 4 } });
assert.equal(outOfRange.res.statusCode, 401);
assert.equal(outOfRange.queryCount, 0);
assert.equal(outOfRange.nextCount, 0);
console.log("ADMIN_AUTH_SESSION_VERSION=GREEN (current, revoked, deleted, invalid, missing and unavailable cases)");
