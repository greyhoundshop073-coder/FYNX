import assert from "node:assert/strict";
import {
  FynxAiUsageLimitError,
  estimateGeminiCostUsd,
  getFynxAiUsageLimits,
  getFynxAiUsageSummary,
  reserveFynxAiUsage,
  settleFynxAiUsage
} from "./fynxAiUsageBudget.js";

function fakePool(counts = {}) {
  const calls = [];
  const pool = {
    calls,
    async query(sql, params) {
      calls.push({ sql: String(sql), params });
      return { rows: [] };
    },
    async connect() {
      return {
        async query(sql, params) {
          calls.push({ sql: String(sql), params });
          if (String(sql).includes("COUNT(*) FILTER")) {
            return { rows: [{
              daily_requests: counts.dailyRequests || 0,
              daily_photo_reviews: counts.dailyPhotoReviews || 0,
              monthly_cost: counts.monthlyCost || 0,
              global_monthly_cost: counts.globalMonthlyCost || 0
            }] };
          }
          return { rows: [] };
        },
        release() { calls.push({ released: true }); }
      };
    }
  };
  return pool;
}

const previousDaily = process.env.FYNX_AI_USER_DAILY_REQUEST_LIMIT;
const previousPhotos = process.env.FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT;
delete process.env.FYNX_AI_USER_DAILY_REQUEST_LIMIT;
delete process.env.FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT;

try {
  assert.deepEqual(getFynxAiUsageLimits(), { dailyRequests: 30, dailyPhotoReviews: 6 });
  assert.equal(estimateGeminiCostUsd("gemini-3.8-flash", 1_000_000, 1_000_000, new Date("2026-11-26T00:00:00Z")), 4.5);
  assert.equal(estimateGeminiCostUsd("gemini-3.8-flash", 1_000_000, 1_000_000, new Date("2027-01-01T00:00:00Z")), 9);
  assert.equal(estimateGeminiCostUsd("unpriced-model", 100, 100), null);

  const pool = fakePool();
  const reservation = await reserveFynxAiUsage({
    userId: "42", provider: "gemini", capability: "conversation",
    model: "gemini-3.8-flash", databasePool: pool
  });
  assert.ok(reservation.id);
  assert.ok(pool.calls.some(call => String(call.sql).includes("pg_advisory_xact_lock")));
  assert.ok(!pool.calls.some(call => String(call.params?.[0] || "").includes("fynx-ai-usage:global")), "do not globally serialize requests when no owner budget is configured");
  assert.ok(pool.calls.some(call => String(call.sql).includes("INSERT INTO fynx_ai_usage_events")));
  assert.ok(pool.calls.some(call => String(call.sql) === "COMMIT"));

  await settleFynxAiUsage({
    reservation, status: "completed", inputTokens: 123, outputTokens: 456,
    inputAudioTokens: 11, inputTextTokens: 12, outputAudioTokens: 13, outputTextTokens: 14,
    inputAudioSeconds: 2.5, estimatedCostUsd: 0.002, databasePool: pool
  });
  const update = pool.calls.find(call => String(call.sql).includes("UPDATE fynx_ai_usage_events"));
  assert.deepEqual(update.params, [reservation.id, "completed", 123, 456, 11, 12, 13, 14, 2.5, 0.002]);

  const summaryPool = fakePool();
  summaryPool.query = async (sql, params) => {
    summaryPool.calls.push({ sql: String(sql), params });
    if (String(sql).includes("monthly_input_tokens")) {
      return { rows: [{
        daily_requests: 5, daily_photo_reviews: 2, monthly_input_tokens: 1200, monthly_output_tokens: 300,
        monthly_input_audio_tokens: 90, monthly_input_text_tokens: 40,
        monthly_output_audio_tokens: 80, monthly_output_text_tokens: 30,
        monthly_input_audio_seconds: "7.5", monthly_estimated_cost_usd: "0.0125"
      }] };
    }
    if (String(sql).includes("GROUP BY provider,capability,model")) {
      return { rows: [{
        provider: "openai", capability: "voice_session", model: "gpt-realtime-2.1",
        requests: 2, input_tokens: "120", output_tokens: "80",
        input_audio_tokens: "90", input_text_tokens: "30",
        output_audio_tokens: "70", output_text_tokens: "10",
        input_audio_seconds: "7.5", estimated_cost_usd: "0.004"
      }] };
    }
    return { rows: [] };
  };
  const summary = await getFynxAiUsageSummary("42", summaryPool);
  assert.equal(summary.dailyRequests, 5);
  assert.equal(summary.remainingDailyRequests, 25);
  assert.equal(summary.dailyPhotoReviews, 2);
  assert.equal(summary.remainingDailyPhotoReviews, 4);
  assert.equal(summary.monthlyInputTokens, 1200);
  assert.equal(summary.monthlyOutputTokens, 300);
  assert.equal(summary.monthlyInputAudioTokens, 90);
  assert.equal(summary.monthlyInputTextTokens, 40);
  assert.equal(summary.monthlyOutputAudioTokens, 80);
  assert.equal(summary.monthlyOutputTextTokens, 30);
  assert.equal(summary.monthlyInputAudioSeconds, 7.5);
  assert.equal(summary.providerUsage.length, 1);
  assert.equal(summary.providerUsage[0].provider, "openai");
  assert.equal(summary.providerUsage[0].capability, "voice_session");
  assert.equal(summary.providerUsage[0].requests, 2);
  assert.equal(summary.providerUsage[0].inputAudioTokens, 90);
  assert.equal(summary.providerUsage[0].outputAudioTokens, 70);
  assert.equal(summary.providerUsage[0].estimatedCostUsd, 0.004);
  assert.equal(summary.monthlyEstimatedCostUsd, 0.0125);

  const dailyLimitPool = fakePool({ dailyRequests: 30 });
  await assert.rejects(
    reserveFynxAiUsage({ userId: "42", databasePool: dailyLimitPool }),
    error => error instanceof FynxAiUsageLimitError && error.status === 429
  );
  assert.ok(!dailyLimitPool.calls.some(call => String(call.sql).includes("INSERT INTO fynx_ai_usage_events")));

  const photoLimitPool = fakePool({ dailyPhotoReviews: 6 });
  await assert.rejects(
    reserveFynxAiUsage({ userId: "42", capability: "marketplace_photo_review", databasePool: photoLimitPool }),
    error => error instanceof FynxAiUsageLimitError
  );

  const previousMonthlyBudget = process.env.FYNX_AI_USER_MONTHLY_BUDGET_USD;
  process.env.FYNX_AI_USER_MONTHLY_BUDGET_USD = "2.00";
  const monthlyLimitPool = fakePool({ monthlyCost: 2.0 });
  await assert.rejects(
    reserveFynxAiUsage({ userId: "42", databasePool: monthlyLimitPool }),
    error => error instanceof FynxAiUsageLimitError
  );
  assert.ok(!monthlyLimitPool.calls.some(call => String(call.sql).includes("INSERT INTO fynx_ai_usage_events")));
  if (previousMonthlyBudget === undefined) delete process.env.FYNX_AI_USER_MONTHLY_BUDGET_USD;
  else process.env.FYNX_AI_USER_MONTHLY_BUDGET_USD = previousMonthlyBudget;
  const previousOwnerBudget = process.env.FYNX_AI_MONTHLY_BUDGET_USD;
  process.env.FYNX_AI_MONTHLY_BUDGET_USD = "10.00";
  const ownerBudgetPool = fakePool({ globalMonthlyCost: 10.00 });
  await assert.rejects(
    reserveFynxAiUsage({ userId: "900", databasePool: ownerBudgetPool }),
    error => error instanceof FynxAiUsageLimitError
  );
  assert.ok(!ownerBudgetPool.calls.some(call => String(call.sql).includes("INSERT INTO fynx_ai_usage_events")));
  assert.ok(ownerBudgetPool.calls.some(call => String(call.params?.[0] || "").includes("fynx-ai-usage:global")), "owner budget checks serialize across users");
  if (previousOwnerBudget === undefined) delete process.env.FYNX_AI_MONTHLY_BUDGET_USD;
  else process.env.FYNX_AI_MONTHLY_BUDGET_USD = previousOwnerBudget;
  console.log("FYNX AI usage budget tests GREEN: per-user daily limits, serialized reservations, token/cost metering and usage summary passed");
} finally {
  if (previousDaily === undefined) delete process.env.FYNX_AI_USER_DAILY_REQUEST_LIMIT;
  else process.env.FYNX_AI_USER_DAILY_REQUEST_LIMIT = previousDaily;
  if (previousPhotos === undefined) delete process.env.FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT;
  else process.env.FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT = previousPhotos;
}
