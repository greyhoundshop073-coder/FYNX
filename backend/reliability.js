const DEFAULT_MAX_ATTEMPTS = 4;
const DEFAULT_BASE_DELAY_MS = 250;

export function retryWithBackoff(operation, options = {}) {
  const maxAttempts = Math.max(1, Number(options.maxAttempts || DEFAULT_MAX_ATTEMPTS));
  const baseDelayMs = Math.max(25, Number(options.baseDelayMs || DEFAULT_BASE_DELAY_MS));
  const shouldRetry = typeof options.shouldRetry === "function" ? options.shouldRetry : () => true;
  return (async () => {
    let lastError;
    for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
      try {
        return await operation(attempt);
      } catch (error) {
        lastError = error;
        if (attempt >= maxAttempts || !shouldRetry(error, attempt)) throw error;
        const jitter = Math.floor(Math.random() * Math.max(1, baseDelayMs));
        await new Promise(resolve => setTimeout(resolve, Math.min(10_000, baseDelayMs * (2 ** (attempt - 1)) + jitter)));
      }
    }
    throw lastError;
  })();
}

export function isTransientDatabaseError(error) {
  return ["40001", "40P01", "53300", "57P01", "08000", "08001", "08003", "08004", "08006", "08007", "08P01"].includes(String(error?.code || ""));
}

export function installFailureRecovery({ server, pool, logger = console }) {
  let accepting = true;
  const state = { accepting: true, startedAt: Date.now(), failures: 0, recovered: 0 };

  const guardedQuery = pool
    ? (text, params = [], options = {}) => retryWithBackoff(
        () => pool.query(text, params),
        { maxAttempts: options.maxAttempts || 3, baseDelayMs: options.baseDelayMs || 150, shouldRetry: isTransientDatabaseError }
      )
    : null;

  const originalClose = server.close.bind(server);
  server.close = callback => {
    accepting = false;
    state.accepting = false;
    return originalClose(callback);
  };

  server.on("request", (_req, res) => {
    if (!accepting && !res.headersSent) res.setHeader("Connection", "close");
  });
  server.on("clientError", (error, socket) => {
    state.failures += 1;
    logger.error("[fynx-recovery] client error", error?.message || error);
    if (socket.writable) socket.end("HTTP/1.1 400 Bad Request\\r\\nConnection: close\\r\\n\\r\\n");
  });

  return {
    state,
    query: guardedQuery,
    stopAccepting: () => { accepting = false; state.accepting = false; },
    markRecovered: () => { state.recovered += 1; },
    snapshot: () => ({ ...state, uptimeMs: Date.now() - state.startedAt })
  };
}

export function createIdempotencyStore({ maxEntries = 10_000, ttlMs = 24 * 60 * 60 * 1000 } = {}) {
  const entries = new Map();
  const cleanup = () => {
    const cutoff = Date.now() - ttlMs;
    for (const [key, entry] of entries) if (entry.createdAt < cutoff) entries.delete(key);
    while (entries.size > maxEntries) entries.delete(entries.keys().next().value);
  };
  const timer = setInterval(cleanup, Math.min(ttlMs, 60_000)).unref();
  return {
    begin(key, fingerprint) {
      cleanup();
      const existing = entries.get(key);
      if (existing) return existing.fingerprint === fingerprint ? { duplicate: true, response: existing.response } : { conflict: true };
      entries.set(key, { fingerprint, createdAt: Date.now(), response: null });
      return { duplicate: false };
    },
    complete(key, response) {
      const entry = entries.get(key);
      if (entry) entry.response = response;
    },
    stop() { clearInterval(timer); entries.clear(); }
  };
}
