import pg from "pg";
import jwt from "jsonwebtoken";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: 2,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  keepAlive: true
}) : null;

export function registerFollowRoutes({ app }) {
  if (!pool) return;
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        CREATE TABLE IF NOT EXISTS social_follows (
          follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          followed_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (follower_id, followed_id),
          CHECK (follower_id <> followed_id)
        );
        CREATE INDEX IF NOT EXISTS social_follows_followed_idx ON social_follows(followed_id, created_at DESC);
      `).catch((error) => {
        schemaPromise = undefined;
        throw error;
      });
    }
    return schemaPromise;
  };

  const auth = (req, res, next) => {
    const header = req.get("authorization") || "";
    const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
    if (!token || !JWT_SECRET) return res.status(401).json({ error: "authentication required" });
    try {
      req.followUser = jwt.verify(token, JWT_SECRET);
      return next();
    } catch {
      return res.status(401).json({ error: "invalid or expired token" });
    }
  };

  const findTarget = async (username) => {
    const normalized = String(username || "").trim().toLowerCase().replace(/^@+/, "");
    if (!normalized || normalized.length > 32) return null;
    const result = await pool.query("SELECT id, username FROM users WHERE username = $1 LIMIT 1", [normalized]);
    return result.rows[0] || null;
  };

  const blocked = async (a, b) => Boolean((await pool.query(
    "SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1",
    [a, b]
  )).rowCount);

  app.post("/api/social/follow/:username", auth, async (req, res) => {
    try {
      await ensureSchema();
      const target = await findTarget(req.params?.username);
      if (!target) return res.status(404).json({ error: "user not found" });
      const viewerId = String(req.followUser.sub);
      const targetId = String(target.id);
      if (viewerId === targetId) return res.status(400).json({ error: "cannot follow yourself" });
      if (await blocked(viewerId, targetId)) return res.status(403).json({ error: "follow unavailable" });
      await pool.query(
        "INSERT INTO social_follows(follower_id, followed_id) VALUES($1,$2) ON CONFLICT DO NOTHING",
        [viewerId, targetId]
      );
      res.set("Cache-Control", "no-store");
      return res.status(201).json({ following: true });
    } catch (error) {
      console.error("social follow", error);
      return res.status(500).json({ error: "follow failed" });
    }
  });

  app.delete("/api/social/follow/:username", auth, async (req, res) => {
    try {
      await ensureSchema();
      const target = await findTarget(req.params?.username);
      if (!target) return res.status(404).json({ error: "user not found" });
      await pool.query(
        "DELETE FROM social_follows WHERE follower_id=$1 AND followed_id=$2",
        [req.followUser.sub, target.id]
      );
      res.set("Cache-Control", "no-store");
      return res.json({ following: false });
    } catch (error) {
      console.error("social unfollow", error);
      return res.status(500).json({ error: "unfollow failed" });
    }
  });
}
