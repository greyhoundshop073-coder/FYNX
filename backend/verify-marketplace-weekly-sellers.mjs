import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const reputation = await readFile(new URL("./marketplaceReputation.js", import.meta.url), "utf8");
const marketplaceSchema = await readFile(new URL("./marketplaceSchema.js", import.meta.url), "utf8");
const sellerStore = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerStoreDialog.kt", import.meta.url), "utf8");
const weeklyClient = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplaceWeeklySellerClient.kt", import.meta.url), "utf8");
const panel = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt", import.meta.url), "utf8");
const routeStart = reputation.indexOf("app.get('/api/marketplace/sellers/weekly'");
const routeEnd = reputation.indexOf("app.get('/api/marketplace/sellers/:username/reputation'", routeStart);
assert.ok(routeStart >= 0 && routeEnd > routeStart, "weekly seller endpoint must be registered before the username reputation route");
const route = reputation.slice(routeStart, routeEnd);

const checks = [
  ["weekly ranking uses the authenticated Marketplace route", reputation.includes("app.get('/api/marketplace/sellers/weekly', auth")],
  ["ranking is bounded to the current Monday-to-Monday Lagos week", route.includes("date_trunc('week'") && route.includes("Africa/Lagos") && route.includes("INTERVAL '1 week'")],
  ["only fully completed Marketplace orders count", route.includes("o.status = 'COMPLETED'") && route.includes("o.completed_at >= w.week_start") && route.includes("o.completed_at < w.week_end")],
  ["weekly ranking initializes shared reserved-inventory schema", route.includes("await ensureMarketplaceSchemaForPool(pool)") && marketplaceSchema.includes("ADD COLUMN IF NOT EXISTS reserved_quantity")],
  ["seller ranking uses completed orders and unique buyers with deterministic tie-breakers", route.includes("COUNT(*)::INTEGER AS completed_orders") && route.includes("COUNT(DISTINCT o.buyer_id)") && route.includes("completed_orders DESC, unique_buyers DESC")],
  ["sellers without a live in-stock listing are excluded before rank assignment", route.includes("eligible_sellers AS") && route.includes("COALESCE(l.active, TRUE) = TRUE") && route.includes("GREATEST(0, l.quantity - COALESCE(l.reserved_quantity, 0)) > 0") && route.indexOf("eligible_sellers AS") < route.indexOf("ranked_sellers AS")],
  ["weekly response includes the real featured listing identity and product data", route.includes("featured_listing_id") && route.includes("featured_product_title") && route.includes("featured_product_price")],
  ["weekly winner features the product with the strongest completed-order count that remains in stock", route.includes("weekly_product_sales AS") && route.includes("COUNT(*)::INTEGER AS completed_product_orders") && route.includes("ORDER BY COALESCE(p.completed_product_orders, 0) DESC")],
  ["paid advertising cannot influence weekly seller ranking", !route.includes("marketplace_ad_campaigns") && !route.includes("spent_kobo") && !route.includes("impressions")],
  ["existing individual seller reputation route remains registered", reputation.includes("app.get('/api/marketplace/sellers/:username/reputation'")],
  ["existing completed-order-only review guard remains intact", reputation.includes("if (order.status !== 'COMPLETED')")],
  ["existing order/payment handler code remains in the reputation module", reputation.includes("verifyOrderPaymentAttempt") && reputation.includes("paystackRequest('/transaction/initialize'")],
  ["weekly awards have a persistent snapshot table and unique week/rank identity", marketplaceSchema.includes("CREATE TABLE IF NOT EXISTS marketplace_weekly_seller_awards") && marketplaceSchema.includes("PRIMARY KEY (week_start, weekly_rank)")],
  ["history snapshots only cover closed Lagos weeks and only completed orders", reputation.includes("archiveClosedMarketplaceSellerWeeks") && reputation.includes("INTERVAL '1 week'") && reputation.includes("o.status = 'COMPLETED'") && reputation.includes("ON CONFLICT (week_start, weekly_rank) DO NOTHING")],
  ["history endpoint is authenticated, username-filtered and limit-bounded", reputation.includes("app.get('/api/marketplace/sellers/weekly/history', auth") && reputation.includes("parseSeller(String(req.query.username))") && reputation.includes("Math.min(100, Math.max(1, requestedLimit))")],
  ["Seller Center can only retrieve the authenticated account weekly-award history", reputation.includes("req.query?.mine") && reputation.includes("seller_id = $4") && reputation.includes("req.user.sub")],
  ["Android weekly award history client calls the authenticated history route", weeklyClient.includes("/api/marketplace/sellers/weekly/history?username=") && weeklyClient.includes("FynxMarketplaceSellerWeeklyAward")],
  ["seller storefront displays past verified weekly awards", sellerStore.includes("FynxMarketplaceWeeklySellerClient.history(context, username)") && sellerStore.includes("FYNX Seller Hall of Fame") && sellerStore.includes("Recognition is based on completed Marketplace orders")],
  ["weekly history snapshots never use paid campaign metrics", !reputation.slice(reputation.indexOf("const archiveClosedMarketplaceSellerWeeks"), reputation.indexOf("app.get('/api/marketplace/sellers/:username/reputation'")).includes("marketplace_ad_campaigns")]
];

for (const [name, ok] of checks) {
  console.log(`${ok ? "PASS" : "FAIL"}: ${name}`);
  assert.ok(ok, name);
}
console.log(`Marketplace weekly seller verification GREEN (${checks.length} checks)`);