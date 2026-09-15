import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

// Phase 1 foundation guard: repair the production bootstrap's shared messaging
// authorization contract before notification/realtime bootstraps generate their
// runtime server. This is intentionally idempotent and fails closed if the
// expected legacy contract is not present, so a future refactor cannot silently
// run with an unverified authorization path.
const backendDir = path.dirname(fileURLToPath(import.meta.url));
const bootstrapPath = path.join(backendDir, "serverBootstrap.js");
let source = await readFile(bootstrapPath, "utf8");

const brokenQuery = `SELECT u.id FROM users u WHERE u.id=$1 AND (COALESCE(u.messages_visibility, 'EVERYONE')='EVERYONE' OR (COALESCE(u.messages_visibility, 'EVERYONE')='MY_FRIENDS' AND EXISTS (SELECT 1 FROM friendships f WHERE ((f.requester_id=$1 AND f.addressee_id=$2) OR (f.requester_id=$2 AND f.addressee_id=$1)) AND f.status='ACCEPTED')) LIMIT 1`;
const fixedQuery = `SELECT u.id FROM users u WHERE u.id=$1 AND (COALESCE((SELECT ps.messages_visibility FROM privacy_settings ps WHERE ps.user_id=u.id), 'My friends')='Everyone' OR (COALESCE((SELECT ps.messages_visibility FROM privacy_settings ps WHERE ps.user_id=u.id), 'My friends')='My friends' AND EXISTS (SELECT 1 FROM friendships f WHERE ((f.user_id=$1 AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=$1)) AND f.status='accepted')) LIMIT 1`;

if (source.includes(brokenQuery)) {
  source = source.replace(brokenQuery, fixedQuery);
} else if (!source.includes(fixedQuery)) {
  throw new Error("Phase 1 foundation: messaging authorization contract marker not found");
}

// Keep the canonical bootstrap honest as well: the runtime server must never
// depend on a users.messages_visibility column that does not exist.
if (source.includes("COALESCE(u.messages_visibility")) {
  throw new Error("Phase 1 foundation: stale users.messages_visibility reference remains");
}
if (source.includes("f.requester_id") || source.includes("f.addressee_id")) {
  throw new Error("Phase 1 foundation: stale requester/addressee friendship columns remain");
}

await writeFile(bootstrapPath, source, "utf8");
console.log("FYNX Phase 1 foundation bootstrap: messaging authorization contract repaired");
