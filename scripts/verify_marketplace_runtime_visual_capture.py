#!/usr/bin/env python3
"""Guard real Marketplace/My Shop visual evidence capture and failure preservation."""
from pathlib import Path
import sys

runner = Path("scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")
navigation = Path("scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
workflow = Path(".github/workflows/android-build.yml").read_text(encoding="utf-8")
wallet_source = Path("app/src/main/java/com/fynx/app/ui/FynxWalletActionPanels.kt").read_text(encoding="utf-8")
emulator_storage = Path("scripts/configure_android_emulator_storage.sh").read_text(encoding="utf-8")

checks = [
    (
        "Marketplace seller-flow capture dismisses only a confirmed visible Android keyboard before scrolling",
        "def dismiss_ime_if_visible(name: str) -> str:" in navigation
        and 'dumpsys", "input_method"' in navigation
        and 'KEYCODE_BACK' in navigation
        and 'dismiss_ime_if_visible("marketplace-visual-keyboard-dismissed.xml")' in navigation,
    ),
    (
        "Wallet bank-transfer capture recognizes the current account sign-in safety gate without claiming account issuance",
        '["Connect your FYNX account", "Your dedicated FYNX funding account", "Dedicated account not ready"]' in navigation
        and "No account request has been sent." in wallet_source,
    ),
    (
        "normal authenticated Marketplace journey captures private My Shop overview and all seller sections",
        '# Continue in the same real, server-authenticated session into the private' in navigation
        and 'shop_xml = tap_control(' in navigation
        and all(
            f'"authenticated-marketplace-my-shop-{slug}.png"' in navigation
            for slug in ("overview", "profile", "listings", "orders", "delivery-drivers", "earnings-payouts")
        )
        and "runtime report was missing real screenshot files:" in navigation
        and "This is read-only: do not save a shop profile" in navigation,
    ),
    (
        "Marketplace refreshes its real destination and restores the shortcut rail after Wallet before seller capture",
        'if not find_control(marketplace_xml, ["My Shop"]):' in navigation
        and 'authenticated-marketplace-refresh-after-wallet' in navigation
        and 'find_control(marketplace_xml, ["Search products, sellers or categories"])' in navigation
        and 'authenticated-marketplace-my-shop-return-top.xml' in navigation
        and 'reverse=True' in navigation
        and navigation.index("authenticated-marketplace-my-shop-return-top.xml") < navigation.index("product_target = marketplace_product_card_target(marketplace_xml)"),
    ),
    (
        "offline debug Wallet capture accepts only the real unavailable-balance safety state, never fabricated balance buckets",
        'offline_preview = LOGIN_MODE == "debug-only local UI entry (not server-authenticated)"' in navigation
        and 'find_control(xml_text, ["Available balance"])' in navigation
        and 'find_control(xml_text, ["Unavailable"])' in navigation
        and 'find_control(xml_text, ["Wallet unavailable"])' in navigation
        and 'find_control(xml_text, ["Wallet security & PIN"])' in navigation
        and 'find_control(xml_text, ["Balance details"])' in navigation,
    ),
    (
        "Android CI attempts focused Marketplace/My Shop capture after instrumentation failure",
        'if [ "${FYNX_AI_VISUALS_ONLY:-0}" = "1" ]; then' in runner
        and 'FYNX_AI_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py' in runner
        and 'FYNX_LOCAL_VISUALS_ONLY=1 FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py' in runner
        and 'Preserve the existing Marketplace/My Shop visual fallback for its workstream.' in runner
        and 'exit "$TEST_EXIT"' in runner,
    ),
    (
        "visual capture failure cannot turn a red instrumentation run green",
        'if [ "${VISUAL_EXIT}" -ne 0 ]; then' in runner
        and 'visual capture did not complete' in runner
        and 'the instrumentation result remains ${TEST_EXIT}' in runner
        and 'exit "$TEST_EXIT"' in runner,
    ),
    (
        "green instrumentation selects authenticated navigation only when backend health is ready",
        'elif node --input-type=module -e' in runner
        and 'body?.database !== "ready"' in runner
        and 'FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py' in runner
        and 'if [ -z "${FYNX_E2E_USERNAME:-}" ] || [ -z "${FYNX_E2E_PASSWORD:-}" ]; then' in runner
        and runner.index('elif node --input-type=module -e') < runner.index('if [ "${FYNX_MARKETPLACE_VISUALS_ONLY:-0}" = "1" ]; then')
        and 'E2E credentials are not configured; running read-only camera/Marketplace UI certification' in runner
        and 'Live backend is unavailable; running read-only camera/Marketplace UI certification' in runner
        and 'FYNX_LOCAL_VISUALS_ONLY=1' in runner
        and 'FORCE_LOCAL_VISUALS=os.environ.get("FYNX_LOCAL_VISUALS_ONLY") == "1"' in navigation
        and 'if debug_entry_visible and (not PASSWORD or FORCE_LOCAL_VISUALS):' in navigation
        and 'TEST_EXIT=$?' in runner,
    ),
    (
        "focused navigation mode is isolated from unrelated Home/Chat/Status journey loops",
        'if os.environ.get("FYNX_MARKETPLACE_VISUALS_ONLY") == "1":' in navigation
        and 'raise SystemExit(1 if FAILURES else 0)' in navigation,
    ),
    (
        "Marketplace-only visuals use the actual debug entry without weakening normal credential login",
        'find_control(xml,["Leave password blank for quick local entry"])' in navigation
        and 'local_visual_username = USERNAME or "marketplace_visual_check"' in navigation
        and 'if not VISUALS_ONLY and (not USERNAME or not PASSWORD):' in navigation
        and 'LOGIN_MODE="debug-only local UI entry (not server-authenticated)"' in navigation
        and "it is not server authentication." in navigation,
    ),
    (
        "debug-only visual capture prevents Android notification prompts from intercepting real UI navigation",
        'def prepare_debug_visual_capture_permissions()' in navigation
        and 'if not VISUALS_ONLY:' in navigation
        and 'run("adb", "shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")' in navigation
        and 'prepare_debug_visual_capture_permissions()' in navigation
        and 'and "com.google.android.permissioncontroller" in xml' in navigation
        and 'find_control(xml, ["Allow FYNX to send you notifications?"])' in navigation
        and 'find_control(xml, ["Allow"])' in navigation
        and 'if VISUALS_ONLY and "com.google.android.permissioncontroller" in xml:' in navigation
        and 'authenticated-before-login-after-permission.png' in navigation
        and 'find_control(xml, ["Open FYNX camera"])' in navigation,
    ),
    (
        "real Marketplace screenshot and UI hierarchy are captured after destination marker verification",
        ('tap_control(\n        xml, ["Marketplace"], "marketplace", ["Search products, sellers or categories"]' in navigation
         or 'tap_control(\n        home_xml, ["Marketplace"], "marketplace", ["Search products, sellers or categories"]' in navigation)
        and 'screenshot("authenticated-marketplace-product-detail.png")' in navigation
        and '"authenticated-marketplace.xml"' in navigation
        and "def wait_for_marketplace_settled(timeout: float = 20.0)" in navigation
        and "wait_for_marketplace_settled(timeout=20.0)" in navigation
        and "Marketplace discovery remained in loading state after 20 seconds" in navigation
        and "def wait_for_my_shop_section_settled(slug: str, timeout: float = 20.0)" in navigation
        and 'wait_for_my_shop_section_settled("overview", timeout=20.0)' in navigation
        and "section_xml, section_settled = wait_for_my_shop_section_settled(slug, timeout=20.0)" in navigation
        and "My Shop overview remained loading after 20 seconds" in navigation
        and "remained in a loading state after 20 seconds; the screenshot is retained for diagnosis." in navigation,
    ),
    (
        "My Shop public profile entry is exercised on Android and captures only a settled real storefront state",
        '["View public Marketplace profile"]' in navigation
        and 'screenshot("authenticated-marketplace-public-profile.png")' in navigation
        and '["Marketplace profile"]' in navigation
        and '["Available products"]' in navigation
        and "This seller has no active products available right now." in navigation
        and "Contact seller" in navigation
        and "My Shop -> View public Marketplace profile" in navigation
        and "Public Marketplace profile Back did not return to Marketplace" in navigation,
    ),
    (
        "dark Home mirror-glass capture keeps the active session and recovers through the real debug UI entry",
        'dark_home_xml = dismiss_runtime_permission_prompt() or dump_ui("home-mirror-glass-dark-probe.xml")' in navigation
        and 'dark_home_xml, dark_login_error = login()' in navigation
        and 'screenshot("home-mirror-glass-dark.png")' in navigation
        and "Home mirror-glass dark-mode capture could not recover the real Home surface" in navigation
        and 'xml = dismiss_runtime_permission_prompt() or dump_ui("authenticated-home-marketplace-light-probe.xml")' in navigation
        and 'restore_home_after_restart("home-mirror-glass-dark-mode")' not in navigation,
    ),
    (
        "product details require an actual clickable card carrying a displayed NGN/USD price",
        "def marketplace_product_card_target(xml_text: str):" in navigation
        and "NGN" in navigation and "USD" in navigation
        and "no visible real listing card" in navigation,
    ),
    (
        "product-publishing capture uses exact Sell matching and scrolls inside the real content viewport",
        '["Sell", "Sell something", "Start selling", "Sell your first product"]' in navigation
        and 'def find_control(xml_text:str, labels:list[str], *, exact_only: bool = False):' in navigation
        and 'candidates = exact if exact_only else (exact or partial)' in navigation
        and 'find_control(marketplace_xml, sell_labels, exact_only=True)' in navigation
        and '["Product name", "Choose media", "Product media"], exact_only=True' in navigation
        and 'def swipe_vertical_scroll_container(' in navigation
        and 'swipe_vertical_scroll_container(\n                marketplace_xml, "marketplace-publish-cta-scroll-"' in navigation
        and 'node.attrib.get("scrollable", "false").lower() != "true"' in navigation
        and "without submitting or creating a listing" in navigation,
    ),
    (
        "My Shop overview and each management section have verified destination markers",
        '"Your shop at a glance"' in navigation
        and all(f'"{name}"' in navigation for name in ("Shop profile", "Listings", "Orders", "Delivery & drivers", "Earnings & payouts"))
        and "Marketplace earnings" in navigation,
    ),
    (
        "visual report explicitly separates screenshots from live backend proof and forbids fabricated records",
        "A visible page or screenshot does not prove that a backend request succeeded." in navigation
        and "No products, orders, payments, earnings, drivers, or seller records are fabricated." in navigation,
    ),
    (
        "the CI workflow executes this visual-capture regression guard",
        "python3 scripts/verify_marketplace_runtime_visual_capture.py" in workflow,
    ),
    (
        "full runtime certification remains explicitly runnable on integration and deliberate home-cert pushes",
        "inputs.full_runtime == 'true'" in workflow
        and "refs/heads/integration/fynx-single-apk-20261013" in workflow
        and "contains(github.event.head_commit.message, '[home-cert]')" in workflow
        and "github.event.pull_request.head.ref == 'work1/seller-profile-port-20261110'" in workflow
        and "FYNX_MARKETPLACE_VISUALS_ONLY" in workflow,
    ),
    (
        "Android emulator userdata is capped to fit the hosted runner AVD storage",
        "profile: pixel_2" in workflow
        and "disk-size: 2G" in workflow
        and "pre-emulator-launch-script: bash scripts/configure_android_emulator_storage.sh" in workflow
        and "sed -i" in emulator_storage
        and "disk.dataPartition.size" in emulator_storage
        and "partition_entries" in emulator_storage
        and '!= "1"' in emulator_storage
        and "partition_value" in emulator_storage
        and '!= "2G"' in emulator_storage
        and "available_mb" in emulator_storage
        and "-lt 2500" in emulator_storage
        and "emulator-boot-timeout" not in workflow.split("uses: reactivecircus/android-emulator-runner@", 1)[1].split("script: bash scripts/run_android_runtime_certification.sh", 1)[0],
    ),
]

marketplace_source = Path("app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
app_source = Path("app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")
hero_source = marketplace_source.split("private fun MarketplaceSmartDealsHero", 1)[1].split("@Composable\nfun FynxMarketplacePanel", 1)[0]
checks.extend([
    (
        "Marketplace secondary controls fold away on real user scroll while search/cart stay available",
        "NestedScrollSource.UserInput" in marketplace_source
        and ".nestedScroll(marketplaceScrollConnection)" in marketplace_source
        and "marketplaceChromeHidden" in marketplace_source
        and "visible = !marketplaceChromeHidden" in marketplace_source
        and "MarketplaceReferenceSearchBar(" in marketplace_source,
    ),
    (
        "Marketplace app title and floating navigation share scroll-away behavior",
        'selected != "Home" && selected != "Marketplace"' in app_source
        and 'if (selected == "Marketplace") {' in app_source
        and '.height(52.dp * (1f - homeChromeProgress))' in app_source
        and ('visible = (selected != "Home" && selected != "Marketplace") || !homeChromeHidden' in app_source or 'visible = (selected != "Home" && selected != "Marketplace") || statusCreatorRequested || !homeChromeHidden' in app_source),
    ),
    (
        "Great Deals uses the Home silver smoky mirror-glass palette with transparent card surface",
        "dealsGlassBrush" in hero_source
        and "dealsGlassBorder" in hero_source
        and "luminance() < 0.5f" in hero_source
        and "Color(0xFFE0E6ED).copy(alpha = 0.68f)" in hero_source
        and "containerColor = Color.Transparent" in hero_source
        and "BorderStroke(1.dp, dealsGlassBorder)" in hero_source,
    ),
])


guard_source = Path(__file__).read_text(encoding="utf-8")
checks.append((
    "Python regression verifier uses Python string APIs for runtime-order checks",
    ("runner." + "indexOf(") not in guard_source and "runner.index(" in guard_source,
))

failed = 0
for name, passed in checks:
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
    failed += not passed
print(f"Marketplace visual-capture contract: {'GREEN' if not failed else 'RED'} ({len(checks)-failed}/{len(checks)})")
sys.exit(1 if failed else 0)
