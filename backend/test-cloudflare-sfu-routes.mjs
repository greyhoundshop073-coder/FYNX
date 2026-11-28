import assert from "node:assert/strict";
import { registerCloudflareSfuRoutes } from "./cloudflareSfuRoutes.js";

const routes = new Map();
const app = {
  get(path, ...handlers) { routes.set(`GET ${path}`, handlers.at(-1)); },
  post(path, ...handlers) { routes.set(`POST ${path}`, handlers.at(-1)); },
  put(path, ...handlers) { routes.set(`PUT ${path}`, handlers.at(-1)); }
};
const sessions = new Map();
const publications = [];
let callActive = true;
let apiCalls = 0;
let partialSubscribe = false;
const pool = {
  async query(sql, params = []) {
    if (/^\s*(CREATE|ALTER|CREATE INDEX)/i.test(sql)) return { rows: [], rowCount: 0 };
    if (sql.startsWith("INSERT INTO fynx_call_sfu_sessions")) {
      sessions.set(params[0], {
        session_id: params[0], call_id: params[1], user_id: String(params[2]),
        mutation_state: "READY", ended_at: null
      });
      return { rows: [], rowCount: 1 };
    }
    if (sql.includes("SET mutation_state='IN_PROGRESS'")) {
      const row = sessions.get(params[0]);
      const allowed = params[3];
      if (!row || row.ended_at || row.call_id !== params[2] || row.user_id !== String(params[1]) || !allowed.includes(row.mutation_state)) {
        return { rows: [], rowCount: 0 };
      }
      row.mutation_state = "IN_PROGRESS";
      return { rows: [{ session_id: row.session_id }], rowCount: 1 };
    }
    if (sql.includes("SET mutation_state=$2")) {
      const row = sessions.get(params[0]);
      if (row?.mutation_state === "IN_PROGRESS") row.mutation_state = params[1];
      return { rows: [], rowCount: row ? 1 : 0 };
    }
    if (sql.trimStart().startsWith("SELECT session_id,call_id,user_id,mutation_state")) {
      const row = sessions.get(params[0]);
      const valid = row && !row.ended_at && row.user_id === String(params[1]) && row.call_id === params[2];
      return { rows: valid ? [{ ...row }] : [], rowCount: valid ? 1 : 0 };
    }
    if (sql.startsWith("INSERT INTO fynx_call_sfu_publications")) {
      const [callId, userId, sessionId, trackName, mid] = params;
      const existing = publications.find((item) => item.session_id === sessionId && item.track_name === trackName);
      if (existing) Object.assign(existing, { mid, active: true });
      else publications.push({ call_id: callId, user_id: String(userId), session_id: sessionId, track_name: trackName, mid, active: true });
      return { rows: [], rowCount: 1 };
    }
    if (sql.includes("JOIN fynx_call_sfu_publications p ON p.session_id=s.session_id")) {
      const [callId, subscriberId] = params;
      const grouped = new Map();
      for (const item of publications) {
        const session = sessions.get(item.session_id);
        if (item.call_id !== callId || item.user_id === String(subscriberId) || !item.active ||
            !session || session.ended_at || session.mutation_state !== "READY") continue;
        const entry = grouped.get(item.session_id) || { session_id: item.session_id, user_id: item.user_id, track_names: [] };
        entry.track_names.push(item.track_name);
        grouped.set(item.session_id, entry);
      }
      return { rows: [...grouped.values()], rowCount: grouped.size };
    }
    if (sql.trimStart().startsWith("SELECT p.session_id,p.track_name")) {
      const [callId, publisherSessionId, subscriberId, trackNames] = params;
      const rows = publications.filter((item) =>
        item.call_id === callId && item.session_id === publisherSessionId &&
        item.user_id !== String(subscriberId) && item.active && trackNames.includes(item.track_name)
      ).map(({ session_id, track_name }) => ({ session_id, track_name }));
      return { rows, rowCount: rows.length };
    }
    if (sql.startsWith("UPDATE fynx_call_sfu_publications SET active=FALSE")) {
      publications.filter((item) => item.session_id === params[0]).forEach((item) => { item.active = false; });
      return { rows: [], rowCount: 1 };
    }
    if (sql.startsWith("UPDATE fynx_call_sfu_sessions SET ended_at=NOW()")) {
      const row = sessions.get(params[0]);
      if (row && row.user_id === String(params[1])) {
        row.ended_at = true;
        row.mutation_state = "ENDED";
      }
      return { rows: [], rowCount: row ? 1 : 0 };
    }
    throw new Error(`Unexpected SQL in SFU route test: ${sql.slice(0, 120)}`);
  }
};

const fetchImpl = async (url, options = {}) => {
  apiCalls += 1;
  const path = new URL(url).pathname;
  let payload;
  if (path.endsWith("/sessions/new")) {
    payload = { sessionId: "session-a" };
  } else if (path.endsWith("/tracks/new") && options.method === "POST" && JSON.parse(options.body || "{}").tracks?.[0]?.location === "local") {
    payload = {
      sessionDescription: { type: "answer", sdp: "sfu-publish-answer" },
      tracks: [{ trackName: "microphone" }]
    };
  } else if (path.endsWith("/tracks/new") && options.method === "POST") {
    payload = {
      requiresImmediateRenegotiation: true,
      sessionDescription: { type: "offer", sdp: "sfu-subscribe-offer" },
      tracks: partialSubscribe
        ? [{ mid: "1" }, { errorCode: "track_unavailable", errorDescription: "test partial allocation" }]
        : [{ mid: "1" }]
    };
  } else if (path.endsWith("/renegotiate")) {
    payload = { success: true };
  } else if (options.method === "GET") {
    payload = { tracks: [{ mid: "0", location: "local", trackName: "microphone", status: "active" }] };
  } else if (path.endsWith("/tracks/close") && options.method === "PUT") {
    payload = { tracks: [{ mid: "0", status: "inactive" }] };
  } else {
    throw new Error(`Unexpected Cloudflare API request: ${options.method} ${path}`);
  }
  return { ok: true, status: 200, async json() { return payload; } };
};

const resolveActiveCall = async (callId, userId) =>
  callActive && callId === "call_liveproof_1" && ["42", "77"].includes(String(userId))
    ? { id: callId, kind: "direct" }
    : null;

registerCloudflareSfuRoutes({
  app, pool, auth: (_req, _res, next) => next(), rateLimit: (_req, _res, next) => next(), fetchImpl,
  env: { CLOUDFLARE_SFU_APP_ID: "test-app", CLOUDFLARE_SFU_APP_SECRET: "test-secret" },
  resolveActiveCall
});

function response() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; }
  };
}
async function invoke(method, path, { userId = "42", params = {}, body = {}, query = {} } = {}) {
  const handler = routes.get(`${method} ${path}`);
  assert.ok(handler, `route is registered: ${method} ${path}`);
  const res = response();
  await handler({ user: { sub: userId }, params, body, query }, res);
  return res;
}

const create = await invoke("POST", "/api/calls/sfu/sessions", { body: { callId: "call_liveproof_1" } });
assert.equal(create.statusCode, 201);
assert.equal(create.body.sessionId, "session-a");
assert.equal(create.body.mutationState, "READY");

const publishPath = "/api/calls/sfu/sessions/:sessionId/publish";
const publishBody = {
  callId: "call_liveproof_1",
  sessionDescription: { type: "offer", sdp: "publisher-offer" },
  tracks: [{ mid: "0", trackName: "microphone" }]
};
const publish = await invoke("POST", publishPath, { params: { sessionId: "session-a" }, body: publishBody });
assert.equal(publish.statusCode, 200);
assert.equal(publish.body.mutationState, "READY");
assert.equal(sessions.get("session-a").mutation_state, "READY");

// Add a second user's publication and prove subscriber authorization is call-scoped.
sessions.set("session-b", {
  session_id: "session-b", call_id: "call_liveproof_1", user_id: "77",
  mutation_state: "READY", ended_at: null
});
publications.push(
  { call_id: "call_liveproof_1", user_id: "77", session_id: "session-b", track_name: "camera", mid: "0", active: true },
  { call_id: "call_liveproof_1", user_id: "77", session_id: "session-b", track_name: "microphone", mid: "1", active: true }
);
const discovery = await invoke("GET", "/api/calls/sfu/sessions", {
  query: { callId: "call_liveproof_1" }
});
assert.equal(discovery.statusCode, 200);
assert.equal(discovery.body.publishers.length, 1);
assert.equal(discovery.body.publishers[0].sessionId, "session-b");
assert.equal(discovery.body.publishers[0].userId, "77");
assert.deepEqual(new Set(discovery.body.publishers[0].trackNames), new Set(["camera", "microphone"]));
const deniedDiscovery = await invoke("GET", "/api/calls/sfu/sessions", {
  userId: "99", query: { callId: "call_liveproof_1" }
});
assert.equal(deniedDiscovery.statusCode, 403, "publication discovery must require an active call participant");

const subscribe = await invoke("POST", "/api/calls/sfu/sessions/:sessionId/subscribe", {
  params: { sessionId: "session-a" },
  body: { callId: "call_liveproof_1", publisherSessionId: "session-b", trackNames: ["camera"] }
});
assert.equal(subscribe.statusCode, 200);
assert.equal(subscribe.body.mutationState, "AWAITING_RENEGOTIATION");

const callsBeforeFence = apiCalls;
const fenced = await invoke("POST", "/api/calls/sfu/sessions/:sessionId/subscribe", {
  params: { sessionId: "session-a" },
  body: { callId: "call_liveproof_1", publisherSessionId: "session-b", trackNames: ["camera"] }
});
assert.equal(fenced.statusCode, 409);
assert.equal(apiCalls, callsBeforeFence, "another subscription must not reach Cloudflare before the SFU offer is answered");

const closeWhileNegotiating = await invoke("POST", "/api/calls/sfu/sessions/:sessionId/close", {
  params: { sessionId: "session-a" }, body: { callId: "call_liveproof_1" }
});
assert.equal(closeWhileNegotiating.statusCode, 409, "cleanup must not mutate a session with an unanswered SFU offer");

const answerSubscribe = await invoke("PUT", "/api/calls/sfu/sessions/:sessionId/renegotiate", {
  params: { sessionId: "session-a" },
  body: { callId: "call_liveproof_1", sessionDescription: { type: "answer", sdp: "subscriber-answer" } }
});
assert.equal(answerSubscribe.statusCode, 200);
assert.equal(sessions.get("session-a").mutation_state, "READY");

const duplicateTracks = await invoke("POST", "/api/calls/sfu/sessions/:sessionId/subscribe", {
  params: { sessionId: "session-a" },
  body: { callId: "call_liveproof_1", publisherSessionId: "session-b", trackNames: ["camera", "camera"] }
});
assert.equal(duplicateTracks.statusCode, 400, "duplicate subscription names must be rejected before reaching Cloudflare");

partialSubscribe = true;
const partial = await invoke("POST", "/api/calls/sfu/sessions/:sessionId/subscribe", {
  params: { sessionId: "session-a" },
  body: { callId: "call_liveproof_1", publisherSessionId: "session-b", trackNames: ["camera", "microphone"] }
});
assert.equal(partial.statusCode, 502, "partial track allocation must not be reported as a successful subscription");
assert.equal(sessions.get("session-a").mutation_state, "RECOVERY_REQUIRED");
partialSubscribe = false;

// After the call ends, original session ownership must still permit upstream teardown.
callActive = false;
const close = await invoke("POST", "/api/calls/sfu/sessions/:sessionId/close", {
  params: { sessionId: "session-a" }, body: { callId: "call_liveproof_1" }
});
assert.equal(close.statusCode, 200);
assert.equal(close.body.sessionEnded, true);
assert.equal(close.body.tracksClosed, 1);
assert.equal(sessions.get("session-a").mutation_state, "ENDED");
assert.equal(publications.filter((item) => item.session_id === "session-a" && item.active).length, 0);

const unauthorized = await invoke("POST", "/api/calls/sfu/sessions", {
  userId: "99", body: { callId: "call_liveproof_1" }
});
assert.equal(unauthorized.statusCode, 403);
assert.ok(!JSON.stringify([create.body, publish.body, answerSubscribe.body, close.body]).includes("test-secret"));

console.log("Cloudflare SFU lifecycle tests passed (call authorization, mutation fencing, renegotiation, subscription, end-of-call cleanup).");
