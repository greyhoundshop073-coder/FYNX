import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

// Batch 3/4 runtime bridge. The existing realtimeIsolationBootstrap remains the
// production entrypoint; it imports this bridge after installing its realtime
// isolation hooks. This bridge temporarily injects checkout and shipping routes
// into the existing server source, then lets serverBootstrap prepare the runtime server.
const backendDir = path.dirname(fileURLToPath(import.meta.url));
const sourcePath = path.join(backendDir, "server.js");
const original = await readFile(sourcePath, "utf8");

const importNeedle = 'import { registerMarketplaceCompletionRoutes } from "./marketplaceCompletion.js";';
const registrationNeedle = 'registerMarketplaceCompletionRoutes({ app, pool, auth });';
const checkoutImport = `${importNeedle}\nimport { registerMarketplaceCheckoutRoutes } from "./marketplaceCheckout.js";\nimport { registerMarketplaceCheckoutOrderRoutes } from "./marketplaceCheckoutOrder.js";\nimport { registerMarketplaceShippingRoutes } from "./marketplaceShipping.js";`;
const checkoutRegistration = `${registrationNeedle}\nif (pool) {\n  registerMarketplaceCheckoutRoutes({ app, pool, auth });\n  registerMarketplaceCheckoutOrderRoutes({ app, pool, auth });\n  registerMarketplaceShippingRoutes({ app, pool, auth });\n}`;

if (!original.includes(importNeedle) || !original.includes(registrationNeedle)) {
  throw new Error("FYNX checkout bootstrap could not locate the existing marketplace route markers");
}

const patched = original
  .replace(importNeedle, checkoutImport)
  .replace(registrationNeedle, checkoutRegistration);

try {
  await writeFile(sourcePath, patched, "utf8");
  await import("./serverBootstrap.js");
} finally {
  await writeFile(sourcePath, original, "utf8");
}
