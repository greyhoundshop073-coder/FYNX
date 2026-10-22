import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const bootstrap = await readFile(new URL("./serverBootstrap.js", import.meta.url), "utf8");
const packageJson = JSON.parse(await readFile(new URL("./package.json", import.meta.url), "utf8"));
assert.match(bootstrap, /generatedRuntime = await readFile\(runtimePath, "utf8"\)/,
  "bootstrap must read back the generated runtime before importing it");
for (const contract of [
  /deployment provenance/,
  /register Marketplace discovery/,
  /authenticated media retrieval/,
  /await database initialization/,
  /must not silently continue after database initialization failure/
]) assert.match(bootstrap, contract, `runtime guard missing contract: ${contract}`);
assert.match(packageJson.scripts.prestart, /verify-runtime-bootstrap\.mjs/,
  "runtime bootstrap verifier must run before service startup");
console.log("Runtime bootstrap integrity checks passed.");
