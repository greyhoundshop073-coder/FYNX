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


def tap(label: str, stage: str, expected: tuple[str, ...], timeout: float = 12.0) -> str:
    latest = dump_ui(f"status-{stage}-before.xml")
    point = find_click_target(latest, label)
    if point is None:
        FAILURES.append(f"{stage}: visible clickable control '{label}' was not found")
        screenshot(f"status-{stage}-control-missing.png")
        return ""
    adb("shell", "input", "tap", str(point[0]), str(point[1]))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        time.sleep(0.45)
        latest = dump_ui(f"status-{stage}-wait.xml") or latest
        if all(find_click_target(latest, marker) is not None for marker in expected):
            screenshot(f"authenticated-status-{stage}.png")
            (ROOT / f"authenticated-status-{stage}.xml").write_text(latest + "\n", encoding="utf-8")
            return latest
    FAILURES.append(f"{stage}: destination did not expose all required controls: {', '.join(expected)}")
    screenshot(f"status-{stage}-timeout.png")
    return latest


def is_home(xml_text: str) -> bool:
    return (
        find_click_target(xml_text, "Create your status") is not None
        and find_click_target(xml_text, "Your feed") is not None
    )


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
        # My Shop is nested under Marketplace; return through its own control first.
        back = find_click_target(current, "Back to Marketplace")
        if back:
            adb("shell", "input", "tap", str(back[0]), str(back[1]))
            time.sleep(1.0)
            current = dump_ui("status-return-marketplace.xml") or current

        # Use the visible Home tab, never guessed screen coordinates or app restarts.
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

        # Dismiss without selecting a creation mode or publishing anything.
        adb("shell", "input", "keyevent", "4")
        deadline = time.monotonic() + 8.0
        returned = ""
        while time.monotonic() < deadline:
            time.sleep(0.4)
            returned = dump_ui("status-after-dismiss.xml") or returned
            if is_home(returned):
                break
        if is_home(returned):
            report.append("- PASS Android Back dismisses the creator and returns to Home without creating a Status.")
        else:
            FAILURES.append("Status creator did not return to Home cleanly after Android Back.")
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
