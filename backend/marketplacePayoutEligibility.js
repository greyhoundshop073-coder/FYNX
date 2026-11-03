export function marketplacePayoutsNotice(env = process.env) {
  if (env.FYNX_PAYSTACK_REGISTERED_BUSINESS_APPROVED !== 'true') {
    return 'Paystack Registered Business verification is required before FYNX seller bank transfers can be enabled.';
  }
  if (env.FYNX_MARKETPLACE_PAYOUTS_APPROVED !== 'true') {
    return 'FYNX seller payout approval is still pending.';
  }
  if (typeof env.PAYSTACK_SECRET_KEY !== 'string' || !env.PAYSTACK_SECRET_KEY.trim()) {
    return 'Paystack payout credentials are not configured.';
  }
  return null;
}

export function marketplacePayoutsEnabled(env = process.env) {
  return marketplacePayoutsNotice(env) === null;
}
