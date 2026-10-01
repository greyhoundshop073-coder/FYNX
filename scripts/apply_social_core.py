from pathlib import Path

root = Path('.')

routes = root / 'backend/socialRoutes.js'
s = routes.read_text()
if '/api/social/feed' not in s:
    addition = r'''

  // FYNX Social Core — server-backed posts, likes, comments, follows and protected media.
  let socialSchemaPromise;
  const ensureSocialSchema = async () => {
    if (!socialSchemaPromise) {
      socialSchemaPromise = pool.query(`
        CREATE TABLE IF NOT EXISTS social_posts (
          id BIGSERIAL PRIMARY KEY,
          author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          text TEXT NOT NULL DEFAULT '',
          visibility TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC','FRIENDS_ONLY')),
          media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL,
          media_type TEXT,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS social_posts_created_idx ON social_posts(created_at DESC);
        CREATE INDEX IF NOT EXISTS social_posts_author_idx ON social_posts(author_id, created_at DESC);
        CREATE TABLE IF NOT EXISTS social_post_likes (
          post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY(post_id, user_id)
        );
        CREATE TABLE IF NOT EXISTS social_post_comments (
          id BIGSERIAL PRIMARY KEY,
          post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE,
          author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          text TEXT NOT NULL,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS social_post_comments_post_idx ON social_post_comments(post_id, created_at ASC);
        CREATE TABLE IF NOT EXISTS social_follows (
          follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          followed_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY(follower_id, followed_id),
          CHECK(follower_id <> followed_id)
        );
        CREATE INDEX IF NOT EXISTS social_follows_followed_idx ON social_follows(followed_id, created_at DESC);
      `).catch((error) => { socialSchemaPromise = undefined; throw error; });
    }
    return socialSchemaPromise;
  };

  const visibleSocialPost = async (postId, userId) => {
    const result = await pool.query(`
      SELECT 1 FROM social_posts p
      WHERE p.id = $1 AND (
        p.author_id = $2 OR p.visibility = 'PUBLIC' OR
        (p.visibility = 'FRIENDS_ONLY' AND EXISTS (
          SELECT 1 FROM friendships f
          WHERE ((f.user_id = p.author_id AND f.friend_id = $2) OR (f.user_id = $2 AND f.friend_id = p.author_id))
            AND f.status = 'accepted'
        ))
      ) AND NOT EXISTS (
        SELECT 1 FROM blocks b
        WHERE (b.blocker_id = $2 AND b.blocked_id = p.author_id) OR (b.blocker_id = p.author_id AND b.blocked_id = $2)
      )`, [postId, userId]);
    return Boolean(result.rowCount);
  };

  app.get('/api/social/feed', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      const limit = Math.min(Math.max(Number(req.query?.limit) || 50, 1), 100);
      const result = await pool.query(`
        SELECT p.id, p.author_id, u.username AS author_username, u.display_name AS author_display_name,
               p.text, p.visibility, p.media_id, p.media_type,
               EXTRACT(EPOCH FROM p.created_at) * 1000 AS timestamp,
               (SELECT COUNT(*) FROM social_post_likes l WHERE l.post_id = p.id) AS like_count,
               (SELECT COUNT(*) FROM social_post_comments c WHERE c.post_id = p.id) AS comment_count,
               EXISTS(SELECT 1 FROM social_post_likes l WHERE l.post_id = p.id AND l.user_id = $1) AS liked_by_current_user,
               EXISTS(SELECT 1 FROM social_follows f WHERE f.follower_id = $1 AND f.followed_id = p.author_id) AS followed_by_current_user
        FROM social_posts p JOIN users u ON u.id = p.author_id
        WHERE (p.author_id = $1 OR p.visibility = 'PUBLIC' OR
          (p.visibility = 'FRIENDS_ONLY' AND EXISTS(
            SELECT 1 FROM friendships fr WHERE ((fr.user_id = p.author_id AND fr.friend_id = $1) OR (fr.user_id = $1 AND fr.friend_id = p.author_id)) AND fr.status = 'accepted'
          )))
          AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id = $1 AND b.blocked_id = p.author_id) OR (b.blocker_id = p.author_id AND b.blocked_id = $1))
        ORDER BY p.created_at DESC LIMIT $2`, [req.user.sub, limit]);
      return res.json({ posts: result.rows.map(row => ({
        id: String(row.id), authorId: String(row.author_id), authorUsername: row.author_username,
        authorDisplayName: row.author_display_name, text: row.text, visibility: row.visibility,
        mediaId: row.media_id == null ? null : String(row.media_id), mediaType: row.media_type || null,
        mediaUrl: row.media_id == null ? null : `/api/social/media/${row.media_id}`,
        timestamp: Number(row.timestamp), likeCount: Number(row.like_count), commentCount: Number(row.comment_count),
        likedByCurrentUser: Boolean(row.liked_by_current_user), followedByCurrentUser: Boolean(row.followed_by_current_user)
      })) });
    } catch (error) { console.error('social feed', error); return res.status(500).json({ error: 'social feed failed' }); }
  });

  app.post('/api/social/posts', auth, async (req, res) => {
    try {
      await ensureSocialSchema();
      const text = typeof req.body?.text === 'string' ? req.body.text.trim().slice(0, 4000) : '';
      const visibility = req.body?.visibility === 'FRIENDS_ONLY' ? 'FRIENDS_ONLY' : 'PUBLIC';
      const mediaId = req.body?.mediaId == null ? null : Number(req.body.mediaId);
      const mediaType = typeof req.body?.mediaType === 'string' ? req.body.mediaType.trim().toLowerCase() : null;
      if (!text && mediaId == null) return res.status(400).json({ error: 'post content is required' });
      if (mediaId != null) {
        if (!Number.isInteger(mediaId) || mediaId < 1) return res.status(400).json({ error: 'invalid media id' });
        if (!['image', 'video'].includes(mediaType)) return res.status(400).json({ error: 'post media must be an image or video' });
        const owned = await pool.query('SELECT id FROM message_media WHERE id = $1 AND owner_id = $2', [mediaId, req.user.sub]);
        if (!owned.rows[0]) return res.status(403).json({ error: 'media is not owned by this account' });
      }
      const result = await pool.query(`INSERT INTO social_posts(author_id,text,visibility,media_id,media_type) VALUES($1,$2,$3,$4,$5) RETURNING id`, [req.user.sub, text, visibility, mediaId, mediaType]);
      return res.status(201).json({ postId: String(result.rows[0].id) });
    } catch (error) { console.error('social create post', error); return res.status(500).json({ error: 'post creation failed' }); }
  });

  app.delete('/api/social/posts/:id', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const id = Number(req.params.id);
      if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: 'invalid post id' });
      const result = await pool.query('DELETE FROM social_posts WHERE id = $1 AND author_id = $2 RETURNING id', [id, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: 'post not found' });
      return res.json({ ok: true });
    } catch (error) { console.error('social delete post', error); return res.status(500).json({ error: 'post deletion failed' }); }
  });

  app.post('/api/social/posts/:id/like', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const id = Number(req.params.id);
      if (!Number.isInteger(id) || id < 1 || !(await visibleSocialPost(id, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const existing = await pool.query('SELECT 1 FROM social_post_likes WHERE post_id = $1 AND user_id = $2', [id, req.user.sub]);
      if (existing.rowCount) await pool.query('DELETE FROM social_post_likes WHERE post_id = $1 AND user_id = $2', [id, req.user.sub]);
      else await pool.query('INSERT INTO social_post_likes(post_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING', [id, req.user.sub]);
      const count = await pool.query('SELECT COUNT(*)::int AS count FROM social_post_likes WHERE post_id = $1', [id]);
      return res.json({ liked: !existing.rowCount, likeCount: count.rows[0].count });
    } catch (error) { console.error('social like', error); return res.status(500).json({ error: 'like failed' }); }
  });

  app.get('/api/social/posts/:id/likes', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const id = Number(req.params.id);
      if (!(await visibleSocialPost(id, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const result = await pool.query('SELECT u.id,u.username,u.display_name FROM social_post_likes l JOIN users u ON u.id=l.user_id WHERE l.post_id=$1 ORDER BY l.created_at DESC LIMIT 100', [id]);
      return res.json({ users: result.rows.map(row => ({ id: String(row.id), username: row.username, displayName: row.display_name })) });
    } catch (error) { console.error('social likes', error); return res.status(500).json({ error: 'likes lookup failed' }); }
  });

  app.get('/api/social/posts/:id/comments', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const id = Number(req.params.id);
      if (!(await visibleSocialPost(id, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const result = await pool.query('SELECT c.id,c.text,EXTRACT(EPOCH FROM c.created_at)*1000 AS timestamp,u.id AS author_id,u.username,u.display_name FROM social_post_comments c JOIN users u ON u.id=c.author_id WHERE c.post_id=$1 ORDER BY c.created_at ASC LIMIT 200', [id]);
      return res.json({ comments: result.rows.map(row => ({ id:String(row.id), text:row.text, timestamp:Number(row.timestamp), authorId:String(row.author_id), authorUsername:row.username, authorDisplayName:row.display_name })) });
    } catch (error) { console.error('social comments', error); return res.status(500).json({ error: 'comments lookup failed' }); }
  });

  app.post('/api/social/posts/:id/comments', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const id = Number(req.params.id); const text = typeof req.body?.text === 'string' ? req.body.text.trim().slice(0,1000) : '';
      if (!text) return res.status(400).json({ error: 'comment text is required' });
      if (!(await visibleSocialPost(id, req.user.sub))) return res.status(404).json({ error: 'post not found' });
      const result = await pool.query('INSERT INTO social_post_comments(post_id,author_id,text) VALUES($1,$2,$3) RETURNING id,EXTRACT(EPOCH FROM created_at)*1000 AS timestamp', [id, req.user.sub, text]);
      const author = await pool.query('SELECT username,display_name FROM users WHERE id=$1', [req.user.sub]);
      return res.status(201).json({ comment:{ id:String(result.rows[0].id), text, timestamp:Number(result.rows[0].timestamp), authorId:String(req.user.sub), authorUsername:author.rows[0]?.username || '', authorDisplayName:author.rows[0]?.display_name || '' } });
    } catch (error) { console.error('social add comment', error); return res.status(500).json({ error: 'comment failed' }); }
  });

  app.post('/api/social/follow/:username', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const target = await findUserByUsername(req.params.username.trim().toLowerCase().replace(/^@+/, ''));
      if (!target) return res.status(404).json({ error:'user not found' });
      if (String(target.id) === String(req.user.sub)) return res.status(400).json({ error:'cannot follow yourself' });
      const blocked = await pool.query('SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1', [req.user.sub,target.id]);
      if (blocked.rowCount) return res.status(403).json({ error:'follow unavailable' });
      await pool.query('INSERT INTO social_follows(follower_id,followed_id) VALUES($1,$2) ON CONFLICT DO NOTHING', [req.user.sub,target.id]);
      return res.status(201).json({ following:true });
    } catch (error) { console.error('social follow', error); return res.status(500).json({ error:'follow failed' }); }
  });

  app.delete('/api/social/follow/:username', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const target = await findUserByUsername(req.params.username.trim().toLowerCase().replace(/^@+/, ''));
      if (!target) return res.status(404).json({ error:'user not found' });
      await pool.query('DELETE FROM social_follows WHERE follower_id=$1 AND followed_id=$2', [req.user.sub,target.id]);
      return res.json({ following:false });
    } catch (error) { console.error('social unfollow', error); return res.status(500).json({ error:'unfollow failed' }); }
  });

  app.get('/api/social/media/:id', auth, async (req, res) => {
    try {
      await ensureSocialSchema(); const mediaId = Number(req.params.id);
      if (!Number.isInteger(mediaId) || mediaId < 1) return res.status(400).json({ error:'invalid media id' });
      const result = await pool.query(`SELECT mm.mime_type,mm.data FROM message_media mm JOIN social_posts p ON p.media_id=mm.id WHERE mm.id=$1 AND (p.author_id=$2 OR p.visibility='PUBLIC' OR (p.visibility='FRIENDS_ONLY' AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=p.author_id)) AND f.status='accepted'))) AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=p.author_id) OR (b.blocker_id=p.author_id AND b.blocked_id=$2)) ORDER BY p.created_at DESC LIMIT 1`, [mediaId,req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error:'media not found' });
      res.set('Cache-Control','private, max-age=3600'); res.type(result.rows[0].mime_type); return res.send(result.rows[0].data);
    } catch (error) { console.error('social media', error); return res.status(500).json({ error:'social media fetch failed' }); }
  });
'''
    idx = s.rfind('\n}')
    if idx < 0: raise SystemExit('socialRoutes closing brace not found')
    routes.write_text(s[:idx] + addition + s[idx:])

(root/'app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt').write_text(r'''package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object FynxRemoteSocialClient {
    data class RemotePost(val id:String,val authorId:String,val authorUsername:String,val authorDisplayName:String,val text:String,val visibility:String,val mediaId:String?,val mediaType:String?,val mediaUrl:String?,val timestamp:Long,val likeCount:Int,val commentCount:Int,val likedByCurrentUser:Boolean,val followedByCurrentUser:Boolean)
    data class RemoteComment(val id:String,val text:String,val timestamp:Long,val authorId:String,val authorUsername:String,val authorDisplayName:String)
    data class RemoteUser(val id:String,val username:String,val displayName:String)

    suspend fun feed(context:Context):Result<List<RemotePost>> = FynxBackendClient.get(context,"/api/social/feed?limit=100").mapCatching { raw ->
        val a=JSONObject(raw).optJSONArray("posts")?:JSONArray(); buildList { for(i in 0 until a.length()) add(fromPost(a.getJSONObject(i))) }
    }
    suspend fun createPost(context:Context,text:String,visibility:FynxPostVisibility,mediaUri:Uri?):Result<Unit> = runCatching {
        val media=mediaUri?.let { uri -> val mime=context.contentResolver.getType(uri)?.lowercase()? : ""; val type=when { mime.startsWith("image/")->"image"; mime.startsWith("video/")->"video"; else->throw IllegalArgumentException("Select an image or video.") }; FynxProductionMessaging.uploadMedia(context,uri,mime).getOrThrow() to type }
        val body=JSONObject().apply { put("text",text.trim());put("visibility",visibility.name);put("mediaId",media?.first?.id?:JSONObject.NULL);put("mediaType",media?.second?:JSONObject.NULL) }
        FynxBackendClient.postJson(context,"/api/social/posts",body.toString()).getOrThrow(); Unit
    }
    suspend fun toggleLike(context:Context,id:String):Result<Pair<Boolean,Int>> { val postId=id.toLongOrNull()?:return Result.failure(IllegalArgumentException("invalid post id")); return FynxBackendClient.postJson(context,"/api/social/posts/$postId/like","{}").mapCatching { val o=JSONObject(it);o.optBoolean("liked") to o.optInt("likeCount",0) } }
    suspend fun comments(context:Context,id:String):Result<List<RemoteComment>> { val postId=id.toLongOrNull()?:return Result.failure(IllegalArgumentException("invalid post id"));return FynxBackendClient.get(context,"/api/social/posts/$postId/comments").mapCatching{raw->val a=JSONObject(raw).optJSONArray("comments")?:JSONArray();buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(RemoteComment(o.optString("id"),o.optString("text"),o.optDouble("timestamp").toLong(),o.optString("authorId"),o.optString("authorUsername"),o.optString("authorDisplayName")))}}}}
    suspend fun addComment(context:Context,id:String,text:String):Result<RemoteComment> { val postId=id.toLongOrNull()?:return Result.failure(IllegalArgumentException("invalid post id"));return FynxBackendClient.postJson(context,"/api/social/posts/$postId/comments",JSONObject().put("text",text.trim()).toString()).mapCatching{val o=JSONObject(it).getJSONObject("comment");RemoteComment(o.optString("id"),o.optString("text"),o.optDouble("timestamp").toLong(),o.optString("authorId"),o.optString("authorUsername"),o.optString("authorDisplayName"))}}
    suspend fun likes(context:Context,id:String):Result<List<RemoteUser>> { val postId=id.toLongOrNull()?:return Result.failure(IllegalArgumentException("invalid post id"));return FynxBackendClient.get(context,"/api/social/posts/$postId/likes").mapCatching{raw->val a=JSONObject(raw).optJSONArray("users")?:JSONArray();buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);add(RemoteUser(o.optString("id"),o.optString("username"),o.optString("displayName")))}}}}
    suspend fun follow(context:Context,username:String,following:Boolean):Result<Boolean> { val path="/api/social/follow/${java.net.URLEncoder.encode(username.trim().removePrefix("@"),"UTF-8")}";return if(following)FynxBackendClient.delete(context,path).map{false}else FynxBackendClient.postJson(context,path,"{}").map{true} }
    suspend fun deletePost(context:Context,id:String):Result<Unit> { val postId=id.toLongOrNull()?:return Result.failure(IllegalArgumentException("invalid post id"));return FynxBackendClient.delete(context,"/api/social/posts/$postId").map{Unit} }
    private fun fromPost(o:JSONObject)=RemotePost(o.optString("id"),o.optString("authorId"),o.optString("authorUsername"),o.optString("authorDisplayName"),o.optString("text"),o.optString("visibility","PUBLIC"),o.optString("mediaId").takeIf{it.isNotBlank()&&it!="null"},o.optString("mediaType").takeIf{it.isNotBlank()&&it!="null"},o.optString("mediaUrl").takeIf{it.isNotBlank()},o.optDouble("timestamp").toLong(),o.optInt("likeCount",0),o.optInt("commentCount",0),o.optBoolean("likedByCurrentUser"),o.optBoolean("followedByCurrentUser"))
}
''')

home = root/'app/src/main/java/com/fynx/app/ui/HomePanel.kt'
h = home.read_text()
start = h.find('        item { SectionHeader("Your feed", "Find people", onOpenFindPeople) }')
end = h.find('        item { SectionHeader("Your conversations", "Open Chats", onOpenChats)', start)
if start >= 0 and end >= 0:
    h = h[:start] + '        item { FynxRemoteHomeSocialPanel(currentUsername = displayUsername, onOpenFindPeople = onOpenFindPeople) }\n' + h[end:]
    home.write_text(h)
'''
(root/'app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt').write_text(r'''package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

@Composable fun FynxRemoteHomeSocialPanel(currentUsername:String,onOpenFindPeople:()->Unit){
    val context=LocalContext.current;val scope=rememberCoroutineScope();var posts by remember{mutableStateOf<List<FynxRemoteSocialClient.RemotePost>>(emptyList())};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};var composer by remember{mutableStateOf(false)};var selected by remember{mutableStateOf<Uri?>(null)};var text by remember{mutableStateOf("")};var visibility by remember{mutableStateOf(FynxPostVisibility.PUBLIC)};var commentsPost by remember{mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null)};var likesPost by remember{mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null)};var busy by remember{mutableStateOf(false)};var actionError by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){selected=it}
    fun reload(){scope.launch{loading=true;FynxRemoteSocialClient.feed(context).onSuccess{posts=it;error=null}.onFailure{error=it.message?:"Unable to load your feed."};loading=false}}
    LaunchedEffect(Unit){reload()}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Your feed",style=MaterialTheme.typography.titleMedium);Text("Real posts from your FYNX network",style=MaterialTheme.typography.bodySmall)};Row{IconButton(onClick=::reload){Icon(Icons.Default.Refresh,"Refresh feed")};IconButton(onClick={composer=true}){Icon(Icons.Default.Add,"Create post")}}}
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(!loading&&posts.isEmpty()&&error==null)EmptyHomeCard("Your feed is ready","There are no visible posts yet. Create a post or find real people to build your FYNX circle.","Find People",onOpenFindPeople)
        posts.forEach{post->RemotePostCard(post,currentUsername,onLike={id->scope.launch{FynxRemoteSocialClient.toggleLike(context,id).onSuccess{liked,count->posts=posts.map{if(it.id==id)it.copy(likedByCurrentUser=liked,likeCount=count)else it}}.onFailure{actionError=it.message}}},onComment={commentsPost=post},onLikes={likesPost=post},onFollow={following->scope.launch{FynxRemoteSocialClient.follow(context,post.authorUsername,following).onSuccess{now->posts=posts.map{if(it.authorUsername.equals(post.authorUsername,true))it.copy(followedByCurrentUser=now)else it}}.onFailure{actionError=it.message}}},onDelete={scope.launch{FynxRemoteSocialClient.deletePost(context,post.id).onSuccess{posts=posts.filterNot{it.id==post.id}}.onFailure{actionError=it.message}}})}
        actionError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }
    if(composer)AlertDialog(onDismissRequest={if(!busy){composer=false;selected=null}},title={Text("Create a post")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(text,{text=it.take(4000)},Modifier.fillMaxWidth(),minLines=3,maxLines=7,placeholder={Text("Share something with your FYNX circle…")});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={picker.launch("image/*")},Modifier.weight(1f)){Icon(Icons.Default.AddAPhoto,null);Spacer(Modifier.width(5.dp));Text("Photo")};OutlinedButton(onClick={picker.launch("video/*")},Modifier.weight(1f)){Icon(Icons.Default.Videocam,null);Spacer(Modifier.width(5.dp));Text("Video")}};if(selected!=null)Text("Media selected",style=MaterialTheme.typography.bodySmall);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(visibility==FynxPostVisibility.PUBLIC,{visibility=FynxPostVisibility.PUBLIC},label={Text("Public")});FilterChip(visibility==FynxPostVisibility.FRIENDS_ONLY,{visibility=FynxPostVisibility.FRIENDS_ONLY},label={Text("Friends")})}}},confirmButton={Button(enabled=!busy,onClick={scope.launch{busy=true;FynxRemoteSocialClient.createPost(context,text,visibility,selected).onSuccess{composer=false;text="";selected=null;reload()}.onFailure{actionError=it.message?:"Post failed."};busy=false}}){Text(if(busy)"Publishing…"else"Post")}},dismissButton={TextButton(enabled=!busy,onClick={composer=false;selected=null}){Text("Cancel")}})
    commentsPost?.let{post->CommentsDialog(post){commentsPost=null}}
    likesPost?.let{post->LikesDialog(post){likesPost=null}}
}

@Composable private fun RemotePostCard(post:FynxRemoteSocialClient.RemotePost,currentUsername:String,onLike:(String)->Unit,onComment:()->Unit,onLikes:()->Unit,onFollow:(Boolean)->Unit,onDelete:()->Unit){val mine=post.authorUsername.equals(currentUsername.removePrefix("@"),true);Card(Modifier.fillMaxWidth(),shape=FynxDesign.LargeCardShape,colors=CardDefaults.cardColors(FynxDesign.Surface),border=BorderStroke(1.dp,FynxDesign.Outline.copy(alpha=.55f))){Column{Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){FynxAvatar(post.authorUsername,Modifier.size(46.dp).clip(CircleShape));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(post.authorDisplayName.ifBlank{post.authorUsername.removePrefix("@")} ,style=MaterialTheme.typography.titleSmall);Text("@${post.authorUsername.removePrefix("@")} • ${relativeTime(post.timestamp)} • ${if(post.visibility=="PUBLIC")"Public"else"Friends"}",style=MaterialTheme.typography.labelSmall)};if(mine)IconButton(onClick=onDelete){Icon(Icons.Default.DeleteOutline,"Delete post")}else TextButton(onClick={onFollow(post.followedByCurrentUser)}){Text(if(post.followedByCurrentUser)"Following"else"Follow")}};if(post.text.isNotBlank())Text(post.text,Modifier.padding(horizontal=14.dp,vertical=4.dp));post.mediaUrl?.let{RemoteSocialMedia(it,post.mediaType)};Row(Modifier.fillMaxWidth().padding(horizontal=6.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={onLike(post.id)}){Icon(if(post.likedByCurrentUser)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null);Spacer(Modifier.width(4.dp));Text(post.likeCount.toString())};TextButton(onClick=onLikes){Text("Likes")};TextButton(onClick=onComment){Icon(Icons.Default.ChatBubbleOutline,null);Spacer(Modifier.width(4.dp));Text(post.commentCount.toString())}}}}}
@Composable private fun RemoteSocialMedia(url:String,mediaType:String?){val context=LocalContext.current;var file by remember(url){mutableStateOf<File?>(null)};LaunchedEffect(url){file=withContext(Dispatchers.IO){downloadMedia(context,url,mediaType)}};if(file==null)Box(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()}else if(mediaType=="video")AndroidView(factory={ctx->VideoView(ctx).apply{layoutParams=ViewGroup.LayoutParams(-1,640);setMediaController(MediaController(ctx));setVideoURI(Uri.fromFile(file));setOnPreparedListener{it.isLooping=true;start()}}},modifier=Modifier.fillMaxWidth().height(320.dp))else{var bitmap by remember(file){mutableStateOf<android.graphics.Bitmap?>(null)};LaunchedEffect(file){bitmap=withContext(Dispatchers.IO){runCatching{BitmapFactory.decodeFile(file!!.absolutePath)}.getOrNull()}};bitmap?.let{Image(it.asImageBitmap(),"Post media",Modifier.fillMaxWidth().heightIn(min=240.dp,max=520.dp),contentScale=ContentScale.Crop)}}}
private fun downloadMedia(context:Context,path:String,mediaType:String?):File?=runCatching{val c=(URL(FynxBackendClient.baseUrl(context)+path).openConnection() as HttpURLConnection).apply{connectTimeout=10000;readTimeout=20000;setRequestProperty("Authorization","Bearer ${FynxBackendClient.accessToken(context)?:""}")};try{if(c.responseCode !in 200..299)return null;val f=File.createTempFile("fynx_social_",if(mediaType=="video")".mp4"else".jpg",context.cacheDir);c.inputStream.use{input->FileOutputStream(f).use{output->input.copyTo(output)}};f}finally{c.disconnect()}}.getOrNull()

@Composable private fun CommentsDialog(post:FynxRemoteSocialClient.RemotePost,onClose:()->Unit){val context=LocalContext.current;val scope=rememberCoroutineScope();var comments by remember(post.id){mutableStateOf<List<FynxRemoteSocialClient.RemoteComment>>(emptyList())};var text by remember{mutableStateOf("")};var loading by remember{mutableStateOf(true)};LaunchedEffect(post.id){FynxRemoteSocialClient.comments(context,post.id).onSuccess{comments=it};loading=false};AlertDialog(onDismissRequest=onClose,title={Text("Comments")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){if(loading)CircularProgressIndicator();comments.forEach{c->Column{Text(c.authorDisplayName.ifBlank{c.authorUsername},style=MaterialTheme.typography.labelLarge);Text(c.text);Text(relativeTime(c.timestamp),style=MaterialTheme.typography.labelSmall)}};if(!loading&&comments.isEmpty())Text("No comments yet.");OutlinedTextField(text,{text=it.take(1000)},Modifier.fillMaxWidth(),placeholder={Text("Write a comment…")},singleLine=true)}},confirmButton={TextButton(onClick={if(text.isNotBlank())scope.launch{FynxRemoteSocialClient.addComment(context,post.id,text).onSuccess{comments=comments+it;text=""}}}){Text("Comment")}},dismissButton={TextButton(onClick=onClose){Text("Close")}})}
@Composable private fun LikesDialog(post:FynxRemoteSocialClient.RemotePost,onClose:()->Unit){val context=LocalContext.current;var users by remember(post.id){mutableStateOf<List<FynxRemoteSocialClient.RemoteUser>>(emptyList())};LaunchedEffect(post.id){FynxRemoteSocialClient.likes(context,post.id).onSuccess{users=it}};AlertDialog(onDismissRequest=onClose,title={Text("People who liked this")},text={LazyColumn{items(users){u->Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){FynxAvatar(u.username,Modifier.size(38.dp));Spacer(Modifier.width(10.dp));Column{Text(u.displayName.ifBlank{u.username});Text("@${u.username.removePrefix("@")}",style=MaterialTheme.typography.labelSmall)}}}}},confirmButton={TextButton(onClick=onClose){Text("Close")}})}
private fun relativeTime(timestamp:Long):String{val elapsed=(System.currentTimeMillis()-timestamp).coerceAtLeast(0L);val minutes=TimeUnit.MILLISECONDS.toMinutes(elapsed);return when{minutes<1->"now";minutes<60->"${minutes}m";minutes<1440->"${TimeUnit.MINUTES.toHours(minutes)}h";else->"${TimeUnit.MINUTES.toDays(minutes)}d"}}
''')

print('Social core source changes applied.')
