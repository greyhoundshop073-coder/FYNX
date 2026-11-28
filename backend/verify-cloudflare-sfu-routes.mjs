import fs from "node:fs";
import assert from "node:assert/strict";

const routes = fs.readFileSync(new URL("./cloudflareSfuRoutes.js", import.meta.url), "utf8");
const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");

assert.match(server, /registerCloudflareSfuRoutes\(\{[\s\S]*resolveActiveCall:/, "SFU routes must use the runtime-aware active-call authorizer");
assert.match(server, /call\.status = "ACTIVE"/, "Railway direct calls must become SFU-eligible only after acceptance");
assert.match(server, /fynx_realtime_group_call_participants/, "Joined group-call members must be eligible for SFU session authorization");
assert.match(routes, /Authorization: \`Bearer \${env\.CLOUDFLARE_SFU_APP_SECRET}\`/, "Cloudflare secret must be used only for server-side API requests");
assert.match(routes, /app\.post\("\/api\/calls\/sfu\/sessions", auth/, "SFU session creation must require FYNX authentication");
assert.match(routes, /app\.post\("\/api\/calls\/sfu\/sessions\/:sessionId\/publish", auth/, "Publishing must require authentication");
assert.match(routes, /app\.post\("\/api\/calls\/sfu\/sessions\/:sessionId\/subscribe", auth/, "Subscribing must require authentication");
assert.match(routes, /p\.user_id<>\$3/, "Subscribers must not subscribe to their own publication");
assert.match(routes, /app\.put\("\/api\/calls\/sfu\/sessions\/:sessionId\/renegotiate", auth/, "Renegotiation must require authentication");
assert.match(routes, /mutation_state='IN_PROGRESS'/, "Session mutations must use an atomic cross-instance fence");
assert.match(routes, /AWAITING_RENEGOTIATION/, "An SFU offer must block later mutations until its answer is accepted");
assert.match(routes, /RECOVERY_REQUIRED/, "Uncertain SFU outcomes must not silently unlock the session");
assert.match(routes, /tracks\/close/, "Ending a call must close upstream Cloudflare media tracks");
assert.match(routes, /force: true/, "Call teardown must stop forwarding without requiring a new SDP exchange");
assert.match(routes, /requireActiveCall = true/, "Normal media mutations must verify active calls while teardown remains owner-authorized");
assert.match(routes, /sessionDescription\.type !== "offer"/, "Publishing must accept only SDP offers");
assert.match(routes, /sessionDescription\.type !== "answer"/, "Renegotiation must accept only SDP answers");
assert.match(routes, /one or more media tracks could not be published/, "Partial publication failures must not be reported as successful");
assert.doesNotMatch(routes, /CLOUDFLARE_SFU_APP_SECRET\s*=\s*["'][^"']+["']/, "No Cloudflare secret may be hard-coded");

console.log("Cloudflare SFU backend lifecycle contract verification passed.");
