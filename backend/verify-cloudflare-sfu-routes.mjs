import fs from "node:fs";
import assert from "node:assert/strict";

const routes = fs.readFileSync(new URL("./cloudflareSfuRoutes.js", import.meta.url), "utf8");
const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");

assert.match(server, /registerCloudflareSfuRoutes\(\{ app, pool, auth \}\)/, "Node backend must register the authenticated SFU routes");
assert.match(routes, /Authorization: `Bearer \$\{env\.CLOUDFLARE_SFU_APP_SECRET\}`/, "Cloudflare secret must be used only for server-side API requests");
assert.match(routes, /app\.post\("\/api\/calls\/sfu\/sessions", auth/, "SFU session creation must require FYNX authentication");
assert.match(routes, /status='ACTIVE' AND \(caller_id=\$2 OR callee_id=\$2\)/, "SFU session creation must be limited to active call participants");
assert.match(routes, /app\.post\("\/api\/calls\/sfu\/sessions\/:sessionId\/publish", auth/, "Publishing must require authentication");
assert.match(routes, /app\.post\("\/api\/calls\/sfu\/sessions\/:sessionId\/subscribe", auth/, "Subscribing must require authentication");
assert.match(routes, /p\.user_id<>\$3/, "Subscribers must not subscribe to their own publication");
assert.match(routes, /app\.put\("\/api\/calls\/sfu\/sessions\/:sessionId\/renegotiate", auth/, "Renegotiation must require authentication");
assert.match(routes, /sessionDescription\.type !== "offer"/, "Publishing must accept only SDP offers");
assert.match(routes, /sessionDescription\.type !== "answer"/, "Renegotiation must accept only SDP answers");
assert.match(routes, /one or more media tracks could not be published/, "Partial track failures must not be reported as successful");
assert.doesNotMatch(routes, /CLOUDFLARE_SFU_APP_SECRET\s*=\s*["'][^"']+["']/, "No Cloudflare secret may be hard-coded");

console.log("Cloudflare SFU backend contract verification passed.");
