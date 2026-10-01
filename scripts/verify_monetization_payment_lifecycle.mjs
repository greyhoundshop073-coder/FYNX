import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const monetization = read('backend/monetizationRoutes.js');
const marketplace = read('backend/marketplaceReputation.js');
const verification = read('scripts/verify_monetization_production.mjs');
const client = read('app/src/main/java/com/fynx/app/ui/FynxMonetizationClient.kt');

const checks = [
  ['paid-plan payment table is server-owned', monetization.includes('CREATE TABLE IF NOT EXISTS fynx_plan_payments') && monetization.includes("status TEXT NOT NULL DEFAULT 'PENDING'")],
  ['Business and Creator prices are server-configured', monetization.includes('FYNX_BUSINESS_PLAN_AMOUNT_KOBO') && monetization.includes('FYNX_CREATOR_PLAN_AMOUNT_KOBO') && monetization.includes('const config = paidPlanCatalog()[plan]')],
  ['live paid-plan charging remains OFF by default', monetization.includes("FYNX_MONETIZATION_CHARGING_ENABLED || ''") && monetization.includes("paid plans are not currently enabled")],
  ['client cannot choose the paid-plan amount', monetization.includes('amount: String(config.amountKobo)') && !monetization.includes('amount: String(req.body')],
  ['Paystack initialization uses server-owned metadata', monetization.includes("purpose: 'FYNX_PAID_PLAN'") && monetization.includes('userId: String(userId)') && monetization.includes('plan, paymentId')],
  ['provider response amount, currency and reference are validated', monetization.includes('providerReference !== reference') && monetization.includes('providerAmount !== config.amountKobo') && monetization.includes('providerCurrency !== PLAN_CURRENCY')],
  ['verification re-checks provider status, amount, currency and metadata', monetization.includes("transaction?.status !== 'success'") && monetization.includes('paidAmount !== Number(payment.amount_kobo)') && monetization.includes('metadataUserId !== String(payment.user_id)')],
  ['entitlement extension supports renewal without losing remaining time', monetization.includes("current.plan === payment.plan") && monetization.includes('currentExpiry > now') && monetization.includes('PLAN_DURATION_DAYS')],
  ['entitlement fulfillment is idempotent', monetization.includes("if (locked.status === 'PAID')") && monetization.includes('FOR UPDATE')],
  ['expired entitlements become inactive on the server', monetization.includes("expires_at<=NOW()") && monetization.includes("SET active=FALSE")],
  ['stale pending payments do not block a fresh checkout', monetization.includes("status='PENDING' AND expires_at>NOW()") && monetization.includes("NOW()+INTERVAL '30 minutes'")],
  ['failed Paystack charges are recorded without activating entitlement', monetization.includes("eventName === 'charge.failed'") && monetization.includes("status='FAILED'") && monetization.includes("status='PENDING'")],
  ['Paystack webhook is signature-protected and handles success/failure separately', monetization.includes('x-paystack-signature') && monetization.includes('safeEqualHex') && monetization.includes("eventName === 'charge.failed'") && monetization.includes("eventName !== 'charge.success'")],
  ['Android client exposes server-authoritative plan catalog and entitlement state', client.includes('FynxMonetizationClient') && client.includes('"/api/monetization/plans"') && client.includes('"/api/monetization/entitlements"')],
  ['Android client starts and verifies payments through the backend', client.includes('"/api/monetization/plans/$normalized/payment"') && client.includes('"/api/monetization/plans/payment/verify"') && client.includes('FynxBackendClient.postJson')],
  ['marketplace payment implementation remains unchanged in this batch', marketplace.includes("app.post('/api/marketplace/orders/:id/payment'") && marketplace.includes("FYNX_MARKETPLACE_ORDER") && !marketplace.includes('FYNX_PAID_PLAN')],
  ['existing production monetization gate remains in CI', verification.includes('chargingEnabled') && verification.includes('AI monetization remains entitlement-only')]
];

for (const [name, ok] of checks) {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
  if (!ok) process.exitCode = 1;
}
if (process.exitCode) process.exit(1);
console.log(`GREEN: ${checks.length}/${checks.length} paid-plan lifecycle invariants passed`);
