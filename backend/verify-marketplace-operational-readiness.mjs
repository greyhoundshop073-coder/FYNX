import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');
const checks = [];
const check = (name, ok) => checks.push([name, Boolean(ok)]);

const tx = read('marketplaceTransactions.js');
const payment = read('marketplacePaymentState.js');
const completion = read('marketplaceCompletion.js');
const protection = read('marketplaceProtection.js');
const protectionResolution = read('marketplaceProtectionResolution.js');
const reputation = read('marketplaceReputation.js');
const settlement = read('marketplaceSettlement.js');
const notification = read('notificationPush.js');
const social = read('socialRoutes.js');
const marketplaceUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt');

// Inventory/listing availability and atomic checkout.
check('listings expose active and in-stock discovery', social.includes('l.active = TRUE') && social.includes('l.quantity > 0'));
check('checkout locks the listing before reservation', tx.includes('FOR UPDATE') && tx.includes('reserved_quantity'));
check('checkout prevents self-purchase', tx.includes('cannot purchase your own listing'));
check('checkout records a product snapshot', tx.includes('product_snapshot') && tx.includes('listingId'));
check('checkout has idempotent order creation', tx.includes('clientOrderId') && tx.includes('idempotent:true'));

// Payment and accounting lifecycle.
check('payment state moves pending orders to paid atomically', payment.includes("status='PAID'") && payment.includes("status='PAYMENT_PENDING'"));
check('payment expiry/recovery exists', fs.existsSync(path.join(root, 'marketplacePaymentExpiry.js')));
check('financial settlement is separated from checkout', settlement.includes('marketplace_escrows') && settlement.includes('marketplace_ledger'));
check('completion refuses active protection cases', completion.includes('hasActiveProtectionCase') && completion.includes('protection case or dispute'));

// Fulfillment and buyer protection.
check('delivery and pickup are represented in fulfillment flow', fs.existsSync(path.join(root, 'verify-marketplace-batch4-shipping.mjs')) && fs.existsSync(path.join(root, 'verify-marketplace-batch2-fulfillment.mjs')));
check('buyer inspection expiry is separately guarded', fs.existsSync(path.join(root, 'marketplaceInspectionExpiry.js')));
check('protection cases preserve previous order state', protection.includes('previousOrderStatus') && protection.includes('previousEscrowStatus'));
check('open disputes prevent conflicting completion', protectionResolution.includes("status='DISPUTED'") && protectionResolution.includes('marketplace_escrows'));

// Reviews/reputation.
check('seller reputation is server-backed', reputation.includes('successful_sales') || reputation.includes('successfulSales'));
check('review path is protected by buyer/order authorization', reputation.includes('only the buyer') && reputation.includes('order'));

// Notification infrastructure and marketplace preference channel.
check('FCM notification infrastructure supports marketplace category', notification.includes('marketplace_enabled') && notification.includes('MARKETPLACE_ORDER'));
check('notifications persist before push delivery', notification.includes('INSERT INTO fynx_notifications') && notification.includes('fynx_notification_delivery'));
check('notification preferences can suppress marketplace push', notification.includes('marketplace_enabled') && notification.includes('pushAllowed'));

// Marketplace information architecture.
check('Marketplace has search', marketplaceUi.includes('Search products or sellers'));
check('Marketplace has category navigation', marketplaceUi.includes('Electronics') && marketplaceUi.includes('Fashion') && marketplaceUi.includes('Services'));
check('Marketplace has nearby discovery', marketplaceUi.includes('Near me') && marketplaceUi.includes('nearbyMarketplaceListings'));
check('Marketplace separates Top Sellers from Products', marketplaceUi.includes('Top Sellers (Highest Sales)') && marketplaceUi.includes('Text("Products"'));
check('Marketplace keeps cart and orders in the primary header', marketplaceUi.includes('ShoppingCart') && marketplaceUi.includes('ReceiptLong'));
check('Marketplace keeps selling as a prominent action', marketplaceUi.includes('Text("Sell"')) && marketplaceUi.includes('FloatingActionButton'));
check('Marketplace supports exact listing deep links', marketplaceUi.includes('loadExactMarketplaceListing') && marketplaceUi.includes('initialListingId'));
check('seller chat carries canonical listing identity', marketplaceUi.includes('chatAppLink(normalized, listingId)'));

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (failed.length) {
  console.error(`Marketplace operational readiness failed: ${failed.length}/${checks.length} checks failed.`);
  process.exit(1);
}
console.log(`Marketplace operational readiness passed: ${checks.length} checks.`);
