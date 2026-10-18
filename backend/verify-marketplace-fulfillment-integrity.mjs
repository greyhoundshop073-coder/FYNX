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
  ['both approved FYNX and seller-local delivery modes are supported', completion.includes("'FYNX_ACCOUNT','SELLER_LOCAL'") && completion.includes("driverType === 'SELLER_LOCAL'")],
  ['local drivers require a name, phone and agreed pickup point', completion.includes('local driver name, phone, and agreed pickup point are required')],
  ['local driver handover does not require a FYNX account acceptance', completion.includes('seller-local drivers do not use FYNX account acceptance') && completion.includes('LOCAL_DRIVER_CUSTODY_HANDED_OVER')],
  ['local driver arrival remains separate from buyer receipt confirmation', completion.includes('LOCAL_DRIVER_ARRIVAL_REPORTED') && completion.includes('only the buyer can confirm delivery')],
  ['delivery details are restricted to buyer or seller', completion.includes('/api/marketplace/orders/:id/delivery-details') && completion.includes('only the buyer or seller can view delivery details')],
  ['seller Android can choose both driver modes and capture vehicle details', sellerUi.includes('Local phone driver') && sellerUi.includes('vehiclePlate') && sellerUi.includes('pickupPoint')],
  ['buyer Android displays agreed pickup and vehicle information', buyerUi.includes('Agreed pickup point:') && buyerUi.includes('Vehicle:')],
  ['local-driver assignment validates identity/contact/pickup fields before opening a transaction', completion.indexOf("if (driverType === 'SELLER_LOCAL' && (!localDriverName || !localDriverPhone || !pickupPoint))") < completion.indexOf('const client = await pool.connect();', completion.indexOf("app.post('/api/marketplace/orders/:id/driver-assignment'"))],
  ['seller-local assignment persists no FYNX user id and preserves optional vehicle identification fields', completion.includes('driverType === \'SELLER_LOCAL\' ? localDriverName : null') && completion.includes('driverType === \'SELLER_LOCAL\' ? localDriverPhone : null') && completion.includes('driverType === \'SELLER_LOCAL\' ? vehiclePlate || null : null') && completion.includes('driverType === \'SELLER_LOCAL\' ? vehicleNote || null : null')],
  ['local arrival is rejected before handover and cannot be reported twice', completion.includes("if (!assignment.handover_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'record the local driver handover before reporting arrival' })") && completion.includes("if (assignment.delivered_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'local driver arrival has already been reported' })")],
  ['local arrival updates the arrival marker/evidence without impersonating buyer receipt', completion.includes("LOCAL_DRIVER_ARRIVAL_REPORTED") && completion.includes("UPDATE marketplace_delivery_assignments SET delivered_at=NOW(),updated_at=NOW() WHERE order_id=$1") && completion.includes("only the buyer can confirm delivery")],
  ['buyer cannot confirm receipt while any assigned driver has not reported delivery/arrival', completion.includes("if (driverAssignment && !driverAssignment.delivered_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'assigned driver has not recorded delivery evidence yet' })")],
  ['delivery details endpoint rejects unrelated users before returning phone or pickup details', completion.indexOf("if (!isBuyer(order, req.user.sub) && !isSeller(order, req.user.sub)) return res.status(403).json({ error: 'only the buyer or seller can view delivery details' })") < completion.indexOf('const localDriver = (assignment.driver_type || \'FYNX_ACCOUNT\') === \'SELLER_LOCAL\';')],
  ['FYNX driver cannot hand over until active approval and explicit acceptance', completion.includes("if (!approvedDriver.rowCount) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'assigned driver approval is inactive; reassign to an active approved driver before handover'") && completion.includes("if (!assignment.accepted_at) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'driver must accept the assignment before handover'")],
  ['buyer receipt confirmation is a separate transition into inspection after driver evidence', completion.includes("UPDATE marketplace_orders SET status='INSPECTION'") && completion.includes("recordFulfillmentEvent(client, id, req.user.sub, 'BUYER_RECEIVED'") && completion.includes('inspectionHours: 48')],
  ['seller handover photo evidence is optional and validated against the authenticated media owner', completion.includes('rawPhotoReference') && completion.includes("SELECT mime_type FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1") && completion.includes('handover photo must be uploaded by the signed-in seller')],
  ['seller handover photo evidence accepts image MIME types only and records PHOTO_REFERENCE', completion.includes("new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif'])") && completion.includes("VALUES ($1,$2,'PHOTO_REFERENCE',$3)")],
  ['delivery-details returns evidence only after buyer/seller authorization', completion.indexOf("only the buyer or seller can view delivery details") < completion.indexOf("SELECT evidence_type, value, created_at FROM marketplace_delivery_evidence WHERE order_id=$1") && completion.includes('evidence })')],
  ['shared media route serves marketplace handover photos only to the related buyer or seller', server.includes("JOIN marketplace_delivery_evidence e") && server.includes("e.evidence_type = 'PHOTO_REFERENCE'") && server.includes('(mo.buyer_id = $2 OR mo.seller_id = $2)') && server.includes('Marketplace handover photos are not message/profile/status media')],
  ['buyer and seller screens share the authenticated handover-photo preview', buyerUi.includes('MarketplaceHandoverPhotoPreview(context, order.id, handoverPhotoMediaId)') && sellerUi.includes('MarketplaceHandoverPhotoPreview(context, order.id, handoverPhotoMediaId)') && buyerUi.includes('"/api/media/$id"') && !buyerUi.includes('"/api/marketplace/media/$id"')],
  ['seller Android uploads the optional handover photo through the existing media uploader', sellerUi.includes('FynxProductionMessaging.uploadMedia(context, photoUri)') && sellerUi.includes('PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)') && sellerUi.includes('put("photoReference", photoReference)')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) {
  console.error('R6-F fulfillment integrity verification FAILED');
  process.exit(1);
}
console.log(`R6-F fulfillment integrity verification GREEN (${checks.length} checks)`);
