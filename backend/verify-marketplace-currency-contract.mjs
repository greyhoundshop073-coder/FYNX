import fs from 'node:fs';

const social = fs.readFileSync(new URL('./socialRoutes.js', import.meta.url), 'utf8');
const checkout = fs.readFileSync(new URL('./marketplaceCheckoutOrder.js', import.meta.url), 'utf8');
const settlement = fs.readFileSync(new URL('./marketplaceSettlement.js', import.meta.url), 'utf8');
const worker = fs.readFileSync(new URL('./marketplaceSettlementWorker.js', import.meta.url), 'utf8');

const checks = [
  ['seller listing creation is restricted to NGN payout-compatible currency', social.includes('currency !== "NGN"')],
  ['Marketplace listing discovery hides unsupported settlement currencies', social.includes("UPPER(COALESCE(l.currency,'')) = 'NGN'")],
  ['checkout rejects non-NGN listing currencies', checkout.includes("currency !== 'NGN'") && checkout.includes('UNSUPPORTED_SETTLEMENT_CURRENCY')],
  ['seller listing updates reject unsupported currencies', social.includes('existing.currency') && social.includes('UNSUPPORTED_SETTLEMENT_CURRENCY')],
  ['payout account is created as an NGN Nigerian bank recipient', settlement.includes("currency: 'NGN'") && settlement.includes("VALUES ($1,$2,'paystack',$3,$4,$5,$6,$7,'NGN'")],
  ['payout release requires a payout account matching order currency', settlement.includes('AND UPPER(currency)=UPPER($2)')],
  ['payout worker refuses unsupported payout currencies before transfer', worker.includes('Paystack seller payouts currently support NGN only')],
  ['payout transfer explicitly uses NGN', worker.includes("currency: 'NGN'")],
  ['provider payout amount and currency are verified', worker.includes('transferMatchesOperation(data?.data, operation)')]
];

let failed = 0;
for (const [name, ok] of checks) {
  console.log((ok ? 'PASS: ' : 'FAIL: ') + name);
  if (!ok) failed++;
}
if (failed) process.exit(1);
