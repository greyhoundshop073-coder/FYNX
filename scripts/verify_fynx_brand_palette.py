#!/usr/bin/env python3
"""Regression gate for the approved FYNX brand palette and appearance choices."""
from pathlib import Path


def relative_luminance(hex_color: str) -> float:
    """Calculate WCAG 2.x relative luminance for a #RRGGBB color."""
    channels = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4 for channel in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast_ratio(first: str, second: str) -> float:
    """Return the WCAG contrast ratio between two #RRGGBB colors."""
    light, dark = sorted((relative_luminance(first), relative_luminance(second)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


ROOT = Path(__file__).resolve().parents[1]
design = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxDesignSystem.kt").read_text(encoding="utf-8")
picker = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxChatWallpaper.kt").read_text(encoding="utf-8")
guidelines = (ROOT / "docs/FYNX_MASTER_UI_GUIDELINES.md").read_text(encoding="utf-8")
marketplace_components = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReferenceComponents.kt").read_text(encoding="utf-8")
marketplace_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text(encoding="utf-8")
my_shop = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt").read_text(encoding="utf-8")
marketplace_seller_orders = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt").read_text(encoding="utf-8")
marketplace_reviews = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceReviews.kt").read_text(encoding="utf-8")
marketplace_checkout = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceCheckout.kt").read_text(encoding="utf-8")
marketplace_lifecycle = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt").read_text(encoding="utf-8")
marketplace_store_dialog = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerStoreDialog.kt").read_text(encoding="utf-8")
app_shell = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text(encoding="utf-8")

checks = [
    ("Marketplace exposes a local cobalt selected-chip palette for light and dark surfaces", "internal fun fynxMarketplaceSelectedChipColors()" in marketplace_components and "MaterialTheme.colorScheme.background.luminance() > 0.5f" in marketplace_components and "selectedContainerColor = if (isLight) Color(0xFFE4ECF8) else Color(0xFF17365D)" in marketplace_components and "selectedLabelColor = if (isLight) Color(0xFF0047AB) else Color(0xFFEAF1FF)" in marketplace_components and marketplace_components.count("colors = selectedChipColors") >= 2 and "MarketplaceReferenceCategoryRow(" in marketplace_panel),
    ("My Shop navigation and seller controls reuse the Marketplace cobalt selected palette", "val selectedChipColors = fynxMarketplaceSelectedChipColors()" in my_shop and my_shop.count("colors = selectedChipColors") >= 3 and "selected = activeSection == section" in my_shop and "selected = editCondition == condition" in my_shop and "selected = payoutCurrency == currency" in my_shop),
    ("seller delivery mode chips use shared Azure selected colors", marketplace_seller_orders.count("colors = fynxMarketplaceSelectedChipColors()") >= 2 and 'selected = driverMode == "FYNX_ACCOUNT"' in marketplace_seller_orders and 'selected = driverMode == "SELLER_LOCAL"' in marketplace_seller_orders),
    ("seller review rating choices use shared Azure selected colors", "colors = fynxMarketplaceSelectedChipColors()" in marketplace_reviews and "selected = rating == value" in marketplace_reviews),
    ("checkout delivery/pickup selections use shared Azure selected colors without changing quote invalidation", marketplace_checkout.count("colors = fynxMarketplaceSelectedChipColors()") >= 2 and "invalidateQuote()" in marketplace_checkout),
    ("order delivery/pickup and dispute reasons use shared Azure selected colors", marketplace_lifecycle.count("colors = fynxMarketplaceSelectedChipColors()") >= 3 and "disputeReason == code" in marketplace_lifecycle),
    ("public storefront Products/About tabs use shared Azure selected colors", "colors = fynxMarketplaceSelectedChipColors()" in marketplace_store_dialog and "selected = tab == label" in marketplace_store_dialog),
    ("active bottom navigation uses the Azure selected container and contrast-safe on-container foreground", "indicatorColor = MaterialTheme.colorScheme.primaryContainer" in app_shell and "indicatorColor = MaterialTheme.colorScheme.secondaryContainer" not in app_shell and "selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer" in app_shell and "selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer" in app_shell),
    ("Marketplace reference links and action icons use the shared Material primary token, without a competing local primary token", "Text(action, color = MaterialTheme.colorScheme.primary)" in marketplace_components and "tint = MaterialTheme.colorScheme.primary" in marketplace_components and "val primary =" not in marketplace_components),
    ("default blue accent uses the Azure Blue token and preserves the existing companion and Classic Green", "Blue(FynxDesign.AzureBlue, Color(0xFF3C73BD), FynxDesign.ClassicGreen)" in design),
    ("Marketplace sell-form category, condition, and currency use the cobalt selection palette", "val selectedChipColors = fynxMarketplaceSelectedChipColors()" in marketplace_panel and all(fragment in marketplace_panel for fragment in ("colors = selectedChipColors", "FilterChip(selected = category == item", "FilterChip(selected = condition == value", "FilterChip(selected = currency == code"))),
    ("Marketplace light/dark cobalt selected labels meet WCAG AA contrast", contrast_ratio("#E4ECF8", "#0047AB") >= 4.5 and contrast_ratio("#17365D", "#EAF1FF") >= 4.5),
    ("Azure Blue token is the exact user-approved primary blue", "val AzureBlue = Color(0xFF007FFF)" in design),
    ("legacy Cobalt Blue is not still used as the default primary", "Blue(Color(0xFF0047AB)" not in design),
    ("primary foreground selection uses the WCAG black/white crossover", "effectiveAccent.primary.luminance() > 0.179f" in design and "onPrimary = onAccent" in design),
    ("black foreground on Azure Blue meets WCAG AA for normal text",
     contrast_ratio("#007FFF", "#000000") >= 4.5 and contrast_ratio("#007FFF", "#000000") > contrast_ratio("#007FFF", "#FFFFFF")),
    ("light-mode selected pale-blue container has WCAG AA text contrast", contrast_ratio("#E8EEFA", "#17202A") >= 4.5),
    ("dark-mode blue selected container has WCAG AA text contrast", contrast_ratio("#132B49", "#F5F8FF") >= 4.5),
    ("Charcoal and AMOLED selected containers retain WCAG AA text contrast", contrast_ratio("#37434B", "#F2F5F7") >= 4.5 and contrast_ratio("#1C1C1C", "#FFFFFF") >= 4.5),
    ("success token is the exact Classic Green", "val ClassicGreen = Color(0xFF008000)" in design),
    ("brand error token is the exact Crimson Red", "val CrimsonRed = Color(0xFFDC143C)" in design),
    ("dark mode uses a contrast-safe red for error text", "error = FynxDesign.DarkModeError" in design and "val DarkModeError = Color(0xFFFF7086)" in design),
    ("light mode uses the exact Crimson Red for error text", "error = FynxDesign.CrimsonRed" in design),
    ("dark-mode error foreground uses a contrast-safe dark color", "error = FynxDesign.DarkModeError" in design and "onError = FynxDesign.Background" in design),
    ("light-mode error foreground stays white on the approved Crimson Red", "error = FynxDesign.CrimsonRed" in design and "onError = Color.White" in design),
    ("accent picker labels the selected blue Azure Blue", 'FynxAccent.Blue -> "Azure Blue"' in picker),
    ("Light, Dark, and System appearance choices are preserved",
     all(f'Triple("{mode}"' in picker for mode in ("System", "Light", "Dark"))),
    ("master UI guidelines document the approved three-color palette",
     all(token in guidelines for token in ("#007FFF", "#008000", "#DC143C"))),

    ("Soft Cloud White token is exact and wired to light background",
     "val SoftCloudWhite = Color(0xFFF5F7FA)" in design and "val LightBackground = SoftCloudWhite" in design and "background = FynxDesign.LightBackground" in design),
    ("Midnight Slate token is exact and wired to raised surfaces",
     "val MidnightSlate = Color(0xFF1C2434)" in design and "val SurfaceRaised = MidnightSlate" in design),
    ("Slate Grey token is exact and wired to secondary light-theme text",
     "val SlateGrey = Color(0xFF64748B)" in design and "val LightTextSecondary = SlateGrey" in design and "onSurfaceVariant = FynxDesign.LightTextSecondary" in design),
    ("Premium Gold is a named, exact semantic token",
     "val PremiumGold = Color(0xFFD4AF37)" in design),
    ("Pale Cobalt is wired into the actual light-mode selection container",
     "val PaleCobalt = Color(0xFFE8EEFA)" in design and "val LightSelectedContainer = PaleCobalt" in design and "FynxAccent.Blue -> FynxDesign.LightSelectedContainer" in design and "primaryContainer = when (effectiveAccent)" in design),
    ("light/dark/Charcoal/AMOLED modes explicitly define readable selection containers",
     "primaryContainer = when {" in design and "FynxDesign.AmoledSelectedContainer" in design and "FynxDesign.CharcoalSelectedContainer" in design and "onPrimaryContainer = FynxDesign.LightTextPrimary" in design),
    ("light theme uses shared neutral tokens rather than duplicate hard-coded colors",
     "background = FynxDesign.LightBackground" in design and "surface = FynxDesign.LightSurface" in design and "onSurface = FynxDesign.LightTextPrimary" in design),
    ("supporting palette usage rules are documented",
     all(token in guidelines for token in ("#F5F7FA", "#1C2434", "#64748B", "#D4AF37", "#E8EEFA"))),
]
for name, ok in checks:
    print(("PASS" if ok else "FAIL") + ": " + name)
failed = [name for name, ok in checks if not ok]
if failed:
    raise SystemExit(f"{len(failed)} FYNX palette check(s) failed")
print(f"FYNX approved brand palette: {len(checks)} checks passed")
