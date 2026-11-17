import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { POST_VISIBILITIES, normalizePostVisibility, resolvePostVisibility } from "./postVisibility.js";

// Every audience the Android composer can send must be accepted.
for (const name of ["PUBLIC", "FRIENDS_ONLY", "SELECTED_PEOPLE", "ONLY_ME"]) {
  assert.equal(normalizePostVisibility(name), name, name + " must be accepted");
  assert.equal(normalizePostVisibility(name.toLowerCase()), name, name + " must be case-insensitive");
  assert.equal(normalizePostVisibility("  " + name + " "), name, name + " must ignore spaces");
}
assert.equal(normalizePostVisibility("FRIENDS"), "FRIENDS_ONLY", "older clients' FRIENDS maps to FRIENDS_ONLY");
for (const bad of ["", null, undefined, "EVERYBODY", "PRIVATE", "friends only", 7, {}]) {
  assert.equal(normalizePostVisibility(bad), null, JSON.stringify(bad) + " must be rejected");
}
assert.deepEqual([...POST_VISIBILITIES], ["PUBLIC", "FRIENDS_ONLY", "SELECTED_PEOPLE", "ONLY_ME"]);

// Default account setting: public becomes friends-only, but nothing is ever widened.
assert.deepEqual(resolvePostVisibility("PUBLIC", "My friends"), { blocked: false, visibility: "FRIENDS_ONLY" });
assert.deepEqual(resolvePostVisibility("FRIENDS_ONLY", "My friends"), { blocked: false, visibility: "FRIENDS_ONLY" });
assert.deepEqual(resolvePostVisibility("SELECTED_PEOPLE", "My friends"), { blocked: false, visibility: "SELECTED_PEOPLE" });
assert.deepEqual(resolvePostVisibility("ONLY_ME", "My friends"), { blocked: false, visibility: "ONLY_ME" }, "an only-me post must never become friends-visible");

// Everyone keeps the author's choice.
for (const name of POST_VISIBILITIES) assert.deepEqual(resolvePostVisibility(name, "Everyone"), { blocked: false, visibility: name });

// Nobody blocks posting whatever was asked.
for (const name of POST_VISIBILITIES) assert.deepEqual(resolvePostVisibility(name, "Nobody"), { blocked: true, visibility: null });

// Missing or unknown requests fall back to the safe audience, never to public.
assert.deepEqual(resolvePostVisibility(undefined, "Everyone"), { blocked: false, visibility: "FRIENDS_ONLY" });
assert.deepEqual(resolvePostVisibility("EVERYBODY", "My friends"), { blocked: false, visibility: "FRIENDS_ONLY" });

// Both layers use the shared rules, so they cannot drift apart again.
const hardening = await readFile(new URL("./socialHardening.js", import.meta.url), "utf8");
const privacy = await readFile(new URL("./privacyRoutes.js", import.meta.url), "utf8");
assert.ok(hardening.includes('from "./postVisibility.js"') && hardening.includes("normalizePostVisibility("), "request validation uses the shared rules");
assert.ok(!hardening.includes('["PUBLIC", "FRIENDS", "ONLY_ME"]'), "the old three-name whitelist must be gone");
assert.ok(privacy.includes('from "./postVisibility.js"') && privacy.includes("resolvePostVisibility("), "privacy guard uses the shared rules");
assert.ok(!privacy.includes('req.body={...(req.body||{}),visibility:"FRIENDS_ONLY"}'), "the guard must not overwrite the author's choice");


// The fallback multi-media route (used when the install step has not run) must not turn only-me or
// selected-people posts into public ones.
const bootstrap = await readFile(new URL("./serverBootstrap.js", import.meta.url), "utf8");
assert.ok(!bootstrap.includes("visibility=req.body?.visibility==='FRIENDS_ONLY'?'FRIENDS_ONLY':'PUBLIC'"), "fallback multi-media route must not map unknown audiences to PUBLIC");
assert.ok(bootstrap.includes("['PUBLIC','FRIENDS_ONLY','SELECTED_PEOPLE','ONLY_ME'].includes(rawVisibility)"), "fallback multi-media route keeps all four audiences");

console.log("Post visibility contract verification passed");
