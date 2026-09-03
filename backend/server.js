import express from "express";
import http from "http";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import pg from "pg";
import { WebSocketServer } from "ws";
import { registerSocialRoutes } from "./socialRoutes.js";

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

const PORT = Number(process.env.PORT || 10000);
const JWT_SECRET = process.env.JWT_SECRET || "";
const DATABASE_URL = process.env.DATABASE_URL || "";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const MAX_MEDIA_BYTES = 12 * 1024 * 1024;
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

app.post("/api/media", auth, async (req, res) => {
  try {
    requireConfig("DATABASE_URL", DATABASE_URL);
    const mimeType = typeof req.body?.mimeType === "string" ? req.body.mimeType.trim().toLowerCase() : "";
    const encoded = typeof req.body?.dataBase64 === "string" ? req.body.dataBase64 : "";
    if (!/^((image)\/(jpeg|png|webp|gif)|(video)\/(mp4|webm|quicktime)|(audio)\/(mp4|mpeg|aac|x-m4a|wav))$/.test(mimeType)) return res.status(400).json({ error: "unsupported media type" });
    if (!encoded || encoded.length > Math.ceil(MAX_MEDIA_BYTES * 1.38)) return res.status(413).json({ error: "media is too large" });
    const data = Buffer.from(encoded, "base64");
    if (!data.length || data.length > MAX_MEDIA_BYTES) return res.status(413).json({ error: "media is too large" });
    const result = await pool.query("INSERT INTO message_media (owner_id, mime_type, data, byte_size) VALUES ($1, $2, $3, $4) RETURNING id, mime_type, byte_size", [req.user.sub, mimeType, data, data.length]);
    return res.status(201).json({ media: { id: String(result.rows[0].id), mimeType: result.rows[0].mime_type, byteSize: result.rows[0].byte_size } });
  } catch (error) { console.error("media upload", error); return res.status(500).json({ error: "media upload failed" }); }
});

const statusSchema = async () => {
  await pool.query(`CREATE TABLE IF NOT EXISTS statuses (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('TEXT','PHOTO','VIDEO','VOICE')),
    text TEXT NOT NULL DEFAULT '',
    media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL,
    background_color BIGINT NOT NULL DEFAULT 4279308561,
    foreground_color BIGINT NOT NULL DEFAULT 4294967295,
    font TEXT NOT NULL DEFAULT 'CLASSIC',
    alignment INTEGER NOT NULL DEFAULT 1 CHECK (alignment BETWEEN 0 AND 2),
    private_status BOOLEAN NOT NULL DEFAULT FALSE,
    voice_duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
  ); CREATE INDEX IF NOT EXISTS statuses_owner_idx ON statuses(owner_id,created_at DESC); CREATE INDEX IF NOT EXISTS statuses_expiry_idx ON statuses(expires_at);`);
};

app.get("/api/statuses", auth, async (req, res) => {
  try {
    await statusSchema();
    const result = await pool.query(`SELECT s.id,s.owner_id,u.username,u.display_name,s.type,s.text,s.media_id,s.background_color,s.foreground_color,s.font,s.alignment,s.private_status,s.voice_duration_ms,EXTRACT(EPOCH FROM s.created_at)*1000 AS created_at,EXTRACT(EPOCH FROM s.expires_at)*1000 AS expires_at
      FROM statuses s JOIN users u ON u.id=s.owner_id
      WHERE s.expires_at > NOW() AND (s.owner_id=$1 OR s.private_status=FALSE OR EXISTS(
        SELECT 1 FROM friendships f WHERE ((f.user_id=s.owner_id AND f.friend_id=$1) OR (f.user_id=$1 AND f.friend_id=s.owner_id)) AND f.status='accepted'
      )) AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=s.owner_id) OR (b.blocker_id=s.owner_id AND b.blocked_id=$1))
      ORDER BY s.created_at DESC LIMIT 200`, [req.user.sub]);
    return res.json({ statuses: result.rows.map(row => ({ id:String(row.id), ownerUsername:row.username, ownerDisplayName:row.display_name, type:row.type, text:row.text || null, mediaId:row.media_id == null ? null : String(row.media_id), mediaUrl:row.media_id == null ? null : `/api/media/${row.media_id}`, backgroundColor:Number(row.background_color), foregroundColor:Number(row.foreground_color), font:row.font, alignment:Number(row.alignment), privateStatus:Boolean(row.private_status), voiceDurationMs:Number(row.voice_duration_ms), createdAtMillis:Number(row.created_at), expiresAtMillis:Number(row.expires_at) })) });
  } catch (error) { console.error("statuses", error); return res.status(500).json({ error:"status lookup failed" }); }
});

app.post("/api/statuses", auth, async (req, res) => {
  try {
    await statusSchema();
    const id = typeof req.body?.id === "string" ? req.body.id.trim() : "";
    const type = typeof req.body?.type === "string" ? req.body.type.trim().toUpperCase() : "";
    const text = typeof req.body?.text === "string" ? req.body.text.trim().slice(0,700) : "";
    const mediaId = req.body?.mediaId == null ? null : Number(req.body.mediaId);
    const backgroundColor = Number(req.body?.backgroundColor ?? 4279308561);
    const foregroundColor = Number(req.body?.foregroundColor ?? 4294967295);
    const font = typeof req.body?.font === "string" ? req.body.font.trim().slice(0,30) : "CLASSIC";
    const alignment = Number(req.body?.alignment ?? 1);
    const privateStatus = Boolean(req.body?.privateStatus);
    const voiceDurationMs = Number(req.body?.voiceDurationMs ?? 0);
    if (!id || !/^[0-9a-f-]{36}$/i.test(id) || !["TEXT","PHOTO","VIDEO","VOICE"].includes(type) || (type==="TEXT" && !text) || !Number.isFinite(backgroundColor) || !Number.isFinite(foregroundColor) || !Number.isInteger(alignment) || alignment<0 || alignment>2 || !Number.isFinite(voiceDurationMs) || voiceDurationMs<0 || voiceDurationMs>30000) return res.status(400).json({error:"invalid status"});
    if (type !== "TEXT" && (!Number.isInteger(mediaId) || mediaId < 1)) return res.status(400).json({error:"media is required"});
    if (mediaId != null) { const media=await pool.query("SELECT id FROM message_media WHERE id=$1 AND owner_id=$2",[mediaId,req.user.sub]); if(!media.rows[0]) return res.status(403).json({error:"media is not owned by this account"}); }
    const result=await pool.query("INSERT INTO statuses (id,owner_id,type,text,media_id,background_color,foreground_color,font,alignment,private_status,voice_duration_ms,expires_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,NOW()+INTERVAL '24 hours') RETURNING id,created_at,expires_at",[id,req.user.sub,type,text,mediaId,backgroundColor,foregroundColor,font,alignment,privateStatus,voiceDurationMs]);
    return res.status(201).json({status:{id:String(result.rows[0].id),createdAtMillis:new Date(result.rows[0].created_at).getTime(),expiresAtMillis:new Date(result.rows[0].expires_at).getTime()}});
  } catch (error) { if(error?.code==="23505") return res.status(409).json({error:"status already exists"}); console.error("status create",error); return res.status(500).json({error:"status creation failed"}); }
});

app.get("/api/media/:id", auth, async (req, res) => {
  try {
    const mediaId = Number(req.params.id);
    if (!Number.isInteger(mediaId) || mediaId < 1) return res.status(400).json({ error: "invalid media id" });
    const result = await pool.query(`SELECT mm.mime_type, mm.data FROM message_media mm LEFT JOIN messages m ON m.media_id = mm.id WHERE mm.id = $1 AND (mm.owner_id = $2 OR m.sender_id = $2 OR m.recipient_id = $2 OR EXISTS (SELECT 1 FROM statuses s WHERE s.media_id = mm.id AND s.expires_at > NOW() AND (s.owner_id = $2 OR s.private_status = FALSE OR EXISTS (SELECT 1 FROM friendships f WHERE ((f.user_id = s.owner_id AND f.friend_id = $2) OR (f.user_id = $2 AND f.friend_id = s.owner_id)) AND f.status = 'accepted')) AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id = $2 AND b.blocked_id = s.owner_id) OR (b.blocker_id = s.owner_id AND b.blocked_id = $2)))) ORDER BY m.id DESC LIMIT 1`, [mediaId, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "media not found" });
    res.set("Cache-Control", "private, max-age=3600");
    res.type(result.rows[0].mime_type);
    return res.send(result.rows[0].data);
  } catch (error) { console.error("media fetch", error); return res.status(500).json({ error: "media fetch failed" }); }
});

function messageProjection() {
  return `SELECT m.id, m.sender_id, sender.username AS sender_username, m.recipient_id, recipient.username AS recipient_username, m.text, EXTRACT(EPOCH FROM m.created_at) * 1000 AS timestamp, m.delivered_at IS NOT NULL AS delivered, m.read_at IS NOT NULL AS read, m.edited, m.deleted, m.reply_to_id, m.media_id, m.media_type, m.voice_duration_ms FROM messages m JOIN users sender ON sender.id = m.sender_id JOIN users recipient ON recipient.id = m.recipient_id`;
}
function rowToMessage(row) {
  return { id: String(row.id), senderId: String(row.sender_id), recipientId: String(row.recipient_id), text: row.text, timestamp: Number(row.timestamp), delivered: row.delivered, read: row.read, edited: row.edited, deleted: row.deleted, replyToId: row.reply_to_id == null ? null : String(row.reply_to_id), mediaId: row.media_id == null ? null : String(row.media_id), mediaType: row.media_type || null, mediaUrl: row.media_id == null ? null : `/api/media/${row.media_id}`, voiceDurationMs: Number(row.voice_duration_ms || 0) };
}

app.get("/api/messages/:username", auth, async (req, res) => {
  try {
    const other = await findUserByUsername(req.params.username.trim().toLowerCase());
    if (!other) return res.status(404).json({ error: "user not found" });
    const blocked = await pool.query(`SELECT 1 FROM blocks WHERE (blocker_id = $1 AND blocked_id = $2) OR (blocker_id = $2 AND blocked_id = $1) LIMIT 1`, [req.user.sub, other.id]);
    if (blocked.rowCount) return res.status(403).json({ error: "conversation unavailable" });
    const result = await pool.query(`${messageProjection()} WHERE (m.sender_id = $1 AND m.recipient_id = $2) OR (m.sender_id = $2 AND m.recipient_id = $1) ORDER BY m.created_at ASC LIMIT 200`, [req.user.sub, other.id]);
    return res.json({ messages: result.rows.map(rowToMessage) });
  } catch (error) { console.error("messages", error); return res.status(500).json({ error: "message history failed" }); }
});

app.post("/api/messages", auth, async (req, res) => {
  try {
    const recipientUsername = typeof req.body?.recipientUsername === "string" ? req.body.recipientUsername.trim().toLowerCase() : "";
    const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
    const replyToId = req.body?.replyToId == null ? null : Number(req.body.replyToId);
    const mediaId = req.body?.mediaId == null ? null : Number(req.body.mediaId);
    const mediaType = typeof req.body?.mediaType === "string" ? req.body.mediaType.trim().toLowerCase() : null;
    const voiceDurationMs = req.body?.voiceDurationMs == null ? 0 : Number(req.body.voiceDurationMs);
    if (!recipientUsername || text.length > 4000 || (!text && !Number.isInteger(mediaId))) return res.status(400).json({ error: "valid recipientUsername and message content are required" });
    if (mediaId != null && (!Number.isInteger(mediaId) || mediaId < 1)) return res.status(400).json({ error: "invalid media id" });
    if (mediaId != null && (!mediaType || !/^(image|video|audio)$/.test(mediaType))) return res.status(400).json({ error: "invalid media type" });
    if (!Number.isFinite(voiceDurationMs) || voiceDurationMs < 0 || voiceDurationMs > 120000) return res.status(400).json({ error: "invalid voice duration" });
    const recipient = await findUserByUsername(recipientUsername);
    if (!recipient || String(recipient.id) === String(req.user.sub)) return res.status(400).json({ error: "invalid recipient" });
    const blocked = await pool.query(`SELECT 1 FROM blocks WHERE (blocker_id = $1 AND blocked_id = $2) OR (blocker_id = $2 AND blocked_id = $1) LIMIT 1`, [req.user.sub, recipient.id]);
    if (blocked.rowCount) return res.status(403).json({ error: "conversation unavailable" });
    if (mediaId != null) {
      const media = await pool.query("SELECT id, mime_type FROM message_media WHERE id = $1 AND owner_id = $2", [mediaId, req.user.sub]);
      if (!media.rows[0]) return res.status(403).json({ error: "media is not owned by this account" });
    }
    if (replyToId != null) {
      if (!Number.isInteger(replyToId) || replyToId < 1) return res.status(400).json({ error: "invalid reply id" });
      const reply = await pool.query("SELECT id FROM messages WHERE id = $1 AND ((sender_id = $2 AND recipient_id = $3) OR (sender_id = $3 AND recipient_id = $2))", [replyToId, req.user.sub, recipient.id]);
      if (!reply.rows[0]) return res.status(400).json({ error: "invalid reply target" });
    }
    const result = await pool.query("INSERT INTO messages (sender_id, recipient_id, text, reply_to_id, media_id, media_type, voice_duration_ms) VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id, created_at", [req.user.sub, recipient.id, text, replyToId, mediaId, mediaType, voiceDurationMs]);
    const message = { id: String(result.rows[0].id), senderId: String(req.user.sub), recipientId: String(recipient.id), text, timestamp: new Date(result.rows[0].created_at).getTime(), delivered: false, read: false, edited: false, deleted: false, replyToId: replyToId == null ? null : String(replyToId), mediaId: mediaId == null ? null : String(mediaId), mediaType, mediaUrl: mediaId == null ? null : `/api/media/${mediaId}`, voiceDurationMs };
    await broadcastMessage(message);
    return res.status(201).json({ message });
  } catch (error) { console.error("send message", error); return res.status(500).json({ error: "message send failed" }); }
});

app.post("/api/messages/:id/read", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid message id" });
    const result = await pool.query("UPDATE messages SET read_at = NOW(), delivered_at = COALESCE(delivered_at, NOW()) WHERE id = $1 AND recipient_id = $2 AND deleted = FALSE RETURNING id, sender_id", [id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    broadcastToUser(result.rows[0].sender_id, { type: "message_status", messageId: String(id), status: "read" });
    return res.json({ ok: true });
  } catch (error) { console.error("message read", error); return res.status(500).json({ error: "read receipt failed" }); }
});

app.post("/api/messages/:id/delivered", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid message id" });
    const result = await pool.query("UPDATE messages SET delivered_at = COALESCE(delivered_at, NOW()) WHERE id = $1 AND recipient_id = $2 AND deleted = FALSE RETURNING id, sender_id", [id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    broadcastToUser(result.rows[0].sender_id, { type: "message_status", messageId: String(id), status: "delivered" });
    return res.json({ ok: true });
  } catch (error) { console.error("message delivered", error); return res.status(500).json({ error: "delivery receipt failed" }); }
});

app.patch("/api/messages/:id", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
    if (!Number.isInteger(id) || id < 1 || !text || text.length > 4000) return res.status(400).json({ error: "valid message text is required" });
    const result = await pool.query("UPDATE messages SET text = $1, edited = TRUE WHERE id = $2 AND sender_id = $3 AND deleted = FALSE RETURNING id, sender_id, recipient_id", [text, id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    const message = { id: String(id), senderId: String(result.rows[0].sender_id), recipientId: String(result.rows[0].recipient_id), text, edited: true };
    broadcastMessage(message);
    return res.json({ message });
  } catch (error) { console.error("message edit", error); return res.status(500).json({ error: "message edit failed" }); }
});

app.delete("/api/messages/:id", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid message id" });
    const result = await pool.query("UPDATE messages SET deleted = TRUE, text = '' WHERE id = $1 AND sender_id = $2 AND deleted = FALSE RETURNING id, sender_id, recipient_id", [id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    const message = { id: String(id), senderId: String(result.rows[0].sender_id), recipientId: String(result.rows[0].recipient_id), text: "", deleted: true };
    broadcastMessage(message);
    return res.json({ message });
  } catch (error) { console.error("message delete", error); return res.status(500).json({ error: "message delete failed" }); }
});

wss.on("connection", (socket, req) => {
  try {
    const url = new URL(req.url || "/realtime", `http://${req.headers.host || "localhost"}`);
    const token = url.searchParams.get("token") || "";
    if (!token || !JWT_SECRET) return socket.close(1008, "authentication required");
    const user = jwt.verify(token, JWT_SECRET);
    const userId = String(user.sub);
    if (!clientsByUserId.has(userId)) clientsByUserId.set(userId, new Set());
    clientsByUserId.get(userId).add(socket);
    broadcastPresence(userId, true);
    markPendingDelivered(userId).catch((error) => console.error("deliver pending", error));
    socket.on("close", () => {
      const sockets = clientsByUserId.get(userId);
      if (!sockets) return;
      sockets.delete(socket);
      if (!sockets.size) { clientsByUserId.delete(userId); broadcastPresence(userId, false); }
    });
  } catch { socket.close(1008, "invalid token"); }
});

registerSocialRoutes(app, { pool, auth, findUserByUsername });

initDatabase().catch((error) => { console.error("database initialization failed", error); process.exitCode = 1; });

server.listen(PORT, "0.0.0.0", () => console.log(`FYNX backend listening on ${PORT}`));
