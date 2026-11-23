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


def grant_camera_permission_if_requested(stage: str, xml_text: str) -> str:
    """Follow the app's contextual camera permission explanation in the UI test."""
    point = find_click_target(xml_text, "Allow camera")
    if point is None:
        return xml_text

    adb("shell", "input", "tap", str(point[0]), str(point[1]))
    permission_options = (
        "While using the app",
        "Allow while using the app",
        "Only this time",
        "Allow",
    )
    deadline = time.monotonic() + 10.0
    latest = xml_text
    while time.monotonic() < deadline:
        time.sleep(0.35)
        latest = dump_ui(f"status-{stage}-permission.xml") or latest
        if find_click_target(latest, "Take photo") and (find_click_target(latest, "Switch to back camera") or find_click_target(latest, "Switch to front camera")):
            return latest
        selected = next((find_click_target(latest, option) for option in permission_options if find_click_target(latest, option)), None)
        if selected is not None:
            adb("shell", "input", "tap", str(selected[0]), str(selected[1]))
            time.sleep(0.7)
            latest = dump_ui(f"status-{stage}-permission-granted.xml") or latest
            return latest

    FAILURES.append(
        f"{stage}: camera permission explanation was shown, but Android's camera permission grant could not be completed."
    )
    screenshot(f"status-{stage}-permission-blocked.png")
    return latest


def exercise_camera_lens_switch(stage: str, shutter_label: str) -> bool:
    """Verify both emulated lenses bind and the shutter becomes ready after each switch."""
    latest = dump_ui(f"status-{stage}-lens-before.xml")
    transitions = (
        ("Switch to back camera", "Switch to front camera"),
        ("Switch to front camera", "Switch to back camera"),
    )
    for index, (current_label, next_label) in enumerate(transitions, start=1):
        point = find_click_target(latest, current_label)
        if point is None:
            FAILURES.append(f"{stage}: camera lens switch control '{current_label}' was not available.")
            screenshot(f"status-{stage}-lens-{index}-control-missing.png")
            return False
        adb("shell", "input", "tap", str(point[0]), str(point[1]))
        deadline = time.monotonic() + 12.0
        switched = False
        while time.monotonic() < deadline:
            time.sleep(0.45)
            latest = dump_ui(f"status-{stage}-lens-{index}-wait.xml") or latest
            if find_click_target(latest, shutter_label) and find_click_target(latest, next_label):
                screenshot(f"authenticated-status-{stage}-lens-{index}.png")
                switched = True
                break
        if not switched:
            FAILURES.append(
                f"{stage}: camera did not become ready on the expected lens after switching to '{next_label}'."
            )
            screenshot(f"status-{stage}-lens-{index}-failed.png")
            return False
    return True


def tap(label: str, stage: str, expected: tuple[str, ...], timeout: float = 12.0) -> str:
    latest = dump_ui(f"status-{stage}-before.xml")
    point = find_click_target(latest, label)
    if point is None:
        FAILURES.append(f"{stage}: visible clickable control '{label}' was not found")
        screenshot(f"status-{stage}-control-missing.png")
        return ""
    adb("shell", "input", "tap", str(point[0]), str(point[1]))
    deadline = time.monotonic() + timeout
    permission_checked = False
    while time.monotonic() < deadline:
        time.sleep(0.45)
        latest = dump_ui(f"status-{stage}-wait.xml") or latest
        if stage in ("photo-camera-entry", "video-camera-entry") and not permission_checked:
            permission_prompt_visible = find_click_target(latest, "Allow camera") is not None
            camera_controls_visible = all(find_click_target(latest, marker) is not None for marker in expected)
            if permission_prompt_visible:
                latest = grant_camera_permission_if_requested(stage, latest)
                permission_checked = True
            elif camera_controls_visible:
                permission_checked = True
        if all(find_click_target(latest, marker) is not None for marker in expected):
            screenshot(f"authenticated-status-{stage}.png")
            (ROOT / f"authenticated-status-{stage}.xml").write_text(latest + "\n", encoding="utf-8")
            return latest
    FAILURES.append(f"{stage}: destination did not expose all required controls: {', '.join(expected)}")
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
    adb("shell", "input", "keyevent", "4")
    deadline = time.monotonic() + 12.0
    returned = ""
    while time.monotonic() < deadline:
        time.sleep(0.4)
        returned = dump_ui(f"status-{stage}-return-home.xml") or returned
        if is_home(returned):
            return returned
    FAILURES.append(f"{stage}: camera Back did not return cleanly to the Home surface.")
    screenshot(f"status-{stage}-return-home-failed.png")
    return returned


def main() -> int:
    report = [
        "# FYNX Status creator runtime check",
        "",
        "- Scope: Home own-Status + button, creator entry, Voice/Text choices, contextual camera permission, Photo/Video preview, front/rear lens switching, and safe dismissal.",
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
                ("Take photo", "Switch to back camera")
            )
            if photo_camera and find_click_target(photo_camera, "Take photo") and find_click_target(photo_camera, "Switch to back camera"):
                report.append("- PASS Status Take photo opens the shared live CameraX preview in Photo mode.")
                screenshot("authenticated-status-photo-camera-mode.png")
                if exercise_camera_lens_switch("photo", "Take photo"):
                    report.append("- PASS front/rear camera switching binds both lenses and restores the ready Photo shutter.")
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
                ("Record video", "Switch to back camera")
            ) if creator else ""
            if video_camera and find_click_target(video_camera, "Record video") and find_click_target(video_camera, "Switch to back camera"):
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
