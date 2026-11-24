/** Shared direct-call privacy guard. A block in either direction prevents a new call. */
export async function isFynxCallBlocked(pool, callerId, targetUserId) {
  const caller = String(callerId ?? "").trim();
  const target = String(targetUserId ?? "").trim();
  if (!/^\d+$/.test(caller) || !/^\d+$/.test(target) || caller === target) {
    throw new Error("Invalid direct-call participants.");
  }
  if (!pool || typeof pool.query !== "function") {
    throw new Error("Direct-call safety storage is unavailable.");
  }
  const result = await pool.query(
    "SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1",
    [caller, target]
  );
  return Number(result.rowCount ?? result.rows?.length ?? 0) > 0;
}
