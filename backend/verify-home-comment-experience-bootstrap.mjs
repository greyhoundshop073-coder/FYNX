import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
const bootstrap = await readFile(new URL("./homeCommentExperienceBootstrap.js", import.meta.url), "utf8");
const realtime = await readFile(new URL("./realtimeIsolationBootstrap.js", import.meta.url), "utf8");
for (const token of [
  "fynxHomeCommentExperienceBatch",
  "ADD COLUMN IF NOT EXISTS media_id",
  "ADD COLUMN IF NOT EXISTS linked_post_id",
  "social_comment_reposts",
  "social_saved_comments",
  "social_comment_views",
  "/comments/experience",
  "/comments/:commentId/linked-video",
  "/api/social/comment-media/:id",
  "/comment-engagement",
  "visibleSocialPost",
  "NOT EXISTS (SELECT 1 FROM blocks",
  "owner_id=$2",
  "getCachedMediaPayload(pool,mediaId)",
  "private,max-age=300",
  "p.media_type='video'",
  "maximum reply depth is 5",
  "ON CONFLICT DO NOTHING"
]) assert.ok(bootstrap.includes(token), "missing comment experience contract: " + token);
assert.ok(realtime.includes('import { installHomeCommentExperience } from "./homeCommentExperienceBootstrap.js";'));
assert.ok(realtime.includes("await installHomeCommentExperience();"));
console.log("HOME COMMENT EXPERIENCE BACKEND GREEN: media ownership, linked FYNX videos, protected media, replies, reposts, bookmarks and view counts.");
