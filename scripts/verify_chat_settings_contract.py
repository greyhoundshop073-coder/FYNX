from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/fynx/app/ui"

chat = (APP / "FynxChatSettingsPanel.kt").read_text(encoding="utf-8")
wallpaper = (APP / "FynxChatWallpaper.kt").read_text(encoding="utf-8")
groups = (APP / "FynxGroupsPanel.kt").read_text(encoding="utf-8")

# The production panel uses the title "Chat Settings" (title case), while
# the wallpaper implementation owns the personalization dialog title
# "Chat settings". Verify the actual UI contracts instead of requiring one
# exact capitalization in the wrong source file.
checks = {
    "chat settings": [("Chat Settings" in chat or "Chat settings" in chat), "FynxChatWallpaperBackground" in chat],
    "wallpaper foundation": ["FynxChatWallpaperBackground" in wallpaper, "wallpaper" in wallpaper],
    "group settings entry": ["Group settings" in groups],
}
for label, results in checks.items():
    if not all(results):
        raise SystemExit(f"SETTINGS CLOSURE RED: {label} missing expected implementation marker")

print("SETTINGS CLOSURE GREEN: existing Chat/Group settings foundations are present")
