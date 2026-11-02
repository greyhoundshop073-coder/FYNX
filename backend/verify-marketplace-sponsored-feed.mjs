import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const advertising = await readFile(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const start = advertising.indexOf('app.get("/api/advertising/marketplace-feed"');
const end = advertising.indexOf("app.post(\"/api/advertising/campaigns/:id/metric\"", start);
assert.ok(start >= 0 && end > start, "Marketplace sponsored feed route must be registered");
const route = advertising.slice(start, end);

const checks = [
  ["sponsored feed requires an authenticated FYNX session", route.includes("const auth = authenticate(req, res)") && route.includes("if (!auth) return")],
  ["only product campaigns can be displayed in the product banner", route.includes("c.creative_type = 'product'")],
  ["only approved and paid campaigns can be displayed", route.includes("c.status = 'active'") && route.includes("c.payment_status = 'paid'") && route.includes("c.approved_at IS NOT NULL")],
  ["campaign schedule is enforced by the server", route.includes("c.starts_at <= NOW()") && route.includes("c.ends_at > NOW()")],
  ["campaigns stop displaying after their total budget is consumed", route.includes("c.spent_kobo < c.total_budget_kobo")],
  ["campaign creative must reference the advertiser's canonical owned listing", route.includes("l.id::text = c.creative_ref AND l.seller_id = c.owner_id")],
  ["only active products with unreserved stock can be promoted", route.includes("COALESCE(l.active, TRUE) = TRUE") && route.includes("GREATEST(0, l.quantity - COALESCE(l.reserved_quantity, 0)) > 0")],
  ["audience-targeted campaigns are not broadcast to everyone", route.includes("c.targeting ? 'ageMin'") && route.includes("jsonb_array_length(c.targeting->'locations')") && route.includes("jsonb_array_length(c.targeting->'interests')")],
  ["promotional feed returns real listing media IDs and prices", route.includes("l.media_ids") && route.includes("l.price") && route.includes("mediaIds: Array.isArray(row.media_ids)")],
  ["sponsored campaign selection is budget-balanced rather than used for seller awards", route.includes("ORDER BY c.spent_kobo ASC") && !route.includes("marketplace_seller_reviews")],
  ["the weekly seller award remains separate from advertising data", !advertising.slice(start, end).includes("weekly_rank")]
];
for (const [name, ok] of checks) {
  console.log(`${ok ? "PASS" : "FAIL"}: ${name}`);
  assert.ok(ok, name);
}
console.log(`Marketplace sponsored feed verification GREEN (${checks.length} checks)`);
