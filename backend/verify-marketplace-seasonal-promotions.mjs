import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const advertising = await readFile(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const banner = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplacePromotionalBanner.kt", import.meta.url), "utf8");
const panel = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt", import.meta.url), "utf8");
const client = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionClient.kt", import.meta.url), "utf8");
const adminUi = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt", import.meta.url), "utf8");
const adminCenter = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt", import.meta.url), "utf8");

const routes = [
  'app.get("/api/admin/marketplace/seasonal-promotions"',
  'app.post("/api/admin/marketplace/seasonal-promotions"',
  'app.patch("/api/admin/marketplace/seasonal-promotions/:id"',
  'app.delete("/api/admin/marketplace/seasonal-promotions/:id"',
  'app.get("/api/marketplace/seasonal-feed"'
];
const checks = [
  ["seasonal schedule has a persistent database table and active-window index", advertising.includes("CREATE TABLE IF NOT EXISTS marketplace_seasonal_promotions") && advertising.includes("marketplace_seasonal_promotions_active_window_idx")],
  ["all admin seasonal CRUD routes are registered", routes.every(route => advertising.includes(route))],
  ["seasonal create, update and delete require authenticated Owner/Admin access", advertising.includes("authenticate(req, res)") && advertising.includes("requireMarketplaceAdmin(req, res, auth)")],
  ["campaign creation validates occasion key and headline lengths", advertising.includes("occasion key must use 2-60 letters") && advertising.includes("headline.length < 3")],
  ["campaign schedule requires valid start/end dates and a bounded 45-day window", advertising.includes("endsAt <= startsAt") && advertising.includes("45 * 24 * 60 * 60 * 1000")],
  ["artwork URLs are HTTPS-only and bounded", advertising.includes("artwork must be an HTTPS image URL") && advertising.includes("artworkUrl.length > 1500")],
  ["seasonal public feed only returns active campaigns inside the scheduled window", advertising.includes("active=TRUE AND starts_at <= NOW() AND ends_at > NOW()")],
  ["paid sponsored campaign approval and payment checks remain separate", advertising.includes("c.payment_status = 'paid'") && advertising.includes("c.approved_at IS NOT NULL")],
  ["Android reads authenticated seasonal schedule feed", client.includes("/api/marketplace/seasonal-feed") && client.includes("FynxMarketplaceSeasonalPromotion")],
  ["Marketplace home loads seasonal campaigns and passes them to the banner", panel.includes("FynxMarketplaceSeasonalPromotionClient.load(context)") && panel.includes("seasonalPromotions = seasonalPromotions")],
  ["banner rotates server-scheduled variable-date celebrations", banner.includes("seasonalPromotions.take(4)") && banner.includes("scheduled-seasonal-") && banner.includes("promotion.occasionKey")],
  ["scheduled celebration artwork is displayed with crop fit when supplied", banner.includes("slide.artworkUrl") && banner.includes("contentScale = ContentScale.Crop")],
  ["fixed-date fallback is suppressed when a matching admin schedule is active", banner.includes("seasonalPromotions.none { it.occasionKey.equals(celebration.first")],
  ["Owner/Admin center exposes the seasonal scheduler", adminCenter.includes("FynxMarketplaceSeasonalPromotionAdminPanel()")],
  ["scheduler exposes date windows, celebration copy and optional artwork URL", adminUi.includes("Starts at (ISO 8601)") && adminUi.includes("Ends at (ISO 8601)") && adminUi.includes("Celebration write-up") && adminUi.includes("Artwork image HTTPS URL")],
  ["seasonal schedules are independent of Best Seller scoring", !advertising.slice(advertising.indexOf('app.get("/api/marketplace/seasonal-feed"')).includes("marketplace_ad_metrics") && banner.includes("BEST SELLER OF THE WEEK")]
];

for (const [name, ok] of checks) {
  console.log(`${ok ? "PASS" : "FAIL"}: ${name}`);
  assert.ok(ok, name);
}
console.log(`Marketplace seasonal promotion verification GREEN (${checks.length} checks)`);
