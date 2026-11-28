const TURN_API_BASE = "https://rtc.live.cloudflare.com/v1";
const TURN_CREDENTIALS_PATH = "credentials/generate-ice-servers";
const DEFAULT_TURN_TTL_SECONDS = 3600;
const MAX_TURN_TTL_SECONDS = 172800;
const TURN_REQUEST_TIMEOUT_MS = 8000;

const TURN_URL_PATTERN = /^turns?:turn\.cloudflare\.com:(?:3478|443|80|5349)(?:\?transport=(?:udp|tcp))?$/i;
const USER_ID_PATTERN = /^\d{1,19}$/;
const CALL_ID_PATTERN = /^call[-_][a-z0-9_-]{1,70}$/i;

function isMissingTable(error) {
  return error?.code === "42P01";
}

function validSecretConfig(env) {
  const keyId = String(env.CLOUDFLARE_TURN_KEY_ID || "").trim();
  const keySecret = String(env.CLOUDFLARE_TURN_KEY_SECRET || "").trim();
  return {
    keyId,
    keySecret,
    configured: /^[a-f0-9]{32}$/i.test(keyId) && /^[a-f0-9]{64}$/i.test(keySecret)
  };
}

function readTtl(env) {
  const raw = env.CLOUDFLARE_TURN_CREDENTIAL_TTL_SECONDS;
  if (raw == null || String(raw).trim() === "") return DEFAULT_TURN_TTL_SECONDS;
  if (!/^\d+$/.test(String(raw).trim())) return null;
  const ttl = Number(raw);
  return Number.isSafeInteger(ttl) && ttl >= 300 && ttl <= MAX_TURN_TTL_SECONDS ? ttl : null;
}

function setNoStoreHeaders(res) {
  res.setHeader("Cache-Control", "private, no-store, max-age=0");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Vary", "Authorization");
}

function responseError(res, status, code, message) {
  return res.status(status).json({ code, error: message });
}

/**
 * A TURN credential can only be minted while the requesting account is a
 * participant in the exact active FYNX call. Supports direct/Marketplace
 * inspection calls and authenticated group calls.
 */
export async function isActiveFynxCallParticipant(pool, callId, userId, resolveActiveCall = null) {
  if (!pool || typeof pool.query !== "function") {
    throw new Error("FYNX call authorization storage is unavailable.");
  }
  const call = String(callId || "").trim();
  const user = String(userId || "").trim();
  if (!CALL_ID_PATTERN.test(call) || !USER_ID_PATTERN.test(user)) return false;
  if (typeof resolveActiveCall === "function") return Boolean(await resolveActiveCall(call, user));

  const direct = await pool.query(
    `SELECT 1
       FROM fynx_realtime_calls
      WHERE id=$1 AND status='ACTIVE'
        AND (caller_id=$2 OR callee_id=$2)
      LIMIT 1`,
    [call, user]
  );
  if (direct.rowCount > 0) return true;

  try {
    const group = await pool.query(
      `SELECT 1
         FROM fynx_realtime_group_call_participants p
         JOIN fynx_realtime_group_calls c ON c.id=p.call_id
        WHERE p.call_id=$1 AND p.user_id=$2
          AND p.status='JOINED' AND c.status='ACTIVE'
        LIMIT 1`,
      [call, user]
    );
    return group.rowCount > 0;
  } catch (error) {
    // The group-call schema is created lazily. A direct call remains usable
    // before the first group call has caused those tables to be created.
    if (isMissingTable(error)) return false;
    throw error;
  }
}

function parseCloudflareIceServers(payload) {
  if (!Array.isArray(payload?.iceServers)) return null;
  const turnServer = payload.iceServers.find((server) =>
    server && typeof server === "object" &&
    typeof server.username === "string" && server.username.length > 0 &&
    typeof server.credential === "string" && server.credential.length > 0
  );
  if (!turnServer) return null;
  const urls = Array.isArray(turnServer.urls) ? turnServer.urls : [turnServer.urls];
  const turnUrls = [...new Set(urls.filter((url) =>
    typeof url === "string" &&
    !/:53(?:\D|$)/.test(url) &&
    TURN_URL_PATTERN.test(url)
  ))];
  if (!turnUrls.length || turnServer.username.length > 512 || turnServer.credential.length > 512) return null;
  return {
    iceServers: [
      { urls: ["stun:stun.cloudflare.com:3478"] },
      {
        urls: turnUrls,
        username: turnServer.username,
        credential: turnServer.credential,
        credentialType: "password"
      }
    ]
  };
}

/**
 * Register the server-only Cloudflare TURN credential route.
 *
 * `rateLimit` is mandatory so the application can apply its shared request
 * limiter (rather than relying on a non-durable per-process counter here).
 * The long-lived TURN key secret is never returned to the Android client.
 */
export function registerCloudflareTurnRoutes({
  app,
  pool,
  auth,
  rateLimit,
  fetchImpl = fetch,
  env = process.env,
  timeoutMs = TURN_REQUEST_TIMEOUT_MS,
  resolveActiveCall = null
}) {
  if (!app || typeof app.post !== "function" ||
      !pool || typeof pool.query !== "function" ||
      typeof auth !== "function" || typeof rateLimit !== "function" ||
      typeof fetchImpl !== "function") {
    throw new Error("Cloudflare TURN routes require the app, shared pool, authentication, rate limiting and fetch.");
  }
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 30000) {
    throw new RangeError("Cloudflare TURN request timeout must be between 1 and 30000 milliseconds.");
  }

  app.post("/api/calls/ice-servers", auth, rateLimit, async (req, res) => {
    setNoStoreHeaders(res);
    const userId = String(req.user?.sub || "").trim();
    const callId = String(req.body?.callId || "").trim();
    if (!USER_ID_PATTERN.test(userId)) {
      return responseError(res, 401, "AUTH_REQUIRED", "Sign in is required to configure call connectivity.");
    }
    if (!CALL_ID_PATTERN.test(callId)) {
      return responseError(res, 400, "INVALID_CALL_ID", "A valid active call is required.");
    }

    let allowed;
    try {
      allowed = await isActiveFynxCallParticipant(pool, callId, userId, resolveActiveCall);
    } catch {
      return responseError(res, 503, "CALL_AUTHORIZATION_UNAVAILABLE", "Call connectivity is temporarily unavailable. Please retry.");
    }
    if (!allowed) {
      return responseError(res, 403, "CALL_ACCESS_DENIED", "Only an active call participant can request connection credentials.");
    }

    const { keyId, keySecret, configured } = validSecretConfig(env);
    if (!configured) {
      return responseError(res, 503, "TURN_NOT_CONFIGURED", "Call relay service is not configured yet.");
    }
    const ttl = readTtl(env);
    if (ttl == null) {
      return responseError(res, 503, "TURN_CONFIGURATION_INVALID", "Call relay service configuration is invalid.");
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const upstream = await fetchImpl(
        `${TURN_API_BASE}/turn/keys/${encodeURIComponent(keyId)}/${TURN_CREDENTIALS_PATH}`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${keySecret}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({ ttl }),
          signal: controller.signal
        }
      );
      if (!upstream.ok) {
        return responseError(res, 503, "TURN_PROVIDER_UNAVAILABLE", "Call relay credentials could not be issued. Please retry.");
      }
      let payload;
      try {
        payload = await upstream.json();
      } catch {
        return responseError(res, 503, "TURN_PROVIDER_INVALID_RESPONSE", "Call relay credentials could not be issued. Please retry.");
      }
      const normalized = parseCloudflareIceServers(payload);
      if (!normalized) {
        return responseError(res, 503, "TURN_PROVIDER_INVALID_RESPONSE", "Call relay service returned an invalid connection configuration.");
      }
      const now = Date.now();
      return res.status(200).json({
        ...normalized,
        expiresInSeconds: ttl,
        refreshAfterSeconds: Math.max(60, Math.floor(ttl * 0.75)),
        expiresAt: new Date(now + ttl * 1000).toISOString()
      });
    } catch {
      return responseError(res, 503, "TURN_PROVIDER_UNAVAILABLE", "Call relay credentials could not be issued. Please check your connection and retry.");
    } finally {
      clearTimeout(timeout);
    }
  });
}
