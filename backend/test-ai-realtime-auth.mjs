import assert from "node:assert/strict";
import jwt from "jsonwebtoken";

process.env.JWT_SECRET = "fynx-ai-realtime-auth-test-secret";
const { registerRealtimeAssistantRoutes } = await import("./aiRealtimeRoutes.js");

const handlers = new Map();
const app = {
  post(path, ...callbacks) {
    handlers.set(path, callbacks.at(-1));
  }
};
registerRealtimeAssistantRoutes({ app });

function responseRecorder() {
  return {
    statusCode: 200,
    payload: null,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.payload = payload; return this; },
    type() { return this; },
    send(payload) { this.payload = payload; return this; }
  };
}

const voiceSession = handlers.get("/api/assistant/realtime-session");
const voiceTool = handlers.get("/api/assistant/realtime-tool");
assert.equal(typeof voiceSession, "function");
assert.equal(typeof voiceTool, "function");

for (const [label, handler] of [["voice session", voiceSession], ["voice tool", voiceTool]]) {
  const res = responseRecorder();
  const tokenWithoutSubject = jwt.sign({ role: "authenticated" }, process.env.JWT_SECRET);
  await handler({ headers: { authorization: `Bearer ${tokenWithoutSubject}` }, body: { sdp: "offer", name: "search_users", arguments: "{}" } }, res);
  assert.equal(res.statusCode, 401, `${label} must reject a validly signed token without a user subject`);
  assert.equal(res.payload?.error, "authenticated user is required");
}

for (const [label, handler] of [["voice session", voiceSession], ["voice tool", voiceTool]]) {
  const res = responseRecorder();
  await handler({ headers: {}, body: { sdp: "offer", name: "search_users", arguments: "{}" } }, res);
  assert.equal(res.statusCode, 401, `${label} must reject missing credentials`);
  assert.equal(res.payload?.error, "authentication required");
}

console.log("FYNX AI realtime authentication tests GREEN: missing and subject-less credentials rejected for both routes");
