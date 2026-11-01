import fs from 'node:fs';
import { buildBuyerWalletStatementCsv } from './buyerWallet.js';
import './verify-wallet-hosted-funding.mjs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const checkout = read('./marketplaceTransactions.js');
const settlement = read('./marketplaceSettlement.js');
const payoutWorker = read('./marketplaceSettlementWorker.js');
const wallet = read('./buyerWallet.js');
const server = read('./server.js');
const webhook = read('./marketplacePaystackWebhook.js');
const walletMarketplace = read('./buyerWalletMarketplace.js');
const protectionResolution = read('./marketplaceProtectionResolution.js');
const scalability = read('./scalability.js');

const checks = [
  ['Marketplace fee remains a configurable five-percent default', checkout.includes("FYNX_MARKETPLACE_FEE_BPS || '500'")],
  ['Seller earnings expose only the seller-paid fee share', settlement.includes('COALESCE(o.marketplace_fee_seller, 0) AS marketplace_fee_seller') && settlement.includes('marketplaceFeeSeller: Number(row.marketplace_fee_seller || 0)')],
  ['Seller earnings separately report gross, seller fee and net', settlement.includes('AS gross_sales') && settlement.includes('AS marketplace_fees') && settlement.includes('AS net_earnings')],
  ['Settled commission is sourced from the marketplace fee ledger', payoutWorker.includes("'fynx_marketplace_fee','FEE'") && settlement.includes("account='fynx_marketplace_fee' AND entry_type='FEE'")],
  ['Platform commission reporting is protected by FYNX admin roles', settlement.includes("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1") && settlement.includes("return res.status(403).json({ error: 'admin access required' })")],
  ['Pending commission is labelled an estimate and not revenue', settlement.includes('pending_commission_estimate') && settlement.includes('not platform cash revenue')],
  ['Only unsettled active orders contribute to pending commission', settlement.includes("o.status IN ('PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED')")],
  ['Wallet deposit, spending and withdrawal capabilities require their explicit provider approvals', wallet.includes("walletProviderApproved('DEPOSIT')") && wallet.includes("walletProviderApproved('SPENDING')") && wallet.includes("walletProviderApproved('WITHDRAWAL')")],
  ['Wallet ledger is immutable and double-entry balanced', wallet.includes('buyer wallet ledger entries are immutable') && wallet.includes('buyer wallet transaction is not double-entry balanced')],
  ['Dedicated funding account creation is gated by provider, custody, compliance and deposit approval', wallet.includes('FYNX_WALLET_PROVIDER_APPROVED') && wallet.includes('FYNX_WALLET_CUSTODY_APPROVED') && wallet.includes('FYNX_WALLET_COMPLIANCE_APPROVED') && wallet.includes('FYNX_WALLET_DEPOSITS_ENABLED')],
  ['Funding account numbers are encrypted with AES-256-GCM before storage', wallet.includes("createCipheriv('aes-256-gcm'") && wallet.includes('funding_account_ciphertext') && wallet.includes('funding_account_tag')],
  ['Funding credits are dispatched through the shared signed Paystack webhook and verified against the provider', wallet.includes("authorization?.channel") && wallet.includes('/transaction/verify/') && wallet.includes("status='SUCCEEDED'") && webhook.includes("handleBuyerWalletPaystackEvent('charge.success'") && webhook.includes("authorization?.channel")],
  ['Dedicated-account assignment events are dispatched through the shared Paystack webhook', webhook.includes("handleBuyerWalletPaystackEvent(eventName, eventData)") && wallet.includes("dedicatedaccount.assign.success")],
  ['Marketplace and Wallet transfer outcomes share one signed webhook route', webhook.includes("reconcileTransferEvent(client, eventName, eventData)") && webhook.includes("reconcileBuyerWalletTransferEvent(client, eventName, eventData)") && !wallet.includes("app.post('/api/wallet/paystack/webhook'")],
  ['Wallet Marketplace payment is behind explicit provider, custody, compliance and spending approvals', walletMarketplace.includes("FYNX_WALLET_SPENDING_ENABLED === 'true'") && walletMarketplace.includes("FYNX_WALLET_PROVIDER_APPROVED === 'true'") && walletMarketplace.includes("FYNX_WALLET_CUSTODY_APPROVED === 'true'") && walletMarketplace.includes("FYNX_WALLET_COMPLIANCE_APPROVED === 'true'")],
  ['Marketplace Wallet checkout is registered in the canonical backend startup', scalability.includes('import { registerBuyerWalletMarketplaceRoutes } from "./buyerWalletMarketplace.js";') && scalability.includes("registerBuyerWalletMarketplaceRoutes({ app });")],
  ['Wallet Marketplace payment atomically checks order owner, NGN currency, Wallet PIN and available balance', walletMarketplace.includes("String(order.buyer_id) !== String(req.user.sub)") && walletMarketplace.includes("String(order.currency || '').toUpperCase() !== 'NGN'") && walletMarketplace.includes("bcrypt.compare(pin, wallet.withdrawal_pin_hash)") && walletMarketplace.includes("your cleared Wallet balance is not enough")],
  ['Wallet Marketplace payment debits available and credits order escrow before marking the order paid', walletMarketplace.includes("'AVAILABLE','DEBIT'") && walletMarketplace.includes("'MARKETPLACE_ESCROW','CREDIT'") && walletMarketplace.includes("status='PAID',payment_method='FYNX_WALLET'")],
  ['Buyer-favourable protection refunds return Wallet-funded orders to that same buyer Wallet', protectionResolution.includes("paymentMethod === 'FYNX_WALLET'") && protectionResolution.includes("refundBuyerWalletMarketplaceOrder(client") && protectionResolution.includes("refundedTo: resolution === 'BUYER'")],
  ['Wallet order escrow is included in buyer reserved balance and cleared on successful seller settlement', wallet.includes("e.account_code IN ('AVAILABLE','RESERVED','MARKETPLACE_ESCROW','WITHDRAWAL_PENDING')") && wallet.includes('(balances.RESERVED || 0) + (balances.MARKETPLACE_ESCROW || 0)') && walletMarketplace.includes("'MARKETPLACE_ESCROW','DEBIT'") && payoutWorker.includes('settleBuyerWalletMarketplaceOrder(client, operation.order_id)')],
  ['Seller payout worker and shared Paystack webhook both finalize Wallet Marketplace escrow', payoutWorker.includes("import { settleBuyerWalletMarketplaceOrder } from './buyerWalletMarketplace.js'") && webhook.includes('await settleBuyerWalletMarketplaceOrder(client, operation.order_id)')],
  ['Wallet order refunds reconcile only the original order payment and exact escrow amount', walletMarketplace.includes("metadata->>'orderId'=$3") && walletMarketplace.includes("e.transaction_id=$2") && walletMarketplace.includes("WALLET_REFUND_ESCROW_MISMATCH")],

  ['Funding credits are applied to the append-only double-entry wallet ledger', wallet.includes("'PROVIDER_CLEARING','DEBIT'") && wallet.includes("'AVAILABLE','CREDIT'") && wallet.includes('PAYSTACK-DVA-AVAILABLE-')],
  ['Wallet CSV statements are owner-scoped and bounded', wallet.includes('/api/wallet/statement.csv') && wallet.includes('WHERE t.user_id=$1 AND t.wallet_id=$2') && wallet.includes('LIMIT 5001') && wallet.includes('rows.rows.length > 5000')],
  ['CSV export neutralizes spreadsheet formula injection', buildBuyerWalletStatementCsv([{ description: '=HYPERLINK("bad")', reference: '+cmd' }]).includes("'=HYPERLINK") && buildBuyerWalletStatementCsv([{ description: '=HYPERLINK("bad")', reference: '+cmd' }]).includes("'+cmd")],
  ['Wallet withdrawals are gated by provider, compliance, payout approval, OTP policy and configured limits', wallet.includes("FYNX_WALLET_WITHDRAWALS_ENABLED === 'true'") && wallet.includes("FYNX_WALLET_PAYOUTS_APPROVED === 'true'") && wallet.includes("FYNX_WALLET_WITHDRAWAL_FEE_POLICY_APPROVED === 'true'") && wallet.includes("FYNX_WALLET_TRANSFER_OTP_DISABLED === 'true'") && wallet.includes("maximum > 10_000_000") && wallet.includes("maximum < 50") && wallet.includes("walletWithdrawalLimits() !== null")],
  ['Wallet withdrawals require a six-digit PIN verified against a slow password hash', wallet.includes("bcrypt.compare(pin, wallet.withdrawal_pin_hash)") && wallet.includes("bcrypt.hash(pin, 12)") && wallet.includes("currentPassword")],
  ['Repeated wrong wallet PINs create a 15-minute lockout', wallet.includes("withdrawal_pin_failed_attempts") && wallet.includes("INTERVAL '15 minutes'") && wallet.includes("failedAttempts >= 5")],
  ['Wallet PIN setup reauthenticates the current password and locks repeated failures', wallet.includes("bcrypt.compare(currentPassword, user.password_hash)") && wallet.includes("withdrawal_pin_setup_failed_attempts") && wallet.includes("withdrawal_pin_setup_locked_until")],
  ['Wallet security returns PIN state and configured withdrawal limits', wallet.includes("app.get('/api/wallet/security', auth") && wallet.includes("maximumPerTransfer") && wallet.includes("dailyAmountLimit") && wallet.includes("dailyCountLimit")],
  ['Per-transfer and daily wallet withdrawal limits are enforced server-side', wallet.includes("FYNX_WALLET_WITHDRAWAL_MAX_AMOUNT") && wallet.includes("FYNX_WALLET_WITHDRAWAL_DAILY_LIMIT") && wallet.includes("FYNX_WALLET_WITHDRAWAL_DAILY_COUNT_LIMIT") && wallet.includes("dailyUsage?.requested_amount") && wallet.includes("dailyUsage?.request_count")],
  ['Withdrawal reservations only move cleared funds to the pending bucket', wallet.includes("'AVAILABLE','DEBIT',$4,'NGN'") && wallet.includes("'WITHDRAWAL_PENDING','CREDIT',$4,'NGN'")],
  ['Transfer webhooks reconcile success, failure and reversal with balanced ledger entries', wallet.includes("reconcileWalletTransferEvent(client, eventName, transfer)") && wallet.includes("eventName === 'transfer.success'") && wallet.includes("eventName === 'transfer.failed'") && wallet.includes("eventName === 'transfer.reversed'")],
  ['Customer history hides internal reversal/settlement rows but reports provider payout status', wallet.includes("COALESCE(t.metadata->>'internal','false') <> 'true'") && wallet.includes("LEFT JOIN buyer_wallet_provider_operations op ON op.transaction_id=t.id") && wallet.includes("WHEN op.status='PENDING' THEN 'PROCESSING'")],
  ['Express preserves raw request bytes for Paystack webhook signatures', server.includes('express.json({ limit: "18mb"') && server.includes('verify: (req, _res, buffer) =>') && server.includes('req.rawBody = Buffer.from(buffer)')],
  ['Paystack webhook validates HMAC SHA-512 before reconciliation', webhook.includes("createHmac('sha512'") && webhook.includes("req.rawBody") && webhook.includes("x-paystack-signature")]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Wallet business-ledger verification passed: ${checks.length}/${checks.length}`);
