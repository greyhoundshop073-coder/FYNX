import { getFynxDatabasePool } from "./databasePool.js";
import pg from "pg";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const pool = getFynxDatabasePool();

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
      ALTER TABLE business_profiles ADD COLUMN IF NOT EXISTS opening_hours JSONB NOT NULL DEFAULT '{}'::jsonb;
      ALTER TABLE business_profiles ADD COLUMN IF NOT EXISTS service_areas JSONB NOT NULL DEFAULT '[]'::jsonb;
      ALTER TABLE business_profiles ADD COLUMN IF NOT EXISTS services JSONB NOT NULL DEFAULT '[]'::jsonb;
      ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS business_id BIGINT REFERENCES business_profiles(id) ON DELETE SET NULL;
      CREATE INDEX IF NOT EXISTS marketplace_listings_business_idx ON marketplace_listings(business_id,active,created_at DESC);
    `);
    return schemaReady;
  };

  app.use("/api/business", async (_req, _res, next) => {
    try { await ready(); next(); } catch (error) { console.error("[fynx-business] schema unavailable", error); next(new Error("business service unavailable")); }
  });

  // Public, read-only business discovery. Only fields intentionally entered for a
  // business profile are returned; internal owner IDs and account metadata stay private.
  app.get("/api/business/directory", async (req, res) => {
    const query = clean(req.query?.query, 80);
    const category = clean(req.query?.category, 60);
    const location = clean(req.query?.location, 120);
    const requestedLimit = Number(req.query?.limit);
    const requestedOffset = Number(req.query?.offset);
    const limit = Number.isInteger(requestedLimit) ? Math.min(Math.max(requestedLimit, 1), 50) : 30;
    const offset = Number.isInteger(requestedOffset) ? Math.min(Math.max(requestedOffset, 0), 100000) : 0;
    const where = ["active = TRUE"];
    const params = [];
    if (query) {
      params.push(`%${query}%`);
      const index = params.length;
      where.push(`(business_name ILIKE $${index} OR business_username ILIKE $${index} OR category ILIKE $${index} OR description ILIKE $${index})`);
    }
    if (category) {
      params.push(`%${category}%`);
      where.push(`category ILIKE $${params.length}`);
    }
    if (location) {
      params.push(`%${location}%`);
      where.push(`location ILIKE $${params.length}`);
    }
    params.push(limit + 1);
    const limitIndex = params.length;
    params.push(offset);
    const offsetIndex = params.length;
    try {
      const result = await pool.query(
        `SELECT id::text AS id,business_name,business_username,category,description,location,phone,website,verified,
                opening_hours,service_areas,services
           FROM business_profiles
          WHERE ${where.join(" AND ")}
          ORDER BY verified DESC,updated_at DESC,id DESC
          LIMIT $${limitIndex} OFFSET $${offsetIndex}`,
        params
      );
      const hasMore = result.rows.length > limit;
      const businesses = result.rows.slice(0, limit);
      return res.json({ businesses, hasMore, limit, offset });
    } catch (error) {
      console.error("[fynx-business] directory search failed", error);
      return res.status(500).json({ error: "business directory is temporarily unavailable" });
    }
  });

  // Public business profile and its active, in-stock Marketplace products.
  // Never return owner IDs, private account data, or unlinked seller listings.
  app.get("/api/business/public/:username", async (req, res) => {
    const username = clean(req.params?.username, 32).replace(/^@+/, "").toLowerCase();
    if (!/^[a-z0-9_]{3,32}$/.test(username)) return res.status(400).json({ error: "invalid business username" });
    try {
      const profileResult = await pool.query(
        `SELECT id::text AS id,business_name,business_username,category,description,location,phone,website,verified,
                opening_hours,service_areas,services
           FROM business_profiles WHERE business_username=$1 AND active=TRUE LIMIT 1`,
        [username]
      );
      const profile = profileResult.rows[0];
      if (!profile) return res.status(404).json({ error: "business profile not found" });
      const productResult = await pool.query(
        `SELECT id::text AS id,title,description,price,currency,category,condition,
                GREATEST(0, quantity - COALESCE(reserved_quantity,0))::int AS available_quantity,
                location,media_ids
           FROM marketplace_listings
          WHERE business_id=$1 AND COALESCE(active,TRUE)=TRUE AND quantity>0
          ORDER BY created_at DESC,id DESC LIMIT 20`,
        [profile.id]
      );
      return res.json({
        profile,
        products: productResult.rows.map(row => ({
          ...row,
          price: Number(row.price),
          available_quantity: Number(row.available_quantity),
          media_ids: Array.isArray(row.media_ids) ? row.media_ids.map(String) : []
        }))
      });
    } catch (error) {
      console.error("[fynx-business] public profile load failed", error);
      return res.status(500).json({ error: "business profile is temporarily unavailable" });
    }
  });

  app.get("/api/business/profile", auth, async (req, res) => {
    try {
      const result = await pool.query(`SELECT id,business_name,business_username,category,description,location,phone,website,opening_hours,service_areas,services,verified,active,created_at,updated_at FROM business_profiles WHERE owner_id=$1`, [req.user.sub]);
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
    const openingHours = {};
    if (req.body?.openingHours && typeof req.body.openingHours === "object" && !Array.isArray(req.body.openingHours)) {
      for (const [day, hours] of Object.entries(req.body.openingHours).slice(0, 7)) {
        const key = clean(day, 20);
        const value = clean(hours, 60);
        if (key && value) openingHours[key] = value;
      }
    }
    const serviceAreas = Array.isArray(req.body?.serviceAreas) ? req.body.serviceAreas.map(value => clean(value, 80)).filter(Boolean).slice(0, 20) : [];
    const services = Array.isArray(req.body?.services) ? req.body.services.slice(0, 30).map(item => ({
      name: clean(item?.name, 100),
      description: clean(item?.description, 240),
      price: clean(String(item?.price ?? ""), 32),
      currency: ["NGN","USD"].includes(String(item?.currency || "").toUpperCase()) ? String(item.currency).toUpperCase() : "NGN"
    })).filter(item => item.name) : [];
    if (businessName.length < 2) return res.status(400).json({ error: "business name is required" });
    if (!/^[a-z0-9_]{3,32}$/.test(businessUsername)) return res.status(400).json({ error: "business username must be 3-32 characters using letters, numbers, or underscore" });
    if (category.length < 2) return res.status(400).json({ error: "business category is required" });
    if (website && !/^https:\/\//i.test(website)) return res.status(400).json({ error: "website must use HTTPS" });
    try {
      const result = await pool.query(`
        INSERT INTO business_profiles (owner_id,business_name,business_username,category,description,location,phone,website,opening_hours,service_areas,services)
        VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb,$10::jsonb,$11::jsonb)
        ON CONFLICT (owner_id) DO UPDATE SET
          business_name=EXCLUDED.business_name,
          business_username=EXCLUDED.business_username,
          category=EXCLUDED.category,
          description=EXCLUDED.description,
          location=EXCLUDED.location,
          phone=EXCLUDED.phone,
          website=EXCLUDED.website,
          opening_hours=EXCLUDED.opening_hours,
          service_areas=EXCLUDED.service_areas,
          services=EXCLUDED.services,
          updated_at=NOW()
        RETURNING id,business_name,business_username,category,description,location,phone,website,opening_hours,service_areas,services,verified,active,created_at,updated_at`,
        [req.user.sub,businessName,businessUsername,category,description,location,phone,website,JSON.stringify(openingHours),JSON.stringify(serviceAreas),JSON.stringify(services)]);
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
      const profile = (await pool.query("SELECT id,business_name,business_username,category,description,location,phone,website,opening_hours,service_areas,services,verified,active FROM business_profiles WHERE owner_id=$1", [req.user.sub])).rows[0] || null;
      const listings = (await pool.query("SELECT COUNT(*)::int AS count FROM marketplace_listings WHERE seller_id=$1 AND COALESCE(active,TRUE)=TRUE", [req.user.sub]).catch(() => ({ rows: [{ count: 0 }] }))).rows[0]?.count || 0;
      const campaigns = (await pool.query("SELECT COUNT(*)::int AS count, COALESCE(SUM(total_budget_kobo),0)::bigint AS budget_kobo, COALESCE(SUM(spent_kobo),0)::bigint AS spent_kobo FROM marketplace_ad_campaigns WHERE owner_id=$1", [req.user.sub]).catch(() => ({ rows: [{ count: 0, budget_kobo: 0, spent_kobo: 0 }] }))).rows[0] || { count: 0, budget_kobo: 0, spent_kobo: 0 };
      return res.json({ overview: { profile, activeListings: Number(listings), campaigns: Number(campaigns.count || 0), budgetKobo: Number(campaigns.budget_kobo || 0), spentKobo: Number(campaigns.spent_kobo || 0) } });
    } catch (error) {
      console.error("[fynx-business] overview failed", error);
      return res.status(500).json({ error: "unable to load business overview" });
    }
  });
}
