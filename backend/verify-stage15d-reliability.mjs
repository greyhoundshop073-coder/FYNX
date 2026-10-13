import fs from "node:fs";

const reliability = fs.readFileSync(new URL("./reliability.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");
const notificationBootstrap = fs.readFileSync(new URL("./notificationBootstrap.js", import.meta.url), "utf8");
const phase1Bootstrap = fs.readFileSync(new URL("./phase1FoundationBootstrap.js", import.meta.url), "utf8");
const checks = [
  ["rate-limit middleware is registered only after its configuration initializes", server.indexOf("const RATE_LIMITS =") < server.indexOf('app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));') && server.indexOf("function rateLimit(bucket, limit)") < server.indexOf('app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));')],
  ["health readiness rejects a missing or unavailable database", server.includes('res.status(database === "ready" ? 200 : 503).json({ ok: database === "ready", service: "fynx-backend", database });')],
  ["message schema SQL contains a real newline between ALTER statements", !server.includes("message_payload JSONB;\\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;") && server.includes("message_payload JSONB;\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;")],
  ["database schema initialization completes before the HTTP server listens", server.includes("await initDatabase();") && !server.includes('initDatabase().catch((error) => { console.error("database initialization failed", error); process.exitCode = 1; });') && server.indexOf("await initDatabase();") < server.indexOf('server.listen(PORT, "0.0.0.0"')],
  ["notification bootstrap avoids repeating already-applied replacements", notificationBootstrap.includes("if (source.includes(patch.replacement)) continue;") && notificationBootstrap.includes("source = source.replace(patch.marker, patch.replacement);")],
  ["phase-one bootstrap contains valid idempotency cleanup instead of escaped source text", phase1Bootstrap.includes("source = source.replaceAll(") && !phase1Bootstrap.includes("\\\\nsource = source.replaceAll")],
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
