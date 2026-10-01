from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    p = ROOT / path
    return p.read_text(encoding="utf-8") if p.is_file() else ""

def exists(path):
    return (ROOT / path).is_file()

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

server = read("backend/server.js")
client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")
token = read("app/src/main/java/com/fynx/app/ui/FynxSecureTokenStore.kt")
auth = read("app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt")
workflow = read(".github/workflows/android-build.yml")
gradle = read("app/build.gradle.kts")
manifest = read("app/src/main/AndroidManifest.xml")

required = [
    "backend/server.js",
    "backend/scalability.js",
    "app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt",
    "app/src/main/java/com/fynx/app/ui/FynxSecureTokenStore.kt",
    "app/src/main/java/com/fynx/app/ui/FynxAuthStore.kt",
    ".github/workflows/android-build.yml",
    "gradlew",
]
check("release hardening files exist", all(exists(p) for p in required))

check("backend secrets remain environment-backed",
      all(x in server for x in ["process.env.JWT_SECRET", "process.env.DATABASE_URL", "process.env.OPENAI_API_KEY"]))
check("JWT authentication fails closed",
      "jwt.verify(token, JWT_SECRET)" in server and 'if (!token || !JWT_SECRET) return res.status(401)' in server)
check("HTTP transport requires HTTPS",
      'startsWith("https://")' in client or 'startsWith("https://")' in client)
check("trusted backend media host is enforced",
      'target.host.equals(configured.host, true)' in client)
check("API responses are bounded",
      all(x in client for x in ["MAX_RESPONSE_BYTES", "total += count", "total > MAX_RESPONSE_BYTES"]))
check("media downloads are bounded",
      all(x in client for x in ["maxBytes", "FYNX media is too large"]))
check("request bodies are bounded",
      'express.json({ limit: "18mb"' in server)
check("endpoint rate limits remain active",
      all(x in server for x in ['rateLimit("auth"', 'rateLimit("assistant"', 'rateLimit("media"', 'rateLimit("messages"']))
check("database timeouts remain bounded",
      all(x in server for x in ["connectionTimeoutMillis", "statement_timeout", "query_timeout"]))
check("security headers remain enabled",
      all(x in server for x in ["X-Content-Type-Options", "Referrer-Policy", "X-Frame-Options", "Strict-Transport-Security"]))
check("secure token storage uses Android Keystore",
      all(x in token for x in ["AndroidKeyStore", "AES/GCM/NoPadding"]))
check("logout clears secure authentication state",
      "FynxSecureTokenStore.save(context, null)" in auth)
check("401 responses clear the authenticated session",
      "HTTP_UNAUTHORIZED" in client and "FynxAuthStore.clear(context)" in client)
check("CI runs security certification before Android build",
      "verify_phase_c_security_production.py" in workflow and "verify_r2e_final.py" in workflow)
check("CI runs final production certification before Android build",
      "verify_fynx_production.py" in workflow)
check("CI keeps the major badge gates before artifact publication",
      all(x in workflow for x in [
          "Large Badge #2",
          "Large Badge #6",
          "Large Badge #9",
          "verify_large_badge11_product_completeness.py"]))
check("authenticated visual certification remains mandatory",
      "verify_authenticated_runtime_navigation.py" in workflow and "FYNX_E2E_USERNAME" in workflow and "FYNX_E2E_PASSWORD" in workflow)
check("instrumentation remains mandatory before APK publication",
      "connectedDebugAndroidTest" in workflow and
      "github.event_name == 'workflow_dispatch' && inputs.full_runtime == 'true'" in workflow and
      "Upload exact-commit debug APK" in workflow and
      "if-no-files-found: error" in workflow)
check("no common API secrets are committed to Android source",
      not re.search(r"sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----",
                   "\n".join(p.read_text(encoding="utf-8") for p in (ROOT / "app/src/main/java").rglob("*.kt"))))
check("manifest does not contain embedded secret-looking values",
      not re.search(r"(api[_-]?key|secret|private[_-]?key)\\s*[=:]\\s*[A-Za-z0-9_\\-]{20,}", manifest, re.I))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)

if failed:
    raise SystemExit("Large Badge #13 regression/security hardening failed: " + "; ".join(failed))

print(f"Large Badge #13 regression/security hardening passed ({len(checks)} checks)")
