from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.is_file():
        raise SystemExit(f"MORE TOOLS RED: missing source file {relative_path}")
    return path.read_text(encoding="utf-8")


def require(source: str, expected: str, label: str) -> None:
    if expected not in source:
        raise SystemExit(f"MORE TOOLS RED: missing {label}: {expected}")


app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")
require(app, 'var navigationHistory by remember { mutableStateOf(listOf("Home")) }', "secondary navigation history")
require(app, "navigationHistory = (navigationHistory + selected).takeLast(30)", "bounded navigation history")
require(app, 'navigationHistory = (navigationHistory.dropLast(1).filterNot { it == selected } + selected).ifEmpty { listOf("Home") }', "safe contextual navigation stack pop")
require(app, 'contentDescription = "Back to ${fynxBackDestination(selected, notificationPreferencesOnly, navigationHistory)}"', "visible secondary-screen back action names its contextual parent")

extra = read("app/src/main/java/com/fynx/app/ui/FynxExtraToolsPanel.kt")
require(extra, "FynxAuthStore.accountStorageKey(context)", "account-scoped Quick Notes")
require(extra, "verticalScroll(rememberScrollState())", "scrollable Extra Tools screen")
require(extra, "notes.filterNot { it.id == note.id }", "Quick Notes deletion affordance")

calendar = read("app/src/main/java/com/fynx/app/ui/CalendarPanel.kt")
require(calendar, 'SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }', "strict calendar date validation")
require(calendar, 'SimpleDateFormat("HH:mm", Locale.US).apply { isLenient = false }', "strict calendar time validation")

todo = read("app/src/main/java/com/fynx/app/ui/TodoPanel.kt")
require(todo, "val overdueCount = todos.count", "visible overdue task accounting")

calls = read("app/src/main/java/com/fynx/app/ui/FynxCallsPanel.kt")
require(calls, 'onClick = { beginOutgoing(newCallUsername, false) }', "direct voice call action")
require(calls, 'onClick = { beginOutgoing(newCallUsername, true) }', "direct video call action")

store = read("app/src/main/java/com/fynx/app/ui/FynxMoneyStore.kt")
require(store, "fun isValidLocalDate(value: String): Boolean", "strict ISO date validation for local money records")
require(store, "fun loadToolRecords(context: Context, tool: String): String", "local tool record loading")
require(store, "fun saveToolRecords(context: Context, tool: String, rawJson: String)", "local tool record saving")
require(store, "FynxAuthStore.accountStorageKey(context)", "account-scoped money storage")

budget = read("app/src/main/java/com/fynx/app/ui/BudgetPlannerPanel.kt")
require(budget, 'put("period", item.period)', "persisted budget period")
require(budget, 'val visibleBudgets = budgets.filter { it.period == period }', "period-correct budget totals")
require(budget, 'Text("Delete budget")', "budget record deletion")

for relative_path in ("BillsPaymentPanel.kt", "SavingsGoalsPanel.kt", "SubscriptionsPanel.kt", "ReceiptsExpensePanel.kt"):
    source = read(f"app/src/main/java/com/fynx/app/ui/{relative_path}")
    require(source, "FynxMoneyStore.isValidLocalDate(", f"{relative_path} rejects invalid dates")
    require(source, "Use a valid date in YYYY-MM-DD format.", f"{relative_path} explains date validation errors")

for relative_path, tool_key in (
    ("BillsPaymentPanel.kt", "bills"),
    ("BudgetPlannerPanel.kt", "budgets"),
    ("SavingsGoalsPanel.kt", "savings_goals"),
    ("SubscriptionsPanel.kt", "subscriptions"),
    ("ReceiptsExpensePanel.kt", "receipts"),
):
    source = read(f"app/src/main/java/com/fynx/app/ui/{relative_path}")
    require(source, f'FynxMoneyStore.loadToolRecords(context, "{tool_key}")', f"{tool_key} persistence read")
    require(source, f'FynxMoneyStore.saveToolRecords(context, "{tool_key}", array.toString())', f"{tool_key} persistence write")

money_dashboard = read("app/src/main/java/com/fynx/app/ui/MoneyToolsPanel.kt")
require(money_dashboard, "Transaction totals are shown separately to avoid double-counting.", "non-double-counted balance explanation")
require(money_dashboard, "does not move real money", "local-only transaction disclaimer")

vault = read("app/src/main/java/com/fynx/app/ui/SecureMoneyVaultPanel.kt")
require(vault, "Local Money Lock (Demo)", "honest vault demo label")
require(vault, "does not encrypt or protect your financial records", "vault security limitation disclosure")

money_center = read("app/src/main/java/com/fynx/app/ui/MoneyCenterPanel.kt")
require(money_center, "BackHandler", "Money Center tool back handling")
require(money_center, "SpendingInsightsPanel", "Spending Insights entry point")


# More / Features directory: two-column, lazy, local-only registry and role-gated Owner card.
features = read("app/src/main/java/com/fynx/app/ui/FynxFeaturesPanel.kt")
require(features, "LazyVerticalGrid(", "lazy feature grid")
require(features, "columns = GridCells.Fixed(2)", "two-column feature layout")
require(features, "rememberLazyGridState()", "remembered grid scroll state")
require(features, 'key = { it.route }', "stable feature identity")
require(features, 'contentType = { "feature-card" }', "lazy grid content type")
require(features, 'adminRole?.trim()?.uppercase()', "normalized server-provided admin role")
require(features, '?.takeIf { it == "OWNER" || it == "ADMIN" }', "Owner/Admin-only card eligibility")
require(features, 'authorizedRole', "role label on protected feature card")
require(features, 'remember(sections, normalizedQuery)', "local memoized feature search")
require(features, 'GridCells.Fixed(2)', "two-column layout")
if "FynxBackendClient" in features or "FynxAdminClient" in features:
    raise SystemExit("MORE TOOLS RED: the feature directory must not perform network or role lookups during composition")

routes = re.findall(r'FynxFeatureCardSpec\(\s*"([^"]+)"', features)
expected_routes = [
    "Calls", "Gifts", "Share", "Announcements",
    "To-Do", "Calendar", "Extra Tools", "Saved Posts",
    "Wallet", "Money Tools",
    "Business Account", "Advertising", "Advertising Dashboard",
    "AI", "Admin",
]
if routes != expected_routes:
    raise SystemExit(f"MORE TOOLS RED: unexpected feature registry/order: {routes}")
if len(routes) != len(set(routes)):
    raise SystemExit("MORE TOOLS RED: duplicate feature routes in More directory")

# These shortcuts duplicate a permanent tab, Profile > Settings, or Marketplace. Notifications is not repeated because Home already opens the activity feed and Profile settings provides notification preferences.
# Keep their real destinations; do not offer another copy in More.
for duplicate_route in ("Marketplace", "Notifications", "Privacy", "Saved Products", "Seller Center"):
    if re.search(r'FynxFeatureCardSpec\(\s*"' + re.escape(duplicate_route) + r'"', features):
        raise SystemExit(f"MORE TOOLS RED: redundant {duplicate_route} shortcut reintroduced")

icon_colors = set(re.findall(r'Color\(0xFF[0-9A-Fa-f]{6}\)', features))
if len(icon_colors) < 15:
    raise SystemExit(f"MORE TOOLS RED: expected distinct icon colors for all 14 user cards and Owner/Admin; found {len(icon_colors)}")

require(app, 'FynxFeaturesPanel(adminRole = adminRole', "More screen receives server-authoritative Owner/Admin role")
require(app, 'dashboard.role.takeIf { it == "OWNER" || it == "ADMIN" }', "server role continues to gate Owner/Admin visibility")
require(app, '"Admin" -> if (adminRole != null) FynxAdminControlCenterPanel()', "Admin destination remains role-gated")

extra = read("app/src/main/java/com/fynx/app/ui/FynxExtraToolsPanel.kt")
require(extra, "fun FynxExtraToolsPanel()", "Extra Tools remains accessible without duplicate Calendar callback")
require(extra, 'Text("About FYNX"', "FYNX help guides remain available")
if "onOpenCalendar" in extra or 'Text("Events"' in extra:
    raise SystemExit("MORE TOOLS RED: duplicate Events/Calendar shortcut remains inside Extra Tools")

more_test = read("app/src/androidTest/java/com/fynx/app/ui/FynxFeaturesPanelComposeTest.kt")
for expected in (
    "ordinaryAccountSeesEveryDistinctFeatureButNoOwnerOrDuplicateShortcuts",
    "ownerGetsOwnerControlCenterCardAndOwnerLabel",
    "adminGetsAdminControlCenterCardButNotOwnerLabel",
    "unknownRoleCannotSeeOwnerOrAdminCard",
    "firstFeatureRowUsesTwoColumnsAndCardsNavigateToTheirExistingRoutes",
    "searchFiltersLocallyToMatchingFeatures",
):
    require(more_test, expected, f"More feature UI test {expected}")

print("MORE TOOLS SOURCE CONTRACT GREEN: navigation, two-column directory, deduplication, role gating, local persistence, validation and calls")
