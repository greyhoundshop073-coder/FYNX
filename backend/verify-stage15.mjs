import fs from 'node:fs';

const files = {
  settlement: fs.readFileSync(new URL('./marketplaceSettlement.js', import.meta.url), 'utf8'),
  scalability: fs.readFileSync(new URL('./scalability.js', import.meta.url), 'utf8'),
  bootstrap: fs.readFileSync(new URL('./serverBootstrap.js', import.meta.url), 'utf8'),
  transactions: fs.readFileSync(new URL('./marketplaceTransactions.js', import.meta.url), 'utf8'),
  marketplaceSchema: fs.readFileSync(new URL('./marketplaceSchema.js', import.meta.url), 'utf8'),
  recovery: fs.readFileSync(new URL('./marketplaceBatch2FinalHardening.js', import.meta.url), 'utf8')
};

const checks = [
  ['settlement tables', 'marketplace_escrows', 'marketplace_ledger_entries', 'marketplace_financial_operations'],
  ['payout accounts', 'marketplace_payout_accounts', 'recipient_code', 'account_last4'],
  ['escrow protection states', "'HELD','RELEASE_ELIGIBLE','RELEASE_PENDING','RELEASED','REFUND_PENDING','REFUNDED','DISPUTED','CANCELLED'"],
  ['payment-to-escrow trigger', 'fynx_marketplace_sync_escrow', 'marketplace_order_escrow_sync', 'payment_confirmation'],
  ['dispute payout blocking', "order.status === 'DISPUTED'", "escrow.status === 'DISPUTED'"],
  ['completion payout gate', "order.status !== 'COMPLETED'", "escrow.status !== 'RELEASE_ELIGIBLE'"],
  ['payout idempotency', 'PAYOUT-', 'idempotency_key', 'ON CONFLICT'],
  ['bank account verification', '/bank/resolve', '/transferrecipient'],
  ['settlement route wiring', 'registerMarketplaceSettlementRoutes', 'setImmediate']
];

for (const [name, ...markers] of checks) {
  const source = name === 'settlement route wiring' ? files.scalability : files.settlement;
  for (const marker of markers) {
    if (!source.includes(marker)) throw new Error(`Stage 15 verification failed: ${name} missing ${marker}`);
  }
}


const listingsInit = files.bootstrap.indexOf('await ensureMarketplaceSchemaForPool(pool)');
const ordersInit = files.bootstrap.indexOf('await initializeMarketplaceTransactionSchema(pool)');
const settlementInit = files.bootstrap.indexOf('await initializeMarketplaceSettlementSchema()');
if (listingsInit < 0 || ordersInit <= listingsInit || settlementInit <= ordersInit) {
  throw new Error('Stage 15 verification failed: startup must initialize listings, orders, then settlement schemas in order');
}
if (!files.transactions.includes('export async function initializeMarketplaceTransactionSchema(pool)') ||
    !files.transactions.includes('transactionSchemaPromises.get(pool)')) {
  throw new Error('Stage 15 verification failed: transaction schema initializer must be exported and idempotent per pool');
}
if (!files.marketplaceSchema.includes('export function ensureMarketplaceSchemaForPool(pool)')) {
  throw new Error('Stage 15 verification failed: startup must use the canonical Marketplace listings schema');
}
if (!files.scalability.includes('void marketplaceStartupReady.then(async () =>') ||
    files.scalability.indexOf('registerMarketplaceSettlementWorker({ jobs') < files.scalability.indexOf('void marketplaceStartupReady.then(async () =>')) {
  throw new Error('Stage 15 verification failed: Marketplace workers must wait for schema readiness');
}
if (files.bootstrap.indexOf('await initializeMarketplaceSettlementSchema()') < 0 ||
    files.bootstrap.indexOf('__fynxResolveMarketplaceStartupReady') < files.bootstrap.indexOf('await initializeMarketplaceSettlementSchema()')) {
  throw new Error('Stage 15 verification failed: startup readiness must be released only after settlement schema initialization');
}
if (files.recovery.includes('timer.unref(); void runSafely();')) {
  throw new Error('Stage 15 verification failed: Batch 2 recovery must not query financial tables immediately before schema initialization');
}

console.log('FYNX Stage 15A settlement foundation verification: PASS');
