import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const completion = read('./marketplaceCompletion.js');
const server = read('./server.js');
const protection = read('./marketplaceProtection.js');
const expiry = read('./marketplaceInspectionExpiry.js');
const settlement = read('./marketplaceSettlement.js');
const sellerUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerOrders.kt');
const buyerUi = read('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceOrderLifecycle.kt');

const checks = [
  ['seller fulfillment transitions lock the order row', completion.includes("SELECT * FROM marketplace_orders WHERE id=$1 FOR UPDATE")],
  ['delivery shipping is seller-only and paid-only', completion.includes('only the seller can ship this order') && completion.includes("order.status !== 'PAID'")],
  ['pickup cannot fall through the delivery shipping transition', completion.includes("order.fulfillment_method === 'PICKUP'") && completion.includes('pickup orders require seller handover confirmation')],
  ['delivery shipping requires the buyer delivery details', completion.includes("order.fulfillment_method === 'DELIVERY' && !order.shipping_address")],
  ['pickup handover serializes on the paid state', completion.includes("WHERE id=$2 AND status='PAID' RETURNING *")],
  ['pickup handover blocks active protection conflicts', completion.includes("marketplace_order_disputes WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')") && completion.includes("marketplace_protection_cases WHERE order_id=$1 AND status IN ('OPEN','UNDER_REVIEW')")],
  ['buyer receipt confirmation locks the order row', completion.includes("only the buyer can confirm delivery") && completion.includes("WHERE id=$2 RETURNING *")],
  ['pickup receipt cannot bypass seller handover', completion.includes("order.fulfillment_method === 'PICKUP' && !order.pickup_handover_at")],
  ['receipt confirmation enters a bounded 48-hour inspection state', completion.includes("status='INSPECTION'") && completion.includes('48 * 60 * 60 * 1000')],
  ['buyer completion is inspection-only', completion.includes("only the buyer can complete this order") && completion.includes("order.status !== 'INSPECTION'")],
  ['active protection blocks buyer completion', completion.includes("status IN ('OPEN','UNDER_REVIEW')") && completion.includes('marketplace_protection_cases')],
  ['inspection expiry has its own worker path', expiry.includes('inspection expiry') || expiry.includes('marketplace.inspection.expire')],
  ['settlement remains gated behind completed/release-pending state', settlement.includes("order.status !== 'COMPLETED'") || settlement.includes("escrow.status !== 'RELEASE_PENDING'")],
  ['seller Android uses the dedicated pickup handover route', sellerUi.includes('/api/marketplace/orders/${order.id}/pickup-handover') && sellerUi.includes('Confirm pickup handover')],
  ['seller Android keeps delivery on the shipping route', sellerUi.includes('/api/marketplace/orders/${order.id}/ship') && sellerUi.includes('Mark dispatched')],
  ['buyer Android reaches the same receive/inspection lifecycle', buyerUi.includes('Confirm received') && buyerUi.includes('Complete order')],
  ['protection creation serializes on the order row', protection.includes('FROM marketplace_orders WHERE id=$1 FOR UPDATE')],
  ['protection changes the order to DISPUTED inside its transaction', protection.includes("UPDATE marketplace_orders SET status='DISPUTED'") && protection.includes('COMMIT')],

  ['FYNX-account and seller-local driver custody modes are both supported', completion.includes("'FYNX_ACCOUNT','SELLER_LOCAL'") && completion.includes("driverType === 'SELLER_LOCAL'")],
  ['seller-local driver assignment requires driver contact details and an agreed pickup point', completion.includes('local driver name, phone, and agreed pickup point are required') && completion.includes('seller-local drivers do not use FYNX account acceptance')],
  ['FYNX drivers require an active approval and explicit acceptance before seller handover', completion.includes('only the assigned driver can accept this delivery') && completion.includes('driver must accept the assignment before handover') && completion.includes('assigned driver approval is inactive')],
  ['local-driver arrival is separate from parcel handover and buyer receipt', completion.includes('LOCAL_DRIVER_ARRIVAL_REPORTED') && completion.includes('record the local driver handover before reporting arrival') && completion.includes('assigned driver has not recorded delivery evidence yet')],
  ['delivery details and evidence are returned only to the order buyer or seller', completion.includes('only the buyer or seller can view delivery details') && completion.includes('SELECT evidence_type, value, created_at FROM marketplace_delivery_evidence')],
  ['handover photo upload verifies seller ownership and restricts image MIME types', completion.includes('SELECT mime_type FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1') && completion.includes("new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])") && completion.includes("VALUES ($1,$2,'PHOTO_REFERENCE',$3)")],
  ['shared media retrieval restricts handover photos to the buyer or seller on the referencing order', server.includes("JOIN marketplace_delivery_evidence e") && server.includes("e.evidence_type = 'PHOTO_REFERENCE'") && server.includes('(o.buyer_id = $2 OR o.seller_id = $2)')],
  ['seller Android uploads and previews handover evidence through the existing authenticated media flow', sellerUi.includes('FynxProductionMessaging.uploadMedia(context, photoUri)') && sellerUi.includes('MarketplaceHandoverPhotoPreview(context, order.id, handoverPhotoMediaId)') && sellerUi.includes('put("photoReference", photoReference)')],
  ['buyer Android loads order-authorized delivery details and uses the protected shared media route', buyerUi.includes('/api/marketplace/orders/${order.id}/delivery-details') && buyerUi.includes('MarketplaceHandoverPhotoPreview(context, order.id, handoverPhotoMediaId)') && buyerUi.includes('"/api/media/$id"')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) {
  console.error('R6-F fulfillment integrity verification FAILED');
  process.exit(1);
}
console.log(`R6-F fulfillment integrity verification GREEN (${checks.length} checks)`);
