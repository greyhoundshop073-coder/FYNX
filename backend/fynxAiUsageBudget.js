import { randomUUID } from "node:crypto";
import { getFynxDatabasePool } from "./databasePool.js";

const DEFAULT_DAILY_REQUEST_LIMIT = 30;
const DEFAULT_DAILY_PHOTO_REVIEW_LIMIT = 6;
const SCHEMA_SQL = [
  "CREATE TABLE IF NOT EXISTS fynx_ai_usage_events (",
  "  id UUID PRIMARY KEY,",
  "  user_id TEXT NOT NULL,",
  "  provider TEXT NOT NULL,",
  "  capability TEXT NOT NULL,",
  "  model TEXT NOT NULL,",
  "  status TEXT NOT NULL CHECK (status IN ('reserved','completed','failed')),",
  "  realtime_call_id TEXT,",
  "  input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),",
  "  output_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),",
  "  input_audio_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_audio_tokens >= 0),",
  "  input_text_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_text_tokens >= 0),",
  "  cached_input_audio_tokens BIGINT NOT NULL DEFAULT 0 CHECK (cached_input_audio_tokens >= 0),",
  "  cached_input_text_tokens BIGINT NOT NULL DEFAULT 0 CHECK (cached_input_text_tokens >= 0),",
  "  output_audio_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_audio_tokens >= 0),",
  "  output_text_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_text_tokens >= 0),",
  "  input_audio_seconds NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (input_audio_seconds >= 0),",
  "  estimated_cost_usd NUMERIC(18,8),",
  "  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),",
  "  finished_at TIMESTAMPTZ",
  ")",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS realtime_call_id TEXT",
  ";",
  "CREATE UNIQUE INDEX IF NOT EXISTS idx_fynx_ai_usage_realtime_call ON fynx_ai_usage_events(realtime_call_id) WHERE realtime_call_id IS NOT NULL",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS input_audio_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_audio_tokens >= 0)",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS input_text_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_text_tokens >= 0)",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS cached_input_audio_tokens BIGINT NOT NULL DEFAULT 0 CHECK (cached_input_audio_tokens >= 0)",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS cached_input_text_tokens BIGINT NOT NULL DEFAULT 0 CHECK (cached_input_text_tokens >= 0)",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS output_audio_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_audio_tokens >= 0)",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS output_text_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_text_tokens >= 0)",
  ";",
  "ALTER TABLE fynx_ai_usage_events ADD COLUMN IF NOT EXISTS input_audio_seconds NUMERIC(18,3) NOT NULL DEFAULT 0 CHECK (input_audio_seconds >= 0)",
  ";",
  "CREATE INDEX IF NOT EXISTS idx_fynx_ai_usage_user_created ON fynx_ai_usage_events(user_id, created_at DESC)",
  ";",
  "CREATE INDEX IF NOT EXISTS idx_fynx_ai_usage_created ON fynx_ai_usage_events(created_at DESC)"
].join("\n");
const schemaPromises = new WeakMap();

function readPositiveInteger(name, fallback, maximum) {
  const raw = String(process.env[name] || fallback).trim();
  if (!/^\d+$/.test(raw)) throw new Error(name + " must be a positive integer.");
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < 1 || value > maximum) {
    throw new Error(name + " must be between 1 and " + maximum + ".");
  }
  return value;
}

function readOptionalBudget(name) {
  const raw = String(process.env[name] || "").trim();
  if (!raw) return null;
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0 || value > 1_000_000) {
    throw new Error(name + " must be a positive USD amount.");
  }
  return value;
}

async function ensureUsageSchema(databasePool) {
  let pending = schemaPromises.get(databasePool);
  if (!pending) {
    pending = databasePool.query(SCHEMA_SQL).catch(error => {
      schemaPromises.delete(databasePool);
      throw error;
    });
    schemaPromises.set(databasePool, pending);
  }
  await pending;
}

export class FynxAiUsageLimitError extends Error {
  constructor(message, retryAfterSeconds = 86400) {
    super(message);
    this.name = "FynxAiUsageLimitError";
    this.status = 429;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export class FynxAiUsageSessionActiveError extends Error {
  constructor() {
    super("A FYNX AI voice session is already active on this account.");
    this.name = "FynxAiUsageSessionActiveError";
    this.status = 409;
  }
}

function validRealtimeCallId(callId) {
  return typeof callId === "string" && /^rtc_[A-Za-z0-9_-]{4,128}$/.test(callId);
}

export function getFynxAiUsageLimits() {
  return {
    dailyRequests: readPositiveInteger("FYNX_AI_USER_DAILY_REQUEST_LIMIT", DEFAULT_DAILY_REQUEST_LIMIT, 1000),
    dailyPhotoReviews: readPositiveInteger("FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT", DEFAULT_DAILY_PHOTO_REVIEW_LIMIT, 100)
  };
}

export function estimateGeminiCostUsd(model, inputTokens, outputTokens, now = new Date()) {
  const normalizedModel = String(model || "").trim();
  const isDefaultModel = normalizedModel === "gemini-3.8-flash";
  const ratesFrom2027 = now >= new Date("2027-01-01T00:00:00.000Z");
  const inputOverride = String(process.env.FYNX_AI_GEMINI_INPUT_USD_PER_MILLION || "").trim();
  const outputOverride = String(process.env.FYNX_AI_GEMINI_OUTPUT_USD_PER_MILLION || "").trim();
  if (!isDefaultModel && (!inputOverride || !outputOverride)) return null;
  const inputRate = inputOverride ? Number(inputOverride) : (ratesFrom2027 ? 1.5 : 0.75);
  const outputRate = outputOverride ? Number(outputOverride) : (ratesFrom2027 ? 7.5 : 3.75);
  if (![inputRate, outputRate].every(value => Number.isFinite(value) && value >= 0)) {
    throw new Error("FYNX Gemini pricing configuration is invalid.");
  }
  const input = Math.max(0, Math.trunc(Number(inputTokens) || 0));
  const output = Math.max(0, Math.trunc(Number(outputTokens) || 0));
  return Number(((input * inputRate + output * outputRate) / 1_000_000).toFixed(8));
}

export async function reserveFynxAiUsage({
  userId,
  provider = "gemini",
  capability = "conversation",
  model = "unknown",
  databasePool = getFynxDatabasePool()
} = {}) {
  if (userId === null || userId === undefined || String(userId).trim() === "") {
    throw new Error("authenticated user is required for FYNX AI usage accounting.");
  }
  if (!databasePool || typeof databasePool.connect !== "function") {
    throw new Error("FYNX AI usage accounting requires the existing FYNX database.");
  }
  const limits = getFynxAiUsageLimits();
  const userMonthlyBudget = readOptionalBudget("FYNX_AI_USER_MONTHLY_BUDGET_USD");
  const ownerMonthlyBudget = readOptionalBudget("FYNX_AI_MONTHLY_BUDGET_USD");
  await ensureUsageSchema(databasePool);

  const client = await databasePool.connect();
  const user = String(userId);
  try {
    await client.query("BEGIN");
    if (ownerMonthlyBudget !== null) {
      await client.query("SELECT pg_advisory_xact_lock(hashtext($1))", ["fynx-ai-usage:global"]);
    }
    await client.query("SELECT pg_advisory_xact_lock(hashtext($1))", ["fynx-ai-usage:" + user]);
    if (String(provider) === "openai" && String(capability).startsWith("voice_")) {
      const active = await client.query(
        "SELECT 1 FROM fynx_ai_usage_events WHERE user_id=$1 AND provider='openai' " +
        "AND capability LIKE 'voice_%' AND status='reserved' LIMIT 1",
        [user]
      );
      if (active.rows?.length) throw new FynxAiUsageSessionActiveError();
    }
    const counts = await client.query(
      "SELECT COUNT(*) FILTER (WHERE user_id=$1 AND created_at >= date_trunc('day', NOW()))::int AS daily_requests, " +
      "COUNT(*) FILTER (WHERE user_id=$1 AND created_at >= date_trunc('day', NOW()) AND capability='marketplace_photo_review')::int AS daily_photo_reviews, " +
      "COALESCE(SUM(estimated_cost_usd) FILTER (WHERE user_id=$1),0)::numeric AS monthly_cost, " +
      "COALESCE(SUM(estimated_cost_usd),0)::numeric AS global_monthly_cost " +
      "FROM fynx_ai_usage_events WHERE created_at >= date_trunc('month', NOW())",
      [user]
    );
    const row = counts.rows?.[0] || {};
    const dailyRequests = Number(row.daily_requests || 0);
    const dailyPhotoReviews = Number(row.daily_photo_reviews || 0);
    if (dailyRequests >= limits.dailyRequests) {
      throw new FynxAiUsageLimitError("You have reached today's FYNX AI request limit. Please try again tomorrow.");
    }
    if (capability === "marketplace_photo_review" && dailyPhotoReviews >= limits.dailyPhotoReviews) {
      throw new FynxAiUsageLimitError("You have reached today's Marketplace photo-review limit. Please try again tomorrow.");
    }
    if (userMonthlyBudget !== null && Number(row.monthly_cost || 0) >= userMonthlyBudget) {
      throw new FynxAiUsageLimitError("You have reached your monthly FYNX AI usage budget. Please try again next month.");
    }
    if (ownerMonthlyBudget !== null && Number(row.global_monthly_cost || 0) >= ownerMonthlyBudget) {
      throw new FynxAiUsageLimitError("FYNX AI has reached its owner-set monthly usage budget. Please try again next month.");
    }

    const id = randomUUID();
    await client.query(
      "INSERT INTO fynx_ai_usage_events(id,user_id,provider,capability,model,status) VALUES($1,$2,$3,$4,$5,'reserved')",
      [id, user, String(provider), String(capability), String(model)]
    );
    await client.query("COMMIT");
    return { id, userId: user, provider, capability, model };
  } catch (error) {
    await client.query("ROLLBACK").catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

export async function attachFynxAiRealtimeCall({ reservation, callId, databasePool = getFynxDatabasePool() } = {}) {
  if (!reservation?.id || !reservation?.userId || !validRealtimeCallId(callId)) {
    throw new Error("A valid FYNX AI reservation and Realtime call ID are required.");
  }
  await ensureUsageSchema(databasePool);
  const result = await databasePool.query(
    "UPDATE fynx_ai_usage_events SET realtime_call_id=$3 " +
    "WHERE id=$1 AND user_id=$2 AND provider='openai' AND status='reserved' AND realtime_call_id IS NULL",
    [reservation.id, String(reservation.userId), callId]
  );
  if (Number(result.rowCount || 0) !== 1) {
    throw new Error("FYNX AI voice reservation could not be bound to its Realtime call.");
  }
  return true;
}

export async function getActiveFynxAiRealtimeCall(userId, databasePool = getFynxDatabasePool()) {
  if (userId === null || userId === undefined || String(userId).trim() === "") return null;
  await ensureUsageSchema(databasePool);
  const result = await databasePool.query(
    "SELECT id,realtime_call_id,model,capability FROM fynx_ai_usage_events " +
    "WHERE user_id=$1 AND provider='openai' AND capability LIKE 'voice_%' " +
    "AND status='reserved' AND realtime_call_id IS NOT NULL ORDER BY created_at DESC LIMIT 1",
    [String(userId)]
  );
  const row = result.rows?.[0];
  if (!row || !validRealtimeCallId(String(row.realtime_call_id || ""))) return null;
  return {
    id: String(row.id),
    userId: String(userId),
    callId: String(row.realtime_call_id),
    model: String(row.model || ""),
    capability: String(row.capability || "")
  };
}

export async function settleFynxAiUsage({
  reservation,
  status = "completed",
  inputTokens = 0,
  outputTokens = 0,
  inputAudioTokens = 0,
  inputTextTokens = 0,
  cachedInputAudioTokens = 0,
  cachedInputTextTokens = 0,
  outputAudioTokens = 0,
  outputTextTokens = 0,
  inputAudioSeconds = 0,
  estimatedCostUsd = null,
  databasePool = getFynxDatabasePool()
} = {}) {
  if (!reservation?.id) throw new Error("FYNX AI usage reservation is required.");
  if (!databasePool) throw new Error("FYNX AI usage database is unavailable.");
  const safeStatus = status === "failed" ? "failed" : "completed";
  const input = Math.max(0, Math.trunc(Number(inputTokens) || 0));
  const output = Math.max(0, Math.trunc(Number(outputTokens) || 0));
  const safeCount = value => Math.max(0, Math.trunc(Number(value) || 0));
  const audioSeconds = Number(inputAudioSeconds);
  const safeAudioSeconds = Number.isFinite(audioSeconds) ? Math.max(0, audioSeconds) : 0;
  const cost = estimatedCostUsd === null || estimatedCostUsd === undefined || estimatedCostUsd === ""
    ? null
    : (Number.isFinite(Number(estimatedCostUsd)) ? Math.max(0, Number(estimatedCostUsd)) : null);
  await databasePool.query(
    "UPDATE fynx_ai_usage_events SET status=$2,input_tokens=$3,output_tokens=$4," +
    "input_audio_tokens=$5,input_text_tokens=$6,cached_input_audio_tokens=$7,cached_input_text_tokens=$8," +
    "output_audio_tokens=$9,output_text_tokens=$10,input_audio_seconds=$11," +
    "estimated_cost_usd=$12,finished_at=NOW() WHERE id=$1 AND status='reserved'",
    [
      reservation.id, safeStatus, input, output,
      safeCount(inputAudioTokens), safeCount(inputTextTokens),
      safeCount(cachedInputAudioTokens), safeCount(cachedInputTextTokens),
      safeCount(outputAudioTokens), safeCount(outputTextTokens),
      safeAudioSeconds, cost
    ]
  );
}

export async function getFynxAiUsageSummary(userId, databasePool = getFynxDatabasePool()) {
  if (userId === null || userId === undefined || String(userId).trim() === "") {
    throw new Error("authenticated user is required.");
  }
  if (!databasePool) throw new Error("FYNX AI usage database is unavailable.");
  await ensureUsageSchema(databasePool);
  const result = await databasePool.query(
    "SELECT COUNT(*) FILTER (WHERE created_at >= date_trunc('day', NOW()))::int AS daily_requests, " +
    "COUNT(*) FILTER (WHERE created_at >= date_trunc('day', NOW()) AND capability='marketplace_photo_review')::int AS daily_photo_reviews, " +
    "COALESCE(SUM(input_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_input_tokens, " +
    "COALESCE(SUM(output_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_output_tokens, " +
    "COALESCE(SUM(input_audio_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_input_audio_tokens, " +
    "COALESCE(SUM(input_text_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_input_text_tokens, " +
    "COALESCE(SUM(cached_input_audio_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_cached_input_audio_tokens, " +
    "COALESCE(SUM(cached_input_text_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_cached_input_text_tokens, " +
    "COALESCE(SUM(output_audio_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_output_audio_tokens, " +
    "COALESCE(SUM(output_text_tokens) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::bigint AS monthly_output_text_tokens, " +
    "COALESCE(SUM(input_audio_seconds) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::numeric AS monthly_input_audio_seconds, " +
    "COALESCE(SUM(estimated_cost_usd) FILTER (WHERE created_at >= date_trunc('month', NOW())),0)::numeric AS monthly_estimated_cost_usd " +
    "FROM fynx_ai_usage_events WHERE user_id=$1 AND created_at >= date_trunc('month', NOW())",
    [String(userId)]
  );
  const row = result.rows?.[0] || {};
  const byProvider = await databasePool.query(
    "SELECT provider,capability,model,COUNT(*)::int AS requests," +
    "COALESCE(SUM(input_tokens),0)::bigint AS input_tokens," +
    "COALESCE(SUM(output_tokens),0)::bigint AS output_tokens," +
    "COALESCE(SUM(input_audio_tokens),0)::bigint AS input_audio_tokens," +
    "COALESCE(SUM(input_text_tokens),0)::bigint AS input_text_tokens," +
    "COALESCE(SUM(cached_input_audio_tokens),0)::bigint AS cached_input_audio_tokens," +
    "COALESCE(SUM(cached_input_text_tokens),0)::bigint AS cached_input_text_tokens," +
    "COALESCE(SUM(output_audio_tokens),0)::bigint AS output_audio_tokens," +
    "COALESCE(SUM(output_text_tokens),0)::bigint AS output_text_tokens," +
    "COALESCE(SUM(input_audio_seconds),0)::numeric AS input_audio_seconds," +
    "COALESCE(SUM(estimated_cost_usd),0)::numeric AS estimated_cost_usd " +
    "FROM fynx_ai_usage_events WHERE user_id=$1 AND created_at >= date_trunc('month', NOW()) " +
    "GROUP BY provider,capability,model ORDER BY provider,capability,model",
    [String(userId)]
  );
  const limits = getFynxAiUsageLimits();
  const dailyRequests = Number(row.daily_requests || 0);
  const dailyPhotoReviews = Number(row.daily_photo_reviews || 0);
  return {
    dailyRequests,
    dailyRequestLimit: limits.dailyRequests,
    remainingDailyRequests: Math.max(0, limits.dailyRequests - dailyRequests),
    dailyPhotoReviews,
    dailyPhotoReviewLimit: limits.dailyPhotoReviews,
    remainingDailyPhotoReviews: Math.max(0, limits.dailyPhotoReviews - dailyPhotoReviews),
    monthlyInputTokens: Number(row.monthly_input_tokens || 0),
    monthlyOutputTokens: Number(row.monthly_output_tokens || 0),
    monthlyInputAudioTokens: Number(row.monthly_input_audio_tokens || 0),
    monthlyInputTextTokens: Number(row.monthly_input_text_tokens || 0),
    monthlyCachedInputAudioTokens: Number(row.monthly_cached_input_audio_tokens || 0),
    monthlyCachedInputTextTokens: Number(row.monthly_cached_input_text_tokens || 0),
    monthlyOutputAudioTokens: Number(row.monthly_output_audio_tokens || 0),
    monthlyOutputTextTokens: Number(row.monthly_output_text_tokens || 0),
    monthlyInputAudioSeconds: Number(row.monthly_input_audio_seconds || 0),
    monthlyEstimatedCostUsd: Number(row.monthly_estimated_cost_usd || 0),
    providerUsage: (byProvider.rows || []).map(item => ({
      provider: String(item.provider || ""),
      capability: String(item.capability || ""),
      model: String(item.model || ""),
      requests: Number(item.requests || 0),
      inputTokens: Number(item.input_tokens || 0),
      outputTokens: Number(item.output_tokens || 0),
      inputAudioTokens: Number(item.input_audio_tokens || 0),
      inputTextTokens: Number(item.input_text_tokens || 0),
      cachedInputAudioTokens: Number(item.cached_input_audio_tokens || 0),
      cachedInputTextTokens: Number(item.cached_input_text_tokens || 0),
      outputAudioTokens: Number(item.output_audio_tokens || 0),
      outputTextTokens: Number(item.output_text_tokens || 0),
      inputAudioSeconds: Number(item.input_audio_seconds || 0),
      estimatedCostUsd: Number(item.estimated_cost_usd || 0)
    })),
    monthlyUserBudgetUsd: readOptionalBudget("FYNX_AI_USER_MONTHLY_BUDGET_USD")
  };
}
