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
    } catch (error) { console.error("delete marketplace listing", error); return res.status(500).json({ error: "listing removal failed" }); }
  });
}
