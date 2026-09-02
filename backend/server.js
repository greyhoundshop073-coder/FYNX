import express from "express";
import http from "http";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";
import { WebSocketServer } from "ws";

const { Pool } = pg;
const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/realtime" });
app.use(express.json({ limit: "2mb" }));

const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET || "";
const DATABASE_URL = process.env.DATABASE_URL || "";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false }) : null;
const clientsByUserId = new Map();

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
      edited BOOLEAN NOT NULL DEFAULT FALSE,
      deleted BOOLEAN NOT NULL DEFAULT FALSE,
      reply_to_id BIGINT REFERENCES messages(id) ON DELETE SET NULL
    );
    CREATE INDEX IF NOT EXISTS messages_conversation_idx ON messages (sender_id, recipient_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS messages_recipient_idx ON messages (recipient_id, created_at DESC);
  `);
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
async function broadcastMessage(message) {
  const payload = JSON.stringify({ type: "message", message });
  for (const userId of [String(message.senderId), String(message.recipientId)]) {
    for (const socket of clientsByUserId.get(userId) || []) if (socket.readyState === 1) socket.send(payload);
  }
}

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
    if (password.length < 8) return res.status(400).json({ error: "password must be at least 8 characters" });
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

app.get("/api/messages/:username", auth, async (req, res) => {
  try {
    const other = await findUserByUsername(req.params.username.trim().toLowerCase());
    if (!other) return res.status(404).json({ error: "user not found" });
    const result = await pool.query(`SELECT m.id, m.sender_id, sender.username AS sender_username, m.recipient_id, recipient.username AS recipient_username, m.text, EXTRACT(EPOCH FROM m.created_at) * 1000 AS timestamp, m.edited, m.deleted, m.reply_to_id FROM messages m JOIN users sender ON sender.id = m.sender_id JOIN users recipient ON recipient.id = m.recipient_id WHERE (m.sender_id = $1 AND m.recipient_id = $2) OR (m.sender_id = $2 AND m.recipient_id = $1) ORDER BY m.created_at ASC LIMIT 200`, [req.user.sub, other.id]);
    return res.json({ messages: result.rows });
  } catch (error) { console.error("messages", error); return res.status(500).json({ error: "message history failed" }); }
});

app.post("/api/messages", auth, async (req, res) => {
  try {
    const recipientUsername = typeof req.body?.recipientUsername === "string" ? req.body.recipientUsername.trim().toLowerCase() : "";
    const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
    const replyToId = req.body?.replyToId == null ? null : Number(req.body.replyToId);
    if (!recipientUsername || !text || text.length > 4000) return res.status(400).json({ error: "valid recipientUsername and message text are required" });
    const recipient = await findUserByUsername(recipientUsername);
    if (!recipient) return res.status(404).json({ error: "recipient not found" });
    if (String(recipient.id) === String(req.user.sub)) return res.status(400).json({ error: "cannot message yourself" });
    const result = await pool.query("INSERT INTO messages (sender_id, recipient_id, text, reply_to_id) VALUES ($1, $2, $3, $4) RETURNING id, sender_id, recipient_id, text, EXTRACT(EPOCH FROM created_at) * 1000 AS timestamp, edited, deleted, reply_to_id", [req.user.sub, recipient.id, text, Number.isInteger(replyToId) ? replyToId : null]);
    const row = result.rows[0];
    const message = { id: String(row.id), senderId: String(row.sender_id), recipientId: String(row.recipient_id), text: row.text, timestamp: Number(row.timestamp), edited: row.edited, deleted: row.deleted, replyToId: row.reply_to_id == null ? null : String(row.reply_to_id) };
    await broadcastMessage(message); return res.status(201).json({ message });
  } catch (error) { console.error("send message", error); return res.status(500).json({ error: "message send failed" }); }
});

app.post("/api/assistant", auth, async (req, res) => {
  try {
    requireConfig("OPENAI_API_KEY", OPENAI_API_KEY);
    const message = typeof req.body?.message === "string" ? req.body.message.trim() : "";
    if (!message || message.length > 12000) return res.status(400).json({ error: "valid message is required" });
    const response = await fetch("https://api.openai.com/v1/responses", { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${OPENAI_API_KEY}` }, body: JSON.stringify({ model: OPENAI_MODEL, input: message }) });
    const data = await response.json();
    if (!response.ok) { console.error("openai", response.status, data?.error?.message || "request failed"); return res.status(502).json({ error: "AI service request failed" }); }
    return res.json({ reply: typeof data.output_text === "string" ? data.output_text : "" });
  } catch (error) { console.error("assistant", error); return res.status(503).json({ error: "AI service is not configured or unavailable" }); }
});

wss.on("connection", (socket, req) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || "localhost"}`); const token = url.searchParams.get("token") || "";
    if (!token || !JWT_SECRET) return socket.close(1008, "authentication required");
    const user = jwt.verify(token, JWT_SECRET); const userId = String(user.sub);
    const sockets = clientsByUserId.get(userId) || new Set(); sockets.add(socket); clientsByUserId.set(userId, sockets);
    socket.on("close", () => { sockets.delete(socket); if (sockets.size === 0) clientsByUserId.delete(userId); });
  } catch { socket.close(1008, "invalid token"); }
});

initDatabase().then(() => server.listen(PORT, "0.0.0.0", () => console.log(`FYNX backend listening on ${PORT}`))).catch((error) => { console.error("database initialization failed", error); process.exit(1); });
