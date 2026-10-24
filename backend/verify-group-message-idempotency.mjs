import assert from "node:assert/strict";
import jwt from "jsonwebtoken";
import { test } from "node:test";

process.env.JWT_SECRET = "fynx-group-idempotency-test-secret";
delete process.env.DATABASE_URL;
const { registerGroupRoutes } = await import("./groupRoutes.js");

class FakeGroupPool {
  messages = new Map();
  insertAttempts = 0;

  async query(sql, params = []) {
    const query = String(sql).replace(/\s+/g, " ").trim().toLowerCase();
    if (query.startsWith("create table if not exists fynx_groups")) return { rows: [] };
    if (query.startsWith("select role from fynx_group_members")) return { rows: [{ role: "ADMIN" }] };
    if (query.startsWith("select send_messages,send_media from fynx_groups")) {
      return { rows: [{ send_messages: true, send_media: true }] };
    }
    if (query.startsWith("select message_safety,account_status from fynx_account_safety")) {
      return { rows: [{ message_safety: false, account_status: "ACTIVE" }] };
    }
    if (query.startsWith("insert into fynx_group_messages")) {
      this.insertAttempts += 1;
      assert.match(query, /on conflict\(id\) do nothing returning id/);
      const [id, groupId, senderId, text, attachmentMediaId, attachmentType, replyToId] = params;
      if (this.messages.has(String(id))) return { rows: [] };
      this.messages.set(String(id), {
        id: String(id), group_id: String(groupId), sender_id: String(senderId), text,
        attachment_media_id: attachmentMediaId, attachment_type: attachmentType, reply_to_id: replyToId,
        reaction: null, edited_at: null, deleted_at: null, pinned: false,
        created_at: new Date("2026-01-01T00:00:00.000Z")
      });
      return { rows: [{ id: String(id) }] };
    }
    if (query.startsWith("select m.id,m.group_id,m.sender_id,m.text")) {
      const stored = this.messages.get(String(params[0]));
      if (!stored) return { rows: [] };
      return { rows: [{
        ...stored,
        sender_username: stored.sender_id === "1" ? "test_owner" : "test_peer"
      }] };
    }
    throw new Error("Unexpected SQL reached the group-message route test: " + query.slice(0, 180));
  }
}

function fakeResponse() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; },
    set() { return this; }
  };
}

test("group message send is idempotent and refuses message-ID collisions", async () => {
  const pool = new FakeGroupPool();
  const broadcasts = [];
  let broadcastAttempts = 0;
  const routes = new Map();
  const register = method => (path, ...handlers) => routes.set(method + " " + path, handlers);
  const app = {
    locals: {
      fynxBroadcastGroupMessage: async (groupId, message) => {
        broadcastAttempts += 1;
        if (broadcastAttempts === 1) throw new Error("simulated transient realtime outage after DB commit");
        broadcasts.push({ groupId: String(groupId), id: String(message.id) });
      }
    },
    get: register("GET"),
    post: register("POST"),
    patch: register("PATCH"),
    delete: register("DELETE")
  };
  registerGroupRoutes({ app, pool, jwtSecret: process.env.JWT_SECRET });
  const handlers = routes.get("POST /api/groups/:groupId/messages");
  assert.ok(handlers && handlers.length === 2, "test must exercise the registered route and real JWT middleware");

  async function send({ actorId = "1", groupId = "group_ci_001", id = "message_ci_001", text = "stable retry marker" } = {}) {
    const token = jwt.sign({ sub: String(actorId), username: actorId === "1" ? "test_owner" : "test_peer" }, process.env.JWT_SECRET);
    const req = {
      params: { groupId },
      body: { id, text },
      get(name) { return name.toLowerCase() === "authorization" ? "Bearer " + token : undefined; }
    };
    const res = fakeResponse();
    let authorized = false;
    handlers[0](req, res, () => { authorized = true; });
    assert.equal(authorized, true, "request must pass actual route auth middleware");
    await handlers[1](req, res);
    return res;
  }

  // The first insert commits, then its live publish transiently fails. The route must return
  // an error so the sender can retry; it must not pretend the failed publish was delivered.
  const first = await send();
  assert.equal(first.statusCode, 500, "failed publish after the DB commit must stay visible to the caller");
  assert.equal(pool.messages.size, 1, "the first failed publish still owns exactly one persisted row");
  assert.equal(broadcastAttempts, 1);
  assert.equal(broadcasts.length, 0);

  // The stable-ID retry must return the canonical persisted row and try live delivery again.
  const retry = await send();
  assert.equal(retry.statusCode, 200, "same sender/group/message ID retry must recover as an idempotent success");
  assert.equal(retry.body?.message?.id, "message_ci_001");
  assert.equal(retry.body?.message?.text, "stable retry marker");
  assert.equal(pool.messages.size, 1, "retry must not create a second database row");
  assert.equal(broadcastAttempts, 2, "retry must attempt realtime delivery again");
  assert.deepEqual(broadcasts, [{ groupId: "group_ci_001", id: "message_ci_001" }]);

  // At-least-once replays still carry the same ID; clients merge by ID so only one message is shown.
  const duplicateRetry = await send();
  assert.equal(duplicateRetry.statusCode, 200);
  assert.equal(pool.messages.size, 1, "repeated retries must retain one canonical message row");
  assert.equal(broadcasts.length, 2);
  assert.deepEqual(broadcasts[0], broadcasts[1], "replayed realtime events must retain the canonical group/message ID");

  const changedPayload = await send({ text: "different body under same stable ID" });
  assert.equal(changedPayload.statusCode, 409, "a stable ID cannot be reused for different content in the same group");
  assert.equal(changedPayload.body?.code, "MESSAGE_ID_CONFLICT");
  assert.equal(pool.messages.size, 1);
  assert.equal(broadcasts.length, 2, "content collisions must not create another realtime event");

  const crossGroup = await send({ groupId: "group_ci_002" });
  assert.equal(crossGroup.statusCode, 409, "an ID already owned by another group must conflict");
  assert.equal(crossGroup.body?.code, "MESSAGE_ID_CONFLICT");
  assert.equal(pool.messages.size, 1);
  assert.equal(broadcasts.length, 2);

  const crossSender = await send({ actorId: "2" });
  assert.equal(crossSender.statusCode, 409, "an ID already owned by another sender must conflict");
  assert.equal(crossSender.body?.code, "MESSAGE_ID_CONFLICT");
  assert.equal(pool.messages.size, 1);
  assert.equal(broadcasts.length, 2);
  assert.equal(broadcastAttempts, 3, "ID collisions must be rejected before another realtime broadcast");
  assert.equal(pool.insertAttempts, 5);

  console.log("PASS committed message survives a transient broadcast error; stable-ID retry recovers delivery");
  console.log("PASS repeated POST retries retain one canonical row and replay the same realtime message ID");
  console.log("PASS same message ID cannot cross group or sender boundaries");
});
