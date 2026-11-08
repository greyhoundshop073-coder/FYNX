import path from "node:path";
import { fileURLToPath } from "node:url";
import { readFile, writeFile } from "node:fs/promises";

export async function installHomeCommentReactions() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  let source = await readFile(socialPath, "utf8");
  if (source.includes("fynxHomeCommentReactionsBatch")) return;
  const marker = "\n}\n";
  const index = source.lastIndexOf(marker);
  if (index < 0) throw new Error("Home comment reactions could not locate social route closing marker");
  const routes = `
  // fynxHomeCommentReactionsBatch: durable emoji reactions on comments and replies.
  const ensureHomeCommentReactionSchema = async () => {
    await pool.query(\`
      CREATE TABLE IF NOT EXISTS social_comment_reactions (
        comment_id BIGINT NOT NULL REFERENCES social_post_comments(id) ON DELETE CASCADE,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        reaction_type TEXT NOT NULL CHECK (reaction_type IN ('LIKE','LOVE','LAUGH','WOW','SAD','FIRE','THUMBS_UP')),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY(comment_id,user_id)
      );
      CREATE INDEX IF NOT EXISTS social_comment_reactions_comment_type_idx ON social_comment_reactions(comment_id,reaction_type);
      CREATE INDEX IF NOT EXISTS social_comment_reactions_user_idx ON social_comment_reactions(user_id,updated_at DESC);
    \`);
  };
  app.get('/api/social/posts/:id/comment-reactions', auth, async (req,res) => {
    try {
      await ensureSocialSchema(); await ensureHomeCommentReactionSchema();
      const postId=Number(req.params.id);
      if (!Number.isSafeInteger(postId) || postId<1 || !(await visibleSocialPost(postId,req.user.sub))) return res.status(404).json({error:'post not found'});
      const ids=String(req.query?.ids||'').split(',').map(Number).filter(x=>Number.isSafeInteger(x)&&x>0).slice(0,100);
      if (!ids.length) return res.json({reactions:{}});
      const rows=await pool.query(\`
        SELECT c.id::text AS comment_id,r.reaction_type,COUNT(r.user_id)::int AS count,
               BOOL_OR(r.user_id=$3) FILTER (WHERE r.user_id IS NOT NULL) AS current_user_reacted,
               MAX(r.reaction_type) FILTER (WHERE r.user_id=$3) AS current_reaction
          FROM social_post_comments c LEFT JOIN social_comment_reactions r ON r.comment_id=c.id
         WHERE c.post_id=$1 AND c.id=ANY($2::bigint[])
           AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3))
         GROUP BY c.id,r.reaction_type ORDER BY c.id
      \`,[postId,ids,req.user.sub]);
      const reactions={};
      for (const row of rows.rows) {
        const entry=reactions[row.comment_id] ||= {counts:{},currentReaction:null};
        if (row.reaction_type) entry.counts[row.reaction_type]=Number(row.count||0);
        if (row.current_reaction) entry.currentReaction=row.current_reaction;
      }
      return res.json({reactions});
    } catch(error) { console.error('home comment reactions lookup',error); return res.status(500).json({error:'comment reactions unavailable'}); }
  });
  app.post('/api/social/posts/:id/comments/:commentId/reaction', auth, async (req,res) => {
    try {
      await ensureSocialSchema(); await ensureHomeCommentReactionSchema();
      const postId=Number(req.params.id), commentId=Number(req.params.commentId);
      const reaction=String(req.body?.reaction||'').trim().toUpperCase();
      const allowed=new Set(['LIKE','LOVE','LAUGH','WOW','SAD','FIRE','THUMBS_UP']);
      if (!Number.isSafeInteger(postId)||postId<1||!Number.isSafeInteger(commentId)||commentId<1||!allowed.has(reaction)) return res.status(400).json({error:'invalid comment reaction'});
      if (!(await visibleSocialPost(postId,req.user.sub))) return res.status(404).json({error:'post not found'});
      const comment=await pool.query(\`SELECT c.id,c.author_id FROM social_post_comments c WHERE c.id=$1 AND c.post_id=$2 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3))\`,[commentId,postId,req.user.sub]);
      if (!comment.rows[0]) return res.status(404).json({error:'comment not found'});
      const previousReaction = await pool.query('SELECT reaction_type FROM social_comment_reactions WHERE comment_id=$1 AND user_id=$2',[commentId,req.user.sub]);
      await pool.query(\`INSERT INTO social_comment_reactions(comment_id,user_id,reaction_type) VALUES($1,$2,$3)
        ON CONFLICT(comment_id,user_id) DO UPDATE SET reaction_type=EXCLUDED.reaction_type,updated_at=NOW()\`,[commentId,req.user.sub,reaction]);
      if ((!previousReaction.rows[0] || previousReaction.rows[0].reaction_type !== reaction) && String(comment.rows[0].author_id) !== String(req.user.sub)) {
        const actor = await pool.query('SELECT username FROM users WHERE id=$1',[req.user.sub]);
        await queueFynxNotification(pool,{
          userId: comment.rows[0].author_id,
          type: 'REACTION',
          title: '@' + (actor.rows[0]?.username || 'A FYNX user') + ' reacted to your comment',
          message: reaction,
          targetId: postId,
          sourceUsername: actor.rows[0]?.username || null,
          route: 'fynx://post/' + postId + '?comment=' + commentId,
          notificationId: 'comment-reaction-' + commentId + '-' + req.user.sub + '-' + reaction
        }).catch(error => console.error('home comment reaction notification failed', error));
      }
      const result=await pool.query('SELECT reaction_type,COUNT(*)::int AS count FROM social_comment_reactions WHERE comment_id=$1 GROUP BY reaction_type',[commentId]);
      const counts={}; for (const row of result.rows) counts[row.reaction_type]=Number(row.count||0);
      const current=await pool.query('SELECT reaction_type FROM social_comment_reactions WHERE comment_id=$1 AND user_id=$2',[commentId,req.user.sub]);
      return res.json({counts,currentReaction:current.rows[0]?.reaction_type||null});
    } catch(error) { console.error('home comment reaction set',error); return res.status(500).json({error:'comment reaction update failed'}); }
  });
  app.delete('/api/social/posts/:id/comments/:commentId/reaction', auth, async (req,res) => {
    try {
      await ensureSocialSchema(); await ensureHomeCommentReactionSchema();
      const postId=Number(req.params.id), commentId=Number(req.params.commentId);
      if (!Number.isSafeInteger(postId)||postId<1||!Number.isSafeInteger(commentId)||commentId<1) return res.status(400).json({error:'invalid comment reference'});
      if (!(await visibleSocialPost(postId,req.user.sub))) return res.status(404).json({error:'post not found'});
      const comment=await pool.query('SELECT c.id FROM social_post_comments c WHERE c.id=$1 AND c.post_id=$2 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3))',[commentId,postId,req.user.sub]);
      if (!comment.rows[0]) return res.status(404).json({error:'comment not found'});
      await pool.query('DELETE FROM social_comment_reactions WHERE comment_id=$1 AND user_id=$2',[commentId,req.user.sub]);
      const result=await pool.query('SELECT reaction_type,COUNT(*)::int AS count FROM social_comment_reactions WHERE comment_id=$1 GROUP BY reaction_type',[commentId]);
      const counts={}; for (const row of result.rows) counts[row.reaction_type]=Number(row.count||0);
      return res.json({counts,currentReaction:null});
    } catch(error) { console.error('home comment reaction delete',error); return res.status(500).json({error:'comment reaction removal failed'}); }
  });
`;
  source=source.slice(0,index)+routes+source.slice(index);
  await writeFile(socialPath,source,"utf8");
}
