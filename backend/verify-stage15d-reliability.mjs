import fs from "node:fs";

const reliability = fs.readFileSync(new URL("./reliability.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");
const serverBootstrap = fs.readFileSync(new URL("./serverBootstrap.js", import.meta.url), "utf8");
const statusSchema = fs.readFileSync(new URL("./statusSchema.js", import.meta.url), "utf8");
const statusManagement = fs.readFileSync(new URL("./statusManagementRoutes.js", import.meta.url), "utf8");
const statusInteraction = fs.readFileSync(new URL("./statusInteractionRoutes.js", import.meta.url), "utf8");
const notificationBootstrap = fs.readFileSync(new URL("./notificationBootstrap.js", import.meta.url), "utf8");
const phase1Bootstrap = fs.readFileSync(new URL("./phase1FoundationBootstrap.js", import.meta.url), "utf8");
const socialPostReactionBootstrap = fs.readFileSync(new URL("./socialPostReactionBootstrap.js", import.meta.url), "utf8");
const homeCommentsPrivacyBootstrap = fs.readFileSync(new URL("./homeCommentsPrivacyBootstrap.js", import.meta.url), "utf8");
const socialFeedBootstrap = fs.readFileSync(new URL("./socialFeedBootstrap.js", import.meta.url), "utf8");
const realtimeIsolationBootstrap = fs.readFileSync(new URL("./realtimeIsolationBootstrap.js", import.meta.url), "utf8");
const checks = [
  ["rate-limit middleware is registered only after its configuration initializes", server.indexOf("const RATE_LIMITS =") < server.indexOf('app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));') && server.indexOf("function rateLimit(bucket, limit)") < server.indexOf('app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));')],
  ["database readiness rejects a missing or unavailable database", server.includes('app.get("/ready", async (_req, res) => {') && server.includes('if (!pool) return res.status(503).json({ ok: false, service: "fynx-backend", database: "not-configured", commit });') && server.includes('try { await pool.query("SELECT 1"); return res.status(200).json({ ok: true, service: "fynx-backend", database: "ready", commit }); }') && server.includes('catch { return res.status(503).json({ ok: false, service: "fynx-backend", database: "unavailable", commit }); }')],
  ["HTTP liveness health endpoint does not depend on database readiness and reports deployed commit", server.includes('app.get("/health", (_req, res) => {') && server.includes('return res.status(200).json({ ok: true, service: "fynx-backend", commit: process.env.RAILWAY_GIT_COMMIT_SHA || null });')],
  ["realtime message visibility uses the canonical friendships schema", serverBootstrap.includes("f.user_id=$1 AND f.friend_id=$2") && serverBootstrap.includes("f.user_id=$2 AND f.friend_id=$1") && serverBootstrap.includes("f.status='accepted'") && !serverBootstrap.includes("f.requester_id") && !serverBootstrap.includes("f.addressee_id")],
  ["message schema SQL contains a real newline between ALTER statements", !server.includes("message_payload JSONB;\\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;") && server.includes("message_payload JSONB;\n    ALTER TABLE messages ADD COLUMN IF NOT EXISTS reaction TEXT;")],
  ["database schema initialization completes before the HTTP server listens", server.includes("await initDatabase();") && !server.includes('initDatabase().catch((error) => { console.error("database initialization failed", error); process.exitCode = 1; });') && server.indexOf("await initDatabase();") < server.indexOf('server.listen(PORT, "0.0.0.0"')],
  ["official-user verification uses a separate parameterized query after the multi-statement schema batch", server.includes('await pool.query("UPDATE users SET verified = TRUE WHERE lower(username) = $1", [officialFynxUsername]);') && !server.includes("  `, [officialFynxUsername]);")],
  ["message-media foreign-key migration retains valid PostgreSQL dollar quoting", server.includes("DO $migration$ BEGIN ALTER TABLE messages ADD CONSTRAINT messages_media_fk") && server.includes("EXCEPTION WHEN duplicate_object THEN NULL; END $migration$;")],
  ["Status schema initialization is shared by list, archive, delete, and interactions", server.includes("ensureStatusSchema(pool)") && statusManagement.includes("await ensureStatusSchema(pool)") && statusInteraction.includes("await ensureStatusSchema(pool)") && statusSchema.includes("CREATE TABLE IF NOT EXISTS statuses")],
  ["notification bootstrap avoids repeating already-applied replacements", notificationBootstrap.includes("if (source.includes(patch.replacement)) continue;") && notificationBootstrap.includes("source = source.replace(patch.marker, patch.replacement);")],
  ["Home reaction-user lookup matches the reaction_type schema", socialPostReactionBootstrap.includes("r.reaction_type AS reaction") && socialPostReactionBootstrap.includes("r.reaction_type=$2") && !socialPostReactionBootstrap.includes("r.reaction=$2")],
  ["phase-one bootstrap contains valid idempotency cleanup instead of escaped source text", phase1Bootstrap.includes("source = source.replaceAll(") && !phase1Bootstrap.includes("\\\\nsource = source.replaceAll")],
  ["Home comment bootstrap applies privacy hardening after installing the route and keeps cursor placeholders aligned", realtimeIsolationBootstrap.indexOf("await installHomeCommentBackend();") < realtimeIsolationBootstrap.indexOf("await installHomeCommentPrivacy();") && homeCommentsPrivacyBootstrap.includes("c.id < $4") && homeCommentsPrivacyBootstrap.includes("LIMIT $3") && homeCommentsPrivacyBootstrap.includes("b.blocker_id=$2")],
  ["Home feed bootstrap is idempotent and refuses to install without the authoritative schema initializer", socialFeedBootstrap.includes('if (source.includes("/api/social/feed")) return;') && socialFeedBootstrap.includes("const ensureSocialSchema = async") && socialFeedBootstrap.includes("LIMIT $2 OFFSET $3")],
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
