import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import vm from "node:vm";
import { fileURLToPath, pathToFileURL } from "node:url";

const sourcePath = fileURLToPath(new URL("./socialPostReactionBootstrap.js", import.meta.url));
const tempDir = await mkdtemp(path.join(os.tmpdir(), "fynx-social-post-reactions-"));

try {
  const bootstrapPath = path.join(tempDir, "socialPostReactionBootstrap.js");
  const routesPath = path.join(tempDir, "socialRoutes.js");
  await writeFile(bootstrapPath, await readFile(sourcePath, "utf8"), "utf8");
  await writeFile(routesPath, "function registerSocialRoutes() {\n  // isolated bootstrap test\n}\n", "utf8");

  const { installSocialPostReactions } = await import(pathToFileURL(bootstrapPath).href);
  await installSocialPostReactions();
  const installed = await readFile(routesPath, "utf8");

  assert.ok(installed.includes("WHERE ${where.join(' AND ')}"), "request-scoped WHERE template stays in the generated route instead of evaluating during bootstrap");
  assert.ok(installed.includes("r.reaction_type AS reaction,r.created_at"), "reaction users query reads the actual reaction_type schema");
  assert.ok(installed.includes("where.push('r.reaction_type=$2')"), "reaction type filter reads the actual reaction_type schema");
  assert.ok(!installed.includes("r.reaction=$2"), "the generated query does not reference a nonexistent reaction column");
  assert.ok(installed.includes("await ensureHomePostReactionSchema()"), "reaction-user route initializes its table before querying it");
  assert.doesNotThrow(() => new vm.Script(installed, { filename: routesPath }), "generated route module parses after bootstrap injection");

  await installSocialPostReactions();
  assert.equal(await readFile(routesPath, "utf8"), installed, "repeated installation is idempotent");
  console.log("Home post-reaction bootstrap: PASS (deferred SQL, schema, column names, generated syntax, idempotency)");
} finally {
  await rm(tempDir, { recursive: true, force: true });
}
