import { getFynxDatabasePool } from "./databasePool.js";
import { mediaStorage } from "./mediaStorage.js";

const CONFIRMATION = "DELETE_EXPIRED_UNCOMPLETED_MEDIA_UPLOADS";
const pool = getFynxDatabasePool();

async function run() {
  if (!pool) throw new Error("DATABASE_URL is not configured.");
  const inventory = await pool.query(
    "SELECT COUNT(*) FILTER (WHERE completed_at IS NULL AND expires_at < NOW()) AS expired_incomplete, " +
    "COUNT(*) FILTER (WHERE completed_at IS NOT NULL AND upload_key IS NOT NULL AND expires_at < NOW()) AS completed_staging " +
    "FROM media_upload_intents"
  );
  const stats = inventory.rows?.[0] || {};
  console.log("Expired uncompleted intents:", Number(stats.expired_incomplete || 0), "completed staging objects:", Number(stats.completed_staging || 0));
  if (!process.argv.includes("--apply")) {
    console.log("Dry-run only. No R2 objects or database rows were deleted.");
    return;
  }
  if (process.env.FYNX_MEDIA_INTENT_CLEANUP_CONFIRM !== CONFIRMATION) throw new Error("Set FYNX_MEDIA_INTENT_CLEANUP_CONFIRM to the documented confirmation string before applying.");
  if (mediaStorage.provider !== "r2" || !mediaStorage.isConfigured) throw new Error("R2 must be configured before expired object cleanup can run.");
  let cleaned = 0;
  while (true) {
    const rows = await pool.query(
      "SELECT id, upload_key, completed_at FROM media_upload_intents " +
      "WHERE upload_key IS NOT NULL AND expires_at < NOW() ORDER BY expires_at LIMIT 50"
    );
    if (!rows.rows?.length) break;
    for (const row of rows.rows) {
      await mediaStorage.deleteMediaObject(row.upload_key);
      if (row.completed_at) {
        await pool.query("UPDATE media_upload_intents SET upload_key = NULL WHERE id = $1 AND completed_at IS NOT NULL", [row.id]);
      } else {
        await pool.query("DELETE FROM media_upload_intents WHERE id = $1 AND completed_at IS NULL AND expires_at < NOW()", [row.id]);
      }
      cleaned += 1;
    }
  }
  console.log("Expired staging uploads cleaned:", cleaned);
}
try { await run(); }
catch (error) { console.error("FYNX expired media upload cleanup stopped safely:", error?.message || "unknown error"); process.exitCode = 1; }
finally { if (pool) await pool.end().catch(() => {}); }
