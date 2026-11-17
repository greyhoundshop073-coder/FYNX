import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

// Guards found by running the real server: a blocked pair could ring each other, a client-chosen call id
// could replace somebody else's live call, and a non-numeric friend request id returned a server error.
const server = await readFile(new URL("./server.js", import.meta.url), "utf8");
const invite = server.slice(server.indexOf('if (signalType === "invite")'));
assert.match(invite, /FROM blocks WHERE \(blocker_id = \$1 AND blocked_id = \$2\) OR \(blocker_id = \$2 AND blocked_id = \$1\)/, "call invites must check blocks in both directions");
assert.ok(invite.indexOf("FROM blocks") < invite.indexOf("activeCalls.set(callId, call)"), "the block check must run before the call is created");
assert.match(invite, /if \(activeCalls\.has\(callId\)\) return sendSocket/, "a live call id must not be reusable");

const social = await readFile(new URL("./socialRoutes.js", import.meta.url), "utf8");
for (const route of ['post("/api/friends/requests/:id/accept"', 'post("/api/friends/requests/:id/reject"', 'delete("/api/friends/requests/:id"']) {
  assert.ok(social.includes(`app.${route}, auth, numericRequestId`), `${route} must validate the request id`);
}
assert.match(social, /INSERT INTO social_follows \(follower_id, followed_id\) VALUES \(\$1, \$2\), \(\$2, \$1\)/, "accepting a friend request must make the two people follow each other so they can message");
console.log("Call and friend request guard verification passed");
