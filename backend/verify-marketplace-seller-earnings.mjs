import fs from 'node:fs';

const settlement = fs.readFileSync(new URL('./marketplaceSettlement.js', import.meta.url), 'utf8');
const sellerUi = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt', import.meta.url), 'utf8');

const checks = [
  ['seller settlement summary reads the canonical order status', settlement.includes('o.status AS order_status')],
  ['completed orders without escrow or payout records are explicitly unreconciled', settlement.includes("funds = 'UNKNOWN' AND order_status = 'COMPLETED' AND payout_status IS NULL THEN 'legacy_unreconciled'")],
  ['completed orders with failed or blocked payouts and no escrow are separately unresolved', settlement.includes("funds = 'UNKNOWN' AND order_status = 'COMPLETED' AND payout_status IN ('FAILED', 'BLOCKED') THEN 'failed_unreconciled'")],
  ['legacy completed earnings are exposed separately', settlement.includes('AS unreconciled_legacy') && settlement.includes('unreconciledLegacy: Number(row.unreconciled_legacy || 0)')],
  ['failed payout earnings are exposed separately', settlement.includes('AS unreconciled_failed_payouts') && settlement.includes('unreconciledFailedPayouts: Number(row.unreconciled_failed_payouts || 0)')],
  ['legacy and failed payout earnings remain included in gross sales and net earnings', settlement.includes("'protected','available','pending','paid','legacy_unreconciled','failed_unreconciled'")],
  ['legacy completed orders are not treated as protected, available, pending or paid', !/summary_bucket\s*=\s*'legacy_unreconciled'[^\n]*(?:protected_funds|available_for_payout|payout_pending|paid_out)/.test(settlement)],
  ['seller history exposes order status for transparent reconciliation labels', settlement.includes("orderStatus: String(row.order_status || 'UNKNOWN')") && sellerUi.includes('orderStatus = row.optString("orderStatus")')],
  ['seller UI labels historical completed rows as settlement-unverified', sellerUi.includes('LEGACY COMPLETED · SETTLEMENT UNVERIFIED')],
  ['seller UI explains unreconciled earnings are not payout-confirmed', sellerUi.includes('not counted as protected, available or paid')],
  ['seller UI displays the unreconciled legacy total', sellerUi.includes('Historical completed · not reconciled') && sellerUi.includes('unreconciledLegacy')],
  ['seller settlement history uses a bounded page size', settlement.includes("Math.min(100, Math.max(1, requestedLimit))") && settlement.includes('LIMIT $4')],
  ['seller settlement cursor is validated before use', settlement.includes('parseSellerSettlementCursor(cursorValue)') && settlement.includes("invalid seller settlement cursor")],
  ['seller settlement pages use a stable timestamp and order-id keyset', settlement.includes('o.id DESC') && settlement.includes('o.id) < ($2::timestamptz, $3::uuid)')],
  ['seller settlement cursor is scoped to the authenticated seller query', settlement.includes('WHERE o.seller_id = $1') && settlement.includes('cursor?.orderId || null')],
  ['seller settlement response reports the next page without changing all-time totals', settlement.includes('hasMore,') && settlement.includes('nextCursor') && settlement.includes('GROUP BY currency')],
  ['seller UI can append older pages without duplicating order records', sellerUi.includes('Load older earnings') && sellerUi.includes('existingIds') && sellerUi.includes('moneyHasMore')]
];

checks.push(['seller UI labels failed/blocked payouts as unresolved and unavailable', sellerUi.includes('Failed payout · reconciliation required') && sellerUi.includes('COMPLETED · PAYOUT FAILED · RECONCILIATION REQUIRED') && sellerUi.includes('unreconciledFailedPayouts')]);

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace seller earnings verification FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}
console.log(`Marketplace seller earnings verification GREEN (${checks.length} checks)`);
