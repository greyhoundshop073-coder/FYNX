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

export function installMediaPrivacyGuard(app) {
  if (!app?._router?.stack || !pool) return;
  if (app._router.stack.some((layer) => layer.fynxMediaPrivacyGuard)) return;

  const guard = async (req, res, next) => {
    if (req.method !== "GET") return next();
    const match = /^\/api\/media\/(\d+)$/.exec(req.path || "");
    if (!match) return next();

    const userId = viewerId(req);
    if (!userId) return next();
    const mediaId = Number(match[1]);
    if (!Number.isInteger(mediaId) || mediaId < 1) return next();

    try {
      const result = await pool.query(`
        SELECT mm.owner_id,
          EXISTS (
            SELECT 1
            FROM messages m
            WHERE m.media_id = mm.id
              AND (m.sender_id = $2 OR m.recipient_id = $2)
              AND EXISTS (
                SELECT 1
                FROM blocks b
                WHERE (b.blocker_id = $2 AND b.blocked_id = CASE WHEN m.sender_id = $2 THEN m.recipient_id ELSE m.sender_id END)
                   OR (b.blocked_id = $2 AND b.blocker_id = CASE WHEN m.sender_id = $2 THEN m.recipient_id ELSE m.sender_id END)
              )
          ) AS blocked_message_media
        FROM message_media mm
        WHERE mm.id = $1
        LIMIT 1`, [mediaId, userId]);

      const row = result.rows[0];
      if (!row) return next();
      if (String(row.owner_id) === userId) return next();
      if (row.blocked_message_media) return res.status(403).json({ error: "media unavailable" });
      return next();
    } catch (error) {
      console.error("media privacy guard", error);
      return res.status(503).json({ error: "media privacy unavailable" });
    }
  };

  app.use("/api/media", guard);
  const addedIndex = app._router.stack.length - 1;
  const added = app._router.stack[addedIndex];
  if (!added) return;
  added.fynxMediaPrivacyGuard = true;
  const targetIndex = app._router.stack.findIndex((layer) => layer.route?.path === "/api/media/:id" && layer.route?.methods?.get);
  if (targetIndex >= 0) {
    app._router.stack.splice(addedIndex, 1);
    app._router.stack.splice(targetIndex, 0, added);
  }
}
