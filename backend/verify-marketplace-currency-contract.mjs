import fs from 'node:fs';
const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const social = read('./socialRoutes.js');
const discovery = read('./discoveryRoutes.js');
const checkout = read('./marketplaceCheckoutOrder.js');
const settlement = read('./marketplaceSettlement.js');
const worker = read('./marketplaceSettlementWorker.js');
const refund = read('./marketplaceProtectionResolution.js');
const payment = read('./marketplaceReputation.js');
const transactions = read('./marketplaceTransactions.js');
const quote = read('./marketplaceCheckout.js');
const ai = read('./fynxAiToolRegistry.js');
const sellerCenter = fs.readFileSync(new URL('../app/src/main/java/com/fynx/app/ui/FynxMarketplaceSellerCenterPanel.kt', import.meta.url), 'utf8');
const checks = [
  ['AI Marketplace search includes NGN and USD listings', ai.includes("UPPER(COALESCE(l.currency,'')) IN ('NGN','USD')")],
  ['legacy Marketplace order route accepts NGN and USD', transactions.includes("!['NGN','USD'].includes(currency)")],
  ['checkout quote keeps the FYNX 5% seller fee', quote.includes("FYNX_MARKETPLACE_FEE_MODE || 'SELLER'") && quote.includes("FYNX_MARKETPLACE_FEE_BPS || '500'")],
  ['checkout quote accepts NGN and USD', quote.includes("!['NGN', 'USD'].includes(currency)")],
  ['multi-product checkout rejects mixed currencies', quote.includes('a multi-product checkout cannot mix NGN and USD')],
  ['protected checkout keeps the FYNX 5% seller fee', checkout.includes("FYNX_MARKETPLACE_FEE_MODE || 'SELLER'") && checkout.includes("FYNX_MARKETPLACE_FEE_BPS || '500'")],
  ['new Marketplace listings accept NGN and USD', social.includes('!["NGN","USD"].includes(currency)')],
  ['legacy Marketplace discovery includes NGN and USD', social.includes("UPPER(COALESCE(l.currency,'')) IN ('NGN','USD')")],
  ['smart discovery and exact listing lookup include NGN and USD', discovery.includes("UPPER(COALESCE(l.currency,'')) IN ('NGN','USD')") && discovery.includes("WHERE l.id=$1 AND UPPER(COALESCE(l.currency,'')) IN ('NGN','USD')")],
  ['future unsupported currencies remain blocked at checkout', checkout.includes('UNSUPPORTED_MARKETPLACE_CURRENCY')],
  ['payment preserves the original order currency', payment.includes('currency: String(existing.currency).toUpperCase()')],
  ['Nigerian Paystack payout account validation remains NGN-only', settlement.includes("if (currency !== 'NGN') return res.status(400)")],
  ['Paystack bank picker is restricted to Nigerian NGN banks', settlement.includes('/bank?country=nigeria&currency=NGN&perPage=100')],
  ['seller payout UI sends NGN and uses the Paystack bank list', sellerCenter.includes('.put("currency", "NGN")') && sellerCenter.includes('/api/marketplace/settlement/payout-banks') && !sellerCenter.includes('payoutCurrency')],
  ['Seller Center still reads payout accounts', sellerCenter.includes('payoutAccounts') && sellerCenter.includes('optJSONArray("payoutAccounts")')],
  ['payout transfers verify provider reference, amount and currency', worker.includes('transferMatchesOperation(data?.data, operation, reference)') && worker.includes('providerReference === String(expectedReference).trim()')],
  ['refunds retain NGN and USD support', refund.includes("['NGN','USD']") && refund.includes('currency: normalizedCurrency')],
  ['FYNX fee ledger is retained', worker.includes("'fynx_marketplace_fee','FEE'")],
];
let failed = 0;
for (const [name, ok] of checks) {
  console.log((ok ? 'PASS: ' : 'FAIL: ') + name);
  if (!ok) failed += 1;
}
if (failed) process.exit(1);
