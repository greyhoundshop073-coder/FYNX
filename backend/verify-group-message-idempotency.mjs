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
  const routes = new Map();
  const register = method => (path, ...handlers) => routes.set(method + " " + path, handlers);
  const app = {
    locals: {
      fynxBroadcastGroupMessage: async (groupId, message) => {
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

  const first = await send();
  assert.equal(first.statusCode, 201, "first insert must report created");
  assert.equal(first.body?.message?.id, "message_ci_001");
  assert.equal(pool.messages.size, 1);
  assert.deepEqual(broadcasts, [{ groupId: "group_ci_001", id: "message_ci_001" }]);

  const retry = await send();
  assert.equal(retry.statusCode, 200, "same sender/group/message ID retry must be an idempotent success");
  assert.equal(retry.body?.message?.id, first.body.message.id);
  assert.equal(retry.body?.message?.text, first.body.message.text);
  assert.equal(pool.messages.size, 1, "retry must not create a second database row");
  assert.equal(broadcasts.length, 1, "retry must not emit a duplicate realtime event");

  const crossGroup = await send({ groupId: "group_ci_002" });
  assert.equal(crossGroup.statusCode, 409, "an ID already owned by another group must conflict");
  assert.equal(crossGroup.body?.code, "MESSAGE_ID_CONFLICT");
  assert.equal(pool.messages.size, 1);
  assert.equal(broadcasts.length, 1);

  const crossSender = await send({ actorId: "2" });
  assert.equal(crossSender.statusCode, 409, "an ID already owned by another sender must conflict");
  assert.equal(crossSender.body?.code, "MESSAGE_ID_CONFLICT");
  assert.equal(pool.messages.size, 1);
  assert.equal(broadcasts.length, 1);
  assert.equal(pool.insertAttempts, 4);

  console.log("PASS first group send inserts and broadcasts once");
  console.log("PASS duplicate request returns HTTP 200 and canonical message without another row/event");
  console.log("PASS same message ID cannot cross group or sender boundaries");
});
