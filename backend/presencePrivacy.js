import jwt from "jsonwebtoken";
import pg from "pg";
import { WebSocketServer } from "ws";

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

const cache = new Map();
const pending = new Map();
const CACHE_TTL_MS = 5_000;

function viewerIdFromRequest(req) {
  try {
    const url = new URL(req.url || "/realtime", `http://${req.headers.host || "localhost"}`);
    const token = url.searchParams.get("token") || "";
    if (!token || !JWT_SECRET) return "";
    return String(jwt.verify(token, JWT_SECRET)?.sub || "");
  } catch {
    return "";
  }
}

async function canSeePresence(viewerId, targetId) {
  if (!viewerId || !targetId) return false;
  if (viewerId === targetId) return true;
  if (!pool) return false;

  const key = `${viewerId}:${targetId}`;
  const cached = cache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.allowed;

  const result = await pool.query(`
    SELECT COALESCE(p.online_visibility, 'My friends') AS visibility,
           EXISTS (
             SELECT 1 FROM friendships f
             WHERE ((f.user_id=$1 AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=$1))
               AND f.status='accepted'
           ) AS friends,
           EXISTS (
             SELECT 1 FROM blocks b
             WHERE (b.blocker_id=$1 AND b.blocked_id=$2) OR (b.blocker_id=$2 AND b.blocked_id=$1)
           ) AS blocked
    FROM (SELECT 1) base
    LEFT JOIN privacy_settings p ON p.user_id=$2
  `, [viewerId, targetId]);

  const row = result.rows[0] || { visibility: "My friends", friends: false, blocked: false };
  const allowed = !row.blocked && (row.visibility === "Everyone" || (row.visibility === "My friends" && row.friends));
  cache.set(key, { allowed, expiresAt: Date.now() + CACHE_TTL_MS });
  return allowed;
}

export function installPresencePrivacyGuard() {
  const marker = "__fynxPresencePrivacyGuard";
  if (WebSocketServer.prototype[marker]) return;
  WebSocketServer.prototype[marker] = true;

  const originalEmit = WebSocketServer.prototype.emit;
  WebSocketServer.prototype.emit = function fynxPresenceEmit(event, socket, req, ...rest) {
    if (event === "connection" && socket && req && !socket.__fynxPresencePrivacyWrapped) {
      socket.__fynxPresencePrivacyWrapped = true;
      const viewerId = viewerIdFromRequest(req);
      const originalSend = socket.send.bind(socket);

      socket.send = (data, ...args) => {
        let parsed;
        try {
          parsed = typeof data === "string" ? JSON.parse(data) : JSON.parse(Buffer.from(data).toString("utf8"));
        } catch {
          return originalSend(data, ...args);
        }

        if (parsed?.type !== "presence") return originalSend(data, ...args);

        const targetId = String(parsed.userId || "");
        if (!viewerId || !targetId || viewerId === targetId) return originalSend(data, ...args);

        const key = `${viewerId}:${targetId}`;
        const cached = cache.get(key);
        if (cached && cached.expiresAt > Date.now()) return cached.allowed ? originalSend(data, ...args) : undefined;

        if (!pending.has(key)) {
          const task = canSeePresence(viewerId, targetId)
            .catch(() => false)
            .finally(() => pending.delete(key));
          pending.set(key, task);
          task.then((allowed) => {
            if (allowed && socket.readyState === 1) originalSend(data, ...args);
          }).catch(() => {});
        }

        return undefined;
      };
    }

    return originalEmit.call(this, event, socket, req, ...rest);
  };
}
