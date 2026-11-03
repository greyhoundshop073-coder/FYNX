import assert from 'node:assert/strict';
import {
  classifyBuyerWalletFundingInitializationFailure,
  isBuyerWalletProviderReferenceNotFound,
  isBuyerWalletTrustedCheckoutUrl
} from './buyerWallet.js';
import {
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

console.log('FYNX Wallet hosted-funding recovery and Marketplace safety tests GREEN (31 assertions)');
