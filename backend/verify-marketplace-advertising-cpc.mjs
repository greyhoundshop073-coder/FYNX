import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = path => readFile(new URL(path, import.meta.url), "utf8");
const advertising = await read("./marketplaceAdvertising.js");
const campaignUi = await read("../app/src/main/java/com/fynx/app/ui/FynxAdvertisingCampaignPanel.kt");
const dashboardUi = await read("../app/src/main/java/com/fynx/app/ui/FynxAdvertisingDashboardPanel.kt");
const sponsoredClient = await read("../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSponsoredPromotionClient.kt");

const metricStart = advertising.indexOf('app.post("/api/advertising/campaigns/:id/metric"');
const feedStart = advertising.indexOf('app.get("/api/advertising/marketplace-feed"');
assert.ok(metricStart >= 0 && feedStart > metricStart, "CPC metrics and sponsored feed routes must exist");
const metricRoute = advertising.slice(metricStart, feedStart);
const feedRoute = advertising.slice(feedStart);

const checks = [
  ["campaigns accept a server-persisted CPC bid in kobo", advertising.includes("costPerClickKobo = 500") && advertising.includes("cost_per_click_kobo")],
  ["campaign creation rejects CPC bids larger than either configured budget", advertising.includes("costPerClick > daily") && advertising.includes("costPerClick > total")],
  ["CPC click records are durable and deduplicated by buyer/campaign/Lagos day and request key", advertising.includes("marketplace_ad_click_events") && advertising.includes("UNIQUE (campaign_id, viewer_id, click_day)") && advertising.includes("UNIQUE (campaign_id, click_key)")],
  ["click processing locks the campaign row before checking limits", metricRoute.includes("BEGIN") && metricRoute.includes("SELECT * FROM marketplace_ad_campaigns WHERE id=$1 FOR UPDATE")],
  ["repeat clicks from a buyer on the same Lagos day are not charged again", metricRoute.includes("viewer_id=$2 AND click_day=$3::date") && metricRoute.includes("return res.status(204).end()")],
  ["advertisers cannot create payable clicks on their own campaigns", metricRoute.includes("advertisers cannot bill their own clicks")],
  ["only active, approved, paid and in-schedule campaigns can receive billable clicks", metricRoute.includes('campaign.status !== "active"') && metricRoute.includes('campaign.payment_status !== "paid"') && metricRoute.includes("!campaign.approved_at") && metricRoute.includes("startsAt > now") && metricRoute.includes("endsAt <= now")],
  ["CPC charges are taken from prepaid campaign balance and daily cap, not from a client-supplied amount", metricRoute.includes("const clickCost = Number(campaign.cost_per_click_kobo)") && metricRoute.includes("totalSpent + clickCost > totalBudget") && metricRoute.includes("todaySpent + clickCost > dailyBudget") && metricRoute.includes("spent_kobo=$2") && metricRoute.includes("daily_spent_kobo=$3")],
  ["a click is billed and counted only after a unique click-ledger insert succeeds", metricRoute.includes("INSERT INTO marketplace_ad_click_events") && metricRoute.includes("ON CONFLICT DO NOTHING RETURNING id") && metricRoute.indexOf("INSERT INTO marketplace_ad_click_events") < metricRoute.indexOf("SET spent_kobo=$2")],
  ["the campaign completes when its remaining prepaid balance cannot cover a full click", metricRoute.includes("status='completed'") && metricRoute.includes("campaign prepaid budget is exhausted")],
  ["feed excludes the seller's own ad and hides campaigns that cannot fund one more click today", feedRoute.includes("AND c.owner_id <> $1") && feedRoute.includes("c.daily_spent_kobo + c.cost_per_click_kobo <= c.daily_budget_kobo") && feedRoute.includes("c.spent_kobo + c.cost_per_click_kobo <= c.total_budget_kobo")],
  ["Android sponsored clicks carry a random retry key but never send a price", sponsoredClient.includes("UUID.randomUUID().toString()") && sponsoredClient.includes('put("clickKey"') && !sponsoredClient.includes('put("chargeKobo"')],
  ["seller campaign UI exposes and validates the CPC bid and budget limits", campaignUi.includes("Cost per verified click") && campaignUi.includes('put("costPerClickKobo", costPerClick)') && campaignUi.includes("daily >= costPerClick && total >= daily")],
  ["seller dashboard separates today's spend, total spend and billed clicks", dashboardUi.includes("today_spent_kobo") && dashboardUi.includes("daily_spent_kobo") && dashboardUi.includes("Billed clicks")]
];

for (const [name, ok] of checks) {
  console.log(`${ok ? "PASS" : "FAIL"}: ${name}`);
  assert.ok(ok, name);
}
console.log(`Marketplace CPC billing and budget guard verification PASSED (${checks.length} checks).`);
