import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const panel = read('app/src/main/java/com/fynx/app/ui/FynxBusinessAccount.kt');
const client = read('app/src/main/java/com/fynx/app/ui/FynxMonetizationClient.kt');
const backend = read('backend/monetizationRoutes.js');

const checks = [
  ['Business UI reads entitlement from the server', panel.includes('FynxMonetizationClient.entitlement(context)')],
  ['Business UI displays server plan and expiry', panel.includes('state.plan') && panel.includes('state.expiresAt')],
  ['client uses the authenticated central backend transport', client.includes('FynxBackendClient.get(context, "/api/monetization/entitlements")')],
  ['client exposes server charging state', client.includes('chargingEnabled = json.optBoolean("chargingEnabled", false)')],
  ['backend explicitly reports charging state', backend.includes('chargingEnabled: false')],
  ['live checkout is not exposed by this UI batch', !panel.includes('initializePayment') && !panel.includes('Paystack')],
];

for (const [name, ok] of checks) {
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
  if (!ok) process.exitCode = 1;
}
if (process.exitCode) process.exit(1);
console.log(`GREEN: ${checks.length}/${checks.length} monetization UI contract checks passed`);
