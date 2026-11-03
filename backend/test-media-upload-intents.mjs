import assert from "node:assert/strict";
import { createMediaUploadIntent, completeMediaUploadIntent, MediaUploadIntentError } from "./mediaUploadIntents.js";
const UUID = "f35c2d80-4f45-4a33-b5b2-bb94928f9a11";
const future = new Date(Date.now() + 60_000);
const queries = [];
const pool = {
  async query(sql, values) { queries.push({ sql, values }); return { rows: [] }; },
  async connect() {
    const calls = [];
    const client = {
      calls,
      async query(sql, values) {
        calls.push({ sql, values });
        if (sql === "BEGIN" || sql === "COMMIT" || sql === "ROLLBACK") return { rows: [] };
        if (sql.includes("SELECT id, upload_key")) return { rows: [{ id: UUID, upload_key: "media-staging/" + UUID, object_key: "media/" + UUID, mime_type: "image/webp", expected_size: 15, expires_at: future, completed_at: null, media_id: null }] };
        if (sql.includes("INSERT INTO message_media")) return { rows: [{ id: 77, mime_type: "image/webp", byte_size: 15 }] };
        if (sql.includes("UPDATE media_upload_intents")) return { rowCount: 1, rows: [{ media_id: 77 }] };
        throw new Error("Unexpected SQL: " + sql);
      },
      release() { calls.push({ sql: "RELEASE" }); }
    };
    pool.lastClient = client;
    return client;
  }
};
const deletedKeys = [];
const storage = {
  provider: "r2", isConfigured: true,
  async createUploadUrl(input) {
    assert.deepEqual(input, { mimeType: "image/webp", byteSize: 15, expiresInSeconds: 900 });
    return { uploadKey: "media-staging/" + UUID, objectKey: "media/" + UUID, uploadUrl: "https://account.r2.cloudflarestorage.com/signed-put" };
  },
  async headMediaObject(key) {
    assert.equal(key, "media-staging/" + UUID);
    return { contentLength: 15, contentType: "image/webp" };
  },
  async promoteMediaObject(uploadKey, objectKey, mimeType) {
    assert.equal(uploadKey, "media-staging/" + UUID);
    assert.equal(objectKey, "media/" + UUID);
    assert.equal(mimeType, "image/webp");
    return { contentLength: 15, contentType: "image/webp" };
  },
  async deleteMediaObject(key) { deletedKeys.push(key); }
};
const intent = await createMediaUploadIntent({ pool, storage, ownerId: "5", mimeType: "image/webp", byteSize: 15, idFactory: () => UUID });
assert.equal(intent.mode, "direct");
assert.equal(intent.uploadId, UUID);
assert.equal(intent.expiresInSeconds, 900);
assert.equal(intent.headers["Content-Type"], "image/webp");
assert.equal(queries.length, 1);
assert.match(queries[0].sql, /upload_key, object_key/);
const legacy = await createMediaUploadIntent({ pool: null, storage: { provider: "postgres" }, ownerId: "5", mimeType: "image/jpeg", byteSize: 10 });
assert.deepEqual(legacy, { mode: "legacy" });
await assert.rejects(createMediaUploadIntent({ pool, storage, ownerId: "5", mimeType: "application/pdf", byteSize: 10 }), error => error instanceof MediaUploadIntentError && error.statusCode === 400);
await assert.rejects(createMediaUploadIntent({ pool, storage: { provider: "r2", isConfigured: false }, ownerId: "5", mimeType: "image/jpeg", byteSize: 10 }), error => error instanceof MediaUploadIntentError && error.statusCode === 503);
const completed = await completeMediaUploadIntent({ pool, storage, ownerId: "5", uploadId: UUID });
assert.deepEqual(completed, { media: { id: "77", mimeType: "image/webp", byteSize: 15 } });
assert.deepEqual(deletedKeys, ["media-staging/" + UUID]);
assert.ok(pool.lastClient.calls.some(c => c.sql === "BEGIN"));
assert.ok(pool.lastClient.calls.some(c => c.sql.includes("FOR UPDATE")));
assert.ok(pool.lastClient.calls.some(c => c.sql === "COMMIT"));
assert.ok(pool.lastClient.calls.some(c => c.sql === "RELEASE"));
assert.ok(pool.lastClient.calls.some(c => c.sql.includes("storage_provider, object_key")));
console.log("FYNX MEDIA UPLOAD INTENTS: GREEN");
console.log("Owner-bound upload, staging promotion, final verification, immutable media key and legacy fallback: PASS");
