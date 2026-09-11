import fs from 'node:fs';

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8');
const expiry = read('./marketplaceInspectionExpiry.js');
const scalability = read('./scalability.js');
const completion = read('./marketplaceCompletion.js');

const checks = [
  ['inspection expiry worker exists', expiry.includes('autoCompleteExpiredMarketplaceInspections')],
  ['only expired inspection orders are selected', expiry.includes("o.status='INSPECTION'") && expiry.includes('o.inspection_deadline <= NOW()')],
  ['open disputes block auto-completion', expiry.includes("d.status IN ('OPEN','UNDER_REVIEW')")],
  ['open protection cases block auto-completion', expiry.includes("c.status IN ('OPEN','UNDER_REVIEW')")],
  ['order is locked before transition', expiry.includes('FOR UPDATE OF o')],
  ['auto-completion is idempotent', expiry.includes("WHERE id=$1 AND status='INSPECTION'")],
  ['inventory is consumed exactly once', expiry.includes('quantity=GREATEST(0,quantity-$1)') && expiry.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)')],
  ['auto-completion is audited', expiry.includes('INSPECTION_EXPIRED_AUTO_COMPLETED')],
  ['worker runs every minute', expiry.includes('setInterval') && expiry.includes('AUTO_COMPLETE_INTERVAL_MS = 60_000')],
  ['worker is registered at backend startup', scalability.includes('registerMarketplaceInspectionExpiryWorker')],
  ['manual completion remains buyer-only', completion.includes("only the buyer can complete this order")],
  ['manual completion remains inspection-only', completion.includes("order.status !== 'INSPECTION'")],
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('R6-F inspection expiry guard FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`R6-F inspection expiry guard GREEN (${checks.length} checks)`);