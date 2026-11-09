import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (name) => readFile(new URL(name, import.meta.url), "utf8");
const [server, cache, social, marketplace, bootstrap, pkg] = await Promise.all([
  read("./server.js"),
  read("./mediaPayloadCache.js"),
  read("./socialRoutes.js"),
  read("./marketplaceCompletion.js"),
  read("./socialMultiMediaBootstrap.js"),
  read("./package.json")
]);

function route(source, startMarker, endMarker) {
  const start = source.indexOf(startMarker);
  const end = source.indexOf(endMarker, start);
  assert.ok(start >= 0 && end > start, "could not find route: " + startMarker);
  return source.slice(start, end);
}

assert.match(server, /storage_provider TEXT NOT NULL DEFAULT 'postgres'/);
assert.match(server, /object_key TEXT/);
assert.match(server, /ALTER COLUMN data DROP NOT NULL/);
assert.match(server, /message_media_object_key_unique/);

const upload = route(server, 'app.post("/api/media"', "\n\nconst statusSchema");
assert.match(upload, /await mediaStorage\.putMediaObject/);
assert.match(upload, /storage_provider,\s*object_key/);
assert.match(upload, /await mediaStorage\.deleteMediaObject/);
assert.match(upload, /stored\.storageProvider === "postgres" \? data : null/);
assert.match(upload, /MAX_MEDIA_BYTES/);

const primary = route(server, 'app.get("/api/media/:id"', "\nfunction messageProjection()");
assert.match(primary, /auth/);
assert.match(primary, /SELECT mm\.mime_type,\s*mm\.storage_provider,\s*mm\.object_key\s+FROM message_media mm/);
assert.ok(primary.indexOf("if (!result.rows[0])") < primary.indexOf("redirectStoredMedia"));
assert.match(primary, /redirectStoredMedia/);
assert.match(primary, /getCachedMediaPayload/);

assert.match(cache, /SELECT mime_type, data, storage_provider, object_key FROM message_media/);
assert.match(cache, /mediaStorage\.getMediaObjectBytes\(row\.object_key, storageProvider\)/);
assert.match(cache, /storageProvider !== "neon"/);
assert.match(cache, /storage_provider/);

assert.match(social, /import \{ redirectStoredMedia \} from "\.\/mediaStorage\.js";/);
assert.match(social, /redirectStoredMedia/);
assert.match(social, /SELECT mm\.mime_type,mm\.storage_provider,mm\.object_key/);
assert.doesNotMatch(social, /SELECT mm\.mime_type,mm\.data/);
assert.match(social, /getCachedMediaPayload/);

assert.match(marketplace, /import \{ redirectStoredMedia \} from '\.\/mediaStorage\.js';/);
const marketplaceRoute = route(marketplace, "app.get('/api/marketplace/media/:id'", "\n  app.get('/api/marketplace/seller/orders'");
assert.match(marketplaceRoute, /SELECT mm\.mime_type,\s*mm\.storage_provider,\s*mm\.object_key/);
assert.ok(marketplaceRoute.indexOf("if (!result.rows[0])") < marketplaceRoute.indexOf("redirectStoredMedia"));
assert.match(marketplaceRoute, /getCachedMediaPayload/);

assert.match(bootstrap, /redirectStoredMedia/);
assert.match(bootstrap, /mm\.storage_provider, mm\.object_key/);
const storage = await read("./mediaStorage.js");
assert.match(storage, /FYNX_NEON_S3_ENDPOINT/);
assert.match(storage, /forcePathStyle: true/);
assert.match(storage, /sourceProvider/);
assert.match(pkg, /test-media-storage\.mjs/);
assert.match(pkg, /verify-media-object-storage\.mjs/);

console.log("FYNX MEDIA STORAGE INTEGRATION CONTRACT: GREEN");
console.log("Existing media IDs, authorization-before-redirect, Postgres fallback and all server media surfaces: PASS");
