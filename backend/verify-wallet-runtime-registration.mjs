import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const read = (name) => readFile(new URL(name, import.meta.url), 'utf8');
const [bootstrap, wallet, webhook] = await Promise.all([
  read('./serverBootstrap.js'),
  read('./buyerWallet.js'),
  read('./marketplacePaystackWebhook.js')
]);

assert.match(bootstrap, /import \{ registerBuyerWalletRoutes \} from "\.\/buyerWallet\.js"/,
  'runtime bootstrap must import the Buyer Wallet route module');
assert.match(bootstrap, /registerBuyerWalletRoutes\(\{ app \}\)/,
  'runtime bootstrap must mount authenticated Buyer Wallet routes');
assert.match(bootstrap, /req\.rawBody = Buffer\.from\(buf\)/,
  'Paystack webhook must receive exact raw request bytes for HMAC verification');
assert.match(wallet, /const pool = getFynxDatabasePool\(\)/,
  'Wallet module must use the existing bounded shared database pool');
assert.doesNotMatch(wallet, /await pool\.end\(\)/,
  'Wallet module must not close the shared database pool owned by the backend');
assert.match(webhook, /handleBuyerWalletPaystackEvent\(eventName, transaction\)/,
  'Paystack charge.success must be dispatched to Wallet reconciliation');
assert.match(webhook, /invalid webhook signature/,
  'shared Paystack webhook must retain signature verification');
console.log('Buyer Wallet runtime registration, shared-pool and webhook contract passed.');
