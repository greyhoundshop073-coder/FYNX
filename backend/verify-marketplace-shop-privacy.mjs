import assert from "node:assert/strict";
import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";

const routes = new Map();
const app = {
  get(path, ...handlers) { routes.set(`GET ${path}`, handlers.at(-1)); },
  patch(path, ...handlers) { routes.set(`PATCH ${path}`, handlers.at(-1)); }
};
const sellerRow = {
  username: "seller",
  display_name: "Seller",
  shop_name: "Seller Shop",
  description: "Quality goods",
  location: "Port Harcourt",
  contact_phone: "08012345678",
  updated_at: "2026-10-31T00:00:00.000Z"
};
let patchParameters = null;
const pool = {
  async query(sql, parameters = []) {
    if (sql.includes("CREATE TABLE IF NOT EXISTS marketplace_shop_profiles")) {
      return { rows: [] };
    }
    if (sql.includes("INSERT INTO marketplace_shop_profiles")) {
      patchParameters = parameters;
      return { rows: [{
        ...sellerRow,
        description: "Updated details"
      }] };
    }
    if (sql.includes("SELECT u.username, u.display_name")) {
      return { rows: [sellerRow] };
    }
    if (sql.includes("SELECT username, display_name FROM users")) {
      return { rows: [{ username: "seller", display_name: "Seller" }] };
    }
    throw new Error(`Unexpected shop-profile query: ${sql.slice(0, 100)}`);
  }
};
registerMarketplaceShopProfileRoutes({
  app,
  pool,
  auth: (_req, _res, next) => next()
});

function response() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; }
  };
}

const privateResponse = response();
await routes.get("GET /api/marketplace/my-shop")(
  { user: { sub: "42" } },
  privateResponse
);
assert.equal(privateResponse.statusCode, 200);
assert.equal(privateResponse.body.shop.contactPhone, sellerRow.contact_phone,
  "authenticated My Shop must return the seller's saved contact phone");

const publicResponse = response();
await routes.get("GET /api/marketplace/shops/:username")(
  { params: { username: "seller" }, user: { sub: "99" } },
  publicResponse
);
assert.equal(publicResponse.statusCode, 200);
assert.equal(Object.hasOwn(publicResponse.body.shop, "contactPhone"), false,
  "public seller profile must omit contact phone");
assert.equal(JSON.stringify(publicResponse.body).includes(sellerRow.contact_phone), false,
  "public seller profile response must not contain the saved phone number");

const patchResponse = response();
await routes.get("PATCH /api/marketplace/my-shop")(
  { user: { sub: "42" }, body: { description: "Updated details" } },
  patchResponse
);
assert.equal(patchResponse.statusCode, 200);
assert.deepEqual(patchParameters, ["42", null, "Updated details", null, null],
  "omitted My Shop fields must remain NULL for SQL COALESCE preservation");
assert.equal(patchResponse.body.shop.contactPhone, sellerRow.contact_phone,
  "private partial update response must preserve contact phone");

const emptyPatchResponse = response();
await routes.get("PATCH /api/marketplace/my-shop")(
  { user: { sub: "42" }, body: {} },
  emptyPatchResponse
);
assert.equal(emptyPatchResponse.statusCode, 400,
  "empty My Shop patches must be rejected");

// A first-time seller must be able to create a shop by supplying a shop name.
const createRoutes = new Map();
const createApp = {
  get(route, ...handlers) { createRoutes.set(`GET ${route}`, handlers.at(-1)); },
  patch(route, ...handlers) { createRoutes.set(`PATCH ${route}`, handlers.at(-1)); }
};
let createParameters = null;
const createPool = {
  async query(sql, parameters = []) {
    if (sql.includes("CREATE TABLE IF NOT EXISTS marketplace_shop_profiles")) {
      return { rows: [] };
    }
    if (sql.includes("INSERT INTO marketplace_shop_profiles")) {
      createParameters = parameters;
      return { rows: [{
        shop_name: "First Shop",
        description: "",
        location: "",
        contact_phone: "",
        updated_at: "2026-10-31T00:00:00.000Z"
      }] };
    }
    if (sql.includes("SELECT username, display_name FROM users")) {
      return { rows: [{ username: "new-seller", display_name: "New Seller" }] };
    }
    throw new Error(`Unexpected first-shop creation query: ${sql.slice(0, 100)}`);
  }
};
registerMarketplaceShopProfileRoutes({
  app: createApp,
  pool: createPool,
  auth: (_req, _res, next) => next()
});
const createdShopResponse = response();
await createRoutes.get("PATCH /api/marketplace/my-shop")(
  { user: { sub: "77" }, body: { shopName: "First Shop" } },
  createdShopResponse
);
assert.equal(createdShopResponse.statusCode, 200,
  "a first-time seller must be able to create My Shop with a shop name");
assert.deepEqual(createParameters, ["77", "First Shop", null, null, null],
  "first-shop creation must leave optional fields at their defaults");
assert.equal(createdShopResponse.body.shop.shopName, "First Shop",
  "first-shop creation must return the saved shop identity");
assert.equal(createdShopResponse.body.shop.contactPhone, "",
  "new shop creation must not invent or expose a contact phone");

console.log("Marketplace My Shop public/private phone privacy passed.");
