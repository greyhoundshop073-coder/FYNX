import pg from "pg";
import jwt from "jsonwebtoken";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const pool = DATABASE_URL
  ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false, max: 8 })
  : null;

const CAMPAIGN_STATUSES = new Set(["draft", "pending_review", "active", "paused", "completed", "rejected"]);
const TARGETING_KEYS = new Set(["locations", "ageMin", "ageMax", "interests"]);
const CREATIVE_TYPES = new Set(["post", "product", "business"]);

async function ensureAdvertisingSchema() {
  if (!pool) return;
  await pool.query(`
    CREATE TABLE IF NOT EXISTS marketplace_ad_campaigns (
      id BIGSERIAL PRIMARY KEY,
      owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      name TEXT NOT NULL,
      creative_type TEXT NOT NULL CHECK (creative_type IN ('post', 'product', 'business')),
      creative_ref TEXT,
      headline TEXT NOT NULL DEFAULT '',
      body TEXT NOT NULL DEFAULT '',
      destination_url TEXT,
      targeting JSONB NOT NULL DEFAULT '{}'::jsonb,
      daily_budget_kobo BIGINT NOT NULL CHECK (daily_budget_kobo >= 0),
      total_budget_kobo BIGINT NOT NULL CHECK (total_budget_kobo > 0),
      spent_kobo BIGINT NOT NULL DEFAULT 0 CHECK (spent_kobo >= 0),
      status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'pending_review', 'active', 'paused', 'completed', 'rejected')),
      starts_at TIMESTAMPTZ,
      ends_at TIMESTAMPTZ,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CHECK (daily_budget_kobo <= total_budget_kobo)
    );
    CREATE INDEX IF NOT EXISTS marketplace_ad_campaigns_owner_idx ON marketplace_ad_campaigns(owner_id, created_at DESC);
    CREATE INDEX IF NOT EXISTS marketplace_ad_campaigns_active_idx ON marketplace_ad_campaigns(status, starts_at, ends_at);

    CREATE TABLE IF NOT EXISTS marketplace_ad_metrics (
      campaign_id BIGINT PRIMARY KEY REFERENCES marketplace_ad_campaigns(id) ON DELETE CASCADE,
      impressions BIGINT NOT NULL DEFAULT 0,
      clicks BIGINT NOT NULL DEFAULT 0,
      engagements BIGINT NOT NULL DEFAULT 0,
      conversions BIGINT NOT NULL DEFAULT 0,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

    CREATE TABLE IF NOT EXISTS marketplace_ad_operations (
      id BIGSERIAL PRIMARY KEY,
      campaign_id BIGINT NOT NULL REFERENCES marketplace_ad_campaigns(id) ON DELETE CASCADE,
      owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      idempotency_key TEXT NOT NULL,
      operation_type TEXT NOT NULL CHECK (operation_type IN ('CREATE_CAMPAIGN', 'STATUS_CHANGE')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      UNIQUE (owner_id, idempotency_key, operation_type)
    );
    CREATE INDEX IF NOT EXISTS marketplace_ad_operations_campaign_idx ON marketplace_ad_operations(campaign_id, created_at DESC);
  `);
  app.post("/api/advertising/campaigns/:id/submit-review", async (req, res) => {
    const auth = authenticate(req, res); if (!auth) return;
    const id = positiveInt(req.params.id); if (!id) return res.status(400).json({ error: "invalid campaign" });
    try {
      const result = await pool.query("UPDATE marketplace_ad_campaigns SET status='pending_review', review_note=NULL, updated_at=NOW() WHERE id=$1 AND owner_id=$2 AND status IN ('draft','rejected') RETURNING *", [id, auth.userId]);
      if (!result.rowCount) return res.status(409).json({ error: "campaign is not eligible for review" });
      return res.json({ campaign: result.rows[0] });
    } catch { return res.status(500).json({ error: "unable to submit campaign for review" }); }
  });

  app.post("/api/advertising/campaigns/:id/approve", async (req, res) => {
    const adminIds = String(process.env.FYNX_AD_ADMIN_IDS || "").split(",").map(v => v.trim()).filter(Boolean);
    const auth = authenticate(req, res); if (!auth) return;
    if (!adminIds.includes(String(auth.userId))) return res.status(403).json({ error: "advertising review access denied" });
    const id = positiveInt(req.params.id); if (!id) return res.status(400).json({ error: "invalid campaign" });
    const approved = req.body?.approved !== false;
    try {
      const result = await pool.query("UPDATE marketplace_ad_campaigns SET status=$1, approved_at=$2, review_note=$3, updated_at=NOW() WHERE id=$4 AND status='pending_review' RETURNING *",
        [approved ? "paused" : "rejected", approved ? new Date() : null, typeof req.body?.note === "string" ? req.body.note.slice(0,500) : null, id]);
      if (!result.rowCount) return res.status(409).json({ error: "campaign is not awaiting review" });
      return res.json({ campaign: result.rows[0] });
    } catch { return res.status(500).json({ error: "unable to review campaign" }); }
  });

  app.post("/api/advertising/campaigns/:id/payment/initialize", async (req, res) => {
    const auth = authenticate(req, res); if (!auth) return;
    const id = positiveInt(req.params.id); if (!id) return res.status(400).json({ error: "invalid campaign" });
    const secret = process.env.PAYSTACK_SECRET_KEY || "";
    if (!secret) return res.status(503).json({ error: "advertising payment provider is not configured yet" });
    try {
      const campaign = (await pool.query("SELECT * FROM marketplace_ad_campaigns WHERE id=$1 AND owner_id=$2", [id, auth.userId])).rows[0];
      if (!campaign) return res.status(404).json({ error: "campaign not found" });
      if (!campaign.approved_at) return res.status(409).json({ error: "campaign must be approved before payment" });
      const emailRow = (await pool.query("SELECT email FROM users WHERE id=$1", [auth.userId])).rows[0];
      const email = String(req.body?.email || emailRow?.email || "").trim();
      if (!/^[^@s]+@[^@s]+.[^@s]+$/.test(email)) return res.status(400).json({ error: "valid payment email is required" });
      const reference = "FYNX-AD-" + id + "-" + crypto.randomUUID();
      const response = await fetch("https://api.paystack.co/transaction/initialize", {
        method: "POST", headers: { Authorization: "Bearer " + secret, "Content-Type": "application/json" },
        body: JSON.stringify({ email, amount: campaign.total_budget_kobo, currency: "NGN", reference, metadata: { type: "FYNX_AD_CAMPAIGN", campaignId: String(id), ownerId: String(auth.userId) } })
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok || data?.status !== true || !data?.data?.authorization_url) return res.status(502).json({ error: "advertising payment initialization failed" });
      await pool.query("UPDATE marketplace_ad_campaigns SET payment_status='pending', payment_reference=$1, payment_amount_kobo=$2, updated_at=NOW() WHERE id=$3 AND owner_id=$4", [reference, campaign.total_budget_kobo, id, auth.userId]);
      return res.json({ authorizationUrl: data.data.authorization_url, reference, amountKobo: campaign.total_budget_kobo, currency: "NGN" });
    } catch { return res.status(502).json({ error: "advertising payment initialization failed" }); }
  });

  app.post("/api/advertising/campaigns/:id/payment/verify", async (req, res) => {
    const auth = authenticate(req, res); if (!auth) return;
    const id = positiveInt(req.params.id); const reference = String(req.body?.reference || "").trim();
    if (!id || !reference) return res.status(400).json({ error: "campaign and payment reference are required" });
    const secret = process.env.PAYSTACK_SECRET_KEY || "";
    if (!secret) return res.status(503).json({ error: "advertising payment provider is not configured yet" });
    try {
      const campaign = (await pool.query("SELECT * FROM marketplace_ad_campaigns WHERE id=$1 AND owner_id=$2", [id, auth.userId])).rows[0];
      if (!campaign || campaign.payment_reference !== reference) return res.status(404).json({ error: "payment not found" });
      const response = await fetch("https://api.paystack.co/transaction/verify/" + encodeURIComponent(reference), { headers: { Authorization: "Bearer " + secret } });
      const data = await response.json().catch(() => ({}));
      const paid = response.ok && data?.status === true && data?.data?.status === "success" && Number(data.data.amount) === Number(campaign.total_budget_kobo) && String(data.data.currency).toUpperCase() === "NGN";
      if (!paid) return res.status(409).json({ error: "payment has not been verified" });
      await pool.query("UPDATE marketplace_ad_campaigns SET payment_status='paid', updated_at=NOW() WHERE id=$1 AND owner_id=$2", [id, auth.userId]);
      return res.json({ paid: true, campaignId: String(id) });
    } catch { return res.status(502).json({ error: "advertising payment verification failed" }); }
  });

  app.get("/api/advertising/dashboard", async (req, res) => {
    const auth = authenticate(req, res); if (!auth) return;
    try {
      const result = await pool.query(`SELECT
        COUNT(*)::int AS campaigns,
        COUNT(*) FILTER (WHERE status='active')::int AS active_campaigns,
        COALESCE(SUM(spent_kobo),0)::bigint AS spent_kobo,
        COALESCE(SUM(total_budget_kobo),0)::bigint AS budget_kobo,
        COALESCE(SUM(m.impressions),0)::bigint AS impressions,
        COALESCE(SUM(m.clicks),0)::bigint AS clicks,
        COALESCE(SUM(m.engagements),0)::bigint AS engagements,
        COALESCE(SUM(m.conversions),0)::bigint AS conversions
        FROM marketplace_ad_campaigns c JOIN marketplace_ad_metrics m ON m.campaign_id=c.id WHERE c.owner_id=$1`, [auth.userId]);
      return res.json({ dashboard: result.rows[0] });
    } catch { return res.status(500).json({ error: "advertising dashboard unavailable" }); }
  });

}

let schemaReady = null;
function ready() {
  if (!schemaReady) schemaReady = ensureAdvertisingSchema();
  return schemaReady;
}

function authenticate(req, res) {
  if (!JWT_SECRET) return res.status(503).json({ error: "service unavailable" });
  const header = req.headers.authorization || "";
  if (!header.startsWith("Bearer ")) return res.status(401).json({ error: "authentication required" });
  try {
    const payload = jwt.verify(header.slice(7), JWT_SECRET);
    if (!payload?.sub) throw new Error("invalid token");
    return { userId: Number(payload.sub) };
  } catch {
    return res.status(401).json({ error: "authentication required" });
  }
}

function parseTargeting(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  for (const key of Object.keys(value)) if (!TARGETING_KEYS.has(key)) return null;
  const result = {};
  if (value.locations !== undefined) {
    if (!Array.isArray(value.locations) || value.locations.length > 50 || value.locations.some(v => typeof v !== "string" || v.length > 100)) return null;
    result.locations = value.locations;
  }
  if (value.interests !== undefined) {
    if (!Array.isArray(value.interests) || value.interests.length > 50 || value.interests.some(v => typeof v !== "string" || v.length > 100)) return null;
    result.interests = value.interests;
  }
  for (const key of ["ageMin", "ageMax"]) {
    if (value[key] !== undefined && (!Number.isInteger(value[key]) || value[key] < 13 || value[key] > 120)) return null;
    if (value[key] !== undefined) result[key] = value[key];
  }
  if (result.ageMin && result.ageMax && result.ageMin > result.ageMax) return null;
  return result;
}

function positiveInt(value, max = 9007199254740991) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 && number <= max ? number : null;
}

export function registerMarketplaceAdvertisingRoutes({ app }) {
  if (!app || !pool) return;
  void ready().catch(error => console.error("[fynx-ads] schema initialization failed", error));

  app.use("/api/advertising", async (_req, _res, next) => {
    try { await ready(); next(); } catch { next(new Error("advertising service unavailable")); }
  });

  app.post("/api/advertising/campaigns", async (req, res) => {
    const auth = authenticate(req, res);
    if (!auth) return;
    const {
      name, creativeType, creativeRef = null, headline = "", body = "", destinationUrl = null,
      targeting = {}, dailyBudgetKobo, totalBudgetKobo, startsAt = null, endsAt = null,
      idempotencyKey = ""
    } = req.body || {};
    if (typeof name !== "string" || name.trim().length < 1 || name.length > 120) return res.status(400).json({ error: "invalid campaign name" });
    if (!CREATIVE_TYPES.has(creativeType)) return res.status(400).json({ error: "invalid creative type" });
    if (creativeRef !== null && (typeof creativeRef !== "string" || creativeRef.length > 200)) return res.status(400).json({ error: "invalid creative reference" });
    if (typeof headline !== "string" || headline.length > 180 || typeof body !== "string" || body.length > 5000) return res.status(400).json({ error: "invalid creative content" });
    if (destinationUrl !== null && (typeof destinationUrl !== "string" || destinationUrl.length > 2000 || !/^https:\/\//i.test(destinationUrl))) return res.status(400).json({ error: "destination must use HTTPS" });
    const parsedTargeting = parseTargeting(targeting);
    const daily = positiveInt(dailyBudgetKobo);
    const total = positiveInt(totalBudgetKobo);
    if (!parsedTargeting || daily === null || total === null || daily < 1 || total < 1 || daily > total) return res.status(400).json({ error: "invalid advertising budget or targeting" });
    if (idempotencyKey && (typeof idempotencyKey !== "string" || idempotencyKey.length > 120)) return res.status(400).json({ error: "invalid idempotency key" });
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      if (idempotencyKey) {
        const prior = await client.query("SELECT c.* FROM marketplace_ad_operations o JOIN marketplace_ad_campaigns c ON c.id=o.campaign_id WHERE o.owner_id=$1 AND o.idempotency_key=$2 AND o.operation_type='CREATE_CAMPAIGN' FOR SHARE", [auth.userId, idempotencyKey]);
        if (prior.rowCount) {
          await client.query("COMMIT");
          return res.status(200).json({ campaign: prior.rows[0], idempotent: true });
        }
      }
      const inserted = await client.query(`INSERT INTO marketplace_ad_campaigns
        (owner_id,name,creative_type,creative_ref,headline,body,destination_url,targeting,daily_budget_kobo,total_budget_kobo,starts_at,ends_at)
        VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10,$11,$12) RETURNING *`,
        [auth.userId, name.trim(), creativeType, creativeRef, headline, body, destinationUrl, JSON.stringify(parsedTargeting), daily, total, startsAt, endsAt]);
      const campaign = inserted.rows[0];
      await client.query("INSERT INTO marketplace_ad_metrics(campaign_id) VALUES($1)", [campaign.id]);
      if (idempotencyKey) await client.query("INSERT INTO marketplace_ad_operations(campaign_id,owner_id,idempotency_key,operation_type) VALUES($1,$2,$3,'CREATE_CAMPAIGN')", [campaign.id, auth.userId, idempotencyKey]);
      await client.query("COMMIT");
      return res.status(201).json({ campaign });
    } catch (error) {
      await client.query("ROLLBACK");
      console.error("[fynx-ads] campaign creation failed", error);
      return res.status(500).json({ error: "unable to create campaign" });
    } finally { client.release(); }
  });

  app.get("/api/advertising/campaigns", async (req, res) => {
    const auth = authenticate(req, res);
    if (!auth) return;
    try {
      const result = await pool.query(`SELECT c.*, m.impressions, m.clicks, m.engagements, m.conversions
        FROM marketplace_ad_campaigns c JOIN marketplace_ad_metrics m ON m.campaign_id=c.id
        WHERE c.owner_id=$1 ORDER BY c.created_at DESC LIMIT 100`, [auth.userId]);
      return res.json({ campaigns: result.rows });
    } catch { return res.status(500).json({ error: "unable to load campaigns" }); }
  });

  app.get("/api/advertising/campaigns/:id", async (req, res) => {
    const auth = authenticate(req, res);
    if (!auth) return;
    const id = positiveInt(req.params.id);
    if (!id) return res.status(400).json({ error: "invalid campaign" });
    try {
      const result = await pool.query(`SELECT c.*, m.impressions, m.clicks, m.engagements, m.conversions
        FROM marketplace_ad_campaigns c JOIN marketplace_ad_metrics m ON m.campaign_id=c.id WHERE c.id=$1 AND c.owner_id=$2`, [id, auth.userId]);
      if (!result.rowCount) return res.status(404).json({ error: "campaign not found" });
      return res.json({ campaign: result.rows[0] });
    } catch { return res.status(500).json({ error: "unable to load campaign" }); }
  });

  app.patch("/api/advertising/campaigns/:id/status", async (req, res) => {
    const auth = authenticate(req, res);
    if (!auth) return;
    const id = positiveInt(req.params.id);
    const status = req.body?.status;
    const idempotencyKey = req.body?.idempotencyKey || "";
    if (!id || !CAMPAIGN_STATUSES.has(status) || (idempotencyKey && (typeof idempotencyKey !== "string" || idempotencyKey.length > 120))) return res.status(400).json({ error: "invalid status request" });
    if (status === "active") {
      const gate = await pool.query("SELECT approved_at,payment_status FROM marketplace_ad_campaigns WHERE id=$1 AND owner_id=$2", [id, auth.userId]);
      if (!gate.rowCount || !gate.rows[0].approved_at) return res.status(409).json({ error: "campaign approval required before activation" });
      if (gate.rows[0].payment_status !== "paid") return res.status(409).json({ error: "advertising payment required before activation" });
    }
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const current = await client.query("SELECT * FROM marketplace_ad_campaigns WHERE id=$1 AND owner_id=$2 FOR UPDATE", [id, auth.userId]);
      if (!current.rowCount) { await client.query("ROLLBACK"); return res.status(404).json({ error: "campaign not found" }); }
      if (idempotencyKey) {
        const prior = await client.query("SELECT 1 FROM marketplace_ad_operations WHERE campaign_id=$1 AND owner_id=$2 AND idempotency_key=$3 AND operation_type='STATUS_CHANGE'", [id, auth.userId, idempotencyKey]);
        if (prior.rowCount) { await client.query("COMMIT"); return res.json({ campaign: current.rows[0], idempotent: true }); }
      }
      const updated = await client.query("UPDATE marketplace_ad_campaigns SET status=$1, updated_at=NOW() WHERE id=$2 RETURNING *", [status, id]);
      if (idempotencyKey) await client.query("INSERT INTO marketplace_ad_operations(campaign_id,owner_id,idempotency_key,operation_type) VALUES($1,$2,$3,'STATUS_CHANGE')", [id, auth.userId, idempotencyKey]);
      await client.query("COMMIT");
      return res.json({ campaign: updated.rows[0] });
    } catch { await client.query("ROLLBACK"); return res.status(500).json({ error: "unable to update campaign" }); }
    finally { client.release(); }
  });

  // Metrics ingestion is deliberately limited to approved active campaigns. Billing/spend remains server-owned.
  app.post("/api/advertising/campaigns/:id/metric", async (req, res) => {
    const metric = req.body?.metric;
    if (!["impression", "click", "engagement", "conversion"].includes(metric)) return res.status(400).json({ error: "invalid metric" });
    const id = positiveInt(req.params.id);
    if (!id) return res.status(400).json({ error: "invalid campaign" });
    try {
      const result = await pool.query(`UPDATE marketplace_ad_metrics m SET
        impressions = m.impressions + CASE WHEN $1='impression' THEN 1 ELSE 0 END,
        clicks = m.clicks + CASE WHEN $1='click' THEN 1 ELSE 0 END,
        engagements = m.engagements + CASE WHEN $1='engagement' THEN 1 ELSE 0 END,
        conversions = m.conversions + CASE WHEN $1='conversion' THEN 1 ELSE 0 END,
        updated_at=NOW()
        FROM marketplace_ad_campaigns c WHERE m.campaign_id=$2 AND c.id=m.campaign_id AND c.status='active' RETURNING m.*`, [metric, id]);
      if (!result.rowCount) return res.status(404).json({ error: "campaign not active" });
      return res.status(204).end();
    } catch { return res.status(500).json({ error: "unable to record metric" }); }
  });
}
