#!/usr/bin/env python3
"""Read-only runtime check for Home -> Status creator entry and safe dismissal."""
from __future__ import annotations

import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.fynx.app"
ROOT = Path("fynx-runtime-screenshots")
ROOT.mkdir(parents=True, exist_ok=True)
FAILURES: list[str] = []


def adb(*args: str, timeout: int = 20) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            ["adb", *args],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout.decode("utf-8", "replace") if isinstance(exc.stdout, bytes) else (exc.stdout or "")
        return subprocess.CompletedProcess(["adb", *args], 124, output + "\nCOMMAND TIMEOUT")


def screenshot(name: str) -> None:
    with (ROOT / name).open("wb") as target:
        subprocess.run(
            ["adb", "exec-out", "screencap", "-p"],
            stdout=target,
            stderr=subprocess.STDOUT,
            check=False,
        )


def dump_ui(name: str) -> str:
    result = adb("shell", "uiautomator", "dump", "/sdcard/fynx-status-runtime.xml")
    if result.returncode != 0:
        return ""
    raw = subprocess.run(
        ["adb", "exec-out", "cat", "/sdcard/fynx-status-runtime.xml"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    ).stdout
    if not raw.startswith(b"<?xml"):
        return ""
    (ROOT / name).write_bytes(raw)
    return raw.decode("utf-8", "replace")


def bounds_center(value: str) -> tuple[int, int] | None:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value or "")
    if not match:
        return None
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        return None
    return (left + right) // 2, (top + bottom) // 2


def find_click_target(xml_text: str, label: str) -> tuple[int, int] | None:
    if not xml_text:
        return None
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return None

    target = label.strip().casefold()
    candidates: list[tuple[int, int, int]] = []

    def walk(node: ET.Element, ancestors: list[ET.Element]) -> None:
        path = ancestors + [node]
        visible = node.attrib.get("visible-to-user", "true").lower() != "false"
        values = (
            node.attrib.get("text", "").strip().casefold(),
            node.attrib.get("content-desc", "").strip().casefold(),
        )
        matched = visible and any(value == target for value in values if value)
        if matched:
            for distance, ancestor in enumerate(reversed(path)):
                if (
                    ancestor.attrib.get("clickable", "false").lower() == "true"
                    and ancestor.attrib.get("enabled", "true").lower() == "true"
                ):
                    center = bounds_center(ancestor.attrib.get("bounds", ""))
                    if center:
                        candidates.append((distance, center[1], center[0]))
                    break
        for child in list(node):
            walk(child, path)

    walk(root, [])
    if not candidates:
        return None
    _, y, x = min(candidates)
    return x, y


def tap(label: str, stage: str, expected: tuple[str, ...], timeout: float = 25.0) -> str:
    """Tap a Status action, honor the app rationale and Android permission prompt, then verify CameraX."""
    latest = dump_ui(f"status-{stage}-before.xml")
    point = find_click_target(latest, label)
    if point is None:
        FAILURES.append(f"{stage}: visible clickable control '{label}' was not found")
        screenshot(f"status-{stage}-control-missing.png")
        return ""

    adb("shell", "input", "tap", str(point[0]), str(point[1]))
    deadline = time.monotonic() + timeout
    permission_handled = False
    action_replayed = False

    while time.monotonic() < deadline:
        time.sleep(0.45)
        latest = dump_ui(f"status-{stage}-wait.xml") or latest

        # Status camera entry intentionally explains why camera access is needed
        # before opening Android's runtime permission dialog. Accept only the
        # explicit user-consent controls in this disposable CI emulator.
        rationale = find_click_target(latest, "Allow camera") if "camera" in stage.lower() else None
        system_permission = (
            find_click_target(latest, "While using the app")
            or find_click_target(latest, "While using app")
            or find_click_target(latest, "Only this time")
            or find_click_target(latest, "Allow")
        )
        permission = rationale or system_permission
        if permission:
            px, py = permission
            adb("shell", "input", "tap", str(px), str(py))
            permission_handled = True
            screenshot(f"status-{stage}-permission-consent.png")
            continue

        if all(find_click_target(latest, marker) is not None for marker in expected):
            screenshot(f"authenticated-status-{stage}.png")
            (ROOT / f"authenticated-status-{stage}.xml").write_text(latest + "\\n", encoding="utf-8")
            return latest

        # Some Android builds recreate the camera panel after the permission
        # callback. Replay the original entry once only if it is visibly present.
        if permission_handled and not action_replayed:
            retry = find_click_target(latest, label)
            if retry:
                adb("shell", "input", "tap", str(retry[0]), str(retry[1]))
                action_replayed = True

    FAILURES.append(f"{stage}: destination did not expose all required controls after permission handling: {', '.join(expected)}")
    screenshot(f"status-{stage}-timeout.png")
    return latest

def has_visible_label(xml_text: str, label: str) -> bool:
    if not xml_text:
        return False
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return False
    wanted = label.strip().casefold()
    return any(
        node.attrib.get("visible-to-user", "true").lower() != "false"
        and wanted in (
            node.attrib.get("text", "").strip().casefold(),
            node.attrib.get("content-desc", "").strip().casefold(),
        )
        for node in root.iter("node")
    )


def is_home(xml_text: str) -> bool:
    return (
        find_click_target(xml_text, "Create your status") is not None
        and has_visible_label(xml_text, "Your feed")
    )


def return_to_home(stage: str) -> str:
    """Dismiss CameraX and the enclosing Add status hub, then verify Home."""
    returned = ""
    for attempt in range(3):
        adb("shell", "input", "keyevent", "4")
        deadline = time.monotonic() + 6.0
        while time.monotonic() < deadline:
            time.sleep(0.4)
            returned = dump_ui(f"status-{stage}-return-home-{attempt + 1}.xml") or returned
            if is_home(returned):
                return returned

        # CameraX is hosted in a dialog inside Add status. Back first dismisses
        # CameraX; explicitly close the still-visible Add status hub next.
        close_creator = find_click_target(returned, "Close Add status")
        if close_creator:
            adb("shell", "input", "tap", str(close_creator[0]), str(close_creator[1]))
            time.sleep(0.6)
            returned = dump_ui(f"status-{stage}-close-creator.xml") or returned
            if is_home(returned):
                return returned

    FAILURES.append(f"{stage}: CameraX/Add status dismissal did not return cleanly to Home.")
    screenshot(f"status-{stage}-return-home-failed.png")
    return returned

def main() -> int:
    report = [
        "# FYNX Status creator runtime check",
        "",
        "- Scope: Home own-Status + button, creator entry, Voice/Text choices, and safe dismissal.",
        "- Session: uses the already-running CI UI session; debug-local access is not server authentication.",
        "- Safety: no status, text, photo, voice recording, or media is created or submitted.",
    ]

    pid = adb("shell", "pidof", PACKAGE)
    if pid.returncode != 0 or not (pid.stdout or "").strip():
        FAILURES.append("FYNX is not running in the existing emulator session; refusing to cold-start or reset the local UI session.")

    current = dump_ui("status-runtime-start.xml")
    if not FAILURES and not is_home(current):
        # Unwind real nested app surfaces first. A group conversation exposes
        # "Back"; the dedicated Groups manager exposes "Back to Chats"; My Shop
        # exposes "Back to Marketplace". Never restart or reset the app session.
        for back_label in ("Back", "Back to Chats", "Back to Marketplace"):
            if is_home(current):
                break
            back = find_click_target(current, back_label)
            if back:
                adb("shell", "input", "tap", str(back[0]), str(back[1]))
                time.sleep(0.8)
                stage_label = back_label.lower().replace(" ", "-")
                current = dump_ui(f"status-return-{stage_label}.xml") or current

        # Use the visible Home tab after nested screens have been closed.
        if not is_home(current):
            home = find_click_target(current, "Home")
            if home:
                adb("shell", "input", "tap", str(home[0]), str(home[1]))
                deadline = time.monotonic() + 18.0
                while time.monotonic() < deadline:
                    time.sleep(0.5)
                    current = dump_ui("status-return-home.xml") or current
                    if is_home(current):
                        break

    if not FAILURES and is_home(current):
        screenshot("authenticated-status-home.png")
        (ROOT / "authenticated-status-home.xml").write_text(current + "\n", encoding="utf-8")
        report.append("- PASS Home shows the user's own Status card and an accessible Create your status action.")
        adb("logcat", "-c")
        creator = tap("Create your status", "create", ("Voice", "Text"))
        if creator and find_click_target(creator, "Voice") and find_click_target(creator, "Text"):
            report.append("- PASS the real Add status surface opens and exposes both Voice and Text choices.")
        else:
            FAILURES.append("Home own-Status + button did not open the creator with both Voice and Text choices.")

        # The Status Photo and Record Video buttons must enter the one shared CameraX
        # surface in their requested modes. Inspect the real shutter accessibility label
        # and return without recording, capturing or publishing media.
        if not FAILURES:
            photo_camera = tap(
                "Take photo", "photo-camera-entry",
                ("Take photo", "Switch front/back camera")
            )
            if photo_camera and find_click_target(photo_camera, "Take photo") and find_click_target(photo_camera, "Switch front/back camera"):
                report.append("- PASS Status Take photo opens the shared live CameraX preview in Photo mode.")
                screenshot("authenticated-status-photo-camera-mode.png")
            else:
                FAILURES.append("Status Take photo did not open the shared CameraX preview in Photo mode.")
            if photo_camera:
                returned = return_to_home("photo-camera")
                if is_home(returned):
                    report.append("- PASS Back from the Status Photo camera returns to Home without publishing.")
                else:
                    FAILURES.append("Status Photo camera did not return to Home cleanly.")

        if not FAILURES:
            creator = tap("Create your status", "video-create", ("Voice", "Text"))
            video_camera = tap(
                "Record video", "video-camera-entry",
                ("Record video", "Switch front/back camera")
            ) if creator else ""
            if video_camera and find_click_target(video_camera, "Record video") and find_click_target(video_camera, "Switch front/back camera"):
                report.append("- PASS Status Record video opens the shared live CameraX preview in Video mode.")
                screenshot("authenticated-status-video-camera-mode.png")
            else:
                FAILURES.append("Status Record video did not open the shared CameraX preview in Video mode.")
            if video_camera:
                returned = return_to_home("video-camera")
                if is_home(returned):
                    report.append("- PASS Back from the Status Video camera returns to Home without starting a recording.")
                else:
                    FAILURES.append("Status Video camera did not return to Home cleanly.")

        logs = adb("logcat", "-d", "-v", "brief")
        if "FATAL EXCEPTION" in logs.stdout or f"ANR in {PACKAGE}" in logs.stdout:
            FAILURES.append("Status creator interaction produced a FYNX crash or ANR in logcat.")
        else:
            report.append("- PASS no FYNX crash/ANR marker was found during the Status creator interaction.")
        if not is_home(returned):
            screenshot("status-after-dismiss-failed.png")
    elif not FAILURES:
        FAILURES.append("The existing app session could not be returned to Home without restarting it.")

    report += ["", f"## Result: {'GREEN' if not FAILURES else 'RED'}"]
    if FAILURES:
        report += ["", "Failures:"] + ["- " + item for item in FAILURES]
    (ROOT / "FYNX-status-runtime.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))
    return 1 if FAILURES else 0


if __name__ == "__main__":
    raise SystemExit(main())
