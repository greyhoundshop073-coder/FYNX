import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const source = await readFile(new URL("./server.js", import.meta.url), "utf8");
const routeStart = source.indexOf('app.get("/api/media/:id"');
assert.notEqual(routeStart, -1, "authenticated media route must exist");

const queryStart = source.indexOf("let result = await pool.query(`", routeStart);
assert.notEqual(queryStart, -1, "media route must execute its primary authorization query");
const sqlStart = source.indexOf("`", queryStart) + 1;
const sqlEnd = source.indexOf("`, [mediaId, req.user.sub]);", sqlStart);
assert.ok(sqlEnd > sqlStart, "media authorization SQL must be a complete template literal");

const sql = source.slice(sqlStart, sqlEnd);
const normalized = sql.replace(/'(?:''|[^'])*'/g, "''").replace(/--[^\n]*/g, "");
let depth = 0;
for (const character of normalized) {
  if (character === "(") depth++;
  if (character === ")") depth--;
  assert.ok(depth >= 0, "media authorization SQL must not close a parenthesis before opening it");
}
assert.equal(depth, 0, "media authorization SQL parentheses must be balanced");
assert.match(sql, /WHERE\s+mm\.id\s*=\s*\$1/i, "media lookup must be restricted to the requested media id");
assert.match(sql, /mm\.owner_id\s*=\s*\$2/i, "media owner must be allowed to read their own media");
assert.match(sql, /privacy_settings/i, "profile-photo visibility rules must be enforced");
assert.match(sql, /FROM\s+blocks/i, "blocked users must be excluded");
assert.match(sql, /FROM\s+statuses/i, "status media must retain audience checks");
assert.doesNotMatch(sql, /ORDER\s+BY\s+m\.id\s+DESC/i, "media query must not order by an unjoined messages alias");
assert.doesNotMatch(sql, /ORDER\\s+BY/i, "single-media authorization lookup must not contain an unnecessary or invalid ORDER BY clause");

console.log("Media route SQL regression checks passed.");
