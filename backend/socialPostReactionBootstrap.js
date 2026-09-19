import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

export async function installSocialPostReactions() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  let source = await readFile(socialPath, "utf8");
  if (source.includes("fynxHomePostReactionsBatch")) return;
  const marker = "\n}\n";
  const index = source.lastIndexOf(marker);
  if (index < 0) throw new Error("Home post reactions could not locate social route closing marker");

  const routes = `
  // fynxHomePostReactionsBatch: durable one-reaction-per-user Home post reactions.
  const ensureHomePostReactionSchema = async () => {
    await pool.query(\`
      CREATE TABLE IF NOT EXISTS social_post_reactions (
        post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        reaction_type TEXT NOT NULL CHECK (reaction_type IN ('LIKE','LOVE','LAUGH','WOW','SAD')),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY (post_id, user_id)
      );
      CREATE INDEX IF NOT EXISTS social_post_reactions_post_type_idx ON social_post_reactions(post_id, reaction_type);
      CREATE INDEX IF NOT EXISTS social_post_reactions_user_idx ON social_post_reactions(user_id, updated_at DESC);
    \`);
  };
  const validHomeReaction = (value) => ['LIKE','LOVE','LAUGH','WOW','SAD'].includes(String(value || '').trim().toUpperCase()) ? String(value).trim().toUpperCase() : null;

  app.get('/api/social/posts/:id/reactions', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      await ensureHomePostReactionSchema();
      const postId = Number(req.params.id);
      if (!Number.isSafeInteger(postId) || postId < 1) return res.status(400).json({ error: 'invalid post id' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const [counts, current] = await Promise.all([
        pool.query('SELECT reaction_type, COUNT(*)::int AS count FROM social_post_reactions WHERE post_id=$1 GROUP BY reaction_type', [postId]),
        pool.query('SELECT reaction_type FROM social_post_reactions WHERE post_id=$1 AND user_id=$2 LIMIT 1', [postId, req.user.sub])
      ]);
      const reactionCounts = {};
      for (const row of counts.rows) reactionCounts[row.reaction_type] = Number(row.count || 0);
      return res.json({ reactions: reactionCounts, currentReaction: current.rows[0]?.reaction_type || null });
    } catch (error) {
      console.error('home post reactions lookup', error);
      return res.status(500).json({ error: 'reactions unavailable' });
    }
  });

  app.post('/api/social/posts/:id/reaction', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      await ensureHomePostReactionSchema();
      const postId = Number(req.params.id);
      const reaction = validHomeReaction(req.body?.reaction);
      if (!Number.isSafeInteger(postId) || postId < 1 || !reaction) return res.status(400).json({ error: 'invalid reaction' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });

      const context = await pool.query(\`
        SELECT p.author_id AS post_author_id,
               owner.username AS post_author_username,
               actor.username AS actor_username,
               r.reaction_type AS previous_reaction
          FROM social_posts p
          JOIN users owner ON owner.id=p.author_id
          JOIN users actor ON actor.id=$2
          LEFT JOIN social_post_reactions r ON r.post_id=p.id AND r.user_id=$2
         WHERE p.id=$1
         LIMIT 1
      \`, [postId, req.user.sub]);
      const reactionContext = context.rows[0];
      if (!reactionContext) return res.status(404).json({ error: 'post not found' });

      await pool.query(\`
        INSERT INTO social_post_reactions(post_id,user_id,reaction_type)
        VALUES($1,$2,$3)
        ON CONFLICT(post_id,user_id) DO UPDATE SET reaction_type=EXCLUDED.reaction_type, updated_at=NOW()
      \`, [postId, req.user.sub, reaction]);

      const isNewOrChangedReaction = !reactionContext.previous_reaction || reactionContext.previous_reaction !== reaction;
      if (isNewOrChangedReaction && String(reactionContext.post_author_id) !== String(req.user.sub)) {
        const actor = reactionContext.actor_username || 'A FYNX user';
        const reactionMessage = reaction === 'LIKE' ? 'Liked your post.' : reaction.toLowerCase() + ' reaction on your post.';
        await queueFynxNotification(pool, {
          userId: reactionContext.post_author_id,
          type: 'REACTION',
          title: '@' + actor + ' reacted to your post',
          message: reactionMessage,
          targetId: postId,
          sourceUsername: reactionContext.actor_username || null,
          route: 'fynx://home',
          notificationId: 'post-reaction-' + postId + '-' + req.user.sub + '-' + Date.now() + '-' + Math.random().toString(36).slice(2,8)
        });
      }

      const counts = await pool.query('SELECT reaction_type, COUNT(*)::int AS count FROM social_post_reactions WHERE post_id=$1 GROUP BY reaction_type', [postId]);
      const reactionCounts = {};
      for (const row of counts.rows) reactionCounts[row.reaction_type] = Number(row.count || 0);
      return res.json({ reactions: reactionCounts, currentReaction: reaction });
    } catch (error) {
      console.error('home post reaction set', error);
      return res.status(500).json({ error: 'reaction update failed' });
    }
  });

  app.delete('/api/social/posts/:id/reaction', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      await ensureHomePostReactionSchema();
      const postId = Number(req.params.id);
      if (!Number.isSafeInteger(postId) || postId < 1) return res.status(400).json({ error: 'invalid post id' });
      if (!(await visibleSocialPost(postId, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      await pool.query('DELETE FROM social_post_reactions WHERE post_id=$1 AND user_id=$2', [postId, req.user.sub]);
      const counts = await pool.query('SELECT reaction_type, COUNT(*)::int AS count FROM social_post_reactions WHERE post_id=$1 GROUP BY reaction_type', [postId]);
      const reactionCounts = {};
      for (const row of counts.rows) reactionCounts[row.reaction_type] = Number(row.count || 0);
      return res.json({ reactions: reactionCounts, currentReaction: null });
    } catch (error) {
      console.error('home post reaction delete', error);
      return res.status(500).json({ error: 'reaction removal failed' });
    }
  });
`;
  source = source.slice(0, index) + routes + source.slice(index);
  await writeFile(socialPath, source, "utf8");
}
