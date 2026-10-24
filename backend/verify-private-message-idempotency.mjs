import assert from "node:assert/strict";
import { test } from "node:test";
import { registerPrivateMessageRoutes } from "./privateMessageRoutes.js";

const REQUEST_ID = "9f6d4f95-2c9d-4a0b-8f41-5b596b6f8a10";

class FakeMessagePool {
  messages = new Map();
  insertAttempts = 0;
  nextId = 7001;

  async query(sql, params = []) {
    const query = String(sql).replace(/\\s+/g, " ").trim().toLowerCase();

    if (query.startsWith("select 1 from blocks")) return { rows: [], rowCount: 0 };

    if (query.startsWith("insert into messages")) {
      this.insertAttempts += 1;
      const [senderId, recipientId, text, replyToId, mediaId, mediaType, voiceDurationMs, messageType, messagePayload, clientMessageId] = params;
      const key = String(senderId) + ":" + String(clientMessageId);
      if (this.messages.has(key)) return { rows: [] };
      const row = {
        id: String(this.nextId++),
        sender_id: String(senderId),
        recipient_id: String(recipientId),
        text,
        reply_to_id: replyToId == null ? null : String(replyToId),
        media_id: mediaId == null ? null : String(mediaId),
        media_type: mediaType,
        voice_duration_ms: Number(voiceDurationMs),
        message_type: messageType,
        message_payload: messagePayload,
        client_message_id: clientMessageId,
        timestamp: Date.parse("2026-10-24T12:00:00.000Z"),
        delivered: false,
        read: false,
        edited: false,
        deleted: false,
        reaction: null,
        pinned: false,
        sender_username: "test_owner",
        sender_display_name: "Test Owner",
        recipient_username: String(recipientId) === "2" ? "test_peer" : "other_peer",
        recipient_display_name: "Test Peer",
      };
      this.messages.set(key, row);
      return { rows: [{ id: row.id }] };
    }

    if (query.startsWith("select id from messages where sender_id=$1 and client_message_id=$2 limit 1")) {
      const row = this.messages.get(String(params[0]) + ":" + String(params[1]));
      return { rows: row ? [{ id: row.id }] : [] };
    }

    if (query.includes("where m.id=$1 limit 1")) {
      const row = [...this.messages.values()].find((item) => item.id === String(params[0]));
      return { rows: row ? [{ ...row }] : [] };
    }

    throw new Error("Unexpected SQL reached private-message route test: " + query.slice(0, 200));
  }
}

function makeResponse() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; },
  };
}

test("private message retry replays canonical realtime event without duplicate rows or ID collisions", async () => {
  const pool = new FakeMessagePool();
  const emitted = [];
  let broadcastAttempts = 0;
  const routes = new Map();
  const app = { post(path, ...handlers) { routes.set(path, handlers); } };

  const auth = (req, res, next) => {
    if (!req.user?.sub) return res.status(401).json({ error: "authentication required" });
    return next();
  };

  const recipientByUsername = {
    test_peer: { id: "2", username: "test_peer", display_name: "Test Peer" },
    other_peer: { id: "3", username: "other_peer", display_name: "Other Peer" },
  };

  async function broadcastMessage(message) {
    broadcastAttempts += 1;
    if (broadcastAttempts === 1) throw new Error("simulated transient realtime publish failure after database commit");
    emitted.push({ id: String(message.id), recipientId: String(message.recipientId) });
  }

  registerPrivateMessageRoutes({
    app,
    auth,
    pool,
    findUserByUsername: async (username) => recipientByUsername[username] || null,
    messageProjection: () => "SELECT private message projection FROM messages m",
    rowToMessage: (row) => ({
      id: String(row.id),
      senderId: String(row.sender_id),
      senderUsername: row.sender_username,
      recipientId: String(row.recipient_id),
      recipientUsername: row.recipient_username,
      text: row.text,
      timestamp: Number(row.timestamp),
      delivered: Boolean(row.delivered),
      read: Boolean(row.read),
      edited: Boolean(row.edited),
      deleted: Boolean(row.deleted),
      replyToId: row.reply_to_id == null ? null : String(row.reply_to_id),
      mediaId: row.media_id == null ? null : String(row.media_id),
      mediaType: row.media_type ?? null,
      voiceDurationMs: Number(row.voice_duration_ms ?? 0),
      messageType: row.message_type ?? "text",
      messagePayload: row.message_payload ?? null,
      reaction: row.reaction ?? null,
      pinned: Boolean(row.pinned),
    }),
    broadcastMessage,
  });

  const handlers = routes.get("/api/messages");
  assert.ok(handlers && handlers.length === 2, "test must exercise the actual registered private-message route");
  let authenticated = false;

  async function send(overrides = {}) {
    const req = {
      user: { sub: "1", username: "test_owner" },
      body: {
        clientMessageId: REQUEST_ID,
        recipientUsername: "test_peer",
        text: "stable retry marker",
        replyToId: null,
        mediaId: null,
        mediaType: null,
        voiceDurationMs: 0,
        ...overrides,
      },
      get(name) { return name.toLowerCase() === "authorization" ? "Bearer isolated-test-token" : undefined; },
    };
    const res = makeResponse();
    handlers[0](req, res, () => { authenticated = true; });
    assert.equal(authenticated, true, "request must pass the injected authenticated-route middleware");
    await handlers[1](req, res);
    return res;
  }

  const first = await send();
  assert.equal(first.statusCode, 500, "an initial failed live publish must remain a visible send failure");
  assert.equal(pool.messages.size, 1, "database commit must survive the simulated publish failure");
  assert.equal(broadcastAttempts, 1);
  assert.equal(emitted.length, 0);

  const retry = await send();
  assert.equal(retry.statusCode, 200, "retry with the same stable ID must recover as idempotent success");
  assert.equal(retry.body?.message?.id, "7001");
  assert.equal(pool.messages.size, 1, "retry must not create a second database row");
  assert.equal(broadcastAttempts, 2, "retry must attempt realtime delivery even though the row already exists");
  assert.deepEqual(emitted, [{ id: "7001", recipientId: "2" }]);

  const repeatedRetry = await send();
  assert.equal(repeatedRetry.statusCode, 200);
  assert.equal(pool.messages.size, 1, "repeated retry must continue to preserve one canonical row");
  assert.equal(broadcastAttempts, 3);
  assert.deepEqual(emitted, [
    { id: "7001", recipientId: "2" },
    { id: "7001", recipientId: "2" },
  ], "replayed events must carry the same canonical message ID");

  const crossRecipientCollision = await send({ recipientUsername: "other_peer" });
  assert.equal(crossRecipientCollision.statusCode, 409, "stable client ID cannot be reused for a different recipient");
  assert.equal(crossRecipientCollision.body?.code, "MESSAGE_ID_CONFLICT");
  const changedTextCollision = await send({ text: "different body under same stable ID" });
  assert.equal(changedTextCollision.statusCode, 409, "stable client ID cannot be reused for different content");
  assert.equal(changedTextCollision.body?.code, "MESSAGE_ID_CONFLICT");
  assert.equal(pool.messages.size, 1);
  assert.equal(broadcastAttempts, 3, "ID collisions must be rejected before extra realtime broadcasts");

  console.log("PASS initial commit + transient publish failure remains retryable");
  console.log("PASS stable-ID retry replays the canonical realtime message without duplicating the database row");
  console.log("PASS recipient/content collisions return HTTP 409 before another broadcast");
});
