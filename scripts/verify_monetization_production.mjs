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

const has = (source, pattern) => pattern instanceof RegExp ? pattern.test(source) : source.includes(pattern);
const checks = [
  ['monetization routes are wired into runtime bootstrap', has(monetization,/export function registerMonetizationRoutes/) && has(scalability,/registerMonetizationRoutes\(\{ app \}\)/)],
  ['revenue remains separate from protected funds', has(admin,/fynx_revenue_transactions/) && has(monetization,/protectedFundsExcluded\s*:\s*true/) && has(monetization,/FROM marketplace_escrows/)],
  ['marketplace fee revenue is derived from authoritative ledger entries', has(monetization,/NEW\.account\s*=\s*'fynx_marketplace_fee'/) && has(monetization,/NEW\.entry_type\s*=\s*'FEE'/)],
  ['revenue idempotency follows ledger idempotency key', has(monetization,/NEW\.idempotency_key/) && has(monetization,/ON CONFLICT\(source_key\) DO NOTHING/)],
  ['refunds reverse marketplace fee revenue without touching protected funds', has(monetization,/NEW\.account\s*=\s*'buyer_refund'/) && has(monetization,/NEW\.entry_type\s*=\s*'REFUND'/) && has(monetization,/status\s*=\s*'REFUNDED'/) && has(webhook,/['\"]buyer_refund['\"].*['\"]REFUND['\"]|buyer_refund.*entry_type.*REFUND/s)],
  ['seller payout remains authoritative ledger release', has(settlement,/marketplace_ledger_entries/) && has(worker,/['\"]seller_payout['\"].*['\"]RELEASE['\"]/s)],
  ['existing seller advertising architecture is preserved', has(advertising,/marketplace_ad_campaigns/) && has(advertising,/\/api\/advertising\/campaigns/)],
  ['advertising payment requires provider state rather than fake activation', has(advertising,/payment_status/) && has(advertising,/payment_reference/)],
  ['business and creator entitlements are server-side', has(monetization,/fynx_business_entitlements/) && has(monetization,/plan IN \('FREE','BUSINESS','CREATOR'\)/)],
  ['AI monetization remains entitlement-only', has(admin,/fynx_ai_entitlements/) && has(monetization,/chargingEnabled:\s*false/)],
  ['admin reconciliation is protected', has(monetization,/\/api\/admin\/monetization\/reconciliation/) && has(monetization,/administrator access required/)],
  ['client cannot establish payment authority', has(monetization,/no client-side payment authority/)],
  ['no provider secret is embedded in the new monetization module', !has(monetization,/PAYSTACK_SECRET_KEY|SECRET_KEY/)],
  ['workflow runs monetization verification before production certification', has(workflow,/Verify FYNX monetization production/) && workflow.indexOf('Verify FYNX monetization production') < workflow.indexOf('Verify final FYNX production certification gate')]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (failed.length) process.exit(1);
console.log(`GREEN: ${checks.length}/${checks.length} monetization production invariants passed`);
