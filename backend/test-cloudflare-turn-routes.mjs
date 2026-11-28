import assert from "node:assert/strict";
import {
  isActiveFynxCallParticipant,
  registerCloudflareTurnRoutes
} from "./cloudflareTurnRoutes.js";

const KEY_ID = "0123456789abcdef0123456789abcdef";
const KEY_SECRET = "a".repeat(64);
const CALL_ID = "call_fynx_test_123";

function makeResponse() {
  return {
    statusCode: 200,
    headers: {},
    body: null,
    setHeader(name, value) { this.headers[name] = value; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; }
  };
}

function register(options = {}) {
  const routes = [];
  const app = { post(path, ...handlers) { routes.push({ path, handlers }); } };
  const auth = (_req, _res, next) => next();
  const rateLimit = (_req, _res, next) => next();
  const pool = options.pool || {
    async query(sql, params) {
      if (sql.includes("fynx_realtime_calls")) return { rowCount: 1, rows: [{}] };
      return { rowCount: 0, rows: [] };
    }
  };
  registerCloudflareTurnRoutes({
    app,
    pool,
    auth,
    rateLimit,
    resolveActiveCall: options.resolveActiveCall || null,
    env: options.env || {
      CLOUDFLARE_TURN_KEY_ID: KEY_ID,
      CLOUDFLARE_TURN_KEY_SECRET: KEY_SECRET
    },
    fetchImpl: options.fetchImpl || (async () => ({
      ok: true,
      async json() {
        return {
          iceServers: [
            { urls: ["stun:stun.cloudflare.com:3478"] },
            {
              urls: [
                "turn:turn.cloudflare.com:3478?transport=udp",
                "turn:turn.cloudflare.com:443?transport=udp",
                "turn:turn.cloudflare.com:53?transport=udp",
                "turns:turn.cloudflare.com:5349?transport=tcp",
                "turns:turn.cloudflare.com:443?transport=tcp"
              ],
              username: "short-lived-turn-user",
              credential: "short-lived-turn-password"
            }
          ]
        };
      }
    }))
  });
  assert.equal(routes.length, 1);
  assert.equal(routes[0].path, "/api/calls/ice-servers");
  assert.equal(routes[0].handlers.length, 3, "authentication and shared rate-limit middleware must run before the route");
  return routes[0].handlers[2];
}

async function call(handler, req = {}) {
  const res = makeResponse();
  await handler({ user: { sub: "42" }, body: { callId: CALL_ID }, ...req }, res);
  return res;
}

// Direct calls and Marketplace Live Proof (which uses an active direct-call record)
// are authorized only for one of the two participants.
{
  const pool = {
    async query(sql, params) {
      assert.deepEqual(params, [CALL_ID, "42"]);
      if (sql.includes("fynx_realtime_calls")) return { rowCount: 1, rows: [{}] };
      throw new Error("Group membership lookup should not run after a direct-call match.");
    }
  };
  assert.equal(await isActiveFynxCallParticipant(pool, CALL_ID, "42"), true);
}

// Group-call credentials require an active call and a JOINED participant.
{
  const pool = {
    async query(sql, params) {
      assert.deepEqual(params, [CALL_ID, "42"]);
      if (sql.includes("fynx_realtime_calls")) return { rowCount: 0, rows: [] };
      assert.match(sql, /p\.status='JOINED'/);
      assert.match(sql, /c\.status='ACTIVE'/);
      return { rowCount: 1, rows: [{}] };
    }
  };
  assert.equal(await isActiveFynxCallParticipant(pool, CALL_ID, "42"), true);
}

// Non-participants and non-active calls are denied.
{
  const pool = { async query() { return { rowCount: 0, rows: [] }; } };
  assert.equal(await isActiveFynxCallParticipant(pool, CALL_ID, "42"), false);
  assert.equal(await isActiveFynxCallParticipant(pool, "invalid", "42"), false);
}

// Railway direct-call authorization uses the active in-memory call record and does not
// incorrectly require a Neon fynx_realtime_calls row.
{
  const pool = { async query() { throw new Error("Railway active call lookup must use the runtime resolver"); } };
  assert.equal(await isActiveFynxCallParticipant(
    pool, CALL_ID, "42",
    async (callId, userId) => callId === CALL_ID && userId === "42" ? { id: callId, kind: "direct" } : null
  ), true);
  assert.equal(await isActiveFynxCallParticipant(
    pool, CALL_ID, "99",
    async () => null
  ), false);
}

// Happy path: secret stays in the backend request, temporary credentials only are returned,
// and unusable port-53 relays are filtered out.
{
  let request;
  const handler = register({
    fetchImpl: async (url, init) => {
      request = { url, init };
      return {
        ok: true,
        async json() {
          return {
            iceServers: [
              { urls: ["stun:stun.cloudflare.com:3478"] },
              {
                urls: [
                  "turn:turn.cloudflare.com:3478?transport=udp",
                  "turn:turn.cloudflare.com:53?transport=udp",
                  "turns:turn.cloudflare.com:443?transport=tcp"
                ],
                username: "temporary-user",
                credential: "temporary-password"
              }
            ]
          };
        }
      };
    }
  });
  const res = await call(handler);
  assert.equal(res.statusCode, 200);
  assert.equal(res.headers["Cache-Control"], "private, no-store, max-age=0");
  assert.equal(res.headers.Pragma, "no-cache");
  assert.equal(request.url, `https://rtc.live.cloudflare.com/v1/turn/keys/${KEY_ID}/credentials/generate-ice-servers`);
  assert.equal(request.init.method, "POST");
  assert.equal(request.init.headers.Authorization, `Bearer ${KEY_SECRET}`);
  assert.equal(JSON.parse(request.init.body).ttl, 3600);
  assert.equal(res.body.iceServers.length, 2);
  assert.deepEqual(res.body.iceServers[0].urls, ["stun:stun.cloudflare.com:3478"]);
  assert.deepEqual(res.body.iceServers[1].urls, [
    "turn:turn.cloudflare.com:3478?transport=udp",
    "turns:turn.cloudflare.com:443?transport=tcp"
  ]);
  assert.equal(res.body.iceServers[1].username, "temporary-user");
  assert.equal(res.body.iceServers[1].credential, "temporary-password");
  assert.equal(res.body.expiresInSeconds, 3600);
  assert.equal(res.body.refreshAfterSeconds, 2700);
  assert.equal(JSON.stringify(res.body).includes(KEY_SECRET), false);
}

// A missing/invalid key configuration fails closed and does not call Cloudflare.
{
  let called = false;
  const handler = register({
    env: {},
    fetchImpl: async () => { called = true; throw new Error("should not call"); }
  });
  const res = await call(handler);
  assert.equal(res.statusCode, 503);
  assert.equal(res.body.code, "TURN_NOT_CONFIGURED");
  assert.equal(called, false);
}

// Malformed TTL configuration is not silently coerced into a different TTL.
{
  const handler = register({ env: {
    CLOUDFLARE_TURN_KEY_ID: KEY_ID,
    CLOUDFLARE_TURN_KEY_SECRET: KEY_SECRET,
    CLOUDFLARE_TURN_CREDENTIAL_TTL_SECONDS: "172801"
  } });
  const res = await call(handler);
  assert.equal(res.statusCode, 503);
  assert.equal(res.body.code, "TURN_CONFIGURATION_INVALID");
}

// Invalid call IDs never reach database authorization or Cloudflare.
{
  let queried = false;
  const handler = register({ pool: { async query() { queried = true; return { rowCount: 0, rows: [] }; } } });
  const res = await call(handler, { body: { callId: "not-a-call" } });
  assert.equal(res.statusCode, 400);
  assert.equal(queried, false);
}

// Provider outages and malformed provider responses are surfaced as retryable service errors.
{
  const handler = register({ fetchImpl: async () => ({ ok: false, status: 502 }) });
  const res = await call(handler);
  assert.equal(res.statusCode, 503);
  assert.equal(res.body.code, "TURN_PROVIDER_UNAVAILABLE");
}
{
  const handler = register({ fetchImpl: async () => ({ ok: true, async json() { return { iceServers: [] }; } }) });
  const res = await call(handler);
  assert.equal(res.statusCode, 503);
  assert.equal(res.body.code, "TURN_PROVIDER_INVALID_RESPONSE");
}

// Registration is rejected if the shared durable rate limiter is omitted.
assert.throws(() => registerCloudflareTurnRoutes({
  app: { post() {} },
  pool: { async query() { return { rowCount: 0, rows: [] }; } },
  auth: () => {},
  env: {}
}), /rate limiting/);

console.log("Cloudflare TURN credential route tests passed.");
