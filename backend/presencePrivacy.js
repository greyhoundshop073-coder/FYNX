import { getFynxDatabasePool } from "./databasePool.js";
import jwt from "jsonwebtoken";
import pg from "pg";
import { WebSocket, WebSocketServer } from "ws";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const pool = getFynxDatabasePool();

const cache = new Map();
const pending = new Map();
const callBlockCache = new Map();
const callBlockPending = new Map();
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

/**
 * Returns only currently online users whose online-visibility setting allows this viewer.
 * A single bounded query avoids exposing the global presence list or issuing N+1 lookups.
 */
export async function visiblePresenceUserIds(viewerId, onlineUserIds) {
  const viewer = String(viewerId || "").trim();
  if (!/^\d+$/.test(viewer) || !pool || !Array.isArray(onlineUserIds)) return new Set();
  const ids = [...new Set(onlineUserIds.map((id) => String(id || "").trim())
    .filter((id) => /^\d+$/.test(id)))].slice(0, 5_000);
  if (!ids.length) return new Set();

  const result = await pool.query(`
    SELECT target.user_id::text AS user_id
      FROM unnest($2::bigint[]) AS target(user_id)
      LEFT JOIN privacy_settings p ON p.user_id = target.user_id
     WHERE target.user_id = $1::bigint
        OR (
          (
            COALESCE(p.online_visibility, 'My friends') = 'Everyone'
            OR (
              COALESCE(p.online_visibility, 'My friends') = 'My friends'
              AND EXISTS (
                SELECT 1 FROM friendships f
                 WHERE ((f.user_id = $1 AND f.friend_id = target.user_id)
                     OR (f.user_id = target.user_id AND f.friend_id = $1))
                   AND f.status = 'accepted'
              )
            )
          )
          AND NOT EXISTS (
            SELECT 1 FROM blocks b
             WHERE (b.blocker_id = $1 AND b.blocked_id = target.user_id)
                OR (b.blocker_id = target.user_id AND b.blocked_id = $1)
          )
        )
  `, [viewer, ids]);
  return new Set(result.rows.map((row) => String(row.user_id)));
}

async function areCallPeersBlocked(userId, targetId) {
  if (!userId || !targetId || userId === targetId) return true;
  if (!pool) return true;
  const key = `${userId}:${targetId}`;
  const cached = callBlockCache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.blocked;
  const result = await pool.query(
    `SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1`,
    [userId, targetId]
  );
  const blocked = Boolean(result.rowCount);
  callBlockCache.set(key, { blocked, expiresAt: Date.now() + CACHE_TTL_MS });
  return blocked;
}

function installCallPrivacyGuard() {
  const marker = "__fynxCallPrivacyGuard";
  if (WebSocket.prototype[marker]) return;
  WebSocket.prototype[marker] = true;
  const originalOn = WebSocket.prototype.on;
  WebSocket.prototype.on = function fynxCallPrivacyOn(event, listener) {
    if (event !== "message" || this.__fynxCallPrivacyMessageWrapped) return originalOn.call(this, event, listener);
    this.__fynxCallPrivacyMessageWrapped = true;
    const socket = this;
    return originalOn.call(this, event, (raw, ...rest) => {
      let body;
      try { body = JSON.parse(raw.toString()); } catch { return listener(raw, ...rest); }
      if (body?.type !== "call") return listener(raw, ...rest);

      const signalType = String(body?.signalType || "").trim().toLowerCase();
      if (!["invite", "accept", "reject", "end", "offer", "answer", "ice"].includes(signalType)) {
        return listener(raw, ...rest);
      }

      const viewerId = socket.__fynxRealtimeViewerId || "";
      const targetId = String(body?.toUserId || body?.targetUserId || "");
      if (!viewerId || !targetId || viewerId === targetId) return listener(raw, ...rest);

      const deny = () => {
        if (socket.readyState === 1) {
          socket.send(JSON.stringify({
            type: "call",
            callId: typeof body?.callId === "string" ? body.callId : undefined,
            signalType: "error",
            error: "calls are unavailable between blocked users"
          }));
        }
      };

      const key = `${viewerId}:${targetId}`;
      const cached = callBlockCache.get(key);
      if (cached && cached.expiresAt > Date.now()) {
        if (!cached.blocked) return listener(raw, ...rest);
        return deny();
      }

      if (!callBlockPending.has(key)) {
        const task = areCallPeersBlocked(viewerId, targetId).catch(() => true).finally(() => callBlockPending.delete(key));
        callBlockPending.set(key, task);
      }

      callBlockPending.get(key)
        .then((blocked) => {
          if (!blocked) listener(raw, ...rest);
          else deny();
        })
        .catch(deny);
    });
  };
}

export function installPresencePrivacyGuard() {
  const marker = "__fynxPresencePrivacyGuard";
  if (WebSocketServer.prototype[marker]) return;
  WebSocketServer.prototype[marker] = true;
  installCallPrivacyGuard();

  const originalEmit = WebSocketServer.prototype.emit;
  WebSocketServer.prototype.emit = function fynxPresenceEmit(event, socket, req, ...rest) {
    if (event === "connection" && socket && req && !socket.__fynxPresencePrivacyWrapped) {
      socket.__fynxPresencePrivacyWrapped = true;
      const viewerId = viewerIdFromRequest(req);
      socket.__fynxRealtimeViewerId = viewerId;
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
