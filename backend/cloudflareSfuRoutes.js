const API_BASE = "https://rtc.live.cloudflare.com/v1";

function validId(value, max = 128) {
  return typeof value === "string" && value.length > 0 && value.length <= max && /^[a-zA-Z0-9_-]+$/.test(value);
}
function validSdp(value) {
  return value && typeof value === "object" && ["offer", "answer"].includes(value.type) &&
    typeof value.sdp === "string" && value.sdp.length > 0 && value.sdp.length <= 200_000;
}
function publicUpstreamError(payload, status) {
  const detail = typeof payload?.errorDescription === "string" ? payload.errorDescription :
    typeof payload?.errors?.[0]?.message === "string" ? payload.errors[0].message : "Realtime media service request failed.";
  return { status: status >= 400 && status < 500 ? status : 502, error: detail.slice(0, 240) };
}

export function registerCloudflareSfuRoutes({ app, pool, auth, fetchImpl = fetch, env = process.env }) {
  if (!app || !pool || typeof pool.query !== "function" || typeof auth !== "function") {
    throw new Error("Cloudflare SFU routes require the app, shared database pool and authentication middleware.");
  }
  let schemaPromise;
  const ensureSchema = () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_call_sfu_sessions (
        session_id TEXT PRIMARY KEY,
        call_id TEXT NOT NULL REFERENCES fynx_realtime_calls(id) ON DELETE CASCADE,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        ended_at TIMESTAMPTZ
      );
      CREATE INDEX IF NOT EXISTS fynx_call_sfu_sessions_call_idx
        ON fynx_call_sfu_sessions(call_id, created_at);
      CREATE TABLE IF NOT EXISTS fynx_call_sfu_publications (
        call_id TEXT NOT NULL REFERENCES fynx_realtime_calls(id) ON DELETE CASCADE,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        session_id TEXT NOT NULL REFERENCES fynx_call_sfu_sessions(session_id) ON DELETE CASCADE,
        track_name TEXT NOT NULL CHECK (track_name IN ('microphone','camera')),
        active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY(session_id, track_name)
      );
    `).catch((error) => { schemaPromise = null; throw error; });
    return schemaPromise;
  };
  const configured = () => Boolean(env.CLOUDFLARE_SFU_APP_ID && env.CLOUDFLARE_SFU_APP_SECRET);
  const sfuRequest = async (path, method = "POST", body) => {
    if (!configured()) {
      const error = new Error("Cloudflare Realtime SFU is not configured on the backend.");
      error.status = 503;
      throw error;
    }
    const response = await fetchImpl(`${API_BASE}/apps/${encodeURIComponent(env.CLOUDFLARE_SFU_APP_ID)}${path}`, {
      method,
      headers: { Authorization: `Bearer ${env.CLOUDFLARE_SFU_APP_SECRET}`, "Content-Type": "application/json" },
      ...(body === undefined ? {} : { body: JSON.stringify(body) })
    });
    let payload = {};
    try { payload = await response.json(); } catch {}
    if (!response.ok || payload?.success === false || payload?.errors?.length) {
      const failure = publicUpstreamError(payload, response.status);
      const error = new Error(failure.error);
      error.status = failure.status;
      throw error;
    }
    return payload;
  };
  const route = (handler) => async (req, res) => {
    try {
      await ensureSchema();
      if (!configured()) return res.status(503).json({ error: "video calling media service is not configured yet" });
      return await handler(req, res);
    } catch (error) {
      const status = Number.isInteger(error?.status) ? error.status : 502;
      if (status >= 500) console.error("[fynx-sfu] request failed", error?.message || error);
      return res.status(status).json({ error: status >= 500 ? "video calling media service is temporarily unavailable" : error.message });
    }
  };
  const getActiveCall = async (callId, userId) => {
    const result = await pool.query(
      `SELECT id FROM fynx_realtime_calls
       WHERE id=$1 AND status='ACTIVE' AND (caller_id=$2 OR callee_id=$2) LIMIT 1`,
      [callId, String(userId)]
    );
    return result.rows[0] || null;
  };
  const getOwnedSession = async (sessionId, userId, callId) => {
    const result = await pool.query(
      `SELECT s.session_id,s.call_id,s.user_id
       FROM fynx_call_sfu_sessions s
       JOIN fynx_realtime_calls c ON c.id=s.call_id
       WHERE s.session_id=$1 AND s.user_id=$2 AND s.call_id=$3
         AND s.ended_at IS NULL AND c.status='ACTIVE'
         AND (c.caller_id=$2 OR c.callee_id=$2) LIMIT 1`,
      [sessionId, String(userId), callId]
    );
    return result.rows[0] || null;
  };

  app.post("/api/calls/sfu/sessions", auth, route(async (req, res) => {
    const callId = req.body?.callId;
    if (!validId(callId, 80)) return res.status(400).json({ error: "valid callId is required" });
    if (!await getActiveCall(callId, req.user.sub)) return res.status(403).json({ error: "active call access required" });
    const created = await sfuRequest("/sessions/new");
    const sessionId = created?.sessionId;
    if (!validId(sessionId)) return res.status(502).json({ error: "media service returned an invalid session" });
    await pool.query(
      "INSERT INTO fynx_call_sfu_sessions(session_id,call_id,user_id) VALUES($1,$2,$3)",
      [sessionId, callId, String(req.user.sub)]
    );
    return res.status(201).json({ sessionId });
  }));

  app.post("/api/calls/sfu/sessions/:sessionId/publish", auth, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId, sessionDescription, tracks } = req.body || {};
    if (!validId(sessionId) || !validId(callId, 80) || !validSdp(sessionDescription) || sessionDescription.type !== "offer" ||
        !Array.isArray(tracks) || tracks.length < 1 || tracks.length > 2 ||
        tracks.some((track) => !validId(String(track?.mid || ""), 16) || !["microphone","camera"].includes(track?.trackName)) ||
        new Set(tracks.map((track) => track.trackName)).size !== tracks.length) {
      return res.status(400).json({ error: "valid offer and microphone/camera track list are required" });
    }
    if (!await getOwnedSession(sessionId, req.user.sub, callId)) return res.status(403).json({ error: "call session access denied" });
    const result = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}/tracks/new`, "POST", {
      sessionDescription,
      tracks: tracks.map(({ mid, trackName }) => ({ location: "local", mid, trackName }))
    });
    const successful = Array.isArray(result?.tracks) ? result.tracks.filter((track) => !track.errorCode && !track.errorDescription) : [];
    for (const track of successful) {
      const source = tracks.find((item) => item.trackName === track.trackName);
      if (source) await pool.query(
        `INSERT INTO fynx_call_sfu_publications(call_id,user_id,session_id,track_name,active)
         VALUES($1,$2,$3,$4,TRUE) ON CONFLICT(session_id,track_name) DO UPDATE SET active=TRUE`,
        [callId, String(req.user.sub), sessionId, source.trackName]
      );
    }
    if (successful.length !== tracks.length) return res.status(502).json({ error: "one or more media tracks could not be published", result });
    return res.json(result);
  }));

  app.post("/api/calls/sfu/sessions/:sessionId/subscribe", auth, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId, publisherSessionId, trackNames } = req.body || {};
    if (!validId(sessionId) || !validId(publisherSessionId) || !validId(callId, 80) ||
        !Array.isArray(trackNames) || trackNames.length < 1 || trackNames.length > 2 ||
        trackNames.some((name) => !["microphone","camera"].includes(name))) {
      return res.status(400).json({ error: "valid publisher session and track names are required" });
    }
    if (!await getOwnedSession(sessionId, req.user.sub, callId)) return res.status(403).json({ error: "call session access denied" });
    const publisher = await pool.query(
      `SELECT p.session_id,p.track_name FROM fynx_call_sfu_publications p
       JOIN fynx_call_sfu_sessions s ON s.session_id=p.session_id
       WHERE p.call_id=$1 AND p.session_id=$2 AND p.active=TRUE AND s.ended_at IS NULL
         AND p.user_id<>$3 AND p.track_name=ANY($4::text[])`,
      [callId, publisherSessionId, String(req.user.sub), trackNames]
    );
    if (publisher.rowCount !== trackNames.length) return res.status(403).json({ error: "requested media publication is not available to this caller" });
    const result = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}/tracks/new`, "POST", {
      tracks: publisher.rows.map((track) => ({ location: "remote", sessionId: track.session_id, trackName: track.track_name }))
    });
    return res.json(result);
  }));

  app.put("/api/calls/sfu/sessions/:sessionId/renegotiate", auth, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId, sessionDescription } = req.body || {};
    if (!validId(sessionId) || !validId(callId, 80) || !validSdp(sessionDescription) || sessionDescription.type !== "answer") {
      return res.status(400).json({ error: "valid SDP answer is required" });
    }
    if (!await getOwnedSession(sessionId, req.user.sub, callId)) return res.status(403).json({ error: "call session access denied" });
    const result = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}/renegotiate`, "PUT", { sessionDescription });
    return res.json(result);
  }));

  app.post("/api/calls/sfu/sessions/:sessionId/close", auth, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId } = req.body || {};
    if (!validId(sessionId) || !validId(callId, 80)) return res.status(400).json({ error: "valid session and call IDs are required" });
    if (!await getOwnedSession(sessionId, req.user.sub, callId)) return res.status(403).json({ error: "call session access denied" });
    await pool.query("UPDATE fynx_call_sfu_sessions SET ended_at=NOW() WHERE session_id=$1", [sessionId]);
    await pool.query("UPDATE fynx_call_sfu_publications SET active=FALSE WHERE session_id=$1", [sessionId]);
    return res.json({ ok: true });
  }));
}
