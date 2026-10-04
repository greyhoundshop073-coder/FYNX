from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/fynx/app/ui"

chat = (APP / "FynxChatSettingsPanel.kt").read_text(encoding="utf-8")
wallpaper = (APP / "FynxChatWallpaper.kt").read_text(encoding="utf-8")
groups = (APP / "FynxGroupsPanel.kt").read_text(encoding="utf-8")

checks = {
    "chat settings": ["Chat settings", "FynxChatWallpaperBackground"],
    "wallpaper foundation": ["FynxChatWallpaperBackground", "wallpaper"],
    "group settings entry": ["Group settings"],
}
for label, needles in checks.items():
    source = chat if label == "chat settings" else wallpaper if label == "wallpaper foundation" else groups
    for needle in needles:
        if needle not in source:
            raise SystemExit(f"SETTINGS CLOSURE RED: {label} missing {needle}")

print("SETTINGS CLOSURE GREEN: existing Chat/Group settings foundations are present")
