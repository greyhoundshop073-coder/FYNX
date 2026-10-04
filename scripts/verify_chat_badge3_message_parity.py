from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/fynx/app/ui"
BACKEND = ROOT / "backend"

private = (UI / "FynxProductionMessaging.kt").read_text(encoding="utf-8")
group_client = (UI / "FynxGroupRemoteClient.kt").read_text(encoding="utf-8")
group_panel = (UI / "GroupChatPanel.kt").read_text(encoding="utf-8")
server = (BACKEND / "server.js").read_text(encoding="utf-8")

checks = [
    ("private message reply state", "replyToId" in private),
    ("private message reaction state", "reaction" in private),
    ("private edited state", "edited" in private),
    ("private deleted state", "deleted" in private),
    ("private pinned state", "pinned" in private),
    ("group reply state", "replyToId" in group_client),
    ("group reaction state", "reaction" in group_client),
    ("group edited state", "edited" in group_client),
    ("group deleted state", "deleted" in group_client),
    ("group pinned state", "pinned" in group_client),
    ("group panel reply flow", "replyTo" in group_panel),
    ("backend edited/deleted/reaction/pin state", all(x in server for x in ("edited", "deleted", "reaction", "pinned"))),
]

failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit("BADGE 3 RED: " + ", ".join(failed))

print(f"BADGE 3 GREEN: {len(checks)} private/group message-parity contracts verified")
