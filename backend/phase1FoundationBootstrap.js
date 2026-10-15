import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const backendDir = path.dirname(fileURLToPath(import.meta.url));
const bootstrapPath = path.join(backendDir, "serverBootstrap.js");
let source = await readFile(bootstrapPath, "utf8");

const helperStart = "const fynxRealtimeMessagingCanSend = async (senderId, targetId) => {";
const helperEnd = "const fynxRealtimeMessagingTypingTarget";
const replacement = `const fynxRealtimeMessagingCanSend = async (senderId, targetId) => {
  if (!pool || !fynxRealtimeMessagingValidUserId(targetId) || targetId === senderId) return false;
  if (await fynxRealtimeMessagingBlocks(senderId, targetId)) return false;
  const result = await pool.query("SELECT messages_visibility FROM privacy_settings WHERE user_id=$1 LIMIT 1", [targetId]);
  const visibility = String(result.rows[0]?.messages_visibility || "My friends");
  if (visibility === "Nobody") return false;
  if (visibility === "Everyone") return true;
  const mutualFollow = await pool.query("SELECT 1 WHERE EXISTS (SELECT 1 FROM social_follows WHERE follower_id=$1 AND followed_id=$2) AND EXISTS (SELECT 1 FROM social_follows WHERE follower_id=$2 AND followed_id=$1) LIMIT 1", [senderId, targetId]);
  return Boolean(mutualFollow.rowCount);
};
`;

const helperStartIndex = source.indexOf(helperStart);
const helperEndIndex = helperStartIndex >= 0 ? source.indexOf(helperEnd, helperStartIndex) : -1;
if (helperStartIndex < 0 || helperEndIndex < 0) {
  throw new Error("Phase 1 foundation: realtime messaging authorization helper not found");
}
source = source.slice(0, helperStartIndex) + replacement + source.slice(helperEndIndex);
// Keep the bootstrap idempotent even if a previous generated runtime already contains the helper marker twice.
source = source.replaceAll(
  "const fynxRealtimeMessagingTypingTargetconst fynxRealtimeMessagingTypingTarget",
  "const fynxRealtimeMessagingTypingTarget"
);
await writeFile(bootstrapPath, source, "utf8");
console.log("FYNX Phase 1 foundation bootstrap: messaging authorization helper aligned with privacy schema");
