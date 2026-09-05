const SAFE_METHODS = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]);
const SENSITIVE_PATHS = /\/(auth|payments?|settlement|refund|dispute|advertis|admin|account|password|token)/i;
const SUSPICIOUS_INPUT = /(?:\.\.(?:\/|\\)|<script|javascript:|\bunion\s+select\b|\bor\s+1\s*=\s*1|\bdrop\s+table\b)/i;

function safeIp(req) {
  const value = req.ip || req.socket?.remoteAddress || "unknown";
  return String(value).slice(0, 128);
}

function audit(event, req, extra = {}) {
  const payload = {
    event,
    method: req.method,
    path: String(req.path || req.originalUrl || "").slice(0, 300),
    ip: safeIp(req),
    userId: req.user?.sub ? String(req.user.sub).slice(0, 80) : null,
    at: new Date().toISOString(),
    ...extra
  };
  console.warn(`[fynx-security] ${JSON.stringify(payload)}`);
}

export function installSecurityHardening({ app }) {
  app.use((req, res, next) => {
    if (!SAFE_METHODS.has(req.method)) {
      audit("method_rejected", req);
      return res.status(405).json({ error: "method not allowed" });
    }

    if (req.method === "OPTIONS") {
      res.setHeader("Allow", [...SAFE_METHODS].join(", "));
      return res.status(204).end();
    }

    res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()" );
    res.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
    res.setHeader("Cross-Origin-Resource-Policy", "same-site");

    const rawTarget = `${req.originalUrl || req.url || ""}`.slice(0, 5000);
    if (SUSPICIOUS_INPUT.test(rawTarget)) {
      audit("suspicious_request_rejected", req);
      return res.status(400).json({ error: "invalid request" });
    }

    const startedAt = process.hrtime.bigint();
    res.on("finish", () => {
      if (res.statusCode === 401 || res.statusCode === 403 || res.statusCode === 429 || res.statusCode >= 500) {
        audit("security_relevant_response", req, {
          status: res.statusCode,
          latencyMs: Number(process.hrtime.bigint() - startedAt) / 1_000_000,
          sensitivePath: SENSITIVE_PATHS.test(req.path || "")
        });
      }
    });
    return next();
  });
}
