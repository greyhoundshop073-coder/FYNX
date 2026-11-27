import assert from "node:assert/strict";
import { registerSocialRoutes, parseMarketplaceListingId } from "./socialRoutes.js";

assert.equal(parseMarketplaceListingId("1"), "1");
assert.equal(parseMarketplaceListingId("9223372036854775807"), "9223372036854775807");
assert.equal(parseMarketplaceListingId("9223372036854775808"), null);
assert.equal(parseMarketplaceListingId("01"), null);
assert.equal(parseMarketplaceListingId("1.0"), null);
assert.equal(parseMarketplaceListingId("-1"), null);
assert.equal(parseMarketplaceListingId(Number.MAX_SAFE_INTEGER + 1), null);

const routes = new Map();
const app = new Proxy({}, {
  get(_target, method) {
    return (path, ...handlers) => {
      if (typeof path === "string" && handlers.length) routes.set(`${String(method).toUpperCase()} ${path}`, handlers.at(-1));
    };
  }
});
let blocked = true;
let listingAvailable = true;
let lastSavedId = null;
let lastReactivationId = null;
let lastSql = "";
let lastParams = [];
const pool = {
  async query(sql, params = []) {
    lastSql = sql;
    lastParams = params;
    if (sql.includes("CREATE TABLE IF NOT EXISTS marketplace_listings")) return { rows: [] };
    if (sql.includes("CREATE TABLE IF NOT EXISTS marketplace_shop_profiles") || sql.includes("ALTER TABLE marketplace_shop_profiles")) return { rows: [] };
    if (sql.includes("SELECT EXISTS (") && sql.includes("marketplace_saved_products")) return { rows: [{ saved: !blocked }] };
    if (sql.includes("FROM marketplace_listings l") && sql.includes("GREATEST(0, l.quantity - COALESCE(l.reserved_quantity")) {
      return { rows: listingAvailable && !blocked ? [{ id: params[0] }] : [] };
    }
    if (sql.includes("INSERT INTO marketplace_saved_products")) { lastSavedId = params[1]; return { rows: [] }; }
    if (sql.includes("DELETE FROM marketplace_saved_products")) return { rows: [] };
    if (sql.includes("FROM marketplace_saved_products sp JOIN marketplace_listings")) return { rows: [] };
    throw new Error("Unexpected saved-products query: " + sql.slice(0, 160));
  },
  async connect() {
    return {
      async query(sql, params = []) {
        lastSql = sql;
        lastParams = params;
        if (sql === "BEGIN" || sql === "COMMIT" || sql === "ROLLBACK") return { rows: [] };
        if (sql.includes("SELECT id, seller_id, quantity, reserved_quantity")) {
          lastReactivationId = params[0];
          return { rows: [{ id: params[0], seller_id: "9", quantity: 3, reserved_quantity: 1 }] };
        }
        if (sql.includes("UPDATE marketplace_listings SET active = TRUE")) {
          lastReactivationId = params[0];
          return { rows: [{ id: params[0], active: true }] };
        }
        throw new Error("Unexpected reactivation query: " + sql.slice(0, 160));
      },
      release() {}
    };
  }
};
registerSocialRoutes({ app, pool, auth: (_req, _res, next) => next(), findUserByUsername: async () => null });

function response() {
  return {
    statusCode: 200, body: null,
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    set() { return this; },
    send(value) { this.body = value; return this; },
    redirect() { return this; }
  };
}
async function invoke(method, path, params = {}, user = "7") {
  const handler = routes.get(`${method} ${path}`);
  assert.equal(typeof handler, "function", `missing route ${method} ${path}`);
  const res = response();
  await handler({ params, body: {}, query: {}, user: { sub: user } }, res);
  return res;
}

const invalidState = await invoke("GET", "/api/marketplace/listings/:id/saved", { id: "9223372036854775808" });
assert.equal(invalidState.statusCode, 400, "out-of-range PostgreSQL BIGINT IDs are rejected before SQL");

blocked = true;
const hiddenState = await invoke("GET", "/api/marketplace/listings/:id/saved", { id: "9223372036854775807" });
assert.equal(hiddenState.body.saved, false, "blocked sellers do not leak saved state");
assert.equal(lastParams[1], "9223372036854775807", "BIGINT ID remains an exact decimal string");
assert.match(lastSql, /NOT EXISTS \([\s\S]*FROM blocks/);

const blockedSave = await invoke("POST", "/api/marketplace/listings/:id/saved", { id: "9223372036854775807" });
assert.equal(blockedSave.statusCode, 404, "blocked seller listings cannot be newly saved");
assert.match(lastSql, /GREATEST\(0, l\.quantity - COALESCE\(l\.reserved_quantity, 0\)\) > 0/);
assert.match(lastSql, /NOT EXISTS \([\s\S]*FROM blocks/);

blocked = false;
listingAvailable = false;
const soldOutSave = await invoke("POST", "/api/marketplace/listings/:id/saved", { id: "9223372036854775807" });
assert.equal(soldOutSave.statusCode, 404, "fully reserved or sold-out listings cannot be newly saved");
listingAvailable = true;
const saved = await invoke("POST", "/api/marketplace/listings/:id/saved", { id: "9223372036854775807" });
assert.equal(saved.statusCode, 201);
assert.equal(lastSavedId, "9223372036854775807", "save writes exact BIGINT ID without Number coercion");

const removed = await invoke("DELETE", "/api/marketplace/listings/:id/saved", { id: "9223372036854775807" });
assert.equal(removed.body.saved, false);
assert.equal(lastParams[1], "9223372036854775807", "remove writes exact BIGINT ID");

const invalidReactivate = await invoke("POST", "/api/marketplace/listings/:id/reactivate", { id: "9223372036854775808" });
assert.equal(invalidReactivate.statusCode, 400, "reactivation rejects out-of-range IDs");
const reactivated = await invoke("POST", "/api/marketplace/listings/:id/reactivate", { id: "9223372036854775807" });
assert.equal(reactivated.body.ok, true);
assert.equal(lastReactivationId, "9223372036854775807", "reactivation preserves the exact BIGINT ID");
assert.equal(reactivated.body.availableQuantity, 2);

console.log("Marketplace saved-products tests passed: BIGINT-safe IDs, block-aware state/add, available-stock guard, removal and seller-owned reactivation.");
