import jwt from "jsonwebtoken";
import { WebSocketServer } from "ws";
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { installSocialFeed } from "./socialFeedBootstrap.js";
import { installHomeCommentPrivacy } from "./homeCommentsPrivacyBootstrap.js";

// R3 connection isolation layer. server.js is intentionally kept intact; this
// wrapper makes the existing WebSocket connection handler reject stale/replaced
// sockets before its message listeners can process or deliver realtime traffic.
const currentSocketByUserId = new Map();
const realtimeReadRate = new Map();
const realtimeAckRate = new Map();
const RATE_WINDOW_MS = 60_000;
const READ_RATE_LIMIT = 120;
const ACK_RATE_LIMIT = 240;
const MAX_READ_IDS = 100;
const MAX_PACKET_BYTES = 64 * 1024;
const originalServerOn = WebSocketServer.prototype.on;

function authenticatedUserId(req) {
  const secret = process.env.JWT_SECRET || "";
  if (!secret) return null;
  try {
    const url = new URL(req?.url || "/realtime", `http://${req?.headers?.host || "localhost"}`);
    const token = url.searchParams.get("token") || "";
    if (!token) return null;
    const user = jwt.verify(token, secret);
    const userId = String(user?.sub || "");
    return /^\d+$/.test(userId) && userId.length <= 20 ? userId : null;
  } catch {
    return null;
  }
}

function allowRate(map, userId, limit) {
  const now = Date.now();
  const current = map.get(userId);
  if (!current || now - current.startedAt >= RATE_WINDOW_MS) {
    map.set(userId, { startedAt: now, count: 1 });
    return true;
  }
  if (current.count >= limit) return false;
  current.count += 1;
  return true;
}

function validReadOrAckPacket(raw, userId) {
  if (Buffer.byteLength(raw.toString(), "utf8") > MAX_PACKET_BYTES) return false;
  let body;
  try { body = JSON.parse(raw.toString()); } catch { return true; }
  if (!body || typeof body !== "object" || Array.isArray(body)) return true;
  if (body.type === "message_ack") {
    const id = Number(body.messageId);
    return Number.isSafeInteger(id) && id > 0 && allowRate(realtimeAckRate, userId, ACK_RATE_LIMIT);
  }
  if (body.type === "read") {
    if (!Array.isArray(body.messageIds) || body.messageIds.length > MAX_READ_IDS) return false;
    if (body.messageIds.some(value => {
      const id = Number(value);
      return !Number.isSafeInteger(id) || id <= 0;
    })) return false;
    return allowRate(realtimeReadRate, userId, READ_RATE_LIMIT);
  }
  return true;
}

setInterval(() => {
  const cutoff = Date.now() - RATE_WINDOW_MS;
  for (const map of [realtimeReadRate, realtimeAckRate]) {
    for (const [key, value] of map) if (value.startedAt < cutoff) map.delete(key);
  }
}, RATE_WINDOW_MS).unref();

WebSocketServer.prototype.on = function on(event, listener) {
  if (event !== "connection") return originalServerOn.call(this, event, listener);

  return originalServerOn.call(this, event, (socket, req, ...rest) => {
    const userId = authenticatedUserId(req);
    if (!userId) return listener(socket, req, ...rest);

    const previous = currentSocketByUserId.get(userId);
    if (previous && previous !== socket) {
      previous.__fynxStale = true;
      try { previous.close(4001, "replaced realtime session"); } catch {}
    }
    currentSocketByUserId.set(userId, socket);
    socket.__fynxStale = false;

    const originalSend = socket.send.bind(socket);
    socket.send = (...args) => {
      if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale) return;
      return originalSend(...args);
    };

    const originalSocketOn = socket.on.bind(socket);
    socket.on = (socketEvent, callback) => {
      if (socketEvent === "message") {
        return originalSocketOn("message", (data, ...args) => {
          if (currentSocketByUserId.get(userId) !== socket || socket.__fynxStale || socket.readyState !== 1) return;
          if (!validReadOrAckPacket(data, userId)) return;
          return callback(data, ...args);
        });
      }
      if (socketEvent === "close") {
        return originalSocketOn("close", (...args) => {
          if (currentSocketByUserId.get(userId) === socket) currentSocketByUserId.delete(userId);
          return callback(...args);
        });
      }
      return originalSocketOn(socketEvent, callback);
    };

    return listener(socket, req, ...rest);
  });
};

// Home Batch 4B is installed as a startup transformation so the existing
// production route module remains the single source of social APIs. The patch
// is idempotent and only adds missing schema/routes; it never replaces the
// existing comments endpoints used by older clients.
async function installHomeCommentBackend() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  let source = await readFile(socialPath, "utf8");
  if (source.includes("fynxHomeCommentsBatch4b")) return;

  const schemaNeedle = "CREATE INDEX IF NOT EXISTS social_post_comments_post_idx ON social_post_comments(post_id, created_at ASC);";
  const schemaPatch = `${schemaNeedle}\n        ALTER TABLE social_post_comments ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT REFERENCES social_post_comments(id) ON DELETE CASCADE;\n        CREATE INDEX IF NOT EXISTS social_post_comments_parent_idx ON social_post_comments(post_id, parent_comment_id, created_at ASC);`;
  if (!source.includes(schemaNeedle)) throw new Error("Batch 4B could not locate social comment schema marker");
  source = source.replace(schemaNeedle, schemaPatch);

  const routeMarker = "  app.post('/api/social/follow/:username'";
  const routes = `
  // fynxHomeCommentsBatch4b: paginated comments and validated replies.
  app.get('/api/social/posts/:id/comments/page', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      const postId = Number(req.params.id);
      if (!Number.isSafeInteger(postId) || postId < 1) return res.status(400).json({ error: 'invalid post id' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const requestedLimit = Number(req.query?.limit);
      const limit = Math.min(Math.max(Number.isInteger(requestedLimit) ? requestedLimit : 50, 1), 100);
      const before = req.query?.before == null || req.query.before === '' ? null : Number(req.query.before);
      if (before !== null && (!Number.isSafeInteger(before) || before < 1)) return res.status(400).json({ error: 'invalid comment cursor' });
      const cursorClause = before === null ? '' : ' AND c.id < $3';
      const result = await pool.query(
        `SELECT c.id,c.post_id,c.parent_comment_id,c.text,EXTRACT(EPOCH FROM c.created_at)*1000 AS timestamp,
                u.id AS author_id,u.username,u.display_name
           FROM social_post_comments c
           JOIN users u ON u.id=c.author_id
          WHERE c.post_id=$1
            AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$2))${cursorClause}
          ORDER BY c.id DESC
          LIMIT $4`,
        before === null ? [postId, req.user.sub, limit + 1] : [postId, req.user.sub, limit + 1, before]
      );
      const rows = result.rows.slice(0, limit).reverse();
      const nextCursor = result.rows.length > limit ? String(result.rows[result.rows.length - 1].id) : null;
      return res.json({ comments: rows.map(row => ({ id: String(row.id), postId: String(row.post_id), parentCommentId: row.parent_comment_id == null ? null : String(row.parent_comment_id), text: row.text, timestamp: Number(row.timestamp), authorId: String(row.author_id), authorUsername: row.username, authorDisplayName: row.display_name })), nextCursor });
    } catch (error) {
      console.error('social comments page', error);
      return res.status(500).json({ error: 'comments page failed' });
    }
  });

  app.post('/api/social/posts/:id/comments/:commentId/replies', auth, async (req, res) => {
    const client = await pool.connect();
    try {
      await ensureSocialSchema();
      const postId = Number(req.params.id);
      const parentId = Number(req.params.commentId);
      const text = typeof req.body?.text === 'string' ? req.body.text.trim() : '';
      if (!Number.isSafeInteger(postId) || postId < 1 || !Number.isSafeInteger(parentId) || parentId < 1) return res.status(400).json({ error: 'invalid comment reference' });
      if (!text || text.length > 1000) return res.status(400).json({ error: 'reply text must be 1-1000 characters' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const parent = await pool.query(`SELECT c.id,c.post_id,c.parent_comment_id,c.author_id FROM social_post_comments c JOIN users u ON u.id=c.author_id WHERE c.id=$1 AND c.post_id=$2 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3))`, [parentId, postId, req.user.sub]);
      if (!parent.rows[0]) return res.status(404).json({ error: 'parent comment not found' });
      const rootId = parent.rows[0].parent_comment_id == null ? parentId : Number(parent.rows[0].parent_comment_id);
      const author = await pool.query('SELECT id,username,display_name FROM users WHERE id=$1', [req.user.sub]);
      if (!author.rows[0]) return res.status(401).json({ error: 'author not found' });
      await client.query('BEGIN');
      const inserted = await client.query(`INSERT INTO social_post_comments(post_id,author_id,parent_comment_id,text) VALUES($1,$2,$3,$4) RETURNING id,post_id,parent_comment_id,text,EXTRACT(EPOCH FROM created_at)*1000 AS timestamp`, [postId, req.user.sub, rootId, text]);
      await client.query('COMMIT');
      const row = inserted.rows[0];
      return res.status(201).json({ comment: { id: String(row.id), postId: String(row.post_id), parentCommentId: String(row.parent_comment_id), text: row.text, timestamp: Number(row.timestamp), authorId: String(req.user.sub), authorUsername: author.rows[0].username || '', authorDisplayName: author.rows[0].display_name || '' } });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('social comment reply', error);
      return res.status(500).json({ error: 'reply failed' });
    } finally { client.release(); }
  });

  app.get('/api/social/posts/:id/comments/:commentId/replies', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      const postId = Number(req.params.id);
      const parentId = Number(req.params.commentId);
      if (!Number.isSafeInteger(postId) || postId < 1 || !Number.isSafeInteger(parentId) || parentId < 1) return res.status(400).json({ error: 'invalid comment reference' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const parent = await pool.query(`SELECT c.id FROM social_post_comments c WHERE c.id=$1 AND c.post_id=$2 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3))`, [parentId, postId, req.user.sub]);
      if (!parent.rows[0]) return res.status(404).json({ error: 'parent comment not found' });
      const requestedLimit = Number(req.query?.limit);
      const limit = Math.min(Math.max(Number.isInteger(requestedLimit) ? requestedLimit : 50, 1), 100);
      const result = await pool.query(`SELECT c.id,c.post_id,c.parent_comment_id,c.text,EXTRACT(EPOCH FROM c.created_at)*1000 AS timestamp, u.id AS author_id,u.username,u.display_name FROM social_post_comments c JOIN users u ON u.id=c.author_id WHERE c.post_id=$1 AND c.parent_comment_id=$2 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3)) ORDER BY c.id ASC LIMIT $4`, [postId, parentId, req.user.sub, limit]);
      return res.json({ comments: result.rows.map(row => ({ id: String(row.id), postId: String(row.post_id), parentCommentId: row.parent_comment_id == null ? null : String(row.parent_comment_id), text: row.text, timestamp: Number(row.timestamp), authorId: String(row.author_id), authorUsername: row.username, authorDisplayName: row.display_name })) });
    } catch (error) {
      console.error('social comment replies', error);
      return res.status(500).json({ error: 'replies lookup failed' });
    }
  });
  `;
  if (!source.includes(routeMarker)) throw new Error("Batch 4B could not locate social follow route marker");
  source = source.replace(routeMarker, routes + routeMarker);
  await writeFile(socialPath, source);
}

// Install the base comments routes first, then apply the privacy hardening to
// that same route source. This preserves the real production entrypoint and
// also works correctly on a clean deployment where Batch 4B has not yet run.
await installSocialFeed();
await installHomeCommentBackend();
await installHomeCommentPrivacy();
await import("./serverBootstrap.js");