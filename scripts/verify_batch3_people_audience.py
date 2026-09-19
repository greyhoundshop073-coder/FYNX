from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

social = (ROOT / "backend/socialRoutes.js").read_text(encoding="utf-8")
multi = (ROOT / "backend/socialMultiMediaBootstrap.js").read_text(encoding="utf-8")
discovery = (ROOT / "backend/discoveryRoutes.js").read_text(encoding="utf-8")
home = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxHomeSocialHubPanel.kt").read_text(encoding="utf-8")
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt").read_text(encoding="utf-8")
models = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxSocialModels.kt").read_text(encoding="utf-8")
audience_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxPostAudienceClient.kt").read_text(encoding="utf-8")

checks = [
    ("audience model has four choices", all(x in models for x in ["EVERYONE", "FRIENDS", "SELECTED", "ONLY_ME"])),
    ("People opens the real audience picker", "showPeoplePicker = true" in home and "Who can see this post?" in home),
    ("friends come from the authenticated backend", 'FynxBackendClient.get(context, "/api/friends")' in audience_client),
    ("selected people are sent with publishing", "audienceUserIds" in client),
    ("backend persists selected audience", "social_post_audience" in social and "SELECTED_PEOPLE" in social),
    ("selected audience is restricted to accepted friends", "selected audience must contain your accepted friends" in social),
    ("single-media publishing accepts all four audience modes", all(x in social for x in ["PUBLIC","FRIENDS_ONLY","SELECTED_PEOPLE","ONLY_ME"])),
    ("multi-media publishing carries audience", "audienceUserIds" in multi and "social_post_audience" in multi),
    ("Home feed enforces selected and only-me visibility", "p.visibility='SELECTED_PEOPLE'" in social and "p.visibility='ONLY_ME'" in social),
    ("post media enforces the same audience", "social_post_audience a WHERE a.post_id=p.id AND a.user_id=$2" in social),
    ("saved/repost visibility reuses the audience rule", "p.visibility='SELECTED_PEOPLE'" in discovery and "p.visibility='ONLY_ME'" in discovery),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)

if failed:
    raise SystemExit("Batch 3 audience verification failed: " + "; ".join(failed))
print("Batch 3 audience verification: GREEN")
