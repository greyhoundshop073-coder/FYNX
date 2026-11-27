import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (name) => readFileSync(new URL(name, import.meta.url), "utf8");
const scalability = read("./scalability.js");
const bootstrap = read("./serverBootstrap.js");
const neonBuild = read("./build-neon-runtime.mjs");
const neonFunction = read("./neon-function.js");

for (const [name, pattern] of [
  ["registerBuyerWalletRoutes", /registerBuyerWalletRoutes\(\{ app \}\)/g],
  ["registerBuyerWalletMarketplaceRoutes", /registerBuyerWalletMarketplaceRoutes\(\{ app \}\)/g]
]) {
  assert.equal((scalability.match(pattern) || []).length, 1, `shared scalability layer must register ${name} exactly once`);
  assert.equal((bootstrap.match(pattern) || []).length, 0, `server bootstrap must not duplicate ${name}`);
}

assert.match(
  bootstrap,
  /if \(process\.env\.FYNX_BUILD_RUNTIME_ONLY !== "1"\) \{[\s\S]*import\("\.\/scalability\.js"\)/,
  "normal Railway startup must load the shared scalability route layer"
);
assert.match(neonFunction, /await import\("\.\/scalability\.js"\)/, "Neon Functions must load the same shared scalability route layer");
assert.ok(neonBuild.includes(String.raw`[scalabilitySource, /registerBuyerWalletRoutes\(\{ app \}\)/`), "Neon build must verify shared Buyer Wallet route registration");
assert.ok(neonBuild.includes(String.raw`[scalabilitySource, /registerBuyerWalletMarketplaceRoutes\(\{ app \}\)/`), "Neon build must verify Marketplace wallet route registration");
assert.ok(neonBuild.includes(String.raw`[functionSource, /await import\("\.\/scalability\.js"\)/`), "Neon build must verify the shared route registration layer");

console.log("Wallet route registration is single-path in both Railway and Neon runtimes: scalability.js owns the two Wallet route modules.");
