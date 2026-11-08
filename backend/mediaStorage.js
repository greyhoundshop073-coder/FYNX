import { randomUUID } from "node:crypto";
import { Buffer } from "node:buffer";
import {
  DeleteObjectCommand,
  GetObjectCommand,
  PutObjectCommand,
  S3Client
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";

const MAX_MEDIA_OBJECT_BYTES = 12 * 1024 * 1024;
const DEFAULT_DOWNLOAD_URL_TTL_SECONDS = 120;
const MAX_DOWNLOAD_URL_TTL_SECONDS = 300;
const MEDIA_OBJECT_KEY_PATTERN = /^media\\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const OBJECT_PROVIDERS = new Set(["neon", "r2"]);

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
    const data = Buffer.from(await body.transformToByteArray());
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

function validBucket(value) {
  return /^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$/.test(value);
}

function readProviderConfig(provider, env) {
  if (provider === "neon") {
    const endpoint = String(env.FYNX_NEON_S3_ENDPOINT || "").trim();
    const region = String(env.FYNX_NEON_S3_REGION || "").trim();
    const accessKeyId = String(env.FYNX_NEON_S3_ACCESS_KEY_ID || "").trim();
    const secretAccessKey = String(env.FYNX_NEON_S3_SECRET_ACCESS_KEY || "").trim();
    const bucket = String(env.FYNX_NEON_STORAGE_BUCKET || "").trim();
    let endpointValid = false;
    try {
      const parsed = new URL(endpoint);
      endpointValid = parsed.protocol === "https:" && parsed.hostname.endsWith(".neon.tech") &&
        !parsed.username && !parsed.password && !parsed.search && !parsed.hash;
    } catch {}
    return {
      provider, endpoint, region, accessKeyId, secretAccessKey, bucket,
      configured: endpointValid && /^[a-z]{2}-[a-z]+-\\d$/.test(region) &&
        accessKeyId.startsWith("nak_") && secretAccessKey.startsWith("nsk_") && validBucket(bucket),
      forcePathStyle: true
    };
  }

  if (provider === "r2") {
    const accountId = String(env.FYNX_R2_ACCOUNT_ID || "").trim().toLowerCase();
    const accessKeyId = String(env.FYNX_R2_ACCESS_KEY_ID || "").trim();
    const secretAccessKey = String(env.FYNX_R2_SECRET_ACCESS_KEY || "").trim();
    const bucket = String(env.FYNX_R2_BUCKET || "").trim();
    return {
      provider, endpoint: /^[a-f0-9]{32}$/.test(accountId) ? "https://" + accountId + ".r2.cloudflarestorage.com" : "",
      region: "auto", accessKeyId, secretAccessKey, bucket,
      configured: /^[a-f0-9]{32}$/.test(accountId) && accessKeyId.length >= 8 &&
        secretAccessKey.length >= 16 && validBucket(bucket),
      forcePathStyle: false
    };
  }

  throw new MediaStorageConfigurationError("Unsupported FYNX media storage provider.");
}

export function createMediaStorage({
  env = process.env,
  clientFactory = (options) => new S3Client(options),
  signUrlFactory = getSignedUrl,
  idFactory = randomUUID
} = {}) {
  const provider = String(env.FYNX_MEDIA_STORAGE_PROVIDER || "postgres").trim().toLowerCase();
  if (provider !== "postgres" && !OBJECT_PROVIDERS.has(provider)) {
    throw new MediaStorageConfigurationError("FYNX_MEDIA_STORAGE_PROVIDER must be postgres, neon or r2.");
  }

  const configs = new Map();
  const clients = new Map();
  for (const name of OBJECT_PROVIDERS) configs.set(name, readProviderConfig(name, env));

  function configFor(name = provider) {
    if (!OBJECT_PROVIDERS.has(name)) throw new MediaStorageConfigurationError("Unknown media storage provider.");
    const config = configs.get(name);
    if (!config?.configured) throw new MediaStorageConfigurationError(name + " media storage is not configured.");
    return config;
  }

  function getClient(name = provider) {
    const config = configFor(name);
    if (!clients.has(name)) {
      clients.set(name, clientFactory({
        region: config.region,
        endpoint: config.endpoint,
        credentials: { accessKeyId: config.accessKeyId, secretAccessKey: config.secretAccessKey },
        ...(config.forcePathStyle ? { forcePathStyle: true } : {})
      }));
    }
    return clients.get(name);
  }

  function validateObjectKey(objectKey) {
    const key = String(objectKey || "").trim();
    if (!MEDIA_OBJECT_KEY_PATTERN.test(key)) throw new Error("Invalid FYNX media object key.");
    return key;
  }

  async function createDownloadUrl(objectKey, sourceProvider = provider, expiresInSeconds = DEFAULT_DOWNLOAD_URL_TTL_SECONDS) {
    const config = configFor(sourceProvider);
    const key = validateObjectKey(objectKey);
    if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 30 || expiresInSeconds > MAX_DOWNLOAD_URL_TTL_SECONDS) {
      throw new RangeError("Media download URL expiry must be between 30 and 300 seconds.");
    }
    const command = new GetObjectCommand({ Bucket: config.bucket, Key: key });
    const url = await signUrlFactory(getClient(sourceProvider), command, { expiresIn: expiresInSeconds });
    if (typeof url !== "string" || !url.startsWith("https://")) throw new Error("Object storage returned an invalid download URL.");
    return url;
  }

  async function getMediaObjectBytes(objectKey, sourceProvider = provider) {
    const config = configFor(sourceProvider);
    const key = validateObjectKey(objectKey);
    const result = await getClient(sourceProvider).send(new GetObjectCommand({ Bucket: config.bucket, Key: key }));
    return bodyToBuffer(result?.Body);
  }

  async function deleteMediaObject(objectKey, sourceProvider = provider) {
    const config = configFor(sourceProvider);
    const key = validateObjectKey(objectKey);
    await getClient(sourceProvider).send(new DeleteObjectCommand({ Bucket: config.bucket, Key: key }));
  }

  return Object.freeze({
    provider,
    isObjectStorageEnabled: OBJECT_PROVIDERS.has(provider),
    isConfigured: provider === "postgres" || Boolean(configs.get(provider)?.configured),
    async putMediaObject({ data, mimeType }) {
      if (provider === "postgres") return { storageProvider: "postgres", objectKey: null };
      const config = configFor(provider);
      const bytes = toBuffer(data);
      if (!bytes.length || bytes.length > MAX_MEDIA_OBJECT_BYTES) throw new Error("Media must be between 1 byte and 12 MB.");
      const normalizedMimeType = String(mimeType || "").trim().toLowerCase();
      if (!/^(image|video|audio)\\//.test(normalizedMimeType)) throw new Error("Unsupported media type.");
      const objectKey = "media/" + idFactory();
      await getClient(provider).send(new PutObjectCommand({
        Bucket: config.bucket, Key: objectKey, Body: bytes, ContentLength: bytes.length,
        ContentType: normalizedMimeType, CacheControl: "private, max-age=60"
      }));
      return { storageProvider: provider, objectKey };
    },
    createDownloadUrl,
    getMediaObjectBytes,
    deleteMediaObject
  });
}

export const mediaStorage = createMediaStorage();

export async function redirectStoredMedia(res, media, { cacheControl = "private, no-store" } = {}) {
  const sourceProvider = String(media?.storage_provider || "postgres").toLowerCase();
  if (sourceProvider === "postgres") return false;
  if (!OBJECT_PROVIDERS.has(sourceProvider)) throw new MediaStorageConfigurationError("Unsupported stored media provider.");
  const url = await mediaStorage.createDownloadUrl(media?.object_key, sourceProvider);
  res.set("Cache-Control", cacheControl);
  res.redirect(302, url);
  return true;
}
