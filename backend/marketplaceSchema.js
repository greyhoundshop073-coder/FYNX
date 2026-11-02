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
      "CREATE INDEX IF NOT EXISTS marketplace_price_watches_listing_idx ON marketplace_price_watches (listing_id, created_at DESC);",
      "CREATE TABLE IF NOT EXISTS marketplace_weekly_seller_awards (",
      "  week_start TIMESTAMPTZ NOT NULL,",
      "  week_end TIMESTAMPTZ NOT NULL,",
      "  weekly_rank INTEGER NOT NULL CHECK (weekly_rank BETWEEN 1 AND 5),",
      "  seller_id BIGINT REFERENCES users(id) ON DELETE SET NULL,",
      "  username TEXT NOT NULL,",
      "  display_name TEXT NOT NULL DEFAULT '',",
      "  completed_orders INTEGER NOT NULL CHECK (completed_orders > 0),",
      "  unique_buyers INTEGER NOT NULL CHECK (unique_buyers > 0),",
      "  featured_listing_id BIGINT,",
      "  featured_product_title TEXT NOT NULL DEFAULT '',",
      "  featured_product_price NUMERIC(14,2),",
      "  featured_product_currency TEXT,",
      "  archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),",
      "  PRIMARY KEY (week_start, weekly_rank),",
      "  UNIQUE (week_start, seller_id)",
      ");",
      "CREATE INDEX IF NOT EXISTS marketplace_weekly_seller_awards_username_idx ON marketplace_weekly_seller_awards (lower(username), week_start DESC);"
    ].join("\n");
    pending = pool.query(sql).catch((error) => {
      schemaPromises.delete(pool);
      throw error;
    });
    schemaPromises.set(pool, pending);
  }
  return pending;
}