import pg from 'pg';
import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 2,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  keepAlive: true
}) : null;

export function registerGroupContentRoutes({ app }) {
  if (!pool) return;

  const auth = (req, res, next) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
    try {
      req.user = jwt.verify(token, JWT_SECRET);
      return next();
    } catch {
      return res.status(401).json({ error: 'invalid or expired token' });
    }
  };

  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`
        CREATE TABLE IF NOT EXISTS fynx_group_posts (
          id TEXT PRIMARY KEY,
          group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE,
          author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          text TEXT NOT NULL DEFAULT '',
          marketplace_product_id TEXT,
          pinned BOOLEAN NOT NULL DEFAULT FALSE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS fynx_group_posts_group_idx ON fynx_group_posts(group_id, created_at DESC);
        CREATE TABLE IF NOT EXISTS fynx_group_polls (
          id TEXT PRIMARY KEY,
          post_id TEXT NOT NULL UNIQUE REFERENCES fynx_group_posts(id) ON DELETE CASCADE,
          question TEXT NOT NULL,
          multiple_choice BOOLEAN NOT NULL DEFAULT FALSE,
          closed BOOLEAN NOT NULL DEFAULT FALSE
        );
        CREATE TABLE IF NOT EXISTS fynx_group_poll_options (
          id TEXT PRIMARY KEY,
          poll_id TEXT NOT NULL REFERENCES fynx_group_polls(id) ON DELETE CASCADE,
          text TEXT NOT NULL,
          position INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS fynx_group_poll_votes (
          poll_id TEXT NOT NULL REFERENCES fynx_group_polls(id) ON DELETE CASCADE,
          option_id TEXT NOT NULL REFERENCES fynx_group_poll_options(id) ON DELETE CASCADE,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY(poll_id, option_id, user_id)
        );
        CREATE INDEX IF NOT EXISTS fynx_group_poll_votes_poll_idx ON fynx_group_poll_votes(poll_id);
        CREATE TABLE IF NOT EXISTS fynx_group_events (
          id TEXT PRIMARY KEY,
          group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE,
          creator_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          title TEXT NOT NULL,
          description TEXT NOT NULL DEFAULT '',
          starts_at TIMESTAMPTZ NOT NULL,
          ends_at TIMESTAMPTZ,
          location TEXT NOT NULL DEFAULT '',
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS fynx_group_events_group_idx ON fynx_group_events(group_id, starts_at ASC);
        CREATE TABLE IF NOT EXISTS fynx_group_event_rsvps (
          event_id TEXT NOT NULL REFERENCES fynx_group_events(id) ON DELETE CASCADE,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          response TEXT NOT NULL CHECK(response IN ('GOING','MAYBE','NOT_GOING')),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY(event_id, user_id)
        );
      `).catch(error => { schemaPromise = undefined; throw error; });
    }
    return schemaPromise;
  };

  const member = async (groupId, userId) => (
    await pool.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1', [groupId, userId])
  ).rows[0] || null;

  const canPost = role => ['ADMIN', 'MODERATOR', 'MEMBER'].includes(String(role));
  const canManage = role => ['ADMIN', 'MODERATOR'].includes(String(role));

  app.get('/api/groups/:groupId/content', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor) return res.status(403).json({ error: 'group membership required' });

      const posts = (await pool.query(`
        SELECT p.id,p.text,p.marketplace_product_id,p.pinned,p.created_at,u.username,
               po.poll_id,po.question,po.multiple_choice,po.closed
        FROM fynx_group_posts p
        JOIN users u ON u.id=p.author_id
        LEFT JOIN fynx_group_polls po ON po.post_id=p.id
        WHERE p.group_id=$1
        ORDER BY p.pinned DESC,p.created_at DESC
        LIMIT 100
      `, [groupId])).rows;

      const postIds = posts.map(p => p.id);
      const options = postIds.length ? (await pool.query(`
        SELECT o.id,o.poll_id,o.text,o.position,COUNT(v.user_id)::int AS vote_count
        FROM fynx_group_poll_options o
        LEFT JOIN fynx_group_poll_votes v ON v.option_id=o.id
        WHERE o.poll_id IN (SELECT poll_id FROM fynx_group_polls WHERE post_id = ANY($1::text[]))
        GROUP BY o.id,o.poll_id,o.text,o.position
        ORDER BY o.poll_id,o.position
      `, [postIds])).rows : [];

      const events = (await pool.query(`
        SELECT e.id,e.title,e.description,e.starts_at,e.ends_at,e.location,u.username AS creator_username,
               COUNT(r.user_id) FILTER (WHERE r.response='GOING')::int AS going_count,
               COUNT(r.user_id) FILTER (WHERE r.response='MAYBE')::int AS maybe_count,
               COUNT(r.user_id) FILTER (WHERE r.response='NOT_GOING')::int AS not_going_count,
               COALESCE((SELECT response FROM fynx_group_event_rsvps mine WHERE mine.event_id=e.id AND mine.user_id=$2 LIMIT 1),'') AS my_response
        FROM fynx_group_events e
        JOIN users u ON u.id=e.creator_id
        LEFT JOIN fynx_group_event_rsvps r ON r.event_id=e.id
        WHERE e.group_id=$1
        GROUP BY e.id,u.username
        ORDER BY e.starts_at ASC
        LIMIT 50
      `, [groupId, req.user.sub])).rows;

      return res.json({
        posts: posts.map(row => ({
          id: row.id,
          text: row.text,
          authorUsername: row.username,
          marketplaceProductId: row.marketplace_product_id,
          pinned: row.pinned,
          createdAt: new Date(row.created_at).getTime(),
          poll: row.poll_id ? {
            id: row.poll_id,
            question: row.question,
            multipleChoice: row.multiple_choice,
            closed: row.closed,
            options: options.filter(o => o.poll_id === row.poll_id).map(o => ({ id: o.id, text: o.text, voteCount: Number(o.vote_count) }))
          } : null
        })),
        events: events.map(row => ({
          id: row.id,
          title: row.title,
          description: row.description,
          startsAt: new Date(row.starts_at).getTime(),
          endsAt: row.ends_at ? new Date(row.ends_at).getTime() : null,
          location: row.location,
          creatorUsername: row.creator_username,
          goingCount: Number(row.going_count),
          maybeCount: Number(row.maybe_count),
          notGoingCount: Number(row.not_going_count),
          myResponse: row.my_response || null
        }))
      });
    } catch (error) {
      console.error('group content load', error);
      return res.status(500).json({ error: 'group content could not be loaded' });
    }
  });

  app.post('/api/groups/:groupId/posts', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor || !canPost(actor.role)) return res.status(403).json({ error: 'group posting permission required' });
      const text = String(req.body?.text || '').trim();
      const marketplaceProductId = String(req.body?.marketplaceProductId || '').trim() || null;
      if (!text && !marketplaceProductId) return res.status(400).json({ error: 'post content is required' });
      const id = String(req.body?.id || randomUUID());
      await pool.query(
        'INSERT INTO fynx_group_posts(id,group_id,author_id,text,marketplace_product_id) VALUES($1,$2,$3,$4,$5)',
        [id, groupId, req.user.sub, text, marketplaceProductId]
      );
      return res.status(201).json({ id });
    } catch (error) {
      console.error('group post create', error);
      return res.status(500).json({ error: 'group post could not be created' });
    }
  });

  app.post('/api/groups/:groupId/polls', auth, async (req, res) => {
    const client = await pool.connect();
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor || !canPost(actor.role)) return res.status(403).json({ error: 'group posting permission required' });
      const question = String(req.body?.question || '').trim();
      const rawOptions = Array.isArray(req.body?.options) ? req.body.options : [];
      const options = rawOptions.map(v => String(v || '').trim()).filter(Boolean).slice(0, 10);
      if (question.length < 2 || options.length < 2) return res.status(400).json({ error: 'A poll needs a question and at least two options.' });
      const postId = randomUUID();
      const pollId = randomUUID();
      await client.query('BEGIN');
      await client.query('INSERT INTO fynx_group_posts(id,group_id,author_id,text) VALUES($1,$2,$3,$4)', [postId, groupId, req.user.sub, '']);
      await client.query('INSERT INTO fynx_group_polls(id,post_id,question,multiple_choice) VALUES($1,$2,$3,$4)', [pollId, postId, Boolean(req.body?.multipleChoice)]);
      for (let i = 0; i < options.length; i += 1) {
        await client.query('INSERT INTO fynx_group_poll_options(id,poll_id,text,position) VALUES($1,$2,$3,$4)', [randomUUID(), pollId, options[i], i]);
      }
      await client.query('COMMIT');
      return res.status(201).json({ id: postId, pollId });
    } catch (error) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('group poll create', error);
      return res.status(500).json({ error: 'group poll could not be created' });
    } finally {
      client.release();
    }
  });

  app.post('/api/groups/:groupId/polls/:pollId/votes', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor) return res.status(403).json({ error: 'group membership required' });
      const pollId = String(req.params?.pollId || '');
      const optionId = String(req.body?.optionId || '');
      const poll = (await pool.query(`SELECT p.id,p.multiple_choice,p.closed FROM fynx_group_polls p JOIN fynx_group_posts gp ON gp.id=p.post_id WHERE p.id=$1 AND gp.group_id=$2 LIMIT 1`, [pollId, groupId])).rows[0];
      if (!poll) return res.status(404).json({ error: 'poll not found' });
      if (poll.closed) return res.status(409).json({ error: 'poll is closed' });
      const option = (await pool.query('SELECT id FROM fynx_group_poll_options WHERE id=$1 AND poll_id=$2 LIMIT 1', [optionId, pollId])).rows[0];
      if (!option) return res.status(400).json({ error: 'poll option not found' });
      if (!poll.multiple_choice) await pool.query('DELETE FROM fynx_group_poll_votes WHERE poll_id=$1 AND user_id=$2', [pollId, req.user.sub]);
      await pool.query('INSERT INTO fynx_group_poll_votes(poll_id,option_id,user_id) VALUES($1,$2,$3) ON CONFLICT DO NOTHING', [pollId, optionId, req.user.sub]);
      return res.json({ voted: true });
    } catch (error) {
      console.error('group poll vote', error);
      return res.status(500).json({ error: 'group poll vote could not be saved' });
    }
  });

  app.patch('/api/groups/:groupId/posts/:postId/pin', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor || !canManage(actor.role)) return res.status(403).json({ error: 'group moderator permission required' });
      const pinned = Boolean(req.body?.pinned);
      const result = await pool.query('UPDATE fynx_group_posts SET pinned=$1 WHERE id=$2 AND group_id=$3 RETURNING pinned', [pinned, req.params.postId, groupId]);
      if (!result.rowCount) return res.status(404).json({ error: 'group post not found' });
      return res.json({ pinned: result.rows[0].pinned });
    } catch (error) {
      console.error('group post pin', error);
      return res.status(500).json({ error: 'group post pin could not be updated' });
    }
  });

  app.post('/api/groups/:groupId/events', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor || !canPost(actor.role)) return res.status(403).json({ error: 'group posting permission required' });
      const title = String(req.body?.title || '').trim();
      const startsAt = String(req.body?.startsAt || '').trim();
      const description = String(req.body?.description || '').trim();
      const location = String(req.body?.location || '').trim();
      if (title.length < 2 || !startsAt) return res.status(400).json({ error: 'Event title and start time are required.' });
      const parsed = new Date(startsAt);
      if (Number.isNaN(parsed.getTime())) return res.status(400).json({ error: 'Event start time is invalid.' });
      const result = await pool.query(`INSERT INTO fynx_group_events(id,group_id,creator_id,title,description,starts_at,ends_at,location) VALUES($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id`, [randomUUID(), groupId, req.user.sub, title, description, parsed.toISOString(), req.body?.endsAt ? new Date(req.body.endsAt).toISOString() : null, location]);
      return res.status(201).json({ id: result.rows[0].id });
    } catch (error) {
      console.error('group event create', error);
      return res.status(500).json({ error: 'group event could not be created' });
    }
  });

  app.post('/api/groups/:groupId/events/:eventId/rsvp', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor) return res.status(403).json({ error: 'group membership required' });
      const response = String(req.body?.response || '').toUpperCase();
      if (!['GOING', 'MAYBE', 'NOT_GOING'].includes(response)) return res.status(400).json({ error: 'Choose Going, Maybe, or Not going.' });
      const event = (await pool.query('SELECT id FROM fynx_group_events WHERE id=$1 AND group_id=$2 LIMIT 1', [req.params.eventId, groupId])).rows[0];
      if (!event) return res.status(404).json({ error: 'group event not found' });
      await pool.query(`INSERT INTO fynx_group_event_rsvps(event_id,user_id,response) VALUES($1,$2,$3) ON CONFLICT(event_id,user_id) DO UPDATE SET response=EXCLUDED.response,updated_at=NOW()`, [event.id, req.user.sub, response]);
      return res.json({ response });
    } catch (error) {
      console.error('group event RSVP', error);
      return res.status(500).json({ error: 'event response could not be saved' });
    }
  });
}
