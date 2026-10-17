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

async function authenticatedRequest(path, token, options = {}) {
  const method = options.method || "GET";
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20_000);
  try {
    const headers = { Accept: "application/json", Authorization: `Bearer ${token}`, "Cache-Control": "no-store" };
    const requestOptions = { method, headers, signal: controller.signal };
    if (options.body !== undefined) {
      headers["Content-Type"] = "application/json";
      requestOptions.body = JSON.stringify(options.body);
    }
    const response = await fetch(`${BASE_URL}${path}`, requestOptions);
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
  ["/api/social/feed", body => Array.isArray(body?.posts), "social feed read (posts array)"],
  ["/api/statuses", body => Array.isArray(body?.statuses), "statuses read (statuses array)"],
  ["/api/marketplace/discovery", body => Array.isArray(body?.listings) && typeof body?.hasMore === "boolean", "Marketplace discovery read (listings + hasMore)"],
];
for (const [path, validate, label] of checks) {
  const body = await authenticatedRequest(path, token);
  if (!validate(body)) throw new Error(`${label} returned an unexpected response shape: ${JSON.stringify(body).slice(0, 500)}`);
  console.log(`PASS: authenticated ${label}`);
}

async function verifyRealtimeSocket(accessToken, expectedUserId) {
  if (typeof globalThis.WebSocket !== "function") throw new Error("Node runtime does not expose WebSocket; realtime authentication cannot be certified.");
  const wsUrl = `${BASE_URL.replace(/^https:/, "wss:")}/realtime?token=${encodeURIComponent(accessToken)}`;
  await new Promise((resolve, reject) => {
    const socket = new globalThis.WebSocket(wsUrl);
    let settled = false;
    const finish = (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      try { socket.close(); } catch {}
      if (error) reject(error); else resolve();
    };
    const timeout = setTimeout(() => finish(new Error("Authenticated realtime socket did not deliver its own presence event within 12 seconds.")), 12_000);
    socket.addEventListener("message", event => {
      try {
        const raw = typeof event.data === "string" ? event.data : event.data?.toString?.() || "";
        const body = JSON.parse(raw);
        if (body?.type === "presence" && String(body?.userId) === String(expectedUserId) && body?.online === true) {
          finish(null);
        }
      } catch {
        // Ignore non-JSON or unrelated realtime events; only the expected authenticated
        // presence event is sufficient evidence that the server accepted this identity.
      }
    });
    socket.addEventListener("error", () => finish(new Error("Authenticated realtime WebSocket handshake failed.")), { once: true });
    socket.addEventListener("close", () => {
      if (!settled) finish(new Error("Realtime WebSocket closed before the authenticated presence event arrived."));
    }, { once: true });
  });
}
await verifyRealtimeSocket(token, login.user.id);
console.log("PASS: authenticated realtime WebSocket identity/presence event");

// Verify a real authenticated write -> read-back -> cleanup cycle. ONLY_ME keeps
// the temporary certification post private while still allowing its owner to read it.
const markerText = `FYNX_NETWORK_E2E_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
let createdPostId = "";
try {
  const created = await authenticatedRequest("/api/social/posts", token, {
    method: "POST",
    body: { text: markerText, visibility: "ONLY_ME" },
  });
  createdPostId = typeof created?.postId === "string" ? created.postId : "";
  if (!createdPostId) throw new Error(`Authenticated post creation did not return postId: ${JSON.stringify(created).slice(0, 500)}`);
  const readBack = await authenticatedRequest("/api/social/feed?limit=50", token);
  if (!Array.isArray(readBack?.posts) || !readBack.posts.some(post => String(post?.id) === createdPostId && post?.text === markerText)) {
    throw new Error("Authenticated post write did not appear in the owner's feed read-back.");
  }
  console.log("PASS: authenticated post write/read-back");
} finally {
  if (createdPostId) {
    const deleted = await authenticatedRequest(`/api/social/posts/${encodeURIComponent(createdPostId)}`, token, { method: "DELETE" });
    if (deleted?.ok !== true) throw new Error(`Temporary authenticated post cleanup failed for postId ${createdPostId}.`);
    console.log("PASS: authenticated E2E post cleanup");
  }
}
console.log(`GREEN: backend health/readiness, login, protected reads, and realtime handshake passed at ${BASE_URL}.`);