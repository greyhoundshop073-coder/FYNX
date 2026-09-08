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
  const header = req.get
    ? (req.get("authorization") || "")
    : (req.headers?.authorization || "");
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

    if (
      (req.method === "GET" && path === "/api/social/feed") ||
      (req.method === "POST" && path === "/api/social/posts") ||
      path.startsWith("/api/social/posts/")
    ) {
      res.set("Cache-Control", "no-store");
    }

    if (req.method === "POST" && path === "/api/social/posts") {
      const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
      const visibility = typeof req.body?.visibility === "string" ? req.body.visibility.trim().toUpperCase() : "";
      const mediaId = req.body?.mediaId;
      const mediaType = req.body?.mediaType == null ? "" : String(req.body.mediaType).trim().toLowerCase();

      if (text.length > 4000) return res.status(413).json({ error: "post text is too long" });
      if (!["PUBLIC", "FRIENDS", "ONLY_ME"].includes(visibility)) {
        return res.status(400).json({ error: "invalid post visibility" });
      }
      if (mediaId != null && mediaId !== "") {
        const numericMediaId = Number(mediaId);
        if (!Number.isInteger(numericMediaId) || numericMediaId < 1) {
          return res.status(400).json({ error: "invalid media id" });
        }
        if (!["image", "video", "audio"].includes(mediaType)) {
          return res.status(400).json({ error: "invalid media type" });
        }
      } else if (mediaType) {
        return res.status(400).json({ error: "media id is required when media type is provided" });
      }
    }

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
      "/api/social/feed",
      "/api/social/posts",
      "/api/users/search",
      "/api/friends",
      "/api/friends/requests",
      "/api/friends/request",
      "/api/blocks",
      "/api/marketplace/listings",
      "/api/marketplace/my-listings"
    ].includes(layer.route?.path) || layer.route?.path === "/api/social/posts/:id",
    "fynxSocialHardening"
  );
}
