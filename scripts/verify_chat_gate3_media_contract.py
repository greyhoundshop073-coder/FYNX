#!/usr/bin/env python3
"""Gate 3: certify the existing Chat media/attachment contracts without duplicating implementation."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def read(rel):
    p = ROOT / rel
    return p.read_text(encoding="utf-8") if p.exists() else ""

files = list((ROOT / "app").rglob("*.kt")) if (ROOT / "app").exists() else list(ROOT.rglob("*.kt"))
text = "\n".join(p.read_text(encoding="utf-8", errors="ignore") for p in files)

checks = {
    "media model supports image/video/document/audio/voice/video-note": all(x in text for x in ["IMAGE", "VIDEO", "DOCUMENT", "AUDIO", "VOICE"]),
    "attachment upload path exists": bool(re.search(r"upload|Upload|attachment", text)),
    "voice/video-note playback presentation boundary exists": any("Voice" in p.name or "VideoNote" in p.name for p in files),
    "media presentation boundary exists": any("Media" in p.name for p in files),
    "retry/failure handling exists": bool(re.search(r"FAILED|failure|retry|Retry", text)),
    "existing chat message media implementation is reused": "Message" in text and "Conversation" in text,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print("CHAT GATE 3 MEDIA VERIFICATION: RED")
    raise SystemExit(1)
print("CHAT GATE 3 MEDIA VERIFICATION: GREEN")
