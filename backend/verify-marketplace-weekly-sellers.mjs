import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const reputation = await readFile(new URL("./marketplaceReputation.js", import.meta.url), "utf8");
const marketplaceSchema = await readFile(new URL("./marketplaceSchema.js", import.meta.url), "utf8");
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
  ["paid advertising cannot influence weekly seller ranking", !route.includes("marketplace_ad_campaigns") && !route.includes("spent_kobo") && !route.includes("impressions")],
  ["existing individual seller reputation route remains registered", reputation.includes("app.get('/api/marketplace/sellers/:username/reputation'")],
  ["existing completed-order-only review guard remains intact", reputation.includes("if (order.status !== 'COMPLETED')")],
  ["existing order/payment handler code remains in the reputation module", reputation.includes("verifyOrderPaymentAttempt") && reputation.includes("paystackRequest('/transaction/initialize'")]
];

for (const [name, ok] of checks) {
  console.log(`${ok ? "PASS" : "FAIL"}: ${name}`);
  assert.ok(ok, name);
}
console.log(`Marketplace weekly seller verification GREEN (${checks.length} checks)`);