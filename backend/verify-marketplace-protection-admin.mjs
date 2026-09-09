import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const resolution = read('./marketplaceProtectionResolution.js');
const adminClient = read('../app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt');
const adminPanel = read('../app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt');

const checks = [
  ['admin case list route exists', resolution.includes("app.get('/api/admin/marketplace/protection/cases'")],
  ['admin case list is authenticated', resolution.includes("app.get('/api/admin/marketplace/protection/cases', auth")],
  ['admin case list enforces administrator access', resolution.includes("administrator access required")],
  ['admin resolution route exists', resolution.includes("app.post('/api/admin/marketplace/protection/cases/:id/resolve'")],
  ['resolution allowlist is enforced', resolution.includes("['BUYER','SELLER','CANCEL']")],
  ['buyer refund uses deterministic idempotency key', resolution.includes('REFUND-${row.order_id}')],
  ['refund pending state protects escrow', resolution.includes("status='REFUND_PENDING'")],
  ['seller resolution makes escrow release eligible', resolution.includes("escrowStatus = resolution === 'SELLER' ? 'RELEASE_ELIGIBLE' : 'CANCELLED'")],
  ['cancel resolution preserves reserved inventory recovery', resolution.includes("status='CANCELLED'") && resolution.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)')],
  ['protection resolution writes audit trail', resolution.includes('marketplace_protection_audit') && resolution.includes('RESOLVED_${resolution}')],
  ['android client exposes protection case lookup', adminClient.includes('marketplaceProtectionCases')],
  ['android client exposes protection resolution', adminClient.includes('resolveMarketplaceProtectionCase')],
  ['admin UI displays protection cases', adminPanel.includes('Marketplace protection cases')],
  ['admin UI provides buyer refund action', adminPanel.includes('resolution = "BUYER"')],
  ['admin UI provides seller release action', adminPanel.includes('resolution = "SELLER"')],
  ['admin UI provides cancellation action', adminPanel.includes('resolution = "CANCEL"')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
if (failed.length) {
  console.error('Marketplace protection admin guard FAILED');
  for (const name of failed) console.error(` - ${name}`);
  process.exit(1);
}

console.log(`Marketplace protection admin guard GREEN (${checks.length} checks)`);
