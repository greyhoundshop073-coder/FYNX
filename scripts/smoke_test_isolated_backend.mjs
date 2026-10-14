const BASE_URL = (process.env.FYNX_TEST_BACKEND_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\\/$/, "");
if (!BASE_URL.startsWith("https://")) throw new Error("Isolated backend smoke test requires HTTPS");

async function request(path, { method = "GET", token, body } = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(20_000),
  });
  const raw = await response.text();
  let data;
  try { data = raw ? JSON.parse(raw) : {}; } catch { data = { raw: raw.slice(0, 300) }; }
  return { status: response.status, data };
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

const suffix = String(process.env.GITHUB_RUN_ID || Date.now()).replace(/\\D/g, "").slice(-10);
const password = `FynxSmoke-${suffix}-Strong!`;

async function account(username) {
  const registration = await request("/api/auth/register", {
    method: "POST",
    body: { username, password, displayName: "FYNX Smoke Test", phone: "" },
  });
  if (registration.status === 201 && registration.data.accessToken) return registration.data;
  if (registration.status === 409) {
    const login = await request("/api/auth/login", { method: "POST", body: { username, password } });
    assert(login.status === 200 && login.data.accessToken, `Smoke account login failed for ${username}: HTTP ${login.status}`);
    return login.data;
  }
  throw new Error(`Smoke account registration failed for ${username}: HTTP ${registration.status} ${JSON.stringify(registration.data)}`);
}

const senderName = `fynxsmoke_${suffix}_a`;
const recipientName = `fynxsmoke_${suffix}_b`;
const sender = await account(senderName);
const recipient = await account(recipientName);
assert(sender.accessToken && recipient.accessToken, "Smoke accounts did not receive access tokens");

// Tiny valid PNG payload: exercises the real authenticated binary upload path.
const pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/gU8AAAAASUVORK5CYII=";
const upload = await request("/api/media", {
  method: "POST",
  token: sender.accessToken,
  body: { mimeType: "image/png", dataBase64: pngBase64 },
});
assert(upload.status === 201 && upload.data.media?.id, `Image upload failed: HTTP ${upload.status} ${JSON.stringify(upload.data)}`);
const mediaId = Number(upload.data.media.id);
assert(Number.isSafeInteger(mediaId) && mediaId > 0, "Image upload returned an invalid media ID");

const post = await request("/api/social/posts", {
  method: "POST",
  token: sender.accessToken,
  body: { text: `Isolated backend smoke ${suffix}`, visibility: "ONLY_ME", mediaId, mediaType: "image" },
});
assert(post.status === 201 && post.data.postId, `Post creation failed: HTTP ${post.status} ${JSON.stringify(post.data)}`);

const message = await request("/api/messages", {
  method: "POST",
  token: sender.accessToken,
  body: { recipientUsername: recipientName, text: `Isolated backend message ${suffix}` },
});
const messageId = message.data.message?.id ?? message.data.id;
assert(message.status === 201 && messageId, `Message send failed: HTTP ${message.status} ${JSON.stringify(message.data)}`);

console.log("PASS: isolated backend health/readiness preflight was followed by registration, image upload, private image post, and direct message.");
console.log(`Smoke records: media=${mediaId}, post=${post.data.postId}, message=${messageId}`);
