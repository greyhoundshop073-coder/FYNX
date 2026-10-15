const BASE_URL = (process.env.FYNX_BACKEND_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\/$/, "");
const USERNAME = (process.env.FYNX_E2E_USERNAME || "").trim();
const PASSWORD = process.env.FYNX_E2E_PASSWORD || "";
const CHAT_TARGET = (process.env.FYNX_E2E_CHAT_TARGET || "").trim().replace(/^@+/, "").toLowerCase();
const TIMEOUT_MS = 30_000;

if (!BASE_URL.startsWith("https://")) throw new Error("FYNX backend smoke test requires HTTPS.");
if (!USERNAME || !PASSWORD) throw new Error("FYNX_E2E_USERNAME and FYNX_E2E_PASSWORD must be configured; live auth cannot be skipped.");

async function request(path, { method = "GET", token, body } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const headers = { Accept: "application/json", "Cache-Control": "no-store" };
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(`${BASE_URL}${path}`, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: controller.signal
    });
    const raw = await response.text();
    let data = {};
    try { data = raw ? JSON.parse(raw) : {}; } catch { data = { raw: raw.slice(0, 300) }; }
    if (!response.ok) throw new Error(`${method} ${path} returned HTTP ${response.status}: ${JSON.stringify(data)}`);
    return data;
  } finally {
    clearTimeout(timer);
  }
}

const health = await request("/health");
if (health?.ok !== true || health?.service !== "fynx-backend" || health?.database !== "ready") {
  throw new Error(`Backend health is not ready: ${JSON.stringify(health)}`);
}
const ready = await request("/ready");
if (ready?.ok !== true || ready?.database !== "ready") {
  throw new Error(`Backend readiness is not ready: ${JSON.stringify(ready)}`);
}

const login = await request("/api/auth/login", {
  method: "POST", body: { username: USERNAME, password: PASSWORD }
});
const token = typeof login?.accessToken === "string" ? login.accessToken : "";
const loggedInUser = login?.user;
if (!token || !loggedInUser?.username) throw new Error("Login returned no authenticated user/token.");

const me = await request("/api/me", { token });
if (String(me?.user?.id) !== String(loggedInUser.id) ||
    String(me?.user?.username || "").toLowerCase() !== String(loggedInUser.username).toLowerCase()) {
  throw new Error("Bearer token validation failed: /api/me did not return the logged-in account.");
}

// Verify the authenticated API surfaces used by the app, not just /health.
await request("/api/social/feed", { token });
await request("/api/statuses", { token });
await request("/api/marketplace/discovery", { token });
await request(`/api/messages/${encodeURIComponent(loggedInUser.username)}`, { token });

// Exercise a real authenticated write, then remove the smoke post so the test account is clean.
let postId = "";
try {
  const post = await request("/api/social/posts", {
    method: "POST", token,
    body: { text: `FYNX live network smoke ${Date.now()}`, visibility: "ONLY_ME" }
  });
  postId = String(post?.postId || "");
  if (!/^\d+$/.test(postId)) throw new Error("Create-post response did not include a valid postId.");
} finally {
  if (postId) await request(`/api/social/posts/${encodeURIComponent(postId)}`, { method: "DELETE", token });
}

if (CHAT_TARGET) {
  let messageId = "";
  try {
    const sent = await request("/api/messages", {
      method: "POST", token,
      body: { recipientUsername: CHAT_TARGET, text: `FYNX live network smoke ${Date.now()}` }
    });
    messageId = String(sent?.message?.id || "");
    if (!/^\d+$/.test(messageId)) throw new Error("Send-message response did not include a valid message ID.");
  } finally {
    if (messageId) await request(`/api/messages/${encodeURIComponent(messageId)}`, { method: "DELETE", token });
  }
  console.log("PASS: authenticated login, token validation, feed/status/Marketplace reads, post create/delete, and message send/delete.");
} else {
  console.log("WARN: FYNX_E2E_CHAT_TARGET is not configured; message send was not exercised.");
  console.log("PASS: authenticated login, token validation, feed/status/Marketplace reads, and post create/delete.");
}
console.log(`LIVE FYNX BACKEND SESSION GREEN: ${BASE_URL}`);
