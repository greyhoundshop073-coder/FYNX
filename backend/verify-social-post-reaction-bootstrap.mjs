import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("./socialPostReactionBootstrap.js", import.meta.url), "utf8");
const start = source.indexOf("app.get('/api/social/posts/:id/reaction-users'");
const end = source.indexOf("\n  app.post('/api/social/posts/:id/reaction'", start);
assert.ok(start >= 0 && end > start, "reaction-users route should have clear extraction markers");
const route = source.slice(start, end);

assert.ok(route.includes("where.push('r.reaction_type=$2')"),
  "reaction filtering must use the schema's reaction_type column");
assert.ok(route.includes("r.reaction_type AS reaction,r.created_at"),
  "the selected reaction must use reaction_type and retain the API's reaction alias");
assert.ok(route.includes("WHERE \${where.join(' AND ')}"),
  "the generated SQL template must preserve WHERE interpolation until the route runs");
assert.ok(!route.includes("r.reaction=$2") && !route.includes("r.reaction,r.created_at"),
  "the reaction-users route must not reference the nonexistent reaction column");

const schemaStart = source.indexOf("CREATE TABLE IF NOT EXISTS social_post_reactions");
const schemaEnd = source.indexOf(");", schemaStart);
assert.ok(schemaStart >= 0 && schemaEnd > schemaStart);
assert.ok(source.slice(schemaStart, schemaEnd).includes("reaction_type TEXT NOT NULL"),
  "test fixture must correspond to the durable reaction schema");

console.log("Home post reaction SQL contract: PASS (column, API alias, generated interpolation)");
