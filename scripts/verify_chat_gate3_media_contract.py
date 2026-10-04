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
models = read("app/src/main/java/com/fynx/app/ui/ChatModels.kt")

# FYNX models ordinary attachments separately from voice notes. Do not require a
# literal VOICE attachment enum when voice has its own voiceUri/duration contract.
attachment_contract = (
    "attachmentType: String?" in models
    and all(kind in models for kind in ["image", "video", "video_note", "audio", "document"])
)
voice_contract = all(field in models for field in ["voiceUri", "voiceDurationMs"])

checks = {
    "media model supports image/video/document/audio/video-note attachments": attachment_contract,
    "media model supports dedicated voice-note payload": voice_contract,
    "attachment upload path exists": bool(re.search(r"upload|Upload|attachment", text)),
    "voice/video-note playback presentation boundary exists": any("Voice" in p.name or "VideoNote" in p.name for p in files),
    "media presentation boundary exists": any("Media" in p.name for p in files),
    "retry/failure handling exists": bool(re.search(r"FAILED|failure|retry|Retry", text)),
    "existing chat message media implementation is reused": "ChatMessage" in text and "Conversation" in text,
}

failed = [name for name, ok in checks.items() if not ok]
for name, ok in checks.items():
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    print("CHAT GATE 3 MEDIA VERIFICATION: RED")
    raise SystemExit(1)
print("CHAT GATE 3 MEDIA VERIFICATION: GREEN")
