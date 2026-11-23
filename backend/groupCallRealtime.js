import { randomUUID } from "node:crypto";

const MAX_GROUP_CALL_PARTICIPANTS = 4;
const CALL_ID_PATTERN = /^call[-_][a-z0-9_-]{1,70}$/i;

export async function ensureFynxGroupCallSchema(pool) {
  if (!pool || typeof pool.query !== "function") throw new Error("Group-call storage is unavailable.");
  await pool.query(`
    CREATE TABLE IF NOT EXISTS fynx_realtime_group_calls (
      id TEXT PRIMARY KEY,
      group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE,
      creator_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      media_type TEXT NOT NULL CHECK (media_type IN ('voice','video')),
      status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','ENDED')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE UNIQUE INDEX IF NOT EXISTS fynx_realtime_group_calls_active_group_idx
      ON fynx_realtime_group_calls(group_id) WHERE status='ACTIVE';
    CREATE TABLE IF NOT EXISTS fynx_realtime_group_call_participants (
      call_id TEXT NOT NULL REFERENCES fynx_realtime_group_calls(id) ON DELETE CASCADE,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      status TEXT NOT NULL DEFAULT 'INVITED' CHECK (status IN ('INVITED','JOINED','LEFT','REJECTED','ENDED')),
      joined_at TIMESTAMPTZ,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY(call_id,user_id)
    );
    CREATE INDEX IF NOT EXISTS fynx_realtime_group_call_participants_user_idx
      ON fynx_realtime_group_call_participants(user_id,status,call_id);
  `);
}

function sendSocket(socket, payload) {
  if (socket?.readyState !== 1) return false;
  try { socket.send(JSON.stringify(payload)); return true; } catch { return false; }
}

function safeCallId(value) {
  return typeof value === "string" && value.length <= 80 && CALL_ID_PATTERN.test(value)
    ? value
    : `call_${Date.now().toString(36)}_${randomUUID().slice(0, 8)}`;
}

function groupCallEvent(call, signalType, extra = {}) {
  return {
    type: "group_call",
    callId: String(call.id),
    groupId: String(call.group_id),
    callType: call.media_type,
    creatorId: String(call.creator_id || call.creatorId || ""),
    signalType,
    ...extra
  };
}

async function participantRoster(pool, callId) {
  const result = await pool.query(
    `SELECT p.user_id::text AS "userId", u.username
     FROM fynx_realtime_group_call_participants p
     JOIN users u ON u.id=p.user_id
     WHERE p.call_id=$1 AND p.status='JOINED'
     ORDER BY p.joined_at ASC, p.user_id ASC`,
    [callId]
  );
  return result.rows.map(row => ({ userId: String(row.userId), username: String(row.username || "") }));
}

export function createFynxGroupCallSignalHandler({ pool, emitToUser, logger = console }) {
  if (!pool || typeof pool.query !== "function" || typeof emitToUser !== "function") {
    throw new Error("Group-call signaling requires the shared database pool and authenticated realtime publisher.");
  }

  return async function handleFynxGroupCallSignal(userId, user, socket, body) {
    await ensureFynxGroupCallSchema(pool);
    const senderId = String(userId);
    const signalType = String(body?.signalType || "").trim().toLowerCase();
    const groupId = String(body?.groupId || "").trim();
    const callId = String(body?.callId || "").trim();
    if (!groupId || groupId.length > 80 || !/^[A-Za-z0-9_-]+$/.test(groupId)) {
      return sendSocket(socket, { type: "group_call", groupId, callId, signalType: "error", error: "invalid group" });
    }

    if (signalType === "group_create") {
      const mediaType = body?.callType === "video" ? "video" : "voice";
      const id = safeCallId(callId);
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        const group = await client.query("SELECT id FROM fynx_groups WHERE id=$1 FOR UPDATE", [groupId]);
        if (!group.rowCount) {
          await client.query("ROLLBACK");
          return sendSocket(socket, { type: "group_call", groupId, callId: id, signalType: "error", error: "group not found" });
        }
        const member = await client.query(
          "SELECT 1 FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1",
          [groupId, senderId]
        );
        if (!member.rowCount) {
          await client.query("ROLLBACK");
          return sendSocket(socket, { type: "group_call", groupId, callId: id, signalType: "error", error: "group membership required" });
        }
        const existing = await client.query(
          "SELECT id FROM fynx_realtime_group_calls WHERE group_id=$1 AND status='ACTIVE' LIMIT 1",
          [groupId]
        );
        if (existing.rowCount) {
          await client.query("ROLLBACK");
          return sendSocket(socket, { type: "group_call", groupId, callId: existing.rows[0].id, signalType: "group_busy", error: "A group call is already active." });
        }
        const inserted = await client.query(
          `INSERT INTO fynx_realtime_group_calls(id,group_id,creator_id,media_type,status)
           VALUES($1,$2,$3,$4,'ACTIVE')
           RETURNING id,group_id,creator_id::text AS creator_id,media_type,status`,
          [id, groupId, senderId, mediaType]
        );
        const members = await client.query(
          "SELECT gm.user_id::text AS user_id FROM fynx_group_members gm WHERE gm.group_id=$1 ORDER BY gm.user_id",
          [groupId]
        );
        for (const memberRow of members.rows) {
          await client.query(
            `INSERT INTO fynx_realtime_group_call_participants(call_id,user_id,status,joined_at)
             VALUES($1,$2,$3,CASE WHEN $2=$4 THEN NOW() ELSE NULL END)`,
            [id, memberRow.user_id, String(memberRow.user_id) === senderId ? "JOINED" : "INVITED", senderId]
          );
        }
        await client.query("COMMIT");
        const online = await pool.query(
          `SELECT DISTINCT c.user_id::text AS user_id
           FROM fynx_realtime_connections c
           JOIN fynx_realtime_group_call_participants p ON p.user_id=c.user_id AND p.call_id=$1
           WHERE p.status='INVITED' AND c.last_seen_at > NOW() - INTERVAL '60 seconds'`,
          [id]
        );
        const inviter = { fromUserId: senderId, fromUsername: user?.username || null };
        for (const row of online.rows) {
          await emitToUser(row.user_id, groupCallEvent(inserted.rows[0], "group_invite", inviter));
        }
        const roster = await participantRoster(pool, id);
        return sendSocket(socket, groupCallEvent(inserted.rows[0], "group_created", { participants: roster, invitedCount: members.rowCount - 1 }));
      } catch (error) {
        await client.query("ROLLBACK").catch(() => {});
        if (error?.code === "23505") {
          return sendSocket(socket, { type: "group_call", groupId, callId: id, signalType: "group_busy", error: "A group call is already active." });
        }
        logger.error("[fynx-group-call] create failed", error?.message || error);
        return sendSocket(socket, { type: "group_call", groupId, callId: id, signalType: "error", error: "The group call could not be started." });
      } finally {
        client.release();
      }
    }

    const callResult = await pool.query(
      `SELECT id,group_id,creator_id::text AS creator_id,media_type,status
       FROM fynx_realtime_group_calls WHERE id=$1 AND group_id=$2 AND status='ACTIVE' LIMIT 1`,
      [callId, groupId]
    );
    const call = callResult.rows[0];
    if (!call) return sendSocket(socket, { type: "group_call", groupId, callId, signalType: "error", error: "The group call is no longer active." });

    const membership = await pool.query(
      "SELECT 1 FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1",
      [groupId, senderId]
    );
    if (!membership.rowCount) {
      return sendSocket(socket, groupCallEvent(call, "error", { error: "Group membership is required to join or signal this call." }));
    }

    if (signalType === "group_join") {
      const client = await pool.connect();
      try {
        await client.query("BEGIN");
        await client.query("SELECT id FROM fynx_realtime_group_calls WHERE id=$1 AND status='ACTIVE' FOR UPDATE", [callId]);
        const current = await client.query(
          "SELECT status FROM fynx_realtime_group_call_participants WHERE call_id=$1 AND user_id=$2 FOR UPDATE",
          [callId, senderId]
        );
        const joinedCount = await client.query(
          "SELECT COUNT(*)::int AS count FROM fynx_realtime_group_call_participants WHERE call_id=$1 AND status='JOINED'",
          [callId]
        );
        if (current.rows[0]?.status !== "JOINED" && Number(joinedCount.rows[0]?.count || 0) >= MAX_GROUP_CALL_PARTICIPANTS) {
          await client.query("ROLLBACK");
          return sendSocket(socket, groupCallEvent(call, "group_full", { error: `Group calls currently support up to ${MAX_GROUP_CALL_PARTICIPANTS} people.` }));
        }
        await client.query(
          `INSERT INTO fynx_realtime_group_call_participants(call_id,user_id,status,joined_at)
           VALUES($1,$2,'JOINED',NOW())
           ON CONFLICT(call_id,user_id) DO UPDATE SET status='JOINED',joined_at=COALESCE(fynx_realtime_group_call_participants.joined_at,NOW()),updated_at=NOW()`,
          [callId, senderId]
        );
        await client.query("COMMIT");
      } catch (error) {
        await client.query("ROLLBACK").catch(() => {});
        throw error;
      } finally {
        client.release();
      }
      const roster = await participantRoster(pool, callId);
      await emitToUser(senderId, groupCallEvent(call, "group_roster", { participants: roster }));
      for (const participant of roster) {
        if (participant.userId !== senderId) {
          await emitToUser(participant.userId, groupCallEvent(call, "group_participant_joined", {
            participant: { userId: senderId, username: user?.username || "" }
          }));
        }
      }
      return;
    }

    const participantState = await pool.query(
      "SELECT status FROM fynx_realtime_group_call_participants WHERE call_id=$1 AND user_id=$2 LIMIT 1",
      [callId, senderId]
    );
    if (signalType === "group_reject") {
      if (participantState.rows[0]?.status !== "INVITED") {
        return sendSocket(socket, groupCallEvent(call, "error", { error: "Only an invited participant can decline this call." }));
      }
      await pool.query(
        "UPDATE fynx_realtime_group_call_participants SET status='REJECTED',updated_at=NOW() WHERE call_id=$1 AND user_id=$2 AND status='INVITED'",
        [callId, senderId]
      );
      const roster = await participantRoster(pool, callId);
      for (const participant of roster) {
        await emitToUser(participant.userId, groupCallEvent(call, "group_participant_left", {
          participant: { userId: senderId, username: user?.username || "" }
        }));
      }
      return sendSocket(socket, groupCallEvent(call, "group_left", { participants: roster }));
    }

    if (participantState.rows[0]?.status !== "JOINED") {
      return sendSocket(socket, groupCallEvent(call, "error", { error: "Join the group call before sending media signals." }));
    }

    if (signalType === "group_leave") {
      await pool.query(
        "UPDATE fynx_realtime_group_call_participants SET status=$3,updated_at=NOW() WHERE call_id=$1 AND user_id=$2",
        [callId, senderId, signalType === "group_reject" ? "REJECTED" : "LEFT"]
      );
      const roster = await participantRoster(pool, callId);
      for (const participant of roster) {
        await emitToUser(participant.userId, groupCallEvent(call, "group_participant_left", {
          participant: { userId: senderId, username: user?.username || "" }
        }));
      }
      if (!roster.length) {
        await pool.query("UPDATE fynx_realtime_group_calls SET status='ENDED',updated_at=NOW() WHERE id=$1 AND status='ACTIVE'", [callId]);
      }
      return sendSocket(socket, groupCallEvent(call, "group_left", { participants: roster }));
    }

    if (signalType === "group_end") {
      if (String(call.creator_id) !== senderId) {
        return sendSocket(socket, groupCallEvent(call, "error", { error: "Only the group-call starter can end the call for everyone." }));
      }
      const ended = await pool.query(
        `UPDATE fynx_realtime_group_calls SET status='ENDED',updated_at=NOW()
         WHERE id=$1 AND status='ACTIVE'
         RETURNING id,group_id,creator_id::text AS creator_id,media_type,status`,
        [callId]
      );
      const invited = await pool.query(
        "SELECT user_id::text AS user_id FROM fynx_realtime_group_call_participants WHERE call_id=$1 AND status IN ('JOINED','INVITED')",
        [callId]
      );
      for (const row of invited.rows) await emitToUser(row.user_id, groupCallEvent(ended.rows[0] || call, "group_ended", { endedByUserId: senderId }));
      await pool.query(
        "UPDATE fynx_realtime_group_call_participants SET status='ENDED',updated_at=NOW() WHERE call_id=$1 AND status IN ('JOINED','INVITED')",
        [callId]
      );
      return;
    }

    if (["group_offer", "group_answer", "group_ice"].includes(signalType)) {
      const targetUserId = String(body?.toUserId || "").trim();
      if (!/^\d+$/.test(targetUserId) || targetUserId === senderId) {
        return sendSocket(socket, groupCallEvent(call, "error", { error: "Invalid group-call participant target." }));
      }
      const target = await pool.query(
        `SELECT 1 FROM fynx_realtime_group_call_participants
         WHERE call_id=$1 AND user_id=$2 AND status='JOINED' LIMIT 1`,
        [callId, targetUserId]
      );
      if (!target.rowCount) return sendSocket(socket, groupCallEvent(call, "error", { error: "That participant is no longer in this call." }));
      let payload = {};
      if (signalType === "group_offer" || signalType === "group_answer") {
        const sdp = body?.sdp;
        if (typeof sdp !== "string" || !sdp.length || sdp.length > 200_000) {
          return sendSocket(socket, groupCallEvent(call, "error", { error: "Invalid group-call media description." }));
        }
        payload.sdp = sdp;
      } else {
        const candidate = body?.candidate;
        if (!candidate || typeof candidate !== "object" || typeof candidate.candidate !== "string" || !candidate.candidate.length || candidate.candidate.length > 20_000) {
          return sendSocket(socket, groupCallEvent(call, "error", { error: "Invalid group-call network candidate." }));
        }
        payload.candidate = {
          candidate: candidate.candidate,
          sdpMid: candidate.sdpMid == null ? null : String(candidate.sdpMid).slice(0, 100),
          sdpMLineIndex: candidate.sdpMLineIndex == null ? null : Number(candidate.sdpMLineIndex),
          usernameFragment: candidate.usernameFragment == null ? null : String(candidate.usernameFragment).slice(0, 200)
        };
      }
      await emitToUser(targetUserId, groupCallEvent(call, signalType, { ...payload, fromUserId: senderId, fromUsername: user?.username || null, toUserId: targetUserId }));
      return;
    }

    return sendSocket(socket, groupCallEvent(call, "error", { error: "Unsupported group-call signal." }));
  };
}
