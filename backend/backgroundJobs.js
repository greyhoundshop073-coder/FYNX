import { createHash } from "node:crypto";
import { isTransientDatabaseError, retryWithBackoff } from "./reliability.js";

const DEFAULT_MAX_ATTEMPTS = 5;
const DEFAULT_LEASE_MS = 30_000;
const DEFAULT_POLL_MS = 1_000;
const DEFAULT_BASE_DELAY_MS = 500;

function backoffMs(attempt, baseDelayMs = DEFAULT_BASE_DELAY_MS) {
  const capped = Math.min(60_000, baseDelayMs * (2 ** Math.max(0, attempt - 1)));
  return capped + Math.floor(Math.random() * Math.max(1, Math.floor(capped / 2)));
}

function makeJobId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function stableSerialize(value) {
  if (Array.isArray(value)) return `[${value.map(stableSerialize).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableSerialize(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

function idempotencyFingerprint(type, payload) {
  return createHash("sha256")
    .update(stableSerialize({ type: String(type), payload: payload ?? {} }))
    .digest("hex");
}

export function createBackgroundJobQueue({ connectionString = process.env.DATABASE_URL, logger = console, pollMs = DEFAULT_POLL_MS, leaseMs = DEFAULT_LEASE_MS } = {}) {
  // Keep pg lazy: verification, health checks, and local startup must work when the
  // optional database-backed worker is disabled or its dependency is not installed.
  if (!connectionString) {
    return {
      enabled: false,
      enqueue: async () => { throw new Error("DATABASE_URL is not configured"); },
      start: async () => {},
      stop: async () => {},
      snapshot: () => ({ enabled: false })
    };
  }

  let poolPromise = null;
  async function getPool() {
    if (!poolPromise) {
      poolPromise = import("pg").then(({ default: pg }) => new pg.Pool({
        connectionString,
        ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
        max: Number(process.env.JOB_DB_POOL_MAX || 5),
        idleTimeoutMillis: 30_000,
        connectionTimeoutMillis: 5_000,
        statement_timeout: 15_000,
        query_timeout: 20_000,
        keepAlive: true
      }));
    }
    return poolPromise;
  }

  let timer = null;
  let running = false;
  let stopped = false;
  let lastAlertSignature = "";
  const metrics = { claimed: 0, completed: 0, retried: 0, deadLettered: 0, recovered: 0, failures: 0 };
  const alertThresholds = {
    failures: Math.max(1, Number(process.env.JOB_FAILURE_ALERT_THRESHOLD || 3)),
    deadLettered: Math.max(1, Number(process.env.JOB_DEAD_LETTER_ALERT_THRESHOLD || 1))
  };

  function snapshot() {
    const alerts = [];
    if (metrics.failures >= alertThresholds.failures) alerts.push("worker_failures");
    if (metrics.deadLettered >= alertThresholds.deadLettered) alerts.push("dead_letter_jobs");
    return {
      enabled: true,
      running,
      stopped,
      ...metrics,
      alert: alerts.length > 0,
      alerts,
      alertThresholds
    };
  }

  function emitAlertsIfNeeded() {
    const state = snapshot();
    if (!state.alert) return;
    const signature = state.alerts.join(",");
    if (signature === lastAlertSignature) return;
    lastAlertSignature = signature;
    logger.error("[fynx-jobs] alert", JSON.stringify({ alerts: state.alerts, failures: state.failures, deadLettered: state.deadLettered }));
  }

  async function ensureSchema() {
    const pool = await getPool();
    await pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_background_jobs (
        id TEXT PRIMARY KEY,
        type TEXT NOT NULL,
        payload JSONB NOT NULL DEFAULT '{}'::jsonb,
        status TEXT NOT NULL CHECK (status IN ('queued','running','completed','dead')) DEFAULT 'queued',
        attempts INTEGER NOT NULL DEFAULT 0,
        max_attempts INTEGER NOT NULL DEFAULT ${DEFAULT_MAX_ATTEMPTS},
        available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        lease_expires_at TIMESTAMPTZ,
        last_error TEXT,
        idempotency_key TEXT,
        idempotency_fingerprint TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        completed_at TIMESTAMPTZ
      );
      ALTER TABLE fynx_background_jobs
        ADD COLUMN IF NOT EXISTS idempotency_fingerprint TEXT;
      CREATE INDEX IF NOT EXISTS fynx_background_jobs_ready_idx ON fynx_background_jobs (status, available_at);
      CREATE INDEX IF NOT EXISTS fynx_background_jobs_lease_idx ON fynx_background_jobs (status, lease_expires_at);
      CREATE UNIQUE INDEX IF NOT EXISTS fynx_background_jobs_idempotency_idx ON fynx_background_jobs (idempotency_key) WHERE idempotency_key IS NOT NULL;
    `);
  }

  async function enqueue(type, payload = {}, options = {}) {
    const pool = await getPool();
    const id = makeJobId();
    const normalizedType = String(type);
    const normalizedPayload = payload ?? {};
    const maxAttempts = Math.max(1, Number(options.maxAttempts || DEFAULT_MAX_ATTEMPTS));
    const delayMs = Math.max(0, Number(options.delayMs || 0));
    const idempotencyKey = options.idempotencyKey ? String(options.idempotencyKey).slice(0, 200) : null;
    const fingerprint = idempotencyFingerprint(normalizedType, normalizedPayload);

    const result = await pool.query(
      `INSERT INTO fynx_background_jobs (id, type, payload, max_attempts, available_at, idempotency_key, idempotency_fingerprint)
       VALUES ($1, $2, $3::jsonb, $4, NOW() + ($5::text || ' milliseconds')::interval, $6, $7)
       ON CONFLICT (idempotency_key) DO NOTHING
       RETURNING id, type, status, attempts, max_attempts, available_at, idempotency_key, idempotency_fingerprint`,
      [id, normalizedType, JSON.stringify(normalizedPayload), maxAttempts, delayMs, idempotencyKey, fingerprint]
    );

    if (result.rows[0]) return result.rows[0];
    if (!idempotencyKey) throw new Error("Background job insert returned no row");

    // A reused idempotency key is only a duplicate when the operation fingerprint
    // matches. A different payload/type must never silently reuse the old job.
    const existing = await pool.query(
      `SELECT id, type, payload, status, attempts, max_attempts, available_at, idempotency_key, idempotency_fingerprint
       FROM fynx_background_jobs
       WHERE idempotency_key = $1`,
      [idempotencyKey]
    );
    if (!existing.rows[0]) {
      throw new Error("Background job idempotency race; retry enqueue");
    }

    const row = existing.rows[0];
    const existingFingerprint = row.idempotency_fingerprint || idempotencyFingerprint(row.type, row.payload);
    if (existingFingerprint !== fingerprint) {
      const error = new Error("IDEMPOTENCY_KEY_CONFLICT: idempotency key was already used with a different operation payload");
      error.code = "IDEMPOTENCY_KEY_CONFLICT";
      throw error;
    }
    return row;
  }

  async function reclaimExpired() {
    const pool = await getPool();
    const result = await pool.query(
      `UPDATE fynx_background_jobs
       SET status = 'queued', available_at = NOW(), lease_expires_at = NULL, updated_at = NOW()
       WHERE status = 'running' AND lease_expires_at < NOW()
       RETURNING id`
    );
    metrics.recovered += result.rowCount || 0;
  }

  async function claimOne() {
    const pool = await getPool();
    return retryWithBackoff(async () => {
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const result = await client.query(
          `SELECT id, type, payload, attempts, max_attempts
           FROM fynx_background_jobs
           WHERE status = 'queued' AND available_at <= NOW()
           ORDER BY available_at ASC, created_at ASC
           FOR UPDATE SKIP LOCKED LIMIT 1`
        );
        if (!result.rows[0]) { await client.query("COMMIT"); return null; }
        const row = result.rows[0];
        const nextAttempt = row.attempts + 1;
        await client.query(
          `UPDATE fynx_background_jobs
           SET status = 'running', attempts = $2, lease_expires_at = NOW() + ($3::text || ' milliseconds')::interval, updated_at = NOW()
           WHERE id = $1`,
          [row.id, nextAttempt, leaseMs]
        );
        await client.query("COMMIT");
        metrics.claimed += 1;
        return { ...row, attempts: nextAttempt };
      } catch (error) {
        await client.query("ROLLBACK").catch(() => {});
        throw error;
      } finally { client.release(); }
    }, {
      maxAttempts: 3,
      baseDelayMs: 100,
      maxDelayMs: 1_000,
      shouldRetry: isTransientDatabaseError
    });
  }

  async function finish(job, error = null) {
    const pool = await getPool();
    if (!error) {
      await pool.query(`UPDATE fynx_background_jobs SET status = 'completed', lease_expires_at = NULL, completed_at = NOW(), updated_at = NOW() WHERE id = $1`, [job.id]);
      metrics.completed += 1;
      return;
    }
    const message = String(error?.message || error).slice(0, 2_000);
    if (job.attempts >= job.max_attempts) {
      await pool.query(`UPDATE fynx_background_jobs SET status = 'dead', lease_expires_at = NULL, last_error = $2, updated_at = NOW() WHERE id = $1`, [job.id, message]);
      metrics.deadLettered += 1;
      emitAlertsIfNeeded();
      return;
    }
    const delay = backoffMs(job.attempts);
    await pool.query(
      `UPDATE fynx_background_jobs
       SET status = 'queued', available_at = NOW() + ($2::text || ' milliseconds')::interval, lease_expires_at = NULL, last_error = $3, updated_at = NOW()
       WHERE id = $1`,
      [job.id, delay, message]
    );
    metrics.retried += 1;
  }

  async function tick() {
    if (stopped || running) return;
    running = true;
    try {
      await reclaimExpired();
      const job = await claimOne();
      if (job && typeof globalThis.__fynxJobHandlers?.[job.type] === "function") {
        try { await globalThis.__fynxJobHandlers[job.type](job.payload, job); await finish(job); }
        catch (error) { await finish(job, error); }
      } else if (job) {
        await finish(job, new Error(`No handler registered for background job type: ${job.type}`));
      }
    } catch (error) {
      metrics.failures += 1;
      emitAlertsIfNeeded();
      logger.error("[fynx-jobs] worker error", error?.message || error);
    } finally {
      running = false;
    }
  }

  async function start() {
    if (stopped) return;
    await ensureSchema();
    await reclaimExpired();
    timer = setInterval(() => { void tick(); }, pollMs).unref();
    void tick();
  }

  async function stop() {
    stopped = true;
    if (timer) clearInterval(timer);
    const pool = poolPromise ? await poolPromise.catch(() => null) : null;
    if (pool) await pool.end();
  }

  return { enabled: true, enqueue, start, stop, snapshot };
}
