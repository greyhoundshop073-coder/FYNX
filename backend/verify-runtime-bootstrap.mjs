import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const bootstrap = await readFile(new URL("./serverBootstrap.js", import.meta.url), "utf8");
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
