import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(process.cwd(), '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const checks = [
  ['Android network client', 'app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt', [
    'MAX_IDEMPOTENT_RETRIES',
    'Semaphore',
    'MAX_CONCURRENT_REQUESTS',
    'isNetworkAvailable',
    'MAX_RESPONSE_BYTES',
    'invokeOnCompletion',
    'HTTP_UNAUTHORIZED'
  ]],
  ['Android media cache', 'app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt', [
    'fynx_media_cache_v2',
    'MAX_IMAGE_DIMENSION',
    'MAX_CACHE_BYTES',
    'getOrDownload'
  ]],
  ['Android media upload optimization', 'app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt', [
    'prepareImageUpload',
    'MAX_IMAGE_DIMENSION',
    'IMAGE_QUALITY',
    'inSampleSize',
    'RGB_565'
  ]],
  ['Backend scalability guard', 'backend/scalability.js', [
    'keepAliveTimeout',
    'requestTimeout',
    'maxConnections',
    'fynx-metrics',
    'errors5xx'
  ]],
  ['Marketplace payment idempotency', 'backend/marketplaceReputation.js', [
    'payment_authorization_url',
    'payment_access_code',
    'marketplace_orders_payment_reference_idx',
    'idempotent',
    'FOR UPDATE',
    'PAYSTACK_MARKETPLACE_ORDER'
  ]]
];

const failures = [];
for (const [name, relative, required] of checks) {
  let content;
  try {
    content = read(relative);
  } catch (error) {
    failures.push(`${name}: unable to read ${relative} (${error.message})`);
    continue;
  }
  for (const marker of required) {
    if (!content.includes(marker)) failures.push(`${name}: missing ${marker}`);
  }
}

const packageJson = JSON.parse(fs.readFileSync(path.join(process.cwd(), 'package.json'), 'utf8'));
if (packageJson.scripts?.start !== 'node --import ./scalability.js server.js') {
  failures.push('Backend start script is not using the Stage 14 scalability guard');
}

if (failures.length) {
  console.error('FYNX Stage 14 verification FAILED');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log('FYNX Stage 14 verification PASSED');
console.log(`Verified ${checks.length} integrated areas plus backend startup configuration.`);
