import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

// The existing compatibility bootstraps generate the canonical production
// server from source patches. Run those patches during the build, never inside
// the read-only Workers runtime.
process.env.FYNX_BUILD_RUNTIME_ONLY = "1";
const backendDir = path.dirname(fileURLToPath(import.meta.url));
await import("./realtimeIsolationBootstrap.js");
await import("./serverBootstrap.js");

const runtimePath = path.join(backendDir, ".fynx-runtime-server.js");
const socialPath = path.join(backendDir, ".fynx-runtime-socialRoutes.js");
const runtime = await readFile(runtimePath, "utf8");
const social = await readFile(socialPath, "utf8");

for (const [pattern, label] of [
  [/export const server = http\.createServer\(app\)/, "exported canonical HTTP server"],
  [/app\.post\(["']\/api\/auth\/login["']/, "existing login route"],
  [/registerDiscoveryRoutes\(\{ app, pool, auth \}\)/, "Marketplace discovery routes"],
  [/registerBuyerWalletRoutes\(\{ app \}\)/, "Wallet routes"],
  [/app\.get\(["']\/api\/media\/:id["']/, "authenticated media route"]
]) assert.match(runtime, pattern, `Cloudflare runtime is missing ${label}`);

for (const route of ["/api/social/feed", "/api/social/posts/multi", "/api/social/posts/:id/media"]) {
  assert.ok(social.includes(route), `Cloudflare social runtime is missing ${route}`);
}

console.log("FYNX Cloudflare runtime artifacts generated and route contracts verified.");
