import pg from "pg";

const { Pool } = pg;
export const DATABASE_URL = process.env.DATABASE_URL || "";

const configuredMaximum = Number.parseInt(process.env.DB_POOL_MAX || "10", 10);
const maximumConnections = Number.isInteger(configuredMaximum) && configuredMaximum > 0
  ? Math.min(configuredMaximum, 20)
  : 10;

// One process-wide pool is shared by API routes, privacy guards and Marketplace
// workers. This prevents module-level pools from multiplying Neon's connection
// usage. Idle connections are released so Neon can suspend when the app is idle.
export const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: maximumConnections,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;

// Handle idle-client disconnects explicitly. Log only the PostgreSQL error code,
// never connection details or query parameters that could contain sensitive data.
if (pool) {
  pool.on("error", (error) => {
    console.error("[fynx-db] idle PostgreSQL client error", error?.code || "UNKNOWN");
  });
}

// The HTTP server owns the lifecycle and closes this pool once on SIGTERM/SIGINT.
globalThis.__fynxPool = pool;

export default pool;
