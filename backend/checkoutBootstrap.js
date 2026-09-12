import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

// Batch 3 runtime bridge. The existing serverBootstrap already prepares the
// production server from server.js, so this wrapper injects the checkout route
// module before that preparation and restores the source file afterwards.
// This keeps the existing server/bootstrap architecture intact while making
// the new checkout quote endpoint live on the real production process.
const backendDir = path.dirname(fileURLToPath(import.meta.url));
const sourcePath = path.join(backendDir, "server.js");
const original = await readFile(sourcePath, "utf8");

const importNeedle = 'import { registerMarketplaceCompletionRoutes } from "./marketplaceCompletion.js";';
const registrationNeedle = 'registerMarketplaceCompletionRoutes({ app, pool, auth });';
const checkoutImport = `${importNeedle}\nimport { registerMarketplaceCheckoutRoutes } from "./marketplaceCheckout.js";`;
const checkoutRegistration = `${registrationNeedle}\nif (pool) registerMarketplaceCheckoutRoutes({ app, pool, auth });`;

if (!original.includes(importNeedle) || !original.includes(registrationNeedle)) {
  throw new Error("FYNX checkout bootstrap could not locate the existing marketplace route markers");
}

const patched = original
  .replace(importNeedle, checkoutImport)
  .replace(registrationNeedle, checkoutRegistration);

try {
  await writeFile(sourcePath, patched, "utf8");
  await import("./realtimeIsolationBootstrap.js");
} finally {
  await writeFile(sourcePath, original, "utf8");
}
