const BASE_URL = (process.env.FYNX_TEST_BACKEND_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\/$/, "");
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

async function requestBinary(path, { token } = {}) {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { Accept: "*/*", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    signal: AbortSignal.timeout(20_000),
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  return { status: response.status, contentType: response.headers.get("content-type") || "", bytes };
}

const suffix = String(Date.now()).replace(/\D/g, "").slice(-10);
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

const currentUser = await request("/api/me", { token: sender.accessToken });
assert(currentUser.status === 200 && String(currentUser.data.user?.id) === String(sender.user?.id), `Authenticated /api/me failed: HTTP ${currentUser.status}`);

const mediaDownload = await requestBinary(`/api/media/${mediaId}`, { token: sender.accessToken });
assert(mediaDownload.status === 200, `Uploaded image download failed: HTTP ${mediaDownload.status}`);
assert(mediaDownload.contentType.toLowerCase().includes("image/png"), `Uploaded image returned unexpected content type: ${mediaDownload.contentType}`);
assert(mediaDownload.bytes.length > 30 && mediaDownload.bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), "Downloaded media is not the original PNG payload");

const post = await request("/api/social/posts", {
  method: "POST",
  token: sender.accessToken,
  body: { text: `Isolated backend smoke ${suffix}`, visibility: "ONLY_ME", mediaId, mediaType: "image" },
});
assert(post.status === 201 && post.data.postId, `Post creation failed: HTTP ${post.status} ${JSON.stringify(post.data)}`);

const friendRequest = await request("/api/friends/request", {
  method: "POST",
  token: sender.accessToken,
  body: { username: recipientName },
});
assert(friendRequest.status === 201 && friendRequest.data.request?.id, `Friend request failed: HTTP ${friendRequest.status} ${JSON.stringify(friendRequest.data)}`);
const accepted = await request(`/api/friends/requests/${friendRequest.data.request.id}/accept`, {
  method: "POST",
  token: recipient.accessToken,
  body: {},
});
assert(accepted.status === 200, `Friend request acceptance failed: HTTP ${accepted.status} ${JSON.stringify(accepted.data)}`);

const senderFollowsRecipient = await request(`/api/social/follow/${recipientName}`, {
  method: "POST",
  token: sender.accessToken,
  body: {},
});
assert(senderFollowsRecipient.status === 201, `Sender follow failed: HTTP ${senderFollowsRecipient.status} ${JSON.stringify(senderFollowsRecipient.data)}`);
const recipientFollowsSender = await request(`/api/social/follow/${senderName}`, {
  method: "POST",
  token: recipient.accessToken,
  body: {},
});
assert(recipientFollowsSender.status === 201, `Mutual follow failed: HTTP ${recipientFollowsSender.status} ${JSON.stringify(recipientFollowsSender.data)}`);

const message = await request("/api/messages", {
  method: "POST",
  token: sender.accessToken,
  body: { recipientUsername: recipientName, text: `Isolated backend message ${suffix}` },
});
const messageId = message.data.message?.id ?? message.data.id;
assert(message.status === 201 && messageId, `Message send failed: HTTP ${message.status} ${JSON.stringify(message.data)}`);

const senderHistory = await request(`/api/messages/${recipientName}`, { token: sender.accessToken });
assert(senderHistory.status === 200 && Array.isArray(senderHistory.data.messages), `Sender message history failed: HTTP ${senderHistory.status}`);
assert(senderHistory.data.messages.some((item) => String(item.id) === String(messageId) && item.text === `Isolated backend message ${suffix}`), "Sent message was not returned in sender conversation history");

const recipientHistory = await request(`/api/messages/${senderName}`, { token: recipient.accessToken });
assert(recipientHistory.status === 200 && Array.isArray(recipientHistory.data.messages), `Recipient message history failed: HTTP ${recipientHistory.status}`);
assert(recipientHistory.data.messages.some((item) => String(item.id) === String(messageId) && item.text === `Isolated backend message ${suffix}`), "Recipient could not retrieve the delivered message");

console.log("PASS: backend health/readiness, registration, authenticated identity, image upload/download integrity, private image post, direct message send, and sender/recipient history retrieval.");
console.log(`Smoke records: media=${mediaId}, post=${post.data.postId}, message=${messageId}`);
