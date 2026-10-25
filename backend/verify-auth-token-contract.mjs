import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

const root = new URL("../", import.meta.url);
const read = async (path) => readFile(new URL(path, root), "utf8");
const backend = await read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt");
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
assert.match(backend, /if \(requiresAuthentication\) accessToken\(context\)\?\.let \{ setRequestProperty\("Authorization", "Bearer \$it"\) \}/,
  "protected API requests must attach the stored bearer token");
assert.match(backend, /if \(requiresAuthentication && !accessToken\(context\)\.isNullOrBlank\(\)\) FynxAuthStore\.clear\(context\)/,
  "an unauthorized request clears only a server token that was actually present");
assert.match(backend, /isPublicAuthPath\(path\): Boolean = path ===? "\/health" \|\| path ===? "\/ready"/,
  "health and readiness probes must remain public so they measure backend reachability, not login state");
assert.ok(authUi.indexOf("FynxAuthStore.saveAccount(context, account.displayName") < authUi.indexOf("FynxBackendClient.saveAccessToken(context, account.token)"),
  "account identity must be saved before its token so account-switch cleanup cannot erase the new token");
assert.match(server, /jwt\.sign\(\{ sub: String\(user\.id\), username: user\.username \}, JWT_SECRET, \{ expiresIn: "30d" \}\)/,
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
