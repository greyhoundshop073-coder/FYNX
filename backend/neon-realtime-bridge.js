import { randomUUID } from "node:crypto";

function callIdIsValid(value) {
  return typeof value === "string" && /^call_[a-z0-9_]+$/i.test(value) && value.length <= 80;
}

function validateSignalPayload(signalType, body) {
  if (signalType === "offer" || signalType === "answer") {
    if (typeof body?.sdp !== "string" || body.sdp.length < 1 || body.sdp.length > 200_000) return null;
    return { sdp: body.sdp };
  }
  if (signalType === "ice") {
    const candidate = body?.candidate;
    if (!candidate || typeof candidate !== "object" || typeof candidate.candidate !== "string" || candidate.candidate.length > 20_000) return null;
    return {
      candidate: {
        candidate: candidate.candidate,
        sdpMid: candidate.sdpMid == null ? null : String(candidate.sdpMid).slice(0, 100),
        sdpMLineIndex: candidate.sdpMLineIndex == null ? null : Number(candidate.sdpMLineIndex),
        usernameFragment: candidate.usernameFragment == null ? null : String(candidate.usernameFragment).slice(0, 200)
      }
    };
  }
  return null;
}

export async function installNeonRealtimeBridge({ pool, dispatchLocal, getLocalConnectionCount, logger = console }) {
  if (!pool || typeof pool.query !== "function" || typeof dispatchLocal !== "function" || typeof getLocalConnectionCount !== "function") {
    throw new Error("Neon realtime bridge requires the shared database pool and local socket dispatchers.");
  }

  const instanceId = randomUUID();
  const schema = [
    `CREATE TABLE IF NOT EXISTS fynx_realtime_events (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NULL,
      source_instance_id TEXT NOT NULL,
      payload JSONB NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )`,
    "CREATE INDEX IF NOT EXISTS fynx_realtime_events_user_id_id_idx ON fynx_realtime_events (user_id, id)",
    "CREATE INDEX IF NOT EXISTS fynx_realtime_events_created_at_idx ON fynx_realtime_events (created_at)",
    `CREATE TABLE IF NOT EXISTS fynx_realtime_connections (
      connection_id TEXT PRIMARY KEY,
      user_id BIGINT NOT NULL,
      instance_id TEXT NOT NULL,
      connected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )`,
    "CREATE INDEX IF NOT EXISTS fynx_realtime_connections_user_seen_idx ON fynx_realtime_connections (user_id, last_seen_at)",
    `CREATE TABLE IF NOT EXISTS fynx_realtime_calls (
      id TEXT PRIMARY KEY,
      caller_id BIGINT NOT NULL,
      callee_id BIGINT NOT NULL,
      media_type TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'RINGING',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )`,
    "CREATE INDEX IF NOT EXISTS fynx_realtime_calls_status_updated_idx ON fynx_realtime_calls (status, updated_at)",
    "CREATE UNIQUE INDEX IF NOT EXISTS fynx_realtime_calls_active_caller_idx ON fynx_realtime_calls (caller_id) WHERE status IN ('RINGING', 'ACTIVE')",
    "CREATE UNIQUE INDEX IF NOT EXISTS fynx_realtime_calls_active_callee_idx ON fynx_realtime_calls (callee_id) WHERE status IN ('RINGING', 'ACTIVE')"
  ];
  for (const statement of schema) await pool.query(statement);

  const publish = async (userId, payload) => {
    await pool.query(
      "INSERT INTO fynx_realtime_events (user_id, source_instance_id, payload) VALUES ($1, $2, $3::jsonb)",
      [userId == null ? null : String(userId), instanceId, JSON.stringify(payload)]
    );
  };

  const emitToUser = async (userId, payload) => {
    dispatchLocal(String(userId), payload);
    await publish(String(userId), payload);
  };
  const emitGlobal = async (payload) => {
    dispatchLocal(null, payload);
    await publish(null, payload);
  };

  const registerConnection = async (userId, connectionId) => {
    await pool.query(
      "INSERT INTO fynx_realtime_connections (connection_id, user_id, instance_id) VALUES ($1, $2, $3)",
      [String(connectionId), String(userId), instanceId]
    );
  };

  const peerIdFor = (call, userId) => String(call.caller_id) === String(userId)
    ? String(call.callee_id)
    : String(call.caller_id);

  const callEvent = (call, fromUserId, signalType, extra = {}) => ({
    type: "call",
    callId: call.id,
    callType: call.media_type,
    fromUserId: String(fromUserId),
    toUserId: peerIdFor(call, fromUserId),
    signalType,
    ...extra
  });

  const endCallsForUser = async (userId, reason = "peer_disconnected") => {
    const result = await pool.query(
      `UPDATE fynx_realtime_calls
       SET status = 'ENDED', updated_at = NOW()
       WHERE status IN ('RINGING', 'ACTIVE') AND (caller_id = $1 OR callee_id = $1)
       RETURNING id, caller_id::text AS caller_id, callee_id::text AS callee_id, media_type`,
      [String(userId)]
    );
    for (const row of result.rows) {
      await emitToUser(row.caller_id, {
        type: "call", callId: row.id, callType: row.media_type,
        fromUserId: row.callee_id, toUserId: row.caller_id, signalType: reason
      });
      await emitToUser(row.callee_id, {
        type: "call", callId: row.id, callType: row.media_type,
        fromUserId: row.caller_id, toUserId: row.callee_id, signalType: reason
      });
    }
    return result.rowCount || 0;
  };

  const disconnect = async (userId, connectionId) => {
    await pool.query(
      "DELETE FROM fynx_realtime_connections WHERE connection_id = $1 AND user_id = $2",
      [String(connectionId), String(userId)]
    );
    const remaining = await pool.query(
      "SELECT 1 FROM fynx_realtime_connections WHERE user_id = $1 AND last_seen_at > NOW() - INTERVAL '60 seconds' LIMIT 1",
      [String(userId)]
    );
    if (!remaining.rowCount) {
      await emitGlobal({ type: "presence", userId: String(userId), online: false });
      await endCallsForUser(userId);
    }
  };

  const sendSocket = (socket, payload) => {
    if (socket?.readyState !== 1) return;
    try { socket.send(JSON.stringify(payload)); } catch {}
  };

  const createCall = async ({ callerId, calleeId, callId, mediaType }) => {
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      const users = await client.query(
        "SELECT id FROM users WHERE id = ANY($1::bigint[]) ORDER BY id FOR UPDATE",
        [[String(callerId), String(calleeId)]]
      );
      if (users.rowCount !== 2) {
        await client.query("ROLLBACK");
        return { unavailable: true };
      }
      const existing = await client.query(
        `SELECT id, caller_id::text AS caller_id, callee_id::text AS callee_id, media_type
         FROM fynx_realtime_calls
         WHERE status IN ('RINGING', 'ACTIVE')
           AND (caller_id = $1 OR callee_id = $1 OR caller_id = $2 OR callee_id = $2)
         LIMIT 1 FOR UPDATE`,
        [String(callerId), String(calleeId)]
      );
      if (existing.rowCount) {
        await client.query("ROLLBACK");
        return { busy: existing.rows[0] };
      }
      const online = await client.query(
        "SELECT 1 FROM fynx_realtime_connections WHERE user_id = $1 AND last_seen_at > NOW() - INTERVAL '60 seconds' LIMIT 1",
        [String(calleeId)]
      );
      if (!online.rowCount) {
        await client.query("ROLLBACK");
        return { unavailable: true };
      }
      const inserted = await client.query(
        `INSERT INTO fynx_realtime_calls (id, caller_id, callee_id, media_type, status)
         VALUES ($1, $2, $3, $4, 'RINGING')
         RETURNING id, caller_id::text AS caller_id, callee_id::text AS callee_id, media_type, status`,
        [callId, String(callerId), String(calleeId), mediaType]
      );
      await client.query("COMMIT");
      return { call: inserted.rows[0] };
    } catch (error) {
      await client.query("ROLLBACK").catch(() => {});
      if (error?.code === "23505") return { busy: true };
      throw error;
    } finally {
      client.release();
    }
  };

  const getCall = async (callId, userId) => {
    const result = await pool.query(
      `SELECT id, caller_id::text AS caller_id, callee_id::text AS callee_id, media_type, status
       FROM fynx_realtime_calls
       WHERE id = $1 AND (caller_id = $2 OR callee_id = $2) AND status IN ('RINGING', 'ACTIVE')
       LIMIT 1`,
      [callId, String(userId)]
    );
    return result.rows[0] || null;
  };

  const handleCall = async (userId, user, socket, body) => {
    const signalType = typeof body?.signalType === "string" ? body.signalType.trim().toLowerCase() : "";
    if (signalType === "invite") {
      const targetUserId = String(body?.toUserId || "");
      if (!/^\d+$/.test(targetUserId) || targetUserId === String(userId)) {
        return sendSocket(socket, { type: "call", signalType: "error", error: "invalid call target" });
      }
      const mediaType = body?.callType === "video" ? "video" : "voice";
      const callId = callIdIsValid(body?.callId) ? body.callId : `call_${Date.now().toString(36)}_${randomUUID().slice(0, 8)}`;
      const created = await createCall({ callerId: userId, calleeId: targetUserId, callId, mediaType });
      if (created.busy) {
        return sendSocket(socket, {
          type: "call", signalType: "busy", callId: created.busy.id || callId,
          callType: created.busy.media_type || mediaType, fromUserId: targetUserId, toUserId: String(userId)
        });
      }
      if (created.unavailable || !created.call) {
        return sendSocket(socket, {
          type: "call", signalType: "unavailable", callId, callType: mediaType,
          fromUserId: targetUserId, toUserId: String(userId)
        });
      }
      await emitToUser(targetUserId, {
        type: "call", callId, callType: mediaType, fromUserId: String(userId),
        toUserId: targetUserId, signalType: "invite", fromUsername: user?.username || null
      });
      return;
    }

    const callId = callIdIsValid(body?.callId) ? body.callId : "";
    const call = callId ? await getCall(callId, userId) : null;
    if (!call) return sendSocket(socket, { type: "call", callId, signalType: "error", error: "call not found" });
    const peerId = peerIdFor(call, userId);
    const targetUserId = String(body?.toUserId || peerId);
    if (targetUserId !== peerId) return sendSocket(socket, { type: "call", callId, signalType: "error", error: "call participant mismatch" });

    if (["accept", "reject", "end"].includes(signalType)) {
      const nextStatus = signalType === "accept" ? "ACTIVE" : signalType === "reject" ? "REJECTED" : "ENDED";
      const updated = await pool.query(
        `UPDATE fynx_realtime_calls SET status = $2, updated_at = NOW()
         WHERE id = $1 AND status IN ('RINGING', 'ACTIVE')
         RETURNING id, caller_id::text AS caller_id, callee_id::text AS callee_id, media_type`,
        [callId, nextStatus]
      );
      if (!updated.rowCount) return sendSocket(socket, { type: "call", callId, signalType: "error", error: "call already closed" });
      await emitToUser(peerId, callEvent(updated.rows[0], userId, signalType));
      return;
    }

    if (!["offer", "answer", "ice"].includes(signalType)) return;
    const payload = validateSignalPayload(signalType, body);
    if (!payload) return sendSocket(socket, { type: "call", callId, signalType: "error", error: "invalid signaling payload" });
    await emitToUser(peerId, callEvent(call, userId, signalType, payload));
  };

  let cursor = 0n;
  const processed = new Set();
  let polling = false;
  let lastHeartbeatAt = 0;
  const initial = await pool.query("SELECT COALESCE(MAX(id), 0)::text AS id FROM fynx_realtime_events");
  cursor = BigInt(initial.rows[0]?.id || "0");

  const poll = async () => {
    if (polling || getLocalConnectionCount() < 1) return;
    polling = true;
    try {
      const lowerBound = cursor > 10_000n ? cursor - 10_000n : 0n;
      const result = await pool.query(
        `SELECT id::text AS id, user_id::text AS user_id, source_instance_id, payload
         FROM fynx_realtime_events
         WHERE id > $1::bigint
         ORDER BY id ASC
         LIMIT 500`,
        [lowerBound.toString()]
      );
      for (const row of result.rows) {
        if (processed.has(row.id)) continue;
        processed.add(row.id);
        const eventId = BigInt(row.id);
        if (eventId > cursor) cursor = eventId;
        if (row.source_instance_id === instanceId) continue;
        dispatchLocal(row.user_id == null ? null : row.user_id, row.payload);
      }
      while (processed.size > 20_000) processed.delete(processed.values().next().value);
      if (Date.now() - lastHeartbeatAt > 20_000) {
        await pool.query("UPDATE fynx_realtime_connections SET last_seen_at = NOW() WHERE instance_id = $1", [instanceId]);
        lastHeartbeatAt = Date.now();
      }
    } catch (error) {
      logger.error("[fynx-realtime] cross-isolate poll failed", error?.message || error);
    } finally {
      polling = false;
    }
  };

  globalThis.__fynxNeonRealtimePublish = publish;
  globalThis.__fynxNeonRegisterConnection = registerConnection;
  globalThis.__fynxNeonDisconnect = disconnect;
  globalThis.__fynxNeonHandleCall = handleCall;

  const timer = setInterval(() => { void poll(); }, 1_000);
  timer.unref?.();

  const cleanup = async () => {
    const stale = await pool.query(
      "SELECT DISTINCT user_id::text AS user_id FROM fynx_realtime_connections WHERE last_seen_at < NOW() - INTERVAL '60 seconds'"
    );
    await pool.query("DELETE FROM fynx_realtime_connections WHERE last_seen_at < NOW() - INTERVAL '60 seconds'");
    for (const row of stale.rows) {
      const remaining = await pool.query(
        "SELECT 1 FROM fynx_realtime_connections WHERE user_id = $1 AND last_seen_at > NOW() - INTERVAL '60 seconds' LIMIT 1",
        [row.user_id]
      );
      if (!remaining.rowCount) {
        await emitGlobal({ type: "presence", userId: row.user_id, online: false });
        await endCallsForUser(row.user_id);
      }
    }
    const removed = await pool.query(
      `DELETE FROM fynx_realtime_events
       WHERE id IN (
         SELECT id FROM fynx_realtime_events
         WHERE created_at < NOW() - INTERVAL '1 hour'
         ORDER BY id ASC LIMIT 1000
       )`
    );
    return { staleConnections: stale.rowCount || 0, expiredEvents: removed.rowCount || 0 };
  };

  logger.log("[fynx-realtime] Neon cross-isolate bridge ready", instanceId);
  return { instanceId, cleanup, pollOnce: poll, stop: () => clearInterval(timer) };
}
