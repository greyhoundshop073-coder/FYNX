const schemaPromises = new WeakMap();

/** Shared, idempotent Marketplace schema for discovery and seller listing routes. */
export function ensureMarketplaceSchemaForPool(pool) {
  if (!pool || (typeof pool !== "object" && typeof pool !== "function")) {
    return Promise.reject(new Error("Marketplace database pool is unavailable"));
  }
  let pending = schemaPromises.get(pool);
  if (!pending) {
    const sql = [
      "CREATE TABLE IF NOT EXISTS marketplace_listings (",
      "  id BIGSERIAL PRIMARY KEY,",
      "  seller_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,",
      "  store_name TEXT NOT NULL DEFAULT '',",
      "  title TEXT NOT NULL,",
      "  description TEXT NOT NULL DEFAULT '',",
      "  price NUMERIC(14,2) NOT NULL CHECK (price > 0),",
      "  currency TEXT NOT NULL DEFAULT 'NGN',",
      "  category TEXT NOT NULL,",
      "  condition TEXT NOT NULL DEFAULT 'NEW' CHECK (condition IN ('NEW','USED','REFURBISHED')),",
      "  quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0),",
      "  location TEXT NOT NULL DEFAULT '',",
      "  delivery_available BOOLEAN NOT NULL DEFAULT FALSE,",
      "  pickup_available BOOLEAN NOT NULL DEFAULT TRUE,",
      "  delivery_fee NUMERIC(14,2),",
      "  media_ids JSONB NOT NULL DEFAULT '[]'::jsonb,",
      "  active BOOLEAN NOT NULL DEFAULT TRUE,",
      "  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),",
      "  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()",
      ");",
      "ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0);",
      "CREATE INDEX IF NOT EXISTS marketplace_listings_active_idx ON marketplace_listings (active, created_at DESC);",
      "CREATE INDEX IF NOT EXISTS marketplace_listings_seller_idx ON marketplace_listings (seller_id, active, created_at DESC);",
      "CREATE INDEX IF NOT EXISTS marketplace_listings_category_idx ON marketplace_listings (category, active, created_at DESC);",
      "CREATE TABLE IF NOT EXISTS marketplace_price_watches (",
      "  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,",
      "  listing_id BIGINT NOT NULL REFERENCES marketplace_listings(id) ON DELETE CASCADE,",
      "  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),",
      "  PRIMARY KEY (user_id, listing_id)",
      ");",
      "CREATE INDEX IF NOT EXISTS marketplace_price_watches_listing_idx ON marketplace_price_watches (listing_id, created_at DESC);"
    ].join("\n");
    pending = pool.query(sql).catch((error) => {
      schemaPromises.delete(pool);
      throw error;
    });
    schemaPromises.set(pool, pending);
  }
  return pending;
}