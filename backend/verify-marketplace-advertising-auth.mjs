import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const advertising = await readFile(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const helperMatch = advertising.match(/function authenticate\(req, res\) \{[\s\S]*?\n\}/);
assert.ok(helperMatch, "Marketplace advertising must expose its local auth helper");

// Execute the exact helper source with a controlled JWT verifier and a minimal
// Express response double. No network, database or real credentials are used.
const createAuthenticate = new Function("jwt", "JWT_SECRET", `${helperMatch[0]}\nreturn authenticate;`);
const jwt = {
  verify(token) {
    if (token === "valid-test-token") return { sub: "314" };
    throw new Error("invalid test token");
  }
};
function responseDouble() {
  return {
    statusCode: null,
    body: null,
    sends: 0,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; this.sends += 1; return this; }
  };
}

{
  const res = responseDouble();
  const authenticate = createAuthenticate(jwt, "");
  assert.equal(authenticate({ headers: {} }, res), null, "missing JWT configuration must stop the route");
  assert.equal(res.statusCode, 503);
  assert.equal(res.sends, 1, "missing JWT configuration sends exactly one response");
}
{
  const res = responseDouble();
  const authenticate = createAuthenticate(jwt, "test-secret");
  assert.equal(authenticate({ headers: {} }, res), null, "missing bearer token must stop the route");
  assert.equal(res.statusCode, 401);
  assert.equal(res.sends, 1, "missing bearer token sends exactly one response");
}
{
  const res = responseDouble();
  const authenticate = createAuthenticate(jwt, "test-secret");
  assert.equal(authenticate({ headers: { authorization: "Bearer invalid-test-token" } }, res), null, "invalid bearer token must stop the route");
  assert.equal(res.statusCode, 401);
  assert.equal(res.sends, 1, "invalid bearer token sends exactly one response");
}
{
  const res = responseDouble();
  const authenticate = createAuthenticate(jwt, "test-secret");
  assert.deepEqual(authenticate({ headers: { authorization: "Bearer valid-test-token" } }, res), { userId: 314 });
  assert.equal(res.sends, 0, "valid authentication does not send an early response");
}

const authCallSites = advertising.match(/const auth = authenticate\(req, res\);/g) || [];
const guardedCallSites = advertising.match(/const auth = authenticate\(req, res\);\s*if \(!auth\) return;/g) || [];
assert.ok(authCallSites.length > 0, "advertising routes must continue to use the shared auth helper");
assert.equal(guardedCallSites.length, authCallSites.length, "every route must stop when authentication returns null");

console.log("Marketplace advertising auth failure isolation passed: missing configuration, missing token, invalid token, valid token, and all route guards.");
