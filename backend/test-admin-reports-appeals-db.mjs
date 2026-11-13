import assert from "node:assert/strict";
import pg from "pg";
import { registerAdminRoutes } from "./adminRoutes.js";
import { registerTrustSafetyRoutes } from "./trustSafety.js";

const { Client } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");

const db = new Client();
const schema = `fynx_admin_reports_appeals_${process.pid}_${Date.now()}`;
const secret = "fynx-admin-reports-appeals-test-secret";
const tokens = {
  owner: { sub: "1", sv: 0 },
  admin: { sub: "2", sv: 0 },
  regular: { sub: "5", sv: 0 },
  "appealing-user": { sub: "3", sv: 0 }
};
const jwtApi = {
  verify(token, receivedSecret) {
    assert.equal(receivedSecret, secret);
    if (!tokens[token]) throw new Error("invalid test JWT");
    return tokens[token];
  }
};

function createResponse() {
  return {
    statusCode: 200,
    body: null,
    headers: {},
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    set(name, value) { this.headers[name.toLowerCase()] = value; return this; },
    end() { return this; }
  };
}

const routes = new Map();
const app = {};
for (const method of ["get", "post", "patch", "delete"]) {
  app[method] = (path, ...handlers) => routes.set(`${method.toUpperCase()} ${path}`, handlers);
}

async function appealUserAuth(req, res, next) {
  const header = req.get("authorization") || "";
  if (header !== "Bearer appealing-user") return res.status(401).json({ error: "authentication required" });
  req.user = { sub: "3" };
  await next();
}

const pool = {
  query(text, values) { return db.query(text, values); },
  async connect() {
    return { query(text, values) { return db.query(text, values); }, release() {} };
  }
};

async function invoke(method, path, {
  token = "admin",
  params = {},
  query = {},
  body = {}
} = {}) {
  const handlers = routes.get(`${method.toUpperCase()} ${path}`);
  assert.ok(handlers && handlers.length === 2, `route must be registered: ${method} ${path}`);
  const [authenticate, handler] = handlers;
  const req = {
    params,
    query,
    body,
    get(name) { return name.toLowerCase() === "authorization" ? (token ? `Bearer ${token}` : "") : ""; }
  };
  const res = createResponse();
  await authenticate(req, res, () => handler(req, res));
  return res;
}

async function invokeDashboard() {
  const handlers = routes.get("GET /api/admin/dashboard");
  assert.ok(handlers && handlers.length === 2);
  const [authenticate, handler] = handlers;
  const req = { get(name) { return name.toLowerCase() === "authorization" ? "Bearer owner" : ""; } };
  const res = createResponse();
  await authenticate(req, res, () => handler(req, res));
  return res;
}

async function statusFor(userId) {
  const row = await db.query(
    "SELECT fas.account_status AS platform_status, fs.account_status AS settings_status FROM fynx_account_safety fas JOIN safety_settings fs ON fs.user_id=fas.user_id WHERE fas.user_id=$1",
    [userId]
  );
  assert.equal(row.rowCount, 1);
  return row.rows[0];
}

await db.connect();
try {
  await db.query(`CREATE SCHEMA "${schema}"`);
  await db.query(`SET search_path TO "${schema}"`);
  await db.query(`
    CREATE TABLE users (
      id BIGINT PRIMARY KEY,
      username TEXT NOT NULL UNIQUE,
      display_name TEXT NOT NULL DEFAULT '',
      session_version INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE message_media (
      id BIGINT PRIMARY KEY,
      owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      mime_type TEXT NOT NULL
    );
    CREATE TABLE marketplace_orders (id UUID PRIMARY KEY);
    CREATE TABLE marketplace_listings (id BIGINT PRIMARY KEY);
    INSERT INTO users(id,username,display_name,session_version) VALUES
      (1,'owner','FYNX Owner',0),
      (2,'admin-two','Admin Two',0),
      (3,'target-three','Target Three',0),
      (4,'target-four','Target Four',0),
      (5,'regular-user','Regular User',0);
  `);

  registerAdminRoutes({ app, databasePool: pool, jwtApi, jwtSecret: secret });
  registerTrustSafetyRoutes({ app, pool, auth: appealUserAuth });
  const dashboard = await invokeDashboard();
  assert.equal(dashboard.statusCode, 200);
  await db.query("INSERT INTO fynx_admin_roles(user_id,role) VALUES(2,'ADMIN')");

  const deniedReports = await invoke("GET", "/api/admin/reports", { token: "regular" });
  assert.equal(deniedReports.statusCode, 403, "ordinary users must not read Admin reports");
  const deniedAppeals = await invoke("GET", "/api/admin/appeals", { token: "regular" });
  assert.equal(deniedAppeals.statusCode, 403, "ordinary users must not read Admin appeals");

  await db.query(`
    INSERT INTO fynx_account_safety(user_id,account_status,status_note) VALUES
      (3,'LOCKED','Existing restriction for target three'),
      (4,'LIMITED','Existing restriction for target four');
    INSERT INTO safety_settings(user_id,account_status,status_note) VALUES
      (3,'LOCKED','Existing restriction for target three'),
      (4,'LIMITED','Existing restriction for target four');
  `);

  const validReport = await db.query(
    "INSERT INTO safety_reports(reporter_id,target_user_id,target_username,reason,details,status) VALUES(5,3,'target-three','ACCOUNT_RESTRICTION','Valid linked account report','OPEN') RETURNING id"
  );
  const mismatchReport = await db.query(
    "INSERT INTO safety_reports(reporter_id,target_user_id,target_username,reason,details,status) VALUES(5,4,'target-four','ACCOUNT_RESTRICTION','Report against a different account','OPEN') RETURNING id"
  );
  const reviewReport = await db.query(
    "INSERT INTO safety_reports(reporter_id,target_user_id,target_username,reason,details,status) VALUES(5,4,'target-four','SPAM','Report lifecycle transition test','OPEN') RETURNING id"
  );
  const validReportId = String(validReport.rows[0].id);
  const mismatchReportId = String(mismatchReport.rows[0].id);
  const reviewReportId = String(reviewReport.rows[0].id);
  const largeReport = await db.query(
    "INSERT INTO safety_reports(id,reporter_id,target_user_id,target_username,reason,details,status) VALUES(9007199254740993,5,3,'target-three','ACCOUNT_RESTRICTION','Large BIGINT report ID test','OPEN') RETURNING id"
  );
  const largeReportId = String(largeReport.rows[0].id);


  const validAppeal = await db.query(
    "INSERT INTO safety_appeals(user_id,report_id,subject,details,status) VALUES(3,$1,'Restore my account','The account restriction was applied in error.','OPEN') RETURNING id",
    [validReportId]
  );
  const mismatchAppeal = await db.query(
    "INSERT INTO safety_appeals(user_id,report_id,subject,details,status) VALUES(3,$1,'Appeal linked to another user','The referenced report belongs to a different account.','OPEN') RETURNING id",
    [mismatchReportId]
  );
  const unlinkedAppeal = await db.query(
    "INSERT INTO safety_appeals(user_id,report_id,subject,details,status) VALUES(4,NULL,'Unlinked appeal','This appeal has no account report link.','OPEN') RETURNING id"
  );
  const rejectedAppeal = await db.query(
    "INSERT INTO safety_appeals(user_id,report_id,subject,details,status) VALUES(4,NULL,'Appeal for rejection','This appeal will be rejected.','OPEN') RETURNING id"
  );
  const rollbackAppeal = await db.query(
    "INSERT INTO safety_appeals(user_id,report_id,subject,details,status) VALUES(3,$1,'Appeal rollback test','Audit failure must roll back decision and restoration.','OPEN') RETURNING id",
    [validReportId]
  );

  const crossUserAppeal = await invoke("POST", "/api/safety/appeals", {
    token: "appealing-user",
    body: { reportId: mismatchReportId, subject: "Wrong account report", details: "A user may only appeal a report linked to their own account." }
  });
  assert.equal(crossUserAppeal.statusCode, 404, "appeal submission must reject a report linked to another account");
  const largeAppeal = await invoke("POST", "/api/safety/appeals", {
    token: "appealing-user",
    body: { reportId: largeReportId, subject: "Appeal linked to a large report ID", details: "A valid PostgreSQL BIGINT string must not be rounded through JavaScript Number." }
  });
  assert.equal(largeAppeal.statusCode, 201, "user appeal route must accept valid report IDs above Number.MAX_SAFE_INTEGER");
  assert.equal(largeAppeal.body.appeal.reportId, "9007199254740993", "large report IDs must round-trip exactly");
  const largeAppealRow = await db.query("SELECT report_id FROM safety_appeals WHERE id=$1", [largeAppeal.body.appeal.id]);
  assert.equal(String(largeAppealRow.rows[0].report_id), "9007199254740993");
  const overflowAppeal = await invoke("POST", "/api/safety/appeals", {
    token: "appealing-user",
    body: { reportId: "9223372036854775808", subject: "Overflow report ID", details: "Values above PostgreSQL BIGINT must be rejected." }
  });
  assert.equal(overflowAppeal.statusCode, 400, "user appeal route must reject PostgreSQL BIGINT overflow");
  const unsafeNumericAppeal = await invoke("POST", "/api/safety/appeals", {
    token: "appealing-user",
    body: { reportId: 9007199254740992, subject: "Unsafe numeric ID", details: "Unsafe JSON numeric IDs must be rejected before conversion." }
  });
  assert.equal(unsafeNumericAppeal.statusCode, 400, "unsafe JavaScript numeric report IDs must be rejected");
  const nonCanonicalAppeal = await invoke("POST", "/api/safety/appeals", {
    token: "appealing-user",
    body: { reportId: "01", subject: "Noncanonical ID", details: "Leading-zero IDs must be rejected." }
  });
  assert.equal(nonCanonicalAppeal.statusCode, 400, "non-canonical report ID strings must be rejected");
  const validAppealId = String(validAppeal.rows[0].id);
  const mismatchAppealId = String(mismatchAppeal.rows[0].id);
  const unlinkedAppealId = String(unlinkedAppeal.rows[0].id);
  const rejectedAppealId = String(rejectedAppeal.rows[0].id);
  const rollbackAppealId = String(rollbackAppeal.rows[0].id);

  const reportList = await invoke("GET", "/api/admin/reports", {
    query: { limit: 50, offset: 0 }
  });
  assert.equal(reportList.statusCode, 200);
  assert.equal(reportList.headers["cache-control"], undefined, "Admin report list is not a shared cache response");
  assert.ok(reportList.body.reports.some(item => item.id === validReportId));

  const invalidReportId = await invoke("PATCH", "/api/admin/reports/:reportId", {
    params: { reportId: "9223372036854775808" },
    body: { status: "RESOLVED" }
  });
  assert.equal(invalidReportId.statusCode, 400, "report IDs above PostgreSQL BIGINT must be rejected");
  const invalidReportStatus = await invoke("PATCH", "/api/admin/reports/:reportId", {
    params: { reportId: validReportId },
    body: { status: "ACCEPTED" }
  });
  assert.equal(invalidReportStatus.statusCode, 400, "unsupported report state transitions must be rejected");

  const markUnderReview = await invoke("PATCH", "/api/admin/reports/:reportId", {
    params: { reportId: reviewReportId },
    body: { status: "UNDER_REVIEW" }
  });
  assert.equal(markUnderReview.statusCode, 200);
  assert.equal(markUnderReview.body.report.status, "UNDER_REVIEW");
  const resolveReport = await invoke("PATCH", "/api/admin/reports/:reportId", {
    params: { reportId: reviewReportId },
    body: { status: "RESOLVED" }
  });
  assert.equal(resolveReport.statusCode, 200);
  assert.equal(resolveReport.body.report.status, "RESOLVED");
  const reReviewFinalReport = await invoke("PATCH", "/api/admin/reports/:reportId", {
    params: { reportId: reviewReportId },
    body: { status: "DISMISSED" }
  });
  assert.equal(reReviewFinalReport.statusCode, 404, "final report states must not be changed through the open-report route");

  const appealList = await invoke("GET", "/api/admin/appeals", {
    query: { limit: 50, offset: 0 }
  });
  assert.equal(appealList.statusCode, 200);
  const linkedAppeal = appealList.body.appeals.find(item => item.id === validAppealId);
  assert.ok(linkedAppeal);
  assert.equal(linkedAppeal.reportId, validReportId);
  assert.equal(linkedAppeal.accountStatus, "LOCKED", "Admin appeal history must expose the actual restriction state");

  const invalidAppealId = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: "9223372036854775808" },
    body: { status: "ACCEPTED", decisionNote: "Invalid ID" }
  });
  assert.equal(invalidAppealId.statusCode, 400, "appeal IDs above PostgreSQL BIGINT must be rejected");
  const invalidAppealStatus = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: validAppealId },
    body: { status: "RESOLVED" }
  });
  assert.equal(invalidAppealStatus.statusCode, 400, "unsupported appeal decisions must be rejected");

  const moveToReview = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: validAppealId },
    body: { status: "UNDER_REVIEW", decisionNote: "Evidence is being reviewed." }
  });
  assert.equal(moveToReview.statusCode, 200);
  assert.equal(moveToReview.body.appeal.status, "UNDER_REVIEW");
  const duplicateReview = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: validAppealId },
    body: { status: "UNDER_REVIEW", decisionNote: "Duplicate review must not create a second transition." }
  });
  assert.equal(duplicateReview.statusCode, 409, "an appeal already under review must not be re-opened as a duplicate action");

  const acceptLinked = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: validAppealId },
    body: { status: "ACCEPTED", decisionNote: "Evidence supports restoring the reported account." }
  });
  assert.equal(acceptLinked.statusCode, 200);
  assert.equal(acceptLinked.body.appeal.restored, true, "a linked report targeting the appellant must restore the account");
  let state = await statusFor("3");
  assert.equal(state.platform_status, "ACTIVE");
  assert.equal(state.settings_status, "ACTIVE");

  const finalDecisionRepeat = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: validAppealId },
    body: { status: "REJECTED", decisionNote: "Second final decision must fail." }
  });
  assert.equal(finalDecisionRepeat.statusCode, 409, "a final appeal decision must not be overwritten");

  await db.query(
    "UPDATE fynx_account_safety SET account_status='LOCKED',status_note='Mismatch test lock' WHERE user_id=3; UPDATE safety_settings SET account_status='LOCKED',status_note='Mismatch test lock' WHERE user_id=3"
  );
  const mismatchDecision = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: mismatchAppealId },
    body: { status: "ACCEPTED", decisionNote: "The linked report targets a different account." }
  });
  assert.equal(mismatchDecision.statusCode, 200);
  assert.equal(mismatchDecision.body.appeal.restored, false, "mismatched report ownership must not restore another account");
  state = await statusFor("3");
  assert.equal(state.platform_status, "LOCKED");
  assert.equal(state.settings_status, "LOCKED");

  const unlinkedDecision = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: unlinkedAppealId },
    body: { status: "ACCEPTED", decisionNote: "Accepted without linked account report." }
  });
  assert.equal(unlinkedDecision.statusCode, 200);
  assert.equal(unlinkedDecision.body.appeal.restored, false, "an unlinked appeal must not automatically restore an account");
  state = await statusFor("4");
  assert.equal(state.platform_status, "LIMITED");
  assert.equal(state.settings_status, "LIMITED");

  const rejectDecision = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: rejectedAppealId },
    body: { status: "REJECTED", decisionNote: "Appeal rejected after review." }
  });
  assert.equal(rejectDecision.statusCode, 200);
  assert.equal(rejectDecision.body.appeal.restored, false);
  state = await statusFor("4");
  assert.equal(state.platform_status, "LIMITED");
  assert.equal(state.settings_status, "LIMITED");

  // A forced audit failure must roll back the final decision and both account restoration writes.
  await db.query(`
    CREATE FUNCTION reject_safety_decision_audit() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN
      IF NEW.action IN ('REPORT_REVIEWED','APPEAL_DECIDED') THEN
        RAISE EXCEPTION 'forced safety decision audit failure';
      END IF;
      RETURN NEW;
    END;
    $$;
    CREATE TRIGGER reject_safety_decision_audit_trigger
      BEFORE INSERT ON fynx_admin_activity
      FOR EACH ROW EXECUTE FUNCTION reject_safety_decision_audit();
  `);
  const reportAuditCount = await db.query("SELECT COUNT(*)::int AS count FROM fynx_admin_activity WHERE action='REPORT_REVIEWED'");
  const failedReportReview = await invoke("PATCH", "/api/admin/reports/:reportId", {
    params: { reportId: validReportId },
    body: { status: "UNDER_REVIEW" }
  });
  assert.equal(failedReportReview.statusCode, 500);
  const reportState = await db.query("SELECT status FROM safety_reports WHERE id=$1", [validReportId]);
  assert.equal(reportState.rows[0].status, "OPEN", "report status must roll back when its audit event fails");
  const reportAuditAfter = await db.query("SELECT COUNT(*)::int AS count FROM fynx_admin_activity WHERE action='REPORT_REVIEWED'");
  assert.equal(reportAuditAfter.rows[0].count, reportAuditCount.rows[0].count);

  const appealAuditCount = await db.query("SELECT COUNT(*)::int AS count FROM fynx_admin_activity WHERE action='APPEAL_DECIDED'");
  const failedAppealDecision = await invoke("PATCH", "/api/admin/appeals/:appealId", {
    params: { appealId: rollbackAppealId },
    body: { status: "ACCEPTED", decisionNote: "The forced audit failure must roll this decision back." }
  });
  assert.equal(failedAppealDecision.statusCode, 500);
  const rollbackAppealState = await db.query("SELECT status FROM safety_appeals WHERE id=$1", [rollbackAppealId]);
  assert.equal(rollbackAppealState.rows[0].status, "OPEN", "failed audit must roll back the final appeal decision");
  state = await statusFor("3");
  assert.equal(state.platform_status, "LOCKED", "failed appeal audit must roll back platform access restoration");
  assert.equal(state.settings_status, "LOCKED", "failed appeal audit must roll back legacy safety restoration");
  const appealAuditAfter = await db.query("SELECT COUNT(*)::int AS count FROM fynx_admin_activity WHERE action='APPEAL_DECIDED'");
  assert.equal(appealAuditAfter.rows[0].count, appealAuditCount.rows[0].count);
  await db.query("DROP TRIGGER reject_safety_decision_audit_trigger ON fynx_admin_activity; DROP FUNCTION reject_safety_decision_audit();");

  // Exercise stable pagination on both Admin queues beyond one 50-item page.
  await db.query(`
    INSERT INTO safety_reports(reporter_id,target_user_id,target_username,reason,details,status)
    SELECT 5,4,'target-four','PAGINATION','Report pagination row '||n,'OPEN'
    FROM generate_series(1,55) AS n;
    INSERT INTO safety_appeals(user_id,report_id,subject,details,status)
    SELECT 4,NULL,'Appeal pagination '||n,'Appeal pagination row '||n,'OPEN'
    FROM generate_series(1,55) AS n;
  `);
  const reportsPageOne = await invoke("GET", "/api/admin/reports", { query: { limit: 50, offset: 0 } });
  const reportsPageTwo = await invoke("GET", "/api/admin/reports", { query: { limit: 50, offset: 50 } });
  assert.equal(reportsPageOne.body.reports.length, 50);
  assert.equal(reportsPageOne.body.hasMore, true);
  assert.equal(reportsPageTwo.body.offset, 50);
  assert.ok(reportsPageTwo.body.reports.length > 0);
  const reportIds = new Set(reportsPageOne.body.reports.map(item => item.id));
  assert.ok(reportsPageTwo.body.reports.every(item => !reportIds.has(item.id)), "report pages must not overlap");

  const appealsPageOne = await invoke("GET", "/api/admin/appeals", { query: { limit: 50, offset: 0 } });
  const appealsPageTwo = await invoke("GET", "/api/admin/appeals", { query: { limit: 50, offset: 50 } });
  assert.equal(appealsPageOne.body.appeals.length, 50);
  assert.equal(appealsPageOne.body.hasMore, true);
  assert.equal(appealsPageTwo.body.offset, 50);
  assert.ok(appealsPageTwo.body.appeals.length > 0);
  const appealIds = new Set(appealsPageOne.body.appeals.map(item => item.id));
  assert.ok(appealsPageTwo.body.appeals.every(item => !appealIds.has(item.id)), "appeal pages must not overlap");

  console.log("ADMIN_REPORTS_APPEALS_ROUTE_DB=GREEN (role protection, report transitions, appeal locking/final decisions, linked restoration, mismatched/unlinked safeguards, pagination and atomic audit rollback)");
} finally {
  await db.query("SET search_path TO public").catch(() => {});
  await db.query(`DROP SCHEMA IF EXISTS "${schema}" CASCADE`).catch(() => {});
  await db.end();
}
