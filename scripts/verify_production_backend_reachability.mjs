const BASE_URL = (process.env.FYNX_PRODUCTION_BASE_URL || "https://fynx-ai-backend.onrender.com").replace(/\/$/, "");
const TIMEOUT_MS = 45_000;
const RETRIES = 5;
const RETRY_DELAY_MS = 5_000;

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

console.log(`GREEN: production backend reachable at ${BASE_URL}; health and database readiness are confirmed.`);
