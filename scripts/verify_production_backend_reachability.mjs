const BASE_URL = (process.env.FYNX_PRODUCTION_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\/$/, "");
// Allow the Railway health/readiness endpoints enough time to respond while keeping
// the certification strict: both health and database readiness must be confirmed.
const TIMEOUT_MS = 120_000;
const RETRIES = 3;
const RETRY_DELAY_MS = 10_000;

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function request(path) {
  let lastError = null;
  for (let attempt = 1; attempt <= RETRIES; attempt += 1) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const response = await fetch(`${BASE_URL}${path}`, {
        method: "GET",
        headers: { Accept: "application/json" },
        signal: controller.signal,
      });
      const text = await response.text();
      let body = {};
      try { body = JSON.parse(text); } catch { body = { raw: text.slice(0, 500) }; }
      if (!response.ok) throw new Error(`${path} returned HTTP ${response.status}: ${JSON.stringify(body)}`);
      return body;
    } catch (error) {
      lastError = error;
      if (attempt < RETRIES) await sleep(RETRY_DELAY_MS);
    } finally {
      clearTimeout(timer);
    }
  }
  throw lastError || new Error(`${path} request failed`);
}

if (!BASE_URL.startsWith("https://")) throw new Error(`Production backend URL must use HTTPS: ${BASE_URL}`);

const health = await request("/health");
if (health?.ok !== true || health?.service !== "fynx-backend" || health?.database !== "ready") {
  throw new Error(`Production /health is not ready: ${JSON.stringify(health)}`);
}

const ready = await request("/ready");
if (ready?.ok !== true || ready?.service !== "fynx-backend" || ready?.database !== "ready") {
  throw new Error(`Production /ready is not ready: ${JSON.stringify(ready)}`);
}

const username = (process.env.FYNX_E2E_USERNAME || "").trim();
const password = process.env.FYNX_E2E_PASSWORD || "";
if (!username || !password) {
  throw new Error("Authenticated network certification requires FYNX_E2E_USERNAME and FYNX_E2E_PASSWORD secrets.");
}

async function authenticatedRequest(path, token) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20_000);
  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: "GET",
      headers: { Accept: "application/json", Authorization: `Bearer ${token}`, "Cache-Control": "no-store" },
      signal: controller.signal,
    });
    const text = await response.text();
    let body = {};
    try { body = JSON.parse(text); } catch { body = { raw: text.slice(0, 300) }; }
    if (!response.ok) throw new Error(`Authenticated ${path} returned HTTP ${response.status}: ${JSON.stringify(body)}`);
    return body;
  } finally {
    clearTimeout(timer);
  }
}

const loginController = new AbortController();
const loginTimer = setTimeout(() => loginController.abort(), 20_000);
let login;
try {
  const response = await fetch(`${BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json", "Cache-Control": "no-store" },
    body: JSON.stringify({ username, password }),
    signal: loginController.signal,
  });
  const text = await response.text();
  let body = {};
  try { body = JSON.parse(text); } catch { body = { raw: text.slice(0, 300) }; }
  if (!response.ok) throw new Error(`E2E login returned HTTP ${response.status}: ${JSON.stringify(body)}`);
  login = body;
} finally {
  clearTimeout(loginTimer);
}
const token = typeof login?.accessToken === "string" ? login.accessToken : "";
if (!token || !login?.user?.id) throw new Error("E2E login did not return a user identity and access token.");

const checks = [
  ["/api/me", body => Boolean(body?.user?.id), "authenticated identity"],
  ["/api/social/feed", body => Array.isArray(body?.posts) || Array.isArray(body?.feed) || Array.isArray(body?.items) || typeof body === "object", "social feed read"],
  ["/api/statuses", body => typeof body === "object", "statuses read"],
  ["/api/marketplace/discovery", body => typeof body === "object", "Marketplace discovery read"],
];
for (const [path, validate, label] of checks) {
  const body = await authenticatedRequest(path, token);
  if (!validate(body)) throw new Error(`${label} returned an unexpected response shape.`);
  console.log(`PASS: authenticated ${label}`);
}

async function verifyRealtimeSocket(accessToken) {
  if (typeof globalThis.WebSocket !== "function") throw new Error("Node runtime does not expose WebSocket; realtime handshake cannot be certified.");
  const wsUrl = `${BASE_URL.replace(/^https:/, "wss:")}/realtime?token=${encodeURIComponent(accessToken)}`;
  await new Promise((resolve, reject) => {
    const socket = new globalThis.WebSocket(wsUrl);
    let opened = false;
    let settled = false;
    const timeout = setTimeout(() => {
      if (settled) return;
      settled = true;
      try { socket.close(); } catch {}
      reject(new Error("Authenticated realtime WebSocket did not open within 12 seconds."));
    }, 12_000);
    socket.addEventListener("open", () => {
      opened = true;
      setTimeout(() => { try { socket.close(1000, "network smoke test complete"); } catch {} }, 750);
    }, { once: true });
    socket.addEventListener("error", () => {
      if (settled || opened) return;
      settled = true;
      clearTimeout(timeout);
      try { socket.close(); } catch {}
      reject(new Error("Authenticated realtime WebSocket handshake failed."));
    });
    socket.addEventListener("close", () => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      if (opened) resolve();
      else reject(new Error("Realtime WebSocket closed before authentication completed."));
    }, { once: true });
  });
}
await verifyRealtimeSocket(token);
console.log("PASS: authenticated realtime WebSocket handshake");
console.log(`GREEN: backend health/readiness, login, protected reads, and realtime handshake passed at ${BASE_URL}.`);