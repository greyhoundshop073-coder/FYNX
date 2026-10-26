import { Buffer } from "node:buffer";

// Cache media bytes only after each route has re-checked the caller's access.
// This avoids repeatedly pulling the same BYTEA payload from Neon while keeping
// every request subject to current message/listing/status/privacy authorization.
const MAX_CACHE_BYTES = 32 * 1024 * 1024;
const MAX_CACHE_ITEM_BYTES = 12 * 1024 * 1024;
const CACHE_TTL_MS = 15 * 60 * 1000;
const entries = new Map();
const pendingLoads = new Map();
let cacheBytes = 0;

function removeEntry(id) {
  const entry = entries.get(id);
  if (!entry) return;
  cacheBytes -= entry.data.length;
  entries.delete(id);
}

function readEntry(id, now) {
  const entry = entries.get(id);
  if (!entry) return null;
  if (now - entry.loadedAt >= CACHE_TTL_MS) {
    removeEntry(id);
    return null;
  }
  entries.delete(id);
  entries.set(id, entry);
  return { mimeType: entry.mimeType, data: entry.data };
}

function cacheEntry(id, mimeType, data, now) {
  if (data.length < 1 || data.length > MAX_CACHE_ITEM_BYTES || data.length > MAX_CACHE_BYTES) return;
  removeEntry(id);
  while (cacheBytes + data.length > MAX_CACHE_BYTES && entries.size > 0) {
    removeEntry(entries.keys().next().value);
  }
  entries.set(id, { mimeType, data, loadedAt: now });
  cacheBytes += data.length;
}

export async function getCachedMediaPayload(pool, mediaId) {
  const id = String(mediaId ?? "").trim();
  if (!/^[1-9]\\d{0,19}$/.test(id)) return null;

  const cached = readEntry(id, Date.now());
  if (cached) return cached;

  const activeLoad = pendingLoads.get(id);
  if (activeLoad) return activeLoad;

  const load = (async () => {
    const result = await pool.query(
      "SELECT mime_type, data FROM message_media WHERE id = $1 LIMIT 1",
      [id]
    );
    const row = result.rows?.[0];
    if (!row || !row.mime_type || row.data == null) return null;
    const data = Buffer.isBuffer(row.data) ? Buffer.from(row.data) : Buffer.from(row.data);
    if (data.length < 1) return null;
    const payload = { mimeType: String(row.mime_type), data };
    cacheEntry(id, payload.mimeType, data, Date.now());
    return payload;
  })();

  pendingLoads.set(id, load);
  try {
    return await load;
  } finally {
    if (pendingLoads.get(id) === load) pendingLoads.delete(id);
  }
}

// Expose counters for deterministic tests and diagnostics without exposing media.
export function getMediaPayloadCacheStats() {
  return { entries: entries.size, bytes: cacheBytes, maxBytes: MAX_CACHE_BYTES, maxItemBytes: MAX_CACHE_ITEM_BYTES };
}
