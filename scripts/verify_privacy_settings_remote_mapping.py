from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxPrivacySettings.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxPrivacyRemoteClient.kt").read_text(encoding="utf-8")

expected = {
    "KEY_PROFILE": "profile_visibility",
    "KEY_ONLINE": "online_visibility",
    "KEY_POSTS": "posts_visibility",
    "KEY_STATUS": "status_visibility",
    "KEY_PROFILE_PHOTO": "profile_photo_visibility",
    "KEY_MESSAGES": "messages_visibility",
}

for local_key, remote_key in expected.items():
    pattern = rf'{local_key}\s+to\s+"{re.escape(remote_key)}"'
    ok = re.search(pattern, panel) is not None
    print(f"{'PASS' if ok else 'FAIL'}: {local_key} maps to backend field {remote_key}")
    if not ok:
        raise SystemExit(1)

checks = [
    ("loading reads backend field names", all(f'remote["{key}"]' in panel for key in expected.values())),
    ("saving translates local key before remote update", "FynxPrivacyRemoteClient.update(context, remotePrivacyKey(key), option)" in panel),
    ("successful save reads translated backend field", "remote[remotePrivacyKey(key)]" in panel),
    ("remote client accepts only its backend field keys", "require(key in keys)" in client),
    ("remote client supports all six backend privacy fields", all(f'"{key}"' in client for key in expected.values())),
]
for label, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {label}")
    if not ok:
        raise SystemExit(1)

print(f"Privacy remote-key mapping checks passed ({len(expected) + len(checks)} checks).")
