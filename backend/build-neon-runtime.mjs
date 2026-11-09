import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

process.env.FYNX_BUILD_RUNTIME_ONLY = "1";
const backendDir = path.dirname(fileURLToPath(import.meta.url));
await import("./realtimeIsolationBootstrap.js");
await import("./serverBootstrap.js");

const runtime = await readFile(path.join(backendDir, ".fynx-runtime-server.js"), "utf8");
const social = await readFile(path.join(backendDir, ".fynx-runtime-socialRoutes.js"), "utf8");

for (const [pattern, label] of [
  [/export const app = express\(\)/, "exported canonical Express application"],
  [/export async function dispatchRealtimeConnection/, "realtime connection adapter"],
  [/app\.post\(["']\/api\/auth\/login["']/, "existing login route"],
  [/registerDiscoveryRoutes\(\{ app, pool, auth \}\)/, "Marketplace discovery routes"],
  [/registerBuyerWalletRoutes\(\{ app \}\)/, "Wallet routes"]
]) assert.match(runtime, pattern, `Neon runtime is missing ${label}`);

for (const route of ["/api/social/feed", "/api/social/posts/multi", "fynxHomeCommentsBatch4b"]) {
  assert.ok(social.includes(route), `Neon social runtime is missing ${route}`);
}

console.log("FYNX Neon Functions runtime artifacts generated and route contracts verified.");
