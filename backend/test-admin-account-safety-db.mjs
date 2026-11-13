import assert from "node:assert/strict";
import pg from "pg";
import { registerAdminRoutes } from "./adminRoutes.js";

const { Client } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");

const db = new Client();
const schema = `fynx_admin_account_safety_${process.pid}_${Date.now()}`;
const secret = "fynx-admin-account-safety-test-secret";
const tokens = {
  owner: { sub: "1", sv: 0 },
  admin: { sub: "2", sv: 0 },
  regular: { sub: "3", sv: 0 }
};
const jwtApi = {
  verify(token, receivedSecret) {
    assert.equal(receivedSecret, secret);
    if (!tokens[token]) throw new Error("invalid test JWT");
    return tokens[token];
  }
};

function response() {
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
const pool = {
  query(text, values) { return db.query(text, values); },
  async connect() {
    return { query(text, values) { return db.query(text, values); }, release() {} };
  }
};

async function invoke(token, userId, status, note = "") {
  const handlers = routes.get("PATCH /api/admin/accounts/:userId/status");
  assert.ok(handlers && handlers.length === 2, "Admin account-status route must be registered");
  const [authenticate, handler] = handlers;
  const req = {
    params: { userId },
    body: { status, note },
    get(name) { return name.toLowerCase() === "authorization" ? `Bearer ${token}` : ""; }
  };
  const res = response();
  await authenticate(req, res, () => handler(req, res));
  return res;
}

async function accountState(userId) {
  const result = await db.query(`
    SELECT u.session_version,
           fas.account_status AS platform_status,
           fas.status_note AS platform_note,
           fs.account_status AS settings_status,
           fs.status_note AS settings_note
    FROM users u
    LEFT JOIN fynx_account_safety fas ON fas.user_id=u.id
    LEFT JOIN safety_settings fs ON fs.user_id=u.id
    WHERE u.id=$1
  `, [userId]);
  assert.equal(result.rowCount, 1);
  return result.rows[0];
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
      (3,'regular-user','Regular User',0);
  `);

  registerAdminRoutes({ app, databasePool: pool, jwtApi, jwtSecret: secret });
  const dashboard = await invokeDashboard();
  assert.equal(dashboard.statusCode, 200);
  await db.query("INSERT INTO fynx_admin_roles(user_id,role) VALUES(2,'ADMIN')");

  const regularDenied = await invoke("regular", "3", "LOCKED");
  assert.equal(regularDenied.statusCode, 403, "ordinary accounts must not change account restrictions");

  const selfLock = await invoke("admin", "2", "LOCKED", "self lock must be prohibited");
  assert.equal(selfLock.statusCode, 400, "Admin must not lock its own active account");

  const adminCannotLockOwner = await invoke("admin", "1", "LOCKED", "owner protection");
  assert.equal(adminCannotLockOwner.statusCode, 403, "an ADMIN must not restrict the persisted OWNER");

  const invalidStatus = await invoke("admin", "3", "SUSPENDED");
  assert.equal(invalidStatus.statusCode, 400, "unsupported status values must be rejected");
  const overflowId = await invoke("admin", "9223372036854775808", "LOCKED");
  assert.equal(overflowId.statusCode, 400, "status route must reject an ID above PostgreSQL BIGINT");

  const lock = await invoke("admin", "3", "LOCKED", "Marketplace and group-message restriction");
  assert.equal(lock.statusCode, 200);
  assert.equal(lock.body.account.userId, "3");
  assert.equal(lock.body.account.status, "LOCKED");
  let state = await accountState("3");
  assert.equal(state.platform_status, "LOCKED");
  assert.equal(state.settings_status, "LOCKED");
  assert.equal(state.platform_note, "Marketplace and group-message restriction");
  assert.equal(state.settings_note, "Marketplace and group-message restriction");
  assert.equal(Number(state.session_version), 0, "status restriction must not sign the user out; that is the documented Admin action");

  const limited = await invoke("owner", "3", "LIMITED", "Temporary purchase and group-send restriction");
  assert.equal(limited.statusCode, 200);
  state = await accountState("3");
  assert.equal(state.platform_status, "LIMITED");
  assert.equal(state.settings_status, "LIMITED");
  assert.equal(state.platform_note, "Temporary purchase and group-send restriction");
  assert.equal(state.settings_note, "Temporary purchase and group-send restriction");

  const restore = await invoke("owner", "3", "ACTIVE", "Restriction cleared after review");
  assert.equal(restore.statusCode, 200);
  state = await accountState("3");
  assert.equal(state.platform_status, "ACTIVE");
  assert.equal(state.settings_status, "ACTIVE");
  assert.equal(state.platform_note, "Restriction cleared after review");
  assert.equal(state.settings_note, "Restriction cleared after review");
  assert.equal(Number(state.session_version), 0);

  const ownerCannotSelfModify = await invoke("owner", "1", "LOCKED");
  assert.equal(ownerCannotSelfModify.statusCode, 400, "even OWNER cannot change its own account status");

  // A failing audit write must roll back both status-store updates.
  await db.query(`
    CREATE FUNCTION reject_account_status_audit() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN
      IF NEW.action='ACCOUNT_STATUS_CHANGED' THEN
        RAISE EXCEPTION 'forced account-status audit failure';
      END IF;
      RETURN NEW;
    END;
    $$;
    CREATE TRIGGER reject_account_status_audit_trigger
      BEFORE INSERT ON fynx_admin_activity
      FOR EACH ROW EXECUTE FUNCTION reject_account_status_audit();
  `);
  const auditCountBefore = await db.query("SELECT COUNT(*)::int AS count FROM fynx_admin_activity WHERE action='ACCOUNT_STATUS_CHANGED'");
  const failedUpdate = await invoke("owner", "3", "LOCKED", "This state change must roll back");
  assert.equal(failedUpdate.statusCode, 500);
  state = await accountState("3");
  assert.equal(state.platform_status, "ACTIVE", "platform safety state must roll back when audit insertion fails");
  assert.equal(state.settings_status, "ACTIVE", "legacy safety state must roll back with platform safety state");
  assert.equal(state.platform_note, "Restriction cleared after review");
  assert.equal(state.settings_note, "Restriction cleared after review");
  const auditCountAfter = await db.query("SELECT COUNT(*)::int AS count FROM fynx_admin_activity WHERE action='ACCOUNT_STATUS_CHANGED'");
  assert.equal(auditCountAfter.rows[0].count, auditCountBefore.rows[0].count, "failed status change must not leave an audit event");
  await db.query("DROP TRIGGER reject_account_status_audit_trigger ON fynx_admin_activity; DROP FUNCTION reject_account_status_audit();");

  const successfulAudits = await db.query(
    "SELECT COUNT(*)::int AS count FROM fynx_admin_activity WHERE action='ACCOUNT_STATUS_CHANGED' AND target_type='user' AND target_id='3'"
  );
  assert.equal(successfulAudits.rows[0].count, 3, "LOCKED, LIMITED and ACTIVE changes must each have one committed audit row");

  console.log("ADMIN_ACCOUNT_SAFETY_ROUTE_DB=GREEN (role boundaries, self-lock protection, LOCKED/LIMITED/ACTIVE mirror writes, session preservation and atomic audit rollback)");
} finally {
  await db.query("SET search_path TO public").catch(() => {});
  await db.query(`DROP SCHEMA IF EXISTS "${schema}" CASCADE`).catch(() => {});
  await db.end();
}

async function invokeDashboard() {
  const handlers = routes.get("GET /api/admin/dashboard");
  assert.ok(handlers && handlers.length === 2, "Admin dashboard route must be registered");
  const [authenticate, handler] = handlers;
  const req = { get(name) { return name.toLowerCase() === "authorization" ? "Bearer owner" : ""; } };
  const res = response();
  await authenticate(req, res, () => handler(req, res));
  return res;
}
