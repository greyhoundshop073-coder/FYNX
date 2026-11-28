import assert from "node:assert/strict";
import jwt from "jsonwebtoken";
import { FynxAiUsageSessionActiveError } from "./fynxAiUsageBudget.js";

process.env.JWT_SECRET = process.env.JWT_SECRET || "fynx-ai-realtime-route-test-secret";
const { registerRealtimeAssistantRoutes } = await import("./aiRealtimeRoutes.js");

const handlers = new Map();
const app = {
  post(path, ...callbacks) {
    handlers.set(path, callbacks.at(-1));
  }
};
const calls = [];
const monitorEvents = [];
const reservation = {
  id: "reservation-route-1",
  userId: "84",
  provider: "openai",
  capability: "voice_assistant",
  model: "gpt-realtime-2.1"
};
const activeCall = {
  id: reservation.id,
  userId: "84",
  callId: "rtc_route12345",
  model: "gpt-realtime-2.1",
  capability: "voice_assistant"
};

registerRealtimeAssistantRoutes({
  app,
  databasePoolProvider: () => ({ name: "test-db" }),
  reserveUsage: async input => {
    calls.push(["reserve", input.userId, input.capability]);
    return reservation;
  },
  createCall: async (sdp, mode, voice) => {
    calls.push(["create", sdp, mode, voice]);
    return { answer: "v=0 test-answer", callId: "rtc_route12345" };
  },
  attachCall: async input => {
    calls.push(["attach", input.reservation.id, input.callId]);
    return true;
  },
  connectSideband: async input => {
    calls.push(["sideband", input.callId, input.includeTranscriptionUsage]);
    return {
      async finish(status) {
        monitorEvents.push(status);
        return true;
      }
    };
  },
  hangupCall: async callId => {
    calls.push(["hangup", callId]);
    return { ended: true, alreadyEnded: false };
  },
  findActiveCall: async userId => {
    calls.push(["find-active", userId]);
    return userId === "84" ? activeCall : null;
  },
  settleUsage: async input => {
    calls.push(["settle", input.reservation.id, input.status]);
  }
});

function responseRecorder() {
  return {
    statusCode: 200,
    payload: null,
    contentType: null,
    headers: {},
    status(code) { this.statusCode = code; return this; },
    setHeader(name, value) { this.headers[name] = value; },
    json(payload) { this.payload = payload; return this; },
    type(value) { this.contentType = value; return this; },
    send(payload) { this.payload = payload; return this; }
  };
}

const token = jwt.sign({ sub: "84" }, process.env.JWT_SECRET);
const headers = { authorization: "Bearer " + token };
const createRoute = handlers.get("/api/assistant/realtime-session");
const hangupRoute = handlers.get("/api/assistant/realtime-session/hangup");
assert.equal(typeof createRoute, "function");
assert.equal(typeof hangupRoute, "function");

const created = responseRecorder();
await createRoute({ headers, body: { sdp: "v=0 offer", mode: "assistant", voice: "cedar" } }, created);
assert.equal(created.statusCode, 200);
assert.equal(created.contentType, "application/sdp");
assert.equal(created.payload, "v=0 test-answer");
assert.ok(calls.some(call => call[0] === "reserve" && call[1] === "84" && call[2] === "voice_assistant"));
assert.ok(calls.some(call => call[0] === "attach" && call[2] === "rtc_route12345"));
assert.ok(calls.some(call => call[0] === "sideband" && call[1] === "rtc_route12345"));

const ended = responseRecorder();
await hangupRoute({ headers, body: {} }, ended);
assert.equal(ended.statusCode, 200);
assert.deepEqual(ended.payload, { ok: true, active: true, alreadyEnded: false });
assert.ok(calls.some(call => call[0] === "hangup" && call[1] === "rtc_route12345"));
assert.deepEqual(monitorEvents, ["completed"]);

const noActive = responseRecorder();
await hangupRoute({ headers: { authorization: "Bearer " + jwt.sign({ sub: "99" }, process.env.JWT_SECRET) }, body: {} }, noActive);
assert.deepEqual(noActive.payload, { ok: true, active: false });

const conflictApp = { post(path, ...callbacks) { this.handler = callbacks.at(-1); } };
registerRealtimeAssistantRoutes({
  app: conflictApp,
  databasePoolProvider: () => ({}),
  reserveUsage: async () => { throw new FynxAiUsageSessionActiveError(); }
});
const conflict = responseRecorder();
await conflictApp.handler({ headers, body: { sdp: "v=0 offer", mode: "assistant" } }, conflict);
assert.equal(conflict.statusCode, 409);
assert.match(conflict.payload.error, /already active/);

console.log("FYNX Realtime route lifecycle tests GREEN: reservation before provider setup, authenticated call binding, sideband start, ownership-scoped hangup and active-session protection");
