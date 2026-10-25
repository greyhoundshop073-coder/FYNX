import assert from "node:assert/strict";
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

console.log("Notification bootstrap idempotency: PASS (imports, route wiring, missing required marker)");
