import fs from "node:fs";
import "./verify-wallet-business-ledger.mjs";
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
  ["real-money capabilities require explicit provider, custody, compliance and payout approvals", source.includes("FYNX_WALLET_PROVIDER_APPROVED") && source.includes("FYNX_WALLET_CUSTODY_APPROVED") && source.includes("FYNX_WALLET_COMPLIANCE_APPROVED") && source.includes("FYNX_WALLET_DEPOSITS_ENABLED") && source.includes("FYNX_WALLET_WITHDRAWALS_ENABLED") && source.includes("canSpend: false")],
  ["dedicated-account and wallet funding events use shared Paystack webhook helpers", source.includes("export async function handleBuyerWalletPaystackEvent") && source.includes("export async function reconcileBuyerWalletTransferEvent") && !source.includes("app.post('/api/wallet/paystack/webhook'")],
  ["named PL/pgSQL dollar quotes are paired", source.split("RETURNS trigger AS $fynxwallet$").length - 1 >= 7 && source.split("$fynxwallet$ LANGUAGE plpgsql;").length - 1 === source.split("RETURNS trigger AS $fynxwallet$").length - 1]
];

const summaryStart = source.indexOf("app.get('/api/wallet/summary', auth");
const summaryEnd = source.indexOf("app.post('/api/wallet/funding/initialize', auth", summaryStart);
const summaryRoute = summaryStart >= 0 && summaryEnd > summaryStart
  ? source.slice(summaryStart, summaryEnd)
  : "";
const noWalletStart = summaryRoute.indexOf("if (!wallet)");
const noWalletEnd = summaryRoute.indexOf("const balanceRows", noWalletStart);
const noWalletBranch = noWalletStart >= 0 && noWalletEnd > noWalletStart
  ? summaryRoute.slice(noWalletStart, noWalletEnd)
  : "";
const providerGateStart = source.indexOf("function walletProviderApproved(action)");
const providerGateEnd = source.indexOf("function walletFundingLimits()", providerGateStart);
const providerGate = providerGateStart >= 0 && providerGateEnd > providerGateStart
  ? source.slice(providerGateStart, providerGateEnd)
  : "";
checks.push(
  ["first-time Wallet summary is read-only and never auto-creates a wallet row",
    summaryRoute.includes("SELECT id,status,provider,currency") &&
    !/INSERT\\s+INTO\\s+buyer_wallets/i.test(summaryRoute) &&
    !/UPDATE\\s+buyer_wallets/i.test(summaryRoute)],
  ["NOT_SETUP returns null balances and keeps spending and withdrawals disabled",
    noWalletBranch.includes("walletStatus: 'NOT_SETUP'") &&
    noWalletBranch.includes("availableBalance: null") &&
    noWalletBranch.includes("reservedBalance: null") &&
    noWalletBranch.includes("withdrawalPendingBalance: null") &&
    noWalletBranch.includes("canSpend: false") &&
    noWalletBranch.includes("canWithdraw: false")],
  ["wallet balances are derived only from succeeded append-only ledger postings",
    summaryRoute.includes("t.status='SUCCEEDED'") &&
    summaryRoute.includes("FROM buyer_wallet_ledger_entries e") &&
    summaryRoute.includes("JOIN buyer_wallet_transactions t ON t.id=e.transaction_id")],
  ["buyer reserved balance includes both reserved funds and Marketplace escrow",
    summaryRoute.includes("(balances.RESERVED || 0) + (balances.MARKETPLACE_ESCROW || 0)")],
  ["uninitialised-wallet payment methods stay closed unless all provider, custody, compliance and Paystack gates are present",
    providerGate.includes("FYNX_WALLET_PROVIDER_APPROVED === 'true'") &&
    providerGate.includes("FYNX_WALLET_CUSTODY_APPROVED === 'true'") &&
    providerGate.includes("FYNX_WALLET_COMPLIANCE_APPROVED === 'true'") &&
    providerGate.includes("Boolean(process.env.PAYSTACK_SECRET_KEY)") &&
    noWalletBranch.includes("canFundByBankTransfer: walletProviderApproved('DEPOSIT')") &&
    noWalletBranch.includes("canFundByCard: walletProviderApproved('CARD_FUNDING')") &&
    noWalletBranch.includes("canFundByUssd: walletProviderApproved('USSD_FUNDING')")],
  ["first-time Wallet summary stays private and uncached",
    summaryRoute.includes("Cache-Control', 'no-store, private") &&
    summaryRoute.includes("WHERE user_id=$1 AND currency=$2")]
);
const failed=checks.filter(([,ok])=>!ok).map(([name])=>name);
if(failed.length){console.error("FYNX buyer wallet backend verification FAILED");for(const name of failed)console.error(" - "+name);process.exit(1);}
console.log("FYNX buyer wallet backend verification GREEN ("+checks.length+" checks)");
