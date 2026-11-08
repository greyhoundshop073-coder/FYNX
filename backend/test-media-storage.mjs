import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { createMediaStorage, MediaStorageConfigurationError } from "./mediaStorage.js";

const UUID = "f35c2d80-4f45-4a33-b5b2-bb94928f9a11";

function createFakeClient(calls) {
  return {
    async send(command) {
      calls.push(command);
      if (command.constructor.name === "GetObjectCommand") {
        return {
          Body: (async function* () {
            yield Buffer.from("FYNX-");
            yield Buffer.from("media-bytes");
          })()
        };
      }
      return {};
    }
  };
}

const r2Calls = [];
const r2OptionsSeen = [];
const r2Storage = createMediaStorage({
  env: {
    FYNX_MEDIA_STORAGE_PROVIDER: "r2",
    FYNX_R2_ACCOUNT_ID: "0123456789abcdef0123456789abcdef",
    FYNX_R2_ACCESS_KEY_ID: "test-access-key",
    FYNX_R2_SECRET_ACCESS_KEY: "test-secret-access-key-value",
    FYNX_R2_BUCKET: "fynx-media"
  },
  clientFactory(options) {
    r2OptionsSeen.push(options);
    return createFakeClient(r2Calls);
  },
  async signUrlFactory(_client, command, options) {
    r2Calls.push(command);
    assert.equal(command.constructor.name, "GetObjectCommand");
    assert.equal(options.expiresIn, 120);
    return "https://fynx-media.example.r2.cloudflarestorage.com/signed-read";
  },
  idFactory: () => UUID
});

assert.equal(r2Storage.provider, "r2");
assert.equal(r2Storage.isObjectStorageEnabled, true);
assert.equal(r2Storage.isConfigured, true);
const r2Stored = await r2Storage.putMediaObject({ data: Buffer.from("new-media-bytes"), mimeType: "image/webp" });
assert.deepEqual(r2Stored, { storageProvider: "r2", objectKey: "media/" + UUID });
assert.equal(r2Calls[0].constructor.name, "PutObjectCommand");
assert.equal(r2Calls[0].input.Bucket, "fynx-media");
assert.equal(r2Calls[0].input.Key, "media/" + UUID);
assert.equal(r2Calls[0].input.ContentType, "image/webp");
assert.equal(r2Calls[0].input.ContentLength, Buffer.byteLength("new-media-bytes"));
assert.equal(r2Calls[0].input.CacheControl, "private, max-age=60");
assert.equal(Buffer.from(r2Calls[0].input.Body).toString(), "new-media-bytes");
assert.equal(await r2Storage.createDownloadUrl("media/" + UUID), "https://fynx-media.example.r2.cloudflarestorage.com/signed-read");
assert.deepEqual(r2OptionsSeen[0], {
  region: "auto",
  endpoint: "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com",
  credentials: { accessKeyId: "test-access-key", secretAccessKey: "test-secret-access-key-value" }
});
assert.equal((await r2Storage.getMediaObjectBytes("media/" + UUID)).toString(), "FYNX-media-bytes");
await r2Storage.deleteMediaObject("media/" + UUID);
assert.equal(r2Calls.at(-1).constructor.name, "DeleteObjectCommand");

const neonCalls = [];
const neonOptionsSeen = [];
const neonStorage = createMediaStorage({
  env: {
    FYNX_MEDIA_STORAGE_PROVIDER: "neon",
    FYNX_MEDIA_STORAGE_BUCKET: "fynx-media",
    AWS_ENDPOINT_URL_S3: "https://br-floral-shape-b5mbajl5.storage.c-7.us-east-2.aws.neon.tech",
    AWS_REGION: "us-east-2",
    AWS_ACCESS_KEY_ID: "neon-branch-access-key",
    AWS_SECRET_ACCESS_KEY: "neon-branch-secret-key"
  },
  clientFactory(options) {
    neonOptionsSeen.push(options);
    return createFakeClient(neonCalls);
  },
  async signUrlFactory(_client, command, options) {
    neonCalls.push(command);
    assert.equal(command.constructor.name, "GetObjectCommand");
    assert.equal(options.expiresIn, 120);
    return "https://br-floral-shape-b5mbajl5.storage.c-7.us-east-2.aws.neon.tech/fynx-media/media/" + UUID + "?signed=1";
  },
  idFactory: () => UUID
});

assert.equal(neonStorage.provider, "neon");
assert.equal(neonStorage.isObjectStorageEnabled, true);
assert.equal(neonStorage.isConfigured, true);
const neonStored = await neonStorage.putMediaObject({ data: Buffer.from("new-media-bytes"), mimeType: "image/webp" });
assert.deepEqual(neonStored, { storageProvider: "neon", objectKey: "media/" + UUID });
assert.equal(neonCalls[0].constructor.name, "PutObjectCommand");
assert.equal(neonCalls[0].input.Bucket, "fynx-media");
assert.equal(neonCalls[0].input.Key, "media/" + UUID);
assert.deepEqual(neonOptionsSeen[0], {
  region: "us-east-2",
  endpoint: "https://br-floral-shape-b5mbajl5.storage.c-7.us-east-2.aws.neon.tech",
  credentials: { accessKeyId: "neon-branch-access-key", secretAccessKey: "neon-branch-secret-key" },
  forcePathStyle: true
});
assert.equal((await neonStorage.createDownloadUrl("media/" + UUID)).includes("?signed=1"), true);
assert.equal((await neonStorage.getMediaObjectBytes("media/" + UUID)).toString(), "FYNX-media-bytes");
await neonStorage.deleteMediaObject("media/" + UUID);
assert.equal(neonCalls.at(-1).constructor.name, "DeleteObjectCommand");

await assert.rejects(neonStorage.createDownloadUrl("../private-file"), /Invalid FYNX media object key/);
await assert.rejects(neonStorage.createDownloadUrl("media/" + UUID, 301), /expiry must be between 30 and 300 seconds/);


const dualCalls = [];
const dualClientOptions = [];
const dualStorage = createMediaStorage({
  env: {
    FYNX_MEDIA_STORAGE_PROVIDER: "neon",
    FYNX_MEDIA_STORAGE_BUCKET: "fynx-neon-media",
    AWS_ENDPOINT_URL_S3: "https://br-floral-shape-b5mbajl5.storage.c-7.us-east-2.aws.neon.tech",
    AWS_REGION: "us-east-2",
    AWS_ACCESS_KEY_ID: "neon-branch-access-key",
    AWS_SECRET_ACCESS_KEY: "neon-branch-secret-key",
    FYNX_R2_ACCOUNT_ID: "0123456789abcdef0123456789abcdef",
    FYNX_R2_ACCESS_KEY_ID: "r2-access-key",
    FYNX_R2_SECRET_ACCESS_KEY: "r2-secret-access-key",
    FYNX_R2_BUCKET: "fynx-r2-media"
  },
  clientFactory(options) {
    dualClientOptions.push(options);
    return { ...createFakeClient(dualCalls), options };
  },
  async signUrlFactory(client, command, options) {
    dualCalls.push(command);
    assert.equal(command.constructor.name, "GetObjectCommand");
    assert.equal(options.expiresIn, 120);
    return "https://signed.example.test/" + new URL(client.options.endpoint).hostname;
  },
  idFactory: () => UUID
});
assert.equal(dualStorage.provider, "neon");
assert.equal(dualStorage.isProviderConfigured("neon"), true);
assert.equal(dualStorage.isProviderConfigured("r2"), true);
const activeWrite = await dualStorage.putMediaObject({ data: Buffer.from("active-neon-write"), mimeType: "image/png" });
assert.equal(activeWrite.storageProvider, "neon");
const oldR2SignedUrl = await dualStorage.createDownloadUrl("media/" + UUID, 120, "r2");
assert.match(oldR2SignedUrl, /0123456789abcdef0123456789abcdef\.r2\.cloudflarestorage\.com/);
const oldR2Bytes = await dualStorage.getMediaObjectBytes("media/" + UUID, "r2");
assert.equal(oldR2Bytes.toString(), "FYNX-media-bytes");
await dualStorage.deleteMediaObject("media/" + UUID, "r2");
assert.equal(dualClientOptions.length, 2);
assert.equal(dualClientOptions[0].forcePathStyle, true);
assert.equal(dualClientOptions[1].endpoint, "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com");

const postgresStorage = createMediaStorage({
  env: {},
  clientFactory() {
    throw new Error("PostgreSQL mode must not initialize an object-storage client.");
  }
});
assert.equal(postgresStorage.provider, "postgres");
assert.equal(postgresStorage.isObjectStorageEnabled, false);
assert.equal(postgresStorage.isConfigured, true);
assert.deepEqual(
  await postgresStorage.putMediaObject({ data: Buffer.from("legacy"), mimeType: "image/jpeg" }),
  { storageProvider: "postgres", objectKey: null }
);

for (const incomplete of [
  { FYNX_MEDIA_STORAGE_PROVIDER: "r2" },
  { FYNX_MEDIA_STORAGE_PROVIDER: "neon" },
  { FYNX_MEDIA_STORAGE_PROVIDER: "future-provider" }
]) {
  const storage = createMediaStorage({ env: incomplete });
  assert.equal(storage.isConfigured, false);
  await assert.rejects(
    storage.putMediaObject({ data: Buffer.from("test"), mimeType: "image/jpeg" }),
    (error) => error instanceof MediaStorageConfigurationError && error.statusCode === 503
  );
}

console.log("FYNX MEDIA OBJECT STORAGE: GREEN");
console.log("Neon and R2 upload, short-lived signed read, download, cleanup, path-style addressing, key validation and PostgreSQL fallback: PASS");
