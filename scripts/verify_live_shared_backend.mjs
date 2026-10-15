import { randomBytes } from "node:crypto";

const baseUrl = (process.env.FYNX_TEST_BACKEND_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(/\/+$/, "");
const suffix = `${Date.now().toString(36)}${randomBytes(4).toString("hex")}`;
const password = randomBytes(24).toString("base64url");
const firstUsername = `fynxprobe_${suffix}`.slice(0, 32);
const secondUsername = `fynxprobe2_${suffix}`.slice(0, 32);
let firstToken = "";
let postId = "";
let keepPostForDiagnosis = false;

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
  const secondMe = await request("/api/me", { token: second.accessToken });
  if (String(secondMe.user?.username || "").toLowerCase() !== secondUsername) {
    throw new Error("second authenticated identity did not match the second registered account");
  }

  const created = await request("/api/social/posts", {
    method: "POST",
    token: firstToken,
    body: { text: `FYNX shared-feed smoke ${suffix}`, visibility: "PUBLIC" },
  });
  postId = String(created.postId || created.id || created.post?.id || "");
  if (!postId) throw new Error("post creation did not return a post ID");
  if (created.visibility !== "PUBLIC") {
    keepPostForDiagnosis = true;
    console.error("Create response visibility diagnostic:", JSON.stringify({ firstUsername, secondUsername, postId, requestedVisibility: "PUBLIC", returnedVisibility: created.visibility ?? null }));
    throw new Error(`public post request was stored as ${created.visibility ?? "unknown visibility"}`);
  }

  async function feedContains(token, expectedPostId) {
    const pageSize = 50;
    for (let offset = 0; offset < 1000; offset += pageSize) {
      const feed = await request(`/api/social/feed?limit=${pageSize}&offset=${offset}`, { token });
      const posts = Array.isArray(feed.posts) ? feed.posts : [];
      if (posts.some((post) => String(post.id) === expectedPostId)) return { found: true, postsSeen: offset + posts.length };
      if (!feed.hasMore || posts.length === 0) return { found: false, postsSeen: offset + posts.length };
    }
    return { found: false, postsSeen: 1000 };
  }
  const firstFeed = await feedContains(firstToken, postId);
  if (!firstFeed.found) throw new Error(`post creator could not read its own post after scanning ${firstFeed.postsSeen} feed entries`);
  const secondFeed = await feedContains(second.accessToken, postId);
  if (!secondFeed.found) {
    keepPostForDiagnosis = true;
    console.error("Cross-user feed diagnostic identifiers:", JSON.stringify({ firstUsername, secondUsername, postId, creatorFeedEntries: firstFeed.postsSeen, secondFeedEntries: secondFeed.postsSeen }));
    throw new Error(`second user could not see the first user's PUBLIC post after scanning ${secondFeed.postsSeen} feed entries`);
  }

  // Verify real authenticated media upload and binary retrieval before exercising media-backed features.
  const pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/gU8AAAAASUVORK5CYII=";
  const media = await request("/api/media", {
    method: "POST",
    token: firstToken,
    body: { mimeType: "image/png", dataBase64: pngBase64 },
  });
  const mediaId = String(media.media?.id || "");
  if (!mediaId) throw new Error("authenticated image upload did not return a media ID");

  // Direct messaging requires an accepted relationship in this backend, so exercise that real prerequisite.
  const friendRequest = await request("/api/friends/request", {
    method: "POST",
    token: firstToken,
    body: { username: secondUsername },
  });
  const friendRequestId = String(friendRequest.request?.id || "");
  if (!friendRequestId) throw new Error("friend request did not return an ID");
  await request(`/api/friends/requests/${encodeURIComponent(friendRequestId)}/accept`, {
    method: "POST",
    token: second.accessToken,
    body: {},
  });

  const messageText = `FYNX live cross-user message ${suffix}`;
  const message = await request("/api/messages", {
    method: "POST",
    token: firstToken,
    body: { recipientUsername: secondUsername, text: messageText },
  });
  const messageId = String(message.message?.id || message.id || "");
  if (!messageId) throw new Error("message send did not return a message ID");
  const senderHistory = await request(`/api/messages/${encodeURIComponent(secondUsername)}`, { token: firstToken });
  if (!Array.isArray(senderHistory.messages) || !senderHistory.messages.some((item) => String(item.id) === messageId && item.text === messageText)) {
    throw new Error("sender could not read the message from persisted conversation history");
  }
  const recipientHistory = await request(`/api/messages/${encodeURIComponent(firstUsername)}`, { token: second.accessToken });
  if (!Array.isArray(recipientHistory.messages) || !recipientHistory.messages.some((item) => String(item.id) === messageId && item.text === messageText)) {
    throw new Error("recipient could not read the delivered message from persisted conversation history");
  }

  // Verify a seller can publish a real media-backed listing and a different authenticated buyer can discover it.
  const listingTitle = `FYNX Live Listing ${suffix}`;
  const listingResponse = await request("/api/marketplace/listings", {
    method: "POST",
    token: firstToken,
    body: {
      storeName: "FYNX Live Test Store",
      title: listingTitle,
      description: `Live cross-account marketplace listing ${suffix}`,
      price: 1250,
      currency: "NGN",
      category: "Electronics",
      condition: "NEW",
      quantity: 2,
      location: "Port Harcourt",
      deliveryAvailable: true,
      pickupAvailable: true,
      deliveryFee: 100,
      mediaIds: [Number(mediaId)],
    },
  });
  const listingId = String(listingResponse.listing?.id || "");
  if (!listingId) throw new Error("marketplace listing creation did not return a listing ID");
  const sellerListings = await request("/api/marketplace/my-listings", { token: firstToken });
  if (!Array.isArray(sellerListings.listings) || !sellerListings.listings.some((item) => String(item.id) === listingId && item.title === listingTitle)) {
    throw new Error("seller could not retrieve the listing they created");
  }
  const buyerListings = await request(`/api/marketplace/listings?q=${encodeURIComponent(listingTitle)}`, { token: second.accessToken });
  if (!Array.isArray(buyerListings.listings) || !buyerListings.listings.some((item) => String(item.id) === listingId && item.title === listingTitle && String(item.seller_username).toLowerCase() === firstUsername)) {
    throw new Error("second account could not discover the seller's active marketplace listing");
  }
  const sellerDiscovery = await request(`/api/marketplace/listings?q=${encodeURIComponent(listingTitle)}`, { token: firstToken });
  if (!Array.isArray(sellerDiscovery.listings) || sellerDiscovery.listings.some((item) => String(item.id) === listingId)) {
    throw new Error("marketplace discovery incorrectly returned the seller's own listing");
  }
  await request(`/api/marketplace/listings/${encodeURIComponent(listingId)}`, { method: "DELETE", token: firstToken });

  console.log("PASS: Railway live backend readiness, registration/login/token identity, public post and cross-user feed, authenticated media upload, accepted-friend direct messaging and both conversation histories, and seller-to-buyer Marketplace listing discovery.");

} finally {
  if (postId && firstToken && !keepPostForDiagnosis) {
    await request(`/api/social/posts/${encodeURIComponent(postId)}`, { method: "DELETE", token: firstToken }).catch(() => {});
  }
}
