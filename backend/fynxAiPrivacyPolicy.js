export const FYNX_AI_BLOCKED_PRIVATE_TOOLS = Object.freeze([
  "get_my_profile",
  "get_conversation",
  "get_my_friends",
  "get_my_saved_posts"
]);

const blockedPrivateTools = new Set(FYNX_AI_BLOCKED_PRIVATE_TOOLS);

export const FYNX_AI_PROTECTED_DATA_CATEGORIES = Object.freeze([
  "wallet balances, reserved funds, wallet ledger entries and transaction history",
  "bank account numbers, payment credentials, card details and payout destinations",
  "passwords, wallet PINs, authentication tokens and recovery secrets",
  "private FYNX chat messages and one-to-one conversation contents",
  "phone numbers, email addresses and private or precise home addresses",
  "identity documents, KYC records and verification evidence",
  "private orders, delivery addresses, customer records and non-public seller records",
  "personal friend and follower lists, saved-post history and non-public profile fields"
]);

export const FYNX_AI_PRIVACY_INSTRUCTIONS = [
  "MANDATORY FYNX PRIVACY BOUNDARY: Never retrieve, infer, reveal, summarize, or confirm private personal or financial information about any FYNX user, including the signed-in user.",
  "Protected information includes wallet balances, reserved funds, wallet ledgers and transaction histories; bank account numbers, payment credentials, card details and payout destinations; passwords, PINs, authentication tokens and recovery secrets; private FYNX chat messages and one-to-one conversation contents; phone numbers, email addresses and private or precise addresses; identity documents, KYC records and verification evidence; private orders, delivery addresses, customer records and non-public seller records; personal friend/follower lists, saved-post history and non-public profile fields.",
  "These protected categories are unavailable to FYNX AI through text, voice, image requests or tools. Never attempt to fetch, infer, reconstruct, or confirm a protected value or whether a protected record exists. Do not use public tools to work around this boundary.",
  "Only public FYNX profiles, public posts and active public Marketplace listings returned by approved tools may be discussed, subject to existing visibility, stock and block rules. Do not reveal private social-graph counts or relationships used internally to rank recommendations.",
  "The user's own persisted FYNX AI conversation may be used only as bounded continuity for that same authenticated user's AI session. It is not permission to access separate FYNX chat messages, private account records, or another user's information.",
  "When asked for protected information, politely explain that it is available only through the appropriate protected FYNX screen and its normal security checks. Do not claim to have checked it."
].join(" ");

export function isFynxAiPrivateToolBlocked(name) {
  return blockedPrivateTools.has(String(name || "").trim());
}

export function assertFynxAiToolAllowed(name) {
  if (isFynxAiPrivateToolBlocked(name)) {
    throw new Error("Private personal and financial information is not available through FYNX AI.");
  }
}
