import path from "node:path";
import { fileURLToPath } from "node:url";
import { readFile, writeFile } from "node:fs/promises";

/** Installs protected media comments, linked FYNX video references and comment engagement APIs. */
export async function installHomeCommentExperience() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  let source = await readFile(socialPath, "utf8");
  if (source.includes("fynxHomeCommentExperienceBatch")) return;

  const selectNeedle = "c.id,c.post_id,c.parent_comment_id,c.text,EXTRACT";
  if (!source.includes(selectNeedle)) throw new Error("Comment experience could not locate paginated comment queries");
  source = source.replaceAll(selectNeedle, "c.id,c.post_id,c.parent_comment_id,c.text,c.media_id,c.media_type,c.linked_post_id,EXTRACT");

  const mappingNeedle = "text: row.text, timestamp: Number(row.timestamp),";
  if (!source.includes(mappingNeedle)) throw new Error("Comment experience could not locate comment response mappings");
  source = source.replaceAll(mappingNeedle, "text: row.text, mediaId: row.media_id == null ? null : String(row.media_id), mediaType: row.media_type || null, mediaUrl: row.media_id == null ? null : '/api/social/comment-media/' + row.media_id, linkedPostId: row.linked_post_id == null ? null : String(row.linked_post_id), timestamp: Number(row.timestamp),");

  const ensureRouteSchema = (input, routeMarker) => {
    const start = input.indexOf(routeMarker);
    if (start < 0) throw new Error("Comment experience could not locate " + routeMarker);
    const end = input.indexOf(String.fromCharCode(10) + "  });", start);
    if (end < 0) throw new Error("Comment experience could not locate end of " + routeMarker);
    const route = input.slice(start, end);
    if (!route.includes("await ensureHomeCommentExperienceSchema();")) {
      const needle = "await ensureSocialSchema();";
      const at = route.indexOf(needle);
      if (at < 0) throw new Error("Comment experience route misses social schema initialization");
      const patched = route.slice(0, at + needle.length) + String.fromCharCode(10) + "      await ensureHomeCommentExperienceSchema();" + route.slice(at + needle.length);
      input = input.slice(0, start) + patched + input.slice(end);
    }
    return input;
  };
  source = ensureRouteSchema(source, "app.get('/api/social/posts/:id/comments/page'");
  source = ensureRouteSchema(source, "app.get('/api/social/posts/:id/comments/:commentId/replies'");

  const routes = `
  // fynxHomeCommentExperienceBatch: durable media, linked-video and action-bar engagement.
  const ensureHomeCommentExperienceSchema = async () => {
    await pool.query([
      "ALTER TABLE social_post_comments ADD COLUMN IF NOT EXISTS media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL",
      "ALTER TABLE social_post_comments ADD COLUMN IF NOT EXISTS media_type TEXT",
      "ALTER TABLE social_post_comments ADD COLUMN IF NOT EXISTS linked_post_id BIGINT REFERENCES social_posts(id) ON DELETE SET NULL",
      "CREATE TABLE IF NOT EXISTS social_comment_reposts (comment_id BIGINT NOT NULL REFERENCES social_post_comments(id) ON DELETE CASCADE,user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(comment_id,user_id))",
      "CREATE TABLE IF NOT EXISTS social_saved_comments (comment_id BIGINT NOT NULL REFERENCES social_post_comments(id) ON DELETE CASCADE,user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(comment_id,user_id))",
      "CREATE TABLE IF NOT EXISTS social_comment_views (comment_id BIGINT NOT NULL REFERENCES social_post_comments(id) ON DELETE CASCADE,user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(comment_id,user_id))",
      "CREATE INDEX IF NOT EXISTS social_comment_reposts_user_idx ON social_comment_reposts(user_id,created_at DESC)",
      "CREATE INDEX IF NOT EXISTS social_saved_comments_user_idx ON social_saved_comments(user_id,created_at DESC)",
      "CREATE INDEX IF NOT EXISTS social_comment_views_comment_idx ON social_comment_views(comment_id,created_at DESC)"
    ].join(";"));
  };

  const visibleHomeComment = async (postId, commentId, userId) => {
    if (!(await visibleSocialPost(postId, userId))) return null;
    const result = await pool.query(
      "SELECT c.id,c.post_id,c.author_id,c.media_id,c.media_type,c.linked_post_id FROM social_post_comments c WHERE c.id=$1 AND c.post_id=$2 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3))",
      [commentId,postId,userId]
    );
    return result.rows[0] || null;
  };

  app.post('/api/social/posts/:id/comments/experience',auth,async(req,res)=>{
    try {
      await ensureSocialSchema();
      await ensureHomeCommentExperienceSchema();
      const postId=Number(req.params.id);
      const parentId=req.body?.parentCommentId == null || req.body.parentCommentId === '' ? null : Number(req.body.parentCommentId);
      const text=typeof req.body?.text === 'string' ? req.body.text.trim() : '';
      const mediaId=req.body?.mediaId == null ? null : Number(req.body.mediaId);
      const mediaType=typeof req.body?.mediaType === 'string' ? req.body.mediaType.trim().toLowerCase() : null;
      const linkedPostId=req.body?.linkedPostId == null || req.body.linkedPostId === '' ? null : Number(req.body.linkedPostId);
      if (!Number.isSafeInteger(postId)||postId<1) return res.status(400).json({error:'invalid post id'});
      if (parentId!==null&&(!Number.isSafeInteger(parentId)||parentId<1)) return res.status(400).json({error:'invalid parent comment id'});
      if (mediaId!==null&&(!Number.isSafeInteger(mediaId)||mediaId<1)) return res.status(400).json({error:'invalid media id'});
      if (linkedPostId!==null&&(!Number.isSafeInteger(linkedPostId)||linkedPostId<1)) return res.status(400).json({error:'invalid linked video post id'});
      if (text.length>1000||(!text&&mediaId===null&&linkedPostId===null)) return res.status(400).json({error:'add text, a photo/video, or a linked FYNX video'});
      if (!(await visibleSocialPost(postId,req.user.sub))) return res.status(404).json({error:'post not found'});
      if (mediaId!==null) {
        if (mediaType!=='image'&&mediaType!=='video') return res.status(400).json({error:'comment media must be an image or video'});
        const media=await pool.query("SELECT id,mime_type FROM message_media WHERE id=$1 AND owner_id=$2",[mediaId,req.user.sub]);
        const item=media.rows[0];
        if (!item) return res.status(403).json({error:'comment media is not owned by this account'});
        if ((mediaType==='image'&&!String(item.mime_type).startsWith('image/'))||(mediaType==='video'&&!String(item.mime_type).startsWith('video/'))) return res.status(400).json({error:'comment media type does not match its file'});
      } else if (mediaType!==null) return res.status(400).json({error:'comment media file is missing'});

      if (linkedPostId!==null) {
        if (!(await visibleSocialPost(linkedPostId,req.user.sub))) return res.status(404).json({error:'linked video is unavailable'});
        const linked=await pool.query("SELECT id FROM social_posts WHERE id=$1 AND media_type='video' AND media_id IS NOT NULL",[linkedPostId]);
        if (!linked.rows[0]) return res.status(400).json({error:'the linked FYNX post must contain a video'});
      }
      if (parentId!==null) {
        if (!(await visibleHomeComment(postId,parentId,req.user.sub))) return res.status(404).json({error:'parent comment not found'});
        const ancestry=await pool.query("WITH RECURSIVE comment_ancestry AS (SELECT c.id,c.parent_comment_id,0 AS depth FROM social_post_comments c WHERE c.id=$1 AND c.post_id=$2 UNION ALL SELECT p.id,p.parent_comment_id,a.depth+1 FROM social_post_comments p JOIN comment_ancestry a ON p.id=a.parent_comment_id WHERE a.depth<5) SELECT COALESCE(MAX(depth),0)::int AS depth FROM comment_ancestry",[parentId,postId]);
        if (Number(ancestry.rows[0]?.depth||0)>=5) return res.status(400).json({error:'maximum reply depth is 5'});
      }
      const inserted=await pool.query(
        "INSERT INTO social_post_comments(post_id,author_id,parent_comment_id,text,media_id,media_type,linked_post_id) VALUES($1,$2,$3,$4,$5,$6,$7) RETURNING id,post_id,parent_comment_id,text,media_id,media_type,linked_post_id,EXTRACT(EPOCH FROM created_at)*1000 AS timestamp",
        [postId,req.user.sub,parentId,text,mediaId,mediaType,linkedPostId]
      );
      const row=inserted.rows[0];
      const author=await pool.query("SELECT username,display_name FROM users WHERE id=$1",[req.user.sub]);
      return res.status(201).json({comment:{id:String(row.id),postId:String(row.post_id),parentCommentId:row.parent_comment_id==null?null:String(row.parent_comment_id),text:row.text,mediaId:row.media_id==null?null:String(row.media_id),mediaType:row.media_type||null,mediaUrl:row.media_id==null?null:'/api/social/comment-media/'+row.media_id,linkedPostId:row.linked_post_id==null?null:String(row.linked_post_id),timestamp:Number(row.timestamp),authorId:String(req.user.sub),authorUsername:author.rows[0]?.username||'',authorDisplayName:author.rows[0]?.display_name||''}});
    } catch(error) {
      console.error('social comment experience create',error);
      return res.status(500).json({error:'comment could not be created'});
    }
  });

  app.get('/api/social/posts/:id/comments/:commentId/linked-video',auth,async(req,res)=>{
    try {
      await ensureSocialSchema();
      await ensureHomeCommentExperienceSchema();
      const postId=Number(req.params.id), commentId=Number(req.params.commentId);
      if (!Number.isSafeInteger(postId)||postId<1||!Number.isSafeInteger(commentId)||commentId<1) return res.status(400).json({error:'invalid comment reference'});
      const comment=await visibleHomeComment(postId,commentId,req.user.sub);
      if (!comment||comment.linked_post_id==null) return res.status(404).json({error:'linked video not found'});
      const linkedId=Number(comment.linked_post_id);
      if (!(await visibleSocialPost(linkedId,req.user.sub))) return res.status(404).json({error:'linked video is unavailable'});
      const result=await pool.query("SELECT p.id,p.text,p.media_id,p.media_type,u.username,u.display_name FROM social_posts p JOIN users u ON u.id=p.author_id WHERE p.id=$1 AND p.media_type='video' AND p.media_id IS NOT NULL",[linkedId]);
      if (!result.rows[0]) return res.status(404).json({error:'linked video is unavailable'});
      const row=result.rows[0];
      return res.json({postId:String(row.id),text:row.text,authorUsername:row.username,authorDisplayName:row.display_name,mediaId:String(row.media_id),mediaType:'video',mediaUrl:'/api/social/media/'+row.media_id});
    } catch(error) {
      console.error('social linked comment video',error);
      return res.status(500).json({error:'linked video could not be opened'});
    }
  });

  app.get('/api/social/comment-media/:id',auth,async(req,res)=>{
    try {
      await ensureSocialSchema();
      await ensureHomeCommentExperienceSchema();
      const mediaId=Number(req.params.id);
      if (!Number.isSafeInteger(mediaId)||mediaId<1) return res.status(400).json({error:'invalid media id'});
      const result=await pool.query("SELECT mm.mime_type,mm.data,c.post_id FROM message_media mm JOIN social_post_comments c ON c.media_id=mm.id WHERE mm.id=$1 AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$2)) ORDER BY c.id DESC LIMIT 1",[mediaId,req.user.sub]);
      if (!result.rows[0]||!(await visibleSocialPost(Number(result.rows[0].post_id),req.user.sub))) return res.status(404).json({error:'comment media not found'});
      res.set('Cache-Control','private,max-age=3600');
      res.type(result.rows[0].mime_type);
      return res.send(result.rows[0].data);
    } catch(error) {
      console.error('social comment media',error);
      return res.status(500).json({error:'comment media could not be loaded'});
    }
  });

  app.get('/api/social/posts/:id/comment-engagement',auth,async(req,res)=>{
    try {
      await ensureSocialSchema();
      await ensureHomeCommentExperienceSchema();
      const postId=Number(req.params.id);
      if (!Number.isSafeInteger(postId)||postId<1||!(await visibleSocialPost(postId,req.user.sub))) return res.status(404).json({error:'post not found'});
      const ids=String(req.query?.ids||'').split(',').map(Number).filter(id=>Number.isSafeInteger(id)&&id>0).slice(0,100);
      if (!ids.length) return res.json({engagement:{}});
      const result=await pool.query(
        "SELECT c.id::text AS comment_id,(SELECT COUNT(*)::int FROM social_post_comments r WHERE r.parent_comment_id=c.id) AS reply_count,(SELECT COUNT(*)::int FROM social_comment_reposts r WHERE r.comment_id=c.id) AS repost_count,(SELECT COUNT(*)::int FROM social_comment_views v WHERE v.comment_id=c.id) AS view_count,(SELECT COUNT(*)::int FROM social_saved_comments s WHERE s.comment_id=c.id) AS bookmark_count,EXISTS(SELECT 1 FROM social_comment_reposts r WHERE r.comment_id=c.id AND r.user_id=$3) AS reposted,EXISTS(SELECT 1 FROM social_saved_comments s WHERE s.comment_id=c.id AND s.user_id=$3) AS bookmarked FROM social_post_comments c WHERE c.post_id=$1 AND c.id=ANY($2::bigint[]) AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id) OR (b.blocker_id=c.author_id AND b.blocked_id=$3))",
        [postId,ids,req.user.sub]
      );
      const engagement={};
      for (const row of result.rows) engagement[String(row.comment_id)]={replyCount:Number(row.reply_count||0),repostCount:Number(row.repost_count||0),viewCount:Number(row.view_count||0),bookmarkCount:Number(row.bookmark_count||0),reposted:Boolean(row.reposted),bookmarked:Boolean(row.bookmarked)};
      return res.json({engagement});
    } catch(error) {
      console.error('social comment engagement',error);
      return res.status(500).json({error:'comment engagement unavailable'});
    }
  });

  const updateHomeCommentCollection = async (req,res,table,enable) => {
    try {
      await ensureSocialSchema();
      await ensureHomeCommentExperienceSchema();
      const postId=Number(req.params.id), commentId=Number(req.params.commentId);
      if (!Number.isSafeInteger(postId)||postId<1||!Number.isSafeInteger(commentId)||commentId<1) return res.status(400).json({error:'invalid comment reference'});
      if (!(await visibleHomeComment(postId,commentId,req.user.sub))) return res.status(404).json({error:'comment not found'});
      if (enable) await pool.query('INSERT INTO '+table+'(comment_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[commentId,req.user.sub]);
      else await pool.query('DELETE FROM '+table+' WHERE comment_id=$1 AND user_id=$2',[commentId,req.user.sub]);
      const count=await pool.query('SELECT COUNT(*)::int AS count FROM '+table+' WHERE comment_id=$1',[commentId]);
      return res.json({enabled:enable,count:Number(count.rows[0]?.count||0)});
    } catch(error) {
      console.error('social comment collection update',error);
      return res.status(500).json({error:'comment action could not be updated'});
    }
  };

  app.post('/api/social/posts/:id/comments/:commentId/repost',auth,(req,res)=>updateHomeCommentCollection(req,res,'social_comment_reposts',true));
  app.delete('/api/social/posts/:id/comments/:commentId/repost',auth,(req,res)=>updateHomeCommentCollection(req,res,'social_comment_reposts',false));
  app.post('/api/social/posts/:id/comments/:commentId/bookmark',auth,(req,res)=>updateHomeCommentCollection(req,res,'social_saved_comments',true));
  app.delete('/api/social/posts/:id/comments/:commentId/bookmark',auth,(req,res)=>updateHomeCommentCollection(req,res,'social_saved_comments',false));

  app.post('/api/social/posts/:id/comments/:commentId/view',auth,async(req,res)=>{
    try {
      await ensureSocialSchema();
      await ensureHomeCommentExperienceSchema();
      const postId=Number(req.params.id), commentId=Number(req.params.commentId);
      if (!Number.isSafeInteger(postId)||postId<1||!Number.isSafeInteger(commentId)||commentId<1) return res.status(400).json({error:'invalid comment reference'});
      if (!(await visibleHomeComment(postId,commentId,req.user.sub))) return res.status(404).json({error:'comment not found'});
      await pool.query('INSERT INTO social_comment_views(comment_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[commentId,req.user.sub]);
      const count=await pool.query('SELECT COUNT(*)::int AS count FROM social_comment_views WHERE comment_id=$1',[commentId]);
      return res.json({viewCount:Number(count.rows[0]?.count||0)});
    } catch(error) {
      console.error('social comment view',error);
      return res.status(500).json({error:'comment view could not be recorded'});
    }
  });
  `;

  const marker = String.fromCharCode(10) + "}" + String.fromCharCode(10);
  const insertAt = source.lastIndexOf(marker);
  if (insertAt < 0) throw new Error("Comment experience could not locate the end of socialRoutes");
  source = source.slice(0,insertAt) + routes + source.slice(insertAt);
  await writeFile(socialPath,source,"utf8");
}
