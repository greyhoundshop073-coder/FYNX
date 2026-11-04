import assert from "node:assert/strict";
import { executeFynxAiTool, getFynxAiTools } from "./fynxAiToolRegistry.js";
import {
  FYNX_AI_BLOCKED_PRIVATE_TOOLS,
  FYNX_AI_PRIVACY_INSTRUCTIONS,
  FYNX_AI_PROTECTED_DATA_CATEGORIES
} from "./fynxAiPrivacyPolicy.js";
import { buildRealtimeSessionConfig, FYNX_AI_VOICE_INSTRUCTIONS } from "./aiVoiceSession.js";

const expectedPublicTools = [
  "prepare_send_message",
  "search_users",
  "get_people_recommendations",
  "search_marketplace",
  "get_trending_posts"
];
const registryTools = getFynxAiTools().map(tool => tool.name);
assert.deepEqual(registryTools, expectedPublicTools, "only reviewed public-data and confirmation-gated tools may be exposed");

const blockedTools = ["get_my_profile", "get_conversation", "get_my_friends", "get_my_saved_posts"];
assert.deepEqual([...FYNX_AI_BLOCKED_PRIVATE_TOOLS], blockedTools);
assert.ok(blockedTools.every(name => !registryTools.includes(name)), "private-data tools must not be offered to Gemini");

let databaseCalls = 0;
const databasePool = {
  async query() {
    databaseCalls += 1;
    throw new Error("database must not be reached for a blocked private-data tool");
  }
};
for (const name of blockedTools) {
  await assert.rejects(
    executeFynxAiTool({ name, argumentsJson: "{}", userId: "17", databasePool }),
    /Private personal and financial information is not available through FYNX AI/
  );
}
assert.equal(databaseCalls, 0, "blocked private tools must be rejected before database access");

const privacyQueries = [];
const privacyAwarePool = {
  async query(sql, params) {
    privacyQueries.push({ sql: String(sql), params });
    return { rows: [], rowCount: 0 };
  }
};
await executeFynxAiTool({ name: "search_users", argumentsJson: JSON.stringify({ query: "alice" }), userId: "17", databasePool: privacyAwarePool });
await executeFynxAiTool({ name: "get_people_recommendations", argumentsJson: "{}", userId: "17", databasePool: privacyAwarePool });
await assert.rejects(
  executeFynxAiTool({ name: "prepare_send_message", argumentsJson: JSON.stringify({ recipientUsername: "alice_public", message: "Hello" }), userId: "17", databasePool: privacyAwarePool }),
  /recipient not found/
);
assert.equal(privacyQueries.length, 3, "profile search, recommendations and message preparation must each use a privacy-scoped query");
assert.match(privacyQueries[0].sql, /LEFT JOIN privacy_settings ps ON ps\.user_id=u\.id/);
assert.match(privacyQueries[0].sql, /profile_visibility.*Everyone/);
assert.match(privacyQueries[1].sql, /profile_visibility.*Everyone/);
assert.doesNotMatch(privacyQueries[1].sql, /friendships|social_follows/i, "AI recommendations must not read private social graphs");
assert.match(privacyQueries[2].sql, /profile_visibility.*Everyone/);
assert.match(privacyQueries[2].sql, /messages_visibility.*Everyone/);

const voiceTools = buildRealtimeSessionConfig().tools.map(tool => tool.name);
assert.deepEqual(voiceTools, expectedPublicTools, "realtime voice must use the same reviewed public-data tool set");
assert.ok(FYNX_AI_VOICE_INSTRUCTIONS.includes(FYNX_AI_PRIVACY_INSTRUCTIONS), "voice must receive the shared privacy instructions");

for (const phrase of [
  "wallet balances",
  "transaction histories",
  "bank account numbers",
  "PINs",
  "private FYNX chat messages",
  "phone numbers",
  "email addresses",
  "identity documents",
  "KYC records",
  "private orders",
  "delivery addresses",
  "friend/follower lists",
  "saved-post history"
]) {
  assert.ok(FYNX_AI_PRIVACY_INSTRUCTIONS.toLowerCase().includes(phrase.toLowerCase()), `privacy policy must cover: ${phrase}`);
}
assert.ok(FYNX_AI_PROTECTED_DATA_CATEGORIES.length >= 8);
console.log("FYNX AI privacy policy tests GREEN: private-data denial, no database reads, public tool allowlist and text/voice parity passed");
