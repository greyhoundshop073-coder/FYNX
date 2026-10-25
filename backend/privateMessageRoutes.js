function canonicalJson(value) {
  if (Array.isArray(value)) return "[" + value.map(canonicalJson).join(",") + "]";
  if (value && typeof value === "object") {
    return "{" + Object.keys(value).sort().map((key) => JSON.stringify(key) + ":" + canonicalJson(value[key])).join(",") + "}";
  }
  return JSON.stringify(value);
}

/**
 * Registers the authenticated private-message endpoint.
 * Dependencies are injected to keep the actual handler testable without starting the full server.
 */
export function registerPrivateMessageRoutes({
  app,
  auth,
  pool,
  findUserByUsername,
  messageProjection,
  rowToMessage,
  broadcastMessage,
}) {
  app.post("/api/messages", auth, async (req, res) => {
    try {
      const recipientUsername = typeof req.body?.recipientUsername === "string" ? req.body.recipientUsername.trim().toLowerCase() : "";
      const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
      const replyToId = req.body?.replyToId == null ? null : Number(req.body.replyToId);
      const mediaId = req.body?.mediaId == null ? null : Number(req.body.mediaId);
      const mediaType = typeof req.body?.mediaType === "string" ? req.body.mediaType.trim().toLowerCase() : null;
      const voiceDurationMs = req.body?.voiceDurationMs == null ? 0 : Number(req.body.voiceDurationMs);
      const messageType = typeof req.body?.messageType === 'string' ? req.body.messageType.trim().toLowerCase() : 'text';
      const messagePayload = req.body?.messagePayload && typeof req.body.messagePayload === 'object' && !Array.isArray(req.body.messagePayload) ? req.body.messagePayload : null;
      const clientMessageId = typeof req.body?.clientMessageId === 'string' ? req.body.clientMessageId.trim().toLowerCase() : null;
      if (clientMessageId != null && !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(clientMessageId)) return res.status(400).json({ error: "invalid client message id" });
      if (!/^(text|location|contact|poll|sticker|gif)$/.test(messageType)) return res.status(400).json({ error: 'invalid message type' });
      if (messageType !== 'text' && !messagePayload) return res.status(400).json({ error: 'message payload is required' });
      if (!recipientUsername || text.length > 4000 || (!text && !Number.isInteger(mediaId))) return res.status(400).json({ error: "valid recipientUsername and message content are required" });
      if (mediaId != null && (!Number.isInteger(mediaId) || mediaId < 1)) return res.status(400).json({ error: "invalid media id" });
      if (mediaId != null && (!mediaType || !/^(image|video|video_note|audio|document)$/.test(mediaType))) return res.status(400).json({ error: "invalid media type" });
      if (!Number.isFinite(voiceDurationMs) || voiceDurationMs < 0 || voiceDurationMs > 120000) return res.status(400).json({ error: "invalid voice duration" });
      const recipient = await findUserByUsername(recipientUsername);
      if (!recipient || String(recipient.id) === String(req.user.sub)) return res.status(400).json({ error: "invalid recipient" });
      const blocked = await pool.query(`SELECT 1 FROM blocks WHERE (blocker_id = $1 AND blocked_id = $2) OR (blocker_id = $2 AND blocked_id = $1) LIMIT 1`, [req.user.sub, recipient.id]);
      if (blocked.rowCount) return res.status(403).json({ error: "conversation unavailable" });
      if (mediaId != null) {
        const media = await pool.query("SELECT id, mime_type FROM message_media WHERE id = $1 AND owner_id = $2", [mediaId, req.user.sub]);
        if (!media.rows[0]) return res.status(403).json({ error: "media is not owned by this account" });
      }
      if (replyToId != null) {
        if (!Number.isInteger(replyToId) || replyToId < 1) return res.status(400).json({ error: "invalid reply id" });
        const reply = await pool.query("SELECT id FROM messages WHERE id = $1 AND ((sender_id = $2 AND recipient_id = $3) OR (sender_id = $3 AND recipient_id = $2))", [replyToId, req.user.sub, recipient.id]);
        if (!reply.rows[0]) return res.status(400).json({ error: "invalid reply target" });
      }
      const insert = await pool.query(
        "INSERT INTO messages (sender_id, recipient_id, text, reply_to_id, media_id, media_type, voice_duration_ms, message_type, message_payload, client_message_id) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) ON CONFLICT (sender_id, client_message_id) WHERE client_message_id IS NOT NULL DO NOTHING RETURNING id",
        [req.user.sub, recipient.id, text, replyToId, mediaId, mediaType, voiceDurationMs, messageType, messagePayload, clientMessageId]
      );
      let messageId = insert.rows[0]?.id;
      const created = Boolean(messageId);
      if (!created && clientMessageId) {
        const existing = await pool.query("SELECT id FROM messages WHERE sender_id=$1 AND client_message_id=$2 LIMIT 1", [req.user.sub, clientMessageId]);
        messageId = existing.rows[0]?.id;
      }
      if (!messageId) return res.status(500).json({ error: "message persistence failed" });
      const full = await pool.query(messageProjection() + " WHERE m.id=$1 LIMIT 1", [messageId]);
      if (!full.rows[0]) return res.status(500).json({ error: "message persistence failed" });
      const persisted = full.rows[0];
      if (!created && (
        String(persisted.sender_id) !== String(req.user.sub) ||
        String(persisted.recipient_id) !== String(recipient.id) ||
        String(persisted.text ?? "") !== text ||
        (persisted.reply_to_id == null ? null : String(persisted.reply_to_id)) !== (replyToId == null ? null : String(replyToId)) ||
        (persisted.media_id == null ? null : String(persisted.media_id)) !== (mediaId == null ? null : String(mediaId)) ||
        String(persisted.media_type ?? "") !== String(mediaType ?? "") ||
        Number(persisted.voice_duration_ms ?? 0) !== voiceDurationMs ||
        String(persisted.message_type ?? "text") !== messageType ||
        canonicalJson(persisted.message_payload ?? null) !== canonicalJson(messagePayload ?? null)
      )) {
        return res.status(409).json({ error: "client message ID already used for different content", code: "MESSAGE_ID_CONFLICT" });
      }
      const message = rowToMessage(persisted);
  
      // Broadcast the canonical persisted message on both first sends and idempotent retries.
      // If the first request committed but its realtime publish/response failed, the same ID
      // can be replayed safely; clients merge messages by ID instead of rendering duplicates.
      await broadcastMessage(message);
      return res.status(created ? 201 : 200).json({ message });
    } catch (error) { console.error("send message", error); return res.status(500).json({ error: "message send failed" }); }
  });
}
