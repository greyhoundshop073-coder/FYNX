import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const support = await readFile(new URL("./supportRoutes.js", import.meta.url), "utf8");
const bootstrap = await readFile(new URL("./serverBootstrap.js", import.meta.url), "utf8");
const adminRoutes = await readFile(new URL("./adminRoutes.js", import.meta.url), "utf8");
const androidClient = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxSupportRemoteClient.kt", import.meta.url), "utf8");
const androidUi = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxSupportCenter.kt", import.meta.url), "utf8");
const androidApp = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxApp.kt", import.meta.url), "utf8");
const notificationModels = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/NotificationModels.kt", import.meta.url), "utf8");
const notificationPanel = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/NotificationPanel.kt", import.meta.url), "utf8");

for (const route of [
  'app.post("/api/support/cases"',
  'app.get("/api/support/cases/mine"',
  'app.get("/api/support/cases/:caseId/messages"',
  'app.post("/api/support/cases/:caseId/messages"',
  'app.post("/api/support/cases/:caseId/attachments"',
  'app.get("/api/support/cases/:caseId/attachments"',
  'app.get("/api/support/cases/:caseId/attachments/:attachmentId"',
  'app.get("/api/admin/support/cases"',
  'app.post("/api/admin/support/cases/:caseId/assign"',
  'app.get("/api/admin/support/cases/:caseId/messages"',
  'app.post("/api/admin/support/cases/:caseId/messages"',
  'app.patch("/api/admin/support/cases/:caseId/status"',
  'app.get("/api/admin/support/cases/:caseId/events"'
]) assert.ok(support.includes(route), `missing support route: ${route}`);

for (const contract of [
  "fynx_support_cases",
  "fynx_support_messages",
  "fynx_support_case_events",
  "fynx_support_attachments",
  "message_media",
  "client_request_id",
  "client_message_id",
  "visibility IN ('PUBLIC','INTERNAL')",
  "requester_id=$1",
  "role === \"OWNER\"",
  "assigned_admin_id IS NULL",
  "Only the Owner can reopen a resolved case",
  "queueFynxNotification"
]) assert.ok(support.includes(contract), `support security or persistence contract missing: ${contract}`);

assert.ok(bootstrap.includes('import { registerSupportRoutes } from "./supportRoutes.js";'),
  "support routes must be imported by the production runtime bootstrap");
assert.ok(bootstrap.includes("registerSupportRoutes({ app, pool, auth })"),
  "support routes must be mounted in the production runtime");
assert.ok(bootstrap.includes('import { registerAdminRoutes } from "./adminRoutes.js";'),
  "existing Owner/Admin routes must be imported by the production runtime");
assert.ok(bootstrap.includes("registerAdminRoutes({ app })"),
  "existing Owner/Admin routes must be mounted in the production runtime");
assert.ok(adminRoutes.includes("fynx_admin_roles"),
  "support Admin authorization must use the existing Admin role table");
for (const contract of [
  "/api/support/cases",
  "/api/support/cases/mine",
  "/api/support/cases/",
  "/api/admin/support/cases",
  "attachMedia",
  "attachmentUrl"
]) assert.ok(androidClient.includes(contract), `Android support client missing contract: ${contract}`);
assert.ok(androidUi.includes("FynxUserSupportPanel") && androidUi.includes("FynxAdminSupportPanel"),
  "Android must provide user and shared Admin support screens");
assert.ok(androidUi.includes("Attach screenshot") && androidUi.includes("SupportAttachmentViewer"),
  "Android must support screenshot attachment and private viewing");
assert.ok(androidApp.includes('"Support" -> FynxUserSupportPanel') && androidApp.includes("onOpenSupport"),
  "Profile Help & Support must route into the case-based support screen");
assert.ok(notificationModels.includes("SUPPORT") && notificationPanel.includes("FynxNotificationType.SUPPORT"),
  "support notifications must be recognised and rendered by Android");

console.log("FYNX support case routes, permissions, idempotency and runtime registration checks passed.");
