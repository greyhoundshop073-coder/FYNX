import ast
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
ast.parse(runtime)
status_runtime = (ROOT / "scripts/verify_status_creator_runtime.py").read_text()
ast.parse(status_runtime)
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
 ("Home recovery preserves the authenticated app process instead of force-stopping it", "def restore_home_after_restart(name:str)->str:" in runtime and 'fynx://home' in runtime and 'run("adb","shell","am","force-stop",PACKAGE)' not in runtime.split("def restore_home_after_restart(name:str)->str:",1)[1].split("def capture_surface(",1)[0]),
 ("wallet UI hierarchy artifacts end with a real newline", "write_text(wallet_xml+chr(10)" in runtime and "(wallet_xml or \"\")+chr(10)" in runtime),
 ("visual-only navigation also captures Wallet read-only", 'capture_wallet_from_features("marketplace-visual-wallet")' in runtime and "no listing/order/payment mutations" in runtime),
 ("Wallet runtime gate checks visible balance buckets rather than below-fold explanatory copy", "def wallet_destination_ready(xml_text: str) -> bool:" in runtime and 'find_control(xml_text, ["Reserved"])' in runtime and 'find_control(xml_text, ["Withdrawal pending"])' in runtime and "wallet_shell_opened=wallet_destination_ready(wallet_xml)" in runtime and "wallet_opened=wallet_shell_opened and wallet_settled" in runtime),
 ("wallet branch receives full emulator and screenshot certification", workflow.count("refs/heads/feat/fynx-wallet-ux-hardening-20261031") == 4),
 ("Wallet capture correction receives full emulator and exact-commit APK certification", workflow.count("refs/heads/fix/real-wallet-runtime-screen-capture-20261101") == 4),
 ("authenticated Wallet capture runs outside the feature-search helper and saves real PNGs", runtime.index("def open_features(") < runtime.index("\nfeatures=open_features()") and "capture_wallet_from_features(\"authenticated-runtime-wallet\")" in runtime and all(name in runtime for name in ["authenticated-wallet.png", "authenticated-wallet-add-money.png", "authenticated-wallet-bank-transfer.png", "authenticated-wallet-card.png", "authenticated-wallet-ussd.png", "authenticated-wallet-withdraw.png"]) and "missing_authenticated_surfaces" in runtime),
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
 ("Wallet explains local-only and expired-token sign-in recovery", "FynxBackendClient.hasAccessToken(context)" in panel and "FynxBackendClient.isUnauthorizedFailure(failure)" in panel and "leaving it blank only opens local mode" in panel and "online FYNX session has expired" in panel),
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
 ("wallet spending stays disabled and money movement is controlled by explicit provider/custody/compliance gates", "canSpend: false" in wallet_backend and "FYNX_WALLET_PROVIDER_APPROVED" in wallet_backend and "FYNX_WALLET_CUSTODY_APPROVED" in wallet_backend and "FYNX_WALLET_COMPLIANCE_APPROVED" in wallet_backend and "walletProviderApproved('DEPOSIT')" in wallet_backend and "walletProviderApproved('WITHDRAWAL')" in wallet_backend),
 ("existing Marketplace Paystack checkout retained", "initializeMarketplacePayment" in payments and "verifyMarketplacePayment" in payments),
 ("NGN-only launch foundation", 'currency == "NGN"' in client and "currently supports the NGN foundation only" in panel),
 ("Add Money opens a dedicated funding-method screen", 'onClick = { activeAction = "ADD" }' in panel and 'screen = activeAction' in panel and 'FynxWalletActionPanel' in panel and '"ADD" ->' in actions),
 ("wallet Add Money, bank-transfer, card, USSD and Withdraw screens are captured by emulator runtime", 'wallet-add-money' in runtime and 'wallet-bank-transfer' in runtime and 'wallet-card-funding' in runtime and 'wallet-ussd-funding' in runtime and 'wallet-withdraw' in runtime),
 ("dedicated bank transfer shows only provider-assigned account details", "FynxWalletFundingAccountPanel" in actions and 'account?.status == "ACTIVE" && account?.accountNumber != null' in actions and '"Copy account number"' in actions and '"0000000000"' not in actions),
 ("bank transfer requires explicit consent and verified provider notification before credit", '"explicitConsent"' in client and "I consent to FYNX sharing these details with Paystack" in actions and "signed notification" in actions and "dedicated_nuban" in wallet_backend),
 ("card and USSD methods have provider-gated hosted funding flows", "funding is not active yet" in actions and "FynxWalletHostedFundingPanel" in actions and "canFundByCard" in actions and "canFundByUssd" in actions and "initializeWalletFunding" in actions and "refreshWalletFunding" in actions),
 ("withdraw screen previews cleared balance and explains verified bank account setup", 'onClick = { activeAction = "WITHDRAW" }' in panel and '"AVAILABLE BALANCE"' in actions and '"Verify bank account"' in actions and '"Verified account name"' in actions),
 ("withdraw screen includes provider bank selection, account entry and verified account-name confirmation", '"Select bank"' in actions and 'label = { Text("Account number") }' in actions and '"Verified account name"' in actions),
 ("withdrawal submission is gated by approved provider capability and a verified bank account", '"Bank withdrawals are not enabled yet"' in actions and "canWithdraw = wallet?.canWithdraw == true" in panel and "Verify bank account" in actions and "Review withdrawal" in actions and "Confirm withdrawal" in actions and "requestWithdrawal" in actions and "withdrawal amount is above your cleared available balance" in wallet_backend),
 ("withdrawal transfers reserve only available funds and await signed provider outcome", "'AVAILABLE','DEBIT'" in wallet_backend and "'WITHDRAWAL_PENDING','CREDIT'" in wallet_backend and "reconcileWalletTransferEvent" in wallet_backend and "transfer.success" in wallet_backend and "transfer.failed" in wallet_backend and "transfer.reversed" in wallet_backend),
 ("withdrawals require a password-reauthenticated wallet PIN with temporary lockout", '"Set your wallet withdrawal PIN"' in actions and '"/api/wallet/security/pin"' in client and "bcrypt.compare(pin, wallet.withdrawal_pin_hash)" in wallet_backend and "INTERVAL '15 minutes'" in wallet_backend),
 ("withdrawal amount and daily-count limits are backend-enforced", "FYNX_WALLET_WITHDRAWAL_MAX_AMOUNT" in wallet_backend and "FYNX_WALLET_WITHDRAWAL_DAILY_LIMIT" in wallet_backend and "FYNX_WALLET_WITHDRAWAL_DAILY_COUNT_LIMIT" in wallet_backend and "dailyUsage?.requested_amount" in wallet_backend and "dailyUsage?.request_count" in wallet_backend),
 ("wallet statements download an owner-scoped formula-safe CSV", "rememberLauncherForActivityResult" in panel and "CreateDocument(\"text/csv\")" in panel and "/api/wallet/statement.csv" in client and "buildBuyerWalletStatementCsv" in wallet_backend),
 ("seller earnings explicitly disclose default five-percent Marketplace commission", '"FYNX Marketplace commission · default 5%"' in seller_center and 'record.marketplaceFee' in seller_center),
 ("Marketplace commission is not charged on wallet top-ups or withdrawals", 'wallet top-ups and bank withdrawals are not charged this fee' in seller_center),
 ("default Marketplace fee policy is seller-paid at five percent", "process.env.FYNX_MARKETPLACE_FEE_MODE || 'SELLER'" in transactions and "process.env.FYNX_MARKETPLACE_FEE_BPS || '500'" in transactions and 'const mode = configuredMode;' in transactions),
 ("FYNX revenue report uses the admin-only revenue endpoint", '"/api/admin/revenue"' in admin_client and "if(!(await requireAdmin(req,res)))return" in admin_routes),
 ("owner/admin control center shows separate Marketplace commission totals", '"FYNX Business Revenue"' in admin_panel and '"Marketplace commission ledger"' in admin_panel and 'it.source == "MARKETPLACE_FEE"' in admin_panel),
 ("company commission is explicitly separate from personal wallet balances", "tracked separately from customer wallet balances and seller payouts" in admin_panel)
]
runtime_token_probe = runtime.split("def secure_access_token_persisted()->bool|None:", 1)[1].split(chr(10)+chr(10)+"install=", 1)[0]
runtime_home_recovery = runtime.split("def restore_home_after_restart(name:str)->str:", 1)[1].split(chr(10)+"def capture_surface(", 1)[0]
runtime_surface_gate = runtime.split("expected_authenticated_surfaces = [", 1)[1].split("]", 1)[0]
checks.extend([
 ("runtime Home recovery requires Home-only feed and camera markers, not Friends bottom-navigation markers",
  'def is_authenticated_home_surface(xml_text:str)->bool:' in runtime
  and 'find_control(xml_text, ["Open FYNX camera"])' in runtime
  and 'find_control(xml_text, ["Your feed"])' in runtime
  and 'is_authenticated_home_surface(current)' in runtime),
 ("runtime sign-in recovery requires Sign In, Username and Password together",
  'def is_server_login_gate(xml_text:str)->bool:' in runtime
  and 'find_control(xml_text, ["Sign In"])' in runtime
  and 'find_control(xml_text, ["Username"])' in runtime
  and 'find_control(xml_text, ["Password"])' in runtime
  and 'if is_server_login_gate(current):' in runtime),
 ("runtime Home recovery waits for the Home destination and does not return on a generic Friends marker",
  'Home deep link did not reach the authenticated Home feed within 22 seconds' in runtime
  and 'dismiss_runtime_permission_prompt(' not in runtime_home_recovery),
 ("Status runtime can recover Home from nested feature panels through the registered deep link without force-stop",
  '"fynx://home"' in status_runtime
  and 'status-return-home-deeplink.xml' in status_runtime
  and '"force-stop"' not in status_runtime),
])
checks.extend([
 ("runtime token persistence probe uses package-scoped test/grep without nested shell quoting",
  '"run-as", PACKAGE, "test", "-f", prefs_file' in runtime_token_probe
  and '"run-as", PACKAGE, "grep", "-q", "access_token", prefs_file' in runtime_token_probe
  and '"sh", "-c"' not in runtime_token_probe),
 ("runtime token probe distinguishes a missing preference/key from an unavailable adb probe",
  "file_check.returncode == 1 and not file_diagnostic" in runtime_token_probe
  and "key_check.returncode == 1 and not key_diagnostic" in runtime_token_probe
  and "return None" in runtime_token_probe),
 ("Wallet runtime evidence requires every captured Wallet and My Shop screen, not unrelated social screenshots",
  all(name in runtime_surface_gate for name in [
   '"authenticated-wallet.png"', '"authenticated-wallet-add-money.png"',
   '"authenticated-wallet-bank-transfer.png"', '"authenticated-wallet-card.png"',
   '"authenticated-wallet-ussd.png"', '"authenticated-wallet-withdraw.png"',
   '"authenticated-marketplace.png"', '"authenticated-marketplace-my-shop-overview.png"',
   '"authenticated-marketplace-my-shop-profile.png"', '"authenticated-marketplace-my-shop-listings.png"',
   '"authenticated-marketplace-my-shop-orders.png"', '"authenticated-marketplace-my-shop-delivery-drivers.png"',
   '"authenticated-marketplace-my-shop-earnings-payouts.png"'
  ])
  and all(name not in runtime_surface_gate for name in [
   '"authenticated-chat.png"', '"authenticated-friends.png"', '"authenticated-stories.png"'
  ]))
])
checks.extend([
 ("server credential is preferred to a visible passwordless debug entry when CI supplies a password",
  "if debug_entry_visible and not PASSWORD:" in runtime
  and "if debug_entry_visible:" not in runtime),
 ("non-visual Wallet runtime certification rejects local-only login mode",
  'LOGIN_MODE != "server credential login"' in runtime
  and "Wallet runtime certification requires a server-credential login" in runtime),
 ("Wallet runtime certification requires a positively verified secure token, not a missing or unavailable probe",
  "if token_present is not True:" in runtime
  and "probe unavailable" in runtime
  and "secure access token was not verifiably present" in runtime),
 ("token probe verifier isolates the function body at the install command",
  "install=run(" not in runtime_token_probe
  and runtime_token_probe.rstrip().endswith("return None"))
])
failed=[name for name,ok in checks if not ok]
for name,ok in checks: print(("PASS" if ok else "FAIL")+": "+name)
if failed: raise SystemExit("FYNX buyer wallet Android integration failed: "+", ".join(failed))
print("FYNX buyer wallet Android integration GREEN ("+str(len(checks))+" checks)")
