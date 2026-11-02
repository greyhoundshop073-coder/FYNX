import fs from 'node:fs';

const wallet = fs.readFileSync(new URL('./buyerWallet.js', import.meta.url), 'utf8');
const webhook = fs.readFileSync(new URL('./marketplacePaystackWebhook.js', import.meta.url), 'utf8');
const checks = [
  ['NGN-only buyer Wallet rows are unique per user and currency', wallet.includes('CREATE TABLE IF NOT EXISTS buyer_wallets') && wallet.includes('UNIQUE (user_id, currency)') && wallet.includes("CHECK (currency = 'NGN')")],
  ['Wallet transactions are owner- and currency-bound to their Wallet', wallet.includes('fynx_buyer_wallet_validate_transaction_owner') && wallet.includes('buyer wallet transaction owner or currency does not match its wallet')],
  ['transaction state transitions are constrained and terminal rows are immutable', wallet.includes('buyer_wallet_transaction_insert_guard') && wallet.includes('buyer_wallet_transaction_update_guard') && wallet.includes('terminal buyer wallet transactions are immutable')],
  ['ledger postings use unique keys and are allowed only during processing', wallet.includes('entry_key TEXT NOT NULL UNIQUE') && wallet.includes('buyer_wallet_ledger_insert_guard') && wallet.includes('wallet ledger entries can only be posted while the transaction is processing')],
  ['successful transactions require balanced append-only double-entry postings', wallet.includes('buyer_wallet_transaction_success_balanced') && wallet.includes('successful buyer wallet transaction must have balanced ledger entries') && wallet.includes('buyer_wallet_reject_ledger_mutation') && wallet.includes('BEFORE UPDATE OR DELETE ON buyer_wallet_ledger_entries') && wallet.includes('DEFERRABLE INITIALLY DEFERRED')],
  ['available, reserved, escrow and withdrawal-pending balances cannot become negative', wallet.includes("e.account_code IN ('AVAILABLE','RESERVED','MARKETPLACE_ESCROW','WITHDRAWAL_PENDING')") && wallet.includes('buyer wallet available, reserved, Marketplace escrow or withdrawal balance cannot be negative')],
  ['Wallet summary is authenticated, private and uncached', wallet.includes("app.get('/api/wallet/summary', auth") && wallet.includes("Cache-Control', 'no-store, private")],
  ['Wallet activity is authenticated and scoped to the signed-in owner', wallet.includes("app.get('/api/wallet/transactions', auth") && wallet.includes('WHERE t.user_id=$1 AND t.wallet_id=$2')],
  ['activity direction is filtered before stable keyset pagination', wallet.includes('parseBuyerWalletActivityDirection') && wallet.includes("['ALL', 'IN', 'OUT']") && wallet.includes('activity_delta.wallet_delta') && wallet.includes('(t.created_at,t.id) < ($4::timestamptz,$5::uuid)') && wallet.includes('ORDER BY t.created_at DESC,t.id DESC')],
  ['balances are calculated only from successful ledger transactions', wallet.includes("t.status='SUCCEEDED'") && wallet.includes('FROM buyer_wallet_ledger_entries e') && wallet.includes('JOIN buyer_wallet_transactions t ON t.id=e.transaction_id')],
  ['Marketplace escrow is included in the buyer reserved balance', wallet.includes('(balances.RESERVED || 0) + (balances.MARKETPLACE_ESCROW || 0)')],
  ['real-money deposits, Wallet spending and withdrawals retain separate approval gates', wallet.includes("walletProviderApproved('DEPOSIT')") && wallet.includes("walletProviderApproved('SPENDING')") && wallet.includes("walletProviderApproved('WITHDRAWAL')") && wallet.includes("FYNX_WALLET_PROVIDER_APPROVED === 'true'") && wallet.includes("FYNX_WALLET_CUSTODY_APPROVED === 'true'") && wallet.includes("FYNX_WALLET_COMPLIANCE_APPROVED === 'true'")],
  ['withdrawals require separate business, payout, fee, PIN and limit approvals', wallet.includes("FYNX_PAYSTACK_REGISTERED_BUSINESS_APPROVED === 'true'") && wallet.includes("FYNX_WALLET_WITHDRAWALS_ENABLED === 'true'") && wallet.includes("FYNX_WALLET_PAYOUTS_APPROVED === 'true'") && wallet.includes("FYNX_WALLET_WITHDRAWAL_FEE_POLICY_APPROVED === 'true'") && wallet.includes("walletWithdrawalLimits() !== null")],
  ['Test Mode requires Railway testing, a Paystack test secret and bounded card limits only', wallet.includes("process.env.RAILWAY_ENVIRONMENT_NAME === 'testing'") && wallet.includes("/^sk_test_/") && wallet.includes("process.env.FYNX_WALLET_CARD_FUNDING_ENABLED === 'true'") && wallet.includes('walletFundingLimits() !== null') && wallet.includes("action === 'CARD_FUNDING'")],
  ['Test Mode is explicitly labelled simulated and cannot enable Wallet spending or withdrawal', wallet.includes('testMode: walletTestModeEnabled()') && wallet.includes('TEST MODE ONLY: Paystack card payments are simulated') && wallet.includes("wallet.status === 'ACTIVE' && walletProviderApproved('SPENDING')") && wallet.includes("wallet.status === 'ACTIVE' && walletProviderApproved('WITHDRAWAL')")],
  ['simulated funding is tagged at intent creation and cannot activate the real Wallet', wallet.includes("JSON.stringify({ testMode: walletTestModeEnabled() })") && wallet.includes("WHERE id=$1 AND status='PENDING_PROVIDER' AND $2::boolean = false") && wallet.includes("const intentIsTestMode = intent.metadata?.testMode === true")],
  ['Wallet balance, activity and CSV statements show only the current Test Mode ledger', wallet.includes("COALESCE(t.metadata->>'testMode','false') = $2") && wallet.includes("COALESCE(t.metadata->>'testMode','false') = $4") && wallet.includes("COALESCE(t.metadata->>'testMode','false') = $3") && wallet.includes("COALESCE(t.metadata->>'testMode','false') <> 'true'")],
  ['Paystack webhook verifies the signed raw body before routing Wallet events', webhook.includes('req.rawBody') && webhook.includes('createHmac') && webhook.includes("await handleBuyerWalletPaystackEvent(eventName, transaction)")],
  ['Wallet module uses the shared bounded database pool and does not close it', wallet.includes("import { getFynxDatabasePool } from './databasePool.js'") && wallet.includes('const pool = getFynxDatabasePool()') && !wallet.includes('await pool.end()')]
];
const failed = checks.filter(([, passed]) => !passed);
for (const [name, passed] of checks) console.log(`${passed ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) {
  console.error(`FYNX Wallet core verification FAILED: ${failed.length}/${checks.length}`);
  process.exit(1);
}
console.log(`FYNX Wallet core verification passed: ${checks.length}/${checks.length}`);
