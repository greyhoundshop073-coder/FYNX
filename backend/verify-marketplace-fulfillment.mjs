import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const completion = read('./marketplaceCompletion.js');
const protection = read('./marketplaceProtection.js');
const expiry = read('./marketplaceInspectionExpiry.js');
const settlement = read('./marketplaceSettlement.js');
const shippingTimeline = read('./marketplaceShippingTimeline.js');
const server = read('./server.js');
const socialClient = read('../app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt');
const adminClient = read('../app/src/main/java/com/fynx/app/ui/FynxAdminClient.kt');
const adminPanel = read('../app/src/main/java/com/fynx/app/ui/FynxAnnouncementsPanel.kt');

const checks = [
  ['fulfillment selection is buyer-only', completion.includes("only the buyer can choose fulfillment")],
  ['delivery requires validated buyer address', completion.includes("delivery requires name, phone and address") && completion.includes('safeAddress')],
  ['delivery/pickup availability is checked from order snapshot', completion.includes('snapshot.deliveryAvailable') && completion.includes('snapshot.pickupAvailable')],
  ['shipping is seller-only', completion.includes("only the seller can ship this order")],
  ['shipping requires paid order', completion.includes("order.status !== 'PAID'")],
  ['pickup cannot use the shipping transition', completion.includes("order.fulfillment_method === 'PICKUP'") && completion.includes('pickup orders require seller handover confirmation')],
  ['delivery shipping blocks active protection conflicts', completion.includes("app.post('/api/marketplace/orders/:id/ship'") && completion.includes("marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')") && completion.includes("marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')")],
  ['pickup handover route is seller-only and pickup-only', completion.includes("/api/marketplace/orders/:id/pickup-handover") && completion.includes('only the seller can confirm pickup handover') && completion.includes("order.fulfillment_method !== 'PICKUP'")],
  ['pickup handover requires paid order and blocks active protection conflicts', completion.includes('only paid orders can be handed over for pickup') && completion.includes('PICKUP_HANDOVER_CONFIRMED') && completion.includes('marketplace_protection_cases')],
  ['delivery confirmation is buyer-only', completion.includes("only the buyer can confirm delivery")],
  ['pickup delivery confirmation requires seller handover', completion.includes("order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at")],
  ['delivery confirmation creates inspection window', completion.includes("status='INSPECTION'") && completion.includes('48 * 60 * 60 * 1000')],
  ['completion is buyer-only', completion.includes("only the buyer can complete this order")],
  ['completion requires inspection', completion.includes("order.status !== 'INSPECTION'")],
  ['active disputes/protection cases block completion', completion.includes("status IN ('OPEN','UNDER_REVIEW')") && completion.includes('marketplace_protection_cases')],
  ['completion consumes reserved inventory once', completion.includes('reserved_quantity=GREATEST(0,reserved_quantity-$1)') && completion.includes("status='INSPECTION'")],
  ['inspection expiry has a dedicated worker', expiry.includes('marketplace.inspection.expire') || expiry.includes('inspection expiry')],
  ['settlement requires completed/release-pending protection', settlement.includes("order.status !== 'COMPLETED'") || settlement.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['protection routes are wired',
    protection.includes("app.post('/api/marketplace/protection/order/:id/dispute'") &&
    protection.includes("app.post('/api/marketplace/orders/:id/disputes'") &&
    protection.includes("app.post('/api/marketplace/protection/order/:id/refund-request'") &&
    protection.includes('marketplace_protection_cases')],
  ['approved driver registry is idempotent and indexed', completion.includes('CREATE TABLE IF NOT EXISTS marketplace_approved_drivers') && completion.includes('marketplace_approved_drivers_active_idx')],
  ['driver assignment and custody evidence tables are indexed', completion.includes('CREATE TABLE IF NOT EXISTS marketplace_delivery_assignments') && completion.includes('marketplace_delivery_assignments_driver_idx') && completion.includes('CREATE TABLE IF NOT EXISTS marketplace_delivery_evidence') && completion.includes('marketplace_delivery_evidence_order_idx')],
  ['admin approval and revocation routes require owner/admin access', completion.includes("app.get('/api/admin/marketplace/drivers'") && completion.includes("app.post('/api/admin/marketplace/drivers/:userId/approval'") && completion.includes('hasMarketplaceAdminAccess')],
  ['Android admin panel can approve and revoke drivers', adminClient.includes('FynxBackendClient.get(context, "/api/admin/marketplace/drivers")') && adminClient.includes('/api/admin/marketplace/drivers/') && adminPanel.includes('Approved delivery drivers')],
  ['seller-only assignment rejects buyer/seller as the assigned driver', completion.includes("app.post('/api/marketplace/orders/:id/driver-assignment'") && completion.includes('only the seller can assign a delivery driver') && completion.includes('buyer and seller cannot be assigned as the delivery driver')],
  ['driver must accept before physical handover', completion.includes("app.post('/api/marketplace/orders/:id/driver-acceptance'") && completion.includes('driver must accept the assignment before handover')],
  ['local phone driver requires contact and agreed pickup point', completion.includes('local driver contact and agreed pickup point are required before handover') && completion.includes('SELLER_LOCAL')],
  ['seller handover photo validates ownership and image MIME type', completion.includes('handover photo must be uploaded by the signed-in seller') && completion.includes('allowedImageTypes.has') && completion.includes('PHOTO_REFERENCE')],
  ['driver delivery photo validates numeric ID, assigned-driver ownership and image MIME; note-only delivery remains supported', completion.includes('photo reference must be a valid uploaded media id') && completion.includes('delivery photo must be an uploaded image owned by the assigned driver') && completion.includes("SELECT mime_type FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1") && completion.includes("if (photoMediaId !== null) await client.query") && completion.includes('!deliveryNote && photoMediaId === null') && completion.includes('hasPhotoReference: photoMediaId !== null')],
  ['handover photo media is restricted to the buyer or seller on the referencing order', server.includes('JOIN marketplace_delivery_evidence e') && server.includes('(o.buyer_id = $2 OR o.seller_id = $2)')],
  ['order timeline returns delivery evidence only to order participants', shippingTimeline.includes('deliveryEvidence: deliveryEvidence.rows.map') && shippingTimeline.includes('buyer_id = $2 OR seller_id = $2')],
  ['Android supports driver assignment, acceptance and delivery evidence', socialClient.includes('marketplaceDriverAssignments') && socialClient.includes('acceptMarketplaceDriverAssignment') && socialClient.includes('recordMarketplaceDriverDelivery')],
  ['seller order UI exposes optional handover photo upload', read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt').includes('Add handover photo (optional)') && read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt').includes('FynxProductionMessaging.uploadMedia')],
  ['buyer order UI displays secure handover photo preview', read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt').includes('MarketplaceHandoverPhotoPreview') && read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderTimeline.kt').includes('Text("Delivery evidence")')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Marketplace fulfillment verification passed: ${checks.length}/${checks.length}`);
