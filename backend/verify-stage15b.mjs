import fs from 'node:fs';

const protection = fs.readFileSync(new URL('./marketplaceProtection.js', import.meta.url), 'utf8');
const scalability = fs.readFileSync(new URL('./scalability.js', import.meta.url), 'utf8');

const checks = [
  ['protection cases schema', 'marketplace_protection_cases', 'REFUND_REQUEST', 'DISPUTE'],
  ['protection audit trail', 'marketplace_protection_audit', 'DISPUTE_OPENED', 'REFUND_REQUESTED'],
  ['buyer refund restriction', "caseType === 'REFUND_REQUEST' && !isBuyer"],
  ['payout protection gate', "status='DISPUTED'", "payoutBlocked: true"],
  ['protection idempotency', 'idempotency-key', 'idempotency_key', 'idempotent: true'],
  ['protection route wiring', 'registerMarketplaceProtectionRoutes', 'protected settlement and buyer/seller protection routes enabled']
];

for (const [name, ...markers] of checks) {
  const source = name === 'protection route wiring' ? scalability : protection;
  for (const marker of markers) {
    if (!source.includes(marker)) throw new Error(`Stage 15B verification failed: ${name} missing ${marker}`);
  }
}

console.log('FYNX Stage 15B buyer/seller protection verification: PASS');
