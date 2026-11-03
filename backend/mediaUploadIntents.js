import { randomUUID } from "node:crypto";

export const MEDIA_UPLOAD_INTENT_TTL_SECONDS = 900;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const MEDIA_MIME_PATTERN = /^(image\/(jpeg|png|webp|gif)|video\/(mp4|webm|quicktime)|audio\/(mp4|mpeg|aac|x-m4a|wav))$/;

export class MediaUploadIntentError extends Error {
  constructor(statusCode, publicMessage) {
    super(publicMessage);
    this.name = "MediaUploadIntentError";
    this.statusCode = statusCode;
    this.publicMessage = publicMessage;
  }
}

function validateUpload({ mimeType, byteSize, maxMediaBytes }) {
  const normalizedMimeType = String(mimeType || "").trim().toLowerCase();
  const size = Number(byteSize);
  if (!MEDIA_MIME_PATTERN.test(normalizedMimeType)) throw new MediaUploadIntentError(400, "Unsupported media type.");
  if (!Number.isInteger(size) || size < 1 || size > maxMediaBytes) throw new MediaUploadIntentError(413, "Media must be between 1 byte and 12 MB.");
  return { mimeType: normalizedMimeType, byteSize: size };
}

export async function createMediaUploadIntent({ pool, storage, ownerId, mimeType, byteSize, maxMediaBytes = 12 * 1024 * 1024, idFactory = randomUUID }) {
  const normalized = validateUpload({ mimeType, byteSize, maxMediaBytes });
  if (storage?.provider === "postgres") return { mode: "legacy" };
  if (storage?.provider !== "r2" || !storage.isConfigured) throw new MediaUploadIntentError(503, "Media object storage is not configured.");
  if (!pool || typeof pool.query !== "function") throw new MediaUploadIntentError(503, "Media database is temporarily unavailable.");
  const upload = await storage.createUploadUrl({ mimeType: normalized.mimeType, byteSize: normalized.byteSize, expiresInSeconds: MEDIA_UPLOAD_INTENT_TTL_SECONDS });
  if (!upload?.uploadKey || !upload?.objectKey || typeof upload.uploadUrl !== "string" || !upload.uploadUrl.startsWith("https://")) {
    throw new MediaUploadIntentError(503, "Media storage could not create a secure upload link.");
  }
  const uploadId = idFactory();
  await pool.query(
    "INSERT INTO media_upload_intents (id, owner_id, upload_key, object_key, mime_type, expected_size, expires_at) " +
    "VALUES ($1, $2, $3, $4, $5, $6, NOW() + ($7 * INTERVAL '1 second'))",
    [uploadId, ownerId, upload.uploadKey, upload.objectKey, normalized.mimeType, normalized.byteSize, MEDIA_UPLOAD_INTENT_TTL_SECONDS]
  );
  return { mode: "direct", uploadId, uploadUrl: upload.uploadUrl, headers: { "Content-Type": normalized.mimeType }, expiresInSeconds: MEDIA_UPLOAD_INTENT_TTL_SECONDS };
}

export async function completeMediaUploadIntent({ pool, storage, ownerId, uploadId }) {
  const normalizedId = String(uploadId || "").trim();
  if (!UUID_PATTERN.test(normalizedId)) throw new MediaUploadIntentError(400, "Invalid media upload ID.");
  if (!pool || typeof pool.connect !== "function") throw new MediaUploadIntentError(503, "Media database is temporarily unavailable.");
  if (storage?.provider !== "r2" || !storage.isConfigured) throw new MediaUploadIntentError(503, "Media object storage is not configured.");
  const client = await pool.connect();
  let transactionOpen = false;
  let promotedObjectKey = null;
  let commitAttempted = false;
  try {
    await client.query("BEGIN");
    transactionOpen = true;
    const result = await client.query(
      "SELECT id, upload_key, object_key, mime_type, expected_size, expires_at, completed_at, media_id FROM media_upload_intents WHERE id = $1 AND owner_id = $2 FOR UPDATE",
      [normalizedId, ownerId]
    );
    const intent = result.rows?.[0];
    if (!intent) throw new MediaUploadIntentError(404, "Media upload was not found.");
    if (intent.completed_at) {
      if (!intent.media_id) throw new MediaUploadIntentError(410, "This media upload has already expired or been removed.");
      await client.query("COMMIT");
      transactionOpen = false;
      return { media: { id: String(intent.media_id), mimeType: intent.mime_type, byteSize: Number(intent.expected_size) } };
    }
    if (!intent.expires_at || new Date(intent.expires_at).getTime() <= Date.now()) throw new MediaUploadIntentError(410, "Media upload link has expired. Please upload again.");
    let stagingHead;
    try { stagingHead = await storage.headMediaObject(intent.upload_key); }
    catch { throw new MediaUploadIntentError(422, "Uploaded media could not be verified. Please retry the upload."); }
    const expectedMimeType = String(intent.mime_type || "").trim().toLowerCase();
    const actualMimeType = String(stagingHead?.contentType || "").split(";")[0].trim().toLowerCase();
    if (Number(stagingHead?.contentLength) !== Number(intent.expected_size) || actualMimeType !== expectedMimeType) {
      throw new MediaUploadIntentError(422, "Uploaded media did not match its declared size or type.");
    }
    const promotedHead = await storage.promoteMediaObject(intent.upload_key, intent.object_key, expectedMimeType);
    if (Number(promotedHead?.contentLength) !== Number(intent.expected_size) || String(promotedHead?.contentType || "").toLowerCase() !== expectedMimeType) {
      throw new MediaUploadIntentError(422, "Promoted media did not pass final storage verification.");
    }
    promotedObjectKey = intent.object_key;
    const inserted = await client.query(
      "INSERT INTO message_media (owner_id, mime_type, data, byte_size, storage_provider, object_key) VALUES ($1, $2, NULL, $3, 'r2', $4) RETURNING id, mime_type, byte_size",
      [ownerId, expectedMimeType, Number(intent.expected_size), intent.object_key]
    );
    const media = inserted.rows?.[0];
    if (!media) throw new Error("Media record was not created.");
    const updated = await client.query(
      "UPDATE media_upload_intents SET completed_at = NOW(), media_id = $3 WHERE id = $1 AND owner_id = $2 AND completed_at IS NULL RETURNING media_id",
      [normalizedId, ownerId, media.id]
    );
    if (updated.rowCount !== 1 && !updated.rows?.length) throw new Error("Media upload intent could not be finalized.");
    commitAttempted = true;
    await client.query("COMMIT");
    transactionOpen = false;
    await storage.deleteMediaObject(intent.upload_key).catch((error) => console.error("staging media cleanup", error));
    return { media: { id: String(media.id), mimeType: media.mime_type, byteSize: Number(media.byte_size) } };
  } catch (error) {
    let rolledBack = false;
    if (transactionOpen) {
      try { await client.query("ROLLBACK"); rolledBack = true; } catch {}
    }
    if (promotedObjectKey && rolledBack && !commitAttempted) await storage.deleteMediaObject(promotedObjectKey).catch(() => {});
    throw error;
  } finally { client.release(); }
}

export function registerMediaUploadIntentRoutes(app, { auth, pool, storage, maxMediaBytes }) {
  app.post("/api/media/upload-url", auth, async (req, res) => {
    try {
      const intent = await createMediaUploadIntent({ pool, storage, ownerId: req.user.sub, mimeType: req.body?.mimeType, byteSize: req.body?.byteSize, maxMediaBytes });
      return res.status(intent.mode === "direct" ? 201 : 200).json(intent);
    } catch (error) {
      if (Number.isInteger(error?.statusCode) && error.statusCode >= 400 && error.statusCode <= 599) return res.status(error.statusCode).json({ error: error.publicMessage || "Media upload could not be started." });
      console.error("media upload intent", error);
      return res.status(500).json({ error: "Media upload could not be started." });
    }
  });
  app.post("/api/media/complete", auth, async (req, res) => {
    try {
      const completed = await completeMediaUploadIntent({ pool, storage, ownerId: req.user.sub, uploadId: req.body?.uploadId });
      return res.status(201).json(completed);
    } catch (error) {
      if (Number.isInteger(error?.statusCode) && error.statusCode >= 400 && error.statusCode <= 599) return res.status(error.statusCode).json({ error: error.publicMessage || "Media upload could not be completed." });
      console.error("media upload completion", error);
      return res.status(500).json({ error: "Media upload could not be completed." });
    }
  });
}
