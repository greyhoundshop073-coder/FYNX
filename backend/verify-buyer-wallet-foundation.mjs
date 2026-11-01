import fs from "node:fs";
const source = fs.readFileSync(new URL("./buyerWallet.js", import.meta.url), "utf8");
const settlement = fs.readFileSync(new URL("./marketplaceSettlement.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const checks = [
  ["buyer wallet routes are registered by backend startup", scalability.includes('import { registerBuyerWalletRoutes } from "./buyerWallet.js";') && scalability.includes("registerBuyerWalletRoutes({ app });")],
  ["buyer wallet accounting is isolated from seller settlement", !settlement.includes("buyer_wallets") && !settlement.includes("buyer_wallet_transactions") && !settlement.includes("buyer_wallet_ledger_entries")],
  ["NGN-only wallet per buyer", source.includes("CREATE TABLE IF NOT EXISTS buyer_wallets") && source.includes("UNIQUE (user_id, currency)") && source.includes("CHECK (currency = 'NGN')")],
  ["unique transaction references and idempotency keys", source.includes("idempotency_key TEXT NOT NULL UNIQUE") && source.includes("reference TEXT NOT NULL UNIQUE")],
  ["transaction owner is validated", source.includes("fynx_buyer_wallet_validate_transaction_owner") && source.includes("buyer wallet transaction owner or currency does not match its wallet")],
  ["safe pending and processing state machine", source.includes("buyer_wallet_transaction_insert_guard") && source.includes("buyer_wallet_transaction_update_guard") && source.includes("terminal buyer wallet transactions are immutable")],
  ["unique ledger-leg keys", source.includes("entry_key TEXT NOT NULL UNIQUE")],
  ["ledger legs only post while processing", source.includes("buyer_wallet_ledger_insert_guard") && source.includes("wallet ledger entries can only be posted while the transaction is processing")],
  ["success cannot finalize without balanced entries", source.includes("buyer_wallet_transaction_success_balanced") && source.includes("successful buyer wallet transaction must have balanced ledger entries")],
  ["ledger is append-only and double-entry balanced", source.includes("fynx_buyer_wallet_reject_ledger_mutation") && source.includes("BEFORE UPDATE OR DELETE ON buyer_wallet_ledger_entries") && source.includes("buyer wallet transaction is not double-entry balanced") && source.includes("DEFERRABLE INITIALLY DEFERRED")],
  ["no negative spendable/reserved/pending buckets", source.includes("buyer wallet available, reserved or withdrawal balance cannot be negative")],
  ["authenticated private summary", source.includes("app.get('/api/wallet/summary', auth") && source.includes("Cache-Control', 'no-store, private")],
  ["authenticated buyer-scoped activity", source.includes("app.get('/api/wallet/transactions', auth") && source.includes("WHERE t.user_id=$1 AND t.wallet_id=$2")],
  ["activity cursor validates paired timestamp and UUID values", source.includes("parseBuyerWalletActivityCursor") && source.includes("activity cursor fields must be single strings") && source.includes("activity cursor date is invalid") && source.includes("activity cursor id is invalid")],
  ["activity cursor retains exact validated timestamp precision", source.includes("return { cursor: { createdAt, id: id.toLowerCase() }, error: null }") && !source.includes("parsedDate.toISOString()")],
  ["activity history uses stable keyset pagination after server-side direction filtering", source.includes("(t.created_at,t.id) < ($4::timestamptz,$5::uuid)") && source.includes("ORDER BY t.created_at DESC,t.id DESC") && source.includes("LIMIT $")],
  ["activity page returns continuation metadata", source.includes("hasMore,") && source.includes("nextCursor: hasMore && lastRow")],
  ["activity direction validates a single ALL/IN/OUT value", source.includes("parseBuyerWalletActivityDirection") && source.includes("activity direction must be a single string") && source.includes("['ALL', 'IN', 'OUT']")],
  ["direction filtering is applied before keyset pagination", source.includes("activity_delta.wallet_delta") && source.includes("$3::text='IN'") && source.includes("$3::text='OUT'") && source.includes("LEFT JOIN LATERAL")],
  ["wallet money movement requires explicit provider, custody and webhook approvals", source.includes("walletProviderReady") && source.includes("FYNX_WALLET_PROVIDER_APPROVED") && source.includes("FYNX_WALLET_CUSTODY_APPROVED") && source.includes("FYNX_WALLET_WEBHOOK_CONFIGURED") && source.includes("canSpend: false")],
  ["wallet funding writes are idempotent and user-scoped", source.includes("app.post('/api/wallet/funding/transfer'") && source.includes("idempotency-key") && source.includes("WHERE user_id=$1 AND idempotency_key=$2")],
  ["wallet bank-transfer accounts are real provider-issued transaction accounts", source.includes("walletPaystackRequest('/charge'") && source.includes("account_expires_at:expiresAt") && source.includes("TRANSACTION_SPECIFIC_TEMPORARY")],
  ["wallet card and USSD use secure hosted checkout", source.includes("app.post('/api/wallet/funding/checkout'") && source.includes("/transaction/initialize") && source.includes("channels:[method]")],
  ["wallet webhook verifies signature on original Paystack request bytes", source.includes("walletWebhookSignatureValid(req.rawBody,signature)") && source.includes("createHmac('sha512'") && source.includes("req.rawBody.toString")],
  ["wallet funding credits only after verified amount, NGN currency and success", source.includes("settleBuyerWalletFunding") && source.includes("Number(payment.amount) !== expected") && source.includes("String(payment.currency || '').toUpperCase() !== 'NGN'")],
  ["wallet funding ledger posting is balanced and idempotent", source.includes("WALLET-FUND-CLEARING-") && source.includes("WALLET-FUND-AVAILABLE-") && source.includes("ON CONFLICT(entry_key) DO NOTHING")],
  ["wallet funding status lookup is private and can reverify pending payments", source.includes("app.get('/api/wallet/funding/:reference'") && source.includes("verifyPendingBuyerWalletFunding")],
  ["payout routes require provider, custody, transfer and KYC approvals", source.includes("FYNX_WALLET_TRANSFERS_APPROVED") && source.includes("FYNX_WALLET_KYC_APPROVED") && source.includes("walletWithdrawalsEnabled")],
  ["payout bank details are resolved with Paystack and only masked account digits are stored", source.includes("/bank/resolve?account_number=") && source.includes("/transferrecipient") && source.includes("accountNumber.slice(-4)")],
  ["Wallet PIN is salted with scrypt and repeated failures lock the PIN", source.includes("crypto.scryptSync") && source.includes("failed_attempts") && source.includes("30 minutes")],
  ["Wallet PIN validator matches six actual digits (not a literal backslash-d)", source.split("\n").some((line, index) => line.includes("function walletPinMatches") && source.split("\n")[index + 1].includes("if (!/^\\d{6}$/.test"))],
  ["withdrawals reserve pending amounts until the provider confirms payout", source.includes("pendingWithdrawals") && source.includes("available<amount+fee") && source.includes("status IN ('PENDING','PROCESSING','PENDING_OTP')")],
  ["withdrawal ledger is posted only on confirmed provider success", source.includes("settleBuyerWalletWithdrawal") && source.includes("EXTERNAL_BANK") && source.includes("FYNX_FEES") && source.includes("Number(transfer.amount)!==expected")],
  ["transfer OTP, success, failure and reversal states are handled, including post-success reversal reconciliation", source.includes("/transfer/finalize") && source.includes("name==='transfer.success'") && source.includes("name==='transfer.failed'") && source.includes("name==='transfer.reversed'") && source.includes("['PENDING','PROCESSING','PENDING_OTP','SUCCEEDED']") && source.includes("reverseBuyerWalletWithdrawal(reference,data,'transfer_verification')")],
  ["wallet amounts reject fractions smaller than one kobo", source.includes("Math.abs(scaled - rounded) > 0.000001")],
  ["withdrawal fee is supplied by backend configuration and returned to Android", source.includes("FYNX_WALLET_WITHDRAWAL_FEE_NGN") && source.includes("withdrawalFee: Number(process.env.FYNX_WALLET_WITHDRAWAL_FEE_NGN || 0)")],
  ["wallet summary subtracts pending withdrawals from spendable balance", source.includes("pendingWithdrawalRow") && source.includes("availableBalance: Math.max(0, Number(balances.AVAILABLE || 0) - pendingWithdrawals)")],
  ["payment-checkout timeouts preserve a pending reference and do not invite duplicate charges", source.includes("providerInitiationUncertain:Boolean(ambiguous)") && source.includes("status:ambiguous?'PENDING':'FAILED'")],
  ["wallet funding and payout tables retain recipient references and only masked account digits", source.includes("CREATE TABLE IF NOT EXISTS buyer_wallet_payout_accounts") && source.includes("recipient_code TEXT NOT NULL UNIQUE") && source.includes("account_last4 TEXT NOT NULL") && !source.includes("account_number TEXT NOT NULL")],
  ["named PL/pgSQL dollar quotes are paired", source.split("RETURNS trigger AS $fynxwallet$").length - 1 >= 7 && source.split("$fynxwallet$ LANGUAGE plpgsql;").length - 1 === source.split("RETURNS trigger AS $fynxwallet$").length - 1]
];
const failed=checks.filter(([,ok])=>!ok).map(([name])=>name);
if(failed.length){console.error("FYNX buyer wallet backend verification FAILED");for(const name of failed)console.error(" - "+name);process.exit(1);}
console.log("FYNX buyer wallet backend verification GREEN ("+checks.length+" checks)");
