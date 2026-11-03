import assert from "node:assert/strict";
import { Buffer } from "node:buffer";
import { createMediaStorage, MediaStorageConfigurationError } from "./mediaStorage.js";

const UUID = "f35c2d80-4f45-4a33-b5b2-bb94928f9a11";
const calls = [];
const fakeClient = {
  async send(command) {
    calls.push(command);
    if (command.constructor.name === "HeadObjectCommand") {
      return { ContentLength: 15, ContentType: "image/webp" };
    }
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

const optionsSeen = [];
const storage = createMediaStorage({
  env: {
    FYNX_MEDIA_STORAGE_PROVIDER: "r2",
    FYNX_R2_ACCOUNT_ID: "0123456789abcdef0123456789abcdef",
    FYNX_R2_ACCESS_KEY_ID: "test-access-key",
    FYNX_R2_SECRET_ACCESS_KEY: "test-secret-access-key-value",
    FYNX_R2_BUCKET: "fynx-media"
  },
  clientFactory(options) {
    optionsSeen.push(options);
    return fakeClient;
  },
  async signUrlFactory(_client, command, options) {
    calls.push(command);
    if (command.constructor.name === "PutObjectCommand") {
      assert.equal(options.expiresIn, 300);
      assert.equal(command.input.Bucket, "fynx-media");
      assert.equal(command.input.Key, "media/" + UUID);
      assert.equal(command.input.ContentType, "image/webp");
      return "https://fynx-media.example.r2.cloudflarestorage.com/signed-write";
    }
    assert.equal(command.constructor.name, "GetObjectCommand");
    assert.equal(options.expiresIn, 120);
    return "https://fynx-media.example.r2.cloudflarestorage.com/signed-read";
  },
  idFactory: () => UUID
});

assert.equal(storage.provider, "r2");
assert.equal(storage.isObjectStorageEnabled, true);
assert.equal(storage.isConfigured, true);

const stored = await storage.putMediaObject({
  data: Buffer.from("new-media-bytes"),
  mimeType: "image/webp"
});
assert.deepEqual(stored, { storageProvider: "r2", objectKey: "media/" + UUID });
assert.equal(calls[0].constructor.name, "PutObjectCommand");
assert.equal(calls[0].input.Bucket, "fynx-media");
assert.equal(calls[0].input.Key, "media/" + UUID);
assert.equal(calls[0].input.ContentType, "image/webp");
assert.equal(calls[0].input.ContentLength, Buffer.byteLength("new-media-bytes"));
assert.equal(calls[0].input.CacheControl, "private, max-age=60");
assert.equal(Buffer.from(calls[0].input.Body).toString(), "new-media-bytes");


const uploadUrl = await storage.createUploadUrl({
  objectKey: "media/" + UUID,
  mimeType: "image/webp"
});
assert.equal(uploadUrl, "https://fynx-media.example.r2.cloudflarestorage.com/signed-write");
const objectInfo = await storage.inspectMediaObject("media/" + UUID);
assert.deepEqual(objectInfo, { byteSize: 15, mimeType: "image/webp" });
await assert.rejects(
  storage.createUploadUrl({ objectKey: "../invalid", mimeType: "image/webp" }),
  /Invalid FYNX media object key/
);
await assert.rejects(
  storage.createUploadUrl({ objectKey: "media/" + UUID, mimeType: "application/pdf" }),
  /Unsupported FYNX media type/
);

const signedUrl = await storage.createDownloadUrl("media/" + UUID);
assert.equal(signedUrl, "https://fynx-media.example.r2.cloudflarestorage.com/signed-read");
assert.equal(calls.at(-1).constructor.name, "GetObjectCommand");
assert.deepEqual(optionsSeen[0], {
  region: "auto",
  endpoint: "https://0123456789abcdef0123456789abcdef.r2.cloudflarestorage.com",
  credentials: { accessKeyId: "test-access-key", secretAccessKey: "test-secret-access-key-value" }
});

const downloaded = await storage.getMediaObjectBytes("media/" + UUID);
assert.equal(downloaded.toString(), "FYNX-media-bytes");
await storage.deleteMediaObject("media/" + UUID);
assert.equal(calls.at(-1).constructor.name, "DeleteObjectCommand");

await assert.rejects(storage.createDownloadUrl("../private-file"), /Invalid FYNX media object key/);
await assert.rejects(storage.createDownloadUrl("media/" + UUID, 301), /expiry must be between 30 and 300 seconds/);

const postgresStorage = createMediaStorage({
  env: {},
  clientFactory() {
    throw new Error("PostgreSQL mode must not initialize an object-storage client.");
  }
});
assert.equal(postgresStorage.provider, "postgres");
assert.equal(postgresStorage.isObjectStorageEnabled, false);
assert.deepEqual(
  await postgresStorage.putMediaObject({ data: Buffer.from("legacy"), mimeType: "image/jpeg" }),
  { storageProvider: "postgres", objectKey: null }
);

const incompleteR2 = createMediaStorage({ env: { FYNX_MEDIA_STORAGE_PROVIDER: "r2" } });
assert.equal(incompleteR2.isConfigured, false);
await assert.rejects(
  incompleteR2.putMediaObject({ data: Buffer.from("test"), mimeType: "image/jpeg" }),
  (error) => error instanceof MediaStorageConfigurationError && error.statusCode === 503
);

console.log("FYNX MEDIA OBJECT STORAGE: GREEN");
console.log("R2 write, short-lived signed read, object download, cleanup, key validation and PostgreSQL fallback: PASS");
