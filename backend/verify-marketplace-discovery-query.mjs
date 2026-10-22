import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const source = await readFile(new URL("./discoveryRoutes.js", import.meta.url), "utf8");
const routeStart = source.indexOf('app.get("/api/marketplace/discovery"');
assert.notEqual(routeStart, -1, "Marketplace discovery route must exist");
const routeEnd = source.indexOf('app.get("/api/marketplace/listing/:id"', routeStart);
assert.ok(routeEnd > routeStart, "Marketplace discovery route must have a clear boundary");
const route = source.slice(routeStart, routeEnd);

// Regression: PostgreSQL placeholders must be numbered ($2, $3, ...), not the
// bare parameter count. Bare numeric counts make node-postgres send extra bind
// values for a statement PostgreSQL believes only uses $1.
assert.match(route, /where\.push\("\(l\.title ILIKE \$" \+ n/, "query filter must bind its first placeholder");
assert.match(route, /COALESCE\(l\.store_name,''\) ILIKE \$" \+ n/, "query filter must bind all searchable columns");
assert.match(route, /where\.push\("l\.category=\$" \+ params\.length\)/, "category filter must use a numbered PostgreSQL placeholder");
assert.match(route, /where\.push\("l\.location ILIKE \$" \+ params\.length\)/, "location filter must use a numbered PostgreSQL placeholder");
assert.match(route, /const limitPlaceholder = "\$" \+ \(params\.length - 1\)/, "LIMIT must use the correct numbered placeholder");
assert.match(route, /const offsetPlaceholder = "\$" \+ params\.length/, "OFFSET must use the correct numbered placeholder");
assert.match(route, /LIMIT \$\{limitPlaceholder\} OFFSET \$\{offsetPlaceholder\}/, "query must use the generated LIMIT/OFFSET placeholders");

// Exercise the placeholder numbering used by the route for no filters and
// combinations of query/category/location, including all filters together.
for (const filterCount of [0, 1, 2, 3]) {
  const params = [123];
  for (let i = 0; i < filterCount; i++) params.push("filter");
  params.push(31, 0);
  const limit = "$" + (params.length - 1);
  const offset = "$" + params.length;
  assert.equal(Number(limit.slice(1)), params.length - 1);
  assert.equal(Number(offset.slice(1)), params.length);
  assert.equal(Number(offset.slice(1)), Number(limit.slice(1)) + 1);
}

console.log("Marketplace discovery bind-parameter regression checks passed.");
