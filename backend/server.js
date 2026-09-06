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
const clientsByUserId = new Map();

// Lightweight per-process abuse protection. Production deployments should also enforce
// rate limits at the edge/load-balancer so limits remain effective across instances.
const rateBuckets = new Map();
const RATE_WINDOW_MS = 60_000;
const RATE_LIMITS = { auth: 20, assistant: 20, media: 30, messages: 120 };
const MAX_RATE_BUCKETS = 20_000;
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
function issueToken(user) {
  requireConfig("JWT_SECRET", JWT_SECRET);
  return jwt.sign({ sub: String(user.id), username: user.username }, JWT_SECRET, { expiresIn: "30d" });
}
function auth(req, res, next) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) return res.status(401).json({ error: "authentication required" });
  try { req.user = jwt.verify(token, JWT_SECRET); return next(); } catch { return res.status(401).json({ error: "invalid or expired token" }); }
}
async function findUserByUsername(username) {
  const result = await pool.query("SELECT id, username, display_name, phone, created_at FROM users WHERE username = $1", [username]);
  return result.rows[0] || null;
}
function sendSocket(socket, payload) { if (socket.readyState === 1) socket.send(JSON.stringify(payload)); }
function broadcastToUser(userId, payload) { for (const socket of clientsByUserId.get(String(userId)) || []) sendSocket(socket, payload); }
async function broadcastMessage(message) {
  const payload = { type: "message", message };
  broadcastToUser(message.senderId, payload);
  broadcastToUser(message.recipientId, payload);
}
function broadcastPresence(userId, online) {
  const payload = { type: "presence", userId: String(userId), online };
  for (const sockets of clientsByUserId.values()) for (const socket of sockets) sendSocket(socket, payload);
}
async function markPendingDelivered(userId) {
  if (!pool) return;
  const result = await pool.query(`UPDATE messages SET delivered_at = NOW() WHERE recipient_id = $1 AND delivered_at IS NULL AND deleted = FALSE RETURNING id, sender_id, recipient_id`, [userId]);
  for (const row of result.rows) broadcastToUser(row.sender_id, { type: "message_status", messageId: String(row.id), status: "delivered" });
}

app.get("/ready", async (_req, res) => {
  if (!pool) return res.status(503).json({ ok: false, service: "fynx-backend", database: "not-configured" });
  try { await pool.query("SELECT 1"); return res.status(200).json({ ok: true, service: "fynx-backend", database: "ready" }); }
  catch { return res.status(503).json({ ok: false, service: "fynx-backend", database: "unavailable" }); }
});

app.get("/health", async (_req, res) => {
  let database = "not-configured";
  if (pool) { try { await pool.query("SELECT 1"); database = "ready"; } catch { database = "unavailable"; } }
  res.status(database === "unavailable" ? 503 : 200).json({ ok: database !== "unavailable", service: "fynx-backend", database });
});

app.post("/api/auth/register", async (req, res) => {
  try {
    requireConfig("DATABASE_URL", DATABASE_URL); requireConfig("JWT_SECRET", JWT_SECRET);
    const username = typeof req.body?.username === "string" ? req.body.username.trim().toLowerCase() : "";
    const password = typeof req.body?.password === "string" ? req.body.password : "";
    const displayName = typeof req.body?.displayName === "string" ? req.body.displayName.trim().slice(0, 80) : "";
    const phone = typeof req.body?.phone === "string" ? req.body.phone.trim().slice(0, 30) : "";
    if (!/^[a-z0-9_]{3,32}$/.test(username)) return res.status(400).json({ error: "username must be 3-32 characters using letters, numbers, or underscore" });
    if (password.length < 8 || password.length > 128) return res.status(400).json({ error: "password must be 8-128 characters" });
    if (displayName.length < 2) return res.status(400).json({ error: "display name is required" });
    const passwordHash = await bcrypt.hash(password, 12);
    const result = await pool.query("INSERT INTO users (username, password_hash, display_name, phone) VALUES ($1, $2, $3, $4) RETURNING id, username, display_name, phone, created_at", [username, passwordHash, displayName, phone]);
    const user = result.rows[0];
    return res.status(201).json({ user, accessToken: issueToken(user) });
  } catch (error) {
    if (error?.code === "23505") return res.status(409).json({ error: "username already exists" });
    console.error("register", error); return res.status(500).json({ error: "registration failed" });
  }
});

app.post("/api/auth/login", async (req, res) => {
  try {
    requireConfig("DATABASE_URL", DATABASE_URL); requireConfig("JWT_SECRET", JWT_SECRET);
    const username = typeof req.body?.username === "string" ? req.body.username.trim().toLowerCase() : "";
    const password = typeof req.body?.password === "string" ? req.body.password : "";
    if (password.length > 128) return res.status(400).json({ error: "invalid username or password" });
    const result = await pool.query("SELECT id, username, password_hash, display_name, phone, created_at FROM users WHERE username = $1", [username]);
    const user = result.rows[0];
    if (!user || !(await bcrypt.compare(password, user.password_hash))) return res.status(401).json({ error: "invalid username or password" });
    return res.json({ user: { id: user.id, username: user.username, display_name: user.display_name, phone: user.phone, created_at: user.created_at }, accessToken: issueToken(user) });
  } catch (error) { console.error("login", error); return res.status(500).json({ error: "login failed" }); }
});

app.get("/api/me", auth, async (req, res) => {
  try {
    const user = await pool.query("SELECT id, username, display_name, phone, created_at FROM users WHERE id = $1", [req.user.sub]);
    if (!user.rows[0]) return res.status(404).json({ error: "user not found" });
    return res.json({ user: user.rows[0] });
  } catch (error) { console.error("me", error); return res.status(500).json({ error: "request failed" }); }
});


app.post("/api/advertising/ai-advice", auth, async (req, res) => {
  try {
    requireConfig("OPENAI_API_KEY", OPENAI_API_KEY);
    const request = typeof req.body?.request === "string" ? req.body.request.trim() : "";
    if (!request || request.length > 3000) return res.status(400).json({ error: "valid advertising request is required" });
    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: { "Authorization": `Bearer ${OPENAI_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: OPENAI_MODEL,
        instructions: "You are FYNX AI Advertising Coach. Help a business write clear, honest adverts and choose sensible audience and budget options. Never promise guaranteed results. Never invent FYNX campaign data. Do not expose private user data or secrets. Never perform or claim to perform payments, campaign activation, refunds, or other account actions.",
        input: request,
        store: false
      })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) return res.status(502).json({ error: "AI advertising advice unavailable" });
    const reply = typeof data?.output_text === "string" ? data.output_text.trim() : "";
    if (!reply) return res.status(502).json({ error: "AI advertising advice unavailable" });
    return res.json({ reply });
  } catch (error) { console.error("ai-advice", error); return res.status(500).json({ error: "AI advertising advice unavailable" }); }
});

export { app, server, pool, auth, findUserByUsername, sendSocket, broadcastToUser, broadcastMessage, broadcastPresence, markPendingDelivered, MAX_MEDIA_BYTES, PORT };
