import pg from "pg";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: 8,
  keepAlive: true,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000
}) : null;

function clean(value, max) {
  return typeof value === "string" ? value.trim().slice(0, max) : "";
}

export function registerBusinessRoutes({ app, auth }) {
  if (!app || !auth || !pool) return;
  let schemaReady = null;
  const ready = () => {
    if (!schemaReady) schemaReady = pool.query(`
      CREATE TABLE IF NOT EXISTS business_profiles (
        id BIGSERIAL PRIMARY KEY,
        owner_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
        business_name TEXT NOT NULL,
        business_username TEXT NOT NULL UNIQUE,
        category TEXT NOT NULL,
        description TEXT NOT NULL DEFAULT '',
        location TEXT NOT NULL DEFAULT '',
        phone TEXT NOT NULL DEFAULT '',
        website TEXT NOT NULL DEFAULT '',
        verified BOOLEAN NOT NULL DEFAULT FALSE,
        active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS business_profiles_category_idx ON business_profiles(category);
      CREATE INDEX IF NOT EXISTS business_profiles_owner_idx ON business_profiles(owner_id);
    `);
    return schemaReady;
  };

  app.use("/api/business", async (_req, _res, next) => {
    try { await ready(); next(); } catch (error) { console.error("[fynx-business] schema unavailable", error); next(new Error("business service unavailable")); }
  });

  app.get("/api/business/profile", auth, async (req, res) => {
    try {
      const result = await pool.query(`SELECT id,business_name,business_username,category,description,location,phone,website,verified,active,created_at,updated_at FROM business_profiles WHERE owner_id=$1`, [req.user.sub]);
      return res.json({ profile: result.rows[0] || null });
    } catch (error) {
      console.error("[fynx-business] profile load failed", error);
      return res.status(500).json({ error: "unable to load business profile" });
    }
  });

  const saveProfile = async (req, res) => {
    const businessName = clean(req.body?.businessName, 120);
    const businessUsername = clean(req.body?.businessUsername, 32).replace(/^@+/, "").toLowerCase();
    const category = clean(req.body?.category, 60);
    const description = clean(req.body?.description, 1000);
    const location = clean(req.body?.location, 160);
    const phone = clean(req.body?.phone, 30);
    const website = clean(req.body?.website, 200);
    if (businessName.length < 2) return res.status(400).json({ error: "business name is required" });
    if (!/^[a-z0-9_]{3,32}$/.test(businessUsername)) return res.status(400).json({ error: "business username must be 3-32 characters using letters, numbers, or underscore" });
    if (category.length < 2) return res.status(400).json({ error: "business category is required" });
    if (website && !/^https:\/\//i.test(website)) return res.status(400).json({ error: "website must use HTTPS" });
    try {
      const result = await pool.query(`
        INSERT INTO business_profiles (owner_id,business_name,business_username,category,description,location,phone,website)
        VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
        ON CONFLICT (owner_id) DO UPDATE SET
          business_name=EXCLUDED.business_name,
          business_username=EXCLUDED.business_username,
          category=EXCLUDED.category,
          description=EXCLUDED.description,
          location=EXCLUDED.location,
          phone=EXCLUDED.phone,
          website=EXCLUDED.website,
          updated_at=NOW()
        RETURNING id,business_name,business_username,category,description,location,phone,website,verified,active,created_at,updated_at`,
        [req.user.sub,businessName,businessUsername,category,description,location,phone,website]);
      return res.json({ profile: result.rows[0] });
    } catch (error) {
      if (error?.code === "23505") return res.status(409).json({ error: "business username is already in use" });
      console.error("[fynx-business] profile save failed", error);
      return res.status(500).json({ error: "unable to save business profile" });
    }
  };
  app.post("/api/business/profile", auth, saveProfile);
  app.put("/api/business/profile", auth, saveProfile);

  app.get("/api/business/overview", auth, async (req, res) => {
    try {
      const profile = (await pool.query("SELECT id,business_name,business_username,category,description,location,phone,website,verified,active FROM business_profiles WHERE owner_id=$1", [req.user.sub])).rows[0] || null;
      const listings = (await pool.query("SELECT COUNT(*)::int AS count FROM marketplace_listings WHERE seller_id=$1 AND COALESCE(active,TRUE)=TRUE", [req.user.sub]).catch(() => ({ rows: [{ count: 0 }] }))).rows[0]?.count || 0;
      const campaigns = (await pool.query("SELECT COUNT(*)::int AS count, COALESCE(SUM(total_budget_kobo),0)::bigint AS budget_kobo, COALESCE(SUM(spent_kobo),0)::bigint AS spent_kobo FROM marketplace_ad_campaigns WHERE owner_id=$1", [req.user.sub]).catch(() => ({ rows: [{ count: 0, budget_kobo: 0, spent_kobo: 0 }] }))).rows[0] || { count: 0, budget_kobo: 0, spent_kobo: 0 };
      return res.json({ overview: { profile, activeListings: Number(listings), campaigns: Number(campaigns.count || 0), budgetKobo: Number(campaigns.budget_kobo || 0), spentKobo: Number(campaigns.spent_kobo || 0) } });
    } catch (error) {
      console.error("[fynx-business] overview failed", error);
      return res.status(500).json({ error: "unable to load business overview" });
    }
  });
}
