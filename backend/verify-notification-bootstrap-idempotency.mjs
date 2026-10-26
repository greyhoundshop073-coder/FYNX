import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { applyNotificationBootstrapPatch } from "./notificationBootstrapPatch.js";

const marker = "export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {";
const importLine = 'import { queueFynxNotification } from "./notificationPush.js";';
const replacement = importLine + "\n\n" + marker;
const patch = { marker, replacement, already: importLine, required: true };

// Reproduce the failure state: an equivalent import already exists but is not
// adjacent to the function marker searched by the older bootstrap implementation.
const existingImportFixture = importLine + "\nimport { Buffer } from \"node:buffer\";\n\n" + marker;
assert.equal(applyNotificationBootstrapPatch(existingImportFixture, patch), existingImportFixture,
  "an existing equivalent import must not be inserted a second time");

const cleanFixture = marker + "\n  // route body\n}\n";
const firstPass = applyNotificationBootstrapPatch(cleanFixture, patch);
assert.notEqual(firstPass, cleanFixture, "a missing import is added");
assert.equal(applyNotificationBootstrapPatch(firstPass, patch), firstPass,
  "running the patch twice is idempotent");
assert.equal(firstPass.split(importLine).length - 1, 1, "the import appears exactly once");

const routePatch = { marker: "res.status(201).json({message:messageJson(row)});",
  replacement: "await notifyGroupMembers(); res.status(201).json({message:messageJson(row)});" };
const routeFixture = "async function handler() { " + routePatch.marker + " }";
const routeFirstPass = applyNotificationBootstrapPatch(routeFixture, routePatch);
assert.equal(applyNotificationBootstrapPatch(routeFirstPass, routePatch), routeFirstPass,
  "route insertion is idempotent too");

assert.throws(() => applyNotificationBootstrapPatch("const untouched = true;", {
  marker: "missing marker", replacement: "missing marker plus insertion", required: true
}, "fixture.js"), /Notification bootstrap could not locate fixture.js marker/,
"required missing markers must fail loudly");

const bootstrap = await readFile(new URL("./notificationBootstrap.js", import.meta.url), "utf8");
const socialRoutes = await readFile(new URL("./socialRoutes.js", import.meta.url), "utf8");
assert.ok(bootstrap.includes('import { applyNotificationBootstrapPatch } from "./notificationBootstrapPatch.js";'),
  "the live bootstrap must use the tested patch helper");
assert.ok(bootstrap.includes("already: 'import { queueFynxNotification } from \"./notificationPush.js\";'"),
  "the social route import patch must declare its already-present equivalent import");
assert.equal(socialRoutes.split(importLine).length - 1, 1,
  "the actual canonical social routes file should have a single notification import");

console.log("Notification bootstrap idempotency: PASS (existing import, repeat starts, required markers)");
