import { randomBytes } from "node:crypto";
import { appendFileSync } from "node:fs";

const BASE_URL = (process.env.FYNX_BACKEND_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\/$/, "");
const TIMEOUT_MS = 30_000;

if (!BASE_URL.startsWith("https://")) throw new Error("FYNX backend smoke test requires HTTPS.");

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

function makeAccount(label, suffix) {
  const random = randomBytes(8).toString("hex");
  const phoneDigits = String(randomBytes(4).readUInt32BE(0) % 100_000_000).padStart(8, "0");
  return {
    username: `fynxnet_${random}_${suffix}`,
    password: randomBytes(24).toString("base64url"),
    displayName: `FYNX Network Smoke ${label}`,
    phone: `+1555${phoneDigits}`
  };
}

async function registerAccount(account) {
  const result = await request("/api/auth/register", {
    method: "POST",
    body: {
      username: account.username,
      password: account.password,
      displayName: account.displayName,
      phone: account.phone
    }
  });
  if (!result?.user?.id || !result?.accessToken) {
    throw new Error("Registration did not return a user and access token.");
  }
  return result;
}

const health = await request("/health");
console.log("Backend /health:", JSON.stringify(health));
if (health?.ok !== true || health?.service !== "fynx-backend") {
  throw new Error(`Backend liveness is not ready: ${JSON.stringify(health)}`);
}
const ready = await request("/ready");
console.log("Backend /ready:", JSON.stringify(ready));
if (ready?.ok !== true || ready?.service !== "fynx-backend" || ready?.database !== "ready") {
  throw new Error(`Backend/database readiness is not ready: ${JSON.stringify(ready)}`);
}

// Use newly registered isolated test accounts. Existing E2E credentials may belong to
// another environment and must not be assumed to exist in this testing database.
const sender = makeAccount("Sender", "a");
const recipient = makeAccount("Recipient", "b");
await registerAccount(sender);
await registerAccount(recipient);

const login = await request("/api/auth/login", {
  method: "POST", body: { username: sender.username, password: sender.password }
});
const token = typeof login?.accessToken === "string" ? login.accessToken : "";
const loggedInUser = login?.user;
if (!token || String(loggedInUser?.username || "").toLowerCase() !== sender.username.toLowerCase()) {
  throw new Error("Fresh test account login returned no matching user/token.");
}

// Pass this isolated account to the optional emulator runtime test in later workflow steps.
if (process.env.GITHUB_ENV) {
  appendFileSync(process.env.GITHUB_ENV, `FYNX_RUNTIME_USERNAME=${sender.username}\nFYNX_RUNTIME_PASSWORD=${sender.password}\n`);
}

const me = await request("/api/me", { token });
if (String(me?.user?.id) !== String(loggedInUser.id) ||
    String(me?.user?.username || "").toLowerCase() !== sender.username.toLowerCase()) {
  throw new Error("Bearer token validation failed: /api/me did not return the logged-in account.");
}

// Verify the authenticated API surfaces used by the app, not just /health.
await request("/api/social/feed", { token });
await request("/api/statuses", { token });
await request("/api/marketplace/discovery", { token });
await request("/api/marketplace/discovery?category=Electronics", { token });
await request("/api/marketplace/discovery?location=Lagos", { token });
await request(`/api/messages/${encodeURIComponent(loggedInUser.username)}`, { token });

// Exercise a real authenticated post write and remove it immediately.
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

// Exercise a real authenticated message write between isolated smoke accounts, then delete it.
let messageId = "";
try {
  const sent = await request("/api/messages", {
    method: "POST", token,
    body: { recipientUsername: recipient.username, text: `FYNX live network smoke ${Date.now()}` }
  });
  messageId = String(sent?.message?.id || "");
  if (!/^\d+$/.test(messageId)) throw new Error("Send-message response did not include a valid message ID.");
} finally {
  if (messageId) await request(`/api/messages/${encodeURIComponent(messageId)}`, { method: "DELETE", token });
}

console.log("PASS: registration, login, token validation, feed/status/Marketplace reads, post create/delete, and message send/delete.");
console.log(`LIVE FYNX BACKEND SESSION GREEN: ${BASE_URL}`);
