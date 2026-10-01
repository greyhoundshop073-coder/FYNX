#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
models = (root / "app/src/main/java/com/fynx/app/ui/FynxSocialModels.kt").read_text()
composer = (root / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text()
client = (root / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text()
remote = (root / "app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt").read_text()
feed = (root / "app/src/main/java/com/fynx/app/ui/FynxRemoteHomeSocialPanel.kt").read_text()
routes = (root / "backend/socialRoutes.js").read_text()
multi = (root / "backend/socialMultiMediaBootstrap.js").read_text()

checks = [
    ("real text background model", "enum class FynxPostTextBackground" in models),
    ("professional background options", all(k in models for k in ["OCEAN", "VIOLET", "EMERALD", "SUNSET", "CHARCOAL"])),
    ("controls appear only after typing", 'if (text.isNotBlank()) {' in composer and '"Text background"' in composer),
    ("composer remains normal when no background", "textBackground?.let { Color(it.color) } ?: MaterialTheme.colorScheme.background" in composer),
    ("background sent to real post client", 'put("textBackground", textBackground?.key ?: "")' in client),
    ("feed reads persisted styling", "textBackgroundColor" in remote and "textForegroundColor" in remote),
    ("feed renders persisted styling", "backgroundColor = post.textBackgroundColor?.let { Color(it) }" in feed),
    ("social schema stores styling", "text_background_color BIGINT" in routes and "text_foreground_color BIGINT" in routes),
    ("single post persists styling", "text_background,text_background_color,text_foreground_color" in routes),
    ("multimedia post persists styling", "text_background,text_background_color,text_foreground_color" in multi),
    ("server validates known styles", "backgroundStyles" in routes and "backgroundStyles" in multi),
]
for name, ok in checks:
    if not ok:
        raise SystemExit(f"FAIL: {name}")
    print(f"PASS: {name}")
print("Batch 4 Text background integrity verification passed")
