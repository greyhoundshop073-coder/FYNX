import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const directory = path.dirname(fileURLToPath(import.meta.url));
const server = await readFile(path.join(directory, "server.js"), "utf8");
const start = server.indexOf("async function initDatabase() {");
assert.notEqual(start, -1, "database initializer must exist");
const end = server.indexOf("\n}\n", start);
assert.notEqual(end, -1, "database initializer must have a closing boundary");
const initializer = server.slice(start, end);

const queryStart = initializer.indexOf("await pool.query(`");
assert.notEqual(queryStart, -1, "schema batch must use a SQL template query");
const queryEnd = initializer.indexOf("`);", queryStart);
assert.notEqual(queryEnd, -1, "schema batch must close without bind parameters");
const schemaBatch = initializer.slice(queryStart, queryEnd + 3);

assert.doesNotMatch(
  schemaBatch,
  /\$\d+/,
  "the multi-command schema batch must not contain bind placeholders"
);
assert.doesNotMatch(
  schemaBatch,
  /\\n/,
  "the SQL schema batch must contain real line breaks, not literal backslash-n tokens"
);
assert.match(schemaBatch, /CREATE TABLE IF NOT EXISTS users/);
assert.match(schemaBatch, /CREATE TABLE IF NOT EXISTS messages/);
assert.doesNotMatch(schemaBatch, /UPDATE users SET verified = FALSE;/, "startup must never reset every user verification flag");
assert.match(initializer, /await pool\\.query\\("UPDATE users SET verified = TRUE WHERE lower\\(username\\) = \\$1", \\[officialFynxUsername\\]\\);/, "only the configured official account may be marked verified");
assert.doesNotMatch(
  schemaBatch,
  /UPDATE users SET verified = TRUE WHERE lower\(username\) = \$1/,
  "the parameterized verification update must not be inside the multi-command batch"
);
assert.match(
  initializer,
  /await pool\.query\("UPDATE users SET verified = TRUE WHERE lower\(username\) = \$1", \[officialFynxUsername\]\);/,
  "the official-account update must remain a separate parameterized query"
);
assert.match(initializer, /await pool\.query\(`DO \$\$ BEGIN/);

console.log("INIT DATABASE QUERY PROTOCOL: GREEN");
console.log("Verified the multi-statement schema batch has no bind parameters and the official-user update remains safely parameterized.");
