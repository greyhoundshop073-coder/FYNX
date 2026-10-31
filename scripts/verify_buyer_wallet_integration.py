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
actions = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxWalletActionPanels.kt").read_text()
seller_center = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt").read_text()
transactions = (ROOT / "backend/marketplaceTransactions.js").read_text()
runtime = (ROOT / "scripts/verify_authenticated_runtime_navigation.py").read_text()
admin_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt").read_text()
admin_panel = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt").read_text()
admin_routes = (ROOT / "backend/adminRoutes.js").read_text()
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
 ("real-money funding, spending and withdrawals remain disabled until provider integration", "canFund: false" in wallet_backend and "canSpend: false" in wallet_backend and "canWithdraw: false" in wallet_backend and 'enabled = false' in actions),
 ("existing Marketplace Paystack checkout retained", "initializeMarketplacePayment" in payments and "verifyMarketplacePayment" in payments),
 ("NGN-only launch foundation", 'currency == "NGN"' in client and "currently supports the NGN foundation only" in panel),
 ("Add Money opens a dedicated funding-method screen", 'onClick = { activeAction = "ADD" }' in panel and 'screen = activeAction' in panel and 'FynxWalletActionPanel' in panel and '"ADD" ->' in actions),
 ("wallet Add Money, bank-transfer and Withdraw screens are captured by emulator runtime", 'wallet-add-money' in runtime and 'wallet-bank-transfer' in runtime and 'wallet-withdraw' in runtime),
 ("dedicated bank transfer never invents account details", '"Account number not assigned yet"' in actions and 'Do not transfer money to an account number supplied outside this FYNX screen.' in actions and "0000000000" not in actions),
 ("bank transfer explains automatic verified wallet credit", '"The provider confirms the payment to FYNX."' in actions and '"FYNX verifies the confirmation and credits your wallet."' in actions),
 ("card and USSD methods have explicit provider-not-ready states", '"CARD" ->' in actions and '"USSD" ->' in actions and '"Card funding is not active yet"' in actions and '"USSD payments are not active yet"' in actions),
 ("withdraw screen previews cleared balance and explains secure bank verification", 'onClick = { activeAction = "WITHDRAW" }' in panel and '"AVAILABLE BALANCE"' in actions and '"FYNX verifies the account name with the provider."' in actions),
 ("withdraw screen reserves bank, account number and verified account-name fields", 'label = "Select bank"' in actions and 'label = "Account number"' in actions and 'label = "Verified account name"' in actions and 'enabled = false' in actions),
 ("withdrawal submission remains disabled without provider integration", '"Withdrawals not available yet"' in actions and 'enabled = false' in actions),
 ("seller earnings explicitly disclose default five-percent Marketplace commission", '"FYNX Marketplace commission · default 5%"' in seller_center and 'record.marketplaceFee' in seller_center),
 ("Marketplace commission is not charged on wallet top-ups or withdrawals", 'wallet top-ups and bank withdrawals are not charged this fee' in seller_center),
 ("default Marketplace fee policy is seller-paid at five percent", "process.env.FYNX_MARKETPLACE_FEE_MODE || 'SELLER'" in transactions and "process.env.FYNX_MARKETPLACE_FEE_BPS || '500'" in transactions and 'const mode = configuredMode;' in transactions),
 ("FYNX revenue report uses the admin-only revenue endpoint", '"/api/admin/revenue"' in admin_client and "if(!(await requireAdmin(req,res)))return" in admin_routes),
 ("owner/admin control center shows separate Marketplace commission totals", '"FYNX Business Revenue"' in admin_panel and '"Marketplace commission ledger"' in admin_panel and 'it.source == "MARKETPLACE_FEE"' in admin_panel),
 ("company commission is explicitly separate from personal wallet balances", "tracked separately from customer wallet balances and seller payouts" in admin_panel)
]
failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(("PASS" if ok else "FAIL")+": "+name)
if failed: raise SystemExit("FYNX buyer wallet Android integration failed: "+", ".join(failed))
print("FYNX buyer wallet Android integration GREEN ("+str(len(checks))+" checks)")
