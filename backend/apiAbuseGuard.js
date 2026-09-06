const LIMITS = [
  [/^\/api\/marketplace\//, 90],
  [/^\/api\/advertising\//, 60]
];
const WINDOW_MS = 60_000;
const MAX_BUCKETS = 10_000;
const buckets = new Map();

function limitFor(path) {
  return LIMITS.find(([pattern]) => pattern.test(path || ""))?.[1] || 0;
}

export function installApiAbuseGuard(app) {
  if (!app?.use || app._fynxApiAbuseGuardInstalled) return;
  app._fynxApiAbuseGuardInstalled = true;

  const middleware = (req, res, next) => {
    const limit = limitFor(req.path || "");
    if (!limit) return next();

    const key = `${req.path.split("/").slice(0, 4).join("/")}:${req.ip || req.socket.remoteAddress || "unknown"}`;
    const now = Date.now();
    const current = buckets.get(key);
    if (!current || now - current.startedAt >= WINDOW_MS) {
      if (!current && buckets.size >= MAX_BUCKETS) {
        res.setHeader("Retry-After", "1");
        return res.status(503).json({ error: "server is temporarily protecting capacity" });
      }
      buckets.set(key, { startedAt: now, count: 1 });
      return next();
    }

    current.count += 1;
    if (current.count > limit) {
      res.setHeader("Retry-After", "60");
      return res.status(429).json({ error: "too many requests" });
    }
    return next();
  };

  app.use(middleware);
  const layer = app._router?.stack?.[app._router.stack.length - 1];
  if (layer) {
    layer.fynxApiAbuseGuard = true;
    app._router.stack.pop();
    app._router.stack.unshift(layer);
  }

  setInterval(() => {
    const cutoff = Date.now() - WINDOW_MS;
    for (const [key, value] of buckets) if (value.startedAt < cutoff) buckets.delete(key);
  }, WINDOW_MS).unref();
}
