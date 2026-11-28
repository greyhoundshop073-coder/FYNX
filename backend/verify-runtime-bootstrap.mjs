import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";
import { assessFynxBackendReadiness } from "./deploymentReadiness.js";

const bootstrap = await readFile(new URL("./serverBootstrap.js", import.meta.url), "utf8");
const server = await readFile(new URL("./server.js", import.meta.url), "utf8");
const scalability = await readFile(new URL("./scalability.js", import.meta.url), "utf8");
const preload = await readFile(new URL("./renderScalabilityPreload.js", import.meta.url), "utf8");
const realtimeBootstrap = await readFile(new URL("./realtimeIsolationBootstrap.js", import.meta.url), "utf8");
const adminRoutes = await readFile(new URL("./adminRoutes.js", import.meta.url), "utf8");
const supportRoutes = await readFile(new URL("./supportRoutes.js", import.meta.url), "utf8");
const neonFunction = await readFile(new URL("./neon-function.js", import.meta.url), "utf8");
const readinessConfigured = assessFynxBackendReadiness({
  databaseStatus: "ready",
  jwtSecretConfigured: true,
  mediaStorageConfigured: true,
  mediaStorageProvider: "neon"
});
assert.deepEqual(readinessConfigured, {
  ready: true,
  checks: {
    database: "ready",
    authentication: "ready",
    mediaStorage: "ready",
    mediaStorageProvider: "neon"
  }
}, "all required backend dependencies must pass readiness");

for (const failedDependency of [
  { databaseStatus: "unavailable", jwtSecretConfigured: true, mediaStorageConfigured: true },
  { databaseStatus: "not-configured", jwtSecretConfigured: true, mediaStorageConfigured: true },
  { databaseStatus: "ready", jwtSecretConfigured: false, mediaStorageConfigured: true },
  { databaseStatus: "ready", jwtSecretConfigured: true, mediaStorageConfigured: false }
]) {
  assert.equal(assessFynxBackendReadiness(failedDependency).ready, false,
    "a missing or unavailable core dependency must fail readiness");
}
assert.equal(assessFynxBackendReadiness({
  databaseStatus: "ready",
  jwtSecretConfigured: true,
  mediaStorageConfigured: true,
  mediaStorageProvider: "unexpected"
}).checks.mediaStorageProvider, "unknown", "unknown provider labels must not be echoed into readiness");

const normalizedServer = server.replace(/\s+/g, " ").toLowerCase();
assert.ok(!normalizedServer.includes("update users set verified = false;"),
  "backend startup must never reset existing users verification status");

// Keep the current login available for the three active test accounts. This is
// a source-level compatibility guard: migration work must not gate, replace,
// or weaken the existing bcrypt-backed login/token contract.
const loginRoute = server.match(/app\.post\(["']\/api\/auth\/login["'], async \(req, res\) => \{([\s\S]*?)\n\}\);/);
assert.ok(loginRoute, "the existing /api/auth/login route must remain registered");
assert.match(loginRoute[1], /bcrypt\.compare\(password,\s*user\.password_hash\)/,
  "login must continue verifying existing password hashes");
assert.match(loginRoute[1], /issueToken\(user\)/,
  "login must continue issuing the existing access token");
assert.doesNotMatch(loginRoute[1], /LOGIN_DISABLED|AUTH_DISABLED|MAINTENANCE_MODE/i,
  "migration work must not disable the existing login route");

// Exactly one expiry worker may complete expired Marketplace inspections.
// The retained worker excludes open disputes and protection cases; the legacy
// bootstrap reconciler did not and could make disputed orders payout-eligible.
assert.doesNotMatch(bootstrap, /marketplaceInspectionReconciliation|startMarketplaceInspectionReconciliation/,
  "runtime bootstrap must not start the unsafe duplicate inspection reconciler");
assert.match(scalability, /registerMarketplaceInspectionExpiryWorker/,
  "the dispute-aware inspection expiry worker must remain registered");
const packageJson = JSON.parse(await readFile(new URL("./package.json", import.meta.url), "utf8"));
const buyerWallet = await readFile(new URL("./buyerWallet.js", import.meta.url), "utf8");
const buyerWalletMarketplace = await readFile(new URL("./buyerWalletMarketplace.js", import.meta.url), "utf8");
assert.match(bootstrap, /generatedRuntime = await readFile\(runtimePath, "utf8"\)/,
  "bootstrap must read back the generated runtime before importing it");
for (const contract of [
  /deployment provenance/,
  /register Marketplace discovery/,
  /authenticated media retrieval/,
  /await database initialization/,
  /must not silently continue after database initialization failure/,
  /safe auth failure diagnostics/,
  /missing JWT configuration/
]) assert.match(bootstrap, contract, `runtime guard missing contract: ${contract}`);
assert.match(packageJson.scripts.prestart, /verify-runtime-bootstrap\.mjs/,
  "runtime bootstrap verifier must run before service startup");

// Certify the actual Node production entrypoint without changing server.js.
// Admin and Support are installed by the preloaded scalability layer after
// serverBootstrap creates the canonical Express app; the request gate must
// hold dispatch until both route modules and guards finish registration.
assert.match(packageJson.scripts.start, /--import \.\/renderScalabilityPreload\.js realtimeIsolationBootstrap\.js/,
  "production start command must preload the production scalability layer");
assert.match(preload, /import ["']\.\/scalability\.js["']/,
  "production preload must install scalability before the application entrypoint");
assert.match(realtimeBootstrap, /await import\(["']\.\/serverBootstrap\.js["']\)/,
  "realtime bootstrap must import the generated canonical server outside runtime-only builds");
assert.match(adminRoutes, /export function registerAdminRoutes\(/,
  "Admin route module must expose its production registration function");
assert.match(supportRoutes, /export function registerSupportRoutes\(/,
  "Support route module must expose its production registration function");
for (const [pattern, label] of [
  [/import \{ registerAdminRoutes \} from ["']\.\/adminRoutes\.js["']/, "Admin route module import"],
  [/import \{ registerSupportRoutes \} from ["']\.\/supportRoutes\.js["']/, "Support route module import"],
  [/await routeRegistrationReady/, "request dispatch registration barrier"],
  [/registerAdminRoutes\(\{ app \}\)/, "Admin route registration"],
  [/registerSupportRoutes\(\{ app \}\)/, "Support route registration"],
  [/resolveRouteRegistration\(\)/, "successful registration barrier release"],
  [/rejectRouteRegistration\(error\)/, "failed registration barrier rejection"]
]) assert.match(scalability, pattern, `production startup is missing ${label}`);
const gateIndex = scalability.indexOf("await routeRegistrationReady");
const adminRegistrationIndex = scalability.indexOf("registerAdminRoutes({ app })");
const supportRegistrationIndex = scalability.indexOf("registerSupportRoutes({ app })");
const releaseIndex = scalability.indexOf("resolveRouteRegistration()");
assert.ok(gateIndex >= 0 && gateIndex < adminRegistrationIndex &&
  adminRegistrationIndex < supportRegistrationIndex && supportRegistrationIndex < releaseIndex,
  "request dispatch must remain gated until Admin and Support routes are registered");
assert.ok(neonFunction.indexOf('await import("./scalability.js")') <
  neonFunction.indexOf('await import("./.fynx-runtime-server.js")'),
  "Neon Function runtime must preload the same Admin/Support route-registration hook before importing the generated server");
console.log("Runtime bootstrap integrity checks passed (Admin and Support production registration chain included).");

for (const contract of [
  "registerBusinessRoutes({ app, auth })",
  "registerMarketplacePaystackWebhook({ app, pool })",
  "registerMarketplaceCheckoutRoutes({ app, pool, auth })",
  "registerMarketplaceCheckoutOrderRoutes({ app, pool, auth })",
  "registerMarketplaceShippingRoutes({ app, pool, auth })",
  "registerMarketplaceCompletionRoutes({ app, pool, auth })",
  'app.post("/api/auth/login"',
  'app.get("/api/me", auth',
  'app.get("/api/statuses", auth',
  "/api/social/feed",
  "/api/social/posts/multi",
  "/api/social/posts/:id/media"
]) assert.ok(bootstrap.includes(contract), `runtime bootstrap must protect route contract: ${contract}`);
assert.match(buyerWallet, /export function registerBuyerWalletRoutes\(\{ app \}\)/,
  "buyer Wallet API module must expose its authenticated route registration function");
assert.match(buyerWalletMarketplace, /export function registerBuyerWalletMarketplaceRoutes\(\{ app \}\)/,
  "buyer Wallet Marketplace module must expose its authenticated route registration function");

const readyRoute = server.match(/app\.get\(["']\/ready["'][\s\S]*?\n\}\);/);
assert.ok(readyRoute, "backend must expose a readiness route");
assert.match(readyRoute[0], /assessFynxBackendReadiness/,
  "readiness must use the shared dependency assessment");
assert.match(readyRoute[0], /Boolean\(JWT_SECRET\)/,
  "readiness must require authentication configuration");
assert.match(readyRoute[0], /Boolean\(mediaStorage\.isConfigured\)/,
  "readiness must require the selected media provider's configuration");
assert.match(readyRoute[0], /readiness\.ready \? 200 : 503/,
  "missing core dependencies must return HTTP 503");
assert.doesNotMatch(readyRoute[0], /process\.env\.(?:JWT_SECRET|DATABASE_URL|.*SECRET_ACCESS_KEY)/,
  "readiness must not expose or read raw secret values in its response route");
