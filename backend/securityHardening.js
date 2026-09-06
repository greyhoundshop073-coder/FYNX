const SAFE_METHODS = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]);
const SENSITIVE_PATHS = /\/(auth|payments?|settlement|refund|dispute|advertis|admin|account|password|token)/i;
const SUSPICIOUS_INPUT = /(?:\.\.(?:\/|\\)|<script|javascript:|\bunion\s+select\b|\bor\s+1\s*=\s*1|\bdrop\s+table\b)/i;
const AUTH_FAILURE_WINDOW_MS = 60_000;
const AUTH_FAILURE_ALERT_THRESHOLD = 5;
const AUTHZ_FAILURE_ALERT_THRESHOLD = 5;
const MAX_AUTH_FAILURE_BUCKETS = 10_000;
const authFailureBuckets = new Map();
const authzFailureBuckets = new Map();

function safeIp(req) { const value = req.ip || req.socket?.remoteAddress || "unknown"; return String(value).slice(0, 128); }
function audit(event, req, extra = {}) { console.warn(`[fynx-security] ${JSON.stringify({ event, method: req.method, path: String(req.path || req.originalUrl || "").slice(0, 300), ip: safeIp(req), userId: req.user?.sub ? String(req.user.sub).slice(0, 80) : null, at: new Date().toISOString(), ...extra })}`); }
function recordFailure(bucketMap, threshold, event, req) { const key = safeIp(req); const now = Date.now(); const current = bucketMap.get(key); if (!current || now - current.startedAt >= AUTH_FAILURE_WINDOW_MS) { if (!current && bucketMap.size >= MAX_AUTH_FAILURE_BUCKETS) return; bucketMap.set(key, { startedAt: now, count: 1, alerted: false }); return; } current.count += 1; if (current.count >= threshold && !current.alerted) { current.alerted = true; audit(event, req, { count: current.count, windowMs: AUTH_FAILURE_WINDOW_MS }); } }
function recordAuthFailure(req) { recordFailure(authFailureBuckets, AUTH_FAILURE_ALERT_THRESHOLD, "repeated_auth_failures", req); }
function recordAuthorizationFailure(req) { recordFailure(authzFailureBuckets, AUTHZ_FAILURE_ALERT_THRESHOLD, "repeated_authorization_failures", req); }
setInterval(() => { const cutoff = Date.now() - AUTH_FAILURE_WINDOW_MS; for (const bucketMap of [authFailureBuckets, authzFailureBuckets]) for (const [key, value] of bucketMap) if (value.startedAt < cutoff) bucketMap.delete(key); }, AUTH_FAILURE_WINDOW_MS).unref();
export function installSecurityHardening({ app }) {
  if (!app?.use || !app?._router?.stack) return;
  if (app._router.stack.some((layer) => layer.fynxSecurityHardening)) return;
  const middleware = (req, res, next) => {
    if (!SAFE_METHODS.has(req.method)) { audit("method_rejected", req); return res.status(405).json({ error: "method not allowed" }); }
    if (req.method === "OPTIONS") { res.setHeader("Allow", [...SAFE_METHODS].join(", ")); return res.status(204).end(); }
    res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()" );
    res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
    res.setHeader("Cross-Origin-Resource-Policy", "same-site");
    res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
    res.setHeader("X-Permitted-Cross-Domain-Policies", "none");
    if (SENSITIVE_PATHS.test(req.path || "")) res.setHeader("Cache-Control", "no-store");
    const rawTarget = `${req.originalUrl || req.url || ""}`.slice(0, 5000);
    if (SUSPICIOUS_INPUT.test(rawTarget)) { audit("suspicious_request_rejected", req); return res.status(400).json({ error: "invalid request" }); }
    const startedAt = process.hrtime.bigint();
    res.on("finish", () => { if (res.statusCode === 401) recordAuthFailure(req); if (res.statusCode === 403) recordAuthorizationFailure(req); if (res.statusCode === 401 || res.statusCode === 403 || res.statusCode === 429 || res.statusCode >= 500) audit("security_relevant_response", req, { status: res.statusCode, latencyMs: Number(process.hrtime.bigint() - startedAt) / 1_000_000, sensitivePath: SENSITIVE_PATHS.test(req.path || "") }); });
    return next();
  };
  app.use(middleware);
  const index = app._router.stack.length - 1;
  const layer = app._router.stack[index];
  if (!layer) return;
  layer.fynxSecurityHardening = true;
  app._router.stack.splice(index, 1);
  app._router.stack.unshift(layer);
}
