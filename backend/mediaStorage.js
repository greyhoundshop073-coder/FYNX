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
const DEFAULT_STORAGE_OPERATION_TIMEOUT_MS = 20_000;
const DEFAULT_DOWNLOAD_URL_TTL_SECONDS = 120;
const MAX_DOWNLOAD_URL_TTL_SECONDS = 300;
const MEDIA_OBJECT_KEY_PATTERN = /^media\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const UPLOAD_OBJECT_KEY_PATTERN = /^uploads\/[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
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

const NETWORK_ERROR_CODES = new Set([
  "ETIMEDOUT", "ECONNRESET", "ECONNREFUSED", "ENOTFOUND", "EAI_AGAIN",
  "EHOSTUNREACH", "ENETUNREACH", "EPIPE", "UND_ERR_CONNECT_TIMEOUT"
]);

/** Classify storage failures without exposing raw provider messages or signed URLs. */
export function classifyMediaStorageError(error) {
  const chain = [];
  let current = error;
  for (let depth = 0; current && depth < 6; depth += 1) {
    chain.push(current);
    current = current.cause;
  }
  const tokens = chain.flatMap((item) => [
    item?.name, item?.code, item?.Code, item?.message
  ]).filter((value) => typeof value === "string").join(" ").toLowerCase();
  const codes = chain.flatMap((item) => [item?.code, item?.Code, item?.name])
    .filter((value) => typeof value === "string").map((value) => value.toLowerCase());
  const statuses = chain.flatMap((item) => [
    item?.statusCode, item?.$metadata?.httpStatusCode, item?.$response?.statusCode
  ]).filter((value) => Number.isInteger(value));
  const status = statuses.find((value) => value >= 400 && value <= 599);

  if (error instanceof MediaStorageConfigurationError || codes.includes("media_storage_not_configured")) {
    return { category: "configuration", code: "MEDIA_STORAGE_NOT_CONFIGURED", statusCode: 503, retryable: false, publicMessage: "Media storage is not configured. Please contact FYNX support." };
  }
  if (codes.includes("nosuchbucket") || tokens.includes("no such bucket")) {
    return { category: "configuration", code: "MEDIA_STORAGE_BUCKET_UNAVAILABLE", statusCode: 503, retryable: false, publicMessage: "FYNX media storage is unavailable. Please contact support." };
  }
  if (/(requestexpired|expiredtoken|expiredsignature|presigned.{0,24}expired|request has expired|token.{0,16}expired)/.test(tokens)) {
    return { category: "signature_expired", code: "MEDIA_STORAGE_SIGNATURE_EXPIRED", statusCode: 410, retryable: false, publicMessage: "This media upload link has expired. Please start the upload again." };
  }
  if (status === 404 || codes.some((code) => ["nosuchkey", "notfound", "nosuchobject"].includes(code))) {
    return { category: "missing_object", code: "MEDIA_STORAGE_OBJECT_NOT_FOUND", statusCode: 404, retryable: false, publicMessage: "The requested media object was not found." };
  }
  if (status === 401 || status === 403 || codes.some((code) =>
    ["accessdenied", "invalidaccesskeyid", "invalidtoken", "signaturedoesnotmatch", "authorizationheadermalformed"].includes(code))) {
    return { category: "authentication", code: "MEDIA_STORAGE_AUTHENTICATION_FAILED", statusCode: 503, retryable: false, publicMessage: "FYNX could not authenticate with media storage. Please contact support." };
  }
  if (chain.some((item) => item?.name === "AbortError" || item?.name === "TimeoutError") ||
      chain.some((item) => NETWORK_ERROR_CODES.has(String(item?.code || "").toUpperCase())) ||
      /(timed? ?out|timeout|network|dns|socket hang up|connection reset|connection refused|aborted)/.test(tokens)) {
    return { category: "network", code: "MEDIA_STORAGE_NETWORK_FAILURE", statusCode: 503, retryable: true, publicMessage: "Media storage could not be reached. Check your connection and retry." };
  }
  return { category: "provider", code: "MEDIA_STORAGE_PROVIDER_ERROR", statusCode: 502, retryable: false, publicMessage: "Media storage could not complete the request. Please retry or contact support." };
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
    // Prefer FYNX-scoped values, but accept Neon's AWS-standard S3 variables too.
    // This keeps the adapter compatible with Neon env-pull and other S3-compatible hosts.
    const endpoint = String(env.FYNX_NEON_S3_ENDPOINT || env.AWS_ENDPOINT_URL_S3 || "").trim();
    const region = String(env.FYNX_NEON_S3_REGION || env.AWS_REGION || "").trim();
    const accessKeyId = String(env.FYNX_NEON_S3_ACCESS_KEY_ID || env.AWS_ACCESS_KEY_ID || "").trim();
    const secretAccessKey = String(env.FYNX_NEON_S3_SECRET_ACCESS_KEY || env.AWS_SECRET_ACCESS_KEY || "").trim();
    const bucket = String(env.FYNX_NEON_STORAGE_BUCKET || "").trim();
    let endpointValid = false;
    try {
      const parsed = new URL(endpoint);
      endpointValid = parsed.protocol === "https:" && /^[a-z0-9][a-z0-9-]{1,62}\.storage\.c-[a-z0-9-]+\.[a-z0-9-]+\.aws\.neon\.tech$/i.test(parsed.hostname) &&
        !parsed.username && !parsed.password && !parsed.search && !parsed.hash;
    } catch {}
    return {
      provider, endpoint, region, accessKeyId, secretAccessKey, bucket,
      configured: endpointValid && /^[a-z]{2}-[a-z]+-\d$/.test(region) &&
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
  idFactory = randomUUID,
  operationTimeoutMs = DEFAULT_STORAGE_OPERATION_TIMEOUT_MS
} = {}) {
  if (!Number.isSafeInteger(operationTimeoutMs) || operationTimeoutMs < 1 || operationTimeoutMs > 120_000) {
    throw new RangeError("Media storage operation timeout must be between 1 and 120000 milliseconds.");
  }
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

  async function sendObjectCommand(name, command, consumeResponse) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), operationTimeoutMs);
    try {
      const response = await getClient(name).send(command, { abortSignal: controller.signal });
      // Keep the same deadline active while consuming streamed response bytes. S3 SDK
      // send() can resolve after headers arrive, before the object body has finished.
      return consumeResponse ? await consumeResponse(response) : response;
    } catch (error) {
      if (controller.signal.aborted) {
        const timeoutError = new Error("FYNX media storage operation timed out.");
        timeoutError.name = "TimeoutError";
        timeoutError.code = "ETIMEDOUT";
        timeoutError.cause = error;
        throw timeoutError;
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
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
    return sendObjectCommand(
      sourceProvider,
      new GetObjectCommand({ Bucket: config.bucket, Key: key }),
      (result) => bodyToBuffer(result?.Body)
    );
  }

  async function deleteMediaObject(objectKey, sourceProvider = provider) {
    const config = configFor(sourceProvider);
    const key = OBJECT_PROVIDERS.has(sourceProvider) ? validateStoredObjectKey(objectKey) : validateObjectKey(objectKey);
    await sendObjectCommand(sourceProvider, new DeleteObjectCommand({ Bucket: config.bucket, Key: key }));
  }

  return Object.freeze({
    provider,
    isObjectStorageEnabled: OBJECT_PROVIDERS.has(provider),
    supportsDirectUpload: OBJECT_PROVIDERS.has(provider),
    isObjectStorageProvider(name) {
      return OBJECT_PROVIDERS.has(String(name || "").trim().toLowerCase());
    },
    isConfiguredFor(name) {
      const normalized = String(name || "").trim().toLowerCase();
      return OBJECT_PROVIDERS.has(normalized) && Boolean(configs.get(normalized)?.configured);
    },
    isConfigured: provider === "postgres" || Boolean(configs.get(provider)?.configured),
    async putMediaObject({ data, mimeType }) {
      if (provider === "postgres") return { storageProvider: "postgres", objectKey: null };
      const config = configFor(provider);
      const bytes = toBuffer(data);
      if (!bytes.length || bytes.length > MAX_MEDIA_OBJECT_BYTES) throw new Error("Media must be between 1 byte and 12 MB.");
      const normalizedMimeType = String(mimeType || "").trim().toLowerCase();
      if (!/^(image|video|audio)\//.test(normalizedMimeType)) throw new Error("Unsupported media type.");
      const objectKey = "media/" + idFactory();
      await sendObjectCommand(provider, new PutObjectCommand({
        Bucket: config.bucket, Key: objectKey, Body: bytes, ContentLength: bytes.length,
        ContentType: normalizedMimeType, CacheControl: "private, max-age=60"
      }));
      return { storageProvider: provider, objectKey };
    },
    async createUploadUrl({ objectKey, mimeType, byteSize, expiresInSeconds = 300, storageProvider = provider }) {
      const config = configFor(storageProvider);
      const key = validateUploadObjectKey(objectKey);
      if (!Number.isSafeInteger(byteSize) || byteSize < 1 || byteSize > MAX_MEDIA_OBJECT_BYTES) {
        throw new RangeError("Media upload must be between 1 byte and 12 MB.");
      }
      const normalizedMimeType = String(mimeType || "").trim().toLowerCase();
      if (!/^(image\/(jpeg|png|webp|gif)|video\/(mp4|webm|quicktime)|audio\/(mp4|mpeg|aac|x-m4a|wav))$/.test(normalizedMimeType)) {
        throw new Error("Unsupported FYNX media type.");
      }
      if (!Number.isInteger(expiresInSeconds) || expiresInSeconds < 60 || expiresInSeconds > 600) {
        throw new RangeError("Media upload URL expiry must be between 60 and 600 seconds.");
      }
      const command = new PutObjectCommand({
        Bucket: config.bucket,
        Key: key,
        ContentType: normalizedMimeType,
        ContentLength: byteSize,
        CacheControl: "private, max-age=60"
      });
      const url = await signUrlFactory(getClient(storageProvider), command, { expiresIn: expiresInSeconds });
      if (typeof url !== "string" || !url.startsWith("https://")) {
        throw new Error("Object storage returned an invalid upload URL.");
      }
      return url;
    },

    async promoteUploadedObject(sourceObjectKey, destinationObjectKey, sourceProvider = provider) {
      const config = configFor(sourceProvider);
      const sourceKey = validateUploadObjectKey(sourceObjectKey);
      const destinationKey = validateObjectKey(destinationObjectKey);
      await sendObjectCommand(sourceProvider, new CopyObjectCommand({
        Bucket: config.bucket,
        Key: destinationKey,
        CopySource: config.bucket + "/" + sourceKey,
        MetadataDirective: "COPY"
      }));
      return { objectKey: destinationKey };
    },

    async inspectMediaObject(objectKey, sourceProvider = provider) {
      const config = configFor(sourceProvider);
      const key = validateStoredObjectKey(objectKey);
      const result = await sendObjectCommand(sourceProvider, new HeadObjectCommand({ Bucket: config.bucket, Key: key }));
      return {
        byteSize: Number(result?.ContentLength || 0),
        mimeType: String(result?.ContentType || "").split(";")[0].trim().toLowerCase()
      };
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
  let url;
  try {
    url = await mediaStorage.createDownloadUrl(media?.object_key, sourceProvider);
  } catch (error) {
    const diagnostic = classifyMediaStorageError(error);
    const safeError = new Error(diagnostic.publicMessage);
    Object.assign(safeError, diagnostic);
    safeError.cause = error;
    throw safeError;
  }
  res.set("Cache-Control", cacheControl);
  res.redirect(302, url);
  return true;
}
