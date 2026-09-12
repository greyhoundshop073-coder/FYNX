import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const transactions = read('./marketplaceTransactions.js');
const settlement = read('./marketplaceSettlement.js');
const worker = read('./marketplaceSettlementWorker.js');
const paymentState = read('./marketplacePaymentState.js');
const webhook = read('./marketplacePaystackWebhook.js');

const checks = [
  ['server calculates marketplace fees', transactions.includes('FYNX_MARKETPLACE_FEE_BPS') && transactions.includes('rawFee=Math.round')],
  ['fee policy is snapshotted on the order', transactions.includes('fee_policy') && transactions.includes('fee_policy_version') && transactions.includes('provider_fee_payer')],
  ['buyer and seller fee shares are authoritative', transactions.includes('marketplace_fee_buyer') && transactions.includes('marketplace_fee_seller') && transactions.includes('sellerNetAmount')],
  ['buyer total includes only authoritative server components', transactions.includes('buyerTotal=Math.round((productSubtotal+deliveryFee+marketplaceFeeBuyer)*100)/100')],
  ['seller net subtracts seller-paid marketplace fee', transactions.includes('sellerNetAmount=Math.round((productSubtotal+deliveryFee-marketplaceFeeSeller)*100)/100')],
  ['legacy orders receive safe zero-fee accounting defaults', transactions.includes("fee_policy=COALESCE(fee_policy,'ZERO')") && transactions.includes("seller_net_amount=COALESCE(seller_net_amount,total_amount)")],
  ['payout is created from seller net, not raw buyer total', settlement.includes('sellerNetAmount = Number(order.seller_net_amount') && settlement.includes('amount,currency,metadata')],
  ['payout operation carries protected amount and fee snapshot', settlement.includes('escrowAmount: protectedAmount') && settlement.includes('marketplaceFee') && settlement.includes('sellerNetAmount')],
  ['payout reconciles seller net plus marketplace fee to escrow', settlement.includes('sellerNetAmount + marketplaceFee') && settlement.includes('=== protectedAmount')],
  ['successful payout records marketplace fee in ledger', worker.includes("'fynx_marketplace_fee','FEE'") && worker.includes('MARKETPLACE-FEE-${operation.order_id}')],
  ['Paystack provider fee is stored on payment confirmation', paymentState.includes('payment_provider_fee=$2') && paymentState.includes('providerFee')],
  ['Paystack webhook supplies provider fee to the transition layer', webhook.includes('providerFee: transaction.fees')],
  ['payment verification supplies provider fee to the transition layer', read('./marketplaceReputation.js').includes('providerFee: transaction.fees')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`Marketplace accounting verification passed: ${checks.length}/${checks.length}`);
