import assert from "node:assert/strict";
import {
  buildAiConversationContext,
  loadPersistedAiConversationHistory,
  MAX_CONTEXT_HISTORY_ITEMS,
  MAX_CONTEXT_SUMMARY_CHARS,
  MAX_CONTEXT_TASK_CHARS
} from "./aiConversationContext.js";

function message(role, text) { return { role, text }; }

const history = [];
for (let index = 0; index < 24; index += 1) {
  history.push(message("user", `Earlier user turn ${index}: remember the blue bicycle`));
  history.push(message("assistant", `Earlier assistant turn ${index}: the bicycle is blue`));
}
history.push(message("user", "Please compare the two options we discussed."));
const context = buildAiConversationContext(history);

assert.ok(context.summary.includes("Earlier user turn"), "older turns must remain available");
assert.ok(context.summary.includes("Earlier assistant turn"), "assistant context must be retained");
assert.ok(!context.summary.includes("Please compare the two options"), "the latest 12-message provider window must not be duplicated in the summary");
assert.equal(context.currentTask, "Earlier user turn 18: remember the blue bicycle");
assert.ok(!context.currentTask.includes("Please compare the two options"), "prior-task hint must not duplicate the recent provider history");
assert.ok(context.summary.length <= MAX_CONTEXT_SUMMARY_CHARS);
assert.ok(context.currentTask.length <= MAX_CONTEXT_TASK_CHARS);

const oversized = Array.from({ length: MAX_CONTEXT_HISTORY_ITEMS + 20 }, (_, index) =>
  message(index % 2 === 0 ? "user" : "assistant", `Turn ${index}: ${"x".repeat(500)}`)
);
const bounded = buildAiConversationContext(oversized);
assert.ok(bounded.summary.length <= MAX_CONTEXT_SUMMARY_CHARS, "summary must remain within its hard character limit");
assert.ok(bounded.currentTask.length <= MAX_CONTEXT_TASK_CHARS, "current task must remain within its hard character limit");
assert.ok(bounded.summary.includes(`Turn ${MAX_CONTEXT_HISTORY_ITEMS + 19}`) === false, "the most recent 12 turns belong to provider history, not the older-context excerpt");

const sanitized = buildAiConversationContext([
  message("system", "must not enter context"),
  message("user", "  useful   user   text  "),
  message("assistant", " "),
  { role: "assistant", text: 42 }
]);
assert.equal(sanitized.summary, "");
assert.equal(sanitized.currentTask, "useful user text");

const empty = buildAiConversationContext(null);
assert.deepEqual(empty, { summary: "", currentTask: "" });

let queryText = "";
let queryValues = [];
const loadedHistory = await loadPersistedAiConversationHistory({
  async query(sql, values) {
    queryText = sql;
    queryValues = values;
    return { rows: [message("assistant", "second"), message("user", "first")] };
  }
}, "conversation-73", "user-17");
assert.match(queryText, /WHERE conversation_id=\$1 AND user_id=\$2/);
assert.match(queryText, /LIMIT \$3/);
assert.deepEqual(queryValues, ["conversation-73", "user-17", MAX_CONTEXT_HISTORY_ITEMS]);
assert.deepEqual(loadedHistory, [message("user", "first"), message("assistant", "second")]);
await assert.rejects(loadPersistedAiConversationHistory({}, "conversation-73", "user-17"), /database query is required/);
await assert.rejects(loadPersistedAiConversationHistory({ query: async () => ({ rows: [] }) }, null, "user-17"), /IDs are required/);

const original = [message("user", "Do not mutate me")];
buildAiConversationContext(original);
assert.equal(original[0].text, "Do not mutate me");

console.log("AI conversation context tests GREEN: 6 suites passed");
