from pathlib import Path

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

print("MORE TOOLS SOURCE CONTRACT GREEN: navigation, local persistence, validation, calls, and money safety disclosures")
