import fs from "node:fs";

const jobs = fs.readFileSync(new URL("./backgroundJobs.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const checks = [
  ["durable jobs table", jobs.includes("fynx_background_jobs") && jobs.includes("CREATE TABLE IF NOT EXISTS")],
  ["safe concurrent claiming", jobs.includes("FOR UPDATE SKIP LOCKED")],
  ["bounded retry", jobs.includes("max_attempts") && jobs.includes("backoffMs")],
  ["lease based crash recovery", jobs.includes("lease_expires_at") && jobs.includes("reclaimExpired")],
  ["dead letter state", jobs.includes("status = 'dead'") && jobs.includes("deadLettered")],
  ["job idempotency", jobs.includes("idempotency_key") && jobs.includes("ON CONFLICT (idempotency_key)")],
  ["runtime worker wiring", scalability.includes("createBackgroundJobQueue") && scalability.includes("__fynxBackgroundJobs")],
  ["handler registry", scalability.includes("__fynxJobHandlers")]
];
for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Stage 15D jobs verification failed: ${name}`);
  console.log(`PASS: ${name}`);
}
console.log("Stage 15D background jobs verification passed");
