import assert from "node:assert/strict";
import jwt from "jsonwebtoken";
import { registerAdminRoutes } from "./adminRoutes.js";

const secret = "stage1-admin-route-test-secret";
const routeHandlers = new Map();
const auditRows = [];
let reportStatus = "OPEN";
const report = {
  id: "91", target_type: "SOCIAL_POST", target_id: "10", target_owner_id: "1",
  reason: "Spam", details: "Repeated spam", status: reportStatus,
  created_at: new Date("2026-11-12T10:00:00Z"), updated_at: new Date("2026-11-12T10:00:00Z"),
  reporter_username: "reporter", owner_username: "official", content_preview: "Example post"
};

function queryResult(sql, params = []) {
  if (sql.includes("FROM users WHERE lower(username)=$1 AND verified=TRUE")) {
    return { rows: params[0] === "official" ? [{ id: "1" }] : [], rowCount: params[0] === "official" ? 1 : 0 };
  }
  if (sql.includes("FROM fynx_admin_roles WHERE user_id=$1")) {
    const allowed = String(params[0]) === "3";
    return { rows: allowed ? [{ "?column?": 1 }] : [], rowCount: allowed ? 1 : 0 };
  }
  if (sql.includes("FROM safety_content_reports r JOIN users reporter")) {
    return { rows: [{ ...report, status: reportStatus }], rowCount: 1 };
  }
  if (sql.includes("SELECT id,status FROM safety_content_reports")) {
    return { rows: [{ id: "91", status: reportStatus }], rowCount: 1 };
  }
  if (sql.includes("UPDATE safety_content_reports")) {
    reportStatus = params[0];
    return { rows: [{ id: "91", status: reportStatus, updated_at: new Date() }], rowCount: 1 };
  }
  if (sql.includes("INSERT INTO safety_content_moderation_audit")) {
    auditRows.push({ reportId: params[0], moderatorId: params[1], previousStatus: params[2], newStatus: params[3], decisionNote: params[4] });
    return { rows: [], rowCount: 1 };
  }
  if (sql.includes("FROM safety_content_moderation_audit a JOIN users u")) {
    return { rows: auditRows.map((entry, index) => ({ id: String(index + 1), moderator_id: entry.moderatorId, username: "moderator", previous_status: entry.previousStatus, new_status: entry.newStatus, decision_note: entry.decisionNote, created_at: new Date() })), rowCount: auditRows.length };
  }
  return { rows: [], rowCount: 0 };
}

const pool = {
  async query(sql, params) { return queryResult(sql, params); },
  async connect() {
    return {
      async query(sql, params) { return queryResult(sql, params); },
      release() {}
    };
  }
};
const app = {
  get(path, ...handlers) { routeHandlers.set(`GET ${path}`, handlers); },
  patch(path, ...handlers) { routeHandlers.set(`PATCH ${path}`, handlers); },
  post(path, ...handlers) { routeHandlers.set(`POST ${path}`, handlers); }
};
registerAdminRoutes({ app, pool, jwtSecret: secret, ownerUsername: "official" });

function makeResponse() {
  return {
    statusCode: 200, body: undefined, headers: {},
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    set(name, value) { this.headers[name] = value; return this; }
  };
}
async function call(method, path, token, body = {}, params = {}) {
  const handlers = routeHandlers.get(`${method} ${path}`);
  assert.ok(handlers, `registered route: ${method} ${path}`);
  const req = {
    body, params, query: { status: "ALL" },
    get(name) { return name.toLowerCase() === "authorization" && token ? `Bearer ${token}` : ""; }
  };
  const res = makeResponse();
  let index = 0;
  const next = async () => {
    const handler = handlers[index++];
    if (handler) return handler(req, res, next);
  };
  await next();
  return res;
}
const token = userId => jwt.sign({ sub: String(userId) }, secret);

const noToken = await call("GET", "/api/admin/content-reports", null);
assert.equal(noToken.statusCode, 401, "missing bearer token is rejected by real JWT middleware");
const invalidToken = await call("GET", "/api/admin/content-reports", "not-a-jwt");
assert.equal(invalidToken.statusCode, 401, "invalid bearer token is rejected");
const ordinaryUser = await call("GET", "/api/admin/content-reports", token(2));
assert.equal(ordinaryUser.statusCode, 403, "ordinary verified account cannot access moderation queue");
const owner = await call("GET", "/api/admin/content-reports", token(1));
assert.equal(owner.statusCode, 200, "configured verified official account receives OWNER access");
assert.equal(owner.body.reports.length, 1, "owner receives the moderation queue");
const admin = await call("GET", "/api/admin/content-reports", token(3));
assert.equal(admin.statusCode, 200, "explicit ADMIN role receives moderation queue");

const reviewed = await call("PATCH", "/api/admin/content-reports/:reportId", token(3), { status: "UNDER_REVIEW", decisionNote: "Review started" }, { reportId: "91" });
assert.equal(reviewed.statusCode, 200, "admin can call the actual moderation endpoint");
assert.equal(reviewed.body.auditRecorded, true, "moderation endpoint confirms audit recording");
assert.equal(auditRows.length, 1, "moderation endpoint writes the audit row");
assert.equal(auditRows[0].moderatorId, "3");
assert.equal(auditRows[0].newStatus, "UNDER_REVIEW");

const backwards = await call("PATCH", "/api/admin/content-reports/:reportId", token(3), { status: "OPEN" }, { reportId: "91" });
assert.equal(backwards.statusCode, 400, "invalid status payload is rejected");
const terminal = await call("PATCH", "/api/admin/content-reports/:reportId", token(1), { status: "RESOLVED", decisionNote: "Resolved" }, { reportId: "91" });
assert.equal(terminal.statusCode, 200, "owner can resolve a report from review");
const repeated = await call("PATCH", "/api/admin/content-reports/:reportId", token(1), { status: "DISMISSED" }, { reportId: "91" });
assert.equal(repeated.statusCode, 409, "terminal moderation decision cannot be overwritten");
const audit = await call("GET", "/api/admin/content-reports/:reportId/audit", token(1), {}, { reportId: "91" });
assert.equal(audit.statusCode, 200, "owner can retrieve the real moderation audit endpoint");
assert.equal(audit.body.audit.length, 2, "audit endpoint returns actual moderation decisions");
console.log("Admin content-report route tests passed (JWT auth, OWNER/ADMIN authorization, moderation and audit).");
