import fs from 'node:fs';
import { buildBuyerWalletStatementCsv } from './buyerWallet.js';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const checkout = read('./marketplaceTransactions.js');
const settlement = read('./marketplaceSettlement.js');
const payoutWorker = read('./marketplaceSettlementWorker.js');
const wallet = read('./buyerWallet.js');
const server = read('./server.js');
const webhook = read('./marketplacePaystackWebhook.js');

const checks = [
  ['Marketplace fee remains a configurable five-percent default', checkout.includes("FYNX_MARKETPLACE_FEE_BPS || '500'")],
  ['Seller earnings expose only the seller-paid fee share', settlement.includes('COALESCE(o.marketplace_fee_seller, 0) AS marketplace_fee_seller') && settlement.includes('marketplaceFeeSeller: Number(row.marketplace_fee_seller || 0)')],
  ['Seller earnings separately report gross, seller fee and net', settlement.includes('AS gross_sales') && settlement.includes('AS marketplace_fees') && settlement.includes('AS net_earnings')],
  ['Settled commission is sourced from the marketplace fee ledger', payoutWorker.includes("'fynx_marketplace_fee','FEE'") && settlement.includes("account='fynx_marketplace_fee' AND entry_type='FEE'")],
  ['Platform commission reporting is protected by FYNX admin roles', settlement.includes("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1") && settlement.includes("return res.status(403).json({ error: 'admin access required' })")],
  ['Pending commission is labelled an estimate and not revenue', settlement.includes('pending_commission_estimate') && settlement.includes('not platform cash revenue')],
  ['Only unsettled active orders contribute to pending commission', settlement.includes("o.status IN ('PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED')")],
  ['Wallet funding, spending and withdrawal remain closed until their real provider workflows are enabled', wallet.includes('canFund: false') && wallet.includes('canSpend: false') && wallet.includes('canWithdraw: false')],
  ['Wallet ledger is immutable and double-entry balanced', wallet.includes('buyer wallet ledger entries are immutable') && wallet.includes('buyer wallet transaction is not double-entry balanced')],
  ['Dedicated funding account creation is gated by provider, custody, compliance and deposit approval', wallet.includes('FYNX_WALLET_PROVIDER_APPROVED') && wallet.includes('FYNX_WALLET_CUSTODY_APPROVED') && wallet.includes('FYNX_WALLET_COMPLIANCE_APPROVED') && wallet.includes('FYNX_WALLET_DEPOSITS_ENABLED')],
  ['Funding account numbers are encrypted with AES-256-GCM before storage', wallet.includes("createCipheriv('aes-256-gcm'") && wallet.includes('funding_account_ciphertext') && wallet.includes('funding_account_tag')],
  ['Funding credits require a signed Paystack webhook and a verified transaction', wallet.includes("authorization?.channel") && wallet.includes('/transaction/verify/') && wallet.includes("status='SUCCEEDED'")],
  ['Funding credits are applied to the append-only double-entry wallet ledger', wallet.includes("'PROVIDER_CLEARING','DEBIT'") && wallet.includes("'AVAILABLE','CREDIT'") && wallet.includes('PAYSTACK-DVA-AVAILABLE-')],
  ['Wallet CSV statements are owner-scoped and bounded', wallet.includes('/api/wallet/statement.csv') && wallet.includes('WHERE t.user_id=$1 AND t.wallet_id=$2') && wallet.includes('LIMIT 5001') && wallet.includes('rows.rows.length > 5000')],
  ['CSV export neutralizes spreadsheet formula injection', buildBuyerWalletStatementCsv([{ description: '=HYPERLINK("bad")', reference: '+cmd' }]).includes("'=HYPERLINK") && buildBuyerWalletStatementCsv([{ description: '=HYPERLINK("bad")', reference: '+cmd' }]).includes("'+cmd")),
  ['Express preserves raw request bytes for Paystack webhook signatures', server.includes('express.json({ limit: "18mb"') && server.includes('verify: (req, _res, buffer) =>') && server.includes('req.rawBody = Buffer.from(buffer)')],
  ['Paystack webhook validates HMAC SHA-512 before reconciliation', webhook.includes("createHmac('sha512'") && webhook.includes("req.rawBody") && webhook.includes("x-paystack-signature")]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Wallet business-ledger verification passed: ${checks.length}/${checks.length}`);
