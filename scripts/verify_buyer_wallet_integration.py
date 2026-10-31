from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
app = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxApp.kt").read_text()
money = (ROOT / "app/src/main/java/com/fynx/app/ui/MoneyCenterPanel.kt").read_text()
client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBuyerWalletClient.kt").read_text()
panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxBuyerWalletPanel.kt").read_text()
market = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt").read_text()
payments = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplacePayments.kt").read_text()
settlement = (ROOT / "backend/marketplaceSettlement.js").read_text()
wallet_backend = (ROOT / "backend/buyerWallet.js").read_text()
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text()
workflow = (ROOT / ".github/workflows/android-build.yml").read_text()
checks = [
 ("wallet direct Features entry", '"Wallet" -> key(marketplaceAccountKey) { FynxBuyerWalletPanel() }' in app and 'Triple("Wallet", "FYNX Wallet"' in app),
 ("Marketplace Wallet shortcut", 'onOpenWallet: () -> Unit = {}' in market and 'OutlinedButton(onClick = onOpenWallet' in market and 'onOpenWallet = { selected = "Wallet" }' in app),
 ("wallet Money Center entry", 'MoneySection("Wallet", "FYNX Wallet"' in money and '"Wallet" -> FynxBuyerWalletPanel()' in money),
 ("emulator waits for wallet summary and activity loading to settle before capture", "capture_wallet_from_features" in runtime and "wallet_still_loading" in runtime and "range(60)" in runtime and "wallet_settled=True" in runtime and "after summary and activity loading settled" in runtime),
 ("wallet result selector excludes the EditText query and chooses a lower result card", 'node.attrib.get("class") == "android.widget.EditText"' in runtime and "search_bottom=bottom" in runtime and "top >= minimum_card_top" in runtime),
 ("authenticated feature navigation selector skips search EditText for Wallet", "def find_feature_entry" in runtime and "below_search=search_bottom is None or top >= search_bottom" in runtime and "search_name=\"wallet\"" in runtime),
 ("wallet runtime dismisses keyboard and restores Home before next journey", '"KEYCODE_BACK"' in runtime and 'restore_home_after_restart(prefix+"-return-home")' in runtime and "home_after=restore_home_after_restart" in runtime),
 ("wallet UI hierarchy artifacts end with a real newline", "write_text(wallet_xml+chr(10)" in runtime and "(wallet_xml or \"\")+chr(10)" in runtime),
 ("visual-only navigation also captures Wallet read-only", 'capture_wallet_from_features("marketplace-visual-wallet")' in runtime and "no listing/order/payment mutations" in runtime),
 ("wallet branch receives full emulator and screenshot certification", workflow.count("refs/heads/feat/fynx-wallet-ux-hardening-20261031") == 4),
 ("wallet back navigation", '"Wallet", "Money Tools" -> "Features"' in app),
 ("wallet authenticated summary and activity APIs", '"/api/wallet/summary"' in client and '"/api/wallet/transactions?limit="' in client),
 ("no locally fabricated balance", "SharedPreferences" not in client and "125,500" not in panel and "100,000" not in panel),
 ("Azure wallet card with white text", "FynxDesign.AzureBlue" in panel and "contentColor = Color.White" in panel and "Color.White" in panel),
 ("hide/show balance control", "balanceVisible" in panel and "Hide balance" in panel and "Show balance" in panel),
 ("reserved and withdrawal-pending balance separated", "reservedBalance" in client and "withdrawalPendingBalance" in client and '"Reserved"' in panel and '"Withdrawal pending"' in panel),
 ("confirmed incoming money uses Classic Green", "FynxDesign.ClassicGreen" in panel and "transaction.walletDelta > 0.0" in panel and 'transaction.status == "SUCCEEDED"' in panel),
 ("summary and activity use independent loading states", "summaryLoading" in panel and "activityLoading" in panel and "Checking your wallet" in panel and "Loading wallet activity" in panel),
 ("preparation notice never appears while summary is loading", "if (!summaryLoading && !isWalletReady && summaryError == null)" in panel and "summaryError != null -> \"Unavailable\"" in panel),
 ("loading, retry and empty states", "Loading wallet activity" in panel and "refreshVersion += 1" in panel and "No wallet activity yet" in panel),
 ("summary and activity failures are distinct", "summaryError" in panel and "activityError" in panel and "Wallet unavailable" in panel and "Activity could not load" in panel),
 ("failed history never appears as a successful empty state", 'visibleActivity.isEmpty() && !loading && summaryError == null && activityError == null' in panel),
 ("activity displays readable date and transaction reference", "fynxWalletActivityDate(transaction.createdAt)" in panel and "transaction.reference" in panel),
 ("activity can be filtered by money direction", 'activityFilter == "IN"' in panel and 'activityFilter == "OUT"' in panel and "FilterChip" in panel),
 ("activity filters apply to complete server history", "LaunchedEffect(refreshVersion, activityFilter)" in panel and "direction = activityFilter" in panel and "&direction=" in client and "$3::text='IN'" in wallet_backend and "$3::text='OUT'" in wallet_backend),
 ("load more preserves the selected server-side direction", "before = cursor, direction = activityFilter" in panel),
 ("activity rows open transaction detail and copy reference", "selectedActivity" in panel and "Transaction reference copied" in panel and "FynxWalletActivityRow(transaction, amountsVisible = balanceVisible)" in panel),
 ("activity history supports cursor-based load more", "loadMoreActivity()" in panel and "Load more activity" in panel and "nextActivityCursor" in panel and "beforeCreatedAt" in client and "nextCursor" in wallet_backend),
 ("stale pagination requests cannot clear a newer loading state", "loadingMoreActivity = false" in panel and "requestGeneration == activityRequestGeneration" in panel and "A request from an older filter/refresh must not clear the current request's spinner." in panel),
 ("pagination preserves database timestamp precision", "created_at_iso" in wallet_backend and "lastRow.created_at_iso" in wallet_backend and "US" in wallet_backend and "createdAt, id: id.toLowerCase()" in wallet_backend and "parsedDate.toISOString()" not in wallet_backend),
 ("balance visibility masks amounts across the screen", "amountsVisible = balanceVisible" in panel and "if (visible) amount?.let(::fynxWalletMoney)" in panel and "if (amountsVisible) sign + fynxWalletMoney" in panel),
 ("balance buckets explain withdrawal eligibility", "Held for an order or an active review." in panel and "How bank withdrawals will work" in panel and "no money moves from this screen today" in panel),
 ("wallet status has user-readable state labels", "Wallet access is restricted" in panel and "This wallet is closed" in panel),
 ("funding and withdrawals remain disabled until provider integration", "isWalletReady" in panel and "canFund: false" in wallet_backend and "canWithdraw: false" in wallet_backend),
 ("existing Marketplace Paystack checkout retained", "initializeMarketplacePayment" in payments and "verifyMarketplacePayment" in payments),
 ("NGN-only launch foundation", 'currency == "NGN"' in client and "currently supports the NGN foundation only" in panel)
]
failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(("PASS" if ok else "FAIL")+": "+name)
if failed: raise SystemExit("FYNX buyer wallet Android integration failed: "+", ".join(failed))
print("FYNX buyer wallet Android integration GREEN ("+str(len(checks))+" checks)")
