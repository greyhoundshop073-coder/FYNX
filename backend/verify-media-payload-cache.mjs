import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { getCachedMediaPayload, getMediaPayloadCacheStats } from "./mediaPayloadCache.js";

const read = (name) => readFile(new URL(name, import.meta.url), "utf8");
const [cacheSource, serverSource, marketplaceSource, socialSource, socialBootstrap, aiSource] = await Promise.all([
  read("./mediaPayloadCache.js"),
  read("./server.js"),
  read("./marketplaceCompletion.js"),
  read("./socialRoutes.js"),
  read("./socialMultiMediaBootstrap.js"),
  read("./aiConversationRoutes.js")
]);

assert.match(cacheSource, /MAX_CACHE_BYTES\s*=\s*32\s*\*\s*1024\s*\*\s*1024/);
assert.match(cacheSource, /MAX_CACHE_ITEM_BYTES\s*=\s*12\s*\*\s*1024\s*\*\s*1024/);
assert.match(cacheSource, /pendingLoads/);
assert.match(cacheSource, /cacheBytes\s*\+\s*data\.length\s*>\s*MAX_CACHE_BYTES/);

function route(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start);
  assert.ok(start >= 0 && end > start, `could not find route: ${startMarker}`);
  return source.slice(start, end);
}

const primaryMediaRoute = route(serverSource, 'app.get("/api/media/:id"', "\nfunction messageProjection()");
assert.match(primaryMediaRoute, /SELECT mm\.mime_type\s+FROM message_media mm/);
assert.doesNotMatch(primaryMediaRoute, /SELECT mm\.mime_type\s*,\s*mm\.data/);
assert.ok(primaryMediaRoute.indexOf("if (!result.rows[0])") < primaryMediaRoute.indexOf("getCachedMediaPayload"));

const marketplaceMediaRoute = route(marketplaceSource, "app.get('/api/marketplace/media/:id'", "\n  app.get('/api/marketplace/seller/orders'");
assert.match(marketplaceMediaRoute, /SELECT mm\.mime_type\s+FROM message_media mm/);
assert.doesNotMatch(marketplaceMediaRoute, /SELECT mm\.mime_type\s*,\s*mm\.data/);
assert.ok(marketplaceMediaRoute.indexOf("if (!result.rows[0])") < marketplaceMediaRoute.indexOf("getCachedMediaPayload"));

const socialMediaRoute = route(socialSource, "app.get('/api/social/media/:id'", "\n\n}");
assert.match(socialMediaRoute, /SELECT mm\.mime_type\s+FROM message_media mm/);
assert.doesNotMatch(socialMediaRoute, /SELECT mm\.mime_type\s*,\s*mm\.data/);
assert.ok(socialMediaRoute.indexOf("if(!r.rows[0])") < socialMediaRoute.indexOf("getCachedMediaPayload"));
assert.match(socialBootstrap, /SELECT mm\.mime_type\s*\n\s*FROM message_media mm/);
assert.doesNotMatch(socialBootstrap, /SELECT mm\.mime_type, mm\.data/);
assert.match(serverSource, /import \{ getCachedMediaPayload \} from "\.\/mediaPayloadCache\.js";/);
assert.match(marketplaceSource, /import \{ getCachedMediaPayload \} from '\.\/mediaPayloadCache\.js';/);
assert.match(socialSource, /import \{ getCachedMediaPayload \} from "\.\/mediaPayloadCache\.js";/);
assert.match(aiSource, /import \{ getCachedMediaPayload \} from "\.\/mediaPayloadCache\.js";/);
const aiImageLoader = route(aiSource, "async function loadImageInputs", "\\n}\\n\\nexport function registerFynxAiConversationRoutes");
assert.match(aiImageLoader, /SELECT id,mime_type FROM message_media/);
assert.doesNotMatch(aiImageLoader, /SELECT id,mime_type,data/);
assert.ok(aiImageLoader.indexOf("WHERE owner_id=$1") < aiImageLoader.indexOf("getCachedMediaPayload"));

// Two concurrent requests for one media id should perform one database payload read.
let queryCount = 0;
const smallPool = {
  async query(_sql, params) {
    queryCount += 1;
    await new Promise((resolve) => setTimeout(resolve, 15));
    assert.equal(params[0], "910000001");
    return { rows: [{ mime_type: "image/jpeg", data: Buffer.from("test-image-bytes") }] };
  }
};
const [first, concurrent] = await Promise.all([
  getCachedMediaPayload(smallPool, "910000001"),
  getCachedMediaPayload(smallPool, "910000001")
]);
assert.equal(queryCount, 1);
assert.equal(first.mimeType, "image/jpeg");
assert.equal(first.data.toString(), "test-image-bytes");
assert.equal(concurrent.data.toString(), "test-image-bytes");
await getCachedMediaPayload(smallPool, "910000001");
assert.equal(queryCount, 1);

// The cache is bounded even as unique uploads are requested.
const largePool = {
  async query(_sql, params) {
    return { rows: [{ mime_type: "video/mp4", data: Buffer.alloc(12 * 1024 * 1024, Number(params[0].slice(-1))) }] };
  }
};
for (const id of ["920000001", "920000002", "920000003"]) {
  const payload = await getCachedMediaPayload(largePool, id);
  assert.equal(payload.data.length, 12 * 1024 * 1024);
}
const stats = getMediaPayloadCacheStats();
assert.ok(stats.bytes <= stats.maxBytes, `cache exceeds bound: ${JSON.stringify(stats)}`);
assert.ok(stats.entries <= 3, `unexpected cache entry count: ${JSON.stringify(stats)}`);

console.log("MEDIA PAYLOAD CACHE CONTRACT: GREEN");
console.log(`Single-flight cache read: PASS; bounded cache: ${stats.entries} entries / ${stats.bytes} bytes / max ${stats.maxBytes} bytes`);
