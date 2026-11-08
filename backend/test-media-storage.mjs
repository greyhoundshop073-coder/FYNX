import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { createMediaStorage, MediaStorageConfigurationError } from "./mediaStorage.js";

const UUID = "f35c2d80-4f45-4a33-b5b2-bb94928f9a11";
const calls = [];
const optionsSeen = [];
const fakeClient = {
  async send(command) {
    calls.push(command);
    if (command.constructor.name === "GetObjectCommand") {
      return { Body: (async function* () { yield Buffer.from("FYNX-"); yield Buffer.from("media-bytes"); })() };
    }
    return {};
  }
};
const common = {
  FYNX_NEON_S3_ENDPOINT: "https://br-floral-shape.storage.c-7.us-east-2.aws.neon.tech",
  FYNX_NEON_S3_REGION: "us-east-2",
  FYNX_NEON_S3_ACCESS_KEY_ID: "nak_test_access_key",
  FYNX_NEON_S3_SECRET_ACCESS_KEY: "nsk_test_secret_access_key",
  FYNX_NEON_STORAGE_BUCKET: "fynx-media",
  FYNX_R2_ACCOUNT_ID: "0123456789abcdef0123456789abcdef",
  FYNX_R2_ACCESS_KEY_ID: "test-r2-access-key",
  FYNX_R2_SECRET_ACCESS_KEY: "test-r2-secret-access-key-value",
  FYNX_R2_BUCKET: "fynx-r2-media"
};

const storage = createMediaStorage({
  env: { ...common, FYNX_MEDIA_STORAGE_PROVIDER: "neon" },
  clientFactory(options) { optionsSeen.push(options); return fakeClient; },
  async signUrlFactory(_client, command, options) {
    calls.push(command);
    assert.equal(command.constructor.name, "GetObjectCommand");
    assert.equal(options.expiresIn, 120);
    return "https://signed.example/fynx-media";
  },
  idFactory: () => UUID
});

assert.equal(storage.provider, "neon");
assert.equal(storage.isObjectStorageEnabled, true);
assert.equal(storage.isConfigured, true);
const stored = await storage.putMediaObject({ data: Buffer.from("new-media-bytes"), mimeType: "image/webp" });
assert.deepEqual(stored, { storageProvider: "neon", objectKey: "media/" + UUID });
assert.equal(calls[0].constructor.name, "PutObjectCommand");
assert.equal(calls[0].input.Bucket, "fynx-media");
assert.equal(calls[0].input.Key, "media/" + UUID);
assert.equal(calls[0].input.ContentType, "image/webp");
assert.equal(calls[0].input.ContentLength, Buffer.byteLength("new-media-bytes"));
assert.equal(calls[0].input.CacheControl, "private, max-age=60");
assert.equal(Buffer.from(calls[0].input.Body).toString(), "new-media-bytes");
assert.deepEqual(optionsSeen[0], {
  region: "us-east-2",
  endpoint: common.FYNX_NEON_S3_ENDPOINT,
  credentials: { accessKeyId: common.FYNX_NEON_S3_ACCESS_KEY_ID, secretAccessKey: common.FYNX_NEON_S3_SECRET_ACCESS_KEY },
  forcePathStyle: true
});

const signedUrl = await storage.createDownloadUrl("media/" + UUID);
assert.equal(signedUrl, "https://signed.example/fynx-media");
assert.equal(calls.at(-1).constructor.name, "GetObjectCommand");
assert.equal(calls.at(-1).input.Bucket, "fynx-media");
const downloaded = await storage.getMediaObjectBytes("media/" + UUID, "neon");
assert.equal(downloaded.toString(), "FYNX-media-bytes");

// Dual-read support is required during a provider migration: Neon can serve new writes
// while rows that still point at R2 remain readable until their verified migration.
const r2Url = await storage.createDownloadUrl("media/" + UUID, "r2");
assert.equal(r2Url, "https://signed.example/fynx-media");
await storage.getMediaObjectBytes("media/" + UUID, "r2");
await storage.deleteMediaObject("media/" + UUID, "r2");
assert.deepEqual(optionsSeen[1], {
  region: "auto",
  endpoint: "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com",
  credentials: { accessKeyId: common.FYNX_R2_ACCESS_KEY_ID, secretAccessKey: common.FYNX_R2_SECRET_ACCESS_KEY }
});

await assert.rejects(storage.createDownloadUrl("../private-file"), /Invalid FYNX media object key/);
await assert.rejects(storage.createDownloadUrl("media/" + UUID, "neon", 301), /expiry must be between 30 and 300 seconds/);
await assert.rejects(storage.getMediaObjectBytes("media/" + UUID, "unknown"), MediaStorageConfigurationError);

const postgresStorage = createMediaStorage({
  env: {},
  clientFactory() { throw new Error("PostgreSQL mode must not initialize an object-storage client."); }
});
assert.equal(postgresStorage.provider, "postgres");
assert.equal(postgresStorage.isObjectStorageEnabled, false);
assert.deepEqual(
  await postgresStorage.putMediaObject({ data: Buffer.from("legacy"), mimeType: "image/jpeg" }),
  { storageProvider: "postgres", objectKey: null }
);

const incompleteNeon = createMediaStorage({ env: { FYNX_MEDIA_STORAGE_PROVIDER: "neon" } });
assert.equal(incompleteNeon.isConfigured, false);
await assert.rejects(
  incompleteNeon.putMediaObject({ data: Buffer.from("test"), mimeType: "image/jpeg" }),
  (error) => error instanceof MediaStorageConfigurationError && error.statusCode === 503
);
assert.throws(
  () => createMediaStorage({ env: { FYNX_MEDIA_STORAGE_PROVIDER: "gcs" } }),
  MediaStorageConfigurationError
);

console.log("FYNX MEDIA OBJECT STORAGE: GREEN");
console.log("Neon path-style write/read/signing, R2 dual-read compatibility, cleanup, validation and PostgreSQL fallback: PASS");
