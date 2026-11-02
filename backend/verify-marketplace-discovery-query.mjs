import assert from "node:assert/strict";
import fs from "node:fs";
import { appendMarketplaceDiscoveryPagination } from "./discoveryRoutes.js";

const filterSets = [
  [],
  ["q"],
  ["category"],
  ["location"],
  ["q", "category"],
  ["q", "location"],
  ["category", "location"],
  ["q", "category", "location"]
];

for (const filters of filterSets) {
  const params = ["authenticated-user-id", ...filters.map((name) => `value-for-${name}`)];
  const expectedLimit = params.length + 1;
  const expectedOffset = params.length + 2;
  const bindings = appendMarketplaceDiscoveryPagination(params, 31, 7);

  assert.equal(bindings.limitPlaceholder, `$${expectedLimit}`, `wrong LIMIT placeholder for filters: ${filters.join(",")}`);
  assert.equal(bindings.offsetPlaceholder, `$${expectedOffset}`, `wrong OFFSET placeholder for filters: ${filters.join(",")}`);
  assert.deepEqual(params.slice(-2), [32, 7], "LIMIT/OFFSET values must remain bound parameters");
  assert.equal(params.length, expectedOffset, "every generated placeholder must have a matching parameter");
}

const routeSource = fs.readFileSync(new URL("./discoveryRoutes.js", import.meta.url), "utf8");
assert.ok(routeSource.includes("appendMarketplaceDiscoveryPagination(params, limit, offset)"), "Marketplace discovery route must use the tested pagination binder");
assert.ok(routeSource.includes("LIMIT ${limitPlaceholder} OFFSET ${offsetPlaceholder}"), "Marketplace discovery SQL must bind both pagination values instead of inserting numeric literals");

console.log(`Marketplace discovery pagination verification GREEN (${filterSets.length} filter combinations)`);
