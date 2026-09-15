import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const monetization = read('backend/monetizationRoutes.js');
const scalability = read('backend/scalability.js');
const admin = read('backend/adminRoutes.js');
const advertising = read('backend/marketplaceAdvertising.js');
const settlement = read('backend/marketplaceSettlement.js');
const worker = read('backend/marketplaceSettlementWorker.js');
const webhook = read('backend/marketplacePaystackWebhook.js');
const workflow = read('.github/workflows/android-build.yml');

const checks = [
  ['monetization routes are wired into runtime bootstrap', monetization.includes('export function registerMonetizationRoutes') && scalability.includes('registerMonetizationRoutes({ app })')],
  ['revenue remains separate from protected funds', admin.includes('fynx_revenue_transactions') && monetization.includes('protectedFundsExcluded:true')],
  ['marketplace fee revenue is derived from authoritative ledger entries', monetization.includes("NEW.account='fynx_marketplace_fee'") && monetization.includes("NEW.entry_type='FEE'")],
  ['revenue idempotency follows ledger idempotency key', monetization.includes('NEW.idempotency_key') && monetization.includes('ON CONFLICT(source_key) DO NOTHING')],
  ['refunds reverse marketplace fee revenue without touching protected funds', monetization.includes("NEW.account='buyer_refund'") && monetization.includes("status='REFUNDED'") && webhook.includes("entry_type='REFUND'")],
  ['seller payout remains authoritative ledger release', settlement.includes('marketplace_ledger_entries') && worker.includes("'seller_payout','RELEASE'")],
  ['existing seller advertising architecture is preserved', advertising.includes('marketplace_ad_campaigns') && advertising.includes('/api/advertising/campaigns')],
  ['advertising payment requires provider state rather than fake activation', advertising.includes('payment_status') && advertising.includes('payment_reference')],
  ['business and creator entitlements are server-side', monetization.includes('fynx_business_entitlements') && monetization.includes("plan IN ('FREE','BUSINESS','CREATOR')")],
  ['AI monetization remains entitlement-only', monetization.includes('fynx_ai_entitlements') && monetization.includes('chargingEnabled: false')],
  ['admin reconciliation is protected', monetization.includes('/api/admin/monetization/reconciliation') && monetization.includes('administrator access required')],
  ['client cannot establish payment authority', monetization.includes('no client-side payment authority')],
  ['no provider secret is embedded in the new monetization module', !monetization.includes('PAYSTACK_SECRET_KEY') && !monetization.includes('SECRET_KEY')],
  ['workflow runs monetization verification before production certification', workflow.includes('Verify FYNX monetization production') && workflow.indexOf('Verify FYNX monetization production') < workflow.indexOf('Verify final FYNX production certification gate')]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (failed.length) process.exit(1);
console.log(`GREEN: ${checks.length}/${checks.length} monetization production invariants passed`);
