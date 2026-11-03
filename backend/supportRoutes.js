import pg from "pg";
import jwt from "jsonwebtoken";
import { queueFynxNotification } from "./notificationPush.js";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: 4,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000,
  query_timeout: 20_000,
  keepAlive: true
}) : null;

const CATEGORIES = new Set([
  "ACCOUNT_ACCESS", "WALLET_PAYMENTS", "MESSAGES_CALLS", "MARKETPLACE_MY_SHOP",
  "STATUS_STORIES", "NOTIFICATIONS", "APP_PERFORMANCE", "OTHER"
]);
const STATUSES = new Set(["OPEN", "IN_REVIEW", "WAITING_USER", "RESOLVED"]);
let schemaPromise;

async function ensureSchema() {
  if (!pool) throw new Error("database not configured");
  if (!schemaPromise) schemaPromise = pool.query(`
    CREATE TABLE IF NOT EXISTS fynx_admin_roles (
      user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
      role TEXT NOT NULL CHECK(role IN ('ADMIN')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    ALTER TABLE fynx_admin_roles ADD COLUMN IF NOT EXISTS support_access BOOLEAN NOT NULL DEFAULT TRUE;
    ALTER TABLE fynx_admin_roles ALTER COLUMN support_access SET DEFAULT TRUE;

    CREATE TABLE IF NOT EXISTS fynx_support_cases (
      id BIGSERIAL PRIMARY KEY,
      case_number TEXT UNIQUE,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      subject TEXT NOT NULL,
      category TEXT NOT NULL,
      description TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_REVIEW','WAITING_USER','RESOLVED')),
      assigned_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
      client_request_id TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      resolved_at TIMESTAMPTZ
    );
    CREATE UNIQUE INDEX IF NOT EXISTS fynx_support_case_request_unique
      ON fynx_support_cases(user_id,client_request_id) WHERE client_request_id IS NOT NULL;
    CREATE INDEX IF NOT EXISTS fynx_support_cases_user_idx
      ON fynx_support_cases(user_id,updated_at DESC);
    CREATE INDEX IF NOT EXISTS fynx_support_cases_queue_idx
      ON fynx_support_cases(status,assigned_admin_id,updated_at DESC);

    CREATE TABLE IF NOT EXISTS fynx_support_messages (
      id BIGSERIAL PRIMARY KEY,
      case_id BIGINT NOT NULL REFERENCES fynx_support_cases(id) ON DELETE CASCADE,
      sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      sender_role TEXT NOT NULL CHECK(sender_role IN ('USER','OWNER','ADMIN')),
      visibility TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(visibility IN ('PUBLIC','INTERNAL')),
      message TEXT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    ALTER TABLE fynx_support_messages ADD COLUMN IF NOT EXISTS client_message_id TEXT;
    CREATE INDEX IF NOT EXISTS fynx_support_messages_case_idx
      ON fynx_support_messages(case_id,created_at ASC);
    CREATE UNIQUE INDEX IF NOT EXISTS fynx_support_messages_sender_request_unique
      ON fynx_support_messages(sender_id,client_message_id) WHERE client_message_id IS NOT NULL;

    CREATE TABLE IF NOT EXISTS fynx_support_case_events (
      id BIGSERIAL PRIMARY KEY,
      case_id BIGINT NOT NULL REFERENCES fynx_support_cases(id) ON DELETE CASCADE,
      actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
      actor_role TEXT NOT NULL CHECK(actor_role IN ('USER','OWNER','ADMIN','SYSTEM')),
      event_type TEXT NOT NULL,
      details JSONB NOT NULL DEFAULT '{}'::jsonb,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS fynx_support_events_case_idx
      ON fynx_support_case_events(case_id,created_at DESC);
  `).catch(error => { schemaPromise = undefined; throw error; });
  return schemaPromise;
}

function auth(req, res, next) {
  const header = req.get("authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || !JWT_SECRET) return res.status(401).json({ error: "authentication required" });
  try {
    req.user = jwt.verify(token, JWT_SECRET);
    return next();
  } catch {
    return res.status(401).json({ error: "invalid or expired token" });
  }
}

async function getRole(userId) {
  const first = (await pool.query("SELECT id FROM users ORDER BY id ASC LIMIT 1")).rows[0];
  if (first && String(first.id) === String(userId)) return { role: "OWNER", supportAccess: true };
  const admin = (await pool.query(
    "SELECT support_access FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1", [userId]
  )).rows[0];
  return admin ? { role: "ADMIN", supportAccess: Boolean(admin.support_access) } : { role: "USER", supportAccess: false };
}

async function requireSupport(req, res) {
  const access = await getRole(req.user.sub);
  if (access.role === "OWNER" || (access.role === "ADMIN" && access.supportAccess)) {
    req.supportRole = access.role;
    return access.role;
  }
  res.status(403).json({ error: "FYNX Support Centre access is not enabled for this account", code: "SUPPORT_ACCESS_REQUIRED" });
  return null;
}

async function requireOwner(req, res) {
  const access = await getRole(req.user.sub);
  if (access.role !== "OWNER") {
    res.status(403).json({ error: "FYNX owner access required", code: "OWNER_REQUIRED" });
    return false;
  }
  req.supportRole = "OWNER";
  return true;
}

function caseNumber(row) {
  if (row.case_number) return row.case_number;
  const year = row.case_year || new Date(row.created_at).getUTCFullYear();
  return `FYNX-${year}-${String(row.id).padStart(6, "0")}`;
}

function shapeCase(row) {
  return {
    id: String(row.id),
    caseNumber: caseNumber(row),
    userId: String(row.user_id),
    username: row.username || "",
    displayName: row.display_name || "",
    subject: row.subject,
    category: row.category,
    description: row.description,
    status: row.status,
    assignedAdminId: row.assigned_admin_id == null ? null : String(row.assigned_admin_id),
    assignedAdminUsername: row.assigned_admin_username || null,
    assignedAdminName: row.assigned_admin_name || null,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    lastMessageAt: row.last_message_at,
    resolvedAt: row.resolved_at || null
  };
}

const caseSelect = `
  SELECT c.*, TO_CHAR(c.created_at,'YYYY') AS case_year,
    u.username, u.display_name,
    a.username AS assigned_admin_username, a.display_name AS assigned_admin_name
  FROM fynx_support_cases c
  JOIN users u ON u.id=c.user_id
  LEFT JOIN users a ON a.id=c.assigned_admin_id
`;

async function findVisibleStaffCase(caseId, userId, role) {
  const result = await pool.query(`
    ${caseSelect}
    WHERE c.id=$1 AND ($2='OWNER' OR c.assigned_admin_id IS NULL OR c.assigned_admin_id=$3)
    LIMIT 1
  `, [caseId, role, userId]);
  return result.rows[0] || null;
}

async function loadMessages(caseId, includeInternal) {
  const result = await pool.query(`
    SELECT m.id,m.sender_id,m.sender_role,m.visibility,m.message,m.created_at,
      u.username,u.display_name
    FROM fynx_support_messages m
    JOIN users u ON u.id=m.sender_id
    WHERE m.case_id=$1 AND ($2::boolean=TRUE OR m.visibility='PUBLIC')
    ORDER BY m.created_at ASC,m.id ASC
    LIMIT 300
  `, [caseId, includeInternal]);
  return result.rows.map(row => ({
    id: String(row.id),
    senderId: String(row.sender_id),
    senderRole: row.sender_role,
    visibility: row.visibility,
    message: row.message,
    username: row.username || "",
    displayName: row.display_name || "",
    createdAt: row.created_at
  }));
}

async function notify(userId, title, message, caseId, sourceUsername = "fynx") {
  if (!userId) return;
  void queueFynxNotification(pool, {
    userId,
    type: "SUPPORT",
    title,
    message,
    targetId: String(caseId),
    sourceUsername,
    route: "fynx://home",
    notificationId: `support-${caseId}-${userId}-${Date.now()}`
  });
}

async function notifySupportTeam(caseId, title, message, assignedAdminId = null) {
  const recipients = new Set();
  const owner = (await pool.query("SELECT id FROM users ORDER BY id ASC LIMIT 1")).rows[0];
  if (owner) recipients.add(String(owner.id));
  if (assignedAdminId != null) recipients.add(String(assignedAdminId));
  else {
    const admins = await pool.query("SELECT user_id FROM fynx_admin_roles WHERE support_access=TRUE");
    admins.rows.forEach(row => recipients.add(String(row.user_id)));
  }
  for (const userId of recipients) await notify(userId, title, message, caseId);
}

async function recordEvent(client, { caseId, actorId, actorRole, eventType, details = {} }) {
  await client.query(
    "INSERT INTO fynx_support_case_events(case_id,actor_id,actor_role,event_type,details) VALUES($1,$2,$3,$4,$5::jsonb)",
    [caseId, actorId || null, actorRole, eventType, JSON.stringify(details)]
  );
}

export function registerSupportRoutes({ app }) {
  if (!app || !pool) return;

  app.get("/api/support/access", auth, async (req, res) => {
    try {
      await ensureSchema();
      const access = await getRole(req.user.sub);
      return res.json({ role: access.role, supportAccess: access.role === "OWNER" || (access.role === "ADMIN" && access.supportAccess) });
    } catch (error) {
      console.error("support access", error);
      return res.status(500).json({ error: "support access unavailable" });
    }
  });

  app.get("/api/support/cases", auth, async (req, res) => {
    try {
      await ensureSchema();
      const result = await pool.query(`
        ${caseSelect}
        WHERE c.user_id=$1
        ORDER BY c.updated_at DESC,c.id DESC
        LIMIT 50
      `, [req.user.sub]);
      return res.json({ cases: result.rows.map(shapeCase) });
    } catch (error) {
      console.error("my support cases", error);
      return res.status(500).json({ error: "support requests unavailable" });
    }
  });

  app.post("/api/support/cases", auth, async (req, res) => {
    const subject = typeof req.body?.subject === "string" ? req.body.subject.trim().slice(0, 120) : "";
    const category = typeof req.body?.category === "string" ? req.body.category.trim().toUpperCase() : "";
    const description = typeof req.body?.description === "string" ? req.body.description.trim().slice(0, 5000) : "";
    const requestKey = typeof req.body?.requestKey === "string" ? req.body.requestKey.trim().slice(0, 80) : "";
    if (subject.length < 3) return res.status(400).json({ error: "Please provide a subject of at least 3 characters." });
    if (!CATEGORIES.has(category)) return res.status(400).json({ error: "Choose a valid support category." });
    if (description.length < 10) return res.status(400).json({ error: "Please describe the problem in at least 10 characters." });
    if (!/^[A-Za-z0-9_-]{12,80}$/.test(requestKey)) return res.status(400).json({ error: "A valid request key is required. Please retry the submission." });
    try {
      await ensureSchema();
      const client = await pool.connect();
      let row;
      let created = false;
      try {
        await client.query("BEGIN");
        const inserted = await client.query(`
          INSERT INTO fynx_support_cases(user_id,subject,category,description,client_request_id)
          VALUES($1,$2,$3,$4,$5)
          ON CONFLICT(user_id,client_request_id) WHERE client_request_id IS NOT NULL DO NOTHING
          RETURNING *
        `, [req.user.sub, subject, category, description, requestKey]);
        if (inserted.rowCount) {
          row = inserted.rows[0];
          await client.query(
            "UPDATE fynx_support_cases SET case_number='FYNX-' || TO_CHAR(created_at,'YYYY') || '-' || LPAD(id::text,6,'0') WHERE id=$1",
            [row.id]
          );
          await recordEvent(client, { caseId: row.id, actorId: req.user.sub, actorRole: "USER", eventType: "CREATED", details: { category } });
          created = true;
        } else {
          row = (await client.query("SELECT * FROM fynx_support_cases WHERE user_id=$1 AND client_request_id=$2 LIMIT 1", [req.user.sub, requestKey])).rows[0];
        }
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
      const result = await pool.query(`${caseSelect} WHERE c.id=$1 LIMIT 1`, [row.id]);
      const supportCase = shapeCase(result.rows[0]);
      if (created) await notifySupportTeam(row.id, "New FYNX support request", `${supportCase.caseNumber}: ${subject}`);
      return res.status(created ? 201 : 200).json({ supportCase, duplicate: !created });
    } catch (error) {
      console.error("support case create", error);
      return res.status(500).json({ error: "Your support request could not be saved. Please retry." });
    }
  });

  app.get("/api/support/cases/:caseId", auth, async (req, res) => {
    try {
      await ensureSchema();
      const id = Number(req.params.caseId);
      if (!Number.isSafeInteger(id) || id < 1) return res.status(400).json({ error: "Invalid support case." });
      const result = await pool.query(`${caseSelect} WHERE c.id=$1 AND c.user_id=$2 LIMIT 1`, [id, req.user.sub]);
      const row = result.rows[0];
      if (!row) return res.status(404).json({ error: "Support request not found." });
      return res.json({ supportCase: shapeCase(row), messages: await loadMessages(id, false) });
    } catch (error) {
      console.error("support case detail", error);
      return res.status(500).json({ error: "Support conversation unavailable." });
    }
  });

  app.post("/api/support/cases/:caseId/messages", auth, async (req, res) => {
    const message = typeof req.body?.message === "string" ? req.body.message.trim().slice(0, 4000) : "";
    const requestKey = typeof req.body?.requestKey === "string" ? req.body.requestKey.trim().slice(0, 80) : "";
    if (message.length < 1) return res.status(400).json({ error: "Write a message before sending." });
    if (!/^[A-Za-z0-9_-]{12,80}$/.test(requestKey)) return res.status(400).json({ error: "A valid message request key is required." });
    const id = Number(req.params.caseId);
    if (!Number.isSafeInteger(id) || id < 1) return res.status(400).json({ error: "Invalid support case." });
    try {
      await ensureSchema();
      const client = await pool.connect();
      let updatedCase;
      let newMessageInserted = false;
      try {
        await client.query("BEGIN");
        const current = (await client.query("SELECT id,status,assigned_admin_id FROM fynx_support_cases WHERE id=$1 AND user_id=$2 FOR UPDATE", [id, req.user.sub])).rows[0];
        if (!current) {
          await client.query("ROLLBACK");
          return res.status(404).json({ error: "Support request not found." });
        }
        const insertedMessage = await client.query(
          "INSERT INTO fynx_support_messages(case_id,sender_id,sender_role,visibility,message,client_message_id) VALUES($1,$2,'USER','PUBLIC',$3,$4) ON CONFLICT(sender_id,client_message_id) WHERE client_message_id IS NOT NULL DO NOTHING RETURNING id",
          [id, req.user.sub, message, requestKey]
        );
        newMessageInserted = insertedMessage.rowCount > 0;
        if (newMessageInserted) {
          const nextStatus = current.status === "RESOLVED" ? "OPEN" : "IN_REVIEW";
          updatedCase = (await client.query(
            "UPDATE fynx_support_cases SET status=$1,resolved_at=NULL,updated_at=NOW(),last_message_at=NOW() WHERE id=$2 RETURNING id,status,assigned_admin_id",
            [nextStatus, id]
          )).rows[0];
          if (current.status === "RESOLVED") await recordEvent(client, { caseId: id, actorId: req.user.sub, actorRole: "USER", eventType: "REOPENED_BY_USER" });
          await recordEvent(client, { caseId: id, actorId: req.user.sub, actorRole: "USER", eventType: "USER_REPLIED" });
        } else {
          updatedCase = current;
        }
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
      if (newMessageInserted) await notifySupportTeam(id, "New reply on a FYNX support request", "A user has replied to a support case.", updatedCase.assigned_admin_id);
      const detail = await pool.query(`${caseSelect} WHERE c.id=$1 LIMIT 1`, [id]);
      return res.json({ supportCase: shapeCase(detail.rows[0]) });
    } catch (error) {
      console.error("user support reply", error);
      return res.status(500).json({ error: "Your reply could not be sent. Please try again." });
    }
  });

  app.get("/api/admin/support/admins", auth, async (req, res) => {
    try {
      await ensureSchema();
      if (!(await requireOwner(req, res))) return;
      const result = await pool.query(`
        SELECT u.id,u.username,u.display_name,r.support_access,r.created_at
        FROM fynx_admin_roles r JOIN users u ON u.id=r.user_id
        ORDER BY r.created_at ASC,u.id ASC LIMIT 100
      `);
      return res.json({ admins: result.rows.map(row => ({
        id: String(row.id), username: row.username, displayName: row.display_name || "",
        supportAccess: Boolean(row.support_access), grantedAt: row.created_at
      })) });
    } catch (error) {
      console.error("support admin list", error);
      return res.status(500).json({ error: "support team settings unavailable" });
    }
  });

  app.patch("/api/admin/support/admins/:userId/access", auth, async (req, res) => {
    const enabled = req.body?.enabled;
    const userId = Number(req.params.userId);
    if (typeof enabled !== "boolean" || !Number.isSafeInteger(userId) || userId < 1) {
      return res.status(400).json({ error: "A valid Admin and support permission are required." });
    }
    try {
      await ensureSchema();
      if (!(await requireOwner(req, res))) return;
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const updated = await client.query(
          "UPDATE fynx_admin_roles SET support_access=$1 WHERE user_id=$2 RETURNING user_id,support_access",
          [enabled, userId]
        );
        if (!updated.rowCount) {
          await client.query("ROLLBACK");
          return res.status(404).json({ error: "Admin account not found." });
        }
        if (!enabled) {
          const unassigned = await client.query(
            "UPDATE fynx_support_cases SET assigned_admin_id=NULL,updated_at=NOW() WHERE assigned_admin_id=$1 AND status<>'RESOLVED' RETURNING id",
            [userId]
          );
          for (const row of unassigned.rows) {
            await recordEvent(client, { caseId: row.id, actorId: req.user.sub, actorRole: "OWNER", eventType: "ADMIN_ACCESS_REVOKED_AND_CASE_UNASSIGNED", details: { adminId: String(userId) } });
          }
        }
        await client.query("COMMIT");
        return res.json({ admin: { userId: String(updated.rows[0].user_id), supportAccess: Boolean(updated.rows[0].support_access) } });
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
    } catch (error) {
      console.error("support admin access update", error);
      return res.status(500).json({ error: "support permission could not be updated." });
    }
  });

  app.get("/api/admin/support/cases", auth, async (req, res) => {
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const status = typeof req.query?.status === "string" ? req.query.status.trim().toUpperCase() : "";
      if (status && !STATUSES.has(status)) return res.status(400).json({ error: "Invalid support status." });
      const values = [role, req.user.sub];
      let where = "($1='OWNER' OR c.assigned_admin_id IS NULL OR c.assigned_admin_id=$2)";
      if (status) {
        values.push(status);
        where += ` AND c.status=$${values.length}`;
      }
      const result = await pool.query(`
        ${caseSelect}
        WHERE ${where}
        ORDER BY CASE c.status WHEN 'OPEN' THEN 0 WHEN 'IN_REVIEW' THEN 1 WHEN 'WAITING_USER' THEN 2 ELSE 3 END,
          c.updated_at DESC,c.id DESC LIMIT 100
      `, values);
      const countsResult = await pool.query(`
        SELECT
          COUNT(*) FILTER (WHERE status='OPEN')::int AS open,
          COUNT(*) FILTER (WHERE status='IN_REVIEW')::int AS in_review,
          COUNT(*) FILTER (WHERE status='WAITING_USER')::int AS waiting_user,
          COUNT(*) FILTER (WHERE status='RESOLVED')::int AS resolved,
          COUNT(*) FILTER (WHERE assigned_admin_id IS NULL AND status<>'RESOLVED')::int AS unassigned
        FROM fynx_support_cases c
        WHERE ($1='OWNER' OR c.assigned_admin_id IS NULL OR c.assigned_admin_id=$2)
      `, [role, req.user.sub]);
      return res.json({ cases: result.rows.map(shapeCase), counts: countsResult.rows[0] || {} });
    } catch (error) {
      console.error("support inbox", error);
      return res.status(500).json({ error: "support inbox unavailable" });
    }
  });

  app.get("/api/admin/support/cases/:caseId", auth, async (req, res) => {
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const id = Number(req.params.caseId);
      if (!Number.isSafeInteger(id) || id < 1) return res.status(400).json({ error: "Invalid support case." });
      const row = await findVisibleStaffCase(id, req.user.sub, role);
      if (!row) return res.status(404).json({ error: "Support request not found." });
      const events = await pool.query(`
        SELECT e.event_type,e.actor_role,e.details,e.created_at,u.username
        FROM fynx_support_case_events e
        LEFT JOIN users u ON u.id=e.actor_id
        WHERE e.case_id=$1 ORDER BY e.created_at DESC,e.id DESC LIMIT 50
      `, [id]);
      return res.json({
        supportCase: shapeCase(row),
        messages: await loadMessages(id, true),
        events: events.rows.map(event => ({ type: event.event_type, actorRole: event.actor_role, username: event.username || "", details: event.details || {}, createdAt: event.created_at }))
      });
    } catch (error) {
      console.error("support staff case detail", error);
      return res.status(500).json({ error: "support case details unavailable" });
    }
  });

  app.post("/api/admin/support/cases/:caseId/claim", auth, async (req, res) => {
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      if (role === "OWNER") return res.status(400).json({ error: "The Owner assigns cases rather than claiming them." });
      const id = Number(req.params.caseId);
      if (!Number.isSafeInteger(id) || id < 1) return res.status(400).json({ error: "Invalid support case." });
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const updated = await client.query(
          "UPDATE fynx_support_cases SET assigned_admin_id=$1,updated_at=NOW() WHERE id=$2 AND assigned_admin_id IS NULL AND status<>'RESOLVED' RETURNING id",
          [req.user.sub, id]
        );
        if (!updated.rowCount) {
          const exists = await client.query("SELECT 1 FROM fynx_support_cases WHERE id=$1", [id]);
          await client.query("ROLLBACK");
          return res.status(exists.rowCount ? 409 : 404).json({ error: exists.rowCount ? "This case has already been claimed or resolved." : "Support request not found." });
        }
        await recordEvent(client, { caseId: id, actorId: req.user.sub, actorRole: "ADMIN", eventType: "CLAIMED" });
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
      return res.json({ claimed: true });
    } catch (error) {
      console.error("support case claim", error);
      return res.status(500).json({ error: "Support case could not be claimed." });
    }
  });

  app.patch("/api/admin/support/cases/:caseId/assign", auth, async (req, res) => {
    const assignedAdminId = req.body?.adminUserId == null ? null : Number(req.body.adminUserId);
    if (assignedAdminId !== null && (!Number.isSafeInteger(assignedAdminId) || assignedAdminId < 1)) {
      return res.status(400).json({ error: "Invalid Admin account." });
    }
    try {
      await ensureSchema();
      if (!(await requireOwner(req, res))) return;
      const id = Number(req.params.caseId);
      if (!Number.isSafeInteger(id) || id < 1) return res.status(400).json({ error: "Invalid support case." });
      if (assignedAdminId !== null) {
        const eligible = await pool.query("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 AND support_access=TRUE", [assignedAdminId]);
        if (!eligible.rowCount) return res.status(400).json({ error: "Choose an Admin who has Support Centre access." });
      }
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const current = (await client.query("SELECT assigned_admin_id FROM fynx_support_cases WHERE id=$1 FOR UPDATE", [id])).rows[0];
        if (!current) {
          await client.query("ROLLBACK");
          return res.status(404).json({ error: "Support request not found." });
        }
        await client.query("UPDATE fynx_support_cases SET assigned_admin_id=$1,updated_at=NOW() WHERE id=$2", [assignedAdminId, id]);
        await recordEvent(client, { caseId: id, actorId: req.user.sub, actorRole: "OWNER", eventType: "ASSIGNED", details: { fromAdminId: current.assigned_admin_id == null ? null : String(current.assigned_admin_id), toAdminId: assignedAdminId == null ? null : String(assignedAdminId) } });
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
      if (assignedAdminId !== null) await notify(assignedAdminId, "Support case assigned to you", "A FYNX support request has been assigned to your queue.", id);
      return res.json({ assignedAdminId: assignedAdminId == null ? null : String(assignedAdminId) });
    } catch (error) {
      console.error("support case assignment", error);
      return res.status(500).json({ error: "Support case assignment failed." });
    }
  });

  app.patch("/api/admin/support/cases/:caseId/status", auth, async (req, res) => {
    const status = typeof req.body?.status === "string" ? req.body.status.trim().toUpperCase() : "";
    if (!STATUSES.has(status)) return res.status(400).json({ error: "Invalid support status." });
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const id = Number(req.params.caseId);
      if (!Number.isSafeInteger(id) || id < 1) return res.status(400).json({ error: "Invalid support case." });
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const current = (await client.query("SELECT id,status,assigned_admin_id FROM fynx_support_cases WHERE id=$1 FOR UPDATE", [id])).rows[0];
        if (!current) {
          await client.query("ROLLBACK");
          return res.status(404).json({ error: "Support request not found." });
        }
        if (role !== "OWNER" && String(current.assigned_admin_id) !== String(req.user.sub)) {
          await client.query("ROLLBACK");
          return res.status(403).json({ error: "Claim this unassigned case before changing its status." });
        }
        await client.query(
          "UPDATE fynx_support_cases SET status=$1,resolved_at=CASE WHEN $1='RESOLVED' THEN NOW() ELSE NULL END,updated_at=NOW() WHERE id=$2",
          [status, id]
        );
        await recordEvent(client, { caseId: id, actorId: req.user.sub, actorRole: role, eventType: "STATUS_CHANGED", details: { from: current.status, to: status } });
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
      const detail = await pool.query(`${caseSelect} WHERE c.id=$1 LIMIT 1`, [id]);
      const supportCase = shapeCase(detail.rows[0]);
      await notify(Number(supportCase.userId), status === "RESOLVED" ? "FYNX support request resolved" : "FYNX support request updated", status === "RESOLVED" ? "Your support request has been marked resolved. Reply to reopen it if the problem remains." : `Your support request is now ${status.replaceAll("_", " ").toLowerCase()}.`, id);
      return res.json({ supportCase });
    } catch (error) {
      console.error("support status update", error);
      return res.status(500).json({ error: "Support status could not be updated." });
    }
  });

  app.post("/api/admin/support/cases/:caseId/messages", auth, async (req, res) => {
    const message = typeof req.body?.message === "string" ? req.body.message.trim().slice(0, 4000) : "";
    const visibility = typeof req.body?.visibility === "string" ? req.body.visibility.trim().toUpperCase() : "PUBLIC";
    const requestKey = typeof req.body?.requestKey === "string" ? req.body.requestKey.trim().slice(0, 80) : "";
    if (!message) return res.status(400).json({ error: "Write a message before sending." });
    if (!["PUBLIC", "INTERNAL"].includes(visibility)) return res.status(400).json({ error: "Invalid message visibility." });
    if (!/^[A-Za-z0-9_-]{12,80}$/.test(requestKey)) return res.status(400).json({ error: "A valid message request key is required." });
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const id = Number(req.params.caseId);
      if (!Number.isSafeInteger(id) || id < 1) return res.status(400).json({ error: "Invalid support case." });
      const client = await pool.connect();
      let userId;
      let newMessageInserted = false;
      try {
        await client.query("BEGIN");
        const current = (await client.query("SELECT id,user_id,status,assigned_admin_id FROM fynx_support_cases WHERE id=$1 FOR UPDATE", [id])).rows[0];
        if (!current) {
          await client.query("ROLLBACK");
          return res.status(404).json({ error: "Support request not found." });
        }
        if (role !== "OWNER" && String(current.assigned_admin_id) !== String(req.user.sub)) {
          await client.query("ROLLBACK");
          return res.status(403).json({ error: "Claim or request assignment before replying to this case." });
        }
        userId = current.user_id;
        const insertedMessage = await client.query(
          "INSERT INTO fynx_support_messages(case_id,sender_id,sender_role,visibility,message,client_message_id) VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(sender_id,client_message_id) WHERE client_message_id IS NOT NULL DO NOTHING RETURNING id",
          [id, req.user.sub, role, visibility, message, requestKey]
        );
        newMessageInserted = insertedMessage.rowCount > 0;
        if (newMessageInserted && visibility === "PUBLIC") {
          await client.query(
            "UPDATE fynx_support_cases SET status='WAITING_USER',resolved_at=NULL,updated_at=NOW(),last_message_at=NOW() WHERE id=$1",
            [id]
          );
          await recordEvent(client, { caseId: id, actorId: req.user.sub, actorRole: role, eventType: "STAFF_REPLIED" });
        } else if (newMessageInserted) {
          await client.query("UPDATE fynx_support_cases SET updated_at=NOW() WHERE id=$1", [id]);
          await recordEvent(client, { caseId: id, actorId: req.user.sub, actorRole: role, eventType: "INTERNAL_NOTE_ADDED" });
        }
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
      if (visibility === "PUBLIC" && newMessageInserted) await notify(Number(userId), "FYNX Support replied", "You have a new private reply about your support request.", id);
      const detail = await pool.query(`${caseSelect} WHERE c.id=$1 LIMIT 1`, [id]);
      return res.json({ supportCase: shapeCase(detail.rows[0]) });
    } catch (error) {
      console.error("staff support reply", error);
      return res.status(500).json({ error: "The support message could not be sent." });
    }
  });
}
