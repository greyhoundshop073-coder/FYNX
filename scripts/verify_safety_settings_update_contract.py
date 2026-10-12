#!/usr/bin/env python3
"""Guard the Android safety settings client against invalid PATCH keys."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSafetyRemoteClient.kt").read_text()
server = (ROOT / "backend/trustSafety.js").read_text()

expected = {"messageSafety", "marketplaceSafety", "loginAlerts"}
checks = [
    ("client allowlist contains all server-supported safety fields",
     all(f'"{key}"' in client.split("private val updateKeys", 1)[1].split(")", 1)[0] for key in expected)),
    ("client rejects unsupported update keys before network request",
     'require(key in updateKeys) { "Unknown safety setting" }' in client and
     client.index('require(key in updateKeys)') < client.index('FynxBackendClient.patchJson')),
    ("server accepts exactly one boolean safety field",
     'if(provided.length!==1)return res.status(400)' in server),
    ("client shares response parsing between load and update",
     'mapCatching { raw -> parseSafety(raw) }' in client),
]

failed = False
for name, passed in checks:
    print(f'{"PASS" if passed else "FAIL"}: {name}')
    failed |= not passed
raise SystemExit(1 if failed else 0)
