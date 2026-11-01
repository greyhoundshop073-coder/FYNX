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
  ["real-money capabilities are off before provider approval", source.includes("canFund: false") && source.includes("canSpend: false") && source.includes("canWithdraw: false") && !source.includes("app.post('/api/wallet/")],
  ["named PL/pgSQL dollar quotes are paired", source.split("RETURNS trigger AS $fynxwallet$").length - 1 >= 7 && source.split("$fynxwallet$ LANGUAGE plpgsql;").length - 1 === source.split("RETURNS trigger AS $fynxwallet$").length - 1]
];
const failed=checks.filter(([,ok])=>!ok).map(([name])=>name);
if(failed.length){console.error("FYNX buyer wallet backend verification FAILED");for(const name of failed)console.error(" - "+name);process.exit(1);}
console.log("FYNX buyer wallet backend verification GREEN ("+checks.length+" checks)");
