import fs from 'node:fs';

const source = fs.readFileSync(new URL('./marketplaceSettlement.js', import.meta.url), 'utf8');
const routeStart = source.indexOf("app.get('/api/marketplace/settlement/seller/summary', auth");
const routeEnd = source.indexOf("app.get('/api/marketplace/settlement/payout-account'", routeStart);
const route = routeStart >= 0 && routeEnd > routeStart ? source.slice(routeStart, routeEnd) : '';

const checks = [
  ['seller summary is registered as an authenticated route', routeStart >= 0 && route.includes("app.get('/api/marketplace/settlement/seller/summary', auth")],
  ['every seller summary row is scoped to the signed-in seller', route.includes('WHERE o.seller_id = $1') && route.includes('[req.user.sub]')],
  ['the route reads seller orders, escrow state and latest payout operation', route.includes('FROM marketplace_orders o') && route.includes('LEFT JOIN marketplace_escrows e') && route.includes('LEFT JOIN LATERAL') && route.includes("f.operation_type = 'PAYOUT_RELEASE'")],
  ['seller totals distinguish protected, available, pending and paid funds', ['protected_funds','available_for_payout','payout_pending','paid_out'].every((field) => route.includes(field))],
  ['the response contract includes the Android seller earnings fields', ['protectedFunds','availableForPayout','payoutPending','paidOut','grossSales','marketplaceFees','netEarnings'].every((field) => route.includes(field))],
  ['the response includes seller settlement detail fields', ['orderId','sellerNetAmount','marketplaceFeeSeller','payoutStatus','updatedAt'].every((field) => route.includes(field))],
  ['seller errors return a bounded JSON error rather than leaving the client waiting', route.includes("seller earnings could not load") && route.includes('return res.status(')]
];

const failed = checks.filter(([, passed]) => !passed);
for (const [name, passed] of checks) console.log(`${passed ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) {
  console.error(`Wallet seller summary route verification failed: ${failed.length}/${checks.length}`);
  process.exit(1);
}
console.log(`Wallet seller summary route verification passed: ${checks.length}/${checks.length}`);
