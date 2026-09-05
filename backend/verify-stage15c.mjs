import fs from "node:fs";

const advertising = fs.readFileSync(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");

const checks = [
  ["campaign schema", advertising.includes("CREATE TABLE IF NOT EXISTS marketplace_ad_campaigns")],
  ["targeting validation", advertising.includes("parseTargeting")],
  ["budget validation", advertising.includes("daily_budget_kobo") && advertising.includes("total_budget_kobo")],
  ["campaign ownership", advertising.includes("owner_id=$2")],
  ["campaign metrics", advertising.includes("marketplace_ad_metrics")],
  ["idempotent campaign creation", advertising.includes("CREATE_CAMPAIGN") && advertising.includes("idempotencyKey")],
  ["activation approval gate", advertising.includes("campaign approval required before activation")],
  ["status controls", advertising.includes("STATUS_CHANGE")],
  ["https destination protection", advertising.includes("destination must use HTTPS")],
  ["advertising route wiring", scalability.includes("registerMarketplaceAdvertisingRoutes")],
];

for (const [name, ok] of checks) {
  if (!ok) {
    console.error(`Stage 15C advertising verification failed: ${name}`);
    process.exit(1);
  }
  console.log(`PASS: ${name}`);
}
console.log("Stage 15C advertising foundation verification passed");
