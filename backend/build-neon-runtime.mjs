import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

process.env.FYNX_BUILD_RUNTIME_ONLY = "1";
const backendDir = path.dirname(fileURLToPath(import.meta.url));
await import("./realtimeIsolationBootstrap.js");
await import("./serverBootstrap.js");

const runtime = await readFile(path.join(backendDir, ".fynx-runtime-server.js"), "utf8");
const social = await readFile(path.join(backendDir, ".fynx-runtime-socialRoutes.js"), "utf8");
const functionSource = await readFile(path.join(backendDir, "neon-function.js"), "utf8");
const bridgeSource = await readFile(path.join(backendDir, "neon-realtime-bridge.js"), "utf8");
const configSource = await readFile(path.join(backendDir, "neon.ts"), "utf8");
const jobsSource = await readFile(path.join(backendDir, "backgroundJobs.js"), "utf8");
const settlementSource = await readFile(path.join(backendDir, "marketplaceSettlementWorker.js"), "utf8");
const paymentExpirySource = await readFile(path.join(backendDir, "marketplacePaymentExpiry.js"), "utf8");
const batch2Source = await readFile(path.join(backendDir, "marketplaceBatch2FinalHardening.js"), "utf8");
const scalabilitySource = await readFile(path.join(backendDir, "scalability.js"), "utf8");


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


for (const [source, pattern, label] of [
  [runtime, /export function dispatchRealtimeEventToLocalSockets/, "local realtime event dispatcher"],
  [runtime, /export function getRealtimeLocalConnectionCount/, "local realtime connection counter"],
  [functionSource, /parseTriggerDelivery/, "authenticated Neon schedule trigger parser"],
  [functionSource, /installNeonRealtimeBridge/, "cross-isolate realtime bridge activation"],
  [functionSource, /upgradeWebSocket/, "Neon WebSocket upgrade adapter"],
  [bridgeSource, /fynx_realtime_events/, "PostgreSQL realtime event outbox"],
  [bridgeSource, /fynx_realtime_connections/, "cross-isolate connection presence"],
  [bridgeSource, /fynx_realtime_calls/, "shared call state"],
  [configSource, /cron: "\\* \\* \\* \\* \\*"/, "one-minute Marketplace maintenance trigger"],
  [jobsSource, /async function runOnce/, "one-shot background job processing"],
  [settlementSource, /startTimer = true/, "timer-free settlement scan"],
  [paymentExpirySource, /export async function expireUnpaidMarketplaceReservations/, "scheduled reservation expiry"],
  [batch2Source, /export async function runMarketplaceBatch2RecoveryOnce/, "scheduled payment recovery"],
  [scalabilitySource, /FYNX_NEON_FUNCTION/, "request-runtime background timer guard"]
]) assert.match(source, pattern, `Neon runtime is missing ${label}`);

const bridgeSyntax = spawnSync(process.execPath, ["--check", path.join(backendDir, "neon-realtime-bridge.js")], { encoding: "utf8" });
assert.equal(bridgeSyntax.status, 0, bridgeSyntax.stderr || "Neon realtime bridge syntax check failed");

console.log("FYNX Neon Functions runtime artifacts generated and route contracts verified.");
