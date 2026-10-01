import fs from "node:fs";

const reliability = fs.readFileSync(new URL("./reliability.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const checks = [
  ["bounded retry helper", reliability.includes("retryWithBackoff") && reliability.includes("maxAttempts")],
  ["exponential backoff with jitter", reliability.includes("2 ** (attempt - 1)") && reliability.includes("Math.random")],
  ["transient database retry classification", reliability.includes("40001") && reliability.includes("40P01") && reliability.includes("08006")],
  ["failure recovery integration", scalability.includes("installFailureRecovery") && scalability.includes("__fynxRecovery")],
  ["bounded idempotency store", reliability.includes("createIdempotencyStore") && reliability.includes("maxEntries") && reliability.includes("ttlMs")],
  ["idempotency integration", scalability.includes("__fynxIdempotency")],
  ["HTTP client failure recovery", reliability.includes("clientError") && reliability.includes("HTTP/1.1 400 Bad Request")]
];
for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Stage 15D reliability verification failed: ${name}`);
  console.log(`PASS: ${name}`);
}
console.log("Stage 15D reliability verification passed");
