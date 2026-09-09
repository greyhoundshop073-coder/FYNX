import crypto from "node:crypto";

export function registerDiscoveryRoutes({ app, pool, auth }) {
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_discovery_events (
        id BIGSERIAL PRIMARY KEY,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        event_type TEXT NOT NULL CHECK (event_type IN ('VIEW','LIKE','COMMENT','SHARE','SAVE','FOLLOW','PROFILE_VIEW','PRODUCT_CLICK','MESSAGE','PURCHASE','NOT_INTERESTED')),
        post_id BIGINT REFERENCES social_posts(id) ON DELETE CASCADE,
        listing_id BIGINT REFERENCES marketplace_listings(id) ON DELETE CASCADE,
        target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
        session_id TEXT,
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS fynx_discovery_events_user_idx ON fynx_discovery_events(user_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_discovery_events_post_idx ON fynx_discovery_events(post_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_discovery_events_listing_idx ON fynx_discovery_events(listing_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_discovery_events_type_idx ON fynx_discovery_events(event_type, created_at DESC);
    `).catch((error) => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };

  const cleanText = (value, max) => typeof value === "string" ? value.trim().slice(0, max) : "";
  const validPostId = (value) => Number.isInteger(Number(value)) && Number(value) > 0 ? Number(value) : null;
  const validListingId = (value) => Number.isInteger(Number(value)) && Number(value) > 0 ? Number(value) : null;

  app.post("/api/discovery/events", auth, async (req, res) => {
    try {
      await ensureSchema();
      const allowed = new Set(['VIEW','LIKE','COMMENT','SHARE','SAVE','FOLLOW','PROFILE_VIEW','PRODUCT_CLICK','MESSAGE','PURCHASE','NOT_INTERESTED']);
      const eventType = cleanText(req.body?.eventType, 32).toUpperCase();
      if (!allowed.has(eventType)) return res.status(400).json({ error: "invalid discovery event" });
      const postId = req.body?.postId == null ? null : validPostId(req.body.postId);
      const listingId = req.body?.listingId == null ? null : validListingId(req.body.listingId);
      const targetUserId = req.body?.targetUserId == null ? null : validPostId(req.body.targetUserId);
      if (req.body?.postId != null && !postId) return res.status(400).json({ error: "invalid post id" });
      if (req.body?.listingId != null && !listingId) return res.status(400).json({ error: "invalid listing id" });
      if (req.body?.targetUserId != null && !targetUserId) return res.status(400).json({ error: "invalid target user id" });
      const sessionId = cleanText(req.body?.sessionId, 120) || crypto.randomUUID();
      const metadata = req.body?.metadata && typeof req.body.metadata === "object" ? req.body.metadata : {};
      await pool.query(
        `INSERT INTO fynx_discovery_events(user_id,event_type,post_id,listing_id,target_user_id,session_id,metadata) VALUES($1,$2,$3,$4,$5,$6,$7::jsonb)`,
        [req.user.sub, eventType, postId, listingId, targetUserId, sessionId, JSON.stringify(metadata)]
      );
      return res.status(201).json({ ok: true, sessionId });
    } catch (error) {
      console.error("discovery event", error);
      return res.status(500).json({ error: "discovery event failed" });
    }
  });

  app.get("/api/discovery/trending", auth, async (req, res) => {
    try {
      await ensureSchema();
      const limit = Math.min(Math.max(Number(req.query?.limit) || 20, 1), 50);
      const result = await pool.query(`
        SELECT p.id,
               p.author_id,
               u.username AS author_username,
               u.display_name AS author_display_name,
               p.text,
               p.visibility,
               p.media_id,
               p.media_type,
               EXTRACT(EPOCH FROM p.created_at) * 1000 AS timestamp,
               COALESCE(l.likes,0) AS like_count,
               COALESCE(c.comments,0) AS comment_count,
               COALESCE(e.shares,0) AS share_count,
               COALESCE(e.saves,0) AS save_count,
               (COALESCE(l.likes,0) * 3 + COALESCE(c.comments,0) * 5 + COALESCE(e.shares,0) * 7 + COALESCE(e.saves,0) * 6)
                 * EXP(-GREATEST(EXTRACT(EPOCH FROM (NOW()-p.created_at))/3600.0,0)/48.0) AS discovery_score
        FROM social_posts p
        JOIN users u ON u.id=p.author_id
        LEFT JOIN (SELECT post_id,COUNT(*) likes FROM social_post_likes GROUP BY post_id) l ON l.post_id=p.id
        LEFT JOIN (SELECT post_id,COUNT(*) comments FROM social_post_comments GROUP BY post_id) c ON c.post_id=p.id
        LEFT JOIN (
          SELECT post_id,
                 COUNT(*) FILTER (WHERE event_type='SHARE') shares,
                 COUNT(*) FILTER (WHERE event_type='SAVE') saves
          FROM fynx_discovery_events GROUP BY post_id
        ) e ON e.post_id=p.id
        WHERE p.visibility='PUBLIC'
          AND NOT EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=p.author_id) OR (b.blocker_id=p.author_id AND b.blocked_id=$1))
          AND NOT EXISTS (SELECT 1 FROM fynx_discovery_events n WHERE n.user_id=$1 AND n.post_id=p.id AND n.event_type='NOT_INTERESTED' AND n.created_at > NOW()-INTERVAL '30 days')
        ORDER BY discovery_score DESC, p.created_at DESC
        LIMIT $2
      `, [req.user.sub, limit]);
      return res.json({
        posts: result.rows.map((row) => ({
          id: String(row.id), authorId: String(row.author_id), authorUsername: row.author_username,
          authorDisplayName: row.author_display_name, text: row.text, visibility: row.visibility,
          mediaId: row.media_id == null ? null : String(row.media_id), mediaType: row.media_type || null,
          mediaUrl: null, timestamp: Number(row.timestamp), likeCount: Number(row.like_count),
          commentCount: Number(row.comment_count), shareCount: Number(row.share_count), saveCount: Number(row.save_count),
          discoveryScore: Number(row.discovery_score || 0)
        })),
        hasMore: result.rows.length >= limit
      });
    } catch (error) {
      console.error("trending feed", error);
      return res.status(500).json({ error: "trending feed unavailable" });
    }
  });

  app.get("/api/marketplace/discovery", auth, async (req, res) => {
    try {
      await ensureSchema();
      const limit = Math.min(Math.max(Number(req.query?.limit) || 30, 1), 60);
      const q = cleanText(req.query?.q, 80);
      const category = cleanText(req.query?.category, 40);
      const params = [req.user.sub];
      const where = ["l.active=TRUE", "l.quantity>0", "l.seller_id<>$1"];
      if (q) { params.push(`%${q}%`); const n=params.length; where.push(`(l.title ILIKE $${n} OR l.description ILIKE $${n} OR u.username ILIKE $${n} OR u.display_name ILIKE $${n})`); }
      if (category && category.toLowerCase() !== "all") { params.push(category); where.push(`l.category=$${params.length}`); }
      params.push(limit);
      const result = await pool.query(`
        SELECT l.id,l.seller_id,u.username seller_username,u.display_name seller_display_name,l.store_name,l.title,l.description,l.price,l.currency,l.category,l.condition,l.quantity,l.location,l.delivery_available,l.pickup_available,l.delivery_fee,l.media_ids,l.created_at,
          (COALESCE((SELECT COUNT(*) FROM marketplace_orders o WHERE o.listing_id=l.id AND o.status IN ('PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED')),0)*8
           + COALESCE((SELECT COUNT(*) FROM fynx_discovery_events e WHERE e.listing_id=l.id AND e.event_type='PRODUCT_CLICK' AND e.created_at>NOW()-INTERVAL '14 days'),0)*2
           + GREATEST(0, 72-EXTRACT(EPOCH FROM (NOW()-l.created_at))/3600.0)) AS discovery_score
        FROM marketplace_listings l JOIN users u ON u.id=l.seller_id
        WHERE ${where.join(" AND ")}
        ORDER BY discovery_score DESC,l.created_at DESC LIMIT $${params.length}
      `, params);
      return res.json({ listings: result.rows.map((row) => ({ ...row, id:String(row.id), seller_id:String(row.seller_id), price:Number(row.price), delivery_fee:row.delivery_fee==null?null:Number(row.delivery_fee), media_ids:Array.isArray(row.media_ids)?row.media_ids.map(String):[], discovery_score:Number(row.discovery_score||0) })) });
    } catch (error) {
      console.error("marketplace discovery", error);
      return res.status(500).json({ error: "marketplace discovery unavailable" });
    }
  });

  app.get("/api/marketplace/listing/:id", auth, async (req, res) => {
    try {
      const listingId = validListingId(req.params?.id);
      if (!listingId) return res.status(400).json({ error: "invalid listing id" });
      const result = await pool.query(`
        SELECT l.id,l.seller_id,u.username seller_username,u.display_name seller_display_name,l.store_name,l.title,l.description,l.price,l.currency,l.category,l.condition,l.quantity,l.location,l.delivery_available,l.pickup_available,l.delivery_fee,l.media_ids,l.active,l.created_at
        FROM marketplace_listings l
        JOIN users u ON u.id=l.seller_id
        WHERE l.id=$1
        LIMIT 1
      `, [listingId]);
      if (!result.rows[0]) return res.status(404).json({ error: "listing not found" });
      const row = result.rows[0];
      if (!row.active || Number(row.quantity) <= 0) return res.status(404).json({ error: "listing unavailable" });
      return res.json({ listing: {
        id:String(row.id), seller_id:String(row.seller_id), seller_username:row.seller_username,
        seller_display_name:row.seller_display_name, store_name:row.store_name, title:row.title,
        description:row.description, price:Number(row.price), currency:row.currency, category:row.category,
        condition:row.condition, quantity:Number(row.quantity), location:row.location,
        delivery_available:Boolean(row.delivery_available), pickup_available:Boolean(row.pickup_available),
        delivery_fee:row.delivery_fee == null ? null : Number(row.delivery_fee),
        media_ids:Array.isArray(row.media_ids) ? row.media_ids.map(String) : [], active:Boolean(row.active)
      }});
    } catch (error) {
      console.error("marketplace exact listing", error);
      return res.status(500).json({ error: "marketplace listing unavailable" });
    }
  });
}
