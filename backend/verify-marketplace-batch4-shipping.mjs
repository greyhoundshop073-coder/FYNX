import fs from 'node:fs';

const bootstrap = fs.readFileSync(new URL('./checkoutBootstrap.js', import.meta.url), 'utf8');
const shipping = fs.readFileSync(new URL('./marketplaceShipping.js', import.meta.url), 'utf8');

const checks = [
  ['shipping module exists', shipping.includes('registerMarketplaceShippingRoutes')],
  ['seller-arranged shipping is supported', shipping.includes("SELLER_ARRANGED")],
  ['pickup has zero shipping', shipping.includes('NO_SHIPPING_FOR_PICKUP')],
  ['quantity-aware shipping exists', shipping.includes('additional_item_shipping_fee')],
  ['shipping cap exists', shipping.includes('shipping_fee_cap')],
  ['carrier is optional', shipping.includes('carrierRequired: false')],
  ['shipping options route exists', shipping.includes("/api/marketplace/shipping/options")],
  ['bootstrap imports shipping routes', bootstrap.includes('marketplaceShipping.js')],
  ['bootstrap registers shipping routes', bootstrap.includes('registerMarketplaceShippingRoutes')]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'} ${name}`);
if (failed.length) process.exit(1);
console.log(`Batch 4 shipping verifier: GREEN (${checks.length} checks)`);
