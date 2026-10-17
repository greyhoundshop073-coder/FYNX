from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt").read_text(encoding="utf-8")
media_cache = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt").read_text(encoding="utf-8")
production_media = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt").read_text(encoding="utf-8")
remote_media = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt").read_text(encoding="utf-8")
currency = (ROOT / "app/src/main/java/com/fynx/app/ui/CurrencyConverterPanel.kt").read_text(encoding="utf-8")
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
auth_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
auth_finish = auth_panel.split("fun finish(", 1)[-1].split("val background", 1)[0]
check("new account identity is saved before its access token", "FynxAuthStore.saveAccount(context" in auth_finish and "FynxBackendClient.saveAccessToken(context, account.token)" in auth_finish and auth_finish.index("FynxAuthStore.saveAccount(context") < auth_finish.index("FynxBackendClient.saveAccessToken(context, account.token)"))

checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

check("backend transport requires HTTPS", 'startsWith("https://")' in client)
# HttpURLConnection uses Android's default network, so availability must inspect that route.
# INTERNET identifies a candidate transport; the actual request proves reachability.
# Do not count secondary networks or require VALIDATED before making the request.
network_gate = client.split("private fun hasNetwork", 1)[-1].split("private fun isRetryableFailure", 1)[0]
check("backend transport uses the default INTERNET network gate", "hasNetwork(context)" in client and "activeNetwork" in network_gate and "NET_CAPABILITY_INTERNET" in network_gate and "allNetworks" not in network_gate and "NET_CAPABILITY_VALIDATED" not in network_gate)
check("idempotent backend requests have bounded retries", "MAX_IDEMPOTENT_RETRIES = 2" in client and "attempt >= MAX_IDEMPOTENT_RETRIES" in client)
check("non-idempotent POST/PATCH are not automatically retried", 'val retryable = method == "GET" || method == "DELETE"' in client)
retry_classifier = client.split("private fun isRetryableFailure", 1)[-1].split("private fun migrateLegacyAccessToken", 1)[0]
check("HTTP status is classified before generic IOException", "if (current is FynxHttpException)" in retry_classifier and retry_classifier.index("if (current is FynxHttpException)") < retry_classifier.index("if (current is IOException)"))
check("only transient HTTP statuses are retried", "setOf(408, 425, 429, 500, 502, 503, 504)" in retry_classifier and "return current.status in setOf" in retry_classifier)
check("protected API requests are gated before transport without a token", "if (!canRequestWithoutAuthentication(path, accessToken(context))) throw FynxUnauthorizedException()" in client and "internal fun canRequestWithoutAuthentication" in client)
check("media downloads require an access token before transport", "if (accessToken(context).isNullOrBlank()) throw FynxUnauthorizedException()" in client)

check("backend responses have a size limit", "MAX_RESPONSE_BYTES" in client and "total > MAX_RESPONSE_BYTES" in client)
check("backend cancellation disconnects the request", "invokeOnCompletion" in client and "connection.disconnect()" in client)
check("HTTP 401 clears the authenticated session", "HTTP_UNAUTHORIZED" in client and "FynxAuthStore.clear(context)" in client)
check("media downloads use the central backend transport", "FynxBackendClient.downloadToFile" in media_cache and "FynxBackendClient.downloadToFile" in production_media and "FynxBackendClient.downloadToFile" in remote_media)
check("media downloads require HTTPS and same trusted host", 'target.protocol.equals("https", true)' in client and 'target.host.equals(configured.host, true)' in client)
check("media downloads have bounded bytes and partial-file protection", "maxBytes: Long" in client and ".part" in client and "temporary.renameTo(destination)" in client)
check("media downloads cancel the underlying connection", "invokeOnCompletion" in client and "connection.disconnect()" in client)
check("currency conversion uses the shared FYNX network gate", "FynxBackendClient.get(context, path)" in currency and "awaitValidatedNetwork(context)" not in currency and "NET_CAPABILITY_VALIDATED" not in currency)
check("currency converter rate function declaration is syntactically intact", "Result<Map<String, Double>> = withContext(Dispatchers.IO)" in currency and "Doubprivate suspend fun fetchOpenRates" not in currency)
check("CI contains the complete R2 sequence", all(x in workflow for x in ["verify_r2c_realtime.py", "verify_r2d_recovery.py", "verify_r2e_final.py"]))
check("R2-E runs before production certification", workflow.index("verify_r2e_final.py") < workflow.index("verify_fynx_production.py"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    print(f"R2-E final network gate: FAIL ({len(failed)} checks)")
    raise SystemExit(1)
print(f"R2-E final network gate: PASS ({len(checks)} checks)")
