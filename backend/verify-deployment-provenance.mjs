import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const source = await readFile(new URL("./server.js", import.meta.url), "utf8");
const route = source.match(/app\.get\(["']\/version["'][\s\S]*?\n\}\);/);
assert.ok(route, "backend must expose a /version deployment provenance endpoint");
for (const field of ["commit:", "branch:", "environment:", "deploymentId:"]) {
  assert.ok(route[0].includes(field), `/version must include ${field}`);
}
for (const variable of ["RAILWAY_GIT_COMMIT_SHA", "RAILWAY_GIT_BRANCH", "RAILWAY_ENVIRONMENT_NAME", "RAILWAY_DEPLOYMENT_ID"]) {
  assert.ok(route[0].includes(variable), `/version must read ${variable}`);
}
assert.ok(route[0].includes('res.setHeader("Cache-Control", "no-store")'), "/version must not be cached");
assert.ok(!/DATABASE_URL|JWT_SECRET|OPENAI_API_KEY|accessToken|password/i.test(route[0]), "/version must not expose secrets");
console.log("Backend deployment provenance guard passed.");
