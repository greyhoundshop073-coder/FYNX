from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/fynx/app/ui"
models = (APP / "ChatModels.kt").read_text(encoding="utf-8")
store = (APP / "FynxChatStore.kt").read_text(encoding="utf-8")

if "ChatMessage" not in models:
    raise SystemExit("ATTACHMENT/AUDIO RED: ChatMessage model missing")
if "ChatMessage" not in store:
    raise SystemExit("ATTACHMENT/AUDIO RED: Chat store missing ChatMessage persistence")
print("ATTACHMENT/AUDIO GREEN: existing message model/store foundation present")
