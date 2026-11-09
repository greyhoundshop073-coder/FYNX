#!/usr/bin/env python3
"""Record a real FYNX Android emulator session while the existing UI journey runs.

The existing runtime verifier is used as the interaction driver. It installs no
production data and uses only its explicit debug-local visual entry. Screen video
is captured directly by Android adb screenrecord, not assembled from screenshots.
"""
from __future__ import annotations
import os, signal, subprocess, sys, time
from pathlib import Path

OUT = Path("fynx-walkthrough")
OUT.mkdir(parents=True, exist_ok=True)
DEVICE_VIDEO = "/sdcard/FYNX-Real-App-Walkthrough.mp4"
LOCAL_VIDEO = OUT / "FYNX-Real-App-Walkthrough.mp4"

def call(*args: str, timeout: int = 30, check: bool = True):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {' '.join(args)}\n{result.stdout[-3000:]}")
    return result

# Confirm the emulator and installed package before recording.
call("adb", "wait-for-device", timeout=60)
package = call("adb", "shell", "pm", "path", "com.fynx.app", check=False)
if package.returncode or "package:" not in package.stdout:
    raise SystemExit("FYNX APK is not installed in the emulator.")

# Keep the demonstration isolated from a real backend/account. The verifier's
# debug-local route is explicitly read-only and never submits a post or payment.
env = os.environ.copy()
env["FYNX_MARKETPLACE_VISUALS_ONLY"] = "1"
env["FYNX_LOCAL_VISUALS_ONLY"] = "1"
env.pop("FYNX_E2E_USERNAME", None)
env.pop("FYNX_E2E_PASSWORD", None)

# Launch a direct Android screen recording. It captures real taps, scrolling,
# transitions and rendered app content for up to 170 seconds.
recorder = subprocess.Popen(
    ["adb", "shell", "screenrecord", "--bit-rate", "8000000",
     "--time-limit", "170", DEVICE_VIDEO],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
)
time.sleep(2)
if recorder.poll() is not None:
    detail = recorder.stdout.read() if recorder.stdout else ""
    raise RuntimeError("Android screenrecord did not start: " + detail[-2000:])

journey = None
try:
    journey = subprocess.run(
        [sys.executable, "scripts/verify_authenticated_runtime_navigation.py"],
        env=env, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        timeout=165, check=False
    )
    (OUT / "runtime-journey-log.txt").write_text(journey.stdout, encoding="utf-8")
finally:
    # Signal the recorder process on Android itself; interrupting the local adb
    # client alone does not reliably finalize the MP4 container on the device.
    call("adb", "shell", "sh", "-c", "kill -INT $(pidof screenrecord) 2>/dev/null || true", timeout=10, check=False)
    if recorder.poll() is None:
        try:
            recorder.wait(timeout=12)
        except subprocess.TimeoutExpired:
            recorder.terminate()
            try:
                recorder.wait(timeout=5)
            except subprocess.TimeoutExpired:
                recorder.kill()
                recorder.wait(timeout=5)
    time.sleep(2)

# Pull the MP4 even if an individual visual assertion failed; the artifact will
# retain the test log so the video is not misrepresented as a full certification.
pull = call("adb", "pull", DEVICE_VIDEO, str(LOCAL_VIDEO), timeout=45, check=False)
if pull.returncode or not LOCAL_VIDEO.is_file() or LOCAL_VIDEO.stat().st_size < 500_000:
    raise SystemExit("No usable real Android MP4 was captured. " + pull.stdout[-2500:])

probe = subprocess.run(
    ["ffprobe", "-v", "error", "-show_entries", "format=duration,size",
     "-of", "default=noprint_wrappers=1", str(LOCAL_VIDEO)],
    text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False
)
(OUT / "recording-metadata.txt").write_text(probe.stdout, encoding="utf-8")
if probe.returncode:
    raise SystemExit("The captured Android recording is not a readable MP4: " + probe.stdout)

summary = [
    "# FYNX real Android walkthrough recording",
    "",
    "- Source: the actual FYNX debug APK built from the canonical integration branch.",
    "- Capture: Android emulator screenrecord (not a slideshow or screenshot animation).",
    "- Session: disposable emulator, debug-local visual entry; no live account authentication.",
    "- Actions: existing FYNX runtime navigation/visual journey; see runtime-journey-log.txt.",
    "- Safety: no production post, comment, order, wallet funding or transfer is submitted.",
    f"- Runtime visual journey exit code: {journey.returncode if journey else 'not available'}.",
    "- A successful MP4 capture does not imply every feature passed runtime verification.",
]
(OUT / "recording-report.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
print("\n".join(summary))
print(probe.stdout)
# Keep the video artifact even if the runtime verifier reported a feature failure.
