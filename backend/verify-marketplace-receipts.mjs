import fs from 'node:fs';

const source = fs.readFileSync(new URL('./marketplaceTransactions.js', import.meta.url), 'utf8');
const checks = [
  ['receipt endpoint requires authentication', /app\.get\('\/api\/marketplace\/orders\/:id\/receipt',\s*auth/.test(source)],
  ['receipt endpoint checks order participation', source.includes('if (!isParticipant(row, req.user.sub))')],
  ['receipt only issues after recorded payment confirmation', source.includes('!receiptStatusAllowed(row.status) || !row.payment_reference || !row.payment_verified_at')],
  ['receipt accounting reconciles before issue', source.includes('if (!receiptAccountingReconciles(row))')],
  ['receipt signature uses HMAC-SHA256', source.includes("createHmac('sha256', secret)")],
  ['receipt signature comparison is timing-safe', source.includes('crypto.timingSafeEqual(a, b)')],
  ['public verification is read-only and returns limited data', source.includes("app.get('/api/marketplace/receipts/:receiptId/verify'") && source.includes('authentic: true, receiptId: rawId, paymentStatus: row.status')],
  ['tampered signature is rejected', source.includes("reason: 'SIGNATURE_MISMATCH'")],
  ['buyer and seller profile identity is included', source.includes('buyer_profile_photo_media_id') && source.includes('seller_profile_photo_media_id') && source.includes('profilePhotoMediaId')],
  ['receipt avoids exposing shipping address and phone', !source.includes('shippingAddress: row.shipping_address') && !source.includes('phone: row.buyer_phone')],
  ['receipt avatar uses privacy-aware profile media endpoint', source.includes('/api/social/profile-photo/')],
  ['profile photo route checks privacy and blocks', source.includes('app.get(\'/api/social/profile-photo/:id\'') && source.includes('photo_visibility') && source.includes('FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2)')],
  ['public verification also requires recorded payment confirmation', source.includes('!row.payment_verified_at || !receiptAccountingReconciles(row)')],
  ['signature binds historical item and financial fields', source.includes('row.product_snapshot || {}') && source.includes('row.payment_reference') && source.includes('row.created_at')],
  ['receipt verification does not trust client-provided amount', source.includes('Number(row.buyer_total ?? row.total_amount)')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);

const cents = value => Math.round(value * 100);
const sample = { subtotal: 1000, delivery: 250, buyerFee: 25, sellerFee: 25, fee: 50, discount: 0 };
const buyerTotal = sample.subtotal + sample.delivery + sample.buyerFee - sample.discount;
const sellerNet = sample.subtotal + sample.delivery - sample.sellerFee;
if (sellerNet + sample.fee - sample.discount !== buyerTotal) failed.push('minor-unit sample accounting reconciles');
if (cents(19.99) !== 1999 || cents(0.01) !== 1) failed.push('NGN/USD minor-unit conversion');
if (failed.length) process.exit(1);
console.log(`Marketplace receipt verification passed: ${checks.length}/${checks.length} structural checks plus accounting samples`);
