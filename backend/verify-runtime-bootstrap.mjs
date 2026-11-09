import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const bootstrap = await readFile(new URL("./serverBootstrap.js", import.meta.url), "utf8");
const server = await readFile(new URL("./server.js", import.meta.url), "utf8");
const normalizedServer = server.replace(/\s+/g, " ").toLowerCase();
assert.ok(!normalizedServer.includes("update users set verified = false;"),
  "backend startup must never reset existing users verification status");

// Keep the current login available for the three active test accounts. This is
// a source-level compatibility guard: migration work must not gate, replace,
// or weaken the existing bcrypt-backed login/token contract.
const loginRoute = server.match(/app\\.post\\(["']\\/api\\/auth\\/login["'], async \\(req, res\\) => \\{([\\s\\S]*?)\\n\\}\\);/);
assert.ok(loginRoute, "the existing /api/auth/login route must remain registered");
assert.match(loginRoute[1], /bcrypt\\.compare\\(password,\\s*user\\.password_hash\\)/,
  "login must continue verifying existing password hashes");
assert.match(loginRoute[1], /issueToken\\(user\\)/,
  "login must continue issuing the existing access token");
assert.doesNotMatch(loginRoute[1], /LOGIN_DISABLED|AUTH_DISABLED|MAINTENANCE_MODE/i,
  "migration work must not disable the existing login route");
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
console.log("Runtime bootstrap integrity checks passed.");

for (const contract of [
  "registerBusinessRoutes({ app, auth })",
  "startMarketplaceInspectionReconciliation({ pool })",
  "registerMarketplacePaystackWebhook({ app, pool })",
  "registerMarketplaceCheckoutRoutes({ app, pool, auth })",
  "registerMarketplaceCheckoutOrderRoutes({ app, pool, auth })",
  "registerMarketplaceShippingRoutes({ app, pool, auth })",
  "registerMarketplaceCompletionRoutes({ app, pool, auth })",
  "registerBuyerWalletRoutes({ app })",
  "registerBuyerWalletMarketplaceRoutes({ app })",
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
