from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = [
    "app/src/main/java/com/fynx/app/ui/FynxApp.kt",
    "app/src/main/java/com/fynx/app/ui/FynxDeepLink.kt",
    "app/src/main/java/com/fynx/app/ui/FynxShare.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMarketplaceRemotePanel.kt",
    "app/src/main/java/com/fynx/app/ui/FynxMultiMediaPostClient.kt",
    "app/src/main/java/com/fynx/app/ui/AiAssistantClient.kt",
    "backend/serverBootstrap.js",
    "backend/marketplaceTransactions.js",
    "backend/marketplaceDisputes.js",
    "gradlew",
    ".github/workflows/android-build.yml",
]

checks = []
def check(name, ok):
    checks.append((name, bool(ok)))

check("all production-critical files exist", all((ROOT / p).is_file() for p in REQUIRED))

app = (ROOT / REQUIRED[0]).read_text(encoding="utf-8")
deep_link = (ROOT / REQUIRED[1]).read_text(encoding="utf-8")
share = (ROOT / REQUIRED[2]).read_text(encoding="utf-8")
marketplace = (ROOT / REQUIRED[3]).read_text(encoding="utf-8")
multimedia = (ROOT / REQUIRED[4]).read_text(encoding="utf-8")
ai = (ROOT / REQUIRED[5]).read_text(encoding="utf-8")
workflow = (ROOT / REQUIRED[-1]).read_text(encoding="utf-8")

check("production app is not left in preview mode", "FYNX_PREVIEW_MODE = false" in app)
check("bottom navigation exposes Business instead of Money", 'FynxNavItem("Business Account", "Business", Icons.Default.Storefront)' in app and 'FynxNavItem("Money Tools", "Money"' not in app)
check("money tools remain reachable outside the bottom bar", '"Money Tools" -> MoneyCenterPanel()' in app and 'Triple("Money Tools", "Money Center"' in app)
check("deep-link routing remains connected", all(x in deep_link for x in ["homeWebLink", "profileWebLink", "chatWebLink", "groupWebLink", "marketplaceWebLink", "storiesWebLink", "moneyWebLink"]))
check("share layer uses FYNX deep links", "FynxDeepLinkParser.homeWebLink()" in share and "FynxDeepLinkParser.inviteWebLink(code)" in share)
check("marketplace remains remote and protected", "FynxMarketplaceClient.listings" in marketplace and "FynxMarketplaceClient.createListing" in marketplace and "FynxMarketplaceSafety.analyze" in marketplace)
check("multi-media posting supports real uploaded media", "FynxProductionMessaging.uploadMedia" in multimedia and "/api/social/posts/multi" in multimedia)
check("AI client uses authenticated backend transport", "FynxBackendClient.postJson" in ai and "/api/assistant" in ai)
check("CI runs journey verification before the Android build", "python3 scripts/verify_fynx_journey.py" in workflow and "./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest" in workflow)

secret_pattern = re.compile(r"sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,}|ghp_[A-Za-z0-9]{30,}")
client_text = "\n".join(
    (ROOT / p).read_text(encoding="utf-8")
    for p in REQUIRED[:6]
)
check("no common API secret pattern is committed in critical client files", not secret_pattern.search(client_text))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(("PASS: " if ok else "FAIL: ") + name)
if failed:
    raise SystemExit("FYNX production certification gate failed: " + "; ".join(failed))
print(f"FYNX production certification gate passed ({len(checks)} checks)")
