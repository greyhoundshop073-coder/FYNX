import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

// Production bootstrap compatibility guard. It preserves the existing Stage 14
// scalability preload while normalizing the current server startup/signature issues.
const backendDir = path.dirname(fileURLToPath(import.meta.url));
const sourcePath = path.join(backendDir, "server.js");
const socialSourcePath = path.join(backendDir, "socialRoutes.js");
const runtimePath = path.join(backendDir, ".fynx-runtime-server.js");
const runtimeSocialPath = path.join(backendDir, ".fynx-runtime-socialRoutes.js");

let source = await readFile(sourcePath, "utf8");
const rateLimitReplacements = [
  ['app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));', 'app.use("/api/auth", rateLimit("auth", 20));'],
  ['app.use("/api/assistant", rateLimit("assistant", RATE_LIMITS.assistant));', 'app.use("/api/assistant", rateLimit("assistant", 20));'],
  ['app.use("/api/media", rateLimit("media", RATE_LIMITS.media));', 'app.use("/api/media", rateLimit("media", 30));'],
  ['app.use("/api/messages", rateLimit("messages", RATE_LIMITS.messages));', 'app.use("/api/messages", rateLimit("messages", 120));']
];
for (const [from, to] of rateLimitReplacements) source = source.replace(from, to);
source = source.replace('from "./socialRoutes.js";', 'from "./.fynx-runtime-socialRoutes.js";\nimport { registerDiscoveryRoutes } from "./discoveryRoutes.js";\nimport { registerBusinessRoutes } from "./businessRoutes.js";');
source = source.replace('registerSocialRoutes(app, { pool, auth, findUserByUsername });', 'registerSocialRoutes(app, { pool, auth, findUserByUsername });\nif (pool) registerDiscoveryRoutes({ app, pool, auth });\nif (pool) registerBusinessRoutes({ app, auth });');

let social = await readFile(socialSourcePath, "utf8");
const signature = 'export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {';
const compatibleSignature = 'export function registerSocialRoutes(config, legacyConfig) {\n  const { app, pool, auth, findUserByUsername } = legacyConfig ? { app: config, ...legacyConfig } : config;';
if (!social.includes(signature)) throw new Error("FYNX bootstrap could not locate social route signature");
social = social.replace(signature, compatibleSignature);

// Group 3 discovery ranking: preserve the existing feed contract while replacing
// chronological-only ordering with engagement velocity + freshness. The query still
// enforces the existing visibility/block rules before ranking.
social = social.replace(
  'ORDER BY p.created_at DESC LIMIT $2',
  `ORDER BY (\n    COALESCE((SELECT COUNT(*) FROM social_post_likes l2 WHERE l2.post_id=p.id),0) * 3\n    + COALESCE((SELECT COUNT(*) FROM social_post_comments c2 WHERE c2.post_id=p.id),0) * 5\n    + GREATEST(0, 72 - EXTRACT(EPOCH FROM (NOW()-p.created_at))/3600.0)\n  ) DESC, p.created_at DESC LIMIT $2`
);

await writeFile(runtimeSocialPath, social, "utf8");
await writeFile(runtimePath, source, "utf8");

// Keep the original scalability preload active so realtime AI, privacy, groups,
// admin and production resource guards are installed on the live HTTP server.
await import("./scalability.js");
// scalability.js installs several compatibility routes from a setImmediate callback.
// Wait for that callback before importing the runtime server so the first real request
// cannot race route registration and receive a false HTTP 404.
await new Promise((resolve) => setImmediate(resolve));
await import(`${pathToFileURL(runtimePath).href}?boot=${Date.now()}`);
