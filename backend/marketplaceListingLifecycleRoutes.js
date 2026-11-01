import { ensureMarketplaceSchemaForPool } from "./marketplaceSchema.js";
import { ensureMarketplaceShopProfileSchema } from "./marketplaceShopProfiles.js";

const schemaPromises = new WeakMap();

export function ensureMarketplaceListingLifecycleSchema(pool) {
  if (!pool || (typeof pool !== "object" && typeof pool !== "function")) {
    return Promise.reject(new Error("Marketplace database pool is unavailable"));
  }
  let pending = schemaPromises.get(pool);
  if (!pending) {
    pending = (async () => {
      await ensureMarketplaceSchemaForPool(pool);
      await ensureMarketplaceShopProfileSchema(pool);
      await pool.query(`
        ALTER TABLE marketplace_listings
          ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0);
        CREATE TABLE IF NOT EXISTS marketplace_saved_products (
          user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
          listing_id BIGINT NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (user_id, listing_id)
        );
        CREATE INDEX IF NOT EXISTS marketplace_saved_products_listing_idx
          ON marketplace_saved_products (listing_id, created_at DESC);
      `);
    })().catch((error) => {
      schemaPromises.delete(pool);
      throw error;
    });
    schemaPromises.set(pool, pending);
  }
  return pending;
}

const parseListingId = (value) => {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
};

const marketplaceMediaTypesProjection = `ARRAY(
  SELECT COALESCE(mm.mime_type, '')
  FROM jsonb_array_elements_text(
    CASE WHEN jsonb_typeof(l.media_ids) = 'array' THEN l.media_ids ELSE '[]'::jsonb END
  ) WITH ORDINALITY AS listing_media(media_id, ord)
  LEFT JOIN message_media mm
    ON mm.id = CASE WHEN listing_media.media_id ~ '^[0-9]{1,18}$' THEN listing_media.media_id::bigint ELSE NULL END
  ORDER BY listing_media.ord
) AS media_types`;

export function registerMarketplaceListingLifecycleRoutes({ app, pool, auth }) {
  const ensureSchema = () => ensureMarketplaceListingLifecycleSchema(pool);

  app.get("/api/marketplace/saved-products", auth, async (req, res) => {
    try {
      await ensureSchema();
      const result = await pool.query(`
        SELECT l.id, l.seller_id, u.username AS seller_username, u.display_name AS seller_display_name,
          COALESCE(NULLIF(shop.shop_name, ''), NULLIF(l.store_name, ''), '') AS store_name,
          l.title, l.description, l.price, l.currency, l.category, l.condition, l.quantity,
          GREATEST(0, l.quantity - COALESCE(l.reserved_quantity, 0)) AS available_quantity,
          l.location, l.delivery_available, l.pickup_available, l.delivery_fee, l.media_ids,
          ${marketplaceMediaTypesProjection}, l.active, l.created_at, sp.created_at AS saved_at
        FROM marketplace_saved_products sp
        JOIN marketplace_listings l ON l.id = sp.listing_id
        JOIN users u ON u.id = l.seller_id
        LEFT JOIN marketplace_shop_profiles shop ON shop.seller_id = l.seller_id
        WHERE sp.user_id = $1
          AND NOT EXISTS (
            SELECT 1 FROM blocks b
            WHERE (b.blocker_id = $1 AND b.blocked_id = l.seller_id)
               OR (b.blocked_id = $1 AND b.blocker_id = l.seller_id)
          )
        ORDER BY sp.created_at DESC
        LIMIT 200
      `, [req.user.sub]);
      return res.json({
        products: result.rows.map((row) => ({
          ...row,
          id: String(row.id),
          seller_id: String(row.seller_id),
          price: Number(row.price),
          delivery_fee: row.delivery_fee == null ? null : Number(row.delivery_fee),
          media_ids: Array.isArray(row.media_ids) ? row.media_ids.map(String) : [],
          media_types: Array.isArray(row.media_types) ? row.media_types.map(String) : []
        }))
      });
    } catch (error) {
      console.error("marketplace saved products", error);
      return res.status(500).json({ error: "saved products lookup failed" });
    }
  });

  app.get("/api/marketplace/listings/:id/saved", auth, async (req, res) => {
    const id = parseListingId(req.params.id);
    if (!id) return res.status(400).json({ error: "invalid listing id" });
    try {
      await ensureSchema();
      const result = await pool.query(`
        SELECT EXISTS (
          SELECT 1
          FROM marketplace_saved_products sp
          JOIN marketplace_listings l ON l.id = sp.listing_id
          WHERE sp.user_id = $1 AND sp.listing_id = $2
            AND NOT EXISTS (
              SELECT 1 FROM blocks b
              WHERE (b.blocker_id = $1 AND b.blocked_id = l.seller_id)
                 OR (b.blocked_id = $1 AND b.blocker_id = l.seller_id)
            )
        ) AS saved
      `, [req.user.sub, id]);
      return res.json({ saved: Boolean(result.rows[0]?.saved) });
    } catch (error) {
      console.error("marketplace saved product state", error);
      return res.status(500).json({ error: "saved product state unavailable" });
    }
  });

  app.post("/api/marketplace/listings/:id/saved", auth, async (req, res) => {
    const id = parseListingId(req.params.id);
    if (!id) return res.status(400).json({ error: "invalid listing id" });
    try {
      await ensureSchema();
      const listing = (await pool.query(`
        SELECT l.id
        FROM marketplace_listings l
        WHERE l.id = $1 AND l.active = TRUE
          AND NOT EXISTS (
            SELECT 1 FROM blocks b
            WHERE (b.blocker_id = $2 AND b.blocked_id = l.seller_id)
               OR (b.blocked_id = $2 AND b.blocker_id = l.seller_id)
          )
      `, [id, req.user.sub])).rows[0];
      if (!listing) return res.status(404).json({ error: "listing not found" });
      await pool.query(`
        INSERT INTO marketplace_saved_products (user_id, listing_id)
        VALUES ($1, $2)
        ON CONFLICT (user_id, listing_id) DO NOTHING
      `, [req.user.sub, id]);
      return res.status(201).json({ saved: true, listingId: String(id) });
    } catch (error) {
      console.error("marketplace save product", error);
      return res.status(500).json({ error: "product could not be saved" });
    }
  });

  app.delete("/api/marketplace/listings/:id/saved", auth, async (req, res) => {
    const id = parseListingId(req.params.id);
    if (!id) return res.status(400).json({ error: "invalid listing id" });
    try {
      await ensureSchema();
      await pool.query(
        "DELETE FROM marketplace_saved_products WHERE user_id = $1 AND listing_id = $2",
        [req.user.sub, id]
      );
      return res.json({ saved: false, listingId: String(id) });
    } catch (error) {
      console.error("marketplace unsave product", error);
      return res.status(500).json({ error: "product could not be removed from Saved Products" });
    }
  });

  app.post("/api/marketplace/listings/:id/reactivate", auth, async (req, res) => {
    const id = parseListingId(req.params.id);
    if (!id) return res.status(400).json({ error: "invalid listing id" });
    try {
      await ensureSchema();
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const existing = (await client.query(
          "SELECT id, seller_id, quantity, reserved_quantity FROM marketplace_listings WHERE id = $1 AND seller_id = $2 FOR UPDATE",
          [id, req.user.sub]
        )).rows[0];
        if (!existing) {
          await client.query("ROLLBACK");
          return res.status(404).json({ error: "listing not found" });
        }
        const quantity = Number(existing.quantity);
        const reservedQuantity = Math.max(0, Number(existing.reserved_quantity) || 0);
        const availableQuantity = quantity - reservedQuantity;
        if (!Number.isInteger(quantity) || quantity < 0 || !Number.isFinite(availableQuantity) || availableQuantity < 1) {
          await client.query("ROLLBACK");
          return res.status(409).json({
            error: "add at least one unreserved unit before republishing this listing",
            availableQuantity: Math.max(0, Number.isFinite(availableQuantity) ? availableQuantity : 0)
          });
        }
        const updated = (await client.query(
          "UPDATE marketplace_listings SET active = TRUE, updated_at = NOW() WHERE id = $1 AND seller_id = $2 RETURNING id, active",
          [id, req.user.sub]
        )).rows[0];
        await client.query("COMMIT");
        return res.json({ ok: true, listingId: String(updated.id), active: Boolean(updated.active), availableQuantity });
      } catch (error) {
        await client.query("ROLLBACK").catch(() => {});
        throw error;
      } finally {
        client.release();
      }
    } catch (error) {
      console.error("reactivate marketplace listing", error);
      return res.status(500).json({ error: "listing could not be republished" });
    }
  });
}
