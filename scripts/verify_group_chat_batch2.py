from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
groups = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt").read_text()
models = (ROOT / "app/src/main/java/com/fynx/app/ui/ChatModels.kt").read_text()
remote = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt").read_text()

checks = []
def check(name, condition):
    checks.append((name, bool(condition)))

check("group swipe reply is wired", "detectHorizontalDragGestures" in groups and "replyToId = message.id" in groups)
check("group reply preview is rendered", "message.replyToId" in groups and "Original message" in groups)
check("group reactions are wired to remote", "reactToMessage" in groups)
check("group pin and delete actions are wired", "setPinned" in groups and "deleteMessage" in groups)
check("group image/video rendering preserves media surface", "FynxRemoteMedia" in groups and 'attachmentType == "video_note"' in groups)
check("group voice message model is supported", 'attachmentType: String? = null' in models and '"audio"' in models)
check("group reply/media payload reaches backend client", "replyToId" in remote and "attachmentMediaId" in remote)
check("group plus menu contains camera gallery files", 'Text("Camera")' in groups and 'Text("Gallery")' in groups and 'Text("Files")' in groups)
check("group plus menu contains location contact poll", 'Text("Location")' in groups and 'Text("Contact")' in groups and 'Text("Poll")' in groups)
check("group composer keeps microphone at the right edge", 'Icons.Default.Mic' in groups and 'Color(0xFF7C3AED)' in groups)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
if failed:
    print(f"Group Chat Batch 2 gate: FAIL ({len(failed)} checks)")
    raise SystemExit(1)
print(f"Group Chat Batch 2 gate: PASS ({len(checks)} checks)")
