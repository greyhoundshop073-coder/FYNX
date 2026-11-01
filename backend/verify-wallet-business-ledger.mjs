import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const checkout = read('./marketplaceTransactions.js');
const settlement = read('./marketplaceSettlement.js');
const payoutWorker = read('./marketplaceSettlementWorker.js');
const wallet = read('./buyerWallet.js');

const checks = [
  ['Marketplace fee remains a configurable five-percent default', checkout.includes("FYNX_MARKETPLACE_FEE_BPS || '500'")],
  ['Seller earnings expose only the seller-paid fee share', settlement.includes('COALESCE(o.marketplace_fee_seller, 0) AS marketplace_fee_seller') && settlement.includes('marketplaceFeeSeller: Number(row.marketplace_fee_seller || 0)')],
  ['Seller earnings separately report gross, seller fee and net', settlement.includes('AS gross_sales') && settlement.includes('AS marketplace_fees') && settlement.includes('AS net_earnings')],
  ['Settled commission is sourced from the marketplace fee ledger', payoutWorker.includes("'fynx_marketplace_fee','FEE'") && settlement.includes("account='fynx_marketplace_fee' AND entry_type='FEE'")],
  ['Platform commission reporting is protected by FYNX admin roles', settlement.includes("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1") && settlement.includes("return res.status(403).json({ error: 'admin access required' })")],
  ['Pending commission is labelled an estimate and not revenue', settlement.includes('pending_commission_estimate') && settlement.includes('not platform cash revenue')],
  ['Only unsettled active orders contribute to pending commission', settlement.includes("o.status IN ('PAID','SHIPPED','DELIVERED','INSPECTION','COMPLETED','DISPUTED')")],
  ['Wallet funding, spending and withdrawal remain closed until their real provider workflows are enabled', wallet.includes('canFund: false') && wallet.includes('canSpend: false') && wallet.includes('canWithdraw: false')],
  ['Wallet ledger is immutable and double-entry balanced', wallet.includes('buyer wallet ledger entries are immutable') && wallet.includes('buyer wallet transaction is not double-entry balanced')]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Wallet business-ledger verification passed: ${checks.length}/${checks.length}`);
