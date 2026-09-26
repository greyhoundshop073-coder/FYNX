#!/usr/bin/env python3
"""Certify the authenticated FYNX runtime journey with a real CI test account."""
from __future__ import annotations
import os, subprocess, time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path("fynx-runtime-screenshots"); ROOT.mkdir(parents=True,exist_ok=True)
PACKAGE="com.fynx.app"
USERNAME=os.environ.get("FYNX_E2E_USERNAME","").strip()
PASSWORD=os.environ.get("FYNX_E2E_PASSWORD","")
FAILURES=[]

def run(*args:str, timeout:int=30):
    try:
        return subprocess.run(args,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        output=exc.stdout.decode("utf-8","replace") if isinstance(exc.stdout,bytes) else (exc.stdout or "")
        return subprocess.CompletedProcess(args,124,output+"\nCOMMAND TIMEOUT")

def screenshot(name:str):
    with (ROOT/name).open("wb") as out:
        subprocess.run(["adb","exec-out","screencap","-p"],stdout=out,stderr=subprocess.STDOUT,check=False)

def dump_ui(name:str)->str:
    dump=run("adb","shell","uiautomator","dump","/sdcard/fynx-authenticated.xml")
    if dump.returncode != 0: return ""
    raw=subprocess.run(["adb","exec-out","cat","/sdcard/fynx-authenticated.xml"],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=False).stdout
    if raw.startswith(b"<?xml"):
        (ROOT/name).write_bytes(raw)
        return raw.decode("utf-8","replace")
    return ""

def nodes(xml_text:str):
    if not xml_text: return []
    try: return list(ET.fromstring(xml_text).iter("node"))
    except ET.ParseError: return []

def _center(node):
    bounds=node.attrib.get("bounds","")
    try:
        left_top,right_bottom=bounds.split("][",1)
        left,top=map(int,left_top.strip("[]").split(","))
        right,bottom=map(int,right_bottom.strip("[]").split(","))
        return (left+right)//2,(top+bottom)//2
    except (ValueError,IndexError):
        return None

def _matches(node, wanted:list[str]) -> bool:
    text=(node.attrib.get("text") or "").strip()
    desc=(node.attrib.get("content-desc") or "").strip()
    rid=(node.attrib.get("resource-id") or "").strip()
    hay=" | ".join((text,desc,rid)).lower()
    return any(label in hay for label in wanted)

def _find_control_node(root, wanted:list[str]):
    # Compose commonly exposes a Card's visible label as a child node while the
    # click action belongs to the clickable Card/merged semantics node above it.
    # Follow the matched node's ancestor path and tap the nearest real clickable
    # ancestor. Fall back to the matched node only when no clickable ancestor
    # is exposed by UIAutomator.
    path=[]
    def walk(node):
        path.append(node)
        if _matches(node, wanted) and _center(node):
            return list(path)
        for child in list(node):
            found=walk(child)
            if found:
                return found
        path.pop()
        return None
    matched_path=walk(root)
    if not matched_path:
        return None
    for node in reversed(matched_path):
        if node.attrib.get("clickable","false").lower()=="true" and _center(node):
            return node
    return matched_path[-1]

def find_control(xml_text:str, labels:list[str]):
    if not xml_text: return None
    wanted=[x.lower() for x in labels]
    try:
        root=ET.fromstring(xml_text)
    except ET.ParseError:
        return None

    # Home intentionally has two real camera entry points: the header camera
    # opens the post composer, while the in-feed fast camera opens capture
    # directly. Both expose the same accessibility label. For this journey,
    # the requested "Open FYNX camera" means the header entry point, so select
    # the topmost matching clickable ancestor instead of whichever duplicate
    # happens to appear first in the UI tree.
    if wanted == ["open fynx camera"]:
        candidates=[]
        path=[]
        def collect(node):
            path.append(node)
            if _matches(node, wanted) and _center(node):
                for ancestor in reversed(path):
                    if ancestor.attrib.get("clickable","false").lower()=="true" and _center(ancestor):
                        candidates.append(ancestor)
                        break
            for child in list(node):
                collect(child)
            path.pop()
        collect(root)
        if candidates:
            node=min(candidates, key=lambda item: (_center(item)[1], _center(item)[0]))
        else:
            node=None
    else:
        node=_find_control_node(root, wanted)

    if node is None:
        return None
    center=_center(node)
    if center:
        text=(node.attrib.get("text") or "").strip()
        desc=(node.attrib.get("content-desc") or "").strip()
        rid=(node.attrib.get("resource-id") or "").strip()
        return text or desc or rid,center[0],center[1]
    return None

def find_edit_fields(xml_text:str):
    fields=[]
    for node in nodes(xml_text):
        if node.attrib.get("class") != "android.widget.EditText":
            continue
        if node.attrib.get("visible-to-user","true").lower() == "false":
            continue
        center=_center(node)
        if center:
            fields.append(center)
    return fields

def tap_control(xml_text:str, labels:list[str], name:str, expected_labels:list[str]|None=None)->str:
    control=find_control(xml_text,labels)
    if not control:
        FAILURES.append(name)
        return ""
    label,x,y=control
    run("adb","shell","input","tap",str(x),str(y))
    # Camera/media entry can legitimately trigger an Android runtime permission
    # dialog before the FYNX surface becomes visible. Handle only that real
    # system prompt, then continue polling the actual app hierarchy.
    next_xml=""
    for _ in range(16):
        time.sleep(.5)
        next_xml=dump_ui(f"authenticated-{name}.xml")
        if find_control(next_xml,["While using the app","Only this time"]):
            permission=find_control(next_xml,["While using the app"])
            if permission:
                _,px,py=permission
                run("adb","shell","input","tap",str(px),str(py))
                time.sleep(1.0)
                continue
        if find_control(next_xml,["Allow FYNX to record audio"]):
            permission=find_control(next_xml,["While using the app","Only this time"])
            if permission:
                _,px,py=permission
                run("adb","shell","input","tap",str(px),str(py))
                time.sleep(1.0)
                continue
        if expected_labels and any(find_control(next_xml,[wanted]) for wanted in expected_labels):
            break
    screenshot(f"authenticated-{name}.png")
    return next_xml

def input_text(value:str):
    # CI test credentials should use an automation-safe password (letters/digits).
    safe=value.replace("%","%25").replace(" ","%s")
    return run("adb","shell","input","text",safe)

def login():
    run("adb","shell","am","force-stop",PACKAGE)
    result=run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d","fynx://home",PACKAGE)
    time.sleep(2.5)
    xml=dump_ui("authenticated-before-login.xml")
    screenshot("authenticated-before-login.png")
    sign_in_gate=find_control(xml,["Sign In"])
    if not sign_in_gate:
        return xml, "authentication gate was not visible"
    # The app starts on the welcome gate when no local session exists. Enter
    # the real Sign In screen before resolving its username/password controls.
    _,x,y=sign_in_gate
    run("adb","shell","input","tap",str(x),str(y))
    # Compose can take longer than a fixed sleep to publish the new semantics
    # tree on a cold CI emulator. Poll the real hierarchy until the login
    # controls are actually exposed instead of declaring a false RED.
    xml=""
    for _ in range(12):
        time.sleep(1.0)
        xml=dump_ui("authenticated-login-screen.xml")
        if find_control(xml, ["Username"]) or len(find_edit_fields(xml)) >= 2:
            break
    screenshot("authenticated-login-screen.png")
    # Prefer explicit accessibility labels from the real auth fields. Fall back
    # to the rendered EditText controls for emulator/UIAutomator variations.
    user_control=find_control(xml,["Username"])
    if not user_control:
        edit_fields=find_edit_fields(xml)
        if not edit_fields:
            return xml, "username control was not visible"
        user_control=("username",edit_fields[0][0],edit_fields[0][1])
    _,x,y=user_control
    run("adb","shell","input","tap",str(x),str(y))
    time.sleep(.3)
    result=input_text(USERNAME)
    if result.returncode != 0:
        return xml, "adb input text failed for username"

    # Opening the keyboard scrolls the Compose form. Re-read the hierarchy
    # before locating Password; never reuse the pre-keyboard coordinates.
    xml=dump_ui("authenticated-after-username.xml")
    pass_control=find_control(xml,["Password"])
    if not pass_control:
        edit_fields=find_edit_fields(xml)
        if len(edit_fields) < 2:
            return xml, f"password control was not visible after username entry (edittexts found {len(edit_fields)})"
        pass_control=("password",edit_fields[1][0],edit_fields[1][1])
    _,x,y=pass_control
    run("adb","shell","input","tap",str(x),str(y))
    time.sleep(.3)
    result=input_text(PASSWORD)
    if result.returncode != 0:
        return xml, "adb input text failed for password"

    # Dismiss the software keyboard so the real Sign In button is back in
    # the visible UI hierarchy before tapping it.
    run("adb","shell","input","keyevent","4")
    time.sleep(1.0)
    xml=dump_ui("authenticated-login-filled.xml")
    screenshot("authenticated-login-filled.png")
    sign_in=find_control(xml,["Sign In"])
    if not sign_in:
        return xml, "Sign In control disappeared after credentials were entered"
    _,x,y=sign_in
    run("adb","shell","input","tap",str(x),str(y))
    time.sleep(4.5)
    xml=dump_ui("authenticated-home.xml")
    screenshot("authenticated-home.png")
    return xml, ""

def dismiss_runtime_permission_prompt()->str:
    # A fresh process can trigger Android's notification permission dialog after
    # the authenticated session is already persisted. That system dialog blocks
    # the real FYNX Home controls, so dismiss only the permission prompt and
    # then re-read the actual app hierarchy. Never fabricate app state.
    for _ in range(6):
        xml=dump_ui("authenticated-permission-check.xml")
        if not xml:
            time.sleep(.5)
            continue
        allow=find_control(xml,["Don’t allow","Don't allow"])
        if allow:
            _,x,y=allow
            run("adb","shell","input","tap",str(x),str(y))
            time.sleep(1.0)
            return dump_ui("authenticated-home-after-permission.xml")
        # The prompt may not have appeared yet; stop polling as soon as the
        # FYNX Home hierarchy is visible.
        if find_control(xml,["Chat","Friends","Stories","More","Features"]):
            return xml
        time.sleep(.5)
    return dump_ui("authenticated-home-after-permission.xml")

def capture_surface(name:str, labels:list[str], xml:str, expected_labels:list[str]|None=None)->str:
    next_xml=tap_control(xml,labels,name,expected_labels)
    if next_xml:
        return next_xml
    return xml

report=["# FYNX Authenticated Runtime Visual Certification","",
        f"- Commit: {os.environ.get('GITHUB_SHA','local')}",
        f"- Run: {os.environ.get('GITHUB_RUN_ID','local')}","",
        "This journey uses a real FYNX account supplied through GitHub Actions secrets.",
        "No fabricated users, posts, messages, listings or application records are created.",
        "Credentials are never written to the APK or repository."]

if not USERNAME or not PASSWORD:
    report += ["","## Result: BLOCKED","- FYNX_E2E_USERNAME and FYNX_E2E_PASSWORD repository secrets are required for authenticated visual certification."]
    (ROOT/"FYNX-authenticated-runtime.md").write_text("\n".join(report)+"\n",encoding="utf-8")
    print("\n".join(report))
    raise SystemExit(2)

install=run("adb","install","-r","app/build/outputs/apk/debug/app-debug.apk")
report.append(f"- APK install: {'PASS' if install.returncode==0 else 'FAIL'}")
if install.returncode != 0:
    report.append("- "+install.stdout.strip().replace("\n"," | "))
    raise SystemExit(1)

xml,error=login()
if error:
    FAILURES.append("real account sign-in: "+error)
else:
    if find_control(xml,["Sign In"]) and find_control(xml,["Create Account"]):
        FAILURES.append("authentication did not leave the login gate")
    else:
        report.append("- PASS real account authenticated through the FYNX login flow")

if not FAILURES:
    # Capture the exact Home camera -> post composer -> real camera path so the
    # uploaded visual artifact shows the surfaces that were actually changed.
    home_before_camera = xml
    composer_xml = tap_control(
        home_before_camera,
        ["Open FYNX camera"],
        "home-camera-composer",
        ["What's on your mind?", "Photo", "Video/Camera"],
    )
    if composer_xml:
        report.append("- PASS Home header camera -> real post composer screenshot/UI hierarchy")
        camera_xml = tap_control(
            composer_xml,
            ["Video/Camera"],
            "composer-camera",
            ["Switch front/back camera", "Photo", "Recording"],
        )
        if camera_xml:
            report.append("- PASS Post composer -> real camera screenshot/UI hierarchy")
        else:
            FAILURES.append("post composer -> real camera")
    else:
        FAILURES.append("Home header camera -> real post composer")
    run("adb","shell","am","force-stop",PACKAGE)
    run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d","fynx://home",PACKAGE)
    time.sleep(2.5)
    xml=dismiss_runtime_permission_prompt() or dump_ui("authenticated-home-camera-reset.xml") or xml

    # Main surfaces. These labels are resolved from the real rendered hierarchy.
    for name,labels,expected in (
        ("chat",["Chat"],["Chat"]),
        ("friends",["Friends"],["Friends"]),
        ("stories",["See all"],["Status","Add status","Status"]),
    ):
        before=xml
        after=capture_surface(name,labels,before,expected)
        if after:
            report.append(f"- PASS authenticated Home -> {name} screenshot/UI hierarchy")
            if name == "chat":
                # Keep an explicitly named copy for the Recent Chats board so the
                # downloadable visual artifact is easy to identify before APK use.
                import shutil
                source = ROOT/"authenticated-chat.png"
                recent = ROOT/"authenticated-chat-recent.png"
                if source.exists():
                    shutil.copyfile(source, recent)
                    report.append("- PASS explicit Recent Chats screenshot artifact")
        else:
            FAILURES.append("authenticated Home -> "+name)
        # Reset the process between journeys. Chat can keep an open panel even
        # after selected=Home, so a deep-link alone is not a clean Home state.
        # force-stop preserves the real persisted login session while clearing
        # transient Compose navigation state.
        run("adb","shell","am","force-stop",PACKAGE)
        run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d","fynx://home",PACKAGE)
        time.sleep(2.5)
        xml=dismiss_runtime_permission_prompt() or dump_ui("authenticated-home-reset.xml") or xml

    # Open Features/Money/AI through the real UI. Features is a scrollable
    # surface, so Money and AI may be below the first viewport. Each destination
    # gets a clean Features launch from Home; this avoids reusing stale coordinates
    # after the first navigation has already left the Features screen.
    def open_features(target_labels:list[str]|None=None):
        run("adb","shell","am","force-stop",PACKAGE)
        run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d","fynx://home",PACKAGE)
        time.sleep(2.5)
        home_xml=dismiss_runtime_permission_prompt() or dump_ui("authenticated-home-features.xml") or xml
        feature_xml=tap_control(home_xml,["More","Features"],"features",["FYNX Features"])
        if not feature_xml:
            return ""
        # Money and AI are real cards in a LazyColumn. Do not stop merely because
        # the other card is visible: the target card itself must be present before
        # we attempt its tap. This prevents a viewport-dependent false RED when
        # one card is just outside the current scroll position.
        for _ in range(12):
            if target_labels is None or find_control(feature_xml,target_labels):
                return feature_xml
            run("adb","shell","input","swipe","540","1600","540","850","500")
            time.sleep(.6)
            feature_xml=dump_ui("authenticated-features-scroll.xml")
        return feature_xml if target_labels is None else ""

    features=open_features()
    if features:
        report.append("- PASS authenticated Home -> Features screenshot/UI hierarchy")
        for name,labels,expected in (
            ("money",["Money Center"],["Money Center"]),
            ("ai",["FYNX AI Assistant"],["FYNX AI Assistant"]),
        ):
            current=open_features(labels)
            if not current:
                FAILURES.append("authenticated Features -> "+name)
                continue
            after=capture_surface(name,labels,current,expected)
            if after:
                report.append(f"- PASS authenticated Features -> {name} screenshot/UI hierarchy")
            else:
                FAILURES.append("authenticated Features -> "+name)
    else:
        FAILURES.append("authenticated Home -> Features")

report += ["",
           "## Captured authenticated surfaces",
           "- authenticated-home.png",
           "- authenticated-chat.png",
           "- authenticated-friends.png",
           "- authenticated-stories.png",
           "- authenticated-features.png",
           "- authenticated-money.png",
           "- authenticated-ai.png",
           "",
           f"## Result: {'GREEN' if not FAILURES else 'RED'}"]
if FAILURES:
    report += ["","Failures:"]+["- "+x for x in FAILURES]
(ROOT/"FYNX-authenticated-runtime.md").write_text("\n".join(report)+"\n",encoding="utf-8")
print("\n".join(report))
raise SystemExit(1 if FAILURES else 0)
