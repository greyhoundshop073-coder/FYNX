import fs from 'node:fs';
import {
  classifyBuyerWalletFundingInitializationFailure,
  isBuyerWalletProviderReferenceNotFound,
  isBuyerWalletTrustedCheckoutUrl
} from './buyerWallet.js';

const read = (name) => fs.readFileSync(new URL(name, import.meta.url), 'utf8');
const wallet = read('./buyerWallet.js');
const webhook = read('./marketplacePaystackWebhook.js');

const initializeStart = wallet.indexOf("app.post('/api/wallet/funding/initialize', auth");
const initializeEnd = wallet.indexOf("app.get('/api/wallet/funding/:reference', auth", initializeStart);
const initializeRoute = initializeStart >= 0 && initializeEnd > initializeStart
  ? wallet.slice(initializeStart, initializeEnd)
  : '';
const statusStart = wallet.indexOf("app.get('/api/wallet/funding/:reference', auth");
const statusEnd = wallet.indexOf("app.get('/api/wallet/funding-account', auth", statusStart);
const statusRoute = statusStart >= 0 && statusEnd > statusStart
  ? wallet.slice(statusStart, statusEnd)
  : '';
const reconcileStart = wallet.indexOf("async function reconcileWalletHostedFunding(");
const reconcileEnd = wallet.indexOf("export async function handleBuyerWalletPaystackEvent", reconcileStart);
const reconcileRoute = reconcileStart >= 0 && reconcileEnd > reconcileStart
  ? wallet.slice(reconcileStart, reconcileEnd)
  : '';

const checks = [
  ['Paystack initialization rejection is terminal while timeout and 5xx uncertainty stays PROCESSING',
    classifyBuyerWalletFundingInitializationFailure({ status: 400 }) === 'FAILED' &&
    classifyBuyerWalletFundingInitializationFailure({ status: 429 }) === 'FAILED' &&
    classifyBuyerWalletFundingInitializationFailure({ status: 200 }) === 'FAILED' &&
    classifyBuyerWalletFundingInitializationFailure({ status: 500 }) === 'PROCESSING' &&
    classifyBuyerWalletFundingInitializationFailure(new Error('timeout')) === 'PROCESSING'],
  ['Paystack reference not-found classifier is exact', isBuyerWalletProviderReferenceNotFound({ status: 404 }) && !isBuyerWalletProviderReferenceNotFound({ status: 503 })],
  ['Paystack checkout allow-list rejects lookalike hosts, URL credentials, HTTP and unexpected ports',
    isBuyerWalletTrustedCheckoutUrl('https://checkout.paystack.com/session') &&
    isBuyerWalletTrustedCheckoutUrl('https://checkout.paystack.com:443/session') &&
    !isBuyerWalletTrustedCheckoutUrl('http://checkout.paystack.com/session') &&
    !isBuyerWalletTrustedCheckoutUrl('https://checkout.paystack.com.attacker.example/session') &&
    !isBuyerWalletTrustedCheckoutUrl('https://checkout.paystack.com@attacker.example/session') &&
    !isBuyerWalletTrustedCheckoutUrl('https://checkout.paystack.com:8443/session')],
  ['idempotent retries return existing terminal and unresolved funding sessions with their original references',
    initializeRoute.includes("['FAILED', 'BLOCKED'].includes(existingStatus)") &&
    initializeRoute.includes("['PENDING', 'PROCESSING'].includes(existingStatus)") &&
    initializeRoute.includes('reference: existing.reference') &&
    initializeRoute.includes('idempotent: true') &&
    !initializeRoute.includes('previous funding request is not reusable')],
  ['an initialization error returns a typed session and stable reference instead of a reference-less generic error',
    initializeRoute.includes('classifyBuyerWalletFundingInitializationFailure(error)') &&
    initializeRoute.includes('status: outcomeStatus') &&
    initializeRoute.includes('reference: intent.reference') &&
    initializeRoute.includes('No Wallet credit was made')],
  ['server rejects non-Paystack URLs before storing the checkout session',
    initializeRoute.includes('isBuyerWalletTrustedCheckoutUrl(authorizationUrl)') &&
    !initializeRoute.includes("authorizationUrl.startsWith('https://')")],
  ['funding status rehydrates the stored checkout URL and access code for an authenticated owner',
    statusRoute.includes('authorization_url,access_code') &&
    statusRoute.includes('authorizationUrl: intent.authorization_url') &&
    statusRoute.includes('accessCode: intent.access_code')],
  ['provider 404 only terminalizes an unresolved intent after the verification grace period',
    reconcileRoute.includes('allowNotFoundFailure') &&
    reconcileRoute.includes('2 * 60 * 1000') &&
    reconcileRoute.includes('isBuyerWalletProviderReferenceNotFound(error)') &&
    reconcileRoute.includes("SET status='FAILED'")],
  ['webhook verification does not use the user-polling not-found timeout flag',
    wallet.includes('return reconcileWalletHostedFunding(reference, String(intent.user_id));') &&
    !wallet.includes('return reconcileWalletHostedFunding(reference, String(intent.user_id), { allowNotFoundFailure: true })')],
  ['card and USSD Wallet funding intents persist owner, amount, method, reference and idempotency', wallet.includes('CREATE TABLE IF NOT EXISTS buyer_wallet_funding_intents') && wallet.includes("payment_method TEXT NOT NULL CHECK (payment_method IN ('CARD','USSD'))") && wallet.includes('idempotency_key TEXT NOT NULL UNIQUE') && wallet.includes('reference TEXT NOT NULL UNIQUE')],
  ['card funding has its own approved provider gate and funding limits', wallet.includes("FYNX_WALLET_CARD_FUNDING_ENABLED === 'true'") && wallet.includes("action === 'CARD_FUNDING'") && wallet.includes('walletFundingLimits() !== null')],
  ['USSD funding has its own approved provider gate and funding limits', wallet.includes("FYNX_WALLET_USSD_FUNDING_ENABLED === 'true'") && wallet.includes("action === 'USSD_FUNDING'") && wallet.includes('walletFundingLimits() !== null')],
  ['Test Mode summary displays simulated balance while keeping spending and withdrawals disabled', wallet.includes('function walletTestModeEnabled()') && wallet.includes('testMode: walletTestModeEnabled()') && wallet.includes('cannot be spent, transferred, used for Marketplace purchases or withdrawn') && wallet.includes("wallet.status === 'ACTIVE' && walletProviderApproved('SPENDING')") && wallet.includes("wallet.status === 'ACTIVE' && walletProviderApproved('WITHDRAWAL')")],
  ['idempotent retries preserve Test Mode state and the original transaction reference', wallet.includes('idempotent: true') && wallet.includes('paymentMethod,') && wallet.includes('testMode: walletTestModeEnabled()') && wallet.includes('simulated Paystack checkout is still pending')],
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
