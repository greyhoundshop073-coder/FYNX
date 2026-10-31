import pg from "pg";

const DATABASE_URL = process.env.DATABASE_URL || "";
const rawMaxConnections = String(process.env.DB_POOL_MAX || "10").trim();
if (!/^\d+$/.test(rawMaxConnections)) {
  throw new Error("DB_POOL_MAX must be a positive integer between 1 and 100");
}
const maxConnections = Number(rawMaxConnections);
if (!Number.isSafeInteger(maxConnections) || maxConnections < 1 || maxConnections > 100) {
  throw new Error("DB_POOL_MAX must be a positive integer between 1 and 100");
}

// All backend route modules share this one bounded pool. Per-module pools multiply
// the database connection budget and can exhaust Postgres when replicas are added.
const pool = DATABASE_URL ? new pg.Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: maxConnections,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;

if (pool) {
  pool.on("error", (error) => {
    // Avoid logging connection strings or query parameters.
    console.error("[fynx-db] idle pooled client error", {
      code: typeof error?.code === "string" ? error.code : "UNKNOWN"
    });
  });
}

export function getFynxDatabasePool() {
  return pool;
}

export function getFynxDatabasePoolConfig() {
  return { configured: Boolean(DATABASE_URL), max: maxConnections, min: 0 };
}
