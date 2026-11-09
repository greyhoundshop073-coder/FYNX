import fs from 'node:fs';

const settlement = fs.readFileSync(new URL('./marketplaceSettlement.js', import.meta.url), 'utf8');
const sellerUi = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt', import.meta.url), 'utf8');

const checks = [
  ['seller settlement summary reads the canonical order status', settlement.includes('o.status AS order_status')],
  ['completed orders without escrow or payout records are explicitly unreconciled', settlement.includes("funds = 'UNKNOWN' AND order_status = 'COMPLETED' AND payout_status IS NULL THEN 'legacy_unreconciled'")],
  ['legacy completed earnings are exposed separately', settlement.includes('AS unreconciled_legacy') && settlement.includes('unreconciledLegacy: Number(row.unreconciled_legacy || 0)')],
  ['legacy completed orders remain included in gross sales and net earnings', settlement.includes("'protected','available','pending','paid','legacy_unreconciled'")],
  ['legacy completed orders are not treated as protected, available, pending or paid', !/summary_bucket\s*=\s*'legacy_unreconciled'[^\n]*(?:protected_funds|available_for_payout|payout_pending|paid_out)/.test(settlement)],
  ['seller history exposes order status for transparent reconciliation labels', settlement.includes("orderStatus: String(row.order_status || 'UNKNOWN')") && sellerUi.includes('orderStatus = row.optString("orderStatus")')],
  ['seller UI labels historical completed rows as settlement-unverified', sellerUi.includes('LEGACY COMPLETED · SETTLEMENT UNVERIFIED')],
  ['seller UI explains unreconciled earnings are not payout-confirmed', sellerUi.includes('not counted as protected, available or paid')],
  ['seller UI displays the unreconciled legacy total', sellerUi.includes('Historical completed · not reconciled') && sellerUi.includes('serverTotals.unreconciledLegacy')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace seller earnings verification FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}
console.log(`Marketplace seller earnings verification GREEN (${checks.length} checks)`);
