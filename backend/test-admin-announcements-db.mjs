import assert from "node:assert/strict";
import pg from "pg";
import { registerAdminRoutes } from "./adminRoutes.js";

const { Client } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");

const db = new Client();
const schema = `fynx_admin_routes_${process.pid}_${Date.now()}`;
const secret = "fynx-admin-route-integration-test-secret";
const tokens = {
  owner: { sub: "1", sv: 0 },
  admin: { sub: "2", sv: 0 },
  regular: { sub: "3", sv: 0 },
  largeIdUser: { sub: "9007199254740993", sv: 0 },
  staleAdmin: { sub: "2", sv: 9 }
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
    ended: false,
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
    set(name, value) { this.headers[name.toLowerCase()] = value; return this; },
    end() { this.ended = true; return this; }
  };
}

const routes = new Map();
const app = {};
for (const method of ["get", "post", "patch", "delete"]) {
  app[method] = (path, ...handlers) => {
    routes.set(`${method.toUpperCase()} ${path}`, handlers);
  };
}

async function invoke(method, path, {
  token = "admin",
  params = {},
  query = {},
  body = {}
} = {}) {
  const handlers = routes.get(`${method.toUpperCase()} ${path}`);
  assert.ok(handlers && handlers.length === 2, `route must be registered: ${method} ${path}`);
  const [authenticate, handler] = handlers;
  const authorization = token ? `Bearer ${token}` : "";
  const req = {
    params,
    query,
    body,
    get(name) {
      return name.toLowerCase() === "authorization" ? authorization : "";
    }
  };
  const res = createResponse();
  await authenticate(req, res, () => handler(req, res));
  return res;
}

const pool = {
  query(text, values) { return db.query(text, values); },
  async connect() {
    return {
      query(text, values) { return db.query(text, values); },
      release() {}
    };
  }
};

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
      (3,'regular-user','Regular User',0),
      (4,'another-user','Another User',0),
      (9007199254740993,'large-decimal-id','Large Decimal ID',0);
    INSERT INTO message_media(id,owner_id,mime_type) VALUES
      (201,2,'image/jpeg'),
      (202,4,'image/png'),
      (203,2,'application/pdf');
  `);

  registerAdminRoutes({ app, databasePool: pool, jwtApi, jwtSecret: secret });

  // The first authenticated Owner request initializes the production Admin schema.
  const dashboard = await invoke("GET", "/api/admin/dashboard", { token: "owner" });
  assert.equal(dashboard.statusCode, 200, "persisted Owner must enter the real Admin dashboard route");
  assert.equal(dashboard.body?.role, "OWNER");

  await db.query("INSERT INTO fynx_admin_roles(user_id,role) VALUES(2,'ADMIN')");

  const missingAuth = await invoke("GET", "/api/admin/announcements", { token: "" });
  assert.equal(missingAuth.statusCode, 401, "Admin route must reject missing bearer credentials");
  const regularUser = await invoke("GET", "/api/admin/announcements", { token: "regular" });
  assert.equal(regularUser.statusCode, 403, "ordinary accounts must not access Admin announcement history");
  const staleSession = await invoke("GET", "/api/admin/announcements", { token: "staleAdmin" });
  assert.equal(staleSession.statusCode, 401, "revoked Admin sessions must be rejected before route execution");

  // Grant/revoke tests verify the Group 5 exact BIGINT behavior on real routes.
  const grantLarge = await invoke("POST", "/api/admin/admins", {
    token: "owner",
    body: { userId: "9007199254740993" }
  });
  assert.equal(grantLarge.statusCode, 201);
  assert.equal(grantLarge.body?.userId, "9007199254740993", "large target IDs must remain exact decimal strings");

  const grantInvalid = await invoke("POST", "/api/admin/admins", {
    token: "owner",
    body: { userId: "9223372036854775808" }
  });
  assert.equal(grantInvalid.statusCode, 400, "grant must reject PostgreSQL BIGINT overflow before SQL");
  const grantNonCanonical = await invoke("POST", "/api/admin/admins", {
    token: "owner",
    body: { userId: "01" }
  });
  assert.equal(grantNonCanonical.statusCode, 400, "grant must reject non-canonical IDs");

  const revokeLarge = await invoke("DELETE", "/api/admin/admins/:userId", {
    token: "owner",
    params: { userId: "9007199254740993" }
  });
  assert.equal(revokeLarge.statusCode, 200);
  assert.equal(revokeLarge.body?.userId, "9007199254740993", "revoke must preserve the exact large ID");
  const revokeInvalid = await invoke("DELETE", "/api/admin/admins/:userId", {
    token: "owner",
    params: { userId: "9223372036854775808" }
  });
  assert.equal(revokeInvalid.statusCode, 400, "revoke must reject PostgreSQL BIGINT overflow");

  const future = new Date(Date.now() + 60 * 60 * 1000).toISOString();
  const createScheduled = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: {
      title: "Scheduled FYNX notice",
      body: "This announcement becomes public at its confirmed future time.",
      priority: "IMPORTANT",
      scheduledAt: future,
      published: true
    }
  });
  assert.equal(createScheduled.statusCode, 201, "Admin must create a future scheduled announcement");
  const scheduledId = createScheduled.body?.announcement?.id;
  assert.ok(scheduledId, "create response must return the persisted announcement ID");
  assert.ok(createScheduled.body.announcement.scheduledAt, "scheduled timestamp must persist");

  const publicBeforeSchedule = await invoke("GET", "/api/announcements", { token: "regular" });
  assert.equal(publicBeforeSchedule.statusCode, 200);
  assert.ok(
    !publicBeforeSchedule.body.announcements.some(item => item.id === scheduledId),
    "future scheduled announcement must remain absent from the public feed"
  );

  const adminHistory = await invoke("GET", "/api/admin/announcements", {
    token: "admin",
    query: { limit: 50, offset: 0 }
  });
  assert.equal(adminHistory.statusCode, 200);
  assert.equal(adminHistory.headers["cache-control"], "private, no-store");
  assert.ok(adminHistory.body.announcements.some(item => item.id === scheduledId), "Admin history must retain the scheduled item");

  const invalidDate = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: { title: "Bad date", body: "This date is impossible.", scheduledAt: "2026-02-30T12:00:00Z" }
  });
  assert.equal(invalidDate.statusCode, 400, "impossible calendar dates must be rejected before insert");

  const invalidLink = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: { title: "Bad link", body: "HTTP links are not accepted.", linkUrl: "http://example.com" }
  });
  assert.equal(invalidLink.statusCode, 400, "non-HTTPS announcement destinations must be rejected");

  const credentialLink = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: { title: "Credential link", body: "Embedded credentials are not accepted.", linkUrl: "https://user:pass@example.com/path" }
  });
  assert.equal(credentialLink.statusCode, 400, "HTTPS URLs with embedded credentials must be rejected");

  const foreignArtwork = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: { title: "Foreign image", body: "An Admin cannot attach another account's upload.", mediaId: "202" }
  });
  assert.equal(foreignArtwork.statusCode, 403, "announcement artwork must belong to the current Admin");

  const nonImageArtwork = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: { title: "Document upload", body: "Only image media may be attached.", mediaId: "203" }
  });
  assert.equal(nonImageArtwork.statusCode, 403, "non-image media must not be accepted as announcement artwork");

  const validImage = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: {
      title: "Announcement with artwork",
      body: "Own image artwork is accepted.",
      mediaId: "201",
      linkUrl: "https://fynx.example/updates"
    }
  });
  assert.equal(validImage.statusCode, 201, "Admin may attach its own image and a safe HTTPS link");
  const imageAnnouncementId = validImage.body.announcement.id;

  const publishNow = await invoke("PATCH", "/api/admin/announcements/:announcementId", {
    token: "admin",
    params: { announcementId: scheduledId },
    body: { published: true, scheduledAt: null }
  });
  assert.equal(publishNow.statusCode, 200, "Admin must be able to cancel schedule and publish immediately");
  const publicAfterPublish = await invoke("GET", "/api/announcements", { token: "regular" });
  assert.ok(publicAfterPublish.body.announcements.some(item => item.id === scheduledId));

  const unpublish = await invoke("PATCH", "/api/admin/announcements/:announcementId", {
    token: "admin",
    params: { announcementId: scheduledId },
    body: { published: false, scheduledAt: null }
  });
  assert.equal(unpublish.statusCode, 200);
  const publicAfterUnpublish = await invoke("GET", "/api/announcements", { token: "regular" });
  assert.ok(!publicAfterUnpublish.body.announcements.some(item => item.id === scheduledId));

  const successfulEdit = await invoke("PATCH", "/api/admin/announcements/:announcementId", {
    token: "admin",
    params: { announcementId: imageAnnouncementId },
    body: { title: "Announcement with artwork updated", published: true }
  });
  assert.equal(successfulEdit.statusCode, 200, "Admin must be able to edit an existing announcement");
  const editedRow = await db.query("SELECT title FROM fynx_announcements WHERE id=$1", [imageAnnouncementId]);
  assert.equal(editedRow.rows[0].title, "Announcement with artwork updated");

  // Force audit failure: the announcement mutation and its audit row must roll back together.
  await db.query(`
    CREATE FUNCTION reject_admin_audit() RETURNS trigger LANGUAGE plpgsql AS $$
    BEGIN RAISE EXCEPTION 'forced Admin audit failure'; END;
    $$;
    CREATE TRIGGER reject_admin_audit_trigger
      BEFORE INSERT ON fynx_admin_activity
      FOR EACH ROW EXECUTE FUNCTION reject_admin_audit();
  `);
  const beforeFailedCreate = await db.query("SELECT COUNT(*)::int AS count FROM fynx_announcements");
  const failedCreate = await invoke("POST", "/api/admin/announcements", {
    token: "admin",
    body: { title: "Must roll back", body: "Audit failure must abort announcement creation." }
  });
  assert.equal(failedCreate.statusCode, 500, "audit database failure must surface as create failure");
  const afterFailedCreate = await db.query("SELECT COUNT(*)::int AS count FROM fynx_announcements");
  assert.equal(afterFailedCreate.rows[0].count, beforeFailedCreate.rows[0].count, "failed create must not leave an announcement row");

  const failedEdit = await invoke("PATCH", "/api/admin/announcements/:announcementId", {
    token: "admin",
    params: { announcementId: imageAnnouncementId },
    body: { title: "Must not persist", published: true }
  });
  assert.equal(failedEdit.statusCode, 500, "audit failure must abort announcement edits");
  const unchanged = await db.query("SELECT title FROM fynx_announcements WHERE id=$1", [imageAnnouncementId]);
  assert.equal(unchanged.rows[0].title, "Announcement with artwork updated", "failed edit must restore the last committed content");
  await db.query("DROP TRIGGER reject_admin_audit_trigger ON fynx_admin_activity; DROP FUNCTION reject_admin_audit();");

  // Exercise both pages against a real PostgreSQL dataset larger than one Admin page.
  await db.query(`
    INSERT INTO fynx_announcements(title,body,priority,published,author_id,published_at)
    SELECT 'Page item ' || n, 'Pagination integration row', 'NORMAL', TRUE, 2, NOW() - (n || ' seconds')::interval
    FROM generate_series(1,55) AS n
  `);
  const pageOne = await invoke("GET", "/api/admin/announcements", {
    token: "admin",
    query: { limit: 50, offset: 0 }
  });
  const pageTwo = await invoke("GET", "/api/admin/announcements", {
    token: "admin",
    query: { limit: 50, offset: 50 }
  });
  assert.equal(pageOne.body.announcements.length, 50);
  assert.equal(pageOne.body.hasMore, true);
  assert.equal(pageTwo.body.offset, 50);
  assert.ok(pageTwo.body.announcements.length > 0);
  const firstIds = new Set(pageOne.body.announcements.map(item => item.id));
  assert.ok(pageTwo.body.announcements.every(item => !firstIds.has(item.id)), "stable pages must not overlap");

  const adminAudit = await db.query(
    "SELECT action FROM fynx_admin_activity WHERE target_type='announcement' ORDER BY id"
  );
  assert.ok(adminAudit.rows.some(row => row.action === "ANNOUNCEMENT_SCHEDULED"));
  assert.ok(adminAudit.rows.some(row => row.action === "ANNOUNCEMENT_PUBLISHED"));
  assert.ok(adminAudit.rows.some(row => row.action === "ANNOUNCEMENT_UNPUBLISHED"));
  assert.ok(adminAudit.rows.some(row => row.action === "ANNOUNCEMENT_UPDATED"));

  console.log("ADMIN_ANNOUNCEMENT_ROUTE_DB=GREEN (auth roles, exact IDs, scheduled/public visibility, image ownership, HTTPS links, publication, pagination, and atomic audit rollback)");
} finally {
  await db.query("SET search_path TO public").catch(() => {});
  await db.query(`DROP SCHEMA IF EXISTS "${schema}" CASCADE`).catch(() => {});
  await db.end();
}
