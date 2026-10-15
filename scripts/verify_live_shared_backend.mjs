import { randomBytes } from "node:crypto";

const baseUrl = (process.env.FYNX_TEST_BACKEND_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\/+$/, "");
const suffix = `${Date.now().toString(36)}${randomBytes(4).toString("hex")}`;
const password = randomBytes(24).toString("base64url");
const firstUsername = `fynxprobe_${suffix}`.slice(0, 32);
const secondUsername = `fynxprobe2_${suffix}`.slice(0, 32);
let firstToken = "";
let postId = "";

async function request(path, { method = "GET", token, body } = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers: {
      ...(body === undefined ? {} : { "content-type": "application/json" }),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(15000),
  });
  const text = await response.text();
  let payload = {};
  try { payload = text ? JSON.parse(text) : {}; } catch { payload = { message: text.slice(0, 200) }; }
  if (!response.ok) {
    throw new Error(`${method} ${path} returned HTTP ${response.status}: ${payload.error || payload.message || "request failed"}`);
  }
  return payload;
}

try {
  const ready = await request("/ready");
  if (ready.database !== "ready") throw new Error("testing backend database is not ready");

  const first = await request("/api/auth/register", {
    method: "POST",
    body: { displayName: "FYNX Network Probe One", username: firstUsername, phone: "", password },
  });
  firstToken = first.accessToken;
  const second = await request("/api/auth/register", {
    method: "POST",
    body: { displayName: "FYNX Network Probe Two", username: secondUsername, phone: "", password },
  });
  if (!firstToken || !second.accessToken) throw new Error("registration did not return both access tokens");

  const login = await request("/api/auth/login", {
    method: "POST",
    body: { username: firstUsername, password },
  });
  if (!login.accessToken) throw new Error("login did not return an access token");

  const me = await request("/api/me", { token: login.accessToken });
  if (String(me.user?.username || "").toLowerCase() !== firstUsername) {
    throw new Error("authenticated identity did not match the registered account");
  }

  const created = await request("/api/social/posts", {
    method: "POST",
    token: firstToken,
    body: { text: `FYNX shared-feed smoke ${suffix}`, visibility: "PUBLIC" },
  });
  postId = String(created.id || created.post?.id || "");
  if (!postId) throw new Error("post creation did not return a post ID");

  const feed = await request("/api/social/feed?limit=50", { token: second.accessToken });
  const visible = Array.isArray(feed.posts) && feed.posts.some((post) =>
    String(post.id) === postId && String(post.authorUsername || "").toLowerCase() === firstUsername
  );
  if (!visible) throw new Error("second user could not see the first user's public post");

  console.log("PASS: Railway test backend readiness, two-account registration, login, token identity, public post creation, and cross-user feed visibility.");
} finally {
  if (postId && firstToken) {
    await request(`/api/social/posts/${encodeURIComponent(postId)}`, { method: "DELETE", token: firstToken }).catch(() => {});
  }
}
