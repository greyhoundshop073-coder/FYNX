import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

// Railway builds only /backend. Keep this startup guard independent of Android sources.
const source = await readFile(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const metricStart = source.indexOf('app.post("/api/advertising/campaigns/:id/metric"');
const feedStart = source.indexOf('app.get("/api/advertising/marketplace-feed"');
assert.ok(metricStart >= 0 && feedStart > metricStart, "CPC metric and sponsored-feed routes must be registered");
const metricRoute = source.slice(metricStart, feedStart);
const feedRoute = source.slice(feedStart);
const checks = [
  ["campaigns persist a positive server-side CPC in kobo", source.includes("cost_per_click_kobo BIGINT") && source.includes("cost_per_click_kobo > 0")],
  ["click ledger has one buyer charge per campaign and Lagos day", source.includes("UNIQUE (campaign_id, viewer_id, click_day)")],
  ["click ledger deduplicates idempotency keys", source.includes("UNIQUE (campaign_id, click_key)")],
  ["billable click transaction locks the campaign row", metricRoute.includes("SELECT * FROM marketplace_ad_campaigns WHERE id=$1 FOR UPDATE")],
  ["only authenticated buyers can create payable clicks", metricRoute.includes("advertisers cannot bill their own clicks") && metricRoute.includes("const auth = authenticate(req, res)")],
  ["campaign must be active, approved, paid and inside its schedule", metricRoute.includes('campaign.status !== "active"') && metricRoute.includes('campaign.payment_status !== "paid"') && metricRoute.includes("!campaign.approved_at")],
  ["total prepaid limit is checked before billing", metricRoute.includes("totalSpent + clickCost > totalBudget")],
  ["daily campaign cap is checked before billing", metricRoute.includes("todaySpent + clickCost > dailyBudget")],
  ["spend is updated only after the unique click ledger insert", metricRoute.indexOf("INSERT INTO marketplace_ad_click_events") < metricRoute.indexOf("SET spent_kobo=$2")],
  ["campaign completes when total prepaid balance is exhausted", metricRoute.includes("status='completed'")],
  ["campaign clicks are upserted into metrics for legacy campaigns", metricRoute.includes("INSERT INTO marketplace_ad_metrics (campaign_id, clicks, updated_at)") && metricRoute.includes("ON CONFLICT (campaign_id) DO UPDATE")],
  ["feed hides ads that cannot pay for another click within both caps", feedRoute.includes("c.spent_kobo + c.cost_per_click_kobo <= c.total_budget_kobo") && feedRoute.includes("c.daily_spent_kobo + c.cost_per_click_kobo <= c.daily_budget_kobo")],
  ["feed excludes the campaign owner's own listings", feedRoute.includes("c.owner_id <> $1")],
];
for (const [name, ok] of checks) {
  console.log(`${ok ? "PASS" : "FAIL"}: ${name}`);
  assert.ok(ok, name);
}
console.log(`Marketplace CPC Railway startup verification PASSED (${checks.length} backend-only checks).`);
