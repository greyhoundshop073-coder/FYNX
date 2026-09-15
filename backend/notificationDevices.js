import pg from "pg";
import jwt from "jsonwebtoken";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: 3,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  keepAlive: true
}) : null;

let schemaPromise;
async function ensureSchema() {
  if (!pool) throw new Error("database not configured");
  if (!schemaPromise) {
    schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS notification_devices (
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        provider TEXT NOT NULL,
        token TEXT NOT NULL,
        enabled BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY (user_id, provider, token)
      );
      CREATE INDEX IF NOT EXISTS notification_devices_token_idx
        ON notification_devices (provider, token) WHERE enabled = TRUE;
    `).catch(error => {
      schemaPromise = undefined;
      throw error;
    });
  }
  return schemaPromise;
}

function auth(req, res, next) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) return res.status(401).json({ error: "authentication required" });
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    return next();
  } catch {
    return res.status(401).json({ error: "invalid or expired token" });
  }
}

function cleanToken(value) {
  return typeof value === "string" ? value.trim().slice(0, 4096) : "";
}

export function registerNotificationDeviceRoutes({ app }) {
  app.post("/api/notification-devices", auth, async (req, res) => {
    try {
      await ensureSchema();
      const provider = typeof req.body?.provider === "string" ? req.body.provider.trim().toLowerCase() : "";
      const token = cleanToken(req.body?.token);
      if (!/^[a-z0-9_-]{2,32}$/.test(provider)) return res.status(400).json({ error: "invalid notification provider" });
      if (!token) return res.status(400).json({ error: "notification token is required" });
      await pool.query(`
        INSERT INTO notification_devices(user_id, provider, token, enabled, updated_at, last_seen_at)
        VALUES($1,$2,$3,TRUE,NOW(),NOW())
        ON CONFLICT(user_id, provider, token)
        DO UPDATE SET enabled=TRUE, updated_at=NOW(), last_seen_at=NOW()
      `, [req.user.sub, provider, token]);
      return res.status(204).end();
    } catch (error) {
      console.error("notification device register", error);
      return res.status(500).json({ error: "notification device registration failed" });
    }
  });

  app.delete("/api/notification-devices", auth, async (req, res) => {
    try {
      await ensureSchema();
      const provider = typeof req.body?.provider === "string" ? req.body.provider.trim().toLowerCase() : "";
      const token = cleanToken(req.body?.token);
      if (!provider && !token) {
        await pool.query("UPDATE notification_devices SET enabled=FALSE, updated_at=NOW() WHERE user_id=$1", [req.user.sub]);
        return res.status(204).end();
      }
      if (!/^[a-z0-9_-]{2,32}$/.test(provider) || !token) return res.status(400).json({ error: "provider and token are required" });
      await pool.query(`UPDATE notification_devices SET enabled=FALSE, updated_at=NOW() WHERE user_id=$1 AND provider=$2 AND token=$3`, [req.user.sub, provider, token]);
      return res.status(204).end();
    } catch (error) {
      console.error("notification device unregister", error);
      return res.status(500).json({ error: "notification device cleanup failed" });
    }
  });
}
