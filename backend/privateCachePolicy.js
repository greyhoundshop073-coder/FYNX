const PRIVATE_GET_PATHS = [
  /^\/api\/me$/,
  /^\/api\/messages(?:\/|$)/,
  /^\/api\/media(?:\/|$)/,
  /^\/api\/statuses(?:\/|$)/,
  /^\/api\/privacy(?:\/|$)/,
  /^\/api\/blocks(?:\/|$)/,
  /^\/api\/friends(?:\/|$)/
];

function isPrivateGetPath(path) {
  return PRIVATE_GET_PATHS.some((pattern) => pattern.test(path || ""));
}

export function installPrivateCachePolicy(app) {
  if (!app?.use || !app?._router?.stack) return;
  if (app._router.stack.some((layer) => layer.fynxPrivateCachePolicy)) return;

  const middleware = (req, res, next) => {
    if ((req.method === "GET" || req.method === "HEAD") && isPrivateGetPath(req.path || "")) {
      res.setHeader("Cache-Control", "no-store");
      res.setHeader("Pragma", "no-cache");
      res.setHeader("Vary", "Authorization");
    }
    return next();
  };

  app.use(middleware);
  const index = app._router.stack.length - 1;
  const layer = app._router.stack[index];
  if (!layer) return;
  layer.fynxPrivateCachePolicy = true;

  const targetIndexes = app._router.stack.reduce((indexes, candidate, candidateIndex) => {
    const path = candidate.route?.path;
    if (typeof path === "string" && isPrivateGetPath(path)) indexes.push(candidateIndex);
    return indexes;
  }, []);

  if (targetIndexes.length) {
    app._router.stack.splice(index, 1);
    app._router.stack.splice(Math.min(...targetIndexes), 0, layer);
  }
}
