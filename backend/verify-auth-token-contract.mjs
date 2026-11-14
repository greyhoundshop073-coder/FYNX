import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const root = new URL("../", import.meta.url);
const read = async (path) => readFile(new URL(path, root), "utf8");
const backend = await read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt");
const productionMessaging = await read("app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt");
const groupRemoteClient = await read("app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt");
const conversationPanel = await read("app/src/main/java/com/fynx/app/ui/ConversationPanel.kt");
const offlineSync = await read("app/src/main/java/com/fynx/app/ui/FynxOfflineSync.kt");
const offlineOutbox = await read("app/src/main/java/com/fynx/app/ui/FynxOfflineOutbox.kt");
const r6gIntegration = await read("app/src/main/java/com/fynx/app/ui/FynxR6GIntegrationClient.kt");
const authUi = await read("app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt");
const remoteAuth = await read("app/src/main/java/com/fynx/app/ui/FynxRemoteAuthClient.kt");
const server = await read("backend/server.js");
const bootstrap = await read("backend/serverBootstrap.js");

assert.match(remoteAuth, /token\s*=\s*json\.getString\("accessToken"\)\.trim\(\)/,
  "login and registration must parse and normalize the issued access token");
assert.match(remoteAuth, /require\(token\.isNotBlank\(\)\)/,
  "authentication must fail closed when the server returns an empty access token");
assert.match(authUi, /FynxBackendClient\.saveAccessToken\(context, account\.token\)/,
  "successful authentication must persist the issued token");
assert.match(backend, /fun saveAccessToken\(context: Context, token: String\?\)/,
  "backend client must expose the secure token persistence boundary");
assert.match(backend, /FynxSecureTokenStore\.save\(context, token\)/,
  "access tokens must be saved through encrypted storage");
assert.match(backend, /requestToken\?\.let \{ setRequestProperty\("Authorization", "Bearer \$it"\) \}/,
  "protected API requests must attach the bearer token captured for the initiating session");
assert.match(backend, /val requestToken = if \(useRequestTokenOverride\) requestTokenOverride else if \(isPublicAuthPath\(path\)\) null else accessToken\(context\)/,
  "ordinary and streaming requests must capture authentication before dispatching to IO or use the explicitly supplied session");
assert.match(backend, /val requestToken = accessToken\(context\)/,
  "authenticated media downloads must capture authentication before dispatching to IO");
assert.match(backend, /internal suspend fun postJsonForSession\(context: Context, path: String, body: String, sessionToken: String\?\): Result<String>/,
  "a multi-step send journey must be able to keep one captured token across upload and message creation");
assert.match(backend, /internal fun isRequestSessionCurrent\(/,
  "the backend client must expose a shared account-and-token identity guard");
assert.match(productionMessaging, /FynxBackendClient\.isRequestSessionCurrent\(expectedAccountKey, FynxAuthStore\.accountStorageKey\(context\), requestToken, FynxBackendClient\.accessToken\(context\)\)/,
  "media preparation must abort rather than transfer an in-flight upload to another account");
assert.match(productionMessaging, /FynxBackendClient\.postJsonForSession\(context, "\/api\/messages", body\.toString\(\), requestToken\)/,
  "private text and structured message sends must use the initiating session token");
assert.match(conversationPanel, /FynxProductionMessaging\.uploadMedia\([\s\S]*?requestAccountKey,\s*requestToken\s*\)/,
  "private voice retry must upload under the account and token captured before IO");
assert.match(conversationPanel, /expectedAccountKey = requestAccountKey, requestToken = requestToken/,
  "private voice message creation must retain the upload's initiating session");
assert.match(offlineSync, /expectedAccountKey = accountKey,[\s\S]*?requestToken = requestToken/,
  "durable private message retries must retain the account and token captured at drain start");
assert.match(offlineOutbox, /fun updateMediaId\(context: Context, id: String, mediaId: String, expectedAccountKey: String\? = null\)/,
  "uploaded media IDs must update the originating account's outbox namespace even during account switches");
assert.match(offlineOutbox, /fun remove\(context: Context, id: String, expectedAccountKey: String\? = null\)/,
  "outbox removal must remain bound to the originating account namespace");
assert.match(r6gIntegration, /FynxBackendClient\.postJsonForSession\(context, "\/api\/r6g\/messages\/\$message\/context",[\s\S]*?requestToken\)/,
  "marketplace context attached to a queued chat message must keep its original session token");
assert.match(groupRemoteClient, /FynxBackendClient\.isRequestSessionCurrent\(expectedAccountKey, FynxAuthStore\.accountStorageKey\(context\), requestToken, FynxBackendClient\.accessToken\(context\)\)/,
  "group message creation and media upload must remain in the initiating account session");
assert.match(backend, /internal fun shouldClearRejectedAccessToken\(rejectedToken: String\?, currentToken: String\?\): Boolean =\s*!rejectedToken\.isNullOrBlank\(\) && rejectedToken == currentToken/,
  "a 401 may clear the session only when the rejected token still belongs to the current session");
assert.match(backend, /shouldClearRejectedAccessToken\(requestToken, accessToken\(context\)\)/,
  "ordinary requests and media downloads must protect a newer account from a stale 401");
assert.match(backend, /shouldClearRejectedAccessToken\(token, accessToken\(context\)\)/,
  "streaming requests must protect a newer account from a stale 401");
assert.match(backend, /executeRequest\(context, root, method, path, body, requestToken\)/,
  "ordinary requests and retries must reuse the initiating session token");
assert.match(backend, /executeStreamingRequest\(context, root, path, body, requestToken, onEvent\)/,
  "streaming requests must forward the initiating session token");
assert.match(backend, /downloadOnce\(context, target, temporary, maxBytes, requestToken\)/,
  "media downloads must forward the initiating session token");
assert.match(backend, /private fun isPublicAuthPath\(path: String\): Boolean =\s*path == "\/health" \|\|\s*path == "\/ready"/,
  "health and readiness probes must remain public so they measure backend reachability, not login state");
assert.ok(authUi.indexOf("FynxAuthStore.saveAccount(context, account.displayName") < authUi.indexOf("FynxBackendClient.saveAccessToken(context, account.token)"),
  "account identity must be saved before its token so account-switch cleanup cannot erase the new token");
assert.match(server, /sv: Number\(user\.session_version \|\| 0\)/,
  "issued JWTs must carry the current session version for password-reset revocation");
assert.match(server, /SELECT session_version FROM users WHERE id = \$1/,
  "protected HTTP and realtime sessions must check the current session version");
assert.match(server, /jwt\.sign\([\s\S]*?expiresIn: "30d"/,
  "login token issuance must use the configured JWT secret and bounded expiry");
assert.match(server, /jwt\.verify\(token, JWT_SECRET\)/,
  "protected requests must verify tokens with the configured JWT secret");
assert.match(server, /bearer token missing/,
  "missing-token failures must be diagnosable without logging credentials");
assert.match(server, /bearer token rejected/,
  "rejected-token failures must be diagnosable without logging credentials");
assert.match(bootstrap, /safe auth failure diagnostics/,
  "generated runtime must preserve safe auth diagnostics");
assert.doesNotMatch(server, /console\.(?:log|warn|error)\([^\n]*(?:token|authorization)[^\n]*\$\{?token/i,
  "backend must not print bearer token values into logs");

console.log("FYNX authentication token contract checks passed.");
