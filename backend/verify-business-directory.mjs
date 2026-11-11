import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const routes = await readFile(new URL("./businessRoutes.js", import.meta.url), "utf8");
const bootstrap = await readFile(new URL("./serverBootstrap.js", import.meta.url), "utf8");

assert.match(routes, /app\.get\(["']\/api\/business\/directory["'],\s*async/);
assert.match(routes, /business_profiles/);
assert.match(routes, /bp\.active\s*=\s*TRUE|active\s*=\s*TRUE/);
assert.match(routes, /business_name ILIKE|bp\.business_name ILIKE/);
assert.match(routes, /business_username ILIKE|bp\.business_username ILIKE/);
assert.match(routes, /category ILIKE|bp\.category ILIKE/);
assert.match(routes, /location ILIKE|bp\.location ILIKE/);
assert.ok(routes.includes('LIMIT $${limitIndex} OFFSET $${offsetIndex}'));
assert.match(routes, /hasMore/);
assert.match(routes, /verified/);
assert.doesNotMatch(routes, /SELECT\s+\*\s+FROM\s+business_profiles/i);
assert.match(bootstrap, /registerBusinessRoutes\(\{ app, auth \}\)/);
console.log("BUSINESS DIRECTORY API CONTRACT: GREEN");
