import fs from 'node:fs';

const files = {
  settlement: fs.readFileSync(new URL('./marketplaceSettlement.js', import.meta.url), 'utf8'),
  scalability: fs.readFileSync(new URL('./scalability.js', import.meta.url), 'utf8')
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

console.log('FYNX Stage 15A settlement foundation verification: PASS');
