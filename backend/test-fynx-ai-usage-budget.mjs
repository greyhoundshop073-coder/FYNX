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
              monthly_cost: counts.monthlyCost || 0
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
  assert.ok(pool.calls.some(call => String(call.sql).includes("INSERT INTO fynx_ai_usage_events")));
  assert.ok(pool.calls.some(call => String(call.sql) === "COMMIT"));

  await settleFynxAiUsage({
    reservation, status: "completed", inputTokens: 123, outputTokens: 456,
    estimatedCostUsd: 0.002, databasePool: pool
  });
  const update = pool.calls.find(call => String(call.sql).includes("UPDATE fynx_ai_usage_events"));
  assert.deepEqual(update.params, [reservation.id, "completed", 123, 456, 0.002]);

  const summaryPool = fakePool();
  summaryPool.query = async (sql, params) => {
    summaryPool.calls.push({ sql: String(sql), params });
    if (String(sql).includes("monthly_input_tokens")) {
      return { rows: [{ daily_requests: 5, daily_photo_reviews: 2, monthly_input_tokens: 1200, monthly_output_tokens: 300, monthly_estimated_cost_usd: "0.0125" }] };
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
  console.log("FYNX AI usage budget tests GREEN: per-user daily limits, serialized reservations, token/cost metering and usage summary passed");
} finally {
  if (previousDaily === undefined) delete process.env.FYNX_AI_USER_DAILY_REQUEST_LIMIT;
  else process.env.FYNX_AI_USER_DAILY_REQUEST_LIMIT = previousDaily;
  if (previousPhotos === undefined) delete process.env.FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT;
  else process.env.FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT = previousPhotos;
}
