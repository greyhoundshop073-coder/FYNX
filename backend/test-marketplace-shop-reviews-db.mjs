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
    CREATE TABLE marketplace_listings (id BIGINT PRIMARY KEY, title TEXT NOT NULL);
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
    INSERT INTO marketplace_listings(id,title) VALUES (501,'Test listing');
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
  assert.equal((await response.json()).shop.shopName, "Seller Studio");
  await pool.query("INSERT INTO blocks(blocker_id,blocked_id) VALUES (2,1)");
  response = await request("/api/marketplace/shops/sellerone", 2);
  assert.equal(response.status, 404, "public shop identity is hidden when either party blocks the other");
  await pool.query("DELETE FROM blocks");

  const completedOrder = "00000000-0000-4000-8000-000000000001";
  const pendingOrder = "00000000-0000-4000-8000-000000000002";
  await pool.query(
    "INSERT INTO marketplace_orders(id,buyer_id,seller_id,listing_id,status,completed_at) VALUES ($1,2,1,501,'COMPLETED',NOW()),($2,2,1,501,'PROCESSING',NULL)",
    [completedOrder, pendingOrder]
  );

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

  response = await request("/api/marketplace/sellers/sellerone/reviews?limit=1&offset=0", 3);
  assert.equal(response.status, 200, "public review list can be loaded");
  const reviewPage = await response.json();
  assert.equal(reviewPage.reviews.length, 1);
  assert.equal(reviewPage.reviews[0].comment, "Verified purchase");
  assert.equal(reviewPage.reviews[0].productTitle, "Test listing");
  assert.equal(reviewPage.limit, 1);
  assert.equal(reviewPage.offset, 0);

  console.log("PostgreSQL shop profile and seller review integration tests GREEN (save/reload, media ownership, avatar isolation, public block privacy, completed-order buyer ownership and duplicate review rejection).");
} finally {
  if (server) await new Promise((resolve) => server.close(resolve));
  await pool.end();
  await admin.query("DROP SCHEMA IF EXISTS " + schema + " CASCADE").catch(() => {});
  await admin.end();
}
