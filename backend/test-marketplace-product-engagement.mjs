import assert from 'node:assert/strict';
import { registerSocialRoutes } from './socialRoutes.js';
import { registerMarketplaceTransactionRoutes } from './marketplaceTransactions.js';

function makeApp() {
  const routes = new Map();
  const app = {};
  for (const method of ['get', 'post', 'patch', 'delete']) {
    app[method] = (path, ...handlers) => {
      routes.set(method.toUpperCase() + ' ' + path, {
        middleware: handlers.slice(0, -1),
        handler: handlers.at(-1)
      });
    };
  }
  return { app, routes };
}

async function invoke(route, request) {
  assert.ok(route, 'expected route to be registered');
  const response = {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; }
  };
  await route.handler(request, response);
  return response;
}

const authenticated = (_req, _res, next) => next();
const likes = new Set();
let listingExists = true;
let sellerBlocked = false;
const likeQueries = [];
const socialPool = {
  async query(sql, params = []) {
    likeQueries.push({ sql: String(sql), params });
    const text = String(sql);
    if (text.includes('CREATE TABLE IF NOT EXISTS marketplace_listings')) {
      return { rows: [], rowCount: 0 };
    }
    if (text.includes('COUNT(DISTINCT likes.user_id)')) {
      const currentUser = String(params[1]);
      return {
        rows: [{
          id: 501,
          like_count: likes.size,
          liked_by_current_user: likes.has(currentUser)
        }],
        rowCount: 1
      };
    }
    if (text.includes('SELECT id, seller_id FROM marketplace_listings')) {
      return listingExists
        ? { rows: [{ id: 501, seller_id: 200 }], rowCount: 1 }
        : { rows: [], rowCount: 0 };
    }
    if (text.includes('SELECT 1 FROM blocks')) {
      return sellerBlocked
        ? { rows: [{ blocked: 1 }], rowCount: 1 }
        : { rows: [], rowCount: 0 };
    }
    if (text.includes('INSERT INTO marketplace_listing_likes')) {
      likes.add(String(params[0]));
      return { rows: [], rowCount: 1 };
    }
    if (text.includes('DELETE FROM marketplace_listing_likes')) {
      const before = likes.size;
      likes.delete(String(params[0]));
      return { rows: [], rowCount: before - likes.size };
    }
    if (text.includes('SELECT COUNT(*)::INTEGER AS like_count')) {
      return {
        rows: [{
          like_count: likes.size,
          liked_by_current_user: likes.has(String(params[1]))
        }],
        rowCount: 1
      };
    }
    throw new Error('Unexpected social query in test: ' + text.slice(0, 180));
  }
};

const social = makeApp();
registerSocialRoutes({
  app: social.app,
  pool: socialPool,
  auth: authenticated,
  findUserByUsername: async () => null
});

const engagementRoute = social.routes.get('GET /api/marketplace/shops/:username/listing-engagement');
const likeRoute = social.routes.get('POST /api/marketplace/listings/:id/like');
assert.ok(engagementRoute?.middleware.includes(authenticated), 'engagement read must require authentication');
assert.ok(likeRoute?.middleware.includes(authenticated), 'like mutation must require authentication');

let response = await invoke(engagementRoute, {
  params: { username: 'seller' }, query: {}, user: { sub: '201' }
});
assert.equal(response.statusCode, 200);
assert.deepEqual(response.body.listings, [{
  listingId: '501', likeCount: 0, likedByCurrentUser: false
}]);

response = await invoke(likeRoute, {
  params: { id: '501' }, body: { liked: true }, user: { sub: '201' }
});
assert.equal(response.statusCode, 200);
assert.deepEqual(response.body, { listingId: '501', liked: true, likeCount: 1 });

response = await invoke(likeRoute, {
  params: { id: '501' }, body: { liked: true }, user: { sub: '201' }
});
assert.equal(response.statusCode, 200);
assert.deepEqual(response.body, { listingId: '501', liked: true, likeCount: 1 });
assert.equal(likes.size, 1, 'repeated likes must not create duplicate rows');

response = await invoke(engagementRoute, {
  params: { username: 'seller' }, query: {}, user: { sub: '201' }
});
assert.deepEqual(response.body.listings, [{
  listingId: '501', likeCount: 1, likedByCurrentUser: true
}]);

response = await invoke(likeRoute, {
  params: { id: '501' }, body: { liked: false }, user: { sub: '201' }
});
assert.equal(response.statusCode, 200);
assert.deepEqual(response.body, { listingId: '501', liked: false, likeCount: 0 });
assert.equal(likes.size, 0, 'unlike must remove the account/product relation');

response = await invoke(likeRoute, {
  params: { id: '501' }, body: { liked: true }, user: { sub: '200' }
});
assert.equal(response.statusCode, 400, 'seller must not like their own product');
assert.equal(likes.size, 0);

sellerBlocked = true;
response = await invoke(likeRoute, {
  params: { id: '501' }, body: { liked: true }, user: { sub: '201' }
});
assert.equal(response.statusCode, 404, 'blocked seller products must remain unavailable');
assert.equal(likes.size, 0);
sellerBlocked = false;

listingExists = false;
response = await invoke(likeRoute, {
  params: { id: '501' }, body: { liked: true }, user: { sub: '201' }
});
assert.equal(response.statusCode, 404, 'inactive or out-of-stock products must not be liked');
assert.equal(likes.size, 0);

const purchaseCalls = [];
const purchasePool = {
  async query(sql, params = []) {
    const text = String(sql);
    purchaseCalls.push({ sql: text, params });
    if (text.includes('ALTER TABLE marketplace_listings ADD COLUMN IF NOT EXISTS reserved_quantity')) {
      return { rows: [], rowCount: 0 };
    }
    if (text.includes('SELECT listing_id::TEXT AS listing_id')) {
      return {
        rows: [{ listing_id: '501', order_count: 1, units_purchased: 2 }],
        rowCount: 1
      };
    }
    throw new Error('Unexpected transaction query in test: ' + text.slice(0, 180));
  }
};

const transactions = makeApp();
registerMarketplaceTransactionRoutes({ app: transactions.app, pool: purchasePool, auth: authenticated });
const purchaseRoute = transactions.routes.get('GET /api/marketplace/my-purchased-listings');
assert.ok(purchaseRoute?.middleware.includes(authenticated), 'purchase history must require authentication');
response = await invoke(purchaseRoute, { params: {}, query: {}, user: { sub: '123' } });
assert.equal(response.statusCode, 200);
assert.deepEqual(response.body.purchases, [{
  listingId: '501', orderCount: 1, unitsPurchased: 2
}]);
const purchaseQuery = purchaseCalls.find(call => call.sql.includes('SELECT listing_id::TEXT AS listing_id'));
assert.deepEqual(purchaseQuery?.params, ['123'], 'purchase history must be scoped to the signed-in buyer');
assert.ok(purchaseQuery?.sql.includes("status IN ('PAID', 'SHIPPED', 'DELIVERED', 'INSPECTION', 'COMPLETED', 'DISPUTED')"));
assert.doesNotMatch(purchaseQuery?.sql || '', /PAYMENT_PENDING|CANCELLED|REFUNDED/);

console.log('Marketplace product engagement route tests GREEN (18 assertions)');
