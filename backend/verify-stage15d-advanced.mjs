import assert from "node:assert/strict";
import fs from "node:fs";
import {
  createIdempotencyStore,
  retryWithBackoff
} from "./reliability.js";
import { createBackgroundJobQueue } from "./backgroundJobs.js";

let attempts = 0;
const retryResult = await retryWithBackoff(async () => {
  attempts += 1;
  if (attempts < 3) throw new Error("transient");
  return "ok";
}, { maxAttempts: 3, baseDelayMs: 1, maxDelayMs: 10 });
assert.equal(retryResult, "ok");
assert.equal(attempts, 3);

const store = createIdempotencyStore({ maxEntries: 2, ttlMs: 60_000 });
assert.deepEqual(store.begin("k1", "fp1"), { duplicate: false });
assert.deepEqual(store.begin("k1", "fp1"), { duplicate: true, response: null });
assert.deepEqual(store.begin("k1", "different"), { conflict: true });
store.complete("k1", { ok: true });
assert.deepEqual(store.begin("k1", "fp1"), { duplicate: true, response: { ok: true } });
store.stop();

const queue = createBackgroundJobQueue({ connectionString: "", pollMs: 10, leaseMs: 1000 });
assert.equal(queue.enabled, false);
assert.equal(queue.snapshot().enabled, false);

const reliability = fs.readFileSync(new URL("./reliability.js", import.meta.url), "utf8");
const backgroundJobs = fs.readFileSync(new URL("./backgroundJobs.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");

const checks = [
  ["bounded retry helper", reliability.includes("retryWithBackoff") && reliability.includes("maxAttempts")],
  ["exponential backoff with jitter", reliability.includes("2 ** (attempt - 1)") && reliability.includes("Math.random")],
  ["transient database retry classification", reliability.includes("40001") && reliability.includes("40P01") && reliability.includes("08006")],
  ["bounded idempotency store", reliability.includes("createIdempotencyStore") && reliability.includes("maxEntries") && reliability.includes("ttlMs")],
  ["durable background job table", backgroundJobs.includes("CREATE TABLE IF NOT EXISTS fynx_background_jobs")],
  ["durable job idempotency", backgroundJobs.includes("CREATE UNIQUE INDEX IF NOT EXISTS fynx_background_jobs_idempotency_idx")],
  ["concurrent worker claiming", backgroundJobs.includes("FOR UPDATE SKIP LOCKED")],
  ["lease recovery", backgroundJobs.includes("lease_expires_at < NOW()") && backgroundJobs.includes("metrics.recovered")],
  ["retry and dead-letter handling", backgroundJobs.includes("status = 'dead'") && backgroundJobs.includes("metrics.deadLettered") && backgroundJobs.includes("metrics.retried")],
  ["durable worker runtime wiring", scalability.includes("createBackgroundJobQueue") && scalability.includes("__fynxBackgroundJobs") && scalability.includes("jobs.start")]
];

for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Stage 15D advanced reliability verification failed: ${name}`);
  console.log(`PASS: ${name}`);
}

console.log("Stage 15D advanced reliability verification passed");
