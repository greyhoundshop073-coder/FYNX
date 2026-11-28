const API_BASE = "https://rtc.live.cloudflare.com/v1";
const SESSION_STATES = ["READY", "IN_PROGRESS", "AWAITING_RENEGOTIATION", "RECOVERY_REQUIRED", "ENDED"];

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
function needsRenegotiation(result) {
  return result?.requiresImmediateRenegotiation === true || result?.sessionDescription?.type === "offer";
}

export function registerCloudflareSfuRoutes({ app, pool, auth, rateLimit, fetchImpl = fetch, env = process.env, resolveActiveCall = null }) {
  if (!app || !pool || typeof pool.query !== "function" || typeof auth !== "function" || typeof rateLimit !== "function") {
    throw new Error("Cloudflare SFU routes require the app, shared database pool, authentication and shared rate-limit middleware.");
  }
  let schemaPromise;
  const ensureSchema = () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_call_sfu_sessions (
        session_id TEXT PRIMARY KEY,
        call_id TEXT NOT NULL,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        mutation_state TEXT NOT NULL DEFAULT 'READY'
          CHECK (mutation_state IN ('READY','IN_PROGRESS','AWAITING_RENEGOTIATION','RECOVERY_REQUIRED','ENDED')),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        ended_at TIMESTAMPTZ
      );
      ALTER TABLE fynx_call_sfu_sessions
        ADD COLUMN IF NOT EXISTS mutation_state TEXT NOT NULL DEFAULT 'READY';
      CREATE INDEX IF NOT EXISTS fynx_call_sfu_sessions_call_idx
        ON fynx_call_sfu_sessions(call_id, created_at);
      CREATE TABLE IF NOT EXISTS fynx_call_sfu_publications (
        call_id TEXT NOT NULL,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        session_id TEXT NOT NULL REFERENCES fynx_call_sfu_sessions(session_id) ON DELETE CASCADE,
        track_name TEXT NOT NULL CHECK (track_name IN ('microphone','camera')),
        mid TEXT,
        active BOOLEAN NOT NULL DEFAULT TRUE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY(session_id, track_name)
      );
      ALTER TABLE fynx_call_sfu_publications
        ADD COLUMN IF NOT EXISTS mid TEXT;
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
    if (typeof resolveActiveCall === "function") {
      return (await resolveActiveCall(callId, String(userId))) || null;
    }
    const result = await pool.query(
      `SELECT id FROM fynx_realtime_calls
       WHERE id=$1 AND status='ACTIVE' AND (caller_id=$2 OR callee_id=$2) LIMIT 1`,
      [callId, String(userId)]
    );
    return result.rows[0] || null;
  };
  const getOwnedSession = async (sessionId, userId, callId, requireActiveCall = true) => {
    const result = await pool.query(
      `SELECT session_id,call_id,user_id,mutation_state
       FROM fynx_call_sfu_sessions
       WHERE session_id=$1 AND user_id=$2 AND call_id=$3 AND ended_at IS NULL LIMIT 1`,
      [sessionId, String(userId), callId]
    );
    const session = result.rows[0] || null;
    if (!session) return null;
    if (requireActiveCall && !await getActiveCall(callId, userId)) return null;
    return session;
  };
  // PostgreSQL's conditional UPDATE is the cross-instance per-session mutex.
  // A returned SFU offer leaves the session fenced until /renegotiate accepts its answer.
  const claimMutation = async (sessionId, userId, callId, allowedStates = ["READY"]) => {
    const result = await pool.query(
      `UPDATE fynx_call_sfu_sessions SET mutation_state='IN_PROGRESS'
       WHERE session_id=$1 AND user_id=$2 AND call_id=$3 AND ended_at IS NULL
         AND mutation_state=ANY($4::text[])
       RETURNING session_id`,
      [sessionId, String(userId), callId, allowedStates]
    );
    return Boolean(result.rowCount);
  };
  const finishMutation = async (sessionId, nextState) => {
    if (!SESSION_STATES.includes(nextState)) throw new Error("Invalid SFU session state.");
    await pool.query(
      "UPDATE fynx_call_sfu_sessions SET mutation_state=$2 WHERE session_id=$1 AND mutation_state='IN_PROGRESS'",
      [sessionId, nextState]
    );
  };

  app.post("/api/calls/sfu/sessions", auth, rateLimit, route(async (req, res) => {
    const callId = req.body?.callId;
    if (!validId(callId, 80)) return res.status(400).json({ error: "valid callId is required" });
    if (!await getActiveCall(callId, req.user.sub)) return res.status(403).json({ error: "active call access required" });
    const created = await sfuRequest("/sessions/new");
    const sessionId = created?.sessionId;
    if (!validId(sessionId)) return res.status(502).json({ error: "media service returned an invalid session" });
    await pool.query(
      "INSERT INTO fynx_call_sfu_sessions(session_id,call_id,user_id,mutation_state) VALUES($1,$2,$3,'READY')",
      [sessionId, callId, String(req.user.sub)]
    );
    return res.status(201).json({ sessionId, mutationState: "READY" });
  }));

  app.post("/api/calls/sfu/sessions/:sessionId/publish", auth, rateLimit, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId, sessionDescription, tracks } = req.body || {};
    if (!validId(sessionId) || !validId(callId, 80) || !validSdp(sessionDescription) || sessionDescription.type !== "offer" ||
        !Array.isArray(tracks) || tracks.length < 1 || tracks.length > 2 ||
        tracks.some((track) => !validId(String(track?.mid || ""), 16) || !["microphone","camera"].includes(track?.trackName)) ||
        new Set(tracks.map((track) => track.trackName)).size !== tracks.length) {
      return res.status(400).json({ error: "valid offer and microphone/camera track list are required" });
    }
    if (!await getOwnedSession(sessionId, req.user.sub, callId)) return res.status(403).json({ error: "call session access denied" });
    if (!await claimMutation(sessionId, req.user.sub, callId)) {
      return res.status(409).json({ error: "this media session has another operation pending; complete renegotiation or recover the session first" });
    }
    try {
      const result = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}/tracks/new`, "POST", {
        sessionDescription,
        tracks: tracks.map(({ mid, trackName }) => ({ location: "local", mid, trackName }))
      });
      // Cloudflare's local-publication recipe returns an SDP answer for the
      // endpoint to apply. An SFU offer here is not the publish handshake.
      if (!validSdp(result?.sessionDescription) || result.sessionDescription.type !== "answer") {
        await finishMutation(sessionId, "RECOVERY_REQUIRED");
        return res.status(502).json({ error: "media service did not return the expected publication answer; replace or recover this session" });
      }
      const successful = Array.isArray(result?.tracks) ? result.tracks.filter((track) => !track.errorCode && !track.errorDescription) : [];
      for (const track of successful) {
        const source = tracks.find((item) => item.trackName === track.trackName);
        if (source) await pool.query(
          `INSERT INTO fynx_call_sfu_publications(call_id,user_id,session_id,track_name,mid,active)
           VALUES($1,$2,$3,$4,$5,TRUE)
           ON CONFLICT(session_id,track_name) DO UPDATE SET mid=EXCLUDED.mid,active=TRUE`,
          [callId, String(req.user.sub), sessionId, source.trackName, source.mid]
        );
      }
      if (successful.length !== tracks.length) {
        await finishMutation(sessionId, "RECOVERY_REQUIRED");
        return res.status(502).json({ error: "one or more media tracks could not be published; the session must be recovered before further changes" });
      }
      await finishMutation(sessionId, needsRenegotiation(result) ? "AWAITING_RENEGOTIATION" : "READY");
      return res.json({ ...result, mutationState: needsRenegotiation(result) ? "AWAITING_RENEGOTIATION" : "READY" });
    } catch (error) {
      await finishMutation(sessionId, "RECOVERY_REQUIRED");
      throw error;
    }
  }));

  app.post("/api/calls/sfu/sessions/:sessionId/subscribe", auth, rateLimit, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId, publisherSessionId, trackNames } = req.body || {};
    if (!validId(sessionId) || !validId(publisherSessionId) || !validId(callId, 80) ||
        !Array.isArray(trackNames) || trackNames.length < 1 || trackNames.length > 2 ||
        trackNames.some((name) => !["microphone","camera"].includes(name)) ||
        new Set(trackNames).size !== trackNames.length) {
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
    if (!await claimMutation(sessionId, req.user.sub, callId)) {
      return res.status(409).json({ error: "this media session has another operation pending; complete renegotiation or recover the session first" });
    }
    try {
      const result = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}/tracks/new`, "POST", {
        tracks: publisher.rows.map((track) => ({ location: "remote", sessionId: track.session_id, trackName: track.track_name }))
      });
      const receivedTracks = Array.isArray(result?.tracks) ? result.tracks : [];
      const successfulTracks = receivedTracks.filter((track) =>
        !track?.errorCode && !track?.errorDescription && validId(String(track?.mid || ""), 16)
      );
      if (successfulTracks.length !== trackNames.length ||
          (needsRenegotiation(result) && (!validSdp(result?.sessionDescription) || result.sessionDescription.type !== "offer"))) {
        await finishMutation(sessionId, "RECOVERY_REQUIRED");
        return res.status(502).json({ error: "one or more requested media tracks could not be subscribed or negotiated; replace or recover this session" });
      }
      const state = needsRenegotiation(result) ? "AWAITING_RENEGOTIATION" : "READY";
      await finishMutation(sessionId, state);
      return res.json({ ...result, mutationState: state });
    } catch (error) {
      await finishMutation(sessionId, "RECOVERY_REQUIRED");
      throw error;
    }
  }));

  // Authorized subscribers need a server-owned view of the publications in this call.
  // Publisher identity and track names come from the database, never client input.
  app.get("/api/calls/sfu/sessions", auth, rateLimit, route(async (req, res) => {
    const callId = String(req.query?.callId || "").trim();
    if (!validId(callId, 80)) return res.status(400).json({ error: "valid callId is required" });
    if (!await getActiveCall(callId, req.user.sub)) return res.status(403).json({ error: "active call access required" });
    const result = await pool.query(
      `SELECT s.session_id, s.user_id, ARRAY_AGG(p.track_name ORDER BY p.track_name) AS track_names
         FROM fynx_call_sfu_sessions s
         JOIN fynx_call_sfu_publications p ON p.session_id=s.session_id
        WHERE s.call_id=$1 AND s.user_id<>$2 AND s.ended_at IS NULL
          AND s.mutation_state='READY' AND p.active=TRUE
        GROUP BY s.session_id, s.user_id, s.created_at
        ORDER BY s.created_at ASC`,
      [callId, String(req.user.sub)]
    );
    const publishers = result.rows.map((row) => ({
      sessionId: String(row.session_id),
      userId: String(row.user_id),
      trackNames: [...new Set((Array.isArray(row.track_names) ? row.track_names : [])
        .filter((name) => name === "microphone" || name === "camera"))]
    })).filter((publisher) => validId(publisher.sessionId) &&
      /^\\d{1,19}$/.test(publisher.userId) && publisher.trackNames.length > 0);
    return res.json({ publishers });
  }));

  app.put("/api/calls/sfu/sessions/:sessionId/renegotiate", auth, rateLimit, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId, sessionDescription } = req.body || {};
    if (!validId(sessionId) || !validId(callId, 80) || !validSdp(sessionDescription) || sessionDescription.type !== "answer") {
      return res.status(400).json({ error: "valid SDP answer is required" });
    }
    if (!await getOwnedSession(sessionId, req.user.sub, callId)) return res.status(403).json({ error: "call session access denied" });
    if (!await claimMutation(sessionId, req.user.sub, callId, ["AWAITING_RENEGOTIATION"])) {
      return res.status(409).json({ error: "this media session is not waiting for a renegotiation answer" });
    }
    try {
      const result = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}/renegotiate`, "PUT", { sessionDescription });
      await finishMutation(sessionId, "READY");
      return res.json({ ...result, mutationState: "READY" });
    } catch (error) {
      await finishMutation(sessionId, "RECOVERY_REQUIRED");
      throw error;
    }
  }));

  app.post("/api/calls/sfu/sessions/:sessionId/close", auth, rateLimit, route(async (req, res) => {
    const { sessionId } = req.params;
    const { callId } = req.body || {};
    if (!validId(sessionId) || !validId(callId, 80)) return res.status(400).json({ error: "valid session and call IDs are required" });
    // Cleanup remains authorized by original session ownership even after a call has ended.
    if (!await getOwnedSession(sessionId, req.user.sub, callId, false)) return res.status(403).json({ error: "call session access denied" });
    if (!await claimMutation(sessionId, req.user.sub, callId, ["READY", "RECOVERY_REQUIRED"])) {
      return res.status(409).json({ error: "media session cleanup is already running; retry after it finishes" });
    }
    try {
      const snapshot = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}`, "GET");
      const activeMids = [...new Set((Array.isArray(snapshot?.tracks) ? snapshot.tracks : [])
        .filter((track) => track && track.status !== "inactive" && validId(String(track.mid || ""), 16))
        .map((track) => String(track.mid)))];
      if (activeMids.length) {
        const closed = await sfuRequest(`/sessions/${encodeURIComponent(sessionId)}/tracks/close`, "PUT", {
          tracks: activeMids.map((mid) => ({ mid })),
          force: true
        });
        const results = Array.isArray(closed?.tracks) ? closed.tracks : [];
        const failed = results.filter((track) => track?.errorCode || track?.errorDescription);
        const successfulMids = new Set(results.filter((track) => !track?.errorCode && !track?.errorDescription)
          .map((track) => String(track?.mid || "")).filter(Boolean));
        const unresolved = activeMids.filter((mid) => !successfulMids.has(mid));
        if (failed.length || unresolved.length) {
          await finishMutation(sessionId, "RECOVERY_REQUIRED");
          return res.status(502).json({ error: "some media tracks could not be confirmed closed; retry cleanup before discarding this session" });
        }
      }
      await pool.query("UPDATE fynx_call_sfu_publications SET active=FALSE WHERE session_id=$1", [sessionId]);
      await pool.query(
        "UPDATE fynx_call_sfu_sessions SET ended_at=NOW(),mutation_state='ENDED' WHERE session_id=$1 AND user_id=$2",
        [sessionId, String(req.user.sub)]
      );
      return res.json({ ok: true, tracksClosed: activeMids.length, sessionEnded: true });
    } catch (error) {
      await finishMutation(sessionId, "RECOVERY_REQUIRED");
      throw error;
    }
  }));
}
