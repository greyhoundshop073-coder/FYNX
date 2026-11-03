import { randomUUID } from "node:crypto";
import { Buffer } from "node:buffer";
import {
  CopyObjectCommand,
  DeleteObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
  PutObjectCommand,
  S3Client
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";

const MAX_MEDIA_OBJECT_BYTES = 12 * 1024 * 1024;
const DEFAULT_DOWNLOAD_URL_TTL_SECONDS = 120;
const MAX_DOWNLOAD_URL_TTL_SECONDS = 300;
const MEDIA_OBJECT_KEY_PATTERN = /^media\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const UPLOAD_OBJECT_KEY_PATTERN = /^uploads\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export class MediaStorageConfigurationError extends Error {
  constructor(message = "FYNX media object storage is not configured.") {
    super(message);
    this.name = "MediaStorageConfigurationError";
    this.code = "MEDIA_STORAGE_NOT_CONFIGURED";
    this.statusCode = 503;
    this.publicMessage = "Media storage is not configured. Please contact FYNX support.";
  }
}

function toBuffer(value) {
  if (Buffer.isBuffer(value)) return Buffer.from(value);
  if (value instanceof Uint8Array) return Buffer.from(value);
  return Buffer.from(value ?? []);
}

async function bodyToBuffer(body) {
  if (!body) return null;
  if (typeof body.transformToByteArray === "function") {
    const bytes = await body.transformToByteArray();
    const data = Buffer.from(bytes);
    if (data.length > MAX_MEDIA_OBJECT_BYTES) throw new Error("Stored media exceeds the supported size.");
    return data;
  }
  const chunks = [];
  let total = 0;
  for await (const chunk of body) {
    const bytes = toBuffer(chunk);
    total += bytes.length;
    if (total > MAX_MEDIA_OBJECT_BYTES) throw new Error("Stored media exceeds the supported size.");
    chunks.push(bytes);
  }
  return total ? Buffer.concat(chunks, total) : null;
}

export function createMediaStorage({
  env = process.env,
  clientFactory = (options) => new S3Client(options),
  signUrlFactory = getSignedUrl,
  idFactory = randomUUID
} = {}) {
  const provider = String(env.FYNX_MEDIA_STORAGE_PROVIDER || "postgres").trim().toLowerCase();
  const accountId = String(env.FYNX_R2_ACCOUNT_ID || "").trim().toLowerCase();
  const accessKeyId = String(env.FYNX_R2_ACCESS_KEY_ID || "").trim();
  const secretAccessKey = String(env.FYNX_R2_SECRET_ACCESS_KEY || "").trim();
  const bucket = String(env.FYNX_R2_BUCKET || "").trim();
  const supportedProvider = provider === "postgres" || provider === "r2";
  const r2Configured =
    /^[a-f0-9]{32}$/.test(accountId) &&
    accessKeyId.length >= 8 &&
    secretAccessKey.length >= 16 &&
    /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/.test(bucket);

  let client;

  function requireR2Configuration() {
    if (!supportedProvider) {
      throw new MediaStorageConfigurationError("FYNX_MEDIA_STORAGE_PROVIDER must be postgres or r2.");
    }
    if (provider !== "r2" || !r2Configured) throw new MediaStorageConfigurationError();
  }

  function getClient() {
    requireR2Configuration();
    if (!client) {
      client = clientFactory({
        region: "auto",
        endpoint: "https://" + accountId + ".r2.cloudflarestorage.com",
        credentials: { accessKeyId, secretAccessKey }
      });
    }
    return client;
  }

  function validateObjectKey(objectKey) {
    const key = String(objectKey || "").trim();
    if (!MEDIA_OBJECT_KEY_PATTERN.test(key)) throw new Error("Invalid FYNX media object key.");
    return key;
  }

  function validateUploadObjectKey(objectKey) {
    const key = String(objectKey || "").trim();
    if (!UPLOAD_OBJECT_KEY_PATTERN.test(key)) throw new Error("Invalid FYNX temporary upload object key.");
    return key;
  }

  function validateStoredObjectKey(objectKey) {
    const key = String(objectKey || "").trim();
    if (!MEDIA_OBJECT_KEY_PATTERN.test(key) && !UPLOAD_OBJECT_KEY_PATTERN.test(key)) {
      throw new Error("Invalid FYNX stored media object key.");
    }
    return key;
  }

  return Object.freeze({
    provider,
    isObjectStorageEnabled: provider === "r2",
    isConfigured: provider === "postgres" || (supportedProvider && r2Configured),

    async putMediaObject({ data, mimeType }) {
      if (!supportedProvider) throw new MediaStorageConfigurationError("FYNX_MEDIA_STORAGE_PROVIDER must be postgres or r2.");
      if (provider === "postgres") return { storageProvider: "postgres", objectKey: null };
      requireR2Configuration();

      const bytes = toBuffer(data);
      if (!bytes.length || bytes.length > MAX_MEDIA_OBJECT_BYTES) {
        throw new Error("Media must be between 1 byte and 12 MB.");
      }
      const normalizedMimeType = String(mimeType || "").trim().toLowerCase();
      if (!/^(image|video|audio)\//.test(normalizedMimeType)) throw new Error("Unsupported media type.");

      const objectKey = "media/" + idFactory();
      await getClient().send(new PutObjectCommand({
        Bucket: bucket,
        Key: objectKey,
        Body: bytes,
        ContentLength: bytes.length,
        ContentType: normalizedMimeType,
        CacheControl: "private, max-age=60"
      }));
      return { storageProvider: "r2", objectKey };
    },

    async createUploadUrl({ objectKey, mimeType, expiresInSeconds = 300 }) {
      requireR2Configuration();
      const key = validateUploadObjectKey(objectKey);
      const normalizedMimeType = String(mimeType || "").trim().toLowerCase();
      if (!/^(image\/(jpeg|png|webp|gif)|video\/(mp4|webm|quicktime)|audio\/(mp4|mpeg|aac|x-m4a|wav))$/.test(normalizedMimeType)) {
        throw new Error("Unsupported FYNX media type.");
      }
      if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 60 || expiresInSeconds > 600) {
        throw new RangeError("Media upload URL expiry must be between 60 and 600 seconds.");
      }
      const command = new PutObjectCommand({
        Bucket: bucket,
        Key: key,
        ContentType: normalizedMimeType,
        CacheControl: "private, max-age=60"
      });
      const url = await signUrlFactory(getClient(), command, { expiresIn: expiresInSeconds });
      if (typeof url !== "string" || !url.startsWith("https://")) throw new Error("Object storage returned an invalid upload URL.");
      return url;
    },

    async promoteUploadedObject(sourceObjectKey, destinationObjectKey) {
      requireR2Configuration();
      const sourceKey = validateUploadObjectKey(sourceObjectKey);
      const destinationKey = validateObjectKey(destinationObjectKey);
      await getClient().send(new CopyObjectCommand({
        Bucket: bucket,
        Key: destinationKey,
        CopySource: bucket + "/" + sourceKey,
        MetadataDirective: "COPY"
      }));
      return { objectKey: destinationKey };
    },

    async inspectMediaObject(objectKey) {
      requireR2Configuration();
      const key = validateStoredObjectKey(objectKey);
      const result = await getClient().send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
      return {
        byteSize: Number(result?.ContentLength || 0),
        mimeType: String(result?.ContentType || "").split(";")[0].trim().toLowerCase()
      };
    },

    async createDownloadUrl(objectKey, expiresInSeconds = DEFAULT_DOWNLOAD_URL_TTL_SECONDS) {
      requireR2Configuration();
      const key = validateObjectKey(objectKey);
      if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 30 || expiresInSeconds > MAX_DOWNLOAD_URL_TTL_SECONDS) {
        throw new RangeError("Media download URL expiry must be between 30 and 300 seconds.");
      }
      const command = new GetObjectCommand({ Bucket: bucket, Key: key });
      const url = await signUrlFactory(getClient(), command, { expiresIn: expiresInSeconds });
      if (typeof url !== "string" || !url.startsWith("https://")) throw new Error("Object storage returned an invalid download URL.");
      return url;
    },

    async getMediaObjectBytes(objectKey) {
      requireR2Configuration();
      const key = validateObjectKey(objectKey);
      const result = await getClient().send(new GetObjectCommand({ Bucket: bucket, Key: key }));
      return bodyToBuffer(result?.Body);
    },

    async deleteMediaObject(objectKey) {
      requireR2Configuration();
      const key = validateStoredObjectKey(objectKey);
      await getClient().send(new DeleteObjectCommand({ Bucket: bucket, Key: key }));
    }
  });
}

export const mediaStorage = createMediaStorage();

export async function redirectStoredMedia(res, media, { cacheControl = "private, no-store" } = {}) {
  if (String(media?.storage_provider || "postgres").toLowerCase() !== "r2") return false;
  const url = await mediaStorage.createDownloadUrl(media?.object_key);
  res.set("Cache-Control", cacheControl);
  res.redirect(302, url);
  return true;
}
