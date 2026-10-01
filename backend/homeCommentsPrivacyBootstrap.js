import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

// Reusable, idempotent hardening pass for the existing Home 4B comment routes.
// It never creates a second comments API surface.
export async function installHomeCommentPrivacy() {
  const backendDir = path.dirname(fileURLToPath(import.meta.url));
  const socialPath = path.join(backendDir, "socialRoutes.js");
  let source = await readFile(socialPath, "utf8");

  // Home 4D cursor hardening: the comments route binds LIMIT to $3, so a
  // supplied `before` cursor must be bound to $4. Apply this even when the
  // privacy marker already exists because the route source may have been
  // installed by an earlier startup before this correction was deployed.
  const oldCursorBinding = "const cursorClause = before === null ? '' : ' AND c.id < $3';";
  const fixedCursorBinding = "const cursorClause = before === null ? '' : ' AND c.id < $4';";
  if (source.includes(oldCursorBinding)) {
    source = source.replace(oldCursorBinding, fixedCursorBinding);
    await writeFile(socialPath, source);
  }

  if (source.includes("fynxHomeCommentsPrivacyBatch")) return;

  if (source.includes("fynxHomeCommentsBatch4b")) {
    const oldComments = `WHERE c.post_id=$1${'${cursorClause}'}\n          ORDER BY c.id DESC\n          LIMIT $2`;
    const newComments = `WHERE c.post_id=$1\n            AND NOT EXISTS (\n              SELECT 1 FROM blocks b\n               WHERE (b.blocker_id=$2 AND b.blocked_id=c.author_id)\n                  OR (b.blocker_id=c.author_id AND b.blocked_id=$2)\n            )${'${cursorClause}'}\n          ORDER BY c.id DESC\n          LIMIT $3`;
    if (source.includes(oldComments)) {
      source = source.replace(oldComments, newComments);
      source = source.replace(
        "const params = [postId, limit + 1];",
        "const params = [postId, req.user.sub, limit + 1];"
      );
      source = source.replace(
        "const cursorClause = before === null ? '' : ' AND c.id < $3';",
        "const cursorClause = before === null ? '' : ' AND c.id < $4';"
      );
    }

    const oldReplyParent = "const parent = await pool.query('SELECT id FROM social_post_comments WHERE id=$1 AND post_id=$2', [parentId, postId]);";
    const newReplyParent = `const parent = await pool.query(\n        \`SELECT c.id\n           FROM social_post_comments c\n          WHERE c.id=$1 AND c.post_id=$2\n            AND NOT EXISTS (\n              SELECT 1 FROM blocks b\n               WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id)\n                  OR (b.blocker_id=c.author_id AND b.blocked_id=$3)\n            )\`,\n        [parentId, postId, req.user.sub]\n      );`;
    source = source.replace(oldReplyParent, newReplyParent);

    const oldReplies = `WHERE c.post_id=$1 AND c.parent_comment_id=$2\n          ORDER BY c.id ASC LIMIT $3`;
    const newReplies = `WHERE c.post_id=$1 AND c.parent_comment_id=$2\n            AND NOT EXISTS (\n              SELECT 1 FROM blocks b\n               WHERE (b.blocker_id=$3 AND b.blocked_id=c.author_id)\n                  OR (b.blocker_id=c.author_id AND b.blocked_id=$3)\n            )\n          ORDER BY c.id ASC LIMIT $4`;
    source = source.replace(oldReplies, newReplies);
    source = source.replace(
      "[postId, parentId, limit]\n      );",
      "[postId, parentId, req.user.sub, limit]\n      );"
    );
    source = source.replace(
      "// fynxHomeCommentsBatch4b: paginated comments and validated replies.",
      "// fynxHomeCommentsBatch4b: paginated comments and validated replies.\n  // fynxHomeCommentsPrivacyBatch: symmetric block filtering and visibility hardening."
    );
    await writeFile(socialPath, source);
    return;
  }

  const schemaNeedle = "CREATE INDEX IF NOT EXISTS social_post_comments_post_idx ON social_post_comments(post_id, created_at ASC);";
  if (!source.includes(schemaNeedle)) throw new Error("Home comment privacy patch could not locate social comment schema marker");
  source = source.replace(schemaNeedle, `${schemaNeedle}\n        ALTER TABLE social_post_comments ADD COLUMN IF NOT EXISTS parent_comment_id BIGINT REFERENCES social_post_comments(id) ON DELETE CASCADE;\n        CREATE INDEX IF NOT EXISTS social_post_comments_parent_idx ON social_post_comments(post_id, parent_comment_id, created_at ASC);`);
  throw new Error("Home comment privacy patch expected the existing 4B route marker; refusing to create a duplicate comments API");
}
