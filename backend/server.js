import express from "express";
import http from "http";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";
import { WebSocketServer } from "ws";
import { registerSocialRoutes } from "./socialRoutes.js";
import { registerMarketplaceTransactionRoutes } from "./marketplaceTransactions.js";
import { registerMarketplaceReputationRoutes } from "./marketplaceReputation.js";
import { registerMarketplaceCompletionRoutes } from "./marketplaceCompletion.js";
import { registerMoneyPlannerRoutes } from "./moneyPlanner.js";
import { registerMarketplaceAdvertisingRoutes } from "./marketplaceAdvertising.js";

const { Pool } = pg;
const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/realtime" });

// Keep the API deliberately strict: large media is uploaded separately and authenticated.
app.disable("x-powered-by");
app.use((req, res, next) => {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("X-Frame-Options", "DENY");
  if (process.env.NODE_ENV === "production") res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  next();
});
app.use(express.json({ limit: "18mb", strict: true }));
app.use((req, res, next) => {
  if (req.method !== "GET" && req.method !== "HEAD") res.setHeader("Cache-Control", "no-store");
  next();
});
app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));
app.use("/api/assistant", rateLimit("assistant", RATE_LIMITS.assistant));
app.use("/api/media", rateLimit("media", RATE_LIMITS.media));
app.use("/api/messages", rateLimit("messages", RATE_LIMITS.messages));

const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET || "";
const DATABASE_URL = process.env.DATABASE_URL || "";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const MAX_MEDIA_BYTES = 12 * 1024 * 1024;
const RATE_WINDOW_MS = 60_000;
const RATE_LIMITS = { auth: 20, assistant: 20, media: 30, messages: 120, general: 240 };
const MAX_RATE_BUCKETS = 20_000;
const rateBuckets = new Map();
const responseCache = new Map();
const CACHE_TTL_MS = 15_000;
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: Number(process.env.DB_POOL_MAX || 20),
  min: Number(process.env.DB_POOL_MIN || 2),
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;
globalThis.__fynxPool = pool;
const clientsByUserId = new Map();
const activeCalls = new Map();

function cacheGet(key) { const hit = responseCache.get(key); if (!hit || hit.expiresAt <= Date.now()) { responseCache.delete(key); return null; } return hit.value; }
function cacheSet(key, value, ttl = CACHE_TTL_MS) { if (responseCache.size >= 1_000) { const first = responseCache.keys().next().value; if (first) responseCache.delete(first); } responseCache.set(key, { value, expiresAt: Date.now() + ttl }); }
function rateLimit(bucket, limit) {
  return (req, res, next) => {
    const key = `${bucket}:${req.ip || req.socket.remoteAddress || "unknown"}:${req.user?.sub || "anonymous"}`;
    if (!rateBuckets.has(key) && rateBuckets.size >= MAX_RATE_BUCKETS) return res.status(503).json({ error: "server is temporarily protecting capacity" });
    const now = Date.now();
    const current = rateBuckets.get(key);
    if (!current || now - current.startedAt >= RATE_WINDOW_MS) {
      rateBuckets.set(key, { startedAt: now, count: 1 });
      return next();
    }
    current.count += 1;
    if (current.count > limit) {
      res.setHeader("Retry-After", "60");
      return res.status(429).json({ error: "too many requests" });
    }
    return next();
  };
}
setInterval(() => {
  const cutoff = Date.now() - RATE_WINDOW_MS;
  for (const [key, value] of rateBuckets) if (value.startedAt < cutoff) rateBuckets.delete(key);
}, RATE_WINDOW_MS).unref();
setInterval(() => { const now = Date.now(); for (const [key, value] of responseCache) if (value.expiresAt <= now) responseCache.delete(key); }, CACHE_TTL_MS).unref();

async function initDatabase() {
  if (!pool) return;
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id BIGSERIAL PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      display_name TEXT NOT NULL DEFAULT '',
      phone TEXT NOT NULL DEFAULT '',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    ALTER TABLE users ADD COLUMN IF NOT EXISTS display_name TEXT NOT NULL DEFAULT '';
    ALTER TABLE users ADD COLUMN IF NOT EXISTS phone TEXT NOT NULL DEFAULT '';
    CREATE TABLE IF NOT EXISTS messages (
      id BIGSERIAL PRIMARY KEY,
      sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      recipient_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      text TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      delivered_at TIMESTAMPTZ,
      read_at TIMESTAMPTZ,
      edited BOOLEAN NOT NULL DEFAULT FALSE,
      deleted BOOLEAN NOT NULL DEFAULT FALSE,
      reply_to_id BIGINT REFERENCES messages(id) ON DELETE SET NULL
    );
    ALTER TABLE messages ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;
    ALTER TABLE messages ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;
    ALTER TABLE messages ADD COLUMN IF NOT EXISTS media_id BIGINT;
    ALTER TABLE messages ADD COLUMN IF NOT EXISTS media_type TEXT;
    ALTER TABLE messages ADD COLUMN IF NOT EXISTS voice_duration_ms BIGINT NOT NULL DEFAULT 0;
    CREATE TABLE IF NOT EXISTS message_media (
      id BIGSERIAL PRIMARY KEY,
      owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      mime_type TEXT NOT NULL,
      data BYTEA NOT NULL,
      byte_size INTEGER NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS message_media_owner_idx ON message_media (owner_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS messages_conversation_idx ON messages (sender_id, recipient_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS messages_recipient_idx ON messages (recipient_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS messages_delivery_idx ON messages (recipient_id, delivered_at, created_at DESC);
    CREATE TABLE IF NOT EXISTS friendships (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      friend_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      status TEXT NOT NULL CHECK (status IN ('pending', 'accepted')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE (user_id, friend_id)
    );
    CREATE INDEX IF NOT EXISTS friendships_user_idx ON friendships (user_id, status);
    CREATE INDEX IF NOT EXISTS friendships_friend_idx ON friendships (friend_id, status);
    CREATE TABLE IF NOT EXISTS blocks (
      blocker_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      blocked_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (blocker_id, blocked_id),
      CHECK (blocker_id <> blocked_id)
    );
    CREATE INDEX IF NOT EXISTS blocks_blocked_idx ON blocks (blocked_id);
  `);
  await pool.query(`DO $$ BEGIN ALTER TABLE messages ADD CONSTRAINT messages_media_fk FOREIGN KEY (media_id) REFERENCES message_media(id) ON DELETE SET NULL; EXCEPTION WHEN duplicate_object THEN NULL; END $$;`);
}

function requireConfig(name, value) { if (!value) throw new Error(`${name} is not configured.`); }
function issueToken(user) { requireConfig("JWT_SECRET", JWT_SECRET); return jwt.sign({ sub: String(user.id), username: user.username }, JWT_SECRET, { expiresIn: "30d" }); }
function auth(req, res, next) { const header = req.get("authorization") || ""; const token = header.startsWith("Bearer ") ? header.slice(7).trim() : ""; if (!token || !JWT_SECRET) return res.status(401).json({ error: "authentication required" }); try { req.user = jwt.verify(token, JWT_SECRET); return next(); } catch { return res.status(401).json({ error: "invalid or expired token" }); } }
async function findUserByUsername(username) { const result = await pool.query("SELECT id, username, display_name, phone, created_at FROM users WHERE username = $1", [username]); return result.rows[0] || null; }
function sendSocket(socket, payload) { if (socket.readyState === 1) socket.send(JSON.stringify(payload)); }
function broadcastToUser(userId, payload) { for (const socket of clientsByUserId.get(String(userId)) || []) sendSocket(socket, payload); }
async function broadcastMessage(message) { const payload = { type: "message", message }; broadcastToUser(message.senderId, payload); broadcastToUser(message.recipientId, payload); }
function broadcastPresence(userId, online) { const payload = { type: "presence", userId: String(userId), online }; for (const sockets of clientsByUserId.values()) for (const socket of sockets) sendSocket(socket, payload); }
async function markPendingDelivered(userId) { if (!pool) return; const result = await pool.query(`UPDATE messages SET delivered_at = NOW() WHERE recipient_id = $1 AND delivered_at IS NULL AND deleted = FALSE RETURNING id, sender_id, recipient_id`, [userId]); for (const row of result.rows) broadcastToUser(row.sender_id, { type: "message_status", messageId: String(row.id), status: "delivered" }); }
function newCallId() { return `call_${Date.now().toString(36)}_${Math.random().toString(36).slice(2,10)}`; }
function callPeer(call, userId) { return String(call.callerId) === String(userId) ? String(call.calleeId) : String(call.callerId); }
function validCallId(value) { return typeof value === "string" && /^call_[a-z0-9_]+$/i.test(value) && value.length <= 80; }
function callMessage(call, userId, type, extra = {}) { return { type: "call", callId: call.id, callType: call.mediaType, fromUserId: String(userId), toUserId: callPeer(call, userId), signalType: type, ...extra }; }
function closeCall(callId, reason, notify = true) { const call = activeCalls.get(callId); if (!call) return; activeCalls.delete(callId); if (notify) { broadcastToUser(call.callerId, { type: "call", callId, callType: call.mediaType, fromUserId: call.calleeId, toUserId: call.callerId, signalType: reason }); broadcastToUser(call.calleeId, { type: "call", callId, callType: call.mediaType, fromUserId: call.callerId, toUserId: call.calleeId, signalType: reason }); } }
function relayCallSignal(call, senderId, signalType, payload) { const targetId = callPeer(call, senderId); const message = callMessage(call, senderId, signalType, payload); broadcastToUser(targetId, message); }
function validateSignalPayload(signalType, body) { if (signalType === "offer" || signalType === "answer") { if (typeof body?.sdp !== "string" || body.sdp.length < 1 || body.sdp.length > 200_000) return null; return { sdp: body.sdp }; } if (signalType === "ice") { const candidate = body?.candidate; if (!candidate || typeof candidate !== "object" || typeof candidate.candidate !== "string" || candidate.candidate.length > 20_000) return null; return { candidate: { candidate: candidate.candidate, sdpMid: candidate.sdpMid == null ? null : String(candidate.sdpMid).slice(0, 100), sdpMLineIndex: candidate.sdpMLineIndex == null ? null : Number(candidate.sdpMLineIndex), usernameFragment: candidate.usernameFragment == null ? null : String(candidate.usernameFragment).slice(0, 200) } }; } return null; }

app.get("/ready", async (_req, res) => {