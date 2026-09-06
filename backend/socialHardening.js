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

function viewerId(req) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) return null;
  try { return String(jwt.verify(token, JWT_SECRET).sub); } catch { return null; }
}

function placeBeforeRoutes(app, middleware, matcher, marker) {
  app.use(middleware);
  const addedIndex = app._router.stack.length - 1;
  const added = app._router.stack[addedIndex];
  if (!added) return;
  added[marker] = true;
  const targetIndexes = app._router.stack.reduce((indexes, layer, index) => {
    if (matcher(layer)) indexes.push(index);
    return indexes;
  }, []);
  if (!targetIndexes.length) return;
  app._router.stack.splice(addedIndex, 1);
  app._router.stack.splice(Math.min(...targetIndexes), 0, added);
}

export function installSocialHardening(app) {
  if (!app?._router?.stack || !pool) return;
  if (app._router.stack.some((layer) => layer.fynxSocialHardening)) return;

  const hardening = async (req, res, next) => {
    const path = req.path || "";
    const privateGet = req.method === "GET" && (
      path === "/api/friends" ||
      path === "/api/friends/requests" ||
      path === "/api/blocks" ||
      path === "/api/marketplace/listings" ||
      path === "/api/marketplace/my-listings"
    );
    if (privateGet || path.startsWith("/api/blocks/")) res.set("Cache-Control", "no-store");

    if (req.method === "POST" && path === "/api/friends/request") {
      const userId = viewerId(req);
      const username = typeof req.body?.username === "string"
        ? req.body.username.trim().toLowerCase().replace(/^@+/, "")
        : "";
      if (userId && username) {
        try {
          const target = await pool.query("SELECT id FROM users WHERE username = $1 LIMIT 1", [username]);
          if (target.rows[0]) {
            const reversePending = await pool.query(`
              SELECT 1 FROM friendships
              WHERE user_id = $1 AND friend_id = $2 AND status = 'pending'
              LIMIT 1`, [target.rows[0].id, userId]);
            if (reversePending.rowCount) return res.status(409).json({ error: "friend request already pending" });
          }
        } catch (error) {
          console.error("social request hardening", error);
          return res.status(503).json({ error: "social protection unavailable" });
        }
      }
    }
    return next();
  };

  placeBeforeRoutes(
    app,
    hardening,
    (layer) => [
      "/api/users/search",
      "/api/friends",
      "/api/friends/requests",
      "/api/friends/request",
      "/api/blocks",
      "/api/marketplace/listings",
      "/api/marketplace/my-listings"
    ].includes(layer.route?.path),
    "fynxSocialHardening"
  );
}
