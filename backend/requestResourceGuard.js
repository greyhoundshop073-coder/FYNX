const MAX_ACTIVE_REQUESTS = 450;
const MAX_URL_LENGTH = 8_192;
const MAX_BODY_BYTES = 18 * 1024 * 1024;
let activeRequests = 0;

export function installRequestResourceGuard(app) {
  if (!app?.use || app._fynxRequestResourceGuardInstalled) return;
  app._fynxRequestResourceGuardInstalled = true;

  const middleware = (req, res, next) => {
    const url = String(req.originalUrl || req.url || "");
    if (url.length > MAX_URL_LENGTH) return res.status(414).json({ error: "request target too long" });

    const contentLength = Number.parseInt(req.get("content-length") || "", 10);
    if (Number.isFinite(contentLength) && contentLength > MAX_BODY_BYTES) {
      return res.status(413).json({ error: "request body is too large" });
    }

    if (activeRequests >= MAX_ACTIVE_REQUESTS) {
      res.setHeader("Retry-After", "1");
      return res.status(503).json({ error: "server is temporarily protecting capacity" });
    }

    activeRequests += 1;
    let released = false;
    const release = () => {
      if (released) return;
      released = true;
      activeRequests = Math.max(0, activeRequests - 1);
    };
    res.once("finish", release);
    res.once("close", release);
    return next();
  };

  app.use(middleware);
  const layer = app._router?.stack?.[app._router.stack.length - 1];
  if (layer) {
    layer.fynxRequestResourceGuard = true;
    app._router.stack.pop();
    app._router.stack.unshift(layer);
  }
}
