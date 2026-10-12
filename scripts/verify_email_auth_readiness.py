#!/usr/bin/env python3
"""Guard the email/auth launch contract against UI-only or dependency-only claims."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
server = (ROOT / "backend/server.js").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteAuthClient.kt").read_text(encoding="utf-8")
auth_ui = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAuthPanel.kt").read_text(encoding="utf-8")
package = json.loads((ROOT / "backend/package.json").read_text(encoding="utf-8"))

def check(label, condition):
    print(f"{'PASS' if condition else 'FAIL'}: {label}")
    if not condition:
        raise SystemExit(1)

# Existing working contract must remain intact.
check("server registration endpoint exists", 'app.post("/api/auth/register"' in server)
check("server login endpoint exists", 'app.post("/api/auth/login"' in server)
check("Android client calls server registration", '"/api/auth/register"' in client)
check("Android client calls server login", '"/api/auth/login"' in client)
check("passwords are hashed before storage", "bcrypt.hash(password, 12)" in server)
check("login compares supplied password to stored hash", "bcrypt.compare(password, user.password_hash)" in server)
check("auth routes have rate limiting", 'app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth))' in server)

# Explicitly detect missing delivery/recovery pieces. This is a truthful readiness
# gate, not a claim that email is implemented. It stays green while the UI makes
# no promise of a capability that the backend cannot fulfil.
auth_routes = re.findall(r'app\.(?:post|get)\(["\'](/api/auth/[^"\']+)', server)
email_dependencies = {
    name for name in package.get("dependencies", {})
    if any(word in name.lower() for word in ("resend", "sendgrid", "nodemailer", "mailgun", "postmark", "ses"))
}
has_email_identity = bool(re.search(r'\bemail\b', server, re.IGNORECASE))
has_email_route = any(any(word in route.lower() for word in ("email", "verify", "recover", "reset", "forgot")) for route in auth_routes)
has_honest_notice = "verification is not being faked" in auth_ui.lower()
check("no unimplemented email/recovery route is advertised by Android auth client",
      not any(any(word in path.lower() for word in ("email", "verify", "recover", "reset", "forgot"))
              for path in re.findall(r'"(/api/auth/[^" ]+)"', client)))
check("current UI clearly discloses that phone/SMS verification is not wired", has_honest_notice)
print("READINESS: email verification/recovery is NOT implemented.")
print(f"Observed auth routes: {', '.join(auth_routes) if auth_routes else '(none found)'}")
print(f"Email identity field detected in server: {'yes' if has_email_identity else 'no'}")
print(f"Email delivery dependency detected: {'yes' if email_dependencies else 'no'}")
print(f"Email/recovery endpoint detected: {'yes' if has_email_route else 'no'}")
print("Do not implement real verification/reset until an email identity contract, provider, token storage/expiry, and deployment configuration are agreed.")
