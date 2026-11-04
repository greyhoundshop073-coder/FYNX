export const MAX_CONTEXT_HISTORY_ITEMS = 80;
export const MAX_CONTEXT_SUMMARY_CHARS = 900;
export const MAX_CONTEXT_TASK_CHARS = 160;
const PROVIDER_HISTORY_WINDOW = 12;
const MAX_CONTEXT_MESSAGE_CHARS = 260;

function normalizePersistedMessage(item) {
  if (!item || (item.role !== "user" && item.role !== "assistant")) return null;
  if (typeof item.text !== "string") return null;
  const text = item.text.replace(/\s+/g, " ").trim();
  return text ? { role: item.role, text } : null;
}

/**
 * Build bounded continuity from messages already persisted for one owned
 * conversation. The caller must scope its database query by conversation ID
 * and authenticated user ID before calling this helper.
 *
 * The provider still receives its normal latest 12 messages separately.
 * This excerpt contains only older turns and is historical context, not
 * authoritative FYNX state or a new instruction.
 */
export function buildAiConversationContext(history = []) {
  const normalized = (Array.isArray(history) ? history : [])
    .slice(-MAX_CONTEXT_HISTORY_ITEMS)
    .map(normalizePersistedMessage)
    .filter(Boolean);

  const olderMessages = normalized.slice(0, -PROVIDER_HISTORY_WINDOW);
  const selected = [];
  let summaryLength = 0;

  for (const item of olderMessages.slice(-48).reverse()) {
    const prefix = item.role === "user" ? "User: " : "Assistant: ";
    const remaining = MAX_CONTEXT_SUMMARY_CHARS - summaryLength;
    if (remaining <= prefix.length + 12) break;

    const excerpt = item.text.slice(0, Math.min(MAX_CONTEXT_MESSAGE_CHARS, remaining - prefix.length));
    const line = prefix + excerpt;
    if (selected.length && summaryLength + line.length + 1 > MAX_CONTEXT_SUMMARY_CHARS) break;
    selected.push(line);
    summaryLength += line.length + (selected.length > 1 ? 1 : 0);
  }

  const latestUserMessage = [...normalized].reverse().find(item => item.role === "user");
  return {
    summary: selected.reverse().join("\n").slice(0, MAX_CONTEXT_SUMMARY_CHARS),
    currentTask: latestUserMessage?.text.slice(0, MAX_CONTEXT_TASK_CHARS) || ""
  };
}
