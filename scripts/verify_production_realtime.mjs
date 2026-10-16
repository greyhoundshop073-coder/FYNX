const BASE_URL = (process.env.FYNX_PRODUCTION_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\/$/, "");
const username = (process.env.FYNX_E2E_USERNAME || "").trim();
const password = process.env.FYNX_E2E_PASSWORD || "";

if (!BASE_URL.startsWith("https://")) throw new Error("Production backend URL must use HTTPS.");
if (!username || !password) throw new Error("FYNX_E2E_USERNAME and FYNX_E2E_PASSWORD must be configured for authenticated network certification.");

const loginResponse = await fetch(`${BASE_URL}/api/auth/login`, {
  method: "POST",
  headers: { "Content-Type": "application/json", Accept: "application/json" },
  body: JSON.stringify({ username, password }),
  signal: AbortSignal.timeout(20_000),
});
const loginText = await loginResponse.text();
let login = {};
try { login = JSON.parse(loginText); } catch { /* keep response details out of logs */ }
if (!loginResponse.ok || typeof login.accessToken !== "string" || !login.accessToken) {
  throw new Error(`Authenticated login certification failed: HTTP ${loginResponse.status}.`);
}
console.log("PASS: production account login returned an access token.");

const meResponse = await fetch(`${BASE_URL}/api/me`, {
  headers: { Authorization: `Bearer ${login.accessToken}`, Accept: "application/json" },
  signal: AbortSignal.timeout(15_000),
});
if (!meResponse.ok) throw new Error(`Authenticated /api/me certification failed: HTTP ${meResponse.status}.`);
const me = await meResponse.json();
if (!me?.user || String(me.user.id ?? "") === "") throw new Error("Authenticated /api/me returned no user identity.");
console.log("PASS: authenticated /api/me returned the signed-in user.");

if (typeof WebSocket !== "function") throw new Error("This Node runtime does not provide WebSocket support.");
const wsUrl = `wss://${BASE_URL.slice("https://".length)}/realtime?token=${encodeURIComponent(login.accessToken)}`;
await new Promise((resolve, reject) => {
  let settled = false;
  const ws = new WebSocket(wsUrl);
  const timer = setTimeout(() => finish(new Error("Authenticated /realtime handshake timed out after 15 seconds.")), 15_000);
  function finish(error) {
    if (settled) return;
    settled = true;
    clearTimeout(timer);
    try { ws.close(1000, "network certification complete"); } catch { /* ignore cleanup errors */ }
    if (error) reject(error); else resolve();
  }
  ws.addEventListener("open", () => finish());
  ws.addEventListener("error", () => finish(new Error("Authenticated /realtime WebSocket connection failed.")));
  ws.addEventListener("close", event => {
    if (!settled && event.code !== 1000) finish(new Error(`Authenticated /realtime closed before opening (code ${event.code}).`));
  });
});
console.log("PASS: authenticated /realtime WebSocket handshake succeeded.");
console.log("GREEN: production login, authenticated API access, and realtime connection are reachable.");
