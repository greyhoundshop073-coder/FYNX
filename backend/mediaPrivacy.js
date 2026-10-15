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

async function isBlocked(userId, otherId) {
  if (!userId || !otherId || String(userId) === String(otherId)) return false;
  const result = await pool.query(`
    SELECT 1 FROM blocks
    WHERE (blocker_id = $1 AND blocked_id = $2)
       OR (blocker_id = $2 AND blocked_id = $1)
    LIMIT 1`, [userId, otherId]);
  return result.rowCount > 0;
}

async function areFriends(userId, otherId) {
  if (!userId || !otherId || String(userId) === String(otherId)) return false;
  const result = await pool.query(`
    SELECT 1 FROM friendships
    WHERE ((user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1))
      AND status = 'accepted'
    LIMIT 1`, [userId, otherId]);
  return result.rowCount > 0;
}

async function profileVisibilityAllows(userId, targetId) {
  if (String(userId) === String(targetId)) return true;
  if (await isBlocked(userId, targetId)) return false;
  const result = await pool.query(`
    SELECT COALESCE(profile_visibility, 'My friends') AS visibility
    FROM privacy_settings
    WHERE user_id = $1
    LIMIT 1`, [targetId]);
  const visibility = result.rows[0]?.visibility || "My friends";
  if (visibility === "Everyone") return true;
  if (visibility === "Nobody") return false;
  return areFriends(userId, targetId);
}

function wrapJsonFiltering(res, filter) {
  if (res.__fynxPrivacyJsonWrapped) return;
  const originalJson = res.json.bind(res);
  res.__fynxPrivacyJsonWrapped = true;
  res.json = (payload) => {
    Promise.resolve(filter(payload)).then((filtered) => originalJson(filtered)).catch((error) => {
      console.error("privacy response filter", error);
      if (!res.headersSent) originalJson({ error: "privacy filtering failed" });
    });
    return res;
  };
}

export function installMediaPrivacyGuard(app) {
  if (!app?._router?.stack || !pool) return;
  if (app._router.stack.some((layer) => layer.fynxMediaPrivacyGuard)) return;

  const mediaGuard = async (req, res, next) => {
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
          )
          AND NOT EXISTS (
            SELECT 1
            FROM statuses s
            WHERE s.media_id = mm.id
              AND s.expires_at > NOW()
              AND (
                s.owner_id = $2
                OR (
                  (
                    s.audience = 'EVERYONE'
                    OR (
                      s.audience = 'FRIENDS'
                      AND EXISTS (
                        SELECT 1 FROM friendships f
                        WHERE ((f.user_id = s.owner_id AND f.friend_id = $2)
                          OR (f.user_id = $2 AND f.friend_id = s.owner_id))
                          AND f.status = 'accepted'
                      )
                    )
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM blocks sb
                    WHERE (sb.blocker_id = $2 AND sb.blocked_id = s.owner_id)
                      OR (sb.blocker_id = s.owner_id AND sb.blocked_id = $2)
                  )
                )
              )
          )
          AND NOT EXISTS (
            SELECT 1
            FROM users u
            LEFT JOIN privacy_settings ps ON ps.user_id = u.id
            WHERE u.profile_photo_media_id = mm.id
              AND (
                u.id = $2
                OR (
                  (
                    COALESCE(ps.profile_visibility, 'My friends') = 'Everyone'
                    OR (
                      COALESCE(ps.profile_visibility, 'My friends') = 'My friends'
                      AND EXISTS (
                        SELECT 1 FROM friendships pf
                        WHERE ((pf.user_id = u.id AND pf.friend_id = $2)
                          OR (pf.user_id = $2 AND pf.friend_id = u.id))
                          AND pf.status = 'accepted'
                      )
                    )
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM blocks pb
                    WHERE (pb.blocker_id = $2 AND pb.blocked_id = u.id)
                      OR (pb.blocker_id = u.id AND pb.blocked_id = $2)
                  )
                )
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

  const discoveryGuard = async (req, res, next) => {
    const userId = viewerId(req);
    if (!userId) return next();

    if (req.method === "GET" && req.path === "/api/users/search") {
      wrapJsonFiltering(res, async (payload) => {
        if (!Array.isArray(payload?.users)) return payload;
        const users = [];
        for (const user of payload.users) {
          if (user?.id != null && await profileVisibilityAllows(userId, String(user.id))) users.push(user);
        }
        return { ...payload, users };
      });
    }

    if (req.method === "GET" && req.path === "/api/marketplace/listings") {
      wrapJsonFiltering(res, async (payload) => {
        if (!Array.isArray(payload?.listings)) return payload;
        const listings = [];
        for (const listing of payload.listings) {
          const sellerId = listing?.seller_id == null ? "" : String(listing.seller_id);
          if (sellerId && !(await isBlocked(userId, sellerId))) listings.push(listing);
        }
        return { ...payload, listings };
      });
    }

    return next();
  };

  app.use("/api/media", mediaGuard);
  const mediaIndex = app._router.stack.length - 1;
  const mediaLayer = app._router.stack[mediaIndex];
  if (mediaLayer) {
    mediaLayer.fynxMediaPrivacyGuard = true;
    const targetIndex = app._router.stack.findIndex((layer) => layer.route?.path === "/api/media/:id" && layer.route?.methods?.get);
    if (targetIndex >= 0) {
      app._router.stack.splice(mediaIndex, 1);
      app._router.stack.splice(targetIndex, 0, mediaLayer);
    }
  }

  app.use(discoveryGuard);
  const discoveryIndex = app._router.stack.length - 1;
  const discoveryLayer = app._router.stack[discoveryIndex];
  if (discoveryLayer) {
    discoveryLayer.fynxDiscoveryPrivacyGuard = true;
    const targets = app._router.stack.reduce((indexes, layer, index) => {
      if (layer.route?.path === "/api/users/search" || layer.route?.path === "/api/marketplace/listings") indexes.push(index);
      return indexes;
    }, []);
    if (targets.length) {
      app._router.stack.splice(discoveryIndex, 1);
      const insertAt = Math.min(...targets);
      app._router.stack.splice(insertAt, 0, discoveryLayer);
    }
  }
}
