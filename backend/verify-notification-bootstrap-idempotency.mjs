import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { applyNotificationBootstrapPatch } from "./notificationBootstrapPatch.js";

const importMarker = "import { inspectTrustSafetyText } from './trustSafety.js';";
const importReplacement = "import { inspectTrustSafetyText } from './trustSafety.js';\nimport { queueFynxNotification } from './notificationPush.js';";
const importPatch = { marker: importMarker, replacement: importReplacement };
const originalModule = `${importMarker}\nexport function registerGroupRoutes() {}\n`;
const firstPass = applyNotificationBootstrapPatch(originalModule, importPatch, "groupRoutes.js");
const secondPass = applyNotificationBootstrapPatch(firstPass, importPatch, "groupRoutes.js");
assert.notEqual(firstPass, originalModule, "the bootstrap applies a missing import");
assert.equal(secondPass, firstPass, "a repeated bootstrap is a no-op once the replacement exists");
assert.equal(secondPass.split("import { queueFynxNotification }").length - 1, 1, "a repeated start never duplicates the notification import");

const routeMarker = "res.status(201).json({message:messageJson(row)});";
const routeReplacement = "await notifyGroupMembers(); " + routeMarker;
const routePatch = { marker: routeMarker, replacement: routeReplacement };
const originalRoute = `async function handler() { ${routeMarker} }\n`;
const routeFirstPass = applyNotificationBootstrapPatch(originalRoute, routePatch, "groupRoutes.js");
assert.equal(applyNotificationBootstrapPatch(routeFirstPass, routePatch, "groupRoutes.js"), routeFirstPass, "route wiring is also idempotent");

assert.throws(
  () => applyNotificationBootstrapPatch("const untouched = true;", {
    marker: "missing marker",
    replacement: "missing marker plus insertion",
    required: true,
    already: "already applied replacement"
  }, "test.js"),
  /Notification bootstrap could not locate test.js marker/,
  "required patch markers fail clearly instead of silently mis-wiring routes"
);


// Generated routes are inserted into socialRoutes.js. Escape template interpolation here
// so the SQL WHERE clause is evaluated when the inserted handler runs, not at bootstrap.
const reactionBootstrapSource = await readFile(new URL("./socialPostReactionBootstrap.js", import.meta.url), "utf8");
assert.ok(reactionBootstrapSource.includes("WHERE \\${where.join(' AND ')}"), "reaction bootstrap must preserve the generated route's WHERE interpolation");
assert.equal(reactionBootstrapSource.split("WHERE \\${where.join(' AND ')}").length - 1, 1, "the dynamic WHERE clause has one deliberately escaped interpolation");


assert.ok(reactionBootstrapSource.includes("r.reaction_type AS reaction"), "reaction-users query must read the schema's reaction_type column");
assert.ok(reactionBootstrapSource.includes("where.push('r.reaction_type=$2')"), "reaction-users filter must use the schema's reaction_type column");
assert.equal(reactionBootstrapSource.includes("r.reaction=$2"), false, "reaction-users query must not reference a nonexistent reaction column");


const socialImportMarker = 'export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {';
const existingNotificationImport = 'import { queueFynxNotification, notifyCommentMentions } from "./notificationPush.js";';
const socialImportPatch = { marker: socialImportMarker, replacement: 'import { queueFynxNotification } from "./notificationPush.js";\n\n' + socialImportMarker, already: existingNotificationImport, required: true };
const existingSocialModule = existingNotificationImport + "\n\n" + socialImportMarker + "\n";
assert.equal(applyNotificationBootstrapPatch(existingSocialModule, socialImportPatch, "socialRoutes.js"), existingSocialModule, "bootstrap must preserve an existing notificationPush import instead of duplicating queueFynxNotification");
const notificationBootstrapSource = await readFile(new URL("./notificationBootstrap.js", import.meta.url), "utf8");
assert.ok(notificationBootstrapSource.includes("already: 'import { queueFynxNotification, notifyCommentMentions } from"), "social-route bootstrap must declare the existing import marker");

console.log("Notification bootstrap idempotency, reaction route, and existing-import contracts: PASS");
