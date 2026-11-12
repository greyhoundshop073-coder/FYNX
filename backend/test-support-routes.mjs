import pg from "pg";
import express from "express";
import jwt from "jsonwebtoken";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";

const { Pool } = pg;

function requiredEnv(name, fallback = "") {
  const value = process.env[name] || fallback;
  if (!value) throw new Error(`Support integration test requires ${name}.`);
  return value;
}

async function main() {
  const host = requiredEnv("PGHOST");
  const port = Number(requiredEnv("PGPORT", "5432"));
  const user = requiredEnv("PGUSER");
  const password = requiredEnv("PGPASSWORD");
  const database = requiredEnv("PGDATABASE");
  const adminPool = new Pool({ host, port, user, password, database, ssl: false, max: 2 });
  const schema = `fynx_support_test_${randomUUID().replaceAll("-", "").slice(0, 20)}`;
  const secret = `support-test-${randomUUID()}`;
  let server;
  try {
    await adminPool.query(`CREATE SCHEMA ${schema}`);
    const databaseUrl = new URL(`postgresql://${encodeURIComponent(user)}:${encodeURIComponent(password)}@${host}:${port}/${database}`);
    databaseUrl.searchParams.set("options", `-c search_path=${schema}`);
    process.env.DATABASE_URL = databaseUrl.toString();
    process.env.JWT_SECRET = secret;
    delete process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    delete process.env.FIREBASE_CLIENT_EMAIL;
    delete process.env.FIREBASE_PRIVATE_KEY;

    const routePool = new Pool({ connectionString: process.env.DATABASE_URL, ssl: false, max: 4 });
    await routePool.query(`
      CREATE TABLE users (
        id BIGSERIAL PRIMARY KEY,
        username TEXT NOT NULL UNIQUE,
        display_name TEXT NOT NULL DEFAULT ''
      );
      CREATE TABLE fynx_admin_roles (
        user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
        role TEXT NOT NULL CHECK(role='ADMIN'),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
    `);
    const createdUsers = await routePool.query(`
      INSERT INTO users(username,display_name) VALUES
        ('support_test_owner','Support Test Owner'),
        ('support_test_user','Support Test User'),
        ('support_test_user_two','Support Test User Two'),
        ('support_test_admin','Support Test Admin'),
        ('support_test_admin_two','Support Test Admin Two'),
        ('support_test_admin_no_access','Support Test Admin No Access')
      RETURNING id,username
    `);
    const ids = Object.fromEntries(createdUsers.rows.map(row => [row.username, String(row.id)]));
    await routePool.query(
      "INSERT INTO fynx_admin_roles(user_id,role) VALUES($1,'ADMIN'),($2,'ADMIN'),($3,'ADMIN')",
      [ids.support_test_admin, ids.support_test_admin_two, ids.support_test_admin_no_access]
    );

    const { registerSupportRoutes } = await import("./supportRoutes.js");
    const app = express();
    app.use(express.json());
    registerSupportRoutes({ app });
    server = await new Promise((resolve, reject) => {
      const instance = app.listen(0, "127.0.0.1", () => resolve(instance));
      instance.once("error", reject);
    });
    const address = server.address();
    const root = `http://127.0.0.1:${address.port}`;
    const tokenFor = (username) => jwt.sign({ sub: ids[username], username }, secret, { expiresIn: "10m" });

    async function request(path, username, method = "GET", body = undefined) {
      const response = await fetch(root + path, {
        method,
        headers: {
          Authorization: `Bearer ${tokenFor(username)}`,
          ...(body === undefined ? {} : { "Content-Type": "application/json" })
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) })
      });
      const payload = await response.json().catch(() => ({}));
      return { status: response.status, payload };
    }

    const requestKey = randomUUID();
    const requestBody = {
      subject: "Wallet balance did not update",
      category: "WALLET_PAYMENTS",
      description: "I completed the transaction but my wallet balance has not updated.",
      requestKey
    };
    const created = await request("/api/support/cases", "support_test_user", "POST", requestBody);
    assert.equal(created.status, 201, "a user can create a support case");
    const caseId = created.payload.supportCase?.id;
    const caseNumber = created.payload.supportCase?.caseNumber;
    assert.ok(caseId && caseNumber?.startsWith("FYNX-"), "case number is returned after persistence");

    const retry = await request("/api/support/cases", "support_test_user", "POST", requestBody);
    assert.equal(retry.status, 200, "a repeated create request is deduplicated");
    assert.equal(retry.payload.supportCase.id, caseId, "retry returns the original case");

    const ownCases = await request("/api/support/cases", "support_test_user");
    assert.equal(ownCases.status, 200);
    assert.equal(ownCases.payload.cases.length, 1, "idempotent retry does not create a second case");

    const crossAccount = await request(`/api/support/cases/${caseId}`, "support_test_user_two");
    assert.equal(crossAccount.status, 404, "another user cannot read this support case");

    const ownerRevokesSupport = await request("/api/admin/support/admins/" + ids.support_test_admin_no_access + "/access", "support_test_owner", "PATCH", { enabled: false });
    assert.equal(ownerRevokesSupport.status, 200, "Owner can revoke support access for an individual Admin");

    const deniedAdmin = await request("/api/admin/support/cases", "support_test_admin_no_access");
    assert.equal(deniedAdmin.status, 403, "ordinary Admin without support permission is denied");

    const defaultSupportAccess = await request("/api/support/access", "support_test_admin_two");
    assert.equal(defaultSupportAccess.payload.supportAccess, true, "new Admin rows receive support access by default");

    const staffInbox = await request("/api/admin/support/cases", "support_test_admin");
    assert.equal(staffInbox.status, 200, "support-enabled Admin can read the inbox");
    assert.equal(staffInbox.payload.cases.length, 1, "Admin can see unassigned cases");

    const replyBeforeClaim = await request(`/api/admin/support/cases/${caseId}/messages`, "support_test_admin", "POST", { message: "I am checking this.", requestKey: randomUUID() });
    assert.equal(replyBeforeClaim.status, 403, "Admin must claim or be assigned before replying");

    const claimed = await request(`/api/admin/support/cases/${caseId}/claim`, "support_test_admin", "POST", {});
    assert.equal(claimed.status, 200, "an enabled Admin can claim an unassigned case");

    const hiddenFromOtherAdmin = await request(`/api/admin/support/cases/${caseId}`, "support_test_admin_two");
    assert.equal(hiddenFromOtherAdmin.status, 404, "Admin cannot inspect another Admin's assigned case");

    const internalNote = await request(`/api/admin/support/cases/${caseId}/messages`, "support_test_admin", "POST", {
      message: "Check the wallet ledger entry before replying.",
      visibility: "INTERNAL",
      requestKey: randomUUID()
    });
    assert.equal(internalNote.status, 200, "assigned Admin can save an internal note");

    const userDetailAfterNote = await request(`/api/support/cases/${caseId}`, "support_test_user");
    assert.equal(userDetailAfterNote.status, 200);
    assert.equal(userDetailAfterNote.payload.messages.some(message => message.message.includes("wallet ledger")), false, "internal notes are hidden from the user");

    const publicRequestKey = randomUUID();
    const publicReplyBody = {
      message: "We are checking the transaction record. Please allow us to review it.",
      requestKey: publicRequestKey
    };
    const publicReply = await request(`/api/admin/support/cases/${caseId}/messages`, "support_test_admin", "POST", publicReplyBody);
    assert.equal(publicReply.status, 200, "assigned Admin can privately reply to the user");
    const publicReplyRetry = await request(`/api/admin/support/cases/${caseId}/messages`, "support_test_admin", "POST", publicReplyBody);
    assert.equal(publicReplyRetry.status, 200, "repeated staff reply request is safely deduplicated");

    const ownerDetail = await request(`/api/admin/support/cases/${caseId}`, "support_test_owner");
    assert.equal(ownerDetail.status, 200, "Owner can inspect every support case");
    assert.equal(ownerDetail.payload.messages.some(message => message.visibility === "INTERNAL"), true, "staff can see internal notes");

    const userDetailAfterReply = await request(`/api/support/cases/${caseId}`, "support_test_user");
    assert.equal(userDetailAfterReply.payload.messages.some(message => message.message.includes("Please allow us")), true, "user can read the Admin's public reply");
    assert.equal(userDetailAfterReply.payload.messages.some(message => message.visibility === "INTERNAL"), false, "user response never includes internal notes");

    const resolved = await request(`/api/admin/support/cases/${caseId}/status`, "support_test_admin", "PATCH", { status: "RESOLVED" });
    assert.equal(resolved.status, 200, "assigned Admin can resolve a case");

    const userMessageBody = {
      message: "The problem is still happening after the last update.",
      requestKey: randomUUID()
    };
    const userReopenPath = "/api/support/cases/" + caseId + "/messages";
    const userReopen = await request(userReopenPath, "support_test_user", "POST", userMessageBody);
    assert.equal(userReopen.status, 200, "user can reply to a resolved case");
    assert.equal(userReopen.payload.supportCase.status, "OPEN", "a user reply reopens a resolved case");
    const userMessageRetry = await request(userReopenPath, "support_test_user", "POST", userMessageBody);
    assert.equal(userMessageRetry.status, 200, "a repeated user reply request is safely deduplicated");

    const reassigned = await request(`/api/admin/support/cases/${caseId}/assign`, "support_test_owner", "PATCH", {
      adminUserId: Number(ids.support_test_admin_two)
    });
    assert.equal(reassigned.status, 200, "Owner can assign or reassign a case to an enabled Admin");

    const newlyAssignedAdmin = await request(`/api/admin/support/cases/${caseId}`, "support_test_admin_two");
    assert.equal(newlyAssignedAdmin.status, 200, "the assigned Admin can now view the case");

    const ownerAccess = await request("/api/admin/support/admins", "support_test_owner");
    assert.equal(ownerAccess.status, 200, "Owner can manage support team permissions");
    const enabledOtherAdmin = await request("/api/admin/support/admins/" + ids.support_test_admin_no_access + "/access", "support_test_owner", "PATCH", { enabled: true });
    assert.equal(enabledOtherAdmin.status, 200, "Owner can grant support access to an existing Admin");

    const regularUserAccess = await request("/api/admin/support/admins", "support_test_user");
    assert.equal(regularUserAccess.status, 403, "ordinary users cannot manage support permissions");

    const userMessagesAfterRetry = await request("/api/support/cases/" + caseId, "support_test_user");
    assert.equal(userMessagesAfterRetry.payload.messages.filter(message => message.message.includes("The problem is still happening")).length, 1, "user reply retry is not duplicated");
    console.log("PASS: support case create, idempotency, account isolation, Admin permissions, assignment, public replies, internal notes, status resolution/reopen and Owner controls.");
    await routePool.end();
  } finally {
    if (server) await new Promise(resolve => server.close(resolve));
    await adminPool.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`).catch(() => {});
    await adminPool.end();
  }
}

main().then(() => {
  process.exit(0);
}).catch(error => {
  console.error("FYNX support integration test failed:", error);
  process.exit(1);
});
