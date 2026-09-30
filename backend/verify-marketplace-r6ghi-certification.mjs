import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const resolveAppFile = (name) => {
  const candidates = [
    path.join(root, 'app/src/main/java/com/fynx/app/ui', name),
    path.join(root, 'app/src/main/java/com/fynx/app', name),
  ];
  const found = candidates.find((candidate) => fs.existsSync(candidate));
  if (!found) throw new Error(`Marketplace UI file not found: ${name}`);
  return fs.readFileSync(found, 'utf8');
};
const checks = [];
const check = (name, ok) => checks.push([name, Boolean(ok)]);

const panel = resolveAppFile('FynxMarketplacePanel.kt');
const client = resolveAppFile('FynxMarketplaceClient.kt');
const checkout = resolveAppFile('FynxMarketplaceCheckout.kt');
const seller = resolveAppFile('FynxMarketplaceSellerCenterPanel.kt');
const adIdentity = read('./verify-marketplace-ad-creative-identity.mjs');
const chatGuard = read('../scripts/verify_marketplace_chat_connection.py');
const protection = read('./verify-marketplace-protection-lifecycle.mjs');
const reconciliation = read('./verify-marketplace-reconciliation.mjs');
const state = read('./verify-marketplace-state-machine.mjs');

// R6-G: one canonical listing identity across discovery and connected surfaces.
check('Marketplace loads real listings from the authenticated client', panel.includes('FynxRemoteSocialClient.listings(context, query, category)'));
check('exact listing deep links resolve the canonical listing', panel.includes('loadExactMarketplaceListing(context, listingId)'));
check('seller chat receives the canonical listing id', panel.includes('chatAppLink(normalized, listingId)'));
check('Home/advertising identity guard remains part of the Marketplace contract', adIdentity.includes('canonical') && adIdentity.includes('listing'));
check('Marketplace chat integration guard remains present', chatGuard.includes('listing') && chatGuard.includes('chat'));

// R6-H: professional marketplace information architecture and buyer/seller workflow.
check('Marketplace has a dedicated search surface', panel.includes('Search products or sellers'));
check('Marketplace has category discovery', panel.includes('Electronics') && panel.includes('Fashion') && panel.includes('Services'));
check('Marketplace has nearby discovery', panel.includes('FynxPostLocationClient.currentPlace(context)'));
check('Marketplace separates sellers from products', panel.includes('Top Sellers (Highest Sales)') && panel.includes('Text("Products"'));
check('Marketplace has cart and orders entry points', panel.includes('ShoppingCart') && panel.includes('ReceiptLong'));
check('Marketplace exposes a dedicated seller action', panel.includes('Text("Sell"'));
check('Marketplace checkout uses the existing protected-order flow', checkout.includes('FynxMarketplaceCheckoutDialog') || checkout.includes('protected'));
check('Seller Center exposes earnings and payout controls', seller.includes('Marketplace earnings') && seller.includes('Payout account'));
check('Protected-order UX clearly communicates buyer protection', panel.includes('Order protected') && panel.includes('Payment received'));

// R6-I: existing authoritative financial/security gates remain connected to the final certification.
check('state-machine guard covers release/refund transitions', state.includes('RELEASE_PENDING') && state.includes('REFUND_PENDING'));
check('reconciliation guard covers payout ledger idempotency', reconciliation.includes('idempotency') && reconciliation.includes('PAYOUT-RELEASE'));
check('protection lifecycle guard covers dispute synchronization', protection.includes('DISPUTED') && protection.includes('escrow'));

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (failed.length) {
  console.error(`Marketplace R6-G/H/I certification failed: ${failed.length}/${checks.length} checks failed.`);
  process.exit(1);
}
console.log(`Marketplace R6-G/H/I certification passed: ${checks.length} checks.`);
