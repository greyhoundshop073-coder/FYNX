import fs from 'node:fs';
import path from 'node:path';
const root = process.cwd();
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const panel = read('app/src/main/java/com/fynx/app/ui/FynxBusinessAccount.kt');
const client = read('app/src/main/java/com/fynx/app/ui/FynxMonetizationClient.kt');
const backend = read('backend/monetizationRoutes.js');
const checks = [
  ['Business UI reads server entitlement', panel.includes('FynxMonetizationClient.entitlements(context)')],
  ['Business UI reads server plan catalog', panel.includes('FynxMonetizationClient.plans(context)')],
  ['Business UI displays server plan and expiry', panel.includes('current.plan') && panel.includes('current.expiresAt')],
  ['client uses central authenticated backend transport', client.includes('FynxBackendClient.get(context, "/api/monetization/entitlements")')],
  ['client does not trust a client-supplied price', client.includes('/api/monetization/plans') && !client.includes('amountKobo:')],
  ['live charging remains server-controlled', backend.includes('chargingEnabled')],
  ['UI does not expose payment while charging is disabled', panel.includes('!catalog.chargingEnabled') && !panel.includes('initializePayment(context')],
];
for (const [name, ok] of checks) { console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`); if (!ok) process.exitCode = 1; }
if (process.exitCode) process.exit(1);
console.log(`GREEN: ${checks.length}/${checks.length} monetization UI contract checks passed`);
