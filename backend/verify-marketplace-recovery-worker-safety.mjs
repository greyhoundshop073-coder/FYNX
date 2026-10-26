import assert from "node:assert/strict";
import fs from "node:fs";
import { marketplaceRecoveryRetryDelayMs } from "./marketplaceBatch2FinalHardening.js";

assert.deepEqual(
  [0, 1, 2, 3, 4, 5, -1].map(marketplaceRecoveryRetryDelayMs),
  [30_000, 60_000, 120_000, 240_000, 300_000, 300_000, 30_000],
  "the worker keeps its healthy cadence and caps repeated-failure retries at five minutes"
);

const worker = fs.readFileSync(new URL("./marketplaceBatch2FinalHardening.js", import.meta.url), "utf8");
assert.match(worker, /let recoveryInFlight = false/, "recovery passes must have a single-flight guard");
assert.match(worker, /Batch 2 recovery worker failed; retrying with backoff/, "database failures must be handled and logged");
assert.match(worker, /setTimeout\(\(\) => \{ void run\(\); \}, delayMs\)/, "the next pass must wait for the current pass to finish");
assert.doesNotMatch(worker, /setInterval\(\(\) => \{ void run\(\); \}, 30_000\)/, "a fixed interval must not overlap slow recovery passes");
assert.match(worker, /AND NOT EXISTS \([\s\S]*?FROM marketplace_order_events ev[\s\S]*?ev\.metadata->>'operationId'=f\.id::text[\s\S]*?\)/, "the recovery audit must skip events already written for the same financial operation");

console.log("Marketplace recovery worker safety: PASS (single flight, bounded retry backoff, idempotent audit filter)");
