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
const MEDIA_OBJECT_KEY_PATTERN = /^media\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const OBJECT_STORAGE_PROVIDERS = new Set(["neon", "r2"]);

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

function isValidBucketName(value) {
  return /^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$/.test(value);
}

function isValidHttpsEndpoint(value) {
  try {
    const endpoint = new URL(value);
    return endpoint.protocol === "https:" &&
      Boolean(endpoint.hostname) &&
      !endpoint.username &&
      !endpoint.password &&
      !endpoint.search &&
      !endpoint.hash &&
      (endpoint.pathname === "" || endpoint.pathname === "/");
  } catch {
    return false;
  }
}

export function createMediaStorage({
  env = process.env,
  clientFactory = (options) => new S3Client(options),
  signUrlFactory = getSignedUrl,
  idFactory = randomUUID
} = {}) {
  // This is the write target. Reads are routed by the provider recorded on each row,
  // so a migration can write to one provider while old objects remain on another.
  const provider = String(env.FYNX_MEDIA_STORAGE_PROVIDER || "postgres").trim().toLowerCase();

  const r2AccountId = String(env.FYNX_R2_ACCOUNT_ID || "").trim().toLowerCase();
  const r2AccessKeyId = String(env.FYNX_R2_ACCESS_KEY_ID || "").trim();
  const r2SecretAccessKey = String(env.FYNX_R2_SECRET_ACCESS_KEY || "").trim();
  const r2Bucket = String(env.FYNX_R2_BUCKET || "").trim();

  // Neon credentials and endpoints are branch-scoped. Keep the endpoint, region and
  // access keys from the same Neon branch; never place these credentials in Android.
  const neonEndpoint = String(env.AWS_ENDPOINT_URL_S3 || "").trim();
  const neonRegion = String(env.AWS_REGION || "").trim();
  const neonAccessKeyId = String(env.AWS_ACCESS_KEY_ID || "").trim();
  const neonSecretAccessKey = String(env.AWS_SECRET_ACCESS_KEY || "").trim();
  const neonBucket = String(env.FYNX_MEDIA_STORAGE_BUCKET || "").trim();

  const configurations = {
    neon: {
      bucket: neonBucket,
      configured: isValidHttpsEndpoint(neonEndpoint) &&
        /^[a-z0-9-]{3,32}$/i.test(neonRegion) &&
        neonAccessKeyId.length > 0 &&
        neonSecretAccessKey.length > 0 &&
        isValidBucketName(neonBucket),
      clientOptions: {
        region: neonRegion,
        endpoint: neonEndpoint,
        credentials: { accessKeyId: neonAccessKeyId, secretAccessKey: neonSecretAccessKey },
        forcePathStyle: true
      }
    },
    r2: {
      bucket: r2Bucket,
      configured: /^[a-f0-9]{32}$/.test(r2AccountId) &&
        r2AccessKeyId.length > 0 &&
        r2SecretAccessKey.length > 0 &&
        isValidBucketName(r2Bucket),
      clientOptions: {
        region: "auto",
        endpoint: r2AccountId ? "https://" + r2AccountId + ".r2.cloudflarestorage.com" : "",
        credentials: { accessKeyId: r2AccessKeyId, secretAccessKey: r2SecretAccessKey }
      }
    }
  };

  const supportedWriteProvider = provider === "postgres" || OBJECT_STORAGE_PROVIDERS.has(provider);
  const providerConfigured = provider === "postgres" ||
    (OBJECT_STORAGE_PROVIDERS.has(provider) && configurations[provider].configured);
  const clients = new Map();

  function requireProviderConfiguration(storageProvider) {
    const requestedProvider = String(storageProvider || provider).trim().toLowerCase();
    if (!OBJECT_STORAGE_PROVIDERS.has(requestedProvider)) {
      throw new MediaStorageConfigurationError("FYNX media storage provider must be neon or r2 for object-backed media.");
    }
    if (!configurations[requestedProvider].configured) {
      throw new MediaStorageConfigurationError("The requested FYNX media storage provider is not configured for this runtime.");
    }
    return requestedProvider;
  }

  function getClient(storageProvider) {
    const selectedProvider = requireProviderConfiguration(storageProvider);
    if (!clients.has(selectedProvider)) {
      clients.set(selectedProvider, clientFactory(configurations[selectedProvider].clientOptions));
    }
    return clients.get(selectedProvider);
  }

  function getBucket(storageProvider) {
    const selectedProvider = requireProviderConfiguration(storageProvider);
    return configurations[selectedProvider].bucket;
  }

  function validateObjectKey(objectKey) {
    const key = String(objectKey || "").trim();
    if (!MEDIA_OBJECT_KEY_PATTERN.test(key)) throw new Error("Invalid FYNX media object key.");
    return key;
  }

  return Object.freeze({
    provider,
    isObjectStorageEnabled: OBJECT_STORAGE_PROVIDERS.has(provider),
    isConfigured: supportedWriteProvider && providerConfigured,
    isProviderConfigured(storageProvider) {
      const selectedProvider = String(storageProvider || "").trim().toLowerCase();
      return OBJECT_STORAGE_PROVIDERS.has(selectedProvider) && configurations[selectedProvider].configured;
    },

    async putMediaObject({ data, mimeType }) {
      if (provider === "postgres") return { storageProvider: "postgres", objectKey: null };
      if (!supportedWriteProvider || !providerConfigured) {
        throw new MediaStorageConfigurationError("The selected FYNX media write provider is not configured.");
      }

      const bytes = toBuffer(data);
      if (!bytes.length || bytes.length > MAX_MEDIA_OBJECT_BYTES) {
        throw new Error("Media must be between 1 byte and 12 MB.");
      }
      const normalizedMimeType = String(mimeType || "").trim().toLowerCase();
      if (!/^(image|video|audio)\//.test(normalizedMimeType)) throw new Error("Unsupported media type.");

      const objectKey = "media/" + idFactory();
      const selectedProvider = requireProviderConfiguration(provider);
      await getClient(selectedProvider).send(new PutObjectCommand({
        Bucket: getBucket(selectedProvider),
        Key: objectKey,
        Body: bytes,
        ContentLength: bytes.length,
        ContentType: normalizedMimeType,
        CacheControl: "private, max-age=60"
      }));
      return { storageProvider: selectedProvider, objectKey };
    },

    async createDownloadUrl(objectKey, expiresInSeconds = DEFAULT_DOWNLOAD_URL_TTL_SECONDS, storageProvider = provider) {
      const selectedProvider = requireProviderConfiguration(storageProvider);
      const key = validateObjectKey(objectKey);
      if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 30 || expiresInSeconds > MAX_DOWNLOAD_URL_TTL_SECONDS) {
        throw new RangeError("Media download URL expiry must be between 30 and 300 seconds.");
      }
      const command = new GetObjectCommand({ Bucket: getBucket(selectedProvider), Key: key });
      const url = await signUrlFactory(getClient(selectedProvider), command, { expiresIn: expiresInSeconds });
      if (typeof url !== "string" || !url.startsWith("https://")) throw new Error("Object storage returned an invalid download URL.");
      return url;
    },

    async getMediaObjectBytes(objectKey, storageProvider = provider) {
      const selectedProvider = requireProviderConfiguration(storageProvider);
      const key = validateObjectKey(objectKey);
      const result = await getClient(selectedProvider).send(new GetObjectCommand({
        Bucket: getBucket(selectedProvider),
        Key: key
      }));
      return bodyToBuffer(result?.Body);
    },

    async deleteMediaObject(objectKey, storageProvider = provider) {
      const selectedProvider = requireProviderConfiguration(storageProvider);
      const key = validateObjectKey(objectKey);
      await getClient(selectedProvider).send(new DeleteObjectCommand({
        Bucket: getBucket(selectedProvider),
        Key: key
      }));
    }
  });
}

export const mediaStorage = createMediaStorage();

export async function redirectStoredMedia(res, media, { cacheControl = "private, no-store" } = {}) {
  const storageProvider = String(media?.storage_provider || "postgres").toLowerCase();
  if (!OBJECT_STORAGE_PROVIDERS.has(storageProvider)) return false;
  const url = await mediaStorage.createDownloadUrl(
    media?.object_key,
    DEFAULT_DOWNLOAD_URL_TTL_SECONDS,
    storageProvider
  );
  res.set("Cache-Control", cacheControl);
  res.redirect(302, url);
  return true;
}
