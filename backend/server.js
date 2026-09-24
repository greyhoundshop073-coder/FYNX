    const result = await pool.query("INSERT INTO messages (sender_id, recipient_id, text, reply_to_id, media_id, media_type, voice_duration_ms) VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id, created_at", [req.user.sub, recipient.id, text, replyToId, mediaId, mediaType, voiceDurationMs]);
    const sender = await pool.query("SELECT username, display_name FROM users WHERE id = $1", [req.user.sub]);
    const message = { id: String(result.rows[0].id), senderId: String(req.user.sub), senderUsername: sender.rows[0]?.username || req.user.username || null, senderDisplayName: sender.rows[0]?.display_name || null, recipientId: String(recipient.id), recipientUsername: recipient.username, recipientDisplayName: recipient.display_name, text, timestamp: new Date(result.rows[0].created_at).getTime(), delivered: false, read: false, edited: false, deleted: false, replyToId: replyToId == null ? null : String(replyToId), mediaId: mediaId == null ? null : String(mediaId), mediaType, mediaUrl: mediaId == null ? null : `/api/media/${mediaId}`, voiceDurationMs };
    await broadcastMessage(message);
    return res.status(201).json({ message });
  } catch (error) { console.error("send message", error); return res.status(500).json({ error: "message send failed" }); }
});

app.post("/api/messages/:id/read", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid message id" });
    const result = await pool.query("UPDATE messages SET read_at = NOW(), delivered_at = COALESCE(delivered_at, NOW()) WHERE id = $1 AND recipient_id = $2 AND deleted = FALSE RETURNING id, sender_id", [id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    broadcastToUser(result.rows[0].sender_id, { type: "message_status", messageId: String(id), status: "read" });
    return res.json({ ok: true });
  } catch (error) { console.error("message read", error); return res.status(500).json({ error: "read receipt failed" }); }
});

app.post("/api/messages/:id/delivered", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid message id" });
    const result = await pool.query("UPDATE messages SET delivered_at = COALESCE(delivered_at, NOW()) WHERE id = $1 AND recipient_id = $2 AND deleted = FALSE RETURNING id, sender_id", [id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    broadcastToUser(result.rows[0].sender_id, { type: "message_status", messageId: String(id), status: "delivered" });
    return res.json({ ok: true });
  } catch (error) { console.error("message delivered", error); return res.status(500).json({ error: "delivery receipt failed" }); }
});

app.patch("/api/messages/:id", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
    if (!Number.isInteger(id) || id < 1 || !text || text.length > 4000) return res.status(400).json({ error: "valid message text is required" });
    const result = await pool.query("UPDATE messages SET text = $1, edited = TRUE WHERE id = $2 AND sender_id = $3 AND deleted = FALSE RETURNING id, sender_id, recipient_id", [text, id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    const message = { id: String(id), senderId: String(result.rows[0].sender_id), recipientId: String(result.rows[0].recipient_id), text, edited: true };
    broadcastMessage(message);
    return res.json({ message });
  } catch (error) { console.error("message edit", error); return res.status(500).json({ error: "message edit failed" }); }
});

app.patch("/api/messages/:id/reaction", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    const reaction = req.body?.reaction == null ? null : String(req.body.reaction).trim().slice(0, 16);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid message id" });
    if (reaction === "") return res.status(400).json({ error: "reaction is invalid" });
    const target = await pool.query("SELECT id,sender_id,recipient_id FROM messages WHERE id=$1 AND deleted=FALSE LIMIT 1", [id]);
    const row = target.rows[0];
    if (!row || ![String(row.sender_id), String(row.recipient_id)].includes(String(req.user.sub))) return res.status(404).json({ error: "message not found" });
    const result = await pool.query("UPDATE messages SET reaction=$1 WHERE id=$2 RETURNING id,sender_id,recipient_id", [reaction, id]);
    const full = await pool.query(messageProjection()+" WHERE m.id=$1 LIMIT 1", [id]);
    const message = rowToMessage(full.rows[0]);
    await broadcastMessage(message);
    return res.json({ message });
  } catch (error) { console.error("message reaction", error); return res.status(500).json({ error: "message reaction failed" }); }
});

app.delete("/api/messages/:id", auth, async (req, res) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "invalid message id" });
    const result = await pool.query("UPDATE messages SET deleted = TRUE, text = '' WHERE id = $1 AND sender_id = $2 AND deleted = FALSE RETURNING id, sender_id, recipient_id", [id, req.user.sub]);
    if (!result.rows[0]) return res.status(404).json({ error: "message not found" });
    const message = { id: String(id), senderId: String(result.rows[0].sender_id), recipientId: String(result.rows[0].recipient_id), text: "", deleted: true };
    broadcastMessage(message);
    return res.json({ message });
  } catch (error) { console.error("message delete", error); return res.status(500).json({ error: "message delete failed" }); }
});

wss.on("connection", (socket, req) => {
  try {
    const url = new URL(req.url || "/realtime", `http://${req.headers.host || "localhost"}`);
    const token = url.searchParams.get("token") || "";
    if (!token || !JWT_SECRET) return socket.close(1008, "authentication required");
    const user = jwt.verify(token, JWT_SECRET);
    const userId = String(user.sub);
    if (!clientsByUserId.has(userId)) clientsByUserId.set(userId, new Set());
    clientsByUserId.get(userId).add(socket);
    broadcastPresence(userId, true);
    markPendingDelivered(userId).catch((error) => console.error("deliver pending", error));
    socket.on("message", (raw) => {
      try {
        const body = JSON.parse(raw.toString());
        const signalType = typeof body?.signalType === "string" ? body.signalType.trim().toLowerCase() : "";
        if (body?.type !== "call") return;
        if (["invite", "accept", "reject", "end"].includes(signalType)) {
          const targetUserId = String(body?.toUserId || "");
          if (!targetUserId || targetUserId === userId) return sendSocket(socket, { type: "call", signalType: "error", error: "invalid call target" });
          if (signalType === "invite") {
            if (activeCalls.size >= 500) return sendSocket(socket, { type: "call", signalType: "error", error: "call capacity reached" });
            const mediaType = body?.callType === "video" ? "video" : "voice";
            const existing = [...activeCalls.values()].find(call => call.callerId === userId || call.calleeId === userId || call.callerId === targetUserId || call.calleeId === targetUserId);
            if (existing) return sendSocket(socket, { type: "call", signalType: "busy", callId: existing.id, callType: existing.mediaType, fromUserId: targetUserId, toUserId: userId });
            const callId = validCallId(body?.callId) ? body.callId : newCallId();
            const call = { id: callId, callerId: userId, calleeId: targetUserId, mediaType, createdAt: Date.now() };
            activeCalls.set(callId, call);
            if (!clientsByUserId.has(targetUserId)) { activeCalls.delete(callId); return sendSocket(socket, { type: "call", callId, callType: mediaType, fromUserId: targetUserId, toUserId: userId, signalType: "unavailable" }); }
            relayCallSignal(call, userId, "invite", { fromUsername: user.username || null });
            return;
          }
          const callId = validCallId(body?.callId) ? body.callId : "";
          const call = activeCalls.get(callId);
          if (!call || ![call.callerId, call.calleeId].includes(userId) || ![call.callerId, call.calleeId].includes(targetUserId)) return sendSocket(socket, { type: "call", callId, signalType: "error", error: "call not found" });
          if (signalType === "accept" || signalType === "reject" || signalType === "end") {
            relayCallSignal(call, userId, signalType);
            if (signalType !== "accept") closeCall(callId, signalType, false);
            return;
          }
        }
        const callId = validCallId(body?.callId) ? body.callId : "";
        const call = activeCalls.get(callId);
        if (!call || ![call.callerId, call.calleeId].includes(userId)) return sendSocket(socket, { type: "call", callId, signalType: "error", error: "call not found" });
        if (!["offer", "answer", "ice"].includes(signalType)) return;
        const payload = validateSignalPayload(signalType, body);
        if (!payload) return sendSocket(socket, { type: "call", callId, signalType: "error", error: "invalid signaling payload" });
        relayCallSignal(call, userId, signalType, payload);
      } catch { sendSocket(socket, { type: "call", signalType: "error", error: "invalid realtime message" }); }
    });
    socket.on("close", () => {
      const sockets = clientsByUserId.get(userId);
      if (!sockets) return;
      sockets.delete(socket);
      if (!sockets.size) {
        clientsByUserId.delete(userId);
        broadcastPresence(userId, false);
        for (const [callId, call] of activeCalls) if (call.callerId === userId || call.calleeId === userId) closeCall(callId, "peer_disconnected");
      }
    });
  } catch { socket.close(1008, "invalid token"); }
});

registerSocialRoutes(app, { pool, auth, findUserByUsername });
if (pool) registerTrustSafetyRoutes({ app, pool, auth });
if (pool) registerMarketplaceTransactionRoutes({ app, pool, auth });
registerMarketplaceReputationRoutes({ app, pool, auth });
registerMarketplaceCompletionRoutes({ app, pool, auth });
if (pool) registerMoneyPlannerRoutes({ app, pool, auth });
if (pool) registerMarketplaceAdvertisingRoutes({ app, pool, auth });

initDatabase().catch((error) => { console.error("database initialization failed", error); process.exitCode = 1; });

const shutdown = async (signal) => {
  console.log(`[fynx-shutdown] ${signal}`);
  server.close(async () => { if (pool) await pool.end().catch(() => {}); process.exit(0); });
  setTimeout(() => process.exit(1), 10_000).unref();
};
process.once("SIGTERM", () => shutdown("SIGTERM"));
process.once("SIGINT", () => shutdown("SIGINT"));

server.listen(PORT, "0.0.0.0", () => console.log(`FYNX backend listening on ${PORT}`));