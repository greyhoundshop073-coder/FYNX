import assert from 'node:assert/strict';
import {
  classifyBuyerWalletFundingInitializationFailure,
  isBuyerWalletProviderReferenceNotFound,
  isBuyerWalletTrustedCheckoutUrl
} from './buyerWallet.js';

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

console.log('FYNX Wallet hosted-funding recovery unit tests GREEN (17 assertions)');
