import assert from "node:assert/strict";
import pg from "pg";
import { registerTrustSafetyRoutes } from "./trustSafety.js";

const { Client } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");
const db = new Client();
await db.connect();

const routes = new Map();
const app = {
  post(path, ...handlers) { routes.set(`POST ${path}`, handlers.at(-1)); },
  get(path, ...handlers) { routes.set(`GET ${path}`, handlers.at(-1)); }
};
registerTrustSafetyRoutes({ app, pool: db, auth: (_req, _res, next) => next?.() });

function response() {
  return {
    statusCode: 200,
    body: undefined,
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; }
  };
}
async function call(method, path, userId, body = {}) {
  const handler = routes.get(`${method} ${path}`);
  assert.ok(handler, `route registered: ${method} ${path}`);
  const res = response();
  await handler({ user: { sub: String(userId) }, body, query: {} }, res);
  return res;
}

try {
  await db.query(`
    CREATE TEMP TABLE users (id BIGINT PRIMARY KEY, username TEXT);
    CREATE TEMP TABLE social_posts (id BIGINT PRIMARY KEY, author_id BIGINT NOT NULL, visibility TEXT NOT NULL);
    CREATE TEMP TABLE social_post_audience (post_id BIGINT NOT NULL, user_id BIGINT NOT NULL);
    CREATE TEMP TABLE friendships (user_id BIGINT NOT NULL, friend_id BIGINT NOT NULL, status TEXT NOT NULL);
    CREATE TEMP TABLE social_post_comments (id BIGINT PRIMARY KEY, post_id BIGINT NOT NULL, author_id BIGINT NOT NULL);
    CREATE TEMP TABLE marketplace_listings (id BIGINT PRIMARY KEY, seller_id BIGINT NOT NULL, active BOOLEAN NOT NULL);
    INSERT INTO users(id,username) VALUES (1,'owner'),(2,'reporter'),(3,'outsider'),(4,'admin');
    INSERT INTO social_posts(id,author_id,visibility) VALUES
      (10,1,'PUBLIC'),(11,1,'PRIVATE'),(12,1,'SELECTED_PEOPLE');
    INSERT INTO social_post_audience(post_id,user_id) VALUES (12,2);
    INSERT INTO social_post_comments(id,post_id,author_id) VALUES (20,11,1),(21,12,1);
    INSERT INTO marketplace_listings(id,seller_id,active) VALUES (30,1,TRUE),(31,1,FALSE);
  `);

  const created = await call("POST", "/api/social/content-reports", 2,
    { targetType: "SOCIAL_POST", targetId: 10, reason: "Spam", details: "Repeated spam" });
  assert.equal(created.statusCode, 201, "public post report is accepted");
  const reportId = created.body.report.id;

  const duplicate = await call("POST", "/api/social/content-reports", 2,
    { targetType: "SOCIAL_POST", targetId: 10, reason: "Spam" });
  assert.equal(duplicate.statusCode, 409, "duplicate open report is rejected");

  const selfReport = await call("POST", "/api/social/content-reports", 1,
    { targetType: "SOCIAL_POST", targetId: 10, reason: "Issue" });
  assert.equal(selfReport.statusCode, 400, "content owner cannot report own post");

  const privatePost = await call("POST", "/api/social/content-reports", 3,
    { targetType: "SOCIAL_POST", targetId: 11, reason: "Issue" });
  assert.equal(privatePost.statusCode, 404, "outsider cannot report private post");

  const privateComment = await call("POST", "/api/social/content-reports", 3,
    { targetType: "SOCIAL_COMMENT", targetId: 20, reason: "Issue" });
  assert.equal(privateComment.statusCode, 404, "outsider cannot report comment on private post");

  const selectedWithoutFriendship = await call("POST", "/api/social/content-reports", 2,
    { targetType: "SOCIAL_COMMENT", targetId: 21, reason: "Issue" });
  assert.equal(selectedWithoutFriendship.statusCode, 404, "selected audience without accepted friendship is denied");

  await db.query("INSERT INTO friendships(user_id,friend_id,status) VALUES (1,2,'accepted')");
  const selectedWithFriendship = await call("POST", "/api/social/content-reports", 2,
    { targetType: "SOCIAL_COMMENT", targetId: 21, reason: "Abuse" });
  assert.equal(selectedWithFriendship.statusCode, 201, "selected audience with accepted friendship can report visible comment");

  const ownListing = await call("POST", "/api/social/content-reports", 1,
    { targetType: "MARKETPLACE_LISTING", targetId: 30, reason: "Issue" });
  assert.equal(ownListing.statusCode, 400, "seller cannot report own listing");
  const inactiveListing = await call("POST", "/api/social/content-reports", 2,
    { targetType: "MARKETPLACE_LISTING", targetId: 31, reason: "Issue" });
  assert.equal(inactiveListing.statusCode, 404, "inactive listing cannot be reported");

  const history = await call("GET", "/api/social/content-reports/mine", 2);
  assert.equal(history.statusCode, 200);
  assert.ok(history.body.reports.some(report => report.id === reportId), "reporter history contains submitted report");

  await db.query(`INSERT INTO safety_content_moderation_audit
    (report_id,moderator_id,previous_status,new_status,decision_note)
    VALUES ($1,4,'OPEN','UNDER_REVIEW','Review started')`, [reportId]);
  const audit = await db.query("SELECT moderator_id,previous_status,new_status,decision_note FROM safety_content_moderation_audit WHERE report_id=$1", [reportId]);
  assert.equal(audit.rowCount, 1, "moderation audit row is persisted");
  assert.equal(audit.rows[0].moderator_id, "4");
  assert.equal(audit.rows[0].new_status, "UNDER_REVIEW");

  console.log("Content reporting PostgreSQL integration tests passed (10 route and persistence assertions).");
} finally {
  await db.end();
}
