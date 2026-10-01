const DEFAULT_MAX_ATTEMPTS = 4;
const DEFAULT_BASE_DELAY_MS = 250;

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

export function retryWithBackoff(operation, options = {}) {
  const maxAttempts = Math.max(1, Number(options.maxAttempts || DEFAULT_MAX_ATTEMPTS));
  const baseDelayMs = Math.max(25, Number(options.baseDelayMs || DEFAULT_BASE_DELAY_MS));
  const maxDelayMs = Math.max(baseDelayMs, Number(options.maxDelayMs || 10_000));
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
        await sleep(Math.min(maxDelayMs, baseDelayMs * (2 ** (attempt - 1)) + jitter));
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
      if (existing) {
        if (existing.fingerprint !== fingerprint) return { conflict: true };
        // Preserve the established response shape for this primitive. Pending execution
        // coordination belongs to createIdempotentExecutor, which already returns/waits
        // on the original operation rather than exposing an implementation detail here.
        return { duplicate: true, response: existing.response };
      }
      entries.set(key, { fingerprint, createdAt: Date.now(), response: null, promise: null });
      return { duplicate: false };
    },
    complete(key, response) {
      const entry = entries.get(key);
      if (entry) entry.response = response;
    },
    stop() { clearInterval(timer); entries.clear(); }
  };
}

// Executes a non-payment operation once per idempotency key within this process.
// Concurrent duplicates wait for the original operation instead of receiving a null result.
// This is deliberately a bounded process-local primitive; durable cross-instance idempotency
// remains a database-backed integration concern for routes that require it.
export function createIdempotentExecutor({ maxEntries = 10_000, ttlMs = 24 * 60 * 60 * 1000 } = {}) {
  const entries = new Map();
  const cleanup = () => {
    const cutoff = Date.now() - ttlMs;
    for (const [key, entry] of entries) if (entry.createdAt < cutoff && !entry.promise) entries.delete(key);
    while (entries.size > maxEntries) {
      const oldest = entries.keys().next().value;
      if (oldest === undefined) break;
      entries.delete(oldest);
    }
  };
  const timer = setInterval(cleanup, Math.min(ttlMs, 60_000)).unref();

  return {
    async execute(key, fingerprint, operation) {
      if (!key || !fingerprint || typeof operation !== "function") throw new Error("idempotency key, fingerprint, and operation are required");
      cleanup();
      const existing = entries.get(key);
      if (existing) {
        if (existing.fingerprint !== fingerprint) {
          const error = new Error("idempotency key reused with different operation");
          error.code = "IDEMPOTENCY_CONFLICT";
          throw error;
        }
        return existing.promise || existing.response;
      }

      const entry = { fingerprint, createdAt: Date.now(), promise: null, response: undefined };
      entry.promise = (async () => {
        try {
          const response = await operation();
          entry.response = response;
          return response;
        } finally {
          entry.promise = null;
          entry.createdAt = Date.now();
          cleanup();
        }
      })();
      entries.set(key, entry);
      return entry.promise;
    },
    stop() { clearInterval(timer); entries.clear(); }
  };
}

// Small bounded background-job primitive for work that is safe to retry. Jobs that exhaust
// attempts are retained in a dead-letter list for inspection/re-drive rather than looping forever.
export function createJobQueue({ concurrency = 2, maxAttempts = 4, maxQueue = 1_000, baseDelayMs = 250 } = {}) {
  const pending = [];
  const deadLetters = [];
  const active = new Set();
  let stopped = false;
  let draining = false;

  const drain = async () => {
    if (draining) return;
    draining = true;
    try {
      while (!stopped && active.size < concurrency && pending.length) {
        const job = pending.shift();
        const task = (async () => {
          try {
            await retryWithBackoff(() => job.handler(job.payload, job.attempts + 1), {
              maxAttempts,
              baseDelayMs,
              shouldRetry: () => true
            });
          } catch (error) {
            job.attempts += maxAttempts;
            deadLetters.push({ ...job, error: String(error?.message || error), failedAt: Date.now() });
            if (deadLetters.length > maxQueue) deadLetters.shift();
          }
        })().finally(() => active.delete(task));
        active.add(task);
      }
    } finally {
      draining = false;
      if (!stopped && pending.length && active.size < concurrency) void drain();
    }
  };

  return {
    enqueue(name, payload, handler) {
      if (stopped) throw new Error("job queue is stopped");
      if (pending.length >= maxQueue) {
        const error = new Error("job queue capacity reached");
        error.code = "JOB_QUEUE_FULL";
        throw error;
      }
      if (typeof handler !== "function") throw new Error("job handler is required");
      pending.push({ id: `${Date.now()}-${Math.random().toString(36).slice(2)}`, name, payload, handler, attempts: 0, queuedAt: Date.now() });
      void drain();
    },
    snapshot() { return { pending: pending.length, active: active.size, deadLetters: deadLetters.length, stopped }; },
    getDeadLetters() { return deadLetters.map(({ handler: _handler, ...job }) => ({ ...job })); },
    async redrive(id) {
      const index = deadLetters.findIndex(job => job.id === id);
      if (index < 0) return false;
      const job = deadLetters.splice(index, 1)[0];
      this.enqueue(job.name, job.payload, job.handler);
      return true;
    },
    stop() { stopped = true; pending.length = 0; }
  };
}
