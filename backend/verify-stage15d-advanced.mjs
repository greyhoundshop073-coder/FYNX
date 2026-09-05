import assert from "node:assert/strict";
import {
  createIdempotentExecutor,
  createJobQueue,
  createIdempotencyStore,
  retryWithBackoff
} from "./reliability.js";

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
assert.deepEqual(store.begin("k1", "fp1"), { duplicate: true, response: null, pending: false });
assert.deepEqual(store.begin("k1", "different"), { conflict: true });
store.complete("k1", { ok: true });
assert.deepEqual(store.begin("k1", "fp1"), { duplicate: true, response: { ok: true }, pending: false });
store.stop();

const executor = createIdempotentExecutor({ maxEntries: 10, ttlMs: 60_000 });
let executions = 0;
const operation = () => new Promise(resolve => setTimeout(() => { executions += 1; resolve({ value: 42 }); }, 10));
const results = await Promise.all([
  executor.execute("same-key", "same-fingerprint", operation),
  executor.execute("same-key", "same-fingerprint", operation),
  executor.execute("same-key", "same-fingerprint", operation)
]);
assert.deepEqual(results, [{ value: 42 }, { value: 42 }, { value: 42 }]);
assert.equal(executions, 1);
await assert.rejects(
  executor.execute("same-key", "different-fingerprint", operation),
  error => error?.code === "IDEMPOTENCY_CONFLICT"
);
executor.stop();

const queue = createJobQueue({ concurrency: 1, maxAttempts: 2, maxQueue: 10, baseDelayMs: 1 });
let successfulJobAttempts = 0;
queue.enqueue("successful", { id: 1 }, async () => {
  successfulJobAttempts += 1;
});
let failingJobAttempts = 0;
queue.enqueue("failing", { id: 2 }, async () => {
  failingJobAttempts += 1;
  throw new Error("permanent test failure");
});

for (let i = 0; i < 50 && queue.snapshot().active > 0; i += 1) await new Promise(resolve => setTimeout(resolve, 5));
for (let i = 0; i < 50 && queue.snapshot().deadLetters < 1; i += 1) await new Promise(resolve => setTimeout(resolve, 5));

assert.equal(successfulJobAttempts, 1);
assert.equal(failingJobAttempts, 2);
assert.equal(queue.snapshot().deadLetters, 1);
assert.equal(queue.getDeadLetters()[0].name, "failing");
queue.stop();

console.log("Stage 15D advanced reliability verification passed");
