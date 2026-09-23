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

def _click_target(node):
    # UIAutomator often exposes Compose labels as non-clickable children of the
    # actual clickable container. Walk down to the rendered label, then return
    # the nearest clickable ancestor so input taps land on the control itself.
    if node.attrib.get("clickable","false").lower() == "true":
        return node
    for child in list(node):
        target=_click_target(child)
        if target is not None:
            return target
    return None

def _find_control_node(node, wanted:list[str]):
    if _matches(node, wanted):
        target=_click_target(node)
        if target is not None:
            return target
        center=_center(node)
        if center:
            return node
    for child in list(node):
        found=_find_control_node(child, wanted)
        if found is not None:
            return found
    return None

def find_control(xml_text:str, labels:list[str]):
    if not xml_text: return None
    wanted=[x.lower() for x in labels]
    try:
        root=ET.fromstring(xml_text)
    except ET.ParseError:
        return None
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

def tap_control(xml_text:str, labels:list[str], name:str)->str:
    control=find_control(xml_text,labels)
    if not control:
        FAILURES.append(name)
        return ""
    label,x,y=control
    run("adb","shell","input","tap",str(x),str(y))
    time.sleep(2.5)
    screenshot(f"authenticated-{name}.png")
    return dump_ui(f"authenticated-{name}.xml")

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

def capture_surface(name:str, labels:list[str], xml:str)->str:
    next_xml=tap_control(xml,labels,name)
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
    # Main surfaces. These labels are resolved from the real rendered hierarchy.
    for name,labels in (
        ("chat",["Chat"]),
        ("friends",["Friends"]),
        ("stories",["Stories","Status"]),
    ):
        before=xml
        after=capture_surface(name,labels,before)
        if after != before:
            report.append(f"- PASS authenticated Home -> {name} screenshot/UI hierarchy")
        else:
            FAILURES.append("authenticated Home -> "+name)
        xml=dump_ui("authenticated-home-reset.xml") or xml
        run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d","fynx://home",PACKAGE)
        time.sleep(2)
        xml=dump_ui("authenticated-home-reset.xml") or xml

    # Open Features/Money/AI through the real UI where exposed.
    run("adb","shell","am","start","-W","-a","android.intent.action.VIEW","-d","fynx://home",PACKAGE)
    time.sleep(2)
    xml=dump_ui("authenticated-home-features.xml") or xml
    features=tap_control(xml,["More","Features"],"features")
    if features:
        report.append("- PASS authenticated Home -> Features screenshot/UI hierarchy")
        for name,labels in (("money",["Money Tools","Money Center"]),("ai",["FYNX AI","AI Assistant"])):
            after=capture_surface(name,labels,features)
            if after != features:
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
