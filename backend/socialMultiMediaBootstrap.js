import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

/**
 * Home multi-media post backend.
 * Keeps legacy social_posts.media_id/media_type populated with the first item
 * while storing the complete ordered media set in a normalized relation.
 */
export async function installSocialMultiMedia() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  let source = await readFile(socialPath, "utf8");
  if (source.includes("fynxHomeMultiMediaPosts")) return;
  if (!source.includes("const ensureSocialSchema = async")) {
    throw new Error("FYNX multi-media bootstrap could not locate social schema initializer");
  }
  if (!source.includes("const visibleSocialPost = async")) {
    throw new Error("FYNX multi-media bootstrap could not locate social visibility helper");
  }

  const schemaNeedle = "CREATE INDEX IF NOT EXISTS social_posts_created_idx ON social_posts(created_at DESC);";
  if (!source.includes(schemaNeedle)) {
    throw new Error("FYNX multi-media bootstrap could not locate social post schema marker");
  }
  source = source.replace(schemaNeedle, `${schemaNeedle}\n      CREATE TABLE IF NOT EXISTS social_post_media (\n        post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,\n        media_id BIGINT NOT NULL REFERENCES message_media(id) ON DELETE CASCADE,\n        media_type TEXT NOT NULL CHECK (media_type IN ('image','video','audio')),\n        position INTEGER NOT NULL CHECK (position >= 0 AND position < 4),\n        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),\n        PRIMARY KEY (post_id, media_id),\n        UNIQUE (post_id, position)\n      );\n      CREATE INDEX IF NOT EXISTS social_post_media_post_idx ON social_post_media(post_id, position);\n      CREATE INDEX IF NOT EXISTS social_post_media_media_idx ON social_post_media(media_id);`);

  const routes = `
  // fynxHomeMultiMediaPosts: one real post can own an ordered set of up to four visual assets.
  app.post('/api/social/posts/multi', auth, async (req, res) => {
    const client = await pool.connect();
    try {
      await ensureSocialSchema();
      const text = typeof req.body?.text === 'string' ? req.body.text.trim().slice(0, 4000) : '';
      const visibility = req.body?.visibility === 'FRIENDS_ONLY' ? 'FRIENDS_ONLY' : 'PUBLIC';
      const rawIds = Array.isArray(req.body?.mediaIds) ? req.body.mediaIds : [];
      const rawTypes = Array.isArray(req.body?.mediaTypes) ? req.body.mediaTypes : [];
      const mediaIds = rawIds.map(Number);
      const mediaTypes = rawTypes.map((value) => String(value || '').toLowerCase());

      if (mediaIds.length < 1 || mediaIds.length > 4 || mediaIds.length !== mediaTypes.length) {
        return res.status(400).json({ error: 'a post must contain 1-4 media items' });
      }
      if (new Set(mediaIds).size !== mediaIds.length || mediaIds.some((id) => !Number.isSafeInteger(id) || id < 1)) {
        return res.status(400).json({ error: 'invalid media selection' });
      }
      const hasAudio = mediaTypes.includes('audio');
      if (hasAudio && (mediaIds.length !== 1 || mediaTypes[0] !== 'audio')) {
        return res.status(400).json({ error: 'voice posts must contain one audio item' });
      }
      if (!hasAudio && mediaTypes.some((type) => type !== 'image' && type !== 'video')) {
        return res.status(400).json({ error: 'Home posts support images and videos' });
      }
      if (!text && mediaIds.length === 0) return res.status(400).json({ error: 'add a caption or media' });

      const mediaResult = await client.query(
        `SELECT id, owner_id, mime_type FROM message_media WHERE id = ANY($1::bigint[])`,
        [mediaIds]
      );
      if (mediaResult.rows.length !== mediaIds.length) return res.status(404).json({ error: 'one or more media items were not found' });
      const byId = new Map(mediaResult.rows.map((row) => [String(row.id), row]));
      for (let i = 0; i < mediaIds.length; i += 1) {
        const row = byId.get(String(mediaIds[i]));
        if (!row || String(row.owner_id) !== String(req.user.sub)) return res.status(403).json({ error: 'media ownership check failed' });
        const expectedPrefix = mediaTypes[i] === 'image' ? 'image/' : mediaTypes[i] === 'video' ? 'video/' : 'audio/';
        if (!String(row.mime_type || '').toLowerCase().startsWith(expectedPrefix)) return res.status(400).json({ error: 'media type does not match uploaded asset' });
      }

      await client.query('BEGIN');
      const post = await client.query(
        `INSERT INTO social_posts(author_id,text,visibility,media_id,media_type) VALUES($1,$2,$3,$4,$5) RETURNING id`,
        [req.user.sub, text, visibility, mediaIds[0], mediaTypes[0]]
      );
      const postId = Number(post.rows[0].id);
      for (let position = 0; position < mediaIds.length; position += 1) {
        await client.query(
          `INSERT INTO social_post_media(post_id,media_id,media_type,position) VALUES($1,$2,$3,$4)`,
          [postId, mediaIds[position], mediaTypes[position], position]
        );
      }
      await client.query('COMMIT');
      return res.status(201).json({ postId: String(postId), mediaCount: mediaIds.length });
    } catch (error) {
      try { await client.query('ROLLBACK'); } catch {}
      console.error('social multi-media post', error);
      return res.status(500).json({ error: 'multi-media post failed' });
    } finally {
      client.release();
    }
  });

  app.get('/api/social/posts/:id/media', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      const postId = Number(req.params.id);
      if (!Number.isSafeInteger(postId) || postId < 1) return res.status(400).json({ error: 'invalid post id' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const result = await pool.query(
        `SELECT spm.media_id,spm.media_type,spm.position
           FROM social_post_media spm
          WHERE spm.post_id=$1
          ORDER BY spm.position ASC`,
        [postId]
      );
      return res.json({
        media: result.rows.map((row) => ({
          id: String(row.media_id),
          mediaType: row.media_type,
          position: Number(row.position),
          mediaUrl: `/api/social/media/${row.media_id}`
        }))
      });
    } catch (error) {
      console.error('social post media', error);
      return res.status(500).json({ error: 'post media lookup failed' });
    }
  });
`;

  const closing = "\n}\n";
  const index = source.lastIndexOf(closing);
  if (index < 0) throw new Error("FYNX multi-media bootstrap could not locate social route closing marker");
  await writeFile(socialPath, source.slice(0, index) + routes + source.slice(index), "utf8");
}
