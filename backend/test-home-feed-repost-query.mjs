import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { Client } from "pg";

const source = await readFile(new URL("./socialRoutes.js", import.meta.url), "utf8");
const routeStart = source.indexOf("app.get('/api/social/feed'");
assert.notEqual(routeStart, -1, "Home feed route exists");
const queryStart = source.indexOf("const r = await pool.query(`", routeStart);
const queryEndMarker = "`, rawCursor ? params : [req.user.sub, limit + 1, offset]);";
const queryEnd = source.indexOf(queryEndMarker, queryStart);
assert.ok(queryStart >= 0 && queryEnd > queryStart, "Home feed SQL query can be extracted");
let query = source.slice(queryStart + "const r = await pool.query(`".length, queryEnd);
assert.ok(query.includes("${pagination}"), "Home feed query retains cursor pagination placeholder");
assert.ok(query.includes("${modeFilter}"), "Home feed query retains the timeline-mode placeholder");
assert.ok(query.includes("${rawCursor ? '' : ' OFFSET $3'}"), "Home feed query retains offset pagination placeholder");
const followingFilter = "AND (p.author_id=$1 OR EXISTS (SELECT 1 FROM social_follows sfm WHERE sfm.follower_id=$1 AND sfm.followed_id=p.author_id) OR repost.user_id IS NOT NULL)";
const baseQuery = query.replace("${modeFilter}", "").replace("${pagination}", "AND TRUE").replace("${rawCursor ? '' : ' OFFSET $3'}", " OFFSET $3");
const followingQuery = query.replace("${modeFilter}", followingFilter).replace("${pagination}", "AND TRUE").replace("${rawCursor ? '' : ' OFFSET $3'}", " OFFSET $3");

const client = new Client({
  host: process.env.PGHOST || "localhost",
  port: Number(process.env.PGPORT || 5432),
  user: process.env.PGUSER || "fynx_test",
  password: process.env.PGPASSWORD || "fynx_test",
  database: process.env.PGDATABASE || "fynx_media_test"
});

await client.connect();
try {
  await client.query("BEGIN");
  await client.query(`
    CREATE TEMP TABLE users (
      id BIGINT PRIMARY KEY,
      username TEXT NOT NULL,
      display_name TEXT NOT NULL
    );
    CREATE TEMP TABLE social_posts (
      id BIGINT PRIMARY KEY,
      author_id BIGINT NOT NULL,
      text TEXT NOT NULL,
      visibility TEXT NOT NULL,
      media_id BIGINT,
      media_type TEXT,
      text_background TEXT,
      text_background_color BIGINT,
      text_foreground_color BIGINT,
      location TEXT,
      music_media_id BIGINT,
      music_title TEXT,
      music_artist TEXT,
      music_duration_ms BIGINT,
      feeling_activity_type TEXT,
      feeling_activity TEXT,
      quoted_post_id BIGINT,
      created_at TIMESTAMPTZ NOT NULL
    );
    CREATE TEMP TABLE social_post_likes (post_id BIGINT NOT NULL, user_id BIGINT NOT NULL);
    CREATE TEMP TABLE social_post_comments (post_id BIGINT NOT NULL);
    CREATE TEMP TABLE social_follows (follower_id BIGINT NOT NULL, followed_id BIGINT NOT NULL);
    CREATE TEMP TABLE friendships (user_id BIGINT NOT NULL, friend_id BIGINT NOT NULL, status TEXT NOT NULL);
    CREATE TEMP TABLE social_post_audience (post_id BIGINT NOT NULL, user_id BIGINT NOT NULL);
    CREATE TEMP TABLE blocks (blocker_id BIGINT NOT NULL, blocked_id BIGINT NOT NULL);
    CREATE TEMP TABLE social_post_reposts (
      post_id BIGINT NOT NULL,
      user_id BIGINT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL
    );
  `);

  await client.query(`
    INSERT INTO users(id,username,display_name) VALUES
      (1,'viewer','Feed Viewer'),
      (2,'followed_reposter','Followed Reposter'),
      (3,'original_author','Original Author'),
      (4,'unfollowed_reposter','Unfollowed Reposter'),
      (5,'blocked_reposter','Blocked Reposter'),
      (6,'private_author','Private Author');
    INSERT INTO social_posts(id,author_id,text,visibility,created_at) VALUES
      (100,1,'my own post','PUBLIC','2026-11-05T09:00:00Z'),
      (101,3,'followed repost sample','PUBLIC','2026-11-05T09:30:00Z'),
      (102,3,'unfollowed repost sample','PUBLIC','2026-11-05T10:10:00Z'),
      (103,6,'restricted original','FRIENDS_ONLY','2026-11-05T09:45:00Z'),
      (104,3,'blocked reposter sample','PUBLIC','2026-11-05T10:05:00Z'),
      (105,2,'followed account post','PUBLIC','2026-11-05T09:50:00Z'),
      (106,1,'my protected original','ONLY_ME','2026-11-05T09:40:00Z'),
      (107,1,'my protected quote','ONLY_ME','2026-11-05T09:42:00Z');
    UPDATE social_posts SET quoted_post_id=106 WHERE id=107;
    INSERT INTO social_follows(follower_id,followed_id) VALUES (1,2),(1,5);
    INSERT INTO blocks(blocker_id,blocked_id) VALUES (1,5);
    INSERT INTO social_post_reposts(post_id,user_id,created_at) VALUES
      (101,2,'2026-11-05T10:20:00Z'),
      (102,4,'2026-11-05T10:45:00Z'),
      (103,2,'2026-11-05T10:50:00Z'),
      (104,5,'2026-11-05T10:55:00Z');
  `);

  const result = await client.query(baseQuery, [1, 100, 0]);
  const rows = result.rows;
  const byId = new Map(rows.map(row => [String(row.id), row]));

  assert.ok(byId.has("100"), "viewer sees their own post");
  assert.ok(byId.has("107"), "viewer sees their own protected quote");
  assert.equal(String(byId.get("107").quoted_original_id), "106", "viewer sees the original protected post embedded in their own quote");
  assert.ok(byId.has("101"), "viewer sees a public post reposted by an account they follow");
  assert.equal(byId.get("101").reposted_by_username, "followed_reposter", "followed repost is attributed");
  assert.equal(new Date(Number(byId.get("101").timestamp)).toISOString(), "2026-11-05T10:20:00.000Z", "followed repost timestamp advances the timeline event");
  assert.ok(byId.has("102"), "public original remains in feed even without a followed repost");
  assert.equal(byId.get("102").reposted_by_username, null, "unfollowed repost is not attributed");
  assert.equal(new Date(Number(byId.get("102").timestamp)).toISOString(), "2026-11-05T10:10:00.000Z", "unfollowed repost cannot advance the viewer's timeline");
  assert.ok(!byId.has("103"), "reposting does not expose a restricted original to an unapproved viewer");
  assert.ok(byId.has("104"), "public original remains visible independently of a blocked reposter");
  assert.equal(byId.get("104").reposted_by_username, null, "blocked reposter is excluded from attribution");
  assert.equal(new Date(Number(byId.get("104").timestamp)).toISOString(), "2026-11-05T10:05:00.000Z", "blocked repost cannot advance the viewer's timeline");

  const ids = rows.map(row => String(row.id));
  assert.equal(new Set(ids).size, ids.length, "one timeline card is returned per original post");
  assert.equal(ids[0], "101", "followed repost event is ordered by its repost timestamp");
  const followingResult = await client.query(followingQuery, [1, 100, 0]);
  const followingIds = new Set(followingResult.rows.map(row => String(row.id)));
  assert.ok(followingIds.has("100"), "Following includes the viewer's own posts");
  assert.ok(followingIds.has("107"), "Following includes the viewer's own protected quote");
  assert.ok(followingIds.has("101"), "Following includes public originals reposted by followed accounts");
  assert.ok(followingIds.has("105"), "Following includes posts authored by followed accounts");
  assert.ok(!followingIds.has("102"), "Following excludes public posts from unfollowed accounts");
  assert.ok(!followingIds.has("103"), "Following still hides restricted originals that the viewer cannot see");
  assert.ok(!followingIds.has("104"), "Following excludes content only reposted by an account the viewer blocked");

  console.log("HOME FEED REPOST + FOLLOWING POSTGRES INTEGRATION: GREEN");
} finally {
  await client.query("ROLLBACK");
  await client.end();
}
