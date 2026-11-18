import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

// A background timer that calls an async function without catching its errors crashes the whole server
// when that function ever rejects (for example during a brief database outage), because Node ends the
// process on an unhandled rejection. Every worker call below must therefore be wrapped.
const files = [
  "marketplacePaymentExpiry.js",
  "marketplaceInspectionExpiry.js",
  "marketplaceProtectionResolution.js",
  "marketplaceSettlementWorker.js",
  "backgroundJobs.js"
];
const bare = /(^|[\s{;(])void\s+[A-Za-z_$][\w$]*\s*\(/;
let guarded = 0;
for (const file of files) {
  const source = await readFile(new URL("./" + file, import.meta.url), "utf8");
  guarded += source.split("void Promise.resolve().then(").length - 1;
  source.split("\n").forEach((line, index) => {
    if (!bare.test(line)) return;
    if (/\)\s*\.catch\(/.test(line)) return;
    assert.fail(`${file}:${index + 1} starts background work without catching its errors: ${line.trim().slice(0, 120)}`);
  });
}
assert.ok(guarded >= 14, "expected all 14 worker call sites to be guarded, found " + guarded);
console.log("Background timer guard verification passed (" + guarded + " guarded call sites)");
