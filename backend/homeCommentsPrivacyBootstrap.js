import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

// Home comment privacy bridge. The production entrypoint remains
// realtimeIsolationBootstrap.js; this bridge installs the hardened 4B comment
// routes first, then lets the existing realtime/bootstrap stack start normally.
// It is idempotent and uses the existing social schema/visibility helpers.
async function installHomeCommentPrivacy() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  let source = await readFile(socialPath, "utf8");
  if (source.includes("fynxHomeCommentsPrivacyBatch")) return;

  const schemaNeedle = "CREATE INDEX IF NOT EXISTS social_post_comments_post_idx ON social_post_comments(post_id, created_at ASC);";
  if (!source.includes(schemaNeedle)) throw new Error("Home comment privacy patch could not locate social comment schema marker");
  source = source.replace(schemaNeedle, `${schemaNeedle}\n        ALTER TABLE social_post_comments ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT REFERENCES social_post_comments(id) ON DELETE CASCADE;\n        CREATE INDEX IF NOT EXISTS social_post_comments_parent_idx ON social_post_comments(post_id, parent_comment_id, created_at ASC);`);

  const routeMarker = "  app.post('/api/social/follow/:username'";
  if (!source.includes(routeMarker)) throw new Error("Home comment privacy patch could not locate social follow route marker");

  const routes = `
  // fynxHomeCommentsPrivacyBatch: privacy-safe paginated comments and replies.
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
      const params = [postId, req.user.sub, limit + 1];
      const cursorClause = before === null ? '' : ' AND c.id < $4';
      if (before !== null) params.push(before);
      const result = await pool.query(
        `SELECT c.id,c.post_id,c.parent_comment_id,c.text,EXTRACT(EPOCH FROM c.created_at)*1000 AS timestamp,
                u.id AS author_id,u.username,u.display_name
           FROM social_post_comments c
           JOIN users u ON u.id=c.author_id
          WHERE c.post_id=$1
            AND NOT EXISTS (
              SELECT 1 FROM blocks b
               WHERE (b.blocker_id=$2 AND b.blocked_id=c.author_id)
                  OR (b.blocker_id=c.author_id AND b.blocked_id=$2)
            )${cursorClause}
          ORDER BY c.id DESC
          LIMIT $3`,
        params
      );
      const rows = result.rows.slice(0, limit).reverse();
      const nextCursor = result.rows.length > limit ? String(result.rows[result.rows.length - 1].id) : null;
      return res.json({
        comments: rows.map(row => ({
          id: String(row.id), postId: String(row.post_id),
          parentCommentId: row.parent_comment_id == null ? null : String(row.parent_comment_id),
          text: row.text, timestamp: Number(row.timestamp), authorId: String(row.author_id),
          authorUsername: row.username, authorDisplayName: row.display_name
        })),
        nextCursor
      });
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

      const parent = await pool.query(
        `SELECT c.id,c.post_id,c.parent_comment_id,c.author_id
           FROM social_post_comments c
           JOIN users u ON u.id=c.author_id
          WHERE c.id=$1 AND c.post_id=$2
            AND NOT EXISTS (
              SELECT 1 FROM blocks b
               WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id)
                  OR (b.blocker_id=c.author_id AND b.blocked_id=$3)
            )`,
        [parentId, postId, req.user.sub]
      );
      if (!parent.rows[0]) return res.status(404).json({ error: 'parent comment not found' });

      const rootId = parent.rows[0].parent_comment_id == null ? parentId : Number(parent.rows[0].parent_comment_id);
      const author = await pool.query('SELECT id,username,display_name FROM users WHERE id=$1', [req.user.sub]);
      if (!author.rows[0]) return res.status(401).json({ error: 'author not found' });

      await client.query('BEGIN');
      const inserted = await client.query(
        `INSERT INTO social_post_comments(post_id,author_id,parent_comment_id,text)
         VALUES($1,$2,$3,$4)
         RETURNING id,post_id,parent_comment_id,text,EXTRACT(EPOCH FROM created_at)*1000 AS timestamp`,
        [postId, req.user.sub, rootId, text]
      );
      await client.query('COMMIT');
      const row = inserted.rows[0];
      return res.status(201).json({
        comment: {
          id: String(row.id), postId: String(row.post_id), parentCommentId: String(row.parent_comment_id),
          text: row.text, timestamp: Number(row.timestamp), authorId: String(req.user.sub),
          authorUsername: author.rows[0].username || '', authorDisplayName: author.rows[0].display_name || ''
        }
      });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('social comment reply', error);
      return res.status(500).json({ error: 'reply failed' });
    } finally {
      client.release();
    }
  });

  app.get('/api/social/posts/:id/comments/:commentId/replies', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      const postId = Number(req.params.id);
      const parentId = Number(req.params.commentId);
      if (!Number.isSafeInteger(postId) || postId < 1 || !Number.isSafeInteger(parentId) || parentId < 1) return res.status(400).json({ error: 'invalid comment reference' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const parent = await pool.query(
        `SELECT c.id
           FROM social_post_comments c
          WHERE c.id=$1 AND c.post_id=$2
            AND NOT EXISTS (
              SELECT 1 FROM blocks b
               JOIN users u ON u.id=c.author_id
               WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id)
                  OR (b.blocker_id=c.author_id AND b.blocked_id=$3)
            )`,
        [parentId, postId, req.user.sub]
      );
      if (!parent.rows[0]) return res.status(404).json({ error: 'parent comment not found' });
      const requestedLimit = Number(req.query?.limit);
      const limit = Math.min(Math.max(Number.isInteger(requestedLimit) ? requestedLimit : 50, 1), 100);
      const result = await pool.query(
        `SELECT c.id,c.post_id,c.parent_comment_id,c.text,EXTRACT(EPOCH FROM c.created_at)*1000 AS timestamp,
                u.id AS author_id,u.username,u.display_name
           FROM social_post_comments c
           JOIN users u ON u.id=c.author_id
          WHERE c.post_id=$1 AND c.parent_comment_id=$2
            AND NOT EXISTS (
              SELECT 1 FROM blocks b
               WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id)
                  OR (b.blocker_id=c.author_id AND b.blocked_id=$3)
            )
          ORDER BY c.id ASC LIMIT $4`,
        [postId, parentId, req.user.sub, limit]
      );
      return res.json({ comments: result.rows.map(row => ({
        id: String(row.id), postId: String(row.post_id),
        parentCommentId: row.parent_comment_id == null ? null : String(row.parent_comment_id),
        text: row.text, timestamp: Number(row.timestamp), authorId: String(row.author_id),
        authorUsername: row.username, authorDisplayName: row.display_name
      })) });
    } catch (error) {
      console.error('social comment replies', error);
      return res.status(500).json({ error: 'replies lookup failed' });
    }
  });

`;
  source = source.replace(routeMarker, routes + routeMarker);
  await writeFile(socialPath, source);
}

await installHomeCommentPrivacy();
await import("./realtimeIsolationBootstrap.js");
