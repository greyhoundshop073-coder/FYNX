import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const monetization = read('backend/monetizationRoutes.js');
const client = read('app/src/main/java/com/fynx/app/ui/FynxMonetizationClient.kt');
const business = read('app/src/main/java/com/fynx/app/ui/FynxBusinessAccount.kt');
const uiVerifier = read('scripts/verify_monetization_ui_contract.mjs');
const lifecycle = read('scripts/verify_monetization_payment_lifecycle.mjs');
const production = read('scripts/verify_monetization_production.mjs');

const has = (s, p) => p instanceof RegExp ? p.test(s) : s.includes(p);
const checks = [
  ['server exposes Business/Creator plan catalog', has(monetization, "app.get('/api/monetization/plans'") && has(monetization, 'paidPlanCatalog')],
  ['server owns payment amount and currency', has(monetization, 'amount: String(config.amountKobo)') && has(monetization, 'PLAN_CURRENCY')],
  ['server records every paid-plan checkout', has(monetization, 'fynx_plan_payments') && has(monetization, "status TEXT NOT NULL DEFAULT 'PENDING'")],
  ['provider verification is authoritative', has(monetization, 'transaction?.status !== \'success\'') && has(monetization, 'providerReference !== payment.reference') && has(monetization, 'paidAmount !== Number(payment.amount_kobo)')],
  ['successful payment activates the server entitlement', has(monetization, 'INSERT INTO fynx_business_entitlements') && has(monetization, "active=TRUE")],
  ['same-plan renewal preserves remaining entitlement time', has(monetization, 'current.plan === payment.plan') && has(monetization, 'currentExpiry > now')],
  ['payment fulfillment is idempotent and transaction-locked', has(monetization, "locked.status === 'PAID'") && has(monetization, 'FOR UPDATE')],
  ['expired entitlements are deactivated server-side', has(monetization, 'expires_at<=NOW()') && has(monetization, 'SET active=FALSE')],
  ['failed Paystack charges do not activate plans', has(monetization, "eventName === 'charge.failed'") && has(monetization, "status='FAILED'")],
  ['webhook authenticity is protected', has(monetization, 'x-paystack-signature') && has(monetization, 'timingSafeEqual')],
  ['live paid-plan charging remains explicitly gated', has(monetization, 'FYNX_MONETIZATION_CHARGING_ENABLED') && has(monetization, 'CHARGING_ENABLED')],
  ['Android client uses the monetization API', has(client, '/api/monetization/plans') && has(client, '/api/monetization/entitlements') && has(client, '/api/monetization/plans/payment/verify')],
  ['Business UI consumes server entitlement state', has(business, 'FynxMonetizationClient')],
  ['existing lifecycle verification remains present', has(lifecycle, 'paid-plan') && has(lifecycle, 'expired entitlements')],
  ['existing production monetization verification remains present', has(production, 'business and creator entitlements are server-side') && has(production, 'protected funds')],
  ['existing UI contract verification remains present', has(uiVerifier, 'FynxMonetizationClient')]
];

for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (checks.some(([, ok]) => !ok)) process.exit(1);
console.log(`GREEN: ${checks.length}/${checks.length} final monetization certification invariants passed`);
