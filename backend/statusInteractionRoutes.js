import pg from 'pg';
import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 4,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  keepAlive: true
}) : null;

let schemaReady;
async function ensureSchema() {
  if (!pool) return;
  if (!schemaReady) {
    schemaReady = (async () => {
      await pool.query(`
        CREATE TABLE IF NOT EXISTS status_views (
          status_id UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
          viewer_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (status_id, viewer_id)
        );
        CREATE TABLE IF NOT EXISTS status_likes (
          status_id UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (status_id, user_id)
        );
        CREATE TABLE IF NOT EXISTS status_reactions (
          status_id UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          reaction TEXT NOT NULL CHECK (char_length(reaction) BETWEEN 1 AND 32),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (status_id, user_id)
        );
        CREATE TABLE IF NOT EXISTS status_replies (
          id UUID PRIMARY KEY,
          status_id UUID NOT NULL REFERENCES statuses(id) ON DELETE CASCADE,
          sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS status_views_status_idx ON status_views(status_id);
        CREATE INDEX IF NOT EXISTS status_likes_status_idx ON status_likes(status_id);
        CREATE INDEX IF NOT EXISTS status_reactions_status_idx ON status_reactions(status_id);
        CREATE INDEX IF NOT EXISTS status_replies_status_idx ON status_replies(status_id, created_at);
      `);
    })().catch(error => { schemaReady = null; throw error; });
  }
  await schemaReady;
}

export function registerStatusInteractionRoutes({ app }) {
  if (!pool) return;
  const auth = (req, res, next) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
    try { req.user = jwt.verify(token, JWT_SECRET); return next(); }
    catch { return res.status(401).json({ error: 'invalid or expired token' }); }
  };
  const statusId = req => String(req.params?.statusId || '').trim();
  const validId = id => /^[0-9a-f-]{36}$/i.test(id);

  async function visibleStatus(id, userId) {
    if (!validId(id)) return null;
    const result = await pool.query(`
      SELECT s.id, s.owner_id
      FROM statuses s
      WHERE s.id=$1 AND s.expires_at > NOW()
        AND (s.owner_id=$2 OR s.audience='EVERYONE' OR (s.audience='FRIENDS' AND EXISTS(
          SELECT 1 FROM friendships f
          WHERE ((f.user_id=s.owner_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=s.owner_id))
            AND f.status='accepted'
        )))
        AND NOT EXISTS (
          SELECT 1 FROM blocks b
          WHERE (b.blocker_id=$2 AND b.blocked_id=s.owner_id) OR (b.blocker_id=s.owner_id AND b.blocked_id=$2)
        )
      LIMIT 1`, [id, userId]);
    return result.rows[0] || null;
  }

  app.get('/api/statuses/:statusId/interactions', auth, async (req, res) => {
    try {
      await ensureSchema();
      const id = statusId(req);
      const status = await visibleStatus(id, req.user.sub);
      if (!status) return res.status(404).json({ error: 'status not found or not visible' });
      const [views, likes, reactions, replies] = await Promise.all([
        pool.query('SELECT COUNT(*)::int AS count FROM status_views WHERE status_id=$1', [id]),
        pool.query('SELECT COUNT(*)::int AS count FROM status_likes WHERE status_id=$1', [id]),
        pool.query('SELECT reaction, COUNT(*)::int AS count FROM status_reactions WHERE status_id=$1 GROUP BY reaction ORDER BY count DESC', [id]),
        pool.query('SELECT COUNT(*)::int AS count FROM status_replies WHERE status_id=$1', [id])
      ]);
      const mineLike = await pool.query('SELECT 1 FROM status_likes WHERE status_id=$1 AND user_id=$2 LIMIT 1', [id, req.user.sub]);
      const mineReaction = await pool.query('SELECT reaction FROM status_reactions WHERE status_id=$1 AND user_id=$2 LIMIT 1', [id, req.user.sub]);
      return res.json({
        statusId: id,
        viewCount: views.rows[0].count,
        likeCount: likes.rows[0].count,
        replyCount: replies.rows[0].count,
        reactionCounts: Object.fromEntries(reactions.rows.map(row => [row.reaction, row.count])),
        likedByMe: Boolean(mineLike.rows[0]),
        myReaction: mineReaction.rows[0]?.reaction || null
      });
    } catch (error) {
      console.error('status interactions read', error);
      return res.status(500).json({ error: 'status interactions unavailable' });
    }
  });

  app.post('/api/statuses/:statusId/view', auth, async (req, res) => {
    try {
      await ensureSchema();
      const id = statusId(req);
      const status = await visibleStatus(id, req.user.sub);
      if (!status) return res.status(404).json({ error: 'status not found or not visible' });
      if (String(status.owner_id) !== String(req.user.sub)) {
        await pool.query('INSERT INTO status_views(status_id, viewer_id) VALUES($1,$2) ON CONFLICT (status_id, viewer_id) DO UPDATE SET viewed_at=NOW()', [id, req.user.sub]);
      }
      return res.json({ viewed: true });
    } catch (error) {
      console.error('status view', error);
      return res.status(500).json({ error: 'status view failed' });
    }
  });

  app.post('/api/statuses/:statusId/like', auth, async (req, res) => {
    try {
      await ensureSchema();
      const id = statusId(req);
      const status = await visibleStatus(id, req.user.sub);
      if (!status) return res.status(404).json({ error: 'status not found or not visible' });
      const existing = await pool.query('SELECT 1 FROM status_likes WHERE status_id=$1 AND user_id=$2 LIMIT 1', [id, req.user.sub]);
      if (existing.rows[0]) await pool.query('DELETE FROM status_likes WHERE status_id=$1 AND user_id=$2', [id, req.user.sub]);
      else await pool.query('INSERT INTO status_likes(status_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING', [id, req.user.sub]);
      return res.json({ liked: !existing.rows[0] });
    } catch (error) {
      console.error('status like', error);
      return res.status(500).json({ error: 'status like failed' });
    }
  });

  app.post('/api/statuses/:statusId/reaction', auth, async (req, res) => {
    try {
      await ensureSchema();
      const id = statusId(req);
      const reaction = String(req.body?.reaction || '').trim();
      if (!reaction || reaction.length > 32) return res.status(400).json({ error: 'invalid reaction' });
      const status = await visibleStatus(id, req.user.sub);
      if (!status) return res.status(404).json({ error: 'status not found or not visible' });
      const existing = await pool.query('SELECT reaction FROM status_reactions WHERE status_id=$1 AND user_id=$2', [id, req.user.sub]);
      if (existing.rows[0]?.reaction === reaction) {
        await pool.query('DELETE FROM status_reactions WHERE status_id=$1 AND user_id=$2', [id, req.user.sub]);
        return res.json({ reaction: null });
      }
      await pool.query(`INSERT INTO status_reactions(status_id,user_id,reaction) VALUES($1,$2,$3)
        ON CONFLICT(status_id,user_id) DO UPDATE SET reaction=EXCLUDED.reaction, created_at=NOW()`, [id, req.user.sub, reaction]);
      return res.json({ reaction });
    } catch (error) {
      console.error('status reaction', error);
      return res.status(500).json({ error: 'status reaction failed' });
    }
  });

  app.post('/api/statuses/:statusId/reply', auth, async (req, res) => {
    try {
      await ensureSchema();
      const id = statusId(req);
      const body = String(req.body?.body || '').trim();
      if (!body || body.length > 1000) return res.status(400).json({ error: 'invalid reply' });
      const status = await visibleStatus(id, req.user.sub);
      if (!status) return res.status(404).json({ error: 'status not found or not visible' });
      const replyId = randomUUID();
      await pool.query('INSERT INTO status_replies(id,status_id,sender_id,body) VALUES($1,$2,$3,$4)', [replyId, id, req.user.sub, body]);
      return res.json({ replyId, sent: true });
    } catch (error) {
      console.error('status reply', error);
      return res.status(500).json({ error: 'status reply failed' });
    }
  });
}
