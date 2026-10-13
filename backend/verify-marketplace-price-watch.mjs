import fs from "node:fs";

const social = fs.readFileSync("socialRoutes.js", "utf8");
const schema = fs.readFileSync("marketplaceSchema.js", "utf8");
const push = fs.readFileSync("notificationPush.js", "utf8");

// Marketplace table creation belongs to the shared schema helper so discovery
// and seller routes initialize the same schema. Keep legacy detection to make
// the verifier compatible with branches that have not extracted the helper yet.
const marketplaceSchema = schema.includes("CREATE TABLE IF NOT EXISTS marketplace_price_watches")
  ? schema
  : social;

const checks = [
  ["price-watch table is account/listing scoped", marketplaceSchema.includes("CREATE TABLE IF NOT EXISTS marketplace_price_watches") && marketplaceSchema.includes("PRIMARY KEY (user_id, listing_id)")],
  ["price-watch state is authenticated", social.includes('app.get("/api/marketplace/listings/:id/price-watch", auth')],
  ["price-watch create is authenticated", social.includes('app.post("/api/marketplace/listings/:id/price-watch", auth')],
  ["price-watch delete is authenticated", social.includes('app.delete("/api/marketplace/listings/:id/price-watch", auth')],
  ["seller listing updates are authenticated", social.includes('app.post("/api/marketplace/listings/:id/update", auth')],
  ["seller ownership is enforced before price changes", social.includes("WHERE id = $1 AND seller_id = $2 FOR UPDATE")],
  ["price change is detected server-side", social.includes("const priceChanged = oldPrice !== Number(updated.price)")],
  ["watchers receive the existing FYNX notification service", social.includes("queueFynxNotification(pool") && social.includes('type: "MARKETPLACE_PRICE"')],
  ["Marketplace notification preference is respected", push.includes('case "MARKETPLACE_PRICE": return "marketplace_enabled"')],
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log((ok ? "PASS" : "FAIL") + ": " + name);
if (failed.length) {
  console.error("Marketplace Price Watch verification failed: " + failed.join(", "));
  process.exit(1);
}
console.log("Marketplace Price Watch verification passed: " + checks.length + "/" + checks.length);
