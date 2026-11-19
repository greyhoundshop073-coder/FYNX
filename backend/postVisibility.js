// One definition of who can see a post, shared by the request validation and the privacy guard so the
// two layers cannot disagree about the names the app sends.

export const POST_VISIBILITIES = Object.freeze(["PUBLIC", "FRIENDS_ONLY", "SELECTED_PEOPLE", "ONLY_ME"]);

// The app sends the enum names above. "FRIENDS" is accepted from older clients and means FRIENDS_ONLY.
export function normalizePostVisibility(value) {
  const key = String(value ?? "").trim().toUpperCase();
  if (key === "FRIENDS") return "FRIENDS_ONLY";
  return POST_VISIBILITIES.includes(key) ? key : null;
}

// The account-level "Posts" privacy setting can only narrow who sees a post, never widen it.
//   Everyone   -> the author's choice is kept
//   My friends -> a public post becomes friends-only; friends-only, selected people and only-me are kept
//   Nobody     -> posting is blocked
export function resolvePostVisibility(requested, accountSetting) {
  if (accountSetting === "Nobody") return { blocked: true, visibility: null };
  const chosen = normalizePostVisibility(requested) ?? "FRIENDS_ONLY";
  if (accountSetting === "Everyone") return { blocked: false, visibility: chosen };
  return { blocked: false, visibility: chosen === "PUBLIC" ? "FRIENDS_ONLY" : chosen };
}
