import assert from "node:assert/strict";
import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";

function createHarness(initial = {}) {
  const saved = {
    shop_name: "",
    description: "",
    location: "",
    contact_phone: "",
    logo_media_id: null,
    cover_media_id: null,
    updated_at: "2026-11-23T12:00:00.000Z",
    ...initial
  };
  const routes = new Map();
  let lastPatchParameters = null;
  const app = {
    get(path, ...handlers) { routes.set(`GET ${path}`, handlers.at(-1)); },
    patch(path, ...handlers) { routes.set(`PATCH ${path}`, handlers.at(-1)); }
  };
  const pool = {
    async query(sql, parameters = []) {
      if (sql.includes("CREATE TABLE IF NOT EXISTS marketplace_shop_profiles") ||
          sql.includes("ALTER TABLE marketplace_shop_profiles")) {
        return { rows: [] };
      }
      if (sql.includes("SELECT id, mime_type FROM message_media")) {
        return { rows: parameters[0].map((id) => ({ id, mime_type: "image/jpeg" })) };
      }
      if (sql.includes("INSERT INTO marketplace_shop_profiles")) {
        lastPatchParameters = parameters;
        const [sellerId, shopName, description, location, contactPhone, logoId, coverId, hasLogo, hasCover] = parameters;
        if (shopName !== null) saved.shop_name = shopName;
        else if (!saved.shop_name) return { rows: [] };
        if (description !== null) saved.description = description;
        if (location !== null) saved.location = location;
        if (contactPhone !== null) saved.contact_phone = contactPhone;
        if (hasLogo) saved.logo_media_id = logoId;
        if (hasCover) saved.cover_media_id = coverId;
        return { rows: [{ ...saved }] };
      }
      if (sql.includes("SELECT u.username, u.display_name")) {
        return { rows: [{ username: "seller", display_name: "Seller", ...saved }] };
      }
      if (sql.includes("SELECT username, display_name FROM users")) {
        return { rows: [{ username: "seller", display_name: "Seller" }] };
      }
      throw new Error(`Unexpected shop-profile query: ${sql.slice(0, 120)}`);
    }
  };
  registerMarketplaceShopProfileRoutes({ app, pool, auth: (_req, _res, next) => next() });
  return { routes, saved, get lastPatchParameters() { return lastPatchParameters; } };
}

function response() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; }
  };
}

const harness = createHarness({
  shop_name: "Seller Shop",
  description: "Quality goods",
  location: "Port Harcourt",
  contact_phone: "08012345678",
  logo_media_id: "41",
  cover_media_id: "42"
});

const privateRead = response();
await harness.routes.get("GET /api/marketplace/my-shop")(
  { user: { sub: "42" } }, privateRead
);
assert.equal(privateRead.statusCode, 200);
assert.equal(privateRead.body.shop.contactPhone, "08012345678",
  "authenticated My Shop must return the seller's own saved contact phone");

const publicRead = response();
await harness.routes.get("GET /api/marketplace/shops/:username")(
  { params: { username: "seller" }, user: { sub: "99" } }, publicRead
);
assert.equal(publicRead.statusCode, 200);
assert.equal(Object.hasOwn(publicRead.body.shop, "contactPhone"), false,
  "public storefront must omit the seller's private contact phone");
assert.equal(JSON.stringify(publicRead.body).includes("08012345678"), false,
  "public storefront response must not contain the private phone number");
assert.equal(publicRead.body.shop.logoMediaId, "41");
assert.equal(publicRead.body.shop.coverMediaId, "42");

const partialPatch = response();
await harness.routes.get("PATCH /api/marketplace/my-shop")(
  { user: { sub: "42" }, body: { description: "Updated shop details" } }, partialPatch
);
assert.equal(partialPatch.statusCode, 200, JSON.stringify(partialPatch.body));
assert.deepEqual(harness.lastPatchParameters, [
  "42", null, "Updated shop details", null, null, null, null, false, false
], "an omitted field must be represented as NULL and preserve its saved value");
assert.equal(harness.saved.shop_name, "Seller Shop");
assert.equal(harness.saved.location, "Port Harcourt");
assert.equal(harness.saved.contact_phone, "08012345678");
assert.equal(harness.saved.logo_media_id, "41");
assert.equal(harness.saved.cover_media_id, "42");
assert.equal(partialPatch.body.shop.contactPhone, "08012345678");

const removeLogo = response();
await harness.routes.get("PATCH /api/marketplace/my-shop")(
  { user: { sub: "42" }, body: { logoMediaId: null } }, removeLogo
);
assert.equal(removeLogo.statusCode, 200, JSON.stringify(removeLogo.body));
assert.equal(harness.saved.logo_media_id, null, "explicit null removes only the shop logo");
assert.equal(harness.saved.cover_media_id, "42", "omitted cover remains unchanged");
assert.equal(harness.saved.contact_phone, "08012345678");

const emptyPatch = response();
await harness.routes.get("PATCH /api/marketplace/my-shop")(
  { user: { sub: "42" }, body: {} }, emptyPatch
);
assert.equal(emptyPatch.statusCode, 400, "empty patches must be rejected");

const firstSeller = createHarness();
const createShop = response();
await firstSeller.routes.get("PATCH /api/marketplace/my-shop")(
  { user: { sub: "77" }, body: { shopName: "First Shop" } }, createShop
);
assert.equal(createShop.statusCode, 200, JSON.stringify(createShop.body));
assert.equal(createShop.body.shop.shopName, "First Shop");
assert.equal(createShop.body.shop.contactPhone, "");

console.log("Marketplace My Shop privacy and partial-update tests passed.");
