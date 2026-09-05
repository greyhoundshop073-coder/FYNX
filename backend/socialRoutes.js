export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {
  let marketplaceSchemaPromise;
  const ensureMarketplaceSchema = async () => {
    if (!marketplaceSchemaPromise) {
      marketplaceSchemaPromise = pool.query(`
        CREATE TABLE IF NOT EXISTS marketplace_listings (
          id BIGSERIAL PRIMARY KEY,
          seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          store_name TEXT NOT NULL DEFAULT '',
          title TEXT NOT NULL,
          description TEXT NOT NULL DEFAULT '',
          price NUMERIC(14,2) NOT NULL CHECK (price > 0),
          currency TEXT NOT NULL DEFAULT 'NGN',
          category TEXT NOT NULL,
          condition TEXT NOT NULL DEFAULT 'NEW' CHECK (condition IN ('NEW','USED','REFURBISHED')),
          quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),
          location TEXT NOT NULL DEFAULT '',
          delivery_available BOOLEAN NOT NULL DEFAULT FALSE,
          pickup_available BOOLEAN NOT NULL DEFAULT TRUE,
          delivery_fee NUMERIC(14,2),
          media_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
          active BOOLEAN NOT NULL DEFAULT TRUE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        CREATE INDEX IF NOT EXISTS marketplace_listings_active_idx ON marketplace_listings (active, created_at DESC);
        CREATE INDEX IF NOT EXISTS marketplace_listings_seller_idx ON marketplace_listings (seller_id, active, created_at DESC);
        CREATE INDEX IF NOT EXISTS marketplace_listings_category_idx ON marketplace_listings (category, active, created_at DESC);
      `).catch((error) => {
        marketplaceSchemaPromise = undefined;
        throw error;
      });
    }
    return marketplaceSchemaPromise;
  };

  app.get("/api/users/search", auth, async (req, res) => {
    try {
      const query = typeof req.query?.q === "string" ? req.query.q.trim() : "";
      const mode = req.query?.mode === "phone" ? "phone" : "username";
      if (query.length < 2 || query.length > 80) return res.status(400).json({ error: "search query must be 2-80 characters" });
      if (mode === "phone") {
        const digits = query.replace(/[^0-9]/g, "");
        if (digits.length < 7 || digits.length > 15) return res.status(400).json({ error: "enter a valid phone number with country code" });
        const result = await pool.query(`SELECT id, username, display_name, created_at FROM users WHERE regexp_replace(phone, '[^0-9]', '', 'g') = $1 LIMIT 5`, [digits]);
        return res.json({ users: result.rows });
      }
      const normalized = query.replace(/^@+/, "").toLowerCase();
      if (normalized.length < 2 || normalized.length > 32) return res.status(400).json({ error: "username search must be 2-32 characters" });
      const result = await pool.query(`SELECT id, username, display_name, created_at FROM users WHERE username ILIKE $1 OR display_name ILIKE $2 ORDER BY CASE WHEN lower(username) = $3 THEN 0 ELSE 1 END, username LIMIT 20`, [`%${normalized}%`, `%${query.toLowerCase()}%`, normalized]);
      return res.json({ users: result.rows });
    } catch (error) { console.error("user search", error); return res.status(500).json({ error: "user search failed" }); }
  });

  app.get("/api/friends", auth, async (req, res) => {
    try {
      res.set("Cache-Control", "no-store");
      const result = await pool.query(`SELECT u.id, u.username, u.display_name, u.phone, f.created_at FROM friendships f JOIN users u ON u.id = CASE WHEN f.user_id = $1 THEN f.friend_id ELSE f.user_id END WHERE (f.user_id = $1 OR f.friend_id = $1) AND f.status = 'accepted' ORDER BY u.username`, [req.user.sub]);
      return res.json({ friends: result.rows });
    } catch (error) { console.error("friends", error); return res.status(500).json({ error: "friends lookup failed" }); }
  });

  app.get("/api/friends/requests", auth, async (req, res) => {
    try {
      const result = await pool.query(`SELECT f.id, f.status, f.created_at, u.id AS user_id, u.username, u.display_name, CASE WHEN f.friend_id = $1 THEN 'incoming' ELSE 'outgoing' END AS direction FROM friendships f JOIN users u ON u.id = CASE WHEN f.user_id = $1 THEN f.friend_id ELSE f.user_id END WHERE (f.user_id = $1 OR f.friend_id = $1) AND f.status = 'pending' ORDER BY f.created_at DESC`, [req.user.sub]);
      return res.json({ requests: result.rows.map((row) => ({ ...row, status: row.direction })) });
    } catch (error) { console.error("friend requests", error); return res.status(500).json({ error: "friend requests lookup failed" }); }
  });

  app.post("/api/friends/request", auth, async (req, res) => {
    try {
      const username = typeof req.body?.username === "string" ? req.body.username.trim().toLowerCase().replace(/^@+/, "") : "";
      const target = await findUserByUsername(username);
      if (!target) return res.status(404).json({ error: "user not found" });
      if (String(target.id) === String(req.user.sub)) return res.status(400).json({ error: "cannot add yourself" });
      const blocked = await pool.query(`SELECT 1 FROM blocks WHERE (blocker_id = $1 AND blocked_id = $2) OR (blocker_id = $2 AND blocked_id = $1) LIMIT 1`, [req.user.sub, target.id]);
      if (blocked.rowCount) return res.status(403).json({ error: "friend request unavailable" });
      const existing = await pool.query(`SELECT id, user_id, friend_id, status FROM friendships WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1) LIMIT 1`, [req.user.sub, target.id]);
      if (existing.rows[0]?.status === "accepted") return res.status(409).json({ error: "already friends" });
      if (existing.rows[0]?.status === "pending") return res.status(409).json({ error: "friend request already pending" });
      const result = await pool.query(`INSERT INTO friendships (user_id, friend_id, status) VALUES ($1, $2, 'pending') RETURNING id, user_id, friend_id, status, created_at`, [req.user.sub, target.id]);
      return res.status(201).json({ request: result.rows[0] });
    } catch (error) { console.error("friend request", error); return res.status(500).json({ error: "friend request failed" }); }
  });

  app.post("/api/friends/requests/:id/accept", auth, async (req, res) => {
    try {
      const result = await pool.query(`UPDATE friendships SET status = 'accepted' WHERE id = $1 AND friend_id = $2 AND status = 'pending' RETURNING id, user_id, friend_id, status, created_at`, [req.params.id, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: "request not found" });
      return res.json({ request: result.rows[0] });
    } catch (error) { console.error("accept friend", error); return res.status(500).json({ error: "accept failed" }); }
  });

  app.post("/api/friends/requests/:id/reject", auth, async (req, res) => {
    try {
      const result = await pool.query(`DELETE FROM friendships WHERE id = $1 AND friend_id = $2 AND status = 'pending' RETURNING id`, [req.params.id, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: "request not found" });
      return res.json({ ok: true });
    } catch (error) { console.error("reject friend", error); return res.status(500).json({ error: "reject failed" }); }
  });

  app.delete("/api/friends/requests/:id", auth, async (req, res) => {
    try {
      const result = await pool.query(`DELETE FROM friendships WHERE id = $1 AND user_id = $2 AND status = 'pending' RETURNING id`, [req.params.id, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: "outgoing request not found" });
      return res.json({ ok: true });
    } catch (error) { console.error("cancel friend request", error); return res.status(500).json({ error: "cancel request failed" }); }
  });

  app.delete("/api/friends/:username", auth, async (req, res) => {
    try {
      const target = await findUserByUsername(req.params.username.trim().toLowerCase().replace(/^@+/, ""));
      if (!target) return res.status(404).json({ error: "user not found" });
      await pool.query(`DELETE FROM friendships WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)`, [req.user.sub, target.id]);
      return res.json({ ok: true });
    } catch (error) { console.error("remove friend", error); return res.status(500).json({ error: "remove friend failed" }); }
  });

  app.get("/api/blocks", auth, async (req, res) => {
    try {
      const result = await pool.query(`SELECT u.id, u.username, u.display_name, b.created_at FROM blocks b JOIN users u ON u.id = b.blocked_id WHERE b.blocker_id = $1 ORDER BY u.username`, [req.user.sub]);
      return res.json({ blocks: result.rows });
    } catch (error) { console.error("blocks", error); return res.status(500).json({ error: "blocks lookup failed" }); }
  });

  app.post("/api/blocks/:username", auth, async (req, res) => {
    try {
      const target = await findUserByUsername(req.params.username.trim().toLowerCase().replace(/^@+/, ""));
      if (!target) return res.status(404).json({ error: "user not found" });
      if (String(target.id) === String(req.user.sub)) return res.status(400).json({ error: "cannot block yourself" });
      await pool.query("INSERT INTO blocks (blocker_id, blocked_id) VALUES ($1, $2) ON CONFLICT DO NOTHING", [req.user.sub, target.id]);
      await pool.query("DELETE FROM friendships WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)", [req.user.sub, target.id]);
      return res.status(201).json({ ok: true });
    } catch (error) { console.error("block", error); return res.status(500).json({ error: "block failed" }); }
  });

  app.delete("/api/blocks/:username", auth, async (req, res) => {
    try {
      const target = await findUserByUsername(req.params.username.trim().toLowerCase().replace(/^@+/, ""));
      if (!target) return res.status(404).json({ error: "user not found" });
      await pool.query("DELETE FROM blocks WHERE blocker_id = $1 AND blocked_id = $2", [req.user.sub, target.id]);
      return res.json({ ok: true });
    } catch (error) { console.error("unblock", error); return res.status(500).json({ error: "unblock failed" }); }
  });

  app.get("/api/marketplace/listings", auth, async (req, res) => {
    try {
      await ensureMarketplaceSchema();
      const q = typeof req.query?.q === "string" ? req.query.q.trim().slice(0, 80) : "";
      const category = typeof req.query?.category === "string" ? req.query.category.trim().slice(0, 40) : "";
      const seller = typeof req.query?.seller === "string" ? req.query.seller.trim().toLowerCase().replace(/^@+/, "") : "";
      const params = [req.user.sub];
      const where = ["l.active = TRUE", "l.quantity > 0", "l.seller_id <> $1"];
      if (q) { params.push(`%${q}%`); where.push(`(l.title ILIKE $${params.length} OR l.description ILIKE $${params.length} OR u.username ILIKE $${params.length} OR u.display_name ILIKE $${params.length})`); }
      if (category && category.toLowerCase() !== "all") { params.push(category); where.push(`l.category = $${params.length}`); }
      if (seller) { params.push(seller); where.push(`u.username = $${params.length}`); }
      const result = await pool.query(`SELECT l.id, l.seller_id, u.username AS seller_username, u.display_name AS seller_display_name, l.store_name, l.title, l.description, l.price, l.currency, l.category, l.condition, l.quantity, l.location, l.delivery_available, l.pickup_available, l.delivery_fee, l.media_ids, l.created_at FROM marketplace_listings l JOIN users u ON u.id = l.seller_id WHERE ${where.join(" AND ")} ORDER BY l.created_at DESC LIMIT 100`, params);
      return res.json({ listings: result.rows.map((row) => ({ ...row, id: String(row.id), seller_id: String(row.seller_id), price: Number(row.price), delivery_fee: row.delivery_fee == null ? null : Number(row.delivery_fee), media_ids: Array.isArray(row.media_ids) ? row.media_ids.map(String) : [] })) });
    } catch (error) { console.error("marketplace listings", error); return res.status(500).json({ error: "marketplace lookup failed" }); }
  });

  app.get("/api/marketplace/my-listings", auth, async (req, res) => {
    try {
      await ensureMarketplaceSchema();
      const result = await pool.query(`SELECT l.id, l.seller_id, u.username AS seller_username, u.display_name AS seller_display_name, l.store_name, l.title, l.description, l.price, l.currency, l.category, l.condition, l.quantity, l.location, l.delivery_available, l.pickup_available, l.delivery_fee, l.media_ids, l.active, l.created_at FROM marketplace_listings l JOIN users u ON u.id = l.seller_id WHERE l.seller_id = $1 ORDER BY l.created_at DESC`, [req.user.sub]);
      return res.json({ listings: result.rows.map((row) => ({ ...row, id: String(row.id), seller_id: String(row.seller_id), price: Number(row.price), delivery_fee: row.delivery_fee == null ? null : Number(row.delivery_fee), media_ids: Array.isArray(row.media_ids) ? row.media_ids.map(String) : [] })) });
    } catch (error) { console.error("my marketplace listings", error); return res.status(500).json({ error: "seller listings lookup failed" }); }
  });

  app.post("/api/marketplace/listings", auth, async (req, res) => {
    try {
      await ensureMarketplaceSchema();
      const title = typeof req.body?.title === "string" ? req.body.title.trim().slice(0, 120) : "";
      const description = typeof req.body?.description === "string" ? req.body.description.trim().slice(0, 4000) : "";
      const storeName = typeof req.body?.storeName === "string" ? req.body.storeName.trim().slice(0, 120) : "";
      const category = typeof req.body?.category === "string" ? req.body.category.trim().slice(0, 40) : "";
      const condition = typeof req.body?.condition === "string" ? req.body.condition.trim().toUpperCase() : "NEW";
      const currency = typeof req.body?.currency === "string" ? req.body.currency.trim().toUpperCase().slice(0, 8) : "NGN";
      const price = Number(req.body?.price);
      const quantity = Number(req.body?.quantity);
      const location = typeof req.body?.location === "string" ? req.body.location.trim().slice(0, 160) : "";
      const deliveryAvailable = Boolean(req.body?.deliveryAvailable);
      const pickupAvailable = req.body?.pickupAvailable == null ? true : Boolean(req.body.pickupAvailable);
      const deliveryFee = req.body?.deliveryFee == null || req.body.deliveryFee === "" ? null : Number(req.body.deliveryFee);
      const mediaIds = Array.isArray(req.body?.mediaIds) ? req.body.mediaIds.map(Number).filter((id) => Number.isInteger(id) && id > 0).slice(0, 12) : [];
      if (title.length < 2 || description.length < 5 || !category || !currency || !Number.isFinite(price) || price <= 0 || !Number.isInteger(quantity) || quantity < 0 || !["NEW","USED","REFURBISHED"].includes(condition) || (deliveryFee != null && (!Number.isFinite(deliveryFee) || deliveryFee < 0)) || !mediaIds.length) return res.status(400).json({ error: "complete listing details and at least one product photo or video are required" });
      const media = await pool.query("SELECT id FROM message_media WHERE id = ANY($1::bigint[]) AND owner_id = $2", [mediaIds, req.user.sub]);
      if (media.rowCount !== mediaIds.length) return res.status(403).json({ error: "one or more media files are not owned by this account" });
      const result = await pool.query(`INSERT INTO marketplace_listings (seller_id, store_name, title, description, price, currency, category, condition, quantity, location, delivery_available, pickup_available, delivery_fee, media_ids) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14::jsonb) RETURNING id, created_at`, [req.user.sub, storeName, title, description, price, currency, category, condition, quantity, location, deliveryAvailable, pickupAvailable, deliveryFee, JSON.stringify(mediaIds)]);
      return res.status(201).json({ listing: { id: String(result.rows[0].id), createdAt: result.rows[0].created_at } });
    } catch (error) { console.error("create marketplace listing", error); return res.status(500).json({ error: "listing creation failed" }); }
  });

  app.delete("/api/marketplace/listings/:id", auth, async (req, res) => {
    try {
      await ensureMarketplaceSchema();
      const id = Number(req.params.id);
      if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid listing id" });
      const result = await pool.query("UPDATE marketplace_listings SET active = FALSE, updated_at = NOW() WHERE id = $1 AND seller_id = $2 RETURNING id", [id, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: "listing not found" });
      return res.json({ ok: true });
    } catch (error) { console.error("delete marketplace listing", error); return res.status(500).json({ error: "listing removal failed" });
    }
  });

  // FYNX Social Core — real posts, likes, comments, follows and protected post media.
  let socialSchemaPromise;
  const ensureSocialSchema = async () => {
    if (!socialSchemaPromise) socialSchemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS social_posts (id BIGSERIAL PRIMARY KEY, author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, text TEXT NOT NULL DEFAULT '', visibility TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC','FRIENDS_ONLY')), media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL, media_type TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE INDEX IF NOT EXISTS social_posts_created_idx ON social_posts(created_at DESC);
      CREATE TABLE IF NOT EXISTS social_post_likes (post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(post_id,user_id));
      CREATE TABLE IF NOT EXISTS social_post_comments (id BIGSERIAL PRIMARY KEY, post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE, author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, text TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE INDEX IF NOT EXISTS social_post_comments_post_idx ON social_post_comments(post_id,created_at ASC);
      CREATE TABLE IF NOT EXISTS social_follows (follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, followed_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(follower_id,followed_id), CHECK(follower_id<>followed_id));
      CREATE INDEX IF NOT EXISTS social_follows_followed_idx ON social_follows(followed_id,created_at DESC);
    `).catch(e=>{socialSchemaPromise=undefined;throw e});
    return socialSchemaPromise;
  };
  const visibleSocialPost = async (id,userId) => { const r=await pool.query(`SELECT 1 FROM social_posts p WHERE p.id=$1 AND (p.author_id=$2 OR p.visibility='PUBLIC' OR (p.visibility='FRIENDS_ONLY' AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=p.author_id)) AND f.status='accepted'))) AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=p.author_id) OR (b.blocker_id=p.author_id AND b.blocked_id=$2))`,[id,userId]); return Boolean(r.rowCount); };

  app.get('/api/social/feed',auth,async(req,res)=>{
    try {
      await ensureSocialSchema();
      const limit = Math.min(Math.max(Number(req.query?.limit) || 20, 1), 50);
      const offset = Math.min(Math.max(Number(req.query?.offset) || 0, 0), 1000000);
      const r = await pool.query(`SELECT p.id,p.author_id,u.username author_username,u.display_name author_display_name,p.text,p.visibility,p.media_id,p.media_type,EXTRACT(EPOCH FROM p.created_at)*1000 timestamp,(SELECT COUNT(*) FROM social_post_likes l WHERE l.post_id=p.id) like_count,(SELECT COUNT(*) FROM social_post_comments c WHERE c.post_id=p.id) comment_count,EXISTS(SELECT 1 FROM social_post_likes l WHERE l.post_id=p.id AND l.user_id=$1) liked_by_current_user,EXISTS(SELECT 1 FROM social_follows f WHERE f.follower_id=$1 AND f.followed_id=p.author_id) followed_by_current_user FROM social_posts p JOIN users u ON u.id=p.author_id WHERE (p.author_id=$1 OR p.visibility='PUBLIC' OR (p.visibility='FRIENDS_ONLY' AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$1) OR (f.user_id=$1 AND f.friend_id=p.author_id)) AND f.status='accepted'))) AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=p.author_id) OR (b.blocker_id=p.author_id AND b.blocked_id=$1)) ORDER BY p.created_at DESC LIMIT $2 OFFSET $3`,[req.user.sub,limit + 1,offset]);
      const hasMore = r.rows.length > limit;
      const rows = hasMore ? r.rows.slice(0, limit) : r.rows;
      res.set('Cache-Control', 'private, max-age=15, stale-while-revalidate=30');
      res.json({ posts: rows.map(x=>({id:String(x.id),authorId:String(x.author_id),authorUsername:x.author_username,authorDisplayName:x.author_display_name,text:x.text,visibility:x.visibility,mediaId:x.media_id==null?null:String(x.media_id),mediaType:x.media_type||null,mediaUrl:x.media_id==null?null:`/api/social/media/${x.media_id}`,timestamp:Number(x.timestamp),likeCount:Number(x.like_count),commentCount:Number(x.comment_count),likedByCurrentUser:Boolean(x.liked_by_current_user),followedByCurrentUser:Boolean(x.followed_by_current_user)})), hasMore });
    } catch(e) { console.error('social feed',e); res.status(500).json({error:'social feed failed'}); }
  });
  app.post('/api/social/posts',auth,async(req,res)=>{try{await ensureSocialSchema();const text=typeof req.body?.text==='string'?req.body.text.trim().slice(0,4000):'';const visibility=req.body?.visibility==='FRIENDS_ONLY'?'FRIENDS_ONLY':'PUBLIC';const mediaId=req.body?.mediaId==null?null:Number(req.body.mediaId);const mediaType=typeof req.body?.mediaType==='string'?req.body.mediaType.trim().toLowerCase():null;if(!text&&mediaId==null)return res.status(400).json({error:'post content is required'});if(mediaId!=null){if(!Number.isInteger(mediaId)||mediaId<1||!['image','video','audio'].includes(mediaType))return res.status(400).json({error:'invalid post media'});const m=await pool.query('SELECT id FROM message_media WHERE id=$1 AND owner_id=$2',[mediaId,req.user.sub]);if(!m.rows[0])return res.status(403).json({error:'media is not owned by this account'})}const r=await pool.query('INSERT INTO social_posts(author_id,text,visibility,media_id,media_type) VALUES($1,$2,$3,$4,$5) RETURNING id',[req.user.sub,text,visibility,mediaId,mediaType]);res.status(201).json({postId:String(r.rows[0].id)})}catch(e){console.error('social create post',e);res.status(500).json({error:'post creation failed'})}});
  app.delete('/api/social/posts/:id',auth,async(req,res)=>{try{await ensureSocialSchema();const id=Number(req.params.id);const r=await pool.query('DELETE FROM social_posts WHERE id=$1 AND author_id=$2 RETURNING id',[id,req.user.sub]);if(!r.rows[0])return res.status(404).json({error:'post not found'});res.json({ok:true})}catch(e){res.status(500).json({error:'post deletion failed'})}});
  app.post('/api/social/posts/:id/like',auth,async(req,res)=>{try{await ensureSocialSchema();const id=Number(req.params.id);if(!Number.isInteger(id)||!(await visibleSocialPost(id,req.user.sub)))return res.status(404).json({error:'post not found'});const x=await pool.query('SELECT 1 FROM social_post_likes WHERE post_id=$1 AND user_id=$2',[id,req.user.sub]);if(x.rowCount)await pool.query('DELETE FROM social_post_likes WHERE post_id=$1 AND user_id=$2',[id,req.user.sub]);else await pool.query('INSERT INTO social_post_likes(post_id,user_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[id,req.user.sub]);const c=await pool.query('SELECT COUNT(*)::int count FROM social_post_likes WHERE post_id=$1',[id]);res.json({liked:!x.rowCount,likeCount:c.rows[0].count})}catch(e){res.status(500).json({error:'like failed'})}});
  app.get('/api/social/posts/:id/likes',auth,async(req,res)=>{try{await ensureSocialSchema();const id=Number(req.params.id);if(!(await visibleSocialPost(id,req.user.sub)))return res.status(404).json({error:'post not found'});const r=await pool.query('SELECT u.id,u.username,u.display_name FROM social_post_likes l JOIN users u ON u.id=l.user_id WHERE l.post_id=$1 ORDER BY l.created_at DESC LIMIT 100',[id]);res.json({users:r.rows.map(x=>({id:String(x.id),username:x.username,displayName:x.display_name}))})}catch(e){res.status(500).json({error:'likes lookup failed'})}});
  app.get('/api/social/posts/:id/comments',auth,async(req,res)=>{try{await ensureSocialSchema();const id=Number(req.params.id);if(!(await visibleSocialPost(id,req.user.sub)))return res.status(404).json({error:'post not found'});const r=await pool.query('SELECT c.id,c.text,EXTRACT(EPOCH FROM c.created_at)*1000 timestamp,u.id author_id,u.username,u.display_name FROM social_post_comments c JOIN users u ON u.id=c.author_id WHERE c.post_id=$1 ORDER BY c.created_at ASC LIMIT 200',[id]);res.json({comments:r.rows.map(x=>({id:String(x.id),text:x.text,timestamp:Number(x.timestamp),authorId:String(x.author_id),authorUsername:x.username,authorDisplayName:x.display_name}))})}catch(e){res.status(500).json({error:'comments lookup failed'})}});
  app.post('/api/social/posts/:id/comments',auth,async(req,res)=>{try{await ensureSocialSchema();const id=Number(req.params.id);const text=typeof req.body?.text==='string'?req.body.text.trim().slice(0,1000):'';if(!text)return res.status(400).json({error:'comment text is required'});if(!(await visibleSocialPost(id,req.user.sub)))return res.status(404).json({error:'post not found'});const r=await pool.query('INSERT INTO social_post_comments(post_id,author_id,text) VALUES($1,$2,$3) RETURNING id,EXTRACT(EPOCH FROM created_at)*1000 timestamp',[id,req.user.sub,text]);const u=await pool.query('SELECT username,display_name FROM users WHERE id=$1',[req.user.sub]);res.status(201).json({comment:{id:String(r.rows[0].id),text,timestamp:Number(r.rows[0].timestamp),authorId:String(req.user.sub),authorUsername:u.rows[0]?.username||'',authorDisplayName:u.rows[0]?.display_name||''}})}catch(e){res.status(500).json({error:'comment failed'})}});
  app.post('/api/social/follow/:username',auth,async(req,res)=>{try{await ensureSocialSchema();const u=await findUserByUsername(req.params.username.trim().toLowerCase().replace(/^@+/,''));if(!u)return res.status(404).json({error:'user not found'});if(String(u.id)===String(req.user.sub))return res.status(400).json({error:'cannot follow yourself'});const b=await pool.query('SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1)',[req.user.sub,u.id]);if(b.rowCount)return res.status(403).json({error:'follow unavailable'});await pool.query('INSERT INTO social_follows(follower_id,followed_id) VALUES($1,$2) ON CONFLICT DO NOTHING',[req.user.sub,u.id]);res.status(201).json({following:true})}catch(e){res.status(500).json({error:'follow failed'})}});
  app.delete('/api/social/follow/:username',auth,async(req,res)=>{try{await ensureSocialSchema();const u=await findUserByUsername(req.params.username.trim().toLowerCase().replace(/^@+/,''));if(!u)return res.status(404).json({error:'user not found'});await pool.query('DELETE FROM social_follows WHERE follower_id=$1 AND followed_id=$2',[req.user.sub,u.id]);res.json({following:false})}catch(e){res.status(500).json({error:'unfollow failed'})}});
  app.get('/api/social/media/:id',auth,async(req,res)=>{try{await ensureSocialSchema();const id=Number(req.params.id);const r=await pool.query(`SELECT mm.mime_type,mm.data FROM message_media mm JOIN social_posts p ON p.media_id=mm.id WHERE mm.id=$1 AND (p.author_id=$2 OR p.visibility='PUBLIC' OR (p.visibility='FRIENDS_ONLY' AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=p.author_id)) AND f.status='accepted'))) AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=p.author_id) OR (b.blocker_id=p.author_id AND b.blocked_id=$2)) LIMIT 1`,[id,req.user.sub]);if(!r.rows[0])return res.status(404).json({error:'media not found'});res.set('Cache-Control','private,max-age=3600');res.type(r.rows[0].mime_type);res.send(r.rows[0].data)}catch(e){res.status(500).json({error:'social media fetch failed'})}});

}
