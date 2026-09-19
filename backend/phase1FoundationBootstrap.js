import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const backendDir = path.dirname(fileURLToPath(import.meta.url));
const bootstrapPath = path.join(backendDir, "serverBootstrap.js");
let source = await readFile(bootstrapPath, "utf8");

const helperPattern = /const fynxRealtimeMessagingCanSend = async \(senderId, targetId\) => \{[\s\S]*?const fynxRealtimeMessagingTypingTarget/;
const replacement = `const fynxRealtimeMessagingCanSend = async (senderId, targetId) => {
  if (!pool || !fynxRealtimeMessagingValidUserId(targetId) || targetId === senderId) return false;
  if (await fynxRealtimeMessagingBlocks(senderId, targetId)) return false;
  const result = await pool.query("SELECT u.id FROM users u WHERE u.id=$1 AND (COALESCE((SELECT ps.messages_visibility FROM privacy_settings ps WHERE ps.user_id=u.id), 'My friends')='Everyone' OR (COALESCE((SELECT ps.messages_visibility FROM privacy_settings ps WHERE ps.user_id=u.id), 'My friends')='My friends' AND EXISTS (SELECT 1 FROM friendships f WHERE ((f.user_id=$1 AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=$1)) AND f.status='accepted')) LIMIT 1", [targetId, senderId]);
  return Boolean(result.rowCount);
};
const fynxRealtimeMessagingTypingTarget`;

if (!helperPattern.test(source)) {
  throw new Error("Phase 1 foundation: realtime messaging authorization helper not found");
}
source = source.replace(helperPattern, replacement);

await writeFile(bootstrapPath, source, "utf8");
console.log("FYNX Phase 1 foundation bootstrap: messaging authorization helper aligned with privacy schema");
