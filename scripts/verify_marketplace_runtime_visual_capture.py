#!/usr/bin/env python3
"""Guard real Marketplace/My Shop visual evidence capture and failure preservation."""
from pathlib import Path
import sys

runner = Path("scripts/run_android_runtime_certification.sh").read_text(encoding="utf-8")
navigation = Path("scripts/verify_authenticated_runtime_navigation.py").read_text(encoding="utf-8")
workflow = Path(".github/workflows/android-build.yml").read_text(encoding="utf-8")

checks = [
    (
        "Android CI attempts focused Marketplace/My Shop capture after instrumentation failure",
        'FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py' in runner
        and 'else\n  # A red instrumentation test must not suppress Marketplace/My Shop screenshot evidence.' in runner,
    ),
    (
        "visual capture failure cannot turn a red instrumentation run green",
        'if [ "$VISUAL_EXIT" -ne 0 ]; then' in runner and 'exit "$TEST_EXIT"' in runner,
    ),
    (
        "green instrumentation selects authenticated navigation only when backend health is ready",
        'if node --input-type=module -e' in runner
        and 'body?.database !== "ready"' in runner
        and 'FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py' in runner
        and 'Live backend is unavailable; running read-only Marketplace/My Shop visual certification' in runner
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
        "real Marketplace screenshot and UI hierarchy are captured after destination marker verification",
        'tap_control(\n        xml, ["Marketplace"], "marketplace", ["Search products, sellers or categories"]' in navigation
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
        "product details require an actual clickable card carrying a displayed NGN/USD price",
        "def marketplace_product_card_target(xml_text: str):" in navigation
        and "NGN" in navigation and "USD" in navigation
        and "no visible real listing card" in navigation,
    ),
    (
        "product-publishing form is captured without submitting a listing",
        '["Sell something", "Start selling", "Sell your first product"]' in navigation
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
        and "contains(github.event.head_commit.message, '[home-cert]')" in workflow,
    ),
]

failed = 0
for name, passed in checks:
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
    failed += not passed
print(f"Marketplace visual-capture contract: {'GREEN' if not failed else 'RED'} ({len(checks)-failed}/{len(checks)})")
sys.exit(1 if failed else 0)
