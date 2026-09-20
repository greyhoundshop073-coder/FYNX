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

  // Batch 5 location migration: persist a human-readable place label only.
  // Raw device coordinates stay on the Android device and are never sent to FYNX.
  const locationMarker = "fynxBatch5LocationV1";
  if (!source.includes(locationMarker)) {
    const schemaMarker = "CREATE INDEX IF NOT EXISTS social_posts_created_idx ON social_posts(created_at DESC);";
    if (!source.includes(schemaMarker)) throw new Error("FYNX location bootstrap could not locate social post schema marker");
    source = source.replace(schemaMarker, schemaMarker + "\n      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS location TEXT;\n      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS music_media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL;\n      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS music_title TEXT;\n      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS music_artist TEXT;\n      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS music_duration_ms BIGINT;\n      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS feeling_activity_type TEXT;\n      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS feeling_activity TEXT;");

    if (source.includes("p.media_type,EXTRACT(EPOCH FROM p.created_at)*1000 timestamp")) {
      source = source.replace("p.media_type,EXTRACT(EPOCH FROM p.created_at)*1000 timestamp", "p.media_type,p.location,EXTRACT(EPOCH FROM p.created_at)*1000 timestamp");
    }
    if (!source.includes("location:x.location||null") && source.includes("timestamp:Number(x.timestamp)")) {
      source = source.replace("timestamp:Number(x.timestamp)", "location:x.location||null,timestamp:Number(x.timestamp)");
    }

    const singleMediaType = "const mediaType=typeof req.body?.mediaType==='string'?req.body.mediaType.trim().toLowerCase():null;";
    if (source.includes(singleMediaType) && !source.includes("const location=typeof req.body?.location")) {
      source = source.replace(singleMediaType, singleMediaType + "const location=typeof req.body?.location==='string'?req.body.location.trim().slice(0,160):null;");
    }
    const singleInsert = "INSERT INTO social_posts(author_id,text,visibility,media_id,media_type) VALUES($1,$2,$3,$4,$5) RETURNING id";
    if (source.includes(singleInsert)) {
      source = source.replace(singleInsert, "INSERT INTO social_posts(author_id,text,visibility,media_id,media_type,location) VALUES($1,$2,$3,$4,$5,$6) RETURNING id");
      source = source.replace("[req.user.sub,text,visibility,mediaId,mediaType]", "[req.user.sub,text,visibility,mediaId,mediaType,location]");
    }

    const multiLocationNeedle = "const backgroundKey = typeof req.body?.textBackground === 'string' ? req.body.textBackground.trim().toUpperCase() : '';";
    if (source.includes(multiLocationNeedle) && !source.includes("const location = typeof req.body?.location")) {
      source = source.replace(multiLocationNeedle, multiLocationNeedle + " const location = typeof req.body?.location === 'string' ? req.body.location.trim().slice(0, 160) : null;");
    }

    const multiInsert = "INSERT INTO social_posts(author_id,text,visibility,media_id,media_type,text_background,text_background_color,text_foreground_color,location) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id";
    if (source.includes(multiInsert)) {
      source = source.replace(multiInsert, "INSERT INTO social_posts(author_id,text,visibility,media_id,media_type,text_background,text_background_color,text_foreground_color,location) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id");
      source = source.replace("[req.user.sub, text, visibility, mediaIds[0], mediaTypes[0], backgroundStyle?.[0] ? backgroundKey : '', backgroundStyle?.[0] ?? null, backgroundStyle?.[1] ?? null, location]", "[req.user.sub, text, visibility, mediaIds[0], mediaTypes[0], backgroundStyle?.[0] ? backgroundKey : '', backgroundStyle?.[0] ?? null, backgroundStyle?.[1] ?? null, location]");
    }

    source += "\n  // fynxBatch5LocationV1\n";
    await writeFile(socialPath, source, "utf8");
  }
  const mediaAuthMarker = "fynxHomeMultiMediaMediaAuthV2";
  if (source.includes(mediaAuthMarker)) return;

  // The social core has existed in both formatted and compact forms. Match the
  // route structurally instead of depending on one exact whitespace layout.
  const mediaRouteNeedle = /app\.get\(['"]\/api\/social\/media\/:id['"]\s*,\s*auth\s*,\s*async\s*\(req\s*,\s*res\s*\)\s*=>\s*\{/;
  if (!mediaRouteNeedle.test(source)) {
    throw new Error("FYNX multi-media bootstrap could not locate social media route");
  }

  // The legacy media route only joins social_posts.media_id, which is the first
  // asset of a multi-media post. Add an earlier route for the normalized media
  // relation so every persisted asset remains retrievable after app restarts.
  const mediaAuthRoute = `  // fynxHomeMultiMediaMediaAuthV2: authorize normalized Home post media before legacy media lookup.
  app.get('/api/social/media/:id', auth, async (req, res, next) => {
    try {
      await ensureSocialSchema();
      const mediaId = Number(req.params.id);
      if (!Number.isInteger(mediaId) || mediaId < 1) return next();
      const result = await pool.query(
        \`SELECT mm.mime_type, mm.data
           FROM message_media mm
           JOIN social_post_media spm ON spm.media_id = mm.id
           JOIN social_posts p ON p.id = spm.post_id
          WHERE mm.id = $1
            AND (
              p.author_id = $2
              OR p.visibility = 'PUBLIC'
              OR (p.visibility = 'FRIENDS_ONLY' AND EXISTS (
                SELECT 1 FROM friendships f
                 WHERE ((f.user_id = p.author_id AND f.friend_id = $2) OR (f.user_id = $2 AND f.friend_id = p.author_id))
                   AND f.status = 'accepted'
              ))
            )
            AND NOT EXISTS (
              SELECT 1 FROM blocks b
               WHERE (b.blocker_id = $2 AND b.blocked_id = p.author_id)
                  OR (b.blocker_id = p.author_id AND b.blocked_id = $2)
            )
          ORDER BY spm.position ASC
          LIMIT 1\`,
        [mediaId, req.user.sub]
      );
      if (!result.rows[0]) return next();
      res.set('Cache-Control', 'private, max-age=3600');
      res.type(result.rows[0].mime_type);
      return res.send(result.rows[0].data);
    } catch (error) {
      console.error('social normalized media', error);
      return res.status(500).json({ error: 'social media failed' });
    }
  });
`;
  source = source.replace(mediaRouteNeedle, mediaAuthRoute + source.match(mediaRouteNeedle)[0]);

  // If production already ran the previous bootstrap and persisted the injected
  // multi-media routes into the working tree, only the retrieval fix is needed.
  if (source.includes("fynxHomeMultiMediaPosts")) {
    await writeFile(socialPath, source, "utf8");
    return;
  }

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
  source = source.replace(schemaNeedle, `${schemaNeedle}\n      CREATE TABLE IF NOT EXISTS social_post_media (\n        post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,\n        media_id BIGINT NOT NULL REFERENCES message_media(id) ON DELETE CASCADE,\n        media_type TEXT NOT NULL CHECK (media_type IN ('image','video','audio')),\n        position INTEGER NOT NULL CHECK (position >= 0 AND position < 4),\n        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),\n        PRIMARY KEY (post_id, media_id),\n        UNIQUE (post_id, position)\n      );\n      CREATE INDEX IF NOT EXISTS social_post_media_post_idx ON social_post_media(post_id, position);\n      CREATE INDEX IF NOT EXISTS social_post_media_media_idx ON social_post_media(media_id);
      ALTER TABLE social_posts DROP CONSTRAINT IF EXISTS social_posts_visibility_check;
      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS text_background TEXT NOT NULL DEFAULT '';
      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS text_background_color BIGINT;
      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS text_foreground_color BIGINT;
      ALTER TABLE social_posts ADD CONSTRAINT social_posts_visibility_check CHECK (visibility IN ('PUBLIC','FRIENDS_ONLY','SELECTED_PEOPLE','ONLY_ME'));
      CREATE TABLE IF NOT EXISTS social_post_audience (post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(post_id,user_id));
      CREATE INDEX IF NOT EXISTS social_post_audience_user_idx ON social_post_audience(user_id, created_at DESC);
      ALTER TABLE social_posts ADD COLUMN IF NOT EXISTS location TEXT;`);

  const routes = `
  // fynxHomeMultiMediaPosts: one real post can own an ordered set of up to four visual assets.
  app.post('/api/social/posts/multi', auth, async (req, res) => {
    const client = await pool.connect();
    try {
      await ensureSocialSchema();
      const text = typeof req.body?.text === 'string' ? req.body.text.trim().slice(0, 4000) : '';
      const visibility = ['PUBLIC','FRIENDS_ONLY','SELECTED_PEOPLE','ONLY_ME'].includes(String(req.body?.visibility || '').toUpperCase()) ? String(req.body.visibility).toUpperCase() : 'PUBLIC';
      const backgroundKey = typeof req.body?.textBackground === 'string' ? req.body.textBackground.trim().toUpperCase() : '';
      const location = typeof req.body?.location === 'string' ? req.body.location.trim().slice(0, 160) : null;
      const backgroundStyles = { OCEAN: [0xFF1565C0,0xFFFFFFFF], VIOLET: [0xFF6A1B9A,0xFFFFFFFF], EMERALD: [0xFF00695C,0xFFFFFFFF], SUNSET: [0xFFE65100,0xFFFFFFFF], CHARCOAL: [0xFF263238,0xFFFFFFFF] };
      const backgroundStyle = backgroundKey && backgroundStyles[backgroundKey] ? backgroundStyles[backgroundKey] : null;
      const audienceUserIds = Array.isArray(req.body?.audienceUserIds) ? req.body.audienceUserIds.map(String).map(value => value.trim()).filter(Boolean).slice(0, 100) : [];
      if (visibility === 'SELECTED_PEOPLE' && audienceUserIds.length === 0) return res.status(400).json({ error: 'select at least one person' });
      if (visibility !== 'SELECTED_PEOPLE' && audienceUserIds.length) return res.status(400).json({ error: 'invalid selected audience' });
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
      if (!text && mediaIds.length === 0 && musicMediaId == null) return res.status(400).json({ error: 'add a caption or media' });
      if (musicMediaId != null) {
        if (!Number.isSafeInteger(musicMediaId) || musicMediaId < 1) return res.status(400).json({ error: 'invalid music selection' });
        const musicResult = await client.query(
          'SELECT c.id,c.media_id,c.title,c.artist,c.duration_ms,mm.mime_type FROM fynx_music_catalogue c JOIN message_media mm ON mm.id=c.media_id WHERE c.media_id=$1 AND c.active=TRUE',
          [musicMediaId]
        );
        if (!musicResult.rows[0] || !String(musicResult.rows[0].mime_type || '').toLowerCase().startsWith('audio/')) return res.status(403).json({ error: 'music selection is not published in the FYNX catalogue' });
        musicTitle = musicResult.rows[0].title;
        musicArtist = musicResult.rows[0].artist;
        musicDurationMs = Number(musicResult.rows[0].duration_ms || 0);
      }
      if (visibility === 'SELECTED_PEOPLE') {
        const friends = await client.query(\`SELECT CASE WHEN f.user_id=$1 THEN f.friend_id ELSE f.user_id END AS id FROM friendships f WHERE (f.user_id=$1 OR f.friend_id=$1) AND f.status='accepted' AND CASE WHEN f.user_id=$1 THEN f.friend_id ELSE f.user_id END = ANY($2::bigint[])\`, [req.user.sub, audienceUserIds]);
        if (friends.rows.length !== audienceUserIds.length) return res.status(403).json({ error: 'selected audience must contain your accepted friends' });
      }

      const mediaResult = await client.query(
        \`SELECT id, owner_id, mime_type FROM message_media WHERE id = ANY($1::bigint[])\`,
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
        \`INSERT INTO social_posts(author_id,text,visibility,media_id,media_type,music_media_id,music_title,music_artist,music_duration_ms,feeling_activity_type,feeling_activity,text_background,text_background_color,text_foreground_color,location) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15) RETURNING id\`,
        [req.user.sub, text, visibility, mediaIds[0], mediaTypes[0], musicMediaId, musicTitle, musicArtist, musicDurationMs, feelingActivityType, feelingActivity, backgroundStyle?.[0] ? backgroundKey : '', backgroundStyle?.[0] ?? null, backgroundStyle?.[1] ?? null, location]
      );
      const postId = Number(post.rows[0].id);
      for (let position = 0; position < mediaIds.length; position += 1) {
        await client.query(
          \`INSERT INTO social_post_media(post_id,media_id,media_type,position) VALUES($1,$2,$3,$4)\`,
          [postId, mediaIds[position], mediaTypes[position], position]
        );
      }
      if (visibility === 'SELECTED_PEOPLE') await client.query('INSERT INTO social_post_audience(post_id,user_id) SELECT $1,unnest($2::bigint[])', [postId, audienceUserIds]);
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
        \`SELECT spm.media_id,spm.media_type,spm.position
           FROM social_post_media spm
          WHERE spm.post_id=$1
          ORDER BY spm.position ASC\`,
        [postId]
      );
      return res.json({
        media: result.rows.map((row) => ({
          id: String(row.media_id),
          mediaType: row.media_type,
          position: Number(row.position),
          mediaUrl: \`/api/social/media/\${row.media_id}\`
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
