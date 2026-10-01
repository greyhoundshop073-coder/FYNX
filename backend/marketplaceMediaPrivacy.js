import pg from "pg";
import jwt from "jsonwebtoken";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false, max: 2, min: 0, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 10_000, query_timeout: 12_000, keepAlive: true }) : null;

function viewerId(req) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) return null;
  try { return String(jwt.verify(token, JWT_SECRET).sub); } catch { return null; }
}

export function installMarketplaceMediaPrivacyGuard(app) {
  if (!app?.use || !app?._router?.stack || !pool) return;
  if (app._router.stack.some((layer) => layer.fynxMarketplaceMediaPrivacyGuard)) return;
  const guard = async (req, res, next) => {
    if (req.method !== "GET") return next();
    const match = /^\/api\/marketplace\/media\/(\d+)$/.exec(req.path || "");
    if (!match) return next();
    const userId = viewerId(req);
    if (!userId) return next();
    const mediaId = Number(match[1]);
    if (!Number.isInteger(mediaId) || mediaId < 1) return next();
    try {
      const result = await pool.query(`SELECT EXISTS (SELECT 1 FROM marketplace_listings ml WHERE ml.active=TRUE AND EXISTS (SELECT 1 FROM regexp_split_to_table(regexp_replace(CAST(ml.media_ids AS TEXT),'[^0-9]+',',','g'),',') x WHERE x=CAST($1 AS TEXT)) AND EXISTS (SELECT 1 FROM blocks b WHERE (b.blocker_id=$2 AND b.blocked_id=ml.seller_id) OR (b.blocked_id=$2 AND b.blocker_id=ml.seller_id)) AND NOT EXISTS (SELECT 1 FROM marketplace_orders mo WHERE (mo.buyer_id=$2 OR mo.seller_id=$2) AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(mo.product_snapshot->'mediaIds','[]'::jsonb)) y WHERE y=CAST($1 AS TEXT)))) AS blocked`, [mediaId, userId]);
      if (result.rows[0]?.blocked) return res.status(403).json({ error: "marketplace media unavailable" });
      return next();
    } catch (error) { console.error("marketplace media privacy guard", error); return res.status(503).json({ error: "marketplace media privacy unavailable" }); }
  };
  app.use(guard);
  const index = app._router.stack.length - 1;
  const layer = app._router.stack[index];
  if (!layer) return;
  layer.fynxMarketplaceMediaPrivacyGuard = true;

  const targetIndexes = app._router.stack.reduce((indexes, candidate, candidateIndex) => {
    const path = candidate.route?.path;
    if (path === "/api/marketplace/media/:id") indexes.push(candidateIndex);
    return indexes;
  }, []);
  if (targetIndexes.length) {
    app._router.stack.splice(index, 1);
    app._router.stack.splice(Math.min(...targetIndexes), 0, layer);
  }
}
