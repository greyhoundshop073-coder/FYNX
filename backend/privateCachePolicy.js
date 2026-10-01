const PRIVATE_GET_PATHS = [
  /^\/api\/me$/,
  /^\/api\/messages(?:\/|$)/,
  /^\/api\/media(?:\/|$)/,
  /^\/api\/statuses(?:\/|$)/,
  /^\/api\/privacy(?:\/|$)/,
  /^\/api\/blocks(?:\/|$)/,
  /^\/api\/friends(?:\/|$)/,
  /^\/api\/marketplace\/media(?:\/|$)/,
  /^\/api\/marketplace\/orders(?:\/|$)/,
  /^\/api\/marketplace\/protection(?:\/|$)/,
  /^\/api\/marketplace\/settlement(?:\/|$)/,
  /^\/api\/advertising\/(?:dashboard|campaigns)(?:\/|$)/
];

function isPrivateGetPath(path) {
  return PRIVATE_GET_PATHS.some((pattern) => pattern.test(path || ""));
}

export function installPrivateCachePolicy(app) {
  if (!app?.use || !app?._router?.stack) return;
  if (app._router.stack.some((layer) => layer.fynxPrivateCachePolicy)) return;

  const middleware = (req, res, next) => {
    if ((req.method === "GET" || req.method === "HEAD") && isPrivateGetPath(req.path || "")) {
      const originalSetHeader = res.setHeader.bind(res);
      res.setHeader = (name, value) => {
        if (String(name).toLowerCase() === "cache-control") value = "no-store";
        return originalSetHeader(name, value);
      };
      originalSetHeader("Cache-Control", "no-store");
      originalSetHeader("Pragma", "no-cache");
      originalSetHeader("Vary", "Authorization");
    }
    return next();
  };

  app.use(middleware);
  const index = app._router.stack.length - 1;
  const layer = app._router.stack[index];
  if (!layer) return;
  layer.fynxPrivateCachePolicy = true;
  app._router.stack.splice(index, 1);
  app._router.stack.unshift(layer);
}
