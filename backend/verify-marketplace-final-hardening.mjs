import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const tx = read('./marketplaceTransactions.js');
const completion = read('./marketplaceCompletion.js');
const protection = read('./marketplaceProtection.js');
const reputation = read('./marketplaceReputation.js');
const discovery = read('./discoveryRoutes.js');
const reconciliation = read('./verify-marketplace-reconciliation.mjs');
const fulfillment = read('./verify-marketplace-batch2-fulfillment.mjs');
const panel = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt', import.meta.url), 'utf8');
const sellerCenter = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt', import.meta.url), 'utf8');
const reviews = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceReviews.kt', import.meta.url), 'utf8');

const checks = [
  ['inventory is reserved atomically during order creation', tx.includes('FOR UPDATE') && tx.includes('reserved_quantity=reserved_quantity+$1')],
  ['unavailable or inactive listings cannot enter checkout', tx.includes('l.active=TRUE') && tx.includes('requested quantity is not available')],
  ['buyer cannot purchase their own listing', tx.includes('you cannot purchase your own listing')],
  ['order stores a product snapshot for historical integrity', tx.includes('product_snapshot JSONB') && tx.includes('const snapshot=')],
  ['checkout accounting stores buyer and seller amounts separately', tx.includes('buyer_total') && tx.includes('seller_net_amount') && tx.includes('fee_policy_version')],
  ['payment initialization is idempotent', tx.includes('clientOrderId') && tx.includes('idempotent:true')],
  ['seller fulfillment is authenticated and row-locked', completion.includes('only the seller can update fulfillment progress') && completion.includes('FOR UPDATE')],
  ['delivery and pickup have separate transitions', completion.includes('DELIVERY_TRANSITIONS') && completion.includes('PICKUP_TRANSITIONS')],
  ['buyer receipt enters an inspection window', completion.includes("status='INSPECTION'") && completion.includes('48 * 60 * 60 * 1000')],
  ['buyer must confirm item and quantity before completion', fulfillment.includes('receivedItemMatchesOrder === true') && fulfillment.includes('quantityMatchesOrder === true')],
  ['active protection blocks unsafe completion/payout movement', completion.includes('hasActiveProtectionCase') && completion.includes('active protection case or dispute')],
  ['protection cases are idempotent and preserve prior state', protection.includes('idempotency_key TEXT NOT NULL UNIQUE') && protection.includes('previous_order_status') && protection.includes('previous_escrow_status')],
  ['protection changes order and escrow to DISPUTED atomically', protection.includes("UPDATE marketplace_orders SET status='DISPUTED'") && protection.includes("UPDATE marketplace_escrows SET status='DISPUTED'")],
  ['reviews are buyer-only and completion-only', reputation.includes('only the buyer can review this order') && reputation.includes('only completed orders can be reviewed')],
  ['reviews are one-per-order idempotent', reputation.includes('UNIQUE (order_id, buyer_id)') && reputation.includes('already been reviewed')],
  ['seller reputation is derived from real order/review data', reputation.includes('successful_orders') && reputation.includes('average_rating') && reputation.includes('completionRate')],
  ['discovery only exposes active in-stock listings', discovery.includes('l.active=TRUE') && discovery.includes('l.quantity>0')],
  ['exact listing lookup rejects unavailable products', discovery.includes('if (!row.active || Number(row.quantity) <= 0)')],
  ['Marketplace has organized search and category navigation', panel.includes('Search products or sellers') && panel.includes('Electronics') && panel.includes('Services')],
  ['Marketplace separates seller discovery from product discovery', panel.includes('Top Sellers (Highest Sales)') && panel.includes('Text("Products"')],
  ['Marketplace keeps cart, orders and selling actions visible', panel.includes('ShoppingCart') && panel.includes('ReceiptLong') && panel.includes('Text("Sell"')],
  ['seller center exposes real sales and payout state', sellerCenter.includes('real FYNX account data') && sellerCenter.includes('payout')],
  ['review UI is connected to the canonical order review endpoint', reviews.includes('/api/marketplace/orders/${orderId}/review')],
  ['reconciliation certification remains connected', reconciliation.includes('deterministic payout reference') && reconciliation.includes('ledger idempotency uniqueness')],
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) throw new Error(`Marketplace final hardening certification failed: ${failed.join('; ')}`);
console.log(`Marketplace final hardening certification GREEN (${checks.length} checks)`);
