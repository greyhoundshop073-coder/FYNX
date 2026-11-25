import assert from "node:assert/strict";
import express from "express";
import pg from "pg";
import { registerMarketplaceShopProfileRoutes } from "./marketplaceShopProfiles.js";
import { registerMarketplaceReputationRoutes } from "./marketplaceReputation.js";

const { Client, Pool } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");

const admin = new Client();
await admin.connect();
const schema = "fynx_shop_review_" + process.pid + "_" + Date.now();
await admin.query("CREATE SCHEMA " + schema);
const pool = new Pool({
  host: process.env.PGHOST,
  port: process.env.PGPORT ? Number(process.env.PGPORT) : undefined,
  user: process.env.PGUSER,
  password: process.env.PGPASSWORD,
  database: process.env.PGDATABASE,
  options: "-c search_path=" + schema,
  max: 2
});

const app = express();
app.use(express.json());
const auth = (req, _res, next) => {
  req.user = { sub: String(req.header("x-test-user") || "") };
  next();
};
registerMarketplaceShopProfileRoutes({ app, pool, auth });
registerMarketplaceReputationRoutes({ app, pool, auth });

let server;
try {
  await pool.query(`
    CREATE TABLE users (
      id BIGINT PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      display_name TEXT NOT NULL DEFAULT '',
      profile_photo_media_id BIGINT
    );
    CREATE TABLE message_media (
      id BIGINT PRIMARY KEY,
      owner_id BIGINT NOT NULL REFERENCES users(id),
      mime_type TEXT NOT NULL
    );
    CREATE TABLE blocks (blocker_id BIGINT NOT NULL, blocked_id BIGINT NOT NULL);
    CREATE TABLE marketplace_listings (
      id BIGINT PRIMARY KEY,
      seller_id BIGINT NOT NULL REFERENCES users(id),
      title TEXT NOT NULL,
      category TEXT NOT NULL DEFAULT '',
      active BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE TABLE marketplace_orders (
      id UUID PRIMARY KEY,
      buyer_id BIGINT NOT NULL REFERENCES users(id),
      seller_id BIGINT NOT NULL REFERENCES users(id),
      listing_id BIGINT,
      status TEXT NOT NULL,
      payment_reference TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      completed_at TIMESTAMPTZ
    );
    INSERT INTO users(id,username,display_name,profile_photo_media_id) VALUES
      (1,'sellerone','Seller One',901),
      (2,'buyerone','Buyer One',902),
      (3,'outsider','Outside Buyer',903);
    INSERT INTO message_media(id,owner_id,mime_type) VALUES
      (101,1,'image/png'),(102,1,'image/jpeg'),(103,2,'image/png'),(104,1,'video/mp4');
    INSERT INTO marketplace_listings(id,seller_id,title,category) VALUES (501,1,'Test listing','Electronics');
  `);

  server = app.listen(0);
  await new Promise((resolve, reject) => {
    server.once("listening", resolve);
    server.once("error", reject);
  });
  const base = "http://127.0.0.1:" + server.address().port;
  const request = (path, user, options = {}) => fetch(base + path, {
    ...options,
    headers: {
      "content-type": "application/json",
      "x-test-user": String(user),
      ...(options.headers || {})
    }
  });
  const json = (value) => JSON.stringify(value);

  let response = await request("/api/marketplace/my-shop", 1);
  assert.equal(response.status, 200, "seller can load an initially empty shop profile");
  assert.equal((await response.json()).shop.shopName, "", "empty shop profile has a stable response shape");

  response = await request("/api/marketplace/my-shop", 1, {
    method: "PATCH", body: json({ shopName: "X", description: "Too short name" })
  });
  assert.equal(response.status, 400, "shop name validation rejects too-short names");

  response = await request("/api/marketplace/my-shop", 1, {
    method: "PATCH",
    body: json({ shopName: "Seller Studio", description: "Independent shop description", logoMediaId: "103" })
  });
  assert.equal(response.status, 400, "a seller cannot attach another account's image");

  response = await request("/api/marketplace/my-shop", 1, {
    method: "PATCH",
    body: json({
      shopName: "Seller Studio",
      description: "Independent shop description",
      location: "Lagos",
      contactPhone: "+2348000000000",
      logoMediaId: "101",
      coverMediaId: "102"
    })
  });
  assert.equal(response.status, 200, "seller can save independently owned logo and cover images");
  const saved = (await response.json()).shop;
  assert.equal(saved.logoMediaId, "101");
  assert.equal(saved.coverMediaId, "102");
  assert.equal(saved.description, "Independent shop description");

  response = await request("/api/marketplace/my-shop", 1);
  const reloaded = (await response.json()).shop;
  assert.equal(reloaded.shopName, "Seller Studio", "shop identity persists across a fresh read");
  assert.equal(reloaded.logoMediaId, "101", "shop logo persists independently");
  assert.equal(reloaded.coverMediaId, "102", "shop cover persists independently");
  const personal = await pool.query("SELECT profile_photo_media_id FROM users WHERE id=1");
  assert.equal(String(personal.rows[0].profile_photo_media_id), "901", "saving shop branding never overwrites the personal avatar");

  response = await request("/api/marketplace/shops/sellerone", 2);
  assert.equal(response.status, 200, "public shop profile is readable by an unblocked viewer");
  const publicShop = (await response.json()).shop;
  assert.equal(publicShop.shopName, "Seller Studio");
  assert.equal(Object.hasOwn(publicShop, "contactPhone"), false, "public storefront must never expose the seller's private contact phone");
  assert.equal(JSON.stringify(publicShop).includes("+2348000000000"), false, "public storefront response must not contain the private phone number");
  response = await request("/api/marketplace/my-shop", 1);
  const privateShop = (await response.json()).shop;
  assert.equal(privateShop.contactPhone, "+2348000000000", "the seller can still see their own private contact phone");
  response = await request("/api/marketplace/my-shop", 1, {
    method: "PATCH", body: json({ description: "Updated without replacing shop identity or branding" })
  });
  assert.equal(response.status, 200, "partial shop edits are accepted");
  const partialShop = (await response.json()).shop;
  assert.equal(partialShop.shopName, "Seller Studio", "omitted shop name is preserved");
  assert.equal(partialShop.location, "Lagos", "omitted location is preserved");
  assert.equal(partialShop.contactPhone, "+2348000000000", "omitted private phone is preserved");
  assert.equal(partialShop.logoMediaId, "101", "omitted logo is preserved");
  assert.equal(partialShop.coverMediaId, "102", "omitted cover is preserved");
  await pool.query("INSERT INTO blocks(blocker_id,blocked_id) VALUES (2,1)");
  response = await request("/api/marketplace/shops/sellerone", 2);
  assert.equal(response.status, 404, "public shop identity is hidden when either party blocks the other");
  await pool.query("DELETE FROM blocks");

  const completedOrder = "00000000-0000-4000-8000-000000000001";
  const pendingOrder = "00000000-0000-4000-8000-000000000002";
  const selfOrder = "00000000-0000-4000-8000-000000000003";
  const unverifiedCompletedOrder = "00000000-0000-4000-8000-000000000004";
  await pool.query(
    "INSERT INTO marketplace_orders(id,buyer_id,seller_id,listing_id,status,completed_at) VALUES ($1,2,1,501,'COMPLETED',NOW()),($2,2,1,501,'PROCESSING',NULL),($3,1,1,501,'COMPLETED',NOW()),($4,2,1,501,'COMPLETED',NULL)",
    [completedOrder, pendingOrder, selfOrder, unverifiedCompletedOrder]
  );

  response = await request("/api/marketplace/orders/" + selfOrder + "/review", 1, {
    method: "POST", body: json({ rating: 5, comment: "Self purchase" })
  });
  assert.equal(response.status, 409, "a seller cannot review their own purchase");
  response = await request("/api/marketplace/orders/" + selfOrder + "/product-review", 1, {
    method: "POST", body: json({ rating: 5, comment: "Self product review" })
  });
  assert.equal(response.status, 409, "a seller cannot review their own product purchase");
  response = await request("/api/marketplace/orders/" + unverifiedCompletedOrder + "/review", 2, {
    method: "POST", body: json({ rating: 5, comment: "Missing completion evidence" })
  });
  assert.equal(response.status, 409, "a COMPLETED flag without completed_at is not reviewable");
  response = await request("/api/marketplace/orders/" + unverifiedCompletedOrder + "/product-review", 2, {
    method: "POST", body: json({ rating: 5, comment: "Missing completion evidence" })
  });
  assert.equal(response.status, 409, "a product review requires a recorded completion timestamp");

  response = await request("/api/marketplace/orders/" + completedOrder + "/review", 3, {
    method: "POST", body: json({ rating: 5, comment: "Not the buyer" })
  });
  assert.equal(response.status, 403, "a non-buyer cannot submit a review");

  response = await request("/api/marketplace/orders/" + pendingOrder + "/review", 2, {
    method: "POST", body: json({ rating: 5, comment: "Too early" })
  });
  assert.equal(response.status, 409, "an incomplete order cannot be reviewed");

  response = await request("/api/marketplace/orders/" + completedOrder + "/review", 2, {
    method: "POST", body: json({ rating: 5, comment: "Verified purchase" })
  });
  assert.equal(response.status, 201, "the buyer can review a completed order");
  const created = (await response.json()).review;
  assert.equal(Number(created.rating), 5);

  response = await request("/api/marketplace/orders/" + completedOrder + "/review", 2, {
    method: "POST", body: json({ rating: 4, comment: "Duplicate" })
  });
  assert.equal(response.status, 409, "the same buyer cannot review the same order twice");

  response = await request("/api/marketplace/orders/" + completedOrder + "/product-review", 3, {
    method: "POST", body: json({ rating: 5, comment: "Not the buyer" })
  });
  assert.equal(response.status, 403, "a non-buyer cannot submit a product review");
  response = await request("/api/marketplace/orders/" + pendingOrder + "/product-review", 2, {
    method: "POST", body: json({ rating: 5, comment: "Too early" })
  });
  assert.equal(response.status, 409, "an incomplete order cannot receive a product review");
  response = await request("/api/marketplace/orders/" + completedOrder + "/product-review", 2, {
    method: "POST", body: json({ rating: 4, comment: "Verified product purchase" })
  });
  assert.equal(response.status, 201, "a buyer can review the purchased product after completion");
  const productReview = (await response.json()).review;
  assert.equal(productReview.listingId, "501");
  assert.equal(productReview.rating, 4);
  response = await request("/api/marketplace/orders/" + completedOrder + "/product-review", 2, {
    method: "POST", body: json({ rating: 5, comment: "Duplicate product review" })
  });
  assert.equal(response.status, 409, "a completed order cannot be reviewed for the product twice");
  response = await request("/api/marketplace/listings/501/reviews?limit=1&offset=0", 3);
  assert.equal(response.status, 200, "public product reviews can be loaded");
  const productPage = await response.json();
  assert.equal(productPage.reviewCount, 1);
  assert.equal(productPage.averageRating, 4);
  assert.equal(productPage.reviews[0].comment, "Verified product purchase");
  response = await request("/api/marketplace/sellers/sellerone/reviews?limit=1&offset=0", 3);
  assert.equal(response.status, 200, "public review list can be loaded");
  const reviewPage = await response.json();
  assert.equal(reviewPage.reviews.length, 1);
  assert.equal(reviewPage.reviews[0].comment, "Verified purchase");
  assert.equal(reviewPage.reviews[0].productTitle, "Test listing");
  assert.equal(reviewPage.limit, 1);
  assert.equal(reviewPage.offset, 0);

  response = await request("/api/marketplace/sellers/sellerone/reputation", 3);
  assert.equal(response.status, 200);
  const beforeRefundReputation = (await response.json()).reputation;
  assert.equal(beforeRefundReputation.totalOrders, 2, "seller reputation denominator excludes self-purchases");
  assert.equal(beforeRefundReputation.successfulSales, 1, "completed third-party orders count as sales");
  assert.equal(beforeRefundReputation.reviewCount, 1, "only completed third-party reviews affect reputation");

  await pool.query("UPDATE marketplace_orders SET status='REFUNDED' WHERE id=$1", [completedOrder]);
  response = await request("/api/marketplace/sellers/sellerone/reviews?limit=1&offset=0", 3);
  assert.equal(response.status, 200);
  assert.equal((await response.json()).reviews.length, 0, "reviews from subsequently refunded orders must not remain public");
  response = await request("/api/marketplace/listings/501/reviews?limit=1&offset=0", 3);
  assert.equal(response.status, 200);
  const refundedProductReviews = await response.json();
  assert.equal(refundedProductReviews.reviewCount, 0, "refunded purchases must not inflate product review counts");
  assert.equal(refundedProductReviews.averageRating, 0, "refunded purchases must not influence product ratings");
  assert.equal(refundedProductReviews.reviews.length, 0);
  response = await request("/api/marketplace/sellers/sellerone/reputation", 3);
  assert.equal(response.status, 200);
  const refundedReputation = (await response.json()).reputation;
  assert.equal(refundedReputation.successfulSales, 0, "refunded orders must not count as successful seller sales");
  assert.equal(refundedReputation.reviewCount, 0, "reviews tied to refunded orders must not count toward seller reputation");

  console.log("PostgreSQL shop profile and seller review integration tests GREEN (private contact, partial edits, media ownership, block privacy, self-review rejection, completed-order reputation, refund exclusion and duplicate review rejection).");
} finally {
  if (server) await new Promise((resolve) => server.close(resolve));
  await pool.end();
  await admin.query("DROP SCHEMA IF EXISTS " + schema + " CASCADE").catch(() => {});
  await admin.end();
}
