import assert from 'node:assert/strict';
import {
  classifyBuyerWalletFundingInitializationFailure,
  isBuyerWalletProviderReferenceNotFound,
  isBuyerWalletTrustedCheckoutUrl
} from './buyerWallet.js';
import {
  buildWalletMarketplaceCheckoutAllocations,
  isValidWalletMarketplacePin,
  walletMarketplaceSpendingApproved
} from './buyerWalletMarketplace.js';

assert.equal(classifyBuyerWalletFundingInitializationFailure({ status: 400 }), 'FAILED');
assert.equal(classifyBuyerWalletFundingInitializationFailure({ status: 401 }), 'FAILED');
assert.equal(classifyBuyerWalletFundingInitializationFailure({ status: 429 }), 'FAILED');
assert.equal(classifyBuyerWalletFundingInitializationFailure({ status: 200 }), 'FAILED');
assert.equal(classifyBuyerWalletFundingInitializationFailure({ status: 500 }), 'PROCESSING');
assert.equal(classifyBuyerWalletFundingInitializationFailure({ status: 503 }), 'PROCESSING');
assert.equal(classifyBuyerWalletFundingInitializationFailure(new Error('provider timeout')), 'PROCESSING');

assert.equal(isBuyerWalletProviderReferenceNotFound({ status: 404 }), true);
assert.equal(isBuyerWalletProviderReferenceNotFound({ status: 503 }), false);
assert.equal(isBuyerWalletProviderReferenceNotFound(new Error('network error')), false);

assert.equal(isBuyerWalletTrustedCheckoutUrl('https://checkout.paystack.com/secure/session'), true);
assert.equal(isBuyerWalletTrustedCheckoutUrl('https://checkout.paystack.com:443/secure/session'), true);
for (const url of [
  'http://checkout.paystack.com/session',
  'https://checkout.paystack.com.attacker.example/session',
  'https://checkout.paystack.com@attacker.example/session',
  'https://attacker.example/session',
  'https://checkout.paystack.com:8443/session'
]) {
  assert.equal(isBuyerWalletTrustedCheckoutUrl(url), false, url);
}

const approvedSpendingEnvironment = {
  FYNX_WALLET_TEST_MODE_ENABLED: 'false',
  FYNX_WALLET_PROVIDER_APPROVED: 'true',
  FYNX_WALLET_CUSTODY_APPROVED: 'true',
  FYNX_WALLET_COMPLIANCE_APPROVED: 'true',
  FYNX_WALLET_SPENDING_ENABLED: 'true',
  PAYSTACK_SECRET_KEY: 'sk_live_unit_test_only'
};
assert.equal(walletMarketplaceSpendingApproved(approvedSpendingEnvironment), true);
assert.equal(walletMarketplaceSpendingApproved({
  ...approvedSpendingEnvironment,
  FYNX_WALLET_TEST_MODE_ENABLED: 'true'
}), false);
assert.equal(walletMarketplaceSpendingApproved({
  ...approvedSpendingEnvironment,
  FYNX_WALLET_CUSTODY_APPROVED: 'false'
}), false);
assert.equal(walletMarketplaceSpendingApproved({
  ...approvedSpendingEnvironment,
  FYNX_WALLET_SPENDING_ENABLED: 'false'
}), false);

assert.equal(isValidWalletMarketplacePin('123456'), true);
assert.equal(isValidWalletMarketplacePin('000000'), true);
for (const invalidPin of ['12345', '1234567', '12345a', '12 456', '', null, 123456]) {
  assert.equal(isValidWalletMarketplacePin(invalidPin), false);
}


const walletGroup = {
  id: '550e8400-e29b-41d4-a716-446655440099', buyer_id: '100', currency: 'NGN',
  status: 'PAYMENT_PENDING', subtotal: '1000.00', delivery_fee: '100.00',
  marketplace_fee: '50.00', buyer_total: '1150.00'
};
const walletGroupChildren = [
  { id: '550e8400-e29b-41d4-a716-446655440001', buyer_id: '100', currency: 'NGN', status: 'PAYMENT_PENDING', payment_reference: null,
    product_subtotal: '500.00', delivery_fee: '100.00', marketplace_fee: '50.00', marketplace_fee_buyer: '25.00',
    marketplace_fee_seller: '25.00', seller_net_amount: '575.00', buyer_total: '625.00', total_amount: '625.00' },
  { id: '550e8400-e29b-41d4-a716-446655440002', buyer_id: '100', currency: 'NGN', status: 'PAYMENT_PENDING', payment_reference: null,
    product_subtotal: '500.00', delivery_fee: '0.00', marketplace_fee: '50.00', marketplace_fee_buyer: '25.00',
    marketplace_fee_seller: '25.00', seller_net_amount: '475.00', buyer_total: '525.00', total_amount: '525.00' }
];
const walletGroupAllocation = buildWalletMarketplaceCheckoutAllocations(walletGroup, walletGroupChildren);
assert.equal(walletGroupAllocation.currency, 'NGN');
assert.equal(walletGroupAllocation.totalCents, 115000);
assert.deepEqual(walletGroupAllocation.children.map((child) => child.amountCents), [62500, 52500]);
assert.throws(() => buildWalletMarketplaceCheckoutAllocations({ ...walletGroup, currency: 'USD' }, walletGroupChildren));
assert.throws(() => buildWalletMarketplaceCheckoutAllocations({ ...walletGroup, buyer_total: '1151.00' }, walletGroupChildren));
assert.throws(() => buildWalletMarketplaceCheckoutAllocations(
  { ...walletGroup, buyer_total: '1151.00' },
  walletGroupChildren.map((child, index) => index === 0
    ? { ...child, total_amount: '626.00', buyer_total: '626.00', seller_net_amount: '576.00' }
    : child)
));
assert.throws(() => buildWalletMarketplaceCheckoutAllocations(walletGroup, walletGroupChildren.map((child, index) => index === 0 ? { ...child, buyer_id: '101' } : child)));
assert.throws(() => buildWalletMarketplaceCheckoutAllocations(walletGroup, walletGroupChildren.map((child, index) => index === 0 ? { ...child, status: 'PAID' } : child)));
assert.throws(() => buildWalletMarketplaceCheckoutAllocations(walletGroup, walletGroupChildren.map((child, index) => index === 0 ? { ...child, payment_reference: 'FYNX-ORDER-REF' } : child)));
assert.throws(() => buildWalletMarketplaceCheckoutAllocations(walletGroup, [{ ...walletGroupChildren[0], marketplace_fee_seller: '15.00' }, walletGroupChildren[1]]));

console.log('FYNX Wallet hosted-funding, Marketplace order and group-payment regression tests GREEN');
