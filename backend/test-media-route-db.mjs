import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import pg from "pg";

const { Client } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");
const source = await readFile(new URL("./server.js", import.meta.url), "utf8");
const start = source.indexOf('app.get("/api/media/:id"');
const marker = source.indexOf("let result = await pool.query(", start);
const tick = String.fromCharCode(96);
const sqlStart = source.indexOf(tick, marker) + 1;
const sqlEnd = source.indexOf(tick + ", [mediaId, req.user.sub]);", sqlStart);
assert.ok(start >= 0 && marker >= 0 && sqlEnd > sqlStart, "media authorization SQL extraction failed");
const sql = source.slice(sqlStart, sqlEnd);
const db = new Client();
await db.connect();
try {
  await db.query([
    "CREATE TEMP TABLE message_media (id BIGINT PRIMARY KEY, owner_id BIGINT NOT NULL, mime_type TEXT, data BYTEA)",
    "CREATE TEMP TABLE messages (media_id BIGINT, sender_id BIGINT, recipient_id BIGINT)",
    "CREATE TEMP TABLE marketplace_listings (id BIGINT PRIMARY KEY, seller_id BIGINT, active BOOLEAN, quantity INTEGER, currency TEXT, media_ids JSONB)",
    "CREATE TEMP TABLE blocks (blocker_id BIGINT, blocked_id BIGINT)",
    "CREATE TEMP TABLE users (id BIGINT PRIMARY KEY, profile_photo_media_id BIGINT)",
    "CREATE TEMP TABLE privacy_settings (user_id BIGINT, profile_visibility TEXT)",
    "CREATE TEMP TABLE friendships (user_id BIGINT, friend_id BIGINT, status TEXT)",
    "CREATE TEMP TABLE statuses (media_id BIGINT, expires_at TIMESTAMPTZ, owner_id BIGINT, audience TEXT)"
  ].join(";"));
  for (const row of [[100,1],[101,4],[106,1],[107,3],[108,2],[109,1],[110,1],[111,4],[112,5],[113,5],[114,99],[115,1],[116,1]]) {
    await db.query("INSERT INTO message_media(id,owner_id,mime_type) VALUES($1,$2,'image/jpeg')", row);
  }
  await db.query("INSERT INTO marketplace_listings(id,seller_id,active,quantity,currency,media_ids) VALUES (1,1,TRUE,3,'NGN','[\"100\",\"101\"]'), (2,1,FALSE,3,'NGN','[\"106\"]'), (3,3,TRUE,3,'USD','[\"107\"]'), (4,1,TRUE,0,'NGN','[\"115\"]'), (5,1,TRUE,2,'EUR','[\"116\"]')");
  await db.query("INSERT INTO blocks VALUES(2,3)");
  await db.query("INSERT INTO messages VALUES(109,1,2)");
  await db.query("INSERT INTO users VALUES(1,110),(2,NULL),(3,NULL),(4,111),(5,NULL)");
  await db.query("INSERT INTO privacy_settings VALUES(1,'Everyone'),(4,'My friends')");
  await db.query("INSERT INTO statuses VALUES(112,NOW()+INTERVAL '1 hour',5,'EVERYONE'),(113,NOW()+INTERVAL '1 hour',5,'FRIENDS')");
  const allowed = async (id, viewer) => (await db.query(sql,[id,viewer])).rows.length > 0;
  const cases = [
    [100,2,true,"seller-owned active listing"],
    [101,2,false,"foreign-owned image referenced by listing"],
    [106,2,false,"inactive listing"],
    [107,2,false,"blocked seller"],
    [108,2,true,"owner access"],
    [109,2,true,"message participant access"],
    [110,2,true,"public profile photo"],
    [111,2,false,"private profile photo"],
    [112,2,true,"public status"],
    [113,2,false,"friends-only status without friendship"],
    [114,2,false,"unrelated private media"],
    [115,2,false,"out-of-stock listing"],
    [116,2,false,"unsupported listing currency"]
  ];
  for (const [id,viewer,expected,name] of cases) assert.equal(await allowed(id,viewer),expected,name);
  console.log("PostgreSQL media authorization integration tests passed (11 cases).");
} finally {
  await db.end();
}
