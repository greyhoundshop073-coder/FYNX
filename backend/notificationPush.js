import { createSign } from "node:crypto";

const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "fynx-f0868";
const FIREBASE_SERVICE_ACCOUNT_JSON = process.env.FIREBASE_SERVICE_ACCOUNT_JSON || "";
const FIREBASE_CLIENT_EMAIL = process.env.FIREBASE_CLIENT_EMAIL || "";
const FIREBASE_PRIVATE_KEY = process.env.FIREBASE_PRIVATE_KEY || "";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const FCM_ENDPOINT = `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`;
const tokenCache = { value: null, expiresAt: 0 };
let schemaPromise;
let credentialsCache;

function credentials() {
  if (credentialsCache) return credentialsCache;
  if (FIREBASE_SERVICE_ACCOUNT_JSON) {
    try { credentialsCache = JSON.parse(FIREBASE_SERVICE_ACCOUNT_JSON); } catch { throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is invalid JSON"); }
  } else if (FIREBASE_CLIENT_EMAIL && FIREBASE_PRIVATE_KEY) {
    credentialsCache = { client_email: FIREBASE_CLIENT_EMAIL, private_key: FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n"), project_id: FIREBASE_PROJECT_ID };
  } else return null;
  if (!credentialsCache.client_email || !credentialsCache.private_key) throw new Error("Firebase server credentials are incomplete");
  return credentialsCache;
}

export function fynxPushConfigured() {
  try { return Boolean(FIREBASE_PROJECT_ID && credentials()); } catch { return false; }
}

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

async function accessToken() {
  const now = Date.now();
  if (tokenCache.value && tokenCache.expiresAt > now + 60_000) return tokenCache.value;
  const key = credentials();
  if (!key) throw new Error("Firebase server credentials are not configured");
  const issuedAt = Math.floor(now / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64Url(JSON.stringify({ iss: key.client_email, scope: FCM_SCOPE, aud: "https://oauth2.googleapis.com/token", iat: issuedAt, exp: issuedAt + 3600 }));
  const unsigned = `${header}.${claim}`;
  const signer = createSign("RSA-SHA256");
  signer.update(unsigned);
  signer.end();
  const assertion = `${unsigned}.${signer.sign(key.private_key, "base64url")}`;
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString()
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok || typeof body.access_token !== "string") throw new Error(`Firebase OAuth token request failed (${response.status})`);
  tokenCache.value = body.access_token;
  tokenCache.expiresAt = now + Number(body.expires_in || 3600) * 1000;
  return body.access_token;
}

async function ensureSchema(pool) {
  if (!pool) throw new Error("database not configured");
  if (!schemaPromise) schemaPromise = pool.query(`
    CREATE TABLE IF NOT EXISTS notification_preferences (
      user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
      enabled BOOLEAN NOT NULL DEFAULT TRUE,
      push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      reactions_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      comments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      friend_requests_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      messages_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      stories_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      group_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      marketplace_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      wallet_enabled BOOLEAN NOT NULL DEFAULT TRUE,
      quiet_mode BOOLEAN NOT NULL DEFAULT FALSE,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE TABLE IF NOT EXISTS fynx_notifications (
      id TEXT NOT NULL,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      type TEXT NOT NULL,
      title TEXT NOT NULL,
      message TEXT NOT NULL,
      target_id TEXT,
      source_username TEXT,
      route TEXT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      read_at TIMESTAMPTZ,
      PRIMARY KEY(id,user_id)
    );
    CREATE INDEX IF NOT EXISTS fynx_notifications_user_idx ON fynx_notifications(user_id,created_at DESC);
    CREATE TABLE IF NOT EXISTS fynx_notification_delivery (
      notification_id TEXT NOT NULL,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      provider TEXT NOT NULL,
      token TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'PENDING',
      attempts INTEGER NOT NULL DEFAULT 0,
      last_error TEXT,
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY(notification_id,user_id,provider,token)
    );
    CREATE INDEX IF NOT EXISTS fynx_notification_delivery_status_idx ON fynx_notification_delivery(status,updated_at);
    ALTER TABLE fynx_notifications ADD COLUMN IF NOT EXISTS route TEXT;
  `).catch(error => { schemaPromise = undefined; throw error; });
  return schemaPromise;
}

function preferenceColumn(type) {
  switch (type) {
    case "MESSAGE": return "messages_enabled";
    case "FRIEND_REQUEST": return "friend_requests_enabled";
    case "FOLLOW": return "friend_requests_enabled";
    case "STORY": return "stories_enabled";
    case "GROUP": return "group_enabled";
    case "COMMENT": return "comments_enabled";
    case "REACTION": return "reactions_enabled";
    case "MARKETPLACE_ORDER": return "marketplace_enabled";
    case "WALLET_ACTIVITY": return "wallet_enabled";
    case "REMINDER": return "reminders_enabled";
    default: return "push_enabled";
  }
}

async function pushAllowed(pool, userId, type) {
  const column = preferenceColumn(type);
  const result = await pool.query(`SELECT enabled,push_enabled,${column} AS category_enabled,quiet_mode FROM notification_preferences WHERE user_id=$1`, [userId]);
  const row = result.rows[0];
  if (!row) return true;
  return Boolean(row.enabled && row.push_enabled && row.category_enabled && !row.quiet_mode);
}

function safeText(value, fallback, max = 160) {
  const text = typeof value === "string" ? value.trim() : "";
  return (text || fallback).slice(0, max);
}

export async function queueFynxNotification(pool, {
  userId,
  type,
  title,
  message,
  targetId = null,
  sourceUsername = null,
  route = "fynx://home",
  notificationId = null
}) {
  try {
    if (!pool || !userId) return { stored: false, delivered: false, reason: "database unavailable" };
    await ensureSchema(pool);
    const id = String(notificationId || `push-${type}-${userId}-${targetId || "home"}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`).slice(0, 180);
    const safeTitle = safeText(title, "FYNX");
    const safeMessage = safeText(message, "You have a new FYNX notification.");
    await pool.query(`INSERT INTO fynx_notifications(id,user_id,type,title,message,target_id,source_username,route,created_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8,NOW()) ON CONFLICT(id,user_id) DO UPDATE SET title=EXCLUDED.title,message=EXCLUDED.message,target_id=EXCLUDED.target_id,source_username=EXCLUDED.source_username,route=EXCLUDED.route`, [id, userId, type, safeTitle, safeMessage, targetId == null ? null : String(targetId).slice(0, 200), sourceUsername == null ? null : String(sourceUsername).slice(0, 80), safeText(route, "fynx://home", 500)]);
    if (!(await pushAllowed(pool, userId, type))) return { stored: true, delivered: false, reason: "notification preference disabled", id };
    if (!fynxPushConfigured()) return { stored: true, delivered: false, reason: "firebase server credentials not configured", id };
    const devices = (await pool.query(`SELECT provider,token FROM notification_devices WHERE user_id=$1 AND enabled=TRUE AND provider='fcm'`, [userId])).rows;
    let delivered = 0;
    for (const device of devices) {
      const result = await sendFcm(pool, { userId, device, notificationId: id, type, title: safeTitle, message: safeMessage, route });
      if (result) delivered += 1;
    }
    return { stored: true, delivered: delivered > 0, deliveredCount: delivered, id };
  } catch (error) {
    console.error("FYNX notification delivery", error);
    return { stored: false, delivered: false, reason: "notification delivery failed" };
  }
}

async function sendFcm(pool, { userId, device, notificationId, type, title, message, route }) {
  const key = { notificationId, userId, provider: device.provider, token: device.token };
  let attempt = 0;
  while (attempt < 3) {
    attempt += 1;
    await pool.query(`INSERT INTO fynx_notification_delivery(notification_id,user_id,provider,token,status,attempts,updated_at) VALUES($1,$2,$3,$4,'PENDING',$5,NOW()) ON CONFLICT(notification_id,user_id,provider,token) DO UPDATE SET attempts=$5,updated_at=NOW()`, [key.notificationId,key.userId,key.provider,key.token,attempt]);
    try {
      const token = await accessToken();
      const response = await fetch(FCM_ENDPOINT, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json; UTF-8" },
        body: JSON.stringify({ message: {
          token: device.token,
          data: { notificationId: String(notificationId), type: String(type), title: String(title), body: String(message), route: String(route) },
          android: { priority: "high", ttl: "2419200s" },
        }})
      });
      const body = await response.json().catch(() => ({}));
      if (response.ok) {
        await pool.query(`UPDATE fynx_notification_delivery SET status='SENT',last_error=NULL,updated_at=NOW() WHERE notification_id=$1 AND user_id=$2 AND provider=$3 AND token=$4`, [key.notificationId,key.userId,key.provider,key.token]);
        return true;
      }
      const errorCode = body?.error?.status || body?.error?.details?.find?.(item => item?.errorCode)?.errorCode || "FCM_ERROR";
      const invalid = response.status === 404 || errorCode === "UNREGISTERED" || (response.status === 400 && errorCode === "INVALID_ARGUMENT");
      if (invalid) {
        await pool.query(`UPDATE notification_devices SET enabled=FALSE,updated_at=NOW() WHERE user_id=$1 AND provider='fcm' AND token=$2`, [userId, device.token]);
        await pool.query(`UPDATE fynx_notification_delivery SET status='INVALID',last_error=$5,updated_at=NOW() WHERE notification_id=$1 AND user_id=$2 AND provider=$3 AND token=$4`, [key.notificationId,key.userId,key.provider,key.token,String(errorCode).slice(0,200)]);
        return false;
      }
      if (response.status !== 429 && response.status < 500) {
        await pool.query(`UPDATE fynx_notification_delivery SET status='FAILED',last_error=$5,updated_at=NOW() WHERE notification_id=$1 AND user_id=$2 AND provider=$3 AND token=$4`, [key.notificationId,key.userId,key.provider,key.token,String(errorCode).slice(0,200)]);
        return false;
      }
      await new Promise(resolve => setTimeout(resolve, 1000 * 2 ** (attempt - 1)));
    } catch (error) {
      if (attempt >= 3) {
        await pool.query(`UPDATE fynx_notification_delivery SET status='FAILED',last_error=$5,updated_at=NOW() WHERE notification_id=$1 AND user_id=$2 AND provider=$3 AND token=$4`, [key.notificationId,key.userId,key.provider,key.token,String(error?.message || error).slice(0,200)]).catch(() => {});
        return false;
      }
      await new Promise(resolve => setTimeout(resolve, 1000 * 2 ** (attempt - 1)));
    }
  }
  return false;
}

export function registerFynxPushRoutes({ app, pool, auth }) {
  if (!app || !pool) return;
  app.get("/api/notification-delivery/status", auth, async (req, res) => {
    try {
      await ensureSchema(pool);
      const configured = fynxPushConfigured();
      const devices = await pool.query("SELECT provider,enabled,last_seen_at FROM notification_devices WHERE user_id=$1 ORDER BY updated_at DESC", [req.user.sub]);
      return res.json({ configured, devices: devices.rows });
    } catch (error) {
      console.error("notification delivery status", error);
      return res.status(500).json({ error: "notification delivery status unavailable" });
    }
  });
}
