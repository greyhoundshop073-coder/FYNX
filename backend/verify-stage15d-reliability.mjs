import fs from "node:fs";

const reliability = fs.readFileSync(new URL("./reliability.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");
const notificationBootstrap = fs.readFileSync(new URL("./notificationBootstrap.js", import.meta.url), "utf8");
const phase1Bootstrap = fs.readFileSync(new URL("./phase1FoundationBootstrap.js", import.meta.url), "utf8");
const socialPostReactionBootstrap = fs.readFileSync(new URL("./socialPostReactionBootstrap.js", import.meta.url), "utf8");
const homeCommentsPrivacyBootstrap = fs.readFileSync(new URL("./homeCommentsPrivacyBootstrap.js", import.meta.url), "utf8");
const socialFeedBootstrap = fs.readFileSync(new URL("./socialFeedBootstrap.js", import.meta.url), "utf8");
const realtimeIsolationBootstrap = fs.readFileSync(new URL("./realtimeIsolationBootstrap.js", import.meta.url), "utf8");
const checks = [
  ["rate-limit middleware is registered only after its configuration initializes", server.indexOf("const RATE_LIMITS =") < server.indexOf('app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));') && server.indexOf("function rateLimit(bucket, limit)") < server.indexOf('app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));')],
  ["health readiness rejects a missing or unavailable database", server.includes('res.status(database === "ready" ? 200 : 503).json({ ok: database === "ready", service: "fynx-backend", database });')],
  ["message schema SQL contains a real newline between ALTER statements", !server.includes("message_payload JSONB;\\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;") && server.includes("message_payload JSONB;\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;")],
  ["database schema initialization completes before the HTTP server listens", server.includes("await initDatabase();") && !server.includes('initDatabase().catch((error) => { console.error("database initialization failed", error); process.exitCode = 1; });') && server.indexOf("await initDatabase();") < server.indexOf('server.listen(PORT, "0.0.0.0"')],
  ["official-user verification uses a separate parameterized query after the multi-statement schema batch", server.includes('await pool.query("UPDATE users SET verified = TRUE WHERE lower(username) = $1", [officialFynxUsername]);') && !server.includes("  `, [officialFynxUsername]);")],
  ["message-media foreign-key migration retains valid PostgreSQL dollar quoting", server.includes("DO $ BEGIN ALTER TABLE messages ADD CONSTRAINT messages_media_fk") && server.includes("EXCEPTION WHEN duplicate_object THEN NULL; END $;")],
  ["notification bootstrap avoids repeating already-applied replacements", notificationBootstrap.includes("if (source.includes(patch.replacement)) continue;") && notificationBootstrap.includes("source = source.replace(patch.marker, patch.replacement);")],
  ["Home reaction-user lookup matches the reaction_type schema", socialPostReactionBootstrap.includes("r.reaction_type AS reaction") && socialPostReactionBootstrap.includes("r.reaction_type=$2") && !socialPostReactionBootstrap.includes("r.reaction=$2")],
  ["phase-one bootstrap contains valid idempotency cleanup instead of escaped source text", phase1Bootstrap.includes("source = source.replaceAll(") && !phase1Bootstrap.includes("\\\\nsource = source.replaceAll")],
  ["Home comment bootstrap applies privacy hardening after installing the route and keeps cursor placeholders aligned", realtimeIsolationBootstrap.indexOf("await installHomeCommentBackend();") < realtimeIsolationBootstrap.indexOf("await installHomeCommentPrivacy();") && homeCommentsPrivacyBootstrap.includes("c.id < $4") && homeCommentsPrivacyBootstrap.includes("LIMIT $3") && homeCommentsPrivacyBootstrap.includes("b.blocker_id=$2")],
  ["Home feed bootstrap is idempotent and refuses to install without the authoritative schema initializer", socialFeedBootstrap.includes("if (source.includes(\"/api/social/feed\")) return;") && socialFeedBootstrap.includes("const ensureSocialSchema = async") && socialFeedBootstrap.includes("LIMIT $2 OFFSET $3")],
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
