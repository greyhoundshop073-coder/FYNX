import assert from "node:assert/strict";
import pg from "pg";
import jwt from "jsonwebtoken";
import { randomUUID } from "node:crypto";

const { Client } = pg;
const host = process.env.PGHOST || "localhost";
const port = Number(process.env.PGPORT || 5432);
const user = process.env.PGUSER || "fynx_test";
const password = process.env.PGPASSWORD || "fynx_test";
const database = process.env.PGDATABASE || "fynx_media_test";
process.env.DATABASE_URL ||= "postgresql://" + encodeURIComponent(user) + ":" + encodeURIComponent(password) + "@" + host + ":" + port + "/" + database;
process.env.JWT_SECRET ||= "fynx-group-content-test-secret";

const { registerGroupContentRoutes } = await import("./groupContentRoutes.js");
const { getFynxDatabasePool } = await import("./databasePool.js");
const client = new Client({ host, port, user, password, database });
const suffix = randomUUID().replaceAll("-", "");
const base = BigInt(Date.now()) * 10000n;
const adminId = base + 1n;
const memberId = base + 2n;
const outsiderId = base + 3n;
const adminUsername = "content_owner_" + suffix.slice(0, 12);
const memberUsername = "content_member_" + suffix.slice(0, 12);
const outsiderUsername = "content_outsider_" + suffix.slice(0, 12);
const groupId = "content_" + suffix.slice(0, 24);
const routes = new Map();
const app = {
  get: (path, ...handlers) => routes.set("GET " + path, handlers),
  post: (path, ...handlers) => routes.set("POST " + path, handlers),
  patch: (path, ...handlers) => routes.set("PATCH " + path, handlers),
  delete: (path, ...handlers) => routes.set("DELETE " + path, handlers)
};
const token = id => jwt.sign({ sub: String(id) }, process.env.JWT_SECRET);

async function call(method, path, params, userId, body = {}) {
  const handlers = routes.get(method + " " + path);
  assert.ok(handlers, "registered " + method + " " + path);
  const req = { params, body, get: () => "Bearer " + token(userId) };
  const res = {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    set() { return this; }
  };
  await handlers[0](req, res, () => handlers[1](req, res));
  return res;
}

await client.connect();
try {
  await client.query(
    "CREATE TABLE IF NOT EXISTS users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL DEFAULT '', display_name TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', verified BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); " +
    "CREATE TABLE IF NOT EXISTS fynx_groups (id TEXT PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', visibility TEXT NOT NULL DEFAULT 'PRIVATE', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); " +
    "ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS send_messages BOOLEAN NOT NULL DEFAULT TRUE; " +
    "CREATE TABLE IF NOT EXISTS fynx_group_members (group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, role TEXT NOT NULL DEFAULT 'MEMBER', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (group_id, user_id)); " +
    "ALTER TABLE fynx_group_members ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(); " +
    "CREATE TABLE IF NOT EXISTS fynx_account_safety (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, message_safety BOOLEAN NOT NULL DEFAULT TRUE, account_status TEXT NOT NULL DEFAULT 'ACTIVE')"
  );

  await client.query(
    "INSERT INTO users(id,username,password_hash) VALUES($1,$2,''),($3,$4,''),($5,$6,'')",
    [adminId, adminUsername, memberId, memberUsername, outsiderId, outsiderUsername]
  );
  await client.query("INSERT INTO fynx_groups(id,owner_id,name) VALUES($1,$2,'Group content route fixture')", [groupId, adminId]);
  await client.query(
    "INSERT INTO fynx_group_members(group_id,user_id,role) VALUES($1,$2,'ADMIN'),($1,$3,'MEMBER')",
    [groupId, adminId, memberId]
  );

  registerGroupContentRoutes({ app });

  const pollPath = "/api/groups/:groupId/polls";
  const votePath = "/api/groups/:groupId/polls/:pollId/votes";
  const contentPath = "/api/groups/:groupId/content";
  const params = { groupId };

  const singleCreate = await call("POST", pollPath, params, adminId, {
    question: "Which delivery option should we use?",
    options: ["Pickup", "Courier", "Dispatch"],
    multipleChoice: false
  });
  assert.equal(singleCreate.statusCode, 201, "single-choice group poll creation must succeed");
  assert.ok(singleCreate.body?.id && singleCreate.body?.pollId, "poll creation must return stable post and poll IDs");
  const singleStored = (await client.query(
    "SELECT question,multiple_choice FROM fynx_group_polls WHERE id=$1 AND post_id=$2",
    [singleCreate.body.pollId, singleCreate.body.id]
  )).rows[0];
  assert.equal(singleStored?.question, "Which delivery option should we use?", "poll question must be stored, not dropped");
  assert.equal(singleStored?.multiple_choice, false, "single-choice setting must be stored");
  const singleOptions = (await client.query(
    "SELECT id,text,position FROM fynx_group_poll_options WHERE poll_id=$1 ORDER BY position",
    [singleCreate.body.pollId]
  )).rows;
  assert.deepEqual(singleOptions.map(row => row.text), ["Pickup", "Courier", "Dispatch"]);

  const firstVote = await call("POST", votePath, { ...params, pollId: singleCreate.body.pollId }, memberId, { optionId: singleOptions[0].id });
  assert.equal(firstVote.statusCode, 200, "a group member must be able to vote");
  const replacementVote = await call("POST", votePath, { ...params, pollId: singleCreate.body.pollId }, memberId, { optionId: singleOptions[1].id });
  assert.equal(replacementVote.statusCode, 200, "a member must be able to change a single-choice vote");
  const singleVotes = await client.query(
    "SELECT option_id FROM fynx_group_poll_votes WHERE poll_id=$1 AND user_id=$2",
    [singleCreate.body.pollId, memberId]
  );
  assert.equal(singleVotes.rowCount, 1, "single-choice polls must retain only the latest selection");
  assert.equal(singleVotes.rows[0].option_id, singleOptions[1].id, "the replacement vote must be authoritative");

  const multiCreate = await call("POST", pollPath, params, adminId, {
    question: "Which support channels should we provide?",
    options: ["Chat", "Email", "Phone"],
    multipleChoice: true
  });
  assert.equal(multiCreate.statusCode, 201, "multiple-choice group poll creation must succeed");
  const multiOptions = (await client.query(
    "SELECT id,text FROM fynx_group_poll_options WHERE poll_id=$1 ORDER BY position",
    [multiCreate.body.pollId]
  )).rows;
  for (const option of multiOptions.slice(0, 2)) {
    const vote = await call("POST", votePath, { ...params, pollId: multiCreate.body.pollId }, memberId, { optionId: option.id });
    assert.equal(vote.statusCode, 200, "multiple-choice poll must accept more than one selection");
  }
  const multiVotes = await client.query(
    "SELECT option_id FROM fynx_group_poll_votes WHERE poll_id=$1 AND user_id=$2",
    [multiCreate.body.pollId, memberId]
  );
  assert.equal(multiVotes.rowCount, 2, "multiple-choice polls must preserve both selected options");

  await client.query("UPDATE fynx_group_polls SET closed=TRUE WHERE id=$1", [multiCreate.body.pollId]);
  const closedVote = await call("POST", votePath, { ...params, pollId: multiCreate.body.pollId }, memberId, { optionId: multiOptions[2].id });
  assert.equal(closedVote.statusCode, 409, "closed polls must reject new votes");
  const outsiderVote = await call("POST", votePath, { ...params, pollId: singleCreate.body.pollId }, outsiderId, { optionId: singleOptions[0].id });
  assert.equal(outsiderVote.statusCode, 403, "non-members must not vote in a private group poll");

  const eventPath = "/api/groups/:groupId/events";
  const badEvent = await call("POST", eventPath, params, adminId, {
    title: "Community meetup",
    startsAt: "2030-06-01T12:00:00.000Z",
    endsAt: "2030-06-01T11:00:00.000Z",
    location: "Lagos"
  });
  assert.equal(badEvent.statusCode, 400, "event end time must be later than its start time");

  const eventCreate = await call("POST", eventPath, params, adminId, {
    title: "Community meetup",
    description: "Meet the group in person",
    startsAt: "2030-06-01T11:00:00.000Z",
    endsAt: "2030-06-01T12:00:00.000Z",
    location: "Lagos"
  });
  assert.equal(eventCreate.statusCode, 201, "valid group event creation must succeed");
  const rsvpPath = "/api/groups/:groupId/events/:eventId/rsvp";
  const rsvp = await call("POST", rsvpPath, { ...params, eventId: eventCreate.body.id }, memberId, { response: "GOING" });
  assert.equal(rsvp.statusCode, 200, "a group member must be able to RSVP");
  assert.equal(rsvp.body?.response, "GOING");

  const content = await call("GET", contentPath, params, memberId);
  assert.equal(content.statusCode, 200, "a member must be able to load group polls and events");
  const savedSinglePoll = content.body.posts.find(post => post.id === singleCreate.body.id)?.poll;
  assert.equal(savedSinglePoll?.question, "Which delivery option should we use?");
  assert.deepEqual(savedSinglePoll?.options.map(option => option.text), ["Pickup", "Courier", "Dispatch"]);
  assert.equal(savedSinglePoll?.options.find(option => option.id === singleOptions[1].id)?.selectedByMe, true);
  assert.equal(savedSinglePoll?.options.find(option => option.id === singleOptions[0].id)?.selectedByMe, false);
  const savedEvent = content.body.events.find(event => event.id === eventCreate.body.id);
  assert.equal(savedEvent?.goingCount, 1, "event content must show the persisted RSVP count");
  assert.equal(savedEvent?.myResponse, "GOING", "event content must show the current member's own response");

  const outsiderContent = await call("GET", contentPath, params, outsiderId);
  assert.equal(outsiderContent.statusCode, 403, "non-members must not read private group content");

  console.log("GROUP CONTENT POSTGRESQL ROUTE INTEGRATION: GREEN (poll creation and persistence, single/multiple voting, closed poll rejection, member privacy, event validation and RSVP)");
} finally {
  await client.query("DELETE FROM fynx_groups WHERE id=$1", [groupId]).catch(() => {});
  await client.query("DELETE FROM users WHERE id = ANY($1::bigint[])", [[adminId.toString(), memberId.toString(), outsiderId.toString()]]).catch(() => {});
  await client.end();
  await getFynxDatabasePool()?.end();
}
