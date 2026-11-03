import { createHash } from "node:crypto";
import { getFynxDatabasePool } from "./databasePool.js";
import { mediaStorage } from "./mediaStorage.js";

const MAX_MEDIA_BYTES = 12 * 1024 * 1024;
const LOCK_NAME = "fynx-media-r2-legacy-copy-v1";
const CONFIRMATION = "COPY_LEGACY_MEDIA_TO_R2_KEEP_POSTGRES_BACKUP";
const pool = getFynxDatabasePool();

function sha256(value) {
  return createHash("sha256").update(value).digest("hex");
}
function parseMaxItems() {
  const value = process.argv.find((item) => item.startsWith("--max-items="))?.split("=")[1];
  if (value == null) return Number.POSITIVE_INFINITY;
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1 || parsed > 100000) throw new Error("--max-items must be an integer between 1 and 100000.");
  return parsed;
}

async function run() {
  if (!pool) throw new Error("DATABASE_URL is not configured.");
  const summary = await pool.query(
    "SELECT COUNT(*) FILTER (WHERE storage_provider = 'postgres' AND data IS NOT NULL AND byte_size BETWEEN 1 AND $1) AS eligible_rows, " +
    "COALESCE(SUM(byte_size) FILTER (WHERE storage_provider = 'postgres' AND data IS NOT NULL AND byte_size BETWEEN 1 AND $1), 0) AS eligible_bytes, " +
    "COUNT(*) FILTER (WHERE storage_provider = 'postgres' AND data IS NOT NULL AND byte_size > $1) AS oversized_rows, " +
    "COUNT(*) FILTER (WHERE storage_provider = 'postgres' AND data IS NULL) AS missing_legacy_bytes FROM message_media",
    [MAX_MEDIA_BYTES]
  );
  const stats = summary.rows?.[0] || {};
  console.log("FYNX legacy media migration inventory", JSON.stringify({
    eligibleRows: Number(stats.eligible_rows || 0),
    eligibleBytes: Number(stats.eligible_bytes || 0),
    oversizedRows: Number(stats.oversized_rows || 0),
    missingLegacyBytes: Number(stats.missing_legacy_bytes || 0),
    mode: process.argv.includes("--apply") ? "apply" : "dry-run"
  }));
  if (!process.argv.includes("--apply")) {
    console.log("Dry-run only. No media objects or database rows were changed.");
    return;
  }
  if (process.env.FYNX_MEDIA_MIGRATION_CONFIRM !== CONFIRMATION) throw new Error("Set FYNX_MEDIA_MIGRATION_CONFIRM to the documented confirmation string before applying.");
  if (mediaStorage.provider !== "r2" || !mediaStorage.isConfigured) throw new Error("R2 must be configured and selected before applying the migration.");

  const maxItems = parseMaxItems();
  const client = await pool.connect();
  let migrated = 0;
  try {
    await client.query("SELECT pg_advisory_lock(hashtext($1))", [LOCK_NAME]);
    while (migrated < maxItems) {
      await client.query("BEGIN");
      let objectKey = null;
      let commitAttempted = false;
      try {
        const candidate = await client.query(
          "SELECT id, mime_type, data, byte_size FROM message_media " +
          "WHERE storage_provider = 'postgres' AND data IS NOT NULL AND byte_size BETWEEN 1 AND $1 " +
          "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
          [MAX_MEDIA_BYTES]
        );
        const row = candidate.rows?.[0];
        if (!row) {
          await client.query("COMMIT");
          break;
        }
        const data = Buffer.from(row.data);
        if (data.length !== Number(row.byte_size) || data.length < 1 || data.length > MAX_MEDIA_BYTES) {
          throw new Error("Legacy media byte-size validation failed for media ID " + row.id + ".");
        }
        const stored = await mediaStorage.putMediaObject({ data, mimeType: row.mime_type });
        if (stored.storageProvider !== "r2" || !stored.objectKey) throw new Error("R2 did not return a durable object key.");
        objectKey = stored.objectKey;
        const readBack = await mediaStorage.getMediaObjectBytes(objectKey);
        if (!readBack || sha256(readBack) !== sha256(data)) throw new Error("R2 checksum verification failed for media ID " + row.id + ".");
        const updated = await client.query(
          "UPDATE message_media SET storage_provider = 'r2', object_key = $2 " +
          "WHERE id = $1 AND storage_provider = 'postgres' AND data IS NOT NULL RETURNING id",
          [row.id, objectKey]
        );
        if (updated.rowCount !== 1) throw new Error("Media row changed during migration; refusing to continue.");
        commitAttempted = true;
        await client.query("COMMIT");
        migrated += 1;
        console.log("Migrated media row", String(row.id), "verifiedBytes", data.length);
      } catch (error) {
        let rolledBack = false;
        try { await client.query("ROLLBACK"); rolledBack = true; } catch {}
        if (objectKey && rolledBack && !commitAttempted) await mediaStorage.deleteMediaObject(objectKey).catch(() => {});
        throw error;
      }
    }
    console.log("FYNX media migration completed", JSON.stringify({ migrated, maxItems: Number.isFinite(maxItems) ? maxItems : "all" }));
    console.log("Original PostgreSQL BYTEA copies were retained as rollback backups.");
  } finally {
    await client.query("SELECT pg_advisory_unlock(hashtext($1))", [LOCK_NAME]).catch(() => {});
    client.release();
  }
}
try { await run(); }
catch (error) { console.error("FYNX media migration stopped safely:", error?.message || "unknown error"); process.exitCode = 1; }
finally { if (pool) await pool.end().catch(() => {}); }
