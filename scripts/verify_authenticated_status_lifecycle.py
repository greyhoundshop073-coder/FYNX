#!/usr/bin/env python3
"""Server-authenticated Status lifecycle certification using a non-debuggable test build.

Uses the existing FYNX sign-in UI and real CI credentials. Any created Status is
identified by this run, verified through the server, opened in the real viewer,
and deleted before this script exits. No user credentials or access tokens are logged.
"""
from __future__ import annotations
import json
import os
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path("fynx-runtime-screenshots")
ROOT.mkdir(parents=True, exist_ok=True)
PACKAGE = "com.fynx.app"
APK = Path("app/build/outputs/apk/runtimeCert/app-runtimeCert.apk")
USERNAME = os.environ.get("FYNX_E2E_USERNAME", "").strip().removeprefix("@")
PASSWORD = os.environ.get("FYNX_E2E_PASSWORD", "")
BASE = os.environ.get("FYNX_PRODUCTION_BASE_URL", "https://fynx-backend-testing-testing.up.railway.app").rstrip("/")
RUN_ID = os.environ.get("GITHUB_RUN_ID", str(int(time.time())))
TEXT_MARKER = "FYNXStatusCheck" + re.sub(r"[^A-Za-z0-9]", "", RUN_ID)[-16:]
FAILURES: list[str] = []
NOTES: list[str] = []
CREATED_IDS: set[str] = set()
BASELINE_STATUS_IDS: set[str] = set()
RUN_START_MS = int(time.time() * 1000)
TOKEN = ""
CURRENT_USERNAME = USERNAME.lower()


def run(*args: str, timeout: int = 30) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        out = exc.stdout.decode("utf-8", "replace") if isinstance(exc.stdout, bytes) else (exc.stdout or "")
        return subprocess.CompletedProcess(args, 124, out + "\nCOMMAND TIMEOUT")


def api_request(
    method: str,
    path: str,
    payload: dict | None = None,
    *,
    timeout: float = 25.0,
    retries: int = 3,
    retry_delay: float = 10.0,
):
    """Use bounded retries for safe requests; match the repository's Render cold-start policy."""
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    # Retrying the login endpoint is safe; never replay an arbitrary content-creation POST.
    retry_safe = method.upper() in {"GET", "DELETE"} or (
        method.upper() == "POST" and path == "/api/auth/login"
    )
    attempts = max(1, retries if retry_safe else 1)
    retryable_http = {408, 425, 429, 500, 502, 503, 504}
    last_error = None
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                raw = response.read().decode("utf-8", "replace")
                return json.loads(raw) if raw.strip() else {}
        except urllib.error.HTTPError as exc:
            if exc.code not in retryable_http or attempt >= attempts:
                raise
            last_error = exc
        except (urllib.error.URLError, TimeoutError) as exc:
            if attempt >= attempts:
                raise
            last_error = exc
        if attempt < attempts:
            time.sleep(retry_delay)
    raise last_error or RuntimeError(f"{method} {path} request failed")


def api_login() -> str:
    global TOKEN
    data = api_request("POST", "/api/auth/login", {"username": USERNAME, "password": PASSWORD})
    TOKEN = str(data.get("accessToken") or "").strip()
    return TOKEN


def api_statuses() -> list[dict]:
    data = api_request("GET", "/api/statuses")
    return [item for item in data.get("statuses", []) if isinstance(item, dict)]


def find_created_status(predicate, timeout: float = 25.0) -> dict | None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            for item in api_statuses():
                if predicate(item):
                    return item
        except Exception:
            pass
        time.sleep(1.0)
    return None


def cleanup_status(status_id: str) -> bool:
    if not status_id:
        return True
    try:
        api_request("DELETE", "/api/statuses/" + urllib.parse.quote(status_id, safe=""))
    except urllib.error.HTTPError as exc:
        if exc.code not in (404, 410):
            return False
    except Exception:
        return False
    try:
        time.sleep(0.5)
        remaining = [s for s in api_statuses() if str(s.get("id") or "") == status_id]
        return not remaining
    except Exception:
        return False


def screenshot(name: str) -> None:
    with (ROOT / name).open("wb") as out:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=out, stderr=subprocess.STDOUT, check=False)


def dump_ui(name: str = "status-cert-current.xml") -> str:
    result = run("adb", "shell", "uiautomator", "dump", "/sdcard/fynx-status-cert.xml")
    if result.returncode != 0:
        return ""
    raw = subprocess.run(["adb", "exec-out", "cat", "/sdcard/fynx-status-cert.xml"],
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False).stdout
    if not raw.startswith(b"<?xml"):
        return ""
    (ROOT / name).write_bytes(raw)
    return raw.decode("utf-8", "replace")

def read_ui_unpersisted() -> str:
    """Read the live hierarchy for sign-in only; never save username/password UI to artifacts."""
    result = run("adb", "shell", "uiautomator", "dump", "/sdcard/fynx-status-cert-login.xml")
    if result.returncode != 0:
        return ""
    raw = subprocess.run(["adb", "exec-out", "cat", "/sdcard/fynx-status-cert-login.xml"],
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False).stdout
    return raw.decode("utf-8", "replace") if raw.startswith(b"<?xml") else ""


def nodes(xml_text: str):
    if not xml_text:
        return []
    try:
        return list(ET.fromstring(xml_text).iter("node"))
    except ET.ParseError:
        return []


def center(node):
    try:
        values = [int(v) for v in re.findall(r"-?\d+", node.attrib.get("bounds", ""))]
        if len(values) != 4:
            return None
        return ((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)
    except Exception:
        return None


def find_control(xml_text: str, labels: list[str]):
    if not xml_text:
        return None
    wanted = [label.casefold() for label in labels]
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError:
        return None
    candidates = []
    path = []

    def walk(node):
        path.append(node)
        value = " | ".join((node.attrib.get("text") or "", node.attrib.get("content-desc") or "",
                            node.attrib.get("resource-id") or "")).casefold()
        if any(label in value for label in wanted) and center(node):
            target = node
            for ancestor in reversed(path):
                if ancestor.attrib.get("clickable", "false").lower() == "true" and center(ancestor):
                    target = ancestor
                    break
            candidates.append(target)
        for child in list(node):
            walk(child)
        path.pop()

    walk(root)
    if not candidates:
        return None
    # Use the first matching visible control in document order (Home Status row precedes feed rows).
    node = candidates[0]
    x, y = center(node)
    return ((node.attrib.get("text") or node.attrib.get("content-desc") or "").strip(), x, y)


def find_edit_fields(xml_text: str):
    fields = []
    for node in nodes(xml_text):
        if node.attrib.get("class") != "android.widget.EditText":
            continue
        if node.attrib.get("visible-to-user", "true").lower() == "false":
            continue
        point = center(node)
        if point:
            fields.append(point)
    return fields


def input_text(value: str):
    escaped = value.replace("%", "%25").replace(" ", "%s")
    return run("adb", "shell", "input", "text", escaped)


def has_any(xml_text: str, labels: list[str]) -> bool:
    return any(find_control(xml_text, [label]) for label in labels)


def marker_summary(xml_text: str) -> str:
    safe = ("Welcome back", "Username", "Password", "Sign In", "Open FYNX camera",
            "Create your status", "Your status", "Add status", "Type a Status",
            "Send", "Status menu", "Like Status", "React", "Recent updates",
            "No recent updates", "FYNX Music", "Search music", "No FYNX music is published yet.",
            "Status viewers", "Music Status", "Status publishing failed")
    text = " ".join(((n.attrib.get("text") or "") + " " + (n.attrib.get("content-desc") or ""))
                    for n in nodes(xml_text)).casefold()
    return ", ".join(x for x in safe if x.casefold() in text) or "no known Status/authentication markers"


def tap_control(xml_text: str, labels: list[str], name: str,
                expected: list[str] | None = None, timeout: float = 16.0) -> str:
    control = find_control(xml_text, labels)
    deadline = time.monotonic() + min(timeout, 8.0)
    while control is None and time.monotonic() < deadline:
        time.sleep(0.5)
        xml_text = dump_ui(name + "-find.xml")
        control = find_control(xml_text, labels)
    if control is None:
        FAILURES.append(f"{name}: control not found [{', '.join(labels)}]; visible markers: {marker_summary(xml_text)}")
        screenshot(name + "-missing.png")
        return ""
    _, x, y = control
    run("adb", "shell", "input", "tap", str(x), str(y))
    deadline = time.monotonic() + timeout
    latest = ""
    while time.monotonic() < deadline:
        time.sleep(0.5)
        latest = dump_ui(name + ".xml")
        if expected and any(find_control(latest, [wanted]) for wanted in expected):
            screenshot(name + ".png")
            return latest
    screenshot(name + ".png")
    if expected:
        FAILURES.append(f"{name}: destination marker not found [{', '.join(expected)}]; visible markers: {marker_summary(latest)}")
        return ""
    return latest


def wait_for_home(timeout: float = 75.0) -> str:
    deadline = time.monotonic() + timeout
    latest = ""
    while time.monotonic() < deadline:
        latest = dump_ui("server-status-home.xml")
        if (find_control(latest, ["Open FYNX camera"])
                and find_control(latest, ["Chat"])
                and find_control(latest, ["Friends"])):
            screenshot("authenticated-server-status-home.png")
            return latest
        time.sleep(1)
    FAILURES.append("runtimeCert sign-in did not reach authenticated Home; visible markers: " + marker_summary(latest))
    screenshot("authenticated-server-status-home-timeout.png")
    return latest


def sign_in() -> bool:
    if not USERNAME or not PASSWORD:
        NOTES.append("BLOCKED: repository FYNX_E2E_USERNAME/FYNX_E2E_PASSWORD secrets are not configured.")
        return False
    if not APK.is_file() or APK.stat().st_size == 0:
        FAILURES.append("runtimeCert APK is missing or empty.")
        return False
    install = run("adb", "install", "-r", str(APK), timeout=90)
    if install.returncode != 0:
        FAILURES.append("runtimeCert APK installation failed: " + (install.stdout or "")[-700:])
        return False
    run("adb", "shell", "am", "force-stop", PACKAGE)
    run("adb", "shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", "fynx://home", PACKAGE, timeout=30)
    time.sleep(2.0)
    xml = dump_ui("authenticated-server-status-before-login.xml")
    # Existing debug-runtime test may have persisted a local username. Do not change
    # any auth behavior; use the normal Password form exposed by the non-debuggable APK.
    if not (find_control(xml, ["Username"]) and find_control(xml, ["Password"])):
        gate = find_control(xml, ["Sign In"])
        if gate:
            xml = tap_control(xml, ["Sign In"], "authenticated-server-status-open-login",
                              ["Username", "Welcome back"], timeout=12.0)
        else:
            xml = dump_ui("authenticated-server-status-login-retry.xml")
    if not xml or not find_control(xml, ["Username"]):
        FAILURES.append("normal server sign-in form did not expose Username; markers: " + marker_summary(xml))
        return False
    username_control = find_control(xml, ["Username"])
    if username_control:
        _, x, y = username_control
        run("adb", "shell", "input", "tap", str(x), str(y))
        time.sleep(0.2)
        if input_text(USERNAME).returncode != 0:
            FAILURES.append("Android input could not enter the configured test username.")
            return False
        run("adb", "shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(0.4)
    # Read live login controls in memory only. The hierarchy can contain the test
    # username, and after password entry it may contain sensitive accessibility data.
    xml = read_ui_unpersisted()
    password_control = find_control(xml, ["Password"])
    sign_in_control = find_control(xml, ["Sign In"])
    if not password_control or not sign_in_control:
        FAILURES.append("Password field or Sign In button was unavailable in the normal server sign-in form.")
        return False
    _, password_x, password_y = password_control
    _, sign_in_x, sign_in_y = sign_in_control
    run("adb", "shell", "input", "tap", str(password_x), str(password_y))
    time.sleep(0.2)
    if input_text(PASSWORD).returncode != 0:
        FAILURES.append("Android input could not enter the configured test password.")
        return False
    run("adb", "shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.5)
    # Do not dump/save the login hierarchy while the password field is populated.
    run("adb", "shell", "input", "tap", str(sign_in_x), str(sign_in_y))
    deadline = time.monotonic() + 75.0
    latest = ""
    while time.monotonic() < deadline:
        time.sleep(1.0)
        latest = read_ui_unpersisted()
        if (find_control(latest, ["Open FYNX camera"])
                and find_control(latest, ["Chat"])
                and find_control(latest, ["Friends"])):
            (ROOT / "authenticated-server-status-login-success.xml").write_text(latest, encoding="utf-8")
            screenshot("authenticated-server-status-login-success.png")
            return True
        combined = " ".join((n.attrib.get("text") or "") + " " + (n.attrib.get("content-desc") or "")
                            for n in nodes(latest)).casefold()
        if "invalid password" in combined or "invalid credentials" in combined or "incorrect password" in combined:
            break
    FAILURES.append("the real FYNX sign-in UI did not reach Home; visible markers: " + marker_summary(latest))
    # Do not save a screenshot or accessibility dump of a failed login screen.
    return False


def launch_status(status_id: str, name: str) -> str:
    uri = "fynx://status/" + urllib.parse.quote(status_id, safe="")
    result = run("adb", "shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity",
                 "-a", "android.intent.action.VIEW", "-d", uri, timeout=30)
    if result.returncode != 0:
        FAILURES.append(name + ": Android could not route the real Status deep link.")
        return ""
    deadline = time.monotonic() + 25.0
    latest = ""
    while time.monotonic() < deadline:
        time.sleep(0.7)
        latest = dump_ui(name + ".xml")
        if find_control(latest, ["Status menu"]) and find_control(latest, ["Like Status"]) and find_control(latest, ["React"]):
            screenshot(name + ".png")
            return latest
    FAILURES.append(name + ": real Status viewer did not expose menu, Like, and React controls; markers: " + marker_summary(latest))
    screenshot(name + "-failed.png")
    return ""


def delete_through_viewer(status_id: str, name: str) -> bool:
    xml = launch_status(status_id, name + "-cleanup-viewer")
    if not xml:
        return False
    menu_xml = tap_control(xml, ["Status menu"], name + "-delete-menu", ["Delete"], timeout=12.0)
    if not menu_xml:
        return False
    after = tap_control(menu_xml, ["Delete"], name + "-delete-status", ["My status", "Recent updates", "Tap to add status update"], timeout=18.0)
    if not after:
        return False
    deadline = time.monotonic() + 15.0
    while time.monotonic() < deadline:
        try:
            if all(str(s.get("id") or "") != status_id for s in api_statuses()):
                CREATED_IDS.discard(status_id)
                return True
        except Exception:
            pass
        time.sleep(1.0)
    return False


def open_creator() -> str:
    run("adb", "shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", "fynx://home", PACKAGE, timeout=25)
    time.sleep(1.2)
    xml = dump_ui("authenticated-server-status-home-before-create.xml")
    control = find_control(xml, ["Create your status"])
    if not control:
        FAILURES.append("Home Status row did not expose its Create your status (+) action; markers: " + marker_summary(xml))
        screenshot("authenticated-server-status-create-control-missing.png")
        return ""
    return tap_control(xml, ["Create your status"], "authenticated-server-status-add", ["Add status", "Text", "Voice"], timeout=15.0)


def publish_text_status() -> dict | None:
    xml = open_creator()
    if not xml:
        return None
    xml = tap_control(xml, ["Text"], "authenticated-server-status-text-open", ["Type a Status", "Text styling", "Send"], timeout=16.0)
    if not xml:
        return None
    fields = find_edit_fields(xml)
    if not fields:
        FAILURES.append("real Status text composer did not expose an editable field.")
        return None
    x, y = fields[0]
    run("adb", "shell", "input", "tap", str(x), str(y))
    time.sleep(0.2)
    if input_text(TEXT_MARKER).returncode != 0:
        FAILURES.append("Android input could not type the Status lifecycle marker.")
        return None
    time.sleep(0.4)
    run("adb", "shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(0.6)
    xml = dump_ui("authenticated-server-status-text-filled.xml")
    if TEXT_MARKER not in xml or not find_control(xml, ["Send"]):
        FAILURES.append("Status text draft did not retain its marker and Send after the keyboard was dismissed.")
        screenshot("authenticated-server-status-text-filled-failed.png")
        return None
    screenshot("authenticated-server-status-text-composer.png")
    xml = tap_control(xml, ["Send"], "authenticated-server-status-text-publish",
                      ["Open FYNX camera", "Your status", "Create your status"], timeout=25.0)
    # Query the server even if the expected Home marker is late: if Send actually
    # succeeded, register the exact id immediately so finally-cleanup can remove it.
    status = find_created_status(lambda item: str(item.get("text") or "").strip() == TEXT_MARKER
                                 and str(item.get("ownerUsername") or "").removeprefix("@").lower() == CURRENT_USERNAME)
    if not status:
        FAILURES.append("server GET /api/statuses did not return the just-published Text Status with its run marker.")
        return None
    status_id = str(status.get("id") or "")
    if not status_id:
        FAILURES.append("the server returned the test Status without an id.")
        return None
    CREATED_IDS.add(status_id)
    if not xml:
        FAILURES.append("Status Send did not return to Home after the server accepted the Text Status.")
    NOTES.append("PASS real Text Status published through the app and retrieved by the authenticated server list.")
    return status


def publish_music_status() -> dict | None:
    start_ms = int(time.time() * 1000) - 10_000
    try:
        baseline_ids = {str(item.get("id") or "") for item in api_statuses()}
    except Exception:
        baseline_ids = set()
    xml = open_creator()
    if not xml:
        return None
    xml = tap_control(xml, ["Music"], "authenticated-server-status-music-open",
                      ["FYNX Music", "Search music"], timeout=18.0)
    if not xml:
        return None
    deadline = time.monotonic() + 20.0
    terminal = ["Add", "No FYNX music is published yet.",
                "Music catalogue couldn't be loaded", "Music catalogue took too long",
                "Sign in to FYNX to browse published music."]
    while time.monotonic() < deadline and not has_any(xml, terminal):
        time.sleep(0.5)
        xml = dump_ui("authenticated-server-status-music-catalogue-wait.xml")
    screenshot("authenticated-server-status-music-catalogue.png")
    if find_control(xml, ["No FYNX music is published yet."]):
        NOTES.append("BLOCKED music-only publish/playback: authenticated catalogue contains no published tracks.")
        run("adb", "shell", "input", "keyevent", "KEYCODE_BACK")
        return None
    if has_any(xml, ["Music catalogue couldn't be loaded", "Music catalogue took too long",
                     "Sign in to FYNX to browse published music."]):
        FAILURES.append("authenticated FYNX Music catalogue could not load: " + marker_summary(xml))
        return None
    if not find_control(xml, ["Add"]):
        FAILURES.append("published FYNX music catalogue had no selectable track.")
        return None
    track_title = next((n.attrib.get("text", "").strip() for n in nodes(xml)
                        if n.attrib.get("text", "").strip() and
                        n.attrib.get("text", "").strip() not in {"FYNX Music", "Search music", "Song or artist", "Preview", "Add", "Close", "Stop"}
                        and len(n.attrib.get("text", "").strip()) > 1), "")
    # Exercise the catalogue preview first if a real track exposes Preview/Stop.
    preview = find_control(xml, ["Preview"])
    if preview:
        preview_xml = tap_control(xml, ["Preview"], "authenticated-server-status-music-preview",
                                  ["Stop"], timeout=12.0)
        if preview_xml and find_control(preview_xml, ["Stop"]):
            NOTES.append("PASS a real published FYNX catalogue track enters preview state.")
            xml = tap_control(preview_xml, ["Stop"], "authenticated-server-status-music-preview-stop", ["Preview", "Add"], timeout=12.0)
        else:
            FAILURES.append("selecting Preview did not expose the Stop control for the real FYNX Music track.")
            xml = dump_ui("authenticated-server-status-music-preview-recovery.xml")
    xml = tap_control(xml, ["Add"], "authenticated-server-status-music-select", ["Send"], timeout=18.0)
    if not xml:
        return None
    screenshot("authenticated-server-status-music-composer.png")
    xml = tap_control(xml, ["Send"], "authenticated-server-status-music-publish",
                      ["Open FYNX camera", "Your status", "Create your status"], timeout=25.0)
    status = find_created_status(lambda item:
        str(item.get("ownerUsername") or "").removeprefix("@").lower() == CURRENT_USERNAME
        and str(item.get("type") or "").upper() == "MUSIC"
        and str(item.get("id") or "") not in baseline_ids
        and int(item.get("createdAtMillis") or 0) >= start_ms
        and item.get("musicCatalogueId") is not None
    )
    if not status:
        FAILURES.append("music-only composer Send did not create a retrievable MUSIC Status with a catalogue id.")
        return None
    status_id = str(status.get("id") or "")
    if not status_id:
        FAILURES.append("the server returned the music-only Status without an id.")
        return None
    CREATED_IDS.add(status_id)
    if not xml:
        FAILURES.append("music-only Status Send did not return to Home after the server accepted the Status.")
    NOTES.append("PASS music-only Status published through the app and retrieved with its catalogue track id.")
    return status


def verify_status_viewer(status: dict, name: str, expected_text: str | None = None) -> bool:
    status_id = str(status.get("id") or "")
    xml = launch_status(status_id, name)
    if not xml:
        return False
    if expected_text and expected_text not in xml:
        FAILURES.append(name + ": viewer did not display the exact content returned by the server.")
        return False
    if not find_control(xml, ["Status menu"]) or not find_control(xml, ["Like Status"]) or not find_control(xml, ["React"]):
        FAILURES.append(name + ": viewer controls were missing.")
        return False
    return True


def main() -> int:
    global CURRENT_USERNAME
    CURRENT_USERNAME = USERNAME.lower()
    report = [
        "# FYNX Server-Authenticated Status Lifecycle Certification",
        "",
        "- Commit: " + os.environ.get("GITHUB_SHA", "local"),
        "- Run: " + RUN_ID,
        "- APK: non-debuggable runtimeCert variant, signed with the CI debug certificate for emulator-only use",
        "- This test uses the normal sign-in UI and the real configured FYNX test account.",
        "- Run credentials and access tokens are never written to report, screenshot, APK, or source.",
        "- Test-created statuses are removed in a finally cleanup through the owner-authorized API.",
        "",
    ]
    if not USERNAME or not PASSWORD:
        report.append("## Result: BLOCKED")
        report.append("- FYNX_E2E_USERNAME/FYNX_E2E_PASSWORD repository secrets are required.")
        (ROOT / "FYNX-server-status-lifecycle.md").write_text("\n".join(report) + "\n", encoding="utf-8")
        print("\n".join(report))
        return 0

    # Preflight the same live API used by the app. An unreachable/invalid external
    # service is reported as BLOCKED, not falsely described as an app regression.
    global BASELINE_STATUS_IDS
    global RUN_START_MS
    RUN_START_MS = int(time.time() * 1000)
    try:
        # Wake the Render free-tier service and confirm database readiness before
        # authenticating. The repository's production reachability gate uses this
        # same 120-second / three-attempt policy for genuine cold starts.
        health = api_request("GET", "/health", timeout=120, retries=3, retry_delay=10)
        if health.get("ok") is not True or health.get("service") != "fynx-backend" or health.get("database") != "ready":
            raise RuntimeError("production /health did not report a ready database")
        ready = api_request("GET", "/ready", timeout=120, retries=3, retry_delay=10)
        if ready.get("ok") is not True or ready.get("service") != "fynx-backend" or ready.get("database") != "ready":
            raise RuntimeError("production /ready did not report a ready database")
        if not api_login():
            raise RuntimeError("API login returned no access token")
        existing = api_statuses()
        BASELINE_STATUS_IDS = {str(item.get("id") or "") for item in existing if str(item.get("id") or "")}
    except Exception as exc:
        reason = f"HTTP {exc.code}" if isinstance(exc, urllib.error.HTTPError) else type(exc).__name__
        report.extend(["## Result: BLOCKED",
                       "- Production health/readiness and authenticated Status API preflight did not complete (" + reason + ").",
                       "- The test will not create content while the live backend is unavailable or unauthenticated.",
                       "- No test Status was created. Existing debug/local UI certification remains separate."])
        (ROOT / "FYNX-server-status-lifecycle.md").write_text("\n".join(report) + "\n", encoding="utf-8")
        print("\n".join(report))
        return 0

    other_candidates = [item for item in existing
                        if str(item.get("ownerUsername") or "").removeprefix("@").lower() != CURRENT_USERNAME
                        and str(item.get("id") or "").strip()]
    app_signed_in = False
    text_status = None
    music_status = None
    try:
        app_signed_in = sign_in()
        if app_signed_in:
            NOTES.append("PASS non-debuggable runtimeCert installed and normal server sign-in reached authenticated Home.")

            # If the account can actually see another owner's Status, tap that owner's
            # visible Home Status circle; do not create a fake second user or test post.
            if other_candidates:
                home = dump_ui("authenticated-server-status-home-other-owner.xml")
                other = None
                control = None
                label = ""
                for candidate in other_candidates:
                    candidate_name = str(candidate.get("ownerDisplayName") or candidate.get("ownerUsername") or "").strip()
                    candidate_user = str(candidate.get("ownerUsername") or "").removeprefix("@").strip()
                    candidate_control = find_control(home, [candidate_name]) if candidate_name else None
                    if candidate_control is None and candidate_user:
                        candidate_control = find_control(home, [candidate_user])
                    if candidate_control:
                        other, control = candidate, candidate_control
                        label = candidate_name or candidate_user
                        break
                if control and other:
                    other_name = str(other.get("ownerDisplayName") or other.get("ownerUsername") or "").strip()
                    other_user = str(other.get("ownerUsername") or "").removeprefix("@").strip()
                    x, y = control[1], control[2]
                    run("adb", "shell", "input", "tap", str(x), str(y))
                    deadline = time.monotonic() + 22.0
                    viewer = ""
                    while time.monotonic() < deadline:
                        time.sleep(0.6)
                        viewer = dump_ui("authenticated-server-status-other-owner-viewer.xml")
                        if (find_control(viewer, ["Status menu"]) and find_control(viewer, ["Like Status"])
                                and find_control(viewer, ["React"]) and label.casefold() in viewer.casefold()):
                            break
                    if (find_control(viewer, ["Status menu"]) and find_control(viewer, ["Like Status"])
                            and find_control(viewer, ["React"]) and label.casefold() in viewer.casefold()):
                        screenshot("authenticated-server-status-other-owner-viewer.png")
                        NOTES.append("PASS tapped a real other owner's Status circle on Home and opened that owner's viewer.")
                        run("adb", "shell", "input", "keyevent", "KEYCODE_BACK")
                        time.sleep(0.8)
                    else:
                        FAILURES.append("Home tap on the other owner's Status circle did not open the expected real viewer.")
                        screenshot("authenticated-server-status-other-owner-viewer-failed.png")
                else:
                    NOTES.append("BLOCKED other-owner Home-row tap: server-visible Statuses exist, but none of their owner circles is currently visible in the Home accessibility hierarchy.")
            else:
                NOTES.append("BLOCKED other-owner Home-row/viewer check: the authenticated Status feed has no active Status by another owner; no fake user or Status was created.")

            text_status = publish_text_status()
            if text_status:
                if verify_status_viewer(text_status, "authenticated-server-status-text-viewer", TEXT_MARKER):
                    NOTES.append("PASS opened the exact server-retrieved Text Status by id in the real viewer; menu, Like, and React controls are visible.")
                    if find_control(dump_ui("authenticated-server-status-text-viewer-controls.xml"), ["Views"]):
                        views = tap_control(dump_ui("authenticated-server-status-text-viewer-before-views.xml"),
                                            ["Views"], "authenticated-server-status-viewers", ["Status viewers"], timeout=12.0)
                        if views and find_control(views, ["Status viewers"]):
                            screenshot("authenticated-server-status-viewers.png")
                            run("adb", "shell", "input", "keyevent", "KEYCODE_BACK")
                            time.sleep(0.8)
                            NOTES.append("PASS own-Status viewer list opened from the real Status viewer.")
                        else:
                            NOTES.append("BLOCKED own-Status viewers-list control was not available in this runtime hierarchy.")
                # Delete the test status through the app, then verify server-side deletion.
                if not delete_through_viewer(str(text_status.get("id") or ""), "authenticated-server-status-text"):
                    FAILURES.append("Text Status did not delete successfully from the viewer; owner-authorized API cleanup will run.")

            # Verify music-only publishing when the real authenticated catalogue has a track.
            music_status = publish_music_status()
            if music_status:
                title = str(music_status.get("musicTitle") or "")
                if verify_status_viewer(music_status, "authenticated-server-status-music-viewer", title or None):
                    NOTES.append("PASS music-only Status was fetched and opened in the real Story viewer.")
                if not delete_through_viewer(str(music_status.get("id") or ""), "authenticated-server-status-music"):
                    FAILURES.append("music-only Status did not delete successfully from the viewer; owner-authorized API cleanup will run.")
        else:
            NOTES.append("BLOCKED: normal server sign-in did not complete; no Status content was submitted.")
    finally:
        # Recover the ids even if UI navigation failed just after Send. Only this
        # account's unique Text marker or newly-created Music Status from this run
        # is eligible for cleanup; pre-existing Status ids are never deleted here.
        try:
            for item in api_statuses():
                status_id = str(item.get("id") or "")
                owner = str(item.get("ownerUsername") or "").removeprefix("@").lower()
                created_at = int(item.get("createdAtMillis") or 0)
                is_this_run_text = str(item.get("text") or "").strip() == TEXT_MARKER
                is_new_music = (
                    owner == CURRENT_USERNAME and status_id and
                    status_id not in BASELINE_STATUS_IDS and
                    str(item.get("type") or "").upper() == "MUSIC" and
                    created_at >= RUN_START_MS
                )
                if owner == CURRENT_USERNAME and status_id and (is_this_run_text or is_new_music):
                    CREATED_IDS.add(status_id)
        except Exception:
            pass
        # Make cleanup unconditional. If the app-level deletion path fails, use the
        # authenticated owner API to remove this run's own content, then report the UI failure.
        cleanup_failed = []
        for status_id in list(CREATED_IDS):
            if not cleanup_status(status_id):
                cleanup_failed.append(status_id)
            else:
                CREATED_IDS.discard(status_id)
        if cleanup_failed:
            FAILURES.append("authenticated API cleanup could not verify deletion of every test Status.")
        elif not CREATED_IDS:
            NOTES.append("PASS cleanup: no test-created Status remains in the authenticated server list.")

    report += ["## Results"]
    report.extend("- " + item for item in NOTES)
    report.extend("- FAIL: " + item for item in FAILURES)
    blocked_checks = any(item.strip().upper().startswith("BLOCKED") for item in NOTES)
    result_label = "RED" if FAILURES else ("PARTIAL / BLOCKED" if blocked_checks else "GREEN")
    report.append("")
    report.append("## Result: " + result_label)
    if blocked_checks and not FAILURES:
        report.append("- Some environment-dependent Status/Story journeys remain unverified; do not treat this report as full certification.")
    report.append("")
    (ROOT / "FYNX-server-status-lifecycle.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))
    return 1 if FAILURES else 0


if __name__ == "__main__":
    raise SystemExit(main())
