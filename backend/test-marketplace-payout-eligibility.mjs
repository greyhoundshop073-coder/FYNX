import assert from 'node:assert/strict';
import {
  marketplacePayoutsEnabled,
  marketplacePayoutsNotice
} from './marketplacePayoutEligibility.js';

const approved = {
  FYNX_PAYSTACK_REGISTERED_BUSINESS_APPROVED: 'true',
  FYNX_MARKETPLACE_PAYOUTS_APPROVED: 'true',
  PAYSTACK_SECRET_KEY: 'sk_live_unit_test_only'
};
assert.equal(marketplacePayoutsEnabled(approved), true);
assert.equal(marketplacePayoutsNotice(approved), null);

const starterBusiness = { ...approved, FYNX_PAYSTACK_REGISTERED_BUSINESS_APPROVED: 'false' };
assert.equal(marketplacePayoutsEnabled(starterBusiness), false);
assert.match(marketplacePayoutsNotice(starterBusiness), /Registered Business verification/);

const pendingFynxApproval = { ...approved, FYNX_MARKETPLACE_PAYOUTS_APPROVED: 'false' };
assert.equal(marketplacePayoutsEnabled(pendingFynxApproval), false);
assert.match(marketplacePayoutsNotice(pendingFynxApproval), /FYNX seller payout approval/);

const missingSecret = { ...approved, PAYSTACK_SECRET_KEY: '   ' };
assert.equal(marketplacePayoutsEnabled(missingSecret), false);
assert.match(marketplacePayoutsNotice(missingSecret), /credentials are not configured/);

console.log('Marketplace payout eligibility tests GREEN (8 assertions)');
