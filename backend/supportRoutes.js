import { queueFynxNotification } from "./notificationPush.js";

const CATEGORIES = new Set([
  "ACCOUNT_LOGIN", "MESSAGES_CALLS", "WALLET_PAYMENTS", "MARKETPLACE_SHOP",
  "STATUS_STORIES", "PERFORMANCE_CRASHES", "NOTIFICATIONS", "OTHER"
]);
const STATUSES = new Set(["OPEN", "IN_REVIEW", "WAITING_FOR_USER", "WAITING_FOR_SUPPORT", "RESOLVED"]);

export function registerSupportRoutes({ app, pool, auth }) {
  if (!app || !pool || !auth) return;
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_admin_roles (
        user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
        role TEXT NOT NULL CHECK(role IN ('ADMIN')),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS fynx_support_cases (
        id BIGSERIAL PRIMARY KEY,
        case_number TEXT UNIQUE,
        requester_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        category TEXT NOT NULL,
        subject TEXT NOT NULL,
        description TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_REVIEW','WAITING_FOR_USER','WAITING_FOR_SUPPORT','RESOLVED')),
        assigned_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
        client_request_id TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        resolved_at TIMESTAMPTZ
      );
      CREATE UNIQUE INDEX IF NOT EXISTS fynx_support_case_request_once
        ON fynx_support_cases(requester_id, client_request_id) WHERE client_request_id IS NOT NULL;
      CREATE INDEX IF NOT EXISTS fynx_support_cases_requester_idx ON fynx_support_cases(requester_id, created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_support_cases_assignment_idx ON fynx_support_cases(assigned_admin_id, status, updated_at DESC);
      CREATE TABLE IF NOT EXISTS fynx_support_messages (
        id BIGSERIAL PRIMARY KEY,
        case_id BIGINT NOT NULL REFERENCES fynx_support_cases(id) ON DELETE CASCADE,
        sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        body TEXT NOT NULL,
        visibility TEXT NOT NULL DEFAULT 'PUBLIC' CHECK(visibility IN ('PUBLIC','INTERNAL')),
        client_message_id TEXT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE UNIQUE INDEX IF NOT EXISTS fynx_support_message_request_once
        ON fynx_support_messages(case_id, sender_id, client_message_id) WHERE client_message_id IS NOT NULL;
      CREATE INDEX IF NOT EXISTS fynx_support_messages_case_idx ON fynx_support_messages(case_id, created_at ASC);
      CREATE TABLE IF NOT EXISTS fynx_support_attachments (
        id BIGSERIAL PRIMARY KEY,
        case_id BIGINT NOT NULL REFERENCES fynx_support_cases(id) ON DELETE CASCADE,
        media_id BIGINT NOT NULL REFERENCES message_media(id) ON DELETE CASCADE,
        uploader_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        UNIQUE(case_id, media_id)
      );
      CREATE INDEX IF NOT EXISTS fynx_support_attachments_case_idx ON fynx_support_attachments(case_id, created_at ASC);
      CREATE TABLE IF NOT EXISTS fynx_support_case_events (
        id BIGSERIAL PRIMARY KEY,
        case_id BIGINT NOT NULL REFERENCES fynx_support_cases(id) ON DELETE CASCADE,
        actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
        event_type TEXT NOT NULL,
        details JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS fynx_support_case_events_idx ON fynx_support_case_events(case_id, created_at DESC);
    `).catch(error => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };

  const officialOwnerId = async () => {
    const officialUsername = (process.env.FYNX_OFFICIAL_USERNAME || "fynx").trim().toLowerCase();
    const result = await pool.query("SELECT id FROM users WHERE lower(username)=$1 LIMIT 1", [officialUsername]);
    return result.rows[0]?.id == null ? null : String(result.rows[0].id);
  };
  const resolveRole = async userId => {
    const ownerId = await officialOwnerId();
    if (ownerId && ownerId === String(userId)) return "OWNER";
    const admin = await pool.query("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 AND role='ADMIN' LIMIT 1", [userId]);
    return admin.rowCount ? "ADMIN" : null;
  };
  const requireSupport = async (req, res) => {
    const role = await resolveRole(req.user.sub);
    if (!role) { res.status(403).json({ error: "support administrator access required", code: "SUPPORT_ADMIN_REQUIRED" }); return null; }
    req.supportRole = role;
    return role;
  };
  const notify = async (userIds, payload) => {
    const unique = [...new Set(userIds.filter(value => value != null && String(value).trim() !== "").map(String))];
    await Promise.allSettled(unique.map(userId => queueFynxNotification(pool, {
      userId, type: "SUPPORT", title: payload.title, message: payload.message,
      targetId: payload.caseId, route: `fynx://support/case/${payload.caseId}`,
      notificationId: `support-${payload.eventId}-${userId}`
    })));
  };
  const caseIdFrom = value => {
    const id = Number(value);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  };
  const adminCanSeeCase = async (role, userId, caseId) => {
    const result = await pool.query("SELECT requester_id,assigned_admin_id,status FROM fynx_support_cases WHERE id=$1", [caseId]);
    const row = result.rows[0];
    if (!row) return { row: null, allowed: false };
    return { row, allowed: role === "OWNER" || row.assigned_admin_id == null || String(row.assigned_admin_id) === String(userId) };
  };
  const canAccessAttachments = async (req, caseId) => {
    const row = (await pool.query("SELECT requester_id FROM fynx_support_cases WHERE id=$1", [caseId])).rows[0];
    if (!row) return { exists: false, allowed: false };
    if (String(row.requester_id) === String(req.user.sub)) return { exists: true, allowed: true };
    const role = await resolveRole(req.user.sub);
    if (!role) return { exists: true, allowed: false };
    const access = await adminCanSeeCase(role, req.user.sub, caseId);
    return { exists: true, allowed: access.allowed };
  };

  app.post("/api/support/cases", auth, async (req, res) => {
    let client;
    try {
      client = await pool.connect();
      await ensureSchema();
      const category = String(req.body?.category || "").trim().toUpperCase();
      const subject = typeof req.body?.subject === "string" ? req.body.subject.trim().slice(0, 140) : "";
      const description = typeof req.body?.description === "string" ? req.body.description.trim().slice(0, 5000) : "";
      const requestId = typeof req.body?.clientRequestId === "string" ? req.body.clientRequestId.trim().slice(0, 100) : "";
      if (!CATEGORIES.has(category)) return res.status(400).json({ error: "Choose a valid support category." });
      if (subject.length < 3 || description.length < 10) return res.status(400).json({ error: "A subject and a description of at least 10 characters are required." });
      if (!requestId) return res.status(400).json({ error: "A request ID is required to prevent duplicate submissions." });
      await client.query("BEGIN");
      const inserted = await client.query(`
        INSERT INTO fynx_support_cases(requester_id,category,subject,description,client_request_id)
        VALUES($1,$2,$3,$4,$5)
        ON CONFLICT(requester_id,client_request_id) WHERE client_request_id IS NOT NULL DO NOTHING
        RETURNING id,requester_id,category,subject,description,status,created_at,updated_at
      `, [req.user.sub, category, subject, description, requestId]);
      let row = inserted.rows[0];
      if (!row) {
        const existing = await client.query("SELECT id,requester_id,category,subject,description,status,created_at,updated_at FROM fynx_support_cases WHERE requester_id=$1 AND client_request_id=$2", [req.user.sub, requestId]);
        row = existing.rows[0];
      } else {
        await client.query("UPDATE fynx_support_cases SET case_number='FYNX-' || LPAD(id::text,8,'0') WHERE id=$1", [row.id]);
        await client.query("INSERT INTO fynx_support_messages(case_id,sender_id,body,visibility,client_message_id) VALUES($1,$2,$3,'PUBLIC',$4)", [row.id, req.user.sub, description, `initial-${requestId}`]);
        await client.query("INSERT INTO fynx_support_case_events(case_id,actor_id,event_type,details) VALUES($1,$2,'CREATED',jsonb_build_object('category',$3::text))", [row.id, req.user.sub, category]);
      }
      await client.query("COMMIT");
      const full = await pool.query("SELECT id,case_number,category,subject,description,status,assigned_admin_id,created_at,updated_at FROM fynx_support_cases WHERE id=$1", [row.id]);
      if (inserted.rows[0]) {
        const owner = await officialOwnerId();
        const admins = (await pool.query("SELECT user_id FROM fynx_admin_roles")).rows.map(x => x.user_id);
        await notify([owner, ...admins], { caseId: row.id, eventId: `created-${row.id}`, title: "New FYNX support request", message: `${full.rows[0].case_number}: ${subject}` });
      }
      return res.status(inserted.rows[0] ? 201 : 200).json({ case: full.rows[0] });
    } catch (error) {
      if (client) await client.query("ROLLBACK").catch(() => {});
      console.error("support case create", error);
      return res.status(500).json({ error: "Support request could not be saved. Please try again." });
    } finally { client?.release(); }
  });

  app.get("/api/support/cases/mine", auth, async (req, res) => {
    try {
      await ensureSchema();
      const result = await pool.query(`
        SELECT c.id,c.case_number,c.category,c.subject,c.status,c.created_at,c.updated_at,c.assigned_admin_id,
          COALESCE(u.display_name,u.username,'') AS assigned_admin_name,
          (SELECT m.body FROM fynx_support_messages m WHERE m.case_id=c.id AND m.visibility='PUBLIC' ORDER BY m.created_at DESC,m.id DESC LIMIT 1) AS last_message
        FROM fynx_support_cases c LEFT JOIN users u ON u.id=c.assigned_admin_id
        WHERE c.requester_id=$1 ORDER BY c.updated_at DESC LIMIT 100
      `, [req.user.sub]);
      return res.json({ cases: result.rows });
    } catch (error) { console.error("support case history", error); return res.status(500).json({ error: "Support history unavailable." }); }
  });

  app.post("/api/support/cases/:caseId/attachments", auth, async (req, res) => {
    try {
      await ensureSchema();
      const caseId = caseIdFrom(req.params.caseId);
      const mediaId = Number(req.body?.mediaId);
      if (!caseId || !Number.isSafeInteger(mediaId) || mediaId < 1) return res.status(400).json({ error: "A valid screenshot is required." });
      const ownedCase = await pool.query("SELECT id FROM fynx_support_cases WHERE id=$1 AND requester_id=$2", [caseId, req.user.sub]);
      if (!ownedCase.rowCount) return res.status(404).json({ error: "Support case not found." });
      const media = await pool.query("SELECT id,mime_type,byte_size FROM message_media WHERE id=$1 AND owner_id=$2", [mediaId, req.user.sub]);
      if (!media.rows[0]) return res.status(403).json({ error: "Screenshot is not owned by this account." });
      if (!String(media.rows[0].mime_type || "").startsWith("image/")) return res.status(400).json({ error: "Only image screenshots can be attached." });
      const result = await pool.query("INSERT INTO fynx_support_attachments(case_id,media_id,uploader_id) VALUES($1,$2,$3) ON CONFLICT(case_id,media_id) DO NOTHING RETURNING id,media_id", [caseId, mediaId, req.user.sub]);
      const row = result.rows[0] || (await pool.query("SELECT id,media_id FROM fynx_support_attachments WHERE case_id=$1 AND media_id=$2", [caseId, mediaId])).rows[0];
      await pool.query("UPDATE fynx_support_cases SET updated_at=NOW() WHERE id=$1", [caseId]);
      return res.status(result.rows[0] ? 201 : 200).json({ attachment: { id:String(row.id), mediaId:String(row.media_id) } });
    } catch (error) { console.error("support screenshot attach", error); return res.status(500).json({ error: "Screenshot could not be attached." }); }
  });

  app.get("/api/support/cases/:caseId/attachments", auth, async (req, res) => {
    try {
      await ensureSchema();
      const caseId = caseIdFrom(req.params.caseId);
      if (!caseId) return res.status(400).json({ error: "Invalid support case." });
      const access = await canAccessAttachments(req, caseId);
      if (!access.exists) return res.status(404).json({ error: "Support case not found." });
      if (!access.allowed) return res.status(403).json({ error: "This case is not assigned to your support account." });
      const result = await pool.query("SELECT a.id,a.media_id,mm.mime_type,mm.byte_size,a.created_at FROM fynx_support_attachments a JOIN message_media mm ON mm.id=a.media_id WHERE a.case_id=$1 ORDER BY a.created_at ASC", [caseId]);
      return res.json({ attachments: result.rows.map(a => ({ id:String(a.id), mediaId:String(a.media_id), mimeType:a.mime_type, byteSize:Number(a.byte_size), createdAt:a.created_at })) });
    } catch (error) { console.error("support attachments list", error); return res.status(500).json({ error: "Support attachments unavailable." }); }
  });

  app.get("/api/support/cases/:caseId/attachments/:attachmentId", auth, async (req, res) => {
    try {
      await ensureSchema();
      const caseId = caseIdFrom(req.params.caseId);
      const attachmentId = caseIdFrom(req.params.attachmentId);
      if (!caseId || !attachmentId) return res.status(400).json({ error: "Invalid support attachment." });
      const access = await canAccessAttachments(req, caseId);
      if (!access.exists) return res.status(404).json({ error: "Support case not found." });
      if (!access.allowed) return res.status(403).json({ error: "This case is not assigned to your support account." });
      const result = await pool.query("SELECT mm.mime_type,mm.data FROM fynx_support_attachments a JOIN message_media mm ON mm.id=a.media_id WHERE a.id=$1 AND a.case_id=$2", [attachmentId, caseId]);
      if (!result.rows[0]) return res.status(404).json({ error: "Support attachment not found." });
      res.set("Content-Type", result.rows[0].mime_type);
      res.set("Cache-Control", "private, no-store");
      res.set("Content-Disposition", "inline");
      return res.send(result.rows[0].data);
    } catch (error) { console.error("support attachment download", error); return res.status(500).json({ error: "Support attachment could not be opened." }); }
  });

  app.get("/api/support/cases/:caseId/messages", auth, async (req, res) => {
    try {
      await ensureSchema();
      const caseId = caseIdFrom(req.params.caseId);
      if (!caseId) return res.status(400).json({ error: "Invalid support case." });
      const owned = await pool.query("SELECT id FROM fynx_support_cases WHERE id=$1 AND requester_id=$2", [caseId, req.user.sub]);
      if (!owned.rowCount) return res.status(404).json({ error: "Support case not found." });
      const result = await pool.query(`
        SELECT m.id,m.sender_id,m.body,m.created_at,u.username,u.display_name
        FROM fynx_support_messages m JOIN users u ON u.id=m.sender_id
        WHERE m.case_id=$1 AND m.visibility='PUBLIC' ORDER BY m.created_at ASC,m.id ASC LIMIT 300
      `, [caseId]);
      return res.json({ messages: result.rows.map(m => ({ id:String(m.id), senderId:String(m.sender_id), username:m.username, displayName:m.display_name, body:m.body, createdAt:m.created_at })) });
    } catch (error) { console.error("support user messages", error); return res.status(500).json({ error: "Support conversation unavailable." }); }
  });

  app.post("/api/support/cases/:caseId/messages", auth, async (req, res) => {
    let client;
    try {
      client = await pool.connect();
      await ensureSchema();
      const caseId = caseIdFrom(req.params.caseId);
      const body = typeof req.body?.body === "string" ? req.body.body.trim().slice(0, 4000) : "";
      const clientMessageId = typeof req.body?.clientMessageId === "string" ? req.body.clientMessageId.trim().slice(0, 100) : "";
      if (!caseId || body.length < 1 || !clientMessageId) return res.status(400).json({ error: "A valid message and request ID are required." });
      await client.query("BEGIN");
      const owned = await client.query("SELECT id,status,case_number,assigned_admin_id FROM fynx_support_cases WHERE id=$1 AND requester_id=$2 FOR UPDATE", [caseId, req.user.sub]);
      if (!owned.rows[0]) { await client.query("ROLLBACK"); return res.status(404).json({ error: "Support case not found." }); }
      if (owned.rows[0].status === "RESOLVED") {
        await client.query("ROLLBACK");
        return res.status(409).json({ error: "This case is resolved. Only the Owner can reopen it; submit a new request for a different issue." });
      }
      const inserted = await client.query("INSERT INTO fynx_support_messages(case_id,sender_id,body,visibility,client_message_id) VALUES($1,$2,$3,'PUBLIC',$4) ON CONFLICT(case_id,sender_id,client_message_id) WHERE client_message_id IS NOT NULL DO NOTHING RETURNING id,created_at", [caseId, req.user.sub, body, clientMessageId]);
      if (inserted.rows[0]) {
        await client.query("UPDATE fynx_support_cases SET status='WAITING_FOR_SUPPORT',updated_at=NOW() WHERE id=$1", [caseId]);
        await client.query("INSERT INTO fynx_support_case_events(case_id,actor_id,event_type,details) VALUES($1,$2,'USER_REPLIED','{}'::jsonb)", [caseId, req.user.sub]);
      }
      await client.query("COMMIT");
      if (inserted.rows[0]) {
        const owner = await officialOwnerId();
        const recipients = owned.rows[0].assigned_admin_id
          ? [owner, owned.rows[0].assigned_admin_id]
          : [owner, ...(await pool.query("SELECT user_id FROM fynx_admin_roles WHERE role='ADMIN'")).rows.map(x => x.user_id)];
        await notify(recipients, { caseId, eventId: `user-reply-${inserted.rows[0].id}`, title: "User replied to a support case", message: `${owned.rows[0].case_number || "FYNX support"} has a new reply.` });
      }
      return res.status(inserted.rows[0] ? 201 : 200).json({ ok: true, duplicate: !inserted.rows[0], messageId: inserted.rows[0]?.id == null ? null : String(inserted.rows[0].id) });
    } catch (error) {
      if (client) await client.query("ROLLBACK").catch(() => {});
      console.error("support user reply", error);
      return res.status(500).json({ error: "Your reply could not be sent." });
    } finally { client?.release(); }
  });

  app.get("/api/admin/support/cases", auth, async (req, res) => {
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const status = String(req.query?.status || "").trim().toUpperCase();
      if (status && !STATUSES.has(status)) return res.status(400).json({ error: "Invalid support status." });
      const params = [];
      const clauses = [];
      if (role !== "OWNER") { params.push(req.user.sub); clauses.push(`(c.assigned_admin_id IS NULL OR c.assigned_admin_id=$${params.length})`); }
      if (status) { params.push(status); clauses.push(`c.status=$${params.length}`); }
      const where = clauses.length ? `WHERE ${clauses.join(" AND ")}` : "";
      const result = await pool.query(`
        SELECT c.id,c.case_number,c.requester_id,c.category,c.subject,c.description,c.status,c.assigned_admin_id,c.created_at,c.updated_at,
          requester.username AS requester_username,requester.display_name AS requester_display_name,
          assigned.username AS assigned_username,assigned.display_name AS assigned_display_name,
          (SELECT m.body FROM fynx_support_messages m WHERE m.case_id=c.id AND m.visibility='PUBLIC' ORDER BY m.created_at DESC,m.id DESC LIMIT 1) AS last_message
        FROM fynx_support_cases c JOIN users requester ON requester.id=c.requester_id
        LEFT JOIN users assigned ON assigned.id=c.assigned_admin_id
        ${where} ORDER BY CASE c.status WHEN 'OPEN' THEN 0 WHEN 'WAITING_FOR_SUPPORT' THEN 1 WHEN 'IN_REVIEW' THEN 2 WHEN 'WAITING_FOR_USER' THEN 3 ELSE 4 END,c.updated_at ASC LIMIT 200
      `, params);
      return res.json({ role, cases: result.rows });
    } catch (error) { console.error("support admin inbox", error); return res.status(500).json({ error: "Support inbox unavailable." }); }
  });

  app.post("/api/admin/support/cases/:caseId/assign", auth, async (req, res) => {
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const caseId = caseIdFrom(req.params.caseId);
      const adminId = req.body?.adminId == null ? String(req.user.sub) : String(req.body.adminId);
      if (!caseId) return res.status(400).json({ error: "Invalid support case." });
      if (role !== "OWNER" && adminId !== String(req.user.sub)) return res.status(403).json({ error: "Admins can only claim cases for themselves." });
      if (role === "OWNER" && adminId !== "" && adminId !== String(req.user.sub)) {
        const isAdmin = await pool.query("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1", [adminId]);
        if (!isAdmin.rowCount) return res.status(400).json({ error: "Choose an active FYNX Admin." });
      }
      const predicate = role === "OWNER" ? "TRUE" : "(assigned_admin_id IS NULL OR assigned_admin_id=$3)";
      const result = await pool.query(`UPDATE fynx_support_cases SET assigned_admin_id=$2,updated_at=NOW() WHERE id=$1 AND ${predicate} RETURNING id,case_number,assigned_admin_id`, role === "OWNER" ? [caseId, adminId === "" ? null : adminId] : [caseId, req.user.sub, req.user.sub]);
      if (!result.rows[0]) return res.status(409).json({ error: "This case is assigned to another Admin or no longer exists." });
      await pool.query("INSERT INTO fynx_support_case_events(case_id,actor_id,event_type,details) VALUES($1,$2,'ASSIGNED',jsonb_build_object('assignedAdminId',$3::text))", [caseId, req.user.sub, adminId]);
      if (adminId) {
        const caseRow = await pool.query("SELECT case_number FROM fynx_support_cases WHERE id=$1", [caseId]);
        await notify([adminId], { caseId, eventId: `assigned-${caseId}-${Date.now()}`, title: "Support case assigned to you", message: `${caseRow.rows[0]?.case_number || "FYNX support"} is ready for your review.` });
      }
      return res.json({ case: result.rows[0] });
    } catch (error) { console.error("support case assign", error); return res.status(500).json({ error: "Case assignment failed." }); }
  });

  app.get("/api/admin/support/cases/:caseId/messages", auth, async (req, res) => {
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const caseId = caseIdFrom(req.params.caseId);
      if (!caseId) return res.status(400).json({ error: "Invalid support case." });
      const access = await adminCanSeeCase(role, req.user.sub, caseId);
      if (!access.row) return res.status(404).json({ error: "Support case not found." });
      if (!access.allowed) return res.status(403).json({ error: "This case is assigned to another Admin." });
      const result = await pool.query(`
        SELECT m.id,m.sender_id,m.body,m.visibility,m.created_at,u.username,u.display_name
        FROM fynx_support_messages m JOIN users u ON u.id=m.sender_id
        WHERE m.case_id=$1 ORDER BY m.created_at ASC,m.id ASC LIMIT 300
      `, [caseId]);
      return res.json({ messages: result.rows.map(m => ({ id:String(m.id), senderId:String(m.sender_id), username:m.username, displayName:m.display_name, body:m.body, visibility:m.visibility, createdAt:m.created_at })) });
    } catch (error) { console.error("support admin messages", error); return res.status(500).json({ error: "Support conversation unavailable." }); }
  });

  app.post("/api/admin/support/cases/:caseId/messages", auth, async (req, res) => {
    let client;
    try {
      client = await pool.connect();
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const caseId = caseIdFrom(req.params.caseId);
      const body = typeof req.body?.body === "string" ? req.body.body.trim().slice(0, 4000) : "";
      const visibility = req.body?.visibility === "INTERNAL" ? "INTERNAL" : "PUBLIC";
      const clientMessageId = typeof req.body?.clientMessageId === "string" ? req.body.clientMessageId.trim().slice(0, 100) : "";
      if (!caseId || !body || !clientMessageId) return res.status(400).json({ error: "A valid message and request ID are required." });
      await client.query("BEGIN");
      const locked = await client.query("SELECT requester_id,assigned_admin_id,status,case_number FROM fynx_support_cases WHERE id=$1 FOR UPDATE", [caseId]);
      const row = locked.rows[0];
      if (!row) { await client.query("ROLLBACK"); return res.status(404).json({ error: "Support case not found." }); }
      if (role !== "OWNER" && row.assigned_admin_id != null && String(row.assigned_admin_id) !== String(req.user.sub)) {
        await client.query("ROLLBACK");
        return res.status(403).json({ error: "This case is assigned to another Admin." });
      }
      if (role !== "OWNER" && String(row.assigned_admin_id || "") !== String(req.user.sub)) {
        await client.query("ROLLBACK");
        return res.status(409).json({ error: "Claim this case before replying." });
      }
      if (role !== "OWNER" && row.status === "RESOLVED" && visibility === "PUBLIC") {
        await client.query("ROLLBACK");
        return res.status(403).json({ error: "Only the Owner can reopen a resolved case." });
      }
      const inserted = await client.query("INSERT INTO fynx_support_messages(case_id,sender_id,body,visibility,client_message_id) VALUES($1,$2,$3,$4,$5) ON CONFLICT(case_id,sender_id,client_message_id) WHERE client_message_id IS NOT NULL DO NOTHING RETURNING id,created_at", [caseId, req.user.sub, body, visibility, clientMessageId]);
      if (inserted.rows[0]) {
        if (visibility === "PUBLIC") {
          await client.query("UPDATE fynx_support_cases SET status='WAITING_FOR_USER',updated_at=NOW(),resolved_at=NULL WHERE id=$1", [caseId]);
        } else {
          await client.query("UPDATE fynx_support_cases SET updated_at=NOW() WHERE id=$1", [caseId]);
        }
        await client.query("INSERT INTO fynx_support_case_events(case_id,actor_id,event_type,details) VALUES($1,$2,$3,jsonb_build_object('visibility',$4::text))", [caseId, req.user.sub, visibility === "INTERNAL" ? "INTERNAL_NOTE" : "ADMIN_REPLIED", visibility]);
      }
      await client.query("COMMIT");
      if (visibility === "PUBLIC" && inserted.rows[0]) {
        await notify([row.requester_id], { caseId, eventId: `admin-reply-${inserted.rows[0].id}`, title: "FYNX Support replied", message: `${row.case_number} has a new response.` });
      }
      return res.status(inserted.rows[0] ? 201 : 200).json({ ok: true, duplicate: !inserted.rows[0], messageId: inserted.rows[0]?.id == null ? null : String(inserted.rows[0].id) });
    } catch (error) {
      if (client) await client.query("ROLLBACK").catch(() => {});
      console.error("support admin reply", error);
      return res.status(500).json({ error: "Support reply could not be sent." });
    } finally { client?.release(); }
  });

  app.patch("/api/admin/support/cases/:caseId/status", auth, async (req, res) => {
    let client;
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const caseId = caseIdFrom(req.params.caseId);
      const status = String(req.body?.status || "").trim().toUpperCase();
      if (!caseId || !STATUSES.has(status)) return res.status(400).json({ error: "Choose a valid support status." });
      client = await pool.connect();
      await client.query("BEGIN");
      const locked = await client.query("SELECT requester_id,assigned_admin_id,status,case_number FROM fynx_support_cases WHERE id=$1 FOR UPDATE", [caseId]);
      const row = locked.rows[0];
      if (!row) { await client.query("ROLLBACK"); return res.status(404).json({ error: "Support case not found." }); }
      if (role !== "OWNER" && row.assigned_admin_id != null && String(row.assigned_admin_id) !== String(req.user.sub)) {
        await client.query("ROLLBACK");
        return res.status(403).json({ error: "This case is assigned to another Admin." });
      }
      if (role !== "OWNER" && String(row.assigned_admin_id || "") !== String(req.user.sub)) {
        await client.query("ROLLBACK");
        return res.status(409).json({ error: "Claim this case before changing its status." });
      }
      if (role !== "OWNER" && row.status === "RESOLVED" && status !== "RESOLVED") {
        await client.query("ROLLBACK");
        return res.status(403).json({ error: "Only the Owner can reopen a resolved case." });
      }
      if (row.status === status) {
        await client.query("COMMIT");
        return res.json({ case: { id: String(caseId), case_number: row.case_number, status }, changed: false });
      }
      const result = await client.query("UPDATE fynx_support_cases SET status=$1,updated_at=NOW(),resolved_at=CASE WHEN $1='RESOLVED' THEN NOW() ELSE NULL END WHERE id=$2 RETURNING id,case_number,status", [status, caseId]);
      const event = await client.query("INSERT INTO fynx_support_case_events(case_id,actor_id,event_type,details) VALUES($1,$2,'STATUS_CHANGED',jsonb_build_object('status',$3::text)) RETURNING id", [caseId, req.user.sub, status]);
      await client.query("COMMIT");
      if (status === "RESOLVED") {
        await notify([row.requester_id], { caseId, eventId: `resolved-${event.rows[0].id}`, title: "Support request resolved", message: `${row.case_number} has been marked resolved.` });
      } else if (row.status === "RESOLVED" && role === "OWNER") {
        await notify([row.requester_id], { caseId, eventId: `reopened-${event.rows[0].id}`, title: "Support request reopened", message: `${row.case_number} has been reopened by FYNX Support.` });
      }
      return res.json({ case: result.rows[0], changed: true });
    } catch (error) {
      if (client) await client.query("ROLLBACK").catch(() => {});
      console.error("support case status", error);
      return res.status(500).json({ error: "Case status could not be updated." });
    } finally { client?.release(); }
  });

  app.get("/api/admin/support/cases/:caseId/events", auth, async (req, res) => {
    try {
      await ensureSchema();
      const role = await requireSupport(req, res);
      if (!role) return;
      const caseId = caseIdFrom(req.params.caseId);
      if (!caseId) return res.status(400).json({ error: "Invalid support case." });
      const access = await adminCanSeeCase(role, req.user.sub, caseId);
      if (!access.row) return res.status(404).json({ error: "Support case not found." });
      if (!access.allowed) return res.status(403).json({ error: "This case is assigned to another Admin." });
      const result = await pool.query("SELECT e.id,e.event_type,e.details,e.created_at,u.username FROM fynx_support_case_events e LEFT JOIN users u ON u.id=e.actor_id WHERE e.case_id=$1 ORDER BY e.created_at DESC LIMIT 200", [caseId]);
      return res.json({ events: result.rows });
    } catch (error) { console.error("support case events", error); return res.status(500).json({ error: "Case activity unavailable." }); }
  });
}
