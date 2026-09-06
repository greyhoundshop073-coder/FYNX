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

const KEYS = ["profile_visibility", "online_visibility", "posts_visibility", "status_visibility", "profile_photo_visibility", "messages_visibility"];
const DEFAULT_VISIBILITY = "My friends";
const OPTIONS = new Set(["Everyone", DEFAULT_VISIBILITY, "Nobody"]);
let schemaPromise;

async function ensureSchema() {
  if (!pool) throw new Error("database not configured");
  if (!schemaPromise) {
    schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS privacy_settings (
        user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
        profile_visibility TEXT NOT NULL DEFAULT 'My friends',
        online_visibility TEXT NOT NULL DEFAULT 'My friends',
        posts_visibility TEXT NOT NULL DEFAULT 'My friends',
        status_visibility TEXT NOT NULL DEFAULT 'My friends',
        profile_photo_visibility TEXT NOT NULL DEFAULT 'My friends',
        messages_visibility TEXT NOT NULL DEFAULT 'My friends',
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        CHECK (profile_visibility IN ('Everyone','My friends','Nobody')),
        CHECK (online_visibility IN ('Everyone','My friends','Nobody')),
        CHECK (posts_visibility IN ('Everyone','My friends','Nobody')),
        CHECK (status_visibility IN ('Everyone','My friends','Nobody')),
        CHECK (profile_photo_visibility IN ('Everyone','My friends','Nobody')),
        CHECK (messages_visibility IN ('Everyone','My friends','Nobody'))
      );
    `).catch((error) => { schemaPromise = undefined; throw error; });
  }
  return schemaPromise;
}

function auth(req, res, next) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) return res.status(401).json({ error: "authentication required" });
  try { req.privacyUser = jwt.verify(token, JWT_SECRET); return next(); }
  catch { return res.status(401).json({ error: "invalid or expired token" }); }
}

function values(row) {
  return {
    profile_visibility: row.profile_visibility || DEFAULT_VISIBILITY,
    online_visibility: row.online_visibility || DEFAULT_VISIBILITY,
    posts_visibility: row.posts_visibility || DEFAULT_VISIBILITY,
    status_visibility: row.status_visibility || DEFAULT_VISIBILITY,
    profile_photo_visibility: row.profile_photo_visibility || DEFAULT_VISIBILITY,
    messages_visibility: row.messages_visibility || DEFAULT_VISIBILITY
  };
}

async function getVisibility(userId, key) {
  await ensureSchema();
  const result = await pool.query(`SELECT ${key} FROM privacy_settings WHERE user_id=$1`, [userId]);
  return result.rows[0]?.[key] || DEFAULT_VISIBILITY;
}

async function areFriends(userA, userB) {
  const result = await pool.query(
    `SELECT 1 FROM friendships WHERE ((user_id=$1 AND friend_id=$2) OR (user_id=$2 AND friend_id=$1)) AND status='accepted' LIMIT 1`,
    [userA, userB]
  );
  return Boolean(result.rowCount);
}

function insertBeforeRoute(app, method, path, middleware) {
  const router = app._router;
  if (!router?.stack) throw new Error("Express router is unavailable");
  const marker = `${method}:${path}`;
  if (router.stack.some((layer) => layer.fynxPrivacyGuard === marker)) return;
  app.use(path, middleware);
  const addedIndex = router.stack.length - 1;
  const added = router.stack[addedIndex];
  if (!added) throw new Error(`privacy guard layer creation failed: ${method.toUpperCase()} ${path}`);
  added.fynxPrivacyGuard = marker;
  const targetIndex = router.stack.findIndex((layer) => layer.route?.path === path && layer.route?.methods?.[method]);
  if (targetIndex < 0) throw new Error(`privacy target route not found: ${method.toUpperCase()} ${path}`);
  router.stack.splice(addedIndex, 1);
  router.stack.splice(targetIndex, 0, added);
}

function createGuard(method, handler) {
  return (req, res, next) => req.method.toLowerCase() === method ? handler(req, res, next) : next();
}

function registerServerEnforcement(app) {
  insertBeforeRoute(app, "post", "/api/social/posts", createGuard("post", async (req, res, next) => {
    try {
      const visibility = await getVisibility(req.user?.sub, "posts_visibility");
      if (visibility === "Nobody") return res.status(403).json({ error: "posting is disabled by your Posts privacy setting" });
      if (visibility === DEFAULT_VISIBILITY) req.body = { ...(req.body || {}), visibility: "FRIENDS_ONLY" };
      return next();
    } catch (error) {
      console.error("privacy post enforcement", error);
      return res.status(503).json({ error: "privacy settings unavailable" });
    }
  }));

  insertBeforeRoute(app, "post", "/api/messages", createGuard("post", async (req, res, next) => {
    try {
      const username = typeof req.body?.recipientUsername === "string" ? req.body.recipientUsername.trim().toLowerCase().replace(/^@+/, "") : "";
      if (!username) return next();
      const recipient = await pool.query("SELECT id FROM users WHERE username=$1", [username]);
      if (!recipient.rows[0]) return next();
      const recipientId = recipient.rows[0].id;
      if (String(recipientId) === String(req.user?.sub)) return next();
      const visibility = await getVisibility(recipientId, "messages_visibility");
      if (visibility === "Nobody") return res.status(403).json({ error: "this user is not accepting messages" });
      if (visibility === DEFAULT_VISIBILITY && !(await areFriends(req.user.sub, recipientId))) return res.status(403).json({ error: "you must be friends with this user to send a message" });
      return next();
    } catch (error) {
      console.error("privacy message enforcement", error);
      return res.status(503).json({ error: "privacy settings unavailable" });
    }
  }));

  insertBeforeRoute(app, "post", "/api/statuses", createGuard("post", async (req, res, next) => {
    try {
      const visibility = await getVisibility(req.user?.sub, "status_visibility");
      if (visibility === "Nobody") return res.status(403).json({ error: "status posting is disabled by your Status privacy setting" });
      if (visibility === DEFAULT_VISIBILITY) req.body = { ...(req.body || {}), private_status: true };
      if (visibility === "Everyone") req.body = { ...(req.body || {}), private_status: false };
      return next();
    } catch (error) {
      console.error("privacy status enforcement", error);
      return res.status(503).json({ error: "privacy settings unavailable" });
    }
  }));
}

export function registerPrivacyRoutes({ app }) {
  app.get("/api/privacy", auth, async (req, res) => {
    try {
      await ensureSchema();
      const result = await pool.query(`INSERT INTO privacy_settings (user_id) VALUES ($1) ON CONFLICT (user_id) DO NOTHING RETURNING profile_visibility, online_visibility, posts_visibility, status_visibility, profile_photo_visibility, messages_visibility`, [req.privacyUser.sub]);
      let row = result.rows[0];
      if (!row) {
        const existing = await pool.query(`SELECT profile_visibility, online_visibility, posts_visibility, status_visibility, profile_photo_visibility, messages_visibility FROM privacy_settings WHERE user_id=$1`, [req.privacyUser.sub]);
        row = existing.rows[0];
      }
      res.set("Cache-Control", "no-store");
      return res.json({ settings: values(row) });
    } catch (error) {
      console.error("privacy get", error);
      return res.status(500).json({ error: "privacy settings unavailable" });
    }
  });

  app.patch("/api/privacy", auth, async (req, res) => {
    try {
      await ensureSchema();
      const input = req.body?.settings && typeof req.body.settings === "object" ? req.body.settings : req.body;
      const updates = {};
      for (const key of KEYS) {
        if (input?.[key] !== undefined) {
          const value = String(input[key]).trim();
          if (!OPTIONS.has(value)) return res.status(400).json({ error: `invalid privacy value for ${key}` });
          updates[key] = value;
        }
      }
      if (!Object.keys(updates).length) return res.status(400).json({ error: "at least one privacy setting is required" });
      const columns = Object.keys(updates);
      const params = [req.privacyUser.sub, ...columns.map((key) => updates[key])];
      const assignments = columns.map((key, index) => `${key}=$${index + 2}`);
      const insertColumns = ["user_id", ...columns].join(", ");
      const insertValues = ["$1", ...columns.map((_, index) => `$${index + 2}`)].join(", ");
      const result = await pool.query(`INSERT INTO privacy_settings (${insertColumns}) VALUES (${insertValues}) ON CONFLICT (user_id) DO UPDATE SET ${assignments.join(", ")}, updated_at=NOW() RETURNING profile_visibility, online_visibility, posts_visibility, status_visibility, profile_photo_visibility, messages_visibility`, params);
      res.set("Cache-Control", "no-store");
      return res.json({ settings: values(result.rows[0]) });
    } catch (error) {
      console.error("privacy update", error);
      return res.status(500).json({ error: "privacy settings update failed" });
    }
  });

  registerServerEnforcement(app);
}
