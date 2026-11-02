import assert from "node:assert/strict";
import express from "express";
import { Pool } from "pg";
import { registerSupportRoutes } from "./supportRoutes.js";

const rootPool = new Pool({
  host: process.env.PGHOST || "127.0.0.1",
  port: Number(process.env.PGPORT || 5432),
  user: process.env.PGUSER || "fynx_test",
  password: process.env.PGPASSWORD || "fynx_test",
  database: process.env.PGDATABASE || "fynx_media_test"
});
const schema = `fynx_support_test_${process.pid}`;
let testPool;
let server;

const expectStatus = (response, status, label) => {
  assert.equal(response.status, status, `${label}: expected HTTP ${status}, got ${response.status}`);
};
const request = async (base, userId, method, path, body) => fetch(base + path, {
  method,
  headers: { "content-type": "application/json", "x-support-test-user": String(userId) },
  body: body == null ? undefined : JSON.stringify(body)
});

try {
  await rootPool.query(`CREATE SCHEMA ${schema}`);
  testPool = new Pool({
    host: process.env.PGHOST || "127.0.0.1",
    port: Number(process.env.PGPORT || 5432),
    user: process.env.PGUSER || "fynx_test",
    password: process.env.PGPASSWORD || "fynx_test",
    database: process.env.PGDATABASE || "fynx_media_test",
    options: `-c search_path=${schema},public`
  });
  await testPool.query(`
    CREATE TABLE users (
      id BIGSERIAL PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      display_name TEXT NOT NULL,
      verified BOOLEAN NOT NULL DEFAULT FALSE
    );
    CREATE TABLE message_media (
      id BIGSERIAL PRIMARY KEY,
      owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      mime_type TEXT NOT NULL,
      data BYTEA NOT NULL,
      byte_size INTEGER NOT NULL
    );
  `);
  await testPool.query("INSERT INTO users(id,username,display_name,verified) VALUES (1,'fynx','FYNX Owner',TRUE),(2,'admin-one','Support Admin One',FALSE),(3,'admin-two','Support Admin Two',FALSE),(4,'reporter','Test Reporter',FALSE),(5,'other-user','Other User',FALSE)");
  await testPool.query("SELECT setval(pg_get_serial_sequence('users','id'),5,true)");

  const app = express();
  app.use(express.json({ limit: "2mb" }));
  const auth = (req, res, next) => {
    const userId = req.header("x-support-test-user");
    if (!userId) return res.status(401).json({ error: "test identity required" });
    req.user = { sub: userId };
    next();
  };
  registerSupportRoutes({ app, pool: testPool, auth });
  server = await new Promise(resolve => {
    const instance = app.listen(0, "127.0.0.1", () => resolve(instance));
  });
  const base = `http://127.0.0.1:${server.address().port}`;

  const createBody = {
    category: "WALLET_PAYMENTS",
    subject: "Wallet transaction missing",
    description: "I completed a payment but the transaction is not showing in my Wallet.",
    clientRequestId: "support-create-once"
  };
  const createdResponse = await request(base, 4, "POST", "/api/support/cases", createBody);
  await expectStatus(createdResponse, 201, "create support case");
  const created = (await createdResponse.json()).case;
  assert.match(created.case_number, /^FYNX-\d{8}$/);
  assert.equal(created.description, createBody.description, "support case response must include the submitted problem description");

  const duplicateResponse = await request(base, 4, "POST", "/api/support/cases", createBody);
  await expectStatus(duplicateResponse, 200, "idempotent support case retry");
  const duplicate = (await duplicateResponse.json()).case;
  assert.equal(duplicate.id, created.id, "duplicate request must return the original case");

  await testPool.query("INSERT INTO fynx_admin_roles(user_id,role) VALUES (2,'ADMIN'),(3,'ADMIN')");

  const ownerInbox = await request(base, 1, "GET", "/api/admin/support/cases");
  await expectStatus(ownerInbox, 200, "Owner support inbox");
  assert.equal((await ownerInbox.json()).cases.length, 1);

  const adminInbox = await request(base, 2, "GET", "/api/admin/support/cases");
  await expectStatus(adminInbox, 200, "Admin support inbox");
  assert.equal((await adminInbox.json()).cases.length, 1, "Admins can see unassigned cases");

  const claim = await request(base, 3, "POST", `/api/admin/support/cases/${created.id}/assign`, {});
  await expectStatus(claim, 200, "Admin claims unassigned case");

  const competingAdmin = await request(base, 2, "GET", `/api/admin/support/cases/${created.id}/messages`);
  await expectStatus(competingAdmin, 403, "Admin cannot read a case assigned to another Admin");

  const internalNote = await request(base, 3, "POST", `/api/admin/support/cases/${created.id}/messages`, {
    body: "Checked the transaction reference internally.",
    visibility: "INTERNAL",
    clientMessageId: "internal-note-1"
  });
  await expectStatus(internalNote, 201, "Admin internal note");

  let userMessagesResponse = await request(base, 4, "GET", `/api/support/cases/${created.id}/messages`);
  await expectStatus(userMessagesResponse, 200, "user conversation");
  let userMessages = (await userMessagesResponse.json()).messages;
  assert.equal(userMessages.length, 1, "internal notes must never be visible to the user");
  assert.equal(userMessages[0].body, createBody.description);

  const publicReply = await request(base, 3, "POST", `/api/admin/support/cases/${created.id}/messages`, {
    body: "We are checking your transaction. Please keep the payment reference available.",
    visibility: "PUBLIC",
    clientMessageId: "admin-reply-1"
  });
  await expectStatus(publicReply, 201, "Admin public reply");

  userMessagesResponse = await request(base, 4, "GET", `/api/support/cases/${created.id}/messages`);
  userMessages = (await userMessagesResponse.json()).messages;
  assert.equal(userMessages.length, 2, "user should receive the public Admin reply");

  const userReply = await request(base, 4, "POST", `/api/support/cases/${created.id}/messages`, {
    body: "Thank you. My payment reference is TXN-TEST-12345.",
    clientMessageId: "user-reply-1"
  });
  await expectStatus(userReply, 201, "user reply");
  let caseRow = (await testPool.query("SELECT status,assigned_admin_id FROM fynx_support_cases WHERE id=$1", [created.id])).rows[0];
  assert.equal(caseRow.status, "WAITING_FOR_SUPPORT");
  const duplicateUserReply = await request(base, 4, "POST", `/api/support/cases/${created.id}/messages`, {
    body: "Thank you. My payment reference is TXN-TEST-12345.",
    clientMessageId: "user-reply-1"
  });
  await expectStatus(duplicateUserReply, 200, "idempotent user reply retry");
  assert.equal((await testPool.query("SELECT COUNT(*)::int AS count FROM fynx_support_messages WHERE case_id=$1 AND sender_id=4 AND client_message_id='user-reply-1'", [created.id])).rows[0].count, 1);
  const replyNotificationUsers = (await testPool.query("SELECT user_id::text FROM fynx_notifications WHERE type='SUPPORT' AND target_id=$1 AND id LIKE 'support-user-reply-%' ORDER BY user_id", [String(created.id)])).rows.map(row => row.user_id);
  assert.deepEqual(replyNotificationUsers, ["1", "3"], "assigned case replies notify only the Owner and the assigned Admin");

  const resolve = await request(base, 3, "PATCH", `/api/admin/support/cases/${created.id}/status`, { status: "RESOLVED" });
  await expectStatus(resolve, 200, "assigned Admin resolves case");

  const adminReopen = await request(base, 3, "PATCH", `/api/admin/support/cases/${created.id}/status`, { status: "OPEN" });
  await expectStatus(adminReopen, 403, "Admin cannot reopen resolved case");
  const adminAlternateReopen = await request(base, 3, "PATCH", `/api/admin/support/cases/${created.id}/status`, { status: "IN_REVIEW" });
  await expectStatus(adminAlternateReopen, 403, "Admin cannot reopen a resolved case through another status");
  const adminClaimResolved = await request(base, 2, "POST", `/api/admin/support/cases/${created.id}/assign`, {});
  await expectStatus(adminClaimResolved, 403, "Admin cannot claim a resolved case");
  const adminResolvedPublicReply = await request(base, 3, "POST", `/api/admin/support/cases/${created.id}/messages`, {
    body: "This reply must not reopen the case.",
    visibility: "PUBLIC",
    clientMessageId: "admin-reopen-by-reply"
  });
  await expectStatus(adminResolvedPublicReply, 403, "Admin cannot reopen a resolved case by sending a public reply");
  const userResolvedReply = await request(base, 4, "POST", `/api/support/cases/${created.id}/messages`, {
    body: "This follow-up must wait for Owner reopening.",
    clientMessageId: "user-reopen-by-reply"
  });
  await expectStatus(userResolvedReply, 409, "requester cannot bypass Owner-only reopening through a reply");
  assert.equal((await testPool.query("SELECT status FROM fynx_support_cases WHERE id=$1", [created.id])).rows[0].status, "RESOLVED");

  const ownerReopen = await request(base, 1, "PATCH", `/api/admin/support/cases/${created.id}/status`, { status: "OPEN" });
  await expectStatus(ownerReopen, 200, "Owner reopens resolved case");

  const screenshotBytes = Buffer.from("fynx-support-screenshot-test");
  const media = await testPool.query("INSERT INTO message_media(owner_id,mime_type,data,byte_size) VALUES(4,'image/png',$1,$2) RETURNING id", [screenshotBytes, screenshotBytes.length]);
  const attach = await request(base, 4, "POST", `/api/support/cases/${created.id}/attachments`, { mediaId: String(media.rows[0].id) });
  await expectStatus(attach, 201, "user attaches owned screenshot");
  const attachment = (await attach.json()).attachment;

  const userAttachments = await request(base, 4, "GET", `/api/support/cases/${created.id}/attachments`);
  await expectStatus(userAttachments, 200, "user reads own screenshot metadata");
  assert.equal((await userAttachments.json()).attachments.length, 1);

  const assignedAdminAttachment = await request(base, 3, "GET", `/api/support/cases/${created.id}/attachments/${attachment.id}`);
  await expectStatus(assignedAdminAttachment, 200, "assigned Admin reads screenshot");
  assert.deepEqual(Buffer.from(await assignedAdminAttachment.arrayBuffer()), screenshotBytes);

  const otherAdminAttachment = await request(base, 2, "GET", `/api/support/cases/${created.id}/attachments`);
  await expectStatus(otherAdminAttachment, 403, "unassigned-to-case Admin cannot read assigned case attachment");

  const otherUserMessages = await request(base, 5, "GET", `/api/support/cases/${created.id}/messages`);
  await expectStatus(otherUserMessages, 404, "other user cannot read support conversation");

  const otherUserAttachment = await request(base, 5, "GET", `/api/support/cases/${created.id}/attachments`);
  await expectStatus(otherUserAttachment, 403, "other user cannot read support evidence");

  const notifications = await testPool.query("SELECT COUNT(*)::int AS count FROM fynx_notifications WHERE type='SUPPORT'");
  assert.ok(notifications.rows[0].count >= 5, "support events should be stored as notifications for the user and support team");

  const unassignedCaseResponse = await request(base, 4, "POST", "/api/support/cases", {
    category: "OTHER",
    subject: "App notification issue",
    description: "Support notifications are not appearing consistently on my device.",
    clientRequestId: "unassigned-notification-case"
  });
  await expectStatus(unassignedCaseResponse, 201, "create second unassigned support case");
  const unassignedCase = (await unassignedCaseResponse.json()).case;
  const unassignedNotificationUsers = (await testPool.query("SELECT user_id::text FROM fynx_notifications WHERE type='SUPPORT' AND target_id=$1 AND id LIKE 'support-created-%' ORDER BY user_id", [String(unassignedCase.id)])).rows.map(row => row.user_id);
  assert.deepEqual(unassignedNotificationUsers, ["1", "2", "3"], "new unassigned cases notify the Owner and all active Admins");

  const adminAssignment = await request(base, 1, "POST", `/api/admin/support/cases/${created.id}/assign`, { adminId: "2" });
  await expectStatus(adminAssignment, 200, "Owner reassigns case to another Admin");
  const previousAdmin = await request(base, 3, "GET", `/api/admin/support/cases/${created.id}/messages`);
  await expectStatus(previousAdmin, 403, "previous Admin loses access after reassignment");
  const newAdmin = await request(base, 2, "GET", `/api/admin/support/cases/${created.id}/messages`);
  await expectStatus(newAdmin, 200, "newly assigned Admin gains case access");
  const assignmentEvent = (await testPool.query("SELECT id FROM fynx_support_case_events WHERE case_id=$1 AND event_type='ASSIGNED' ORDER BY id DESC LIMIT 1", [created.id])).rows[0];
  const assignmentNotificationUsers = (await testPool.query("SELECT user_id::text FROM fynx_notifications WHERE type='SUPPORT' AND target_id=$1 AND id LIKE $2 ORDER BY user_id", [String(created.id), `support-assigned-${assignmentEvent.id}-%`])).rows.map(row => row.user_id);
  assert.deepEqual(assignmentNotificationUsers, ["1", "2"], "Owner and newly assigned Admin receive reassignment notification; old Admin does not");

  console.log("FYNX support database/API integration passed: case creation, idempotency, Owner/Admin access, assignment, private notes, replies, statuses, screenshot privacy and notifications.");
} finally {
  if (server) await new Promise(resolve => server.close(resolve));
  if (testPool) await testPool.end();
  await rootPool.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`).catch(() => {});
  await rootPool.end();
}
