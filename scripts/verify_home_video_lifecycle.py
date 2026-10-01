#!/usr/bin/env python3
"""Static guard for the Home video lifecycle contract.

This intentionally checks the existing implementation rather than replacing it:
- the feed selects a single focused video
- playback is explicitly gated by that selection
- the passive VideoView does not consume feed scroll gestures
- player instances are stopped/released when the composable leaves composition
- prepared/error/completion callbacks update player state
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt"
MEDIA = ROOT / "app/src/main/java/com/fynx/app/ui/FynxRemoteMedia.kt"

home = HOME.read_text(encoding="utf-8")
media = MEDIA.read_text(encoding="utf-8")

required_home = {
    "focused video selection": "focusedVideoPostId",
    "single-player playback gate": "playbackActive = post.id == focusedVideoPostId",
}
required_media = {
    "playbackActive parameter": "playbackActive: Boolean = true",
    "lifecycle cleanup": "videoView?.stopPlayback()",
    "passive scroll touch": "override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false",
    "prepared callback": "setOnPreparedListener",
    "completion callback": "setOnCompletionListener",
    "error callback": "setOnErrorListener",
    "explicit playback pause": "view.pause()",
}

missing = []
for label, needle in {**required_home, **required_media}.items():
    source = home if label in required_home else media
    if needle not in source:
        missing.append(f"{label}: {needle}")

if missing:
    raise SystemExit("H7/H8 video lifecycle guard failed:\n- " + "\n- ".join(missing))

print("H7/H8 video lifecycle guard: PASS")
