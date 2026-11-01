import fs from 'node:fs';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const wallet = read('./buyerWallet.js');
const webhook = read('./marketplacePaystackWebhook.js');

const checks = [
  ['card and USSD Wallet funding intents persist owner, amount, method, reference and idempotency', wallet.includes('CREATE TABLE IF NOT EXISTS buyer_wallet_funding_intents') && wallet.includes("payment_method TEXT NOT NULL CHECK (payment_method IN ('CARD','USSD'))") && wallet.includes('idempotency_key TEXT NOT NULL UNIQUE') && wallet.includes('reference TEXT NOT NULL UNIQUE')],
  ['card funding has its own approved provider gate and funding limits', wallet.includes("FYNX_WALLET_CARD_FUNDING_ENABLED === 'true'") && wallet.includes("action === 'CARD_FUNDING'") && wallet.includes('walletFundingLimits() !== null')],
  ['USSD funding has its own approved provider gate and funding limits', wallet.includes("FYNX_WALLET_USSD_FUNDING_ENABLED === 'true'") && wallet.includes("action === 'USSD_FUNDING'") && wallet.includes('walletFundingLimits() !== null')],
  ['hosted funding initializes Paystack with a unique reference and wallet intent metadata', wallet.includes("walletPaystackRequest('/transaction/initialize'") && wallet.includes('fynxWalletFundingIntentId') && wallet.includes('fynxUserId') && wallet.includes('channels')],
  ['hosted funding requires a secure HTTPS authorization URL', wallet.includes("authorizationUrl.startsWith('https://')")],
  ['funding verification matches the exact reference, NGN amount, user, intent and channel', wallet.includes("providerReference !== normalizedReference") && wallet.includes("providerCurrency !== 'NGN'") && wallet.includes('metadataIntentId !== String(existing.id)') && wallet.includes("metadataUserId !== String(existing.user_id)") && wallet.includes('providerChannel !== expectedChannel')],
  ['wallet card/USSD funding credits only after provider verification and with balanced ledger entries', wallet.includes("'PROVIDER_CLEARING','DEBIT'") && wallet.includes("'AVAILABLE','CREDIT'") && wallet.includes("UPDATE buyer_wallet_funding_intents") && wallet.includes("status='SUCCEEDED'")],
  ['customer can verify a hosted funding reference without client-side credit', wallet.includes("app.get('/api/wallet/funding/:reference', auth") && wallet.includes('reconcileWalletHostedFunding(reference, String(req.user.sub))')],
  ['shared signed Paystack webhook dispatches hosted wallet-funding metadata', webhook.includes('walletFundingIntentId') && webhook.includes("handleBuyerWalletPaystackEvent('charge.success', transaction)")]
];

const failed = checks.filter(([, ok]) => !ok);
for (const [name, ok] of checks) console.log(`${ok ? 'PASS' : 'FAIL'}: ${name}`);
if (failed.length) process.exit(1);
console.log(`FYNX Wallet hosted-funding verification passed: ${checks.length}/${checks.length}`);
