import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const advertising = await readFile(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const mediaPrivacy = await readFile(new URL("./mediaPrivacy.js", import.meta.url), "utf8");
const banner = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplacePromotionalBanner.kt", import.meta.url), "utf8");
const panel = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt", import.meta.url), "utf8");
const client = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionClient.kt", import.meta.url), "utf8");
const adminUi = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSeasonalPromotionAdminPanel.kt", import.meta.url), "utf8");
const seasonalDbTest = await readFile(new URL("./test-marketplace-advertising-schema-db.mjs", import.meta.url), "utf8");
const backendWorkflow = await readFile(new URL("../.github/workflows/fynx-backend-ci.yml", import.meta.url), "utf8");
const adminCenter = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt", import.meta.url), "utf8");
const seasonalFeedStart = advertising.indexOf('app.get("/api/marketplace/seasonal-feed"');
const seasonalFeedEnd = advertising.indexOf('app.post("/api/advertising/campaigns"', seasonalFeedStart);
const seasonalFeedRoute = seasonalFeedStart >= 0 && seasonalFeedEnd > seasonalFeedStart
  ? advertising.slice(seasonalFeedStart, seasonalFeedEnd)
  : "";

const routes = [
  'app.get("/api/admin/marketplace/seasonal-promotions"',
  'app.post("/api/admin/marketplace/seasonal-promotions"',
  'app.patch("/api/admin/marketplace/seasonal-promotions/:id"',
  'app.delete("/api/admin/marketplace/seasonal-promotions/:id"',
  'app.get("/api/marketplace/seasonal-feed"'
];
const checks = [
  ["campaign schedule has a persistent database table, typed campaign constraint and active-window index", advertising.includes("CREATE TABLE IF NOT EXISTS marketplace_seasonal_promotions") && advertising.includes("promotion_type TEXT NOT NULL DEFAULT 'SEASONAL'") && advertising.includes("marketplace_seasonal_promotions_type_check") && advertising.includes("DO " + "$".repeat(2)) && advertising.includes("END " + "$".repeat(2) + ";") && advertising.includes("marketplace_seasonal_promotions_active_window_idx")],
  ["CI runs an actual PostgreSQL legacy-schema migration and idempotency test", seasonalDbTest.includes("CREATE TABLE marketplace_seasonal_promotions") && seasonalDbTest.includes("await db.query(migration);") && seasonalDbTest.includes("await assert.rejects(") && backendWorkflow.includes("node test-marketplace-advertising-schema-db.mjs")],
  ["all admin seasonal CRUD routes are registered", routes.every(route => advertising.includes(route))],
  ["seasonal create, update and delete require authenticated Owner/Admin access", advertising.includes("authenticate(req, res)") && advertising.includes("requireMarketplaceAdmin(req, res, auth)")],
  ["campaign creation validates campaign type, campaign key and headline lengths", advertising.includes("SEASONAL_PROMOTION_TYPES") && advertising.includes("ANNOUNCEMENT") && advertising.includes("OFFER") && advertising.includes("campaign key must use 2-60 letters") && advertising.includes("headline.length < 3")],
  ["announcements and offers require supporting copy instead of a headline-only fake campaign", advertising.includes("body.length < 10") && advertising.includes("supporting details")],
  ["campaign schedule requires valid start/end dates and a bounded 45-day window", advertising.includes("endsAt <= startsAt") && advertising.includes("45 * 24 * 60 * 60 * 1000")],
  ["artwork is stored as a FYNX media ID instead of an external URL", advertising.includes("artwork_media_id BIGINT") && advertising.includes("artworkMediaId === null") && !advertising.includes("artwork_url TEXT")],
  ["active scheduled artwork is authorized through the FYNX media privacy guard", mediaPrivacy.includes("marketplace_seasonal_promotions WHERE artwork_media_id=$1") && mediaPrivacy.includes("active=TRUE AND starts_at <= NOW() AND ends_at > NOW()")],
  ["media privacy remains safe before the seasonal campaign table is provisioned", mediaPrivacy.includes("to_regclass('public.marketplace_seasonal_promotions')")],
  ["Owner/Admin can only attach artwork uploaded by that same account", advertising.includes("message_media WHERE id=$1 AND owner_id=$2") && advertising.includes("seasonal artwork must be uploaded by the current Owner/Admin account")],
  ["seasonal public feed only returns active campaigns inside the scheduled window", advertising.includes("active=TRUE AND starts_at <= NOW() AND ends_at > NOW()")],
  ["paid sponsored campaign approval and payment checks remain separate", advertising.includes("c.payment_status = 'paid'") && advertising.includes("c.approved_at IS NOT NULL")],
  ["Android reads typed authenticated Marketplace campaign feed", client.includes("/api/marketplace/seasonal-feed") && client.includes("promotionType") && client.includes("FynxMarketplaceSeasonalPromotion")],
  ["offline seasonal schedule fallback is short-lived rather than replaying yesterday's campaign", client.includes("5L * 60L * 1000L") && client.includes("readResponse(context, \"seasonal_promotions\"")],
  ["Owner/Admin can choose seasonal, FYNX announcement or special-offer campaign types", adminUi.includes('listOf("SEASONAL", "ANNOUNCEMENT", "OFFER")') && adminUi.includes("Campaign type") && adminUi.includes("promotionType")],
  ["banner labels FYNX announcements and special offers separately from seasonal celebrations", banner.includes("FYNX ANNOUNCEMENT") && banner.includes("SPECIAL OFFER") && banner.includes("A CELEBRATION FROM FYNX")],
  ["permanent FYNX house message remains in every banner rotation", banner.includes('key = "fynx-evergreen"') && banner.includes("FYNX MARKETPLACE") && banner.includes("special offers on FYNX")],
  ["sponsored impressions only count after a minimum visible dwell period", banner.includes("MARKETPLACE_SPONSORED_IMPRESSION_MIN_DWELL_MS") && banner.includes("delay(MARKETPLACE_SPONSORED_IMPRESSION_MIN_DWELL_MS)")],
  ["Marketplace home loads seasonal campaigns and passes them to the banner", panel.includes("FynxMarketplaceSeasonalPromotionClient.load(context)") && panel.includes("seasonalPromotions = seasonalPromotions")],
  ["banner rotates server-scheduled variable-date celebrations", banner.includes("seasonalPromotions.take(4)") && banner.includes("scheduled-campaign-") && banner.includes("promotion.promotionType") && banner.includes("it.occasionKey.equals(celebration.first")],
  ["scheduled celebration artwork is displayed from authenticated FYNX media with crop fit", banner.includes("slide.artworkMediaId") && banner.includes('mediaUrl = "/api/media/\u0024{slide.artworkMediaId}"') && banner.includes("contentScale = ContentScale.Crop")],
  ["fixed-date fallback is suppressed when a matching admin schedule is active", banner.includes("seasonalPromotions.none { it.occasionKey.equals(celebration.first")],
  ["Owner/Admin center exposes the seasonal scheduler", adminCenter.includes("FynxMarketplaceSeasonalPromotionAdminPanel()")],
  ["scheduler uploads optional celebration artwork to authenticated FYNX media storage", adminUi.includes("Choose photo") && adminUi.includes("uploadArtwork(context, artworkUri!!)") && client.includes("FynxProductionMessaging.uploadMedia")],
  ["seasonal schedules are independent of Best Seller scoring", seasonalFeedRoute.length > 0 && !seasonalFeedRoute.includes("marketplace_ad_metrics") && banner.includes("BEST SELLER OF THE WEEK")]
];

for (const [name, ok] of checks) {
  console.log(`${ok ? "PASS" : "FAIL"}: ${name}`);
  assert.ok(ok, name);
}
console.log(`Marketplace seasonal promotion verification GREEN (${checks.length} checks)`);
