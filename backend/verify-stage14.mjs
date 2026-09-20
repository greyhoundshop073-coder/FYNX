import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(process.cwd(), '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const checks = [
  ['Android network client', 'app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt', ['MAX_IDEMPOTENT_RETRIES','Semaphore','MAX_CONCURRENT_REQUESTS','isNetworkAvailable','MAX_RESPONSE_BYTES','invokeOnCompletion','HTTP_UNAUTHORIZED']],
  ['Android media cache', 'app/src/main/java/com/fynx/app/ui/FynxMediaCache.kt', ['fynx_media_cache_v2','MAX_IMAGE_DIMENSION','MAX_FYNX_MEDIA_CACHE_BYTES','getOrDownload']],
  ['Android media upload optimization', 'app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt', ['prepareImageUpload','MAX_IMAGE_DIMENSION','IMAGE_QUALITY','inSampleSize','RGB_565']],
  ['Backend scalability guard', 'backend/scalability.js', ['keepAliveTimeout','requestTimeout','maxConnections','fynx-metrics','errors5xx']],
  ['Marketplace payment idempotency', 'backend/marketplaceReputation.js', ['payment_authorization_url','payment_access_code','marketplace_orders_payment_reference_idx','idempotent','FOR UPDATE','FYNX_MARKETPLACE_ORDER']]
];

const failures = [];
for (const [name, relative, required] of checks) {
  let content;
  try { content = read(relative); }
  catch (error) { failures.push(`${name}: unable to read ${relative} (${error.message})`); continue; }
  for (const marker of required) if (!content.includes(marker)) failures.push(`${name}: missing ${marker}`);
}

const backendClient = read('app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt');
const realtimeClient = read('app/src/main/java/com/fynx/app/ui/FynxRealtimeClient.kt');
const networkGate = backendClient.match(/private fun hasNetwork\(context: Context\): Boolean \{[\s\S]*?\n    \}/)?.[0] || '';
if (!networkGate.includes('NET_CAPABILITY_INTERNET') || !networkGate.includes('allNetworks')) {
  failures.push('Android production transport is missing the INTERNET-based network gate');
}
if (networkGate.includes('NET_CAPABILITY_VALIDATED')) {
  failures.push('Android HTTP transport still hard-requires NET_CAPABILITY_VALIDATED before attempting production requests');
}
if (!realtimeClient.includes('FynxBackendClient.isNetworkAvailable(context)')) {
  failures.push('Realtime transport is not using the shared production network gate');
}
if (realtimeClient.includes('capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)')) {
  failures.push('Realtime transport still hard-requires NET_CAPABILITY_VALIDATED');
}

const packageJson = JSON.parse(fs.readFileSync(path.join(process.cwd(), 'package.json'), 'utf8'));
const startScript = packageJson.scripts?.start || '';
const usesScalabilityGuard = startScript === 'node --import ./scalability.js server.js'
  || startScript === 'node serverBootstrap.js'
  || startScript === 'node realtimeIsolationBootstrap.js'
  || startScript === 'node --import ./scalability.js realtimeIsolationBootstrap.js'
  || (startScript === 'node --import ./renderScalabilityPreload.js realtimeIsolationBootstrap.js' && read('backend/renderScalabilityPreload.js').includes('import \\"./scalability.js\\"'));
if (!usesScalabilityGuard) failures.push('Backend start script is not using the Stage 14 scalability guard');
if (startScript === 'node serverBootstrap.js') {
  const bootstrap = read('backend/serverBootstrap.js');
  if (!bootstrap.includes('scalability') || !bootstrap.includes('rateLimit') || !bootstrap.includes('.fynx-runtime-server.js')) {
    failures.push('Backend bootstrap is missing the production scalability/startup compatibility guard');
  }
}
if (startScript === 'node realtimeIsolationBootstrap.js' || startScript === 'node --import ./scalability.js realtimeIsolationBootstrap.js' || startScript === 'node --import ./renderScalabilityPreload.js realtimeIsolationBootstrap.js') {
  const isolation = read('backend/realtimeIsolationBootstrap.js');
  if (!isolation.includes('serverBootstrap.js') || !isolation.includes('currentSocketByUserId') || !isolation.includes('__fynxStale')) {
    failures.push('Realtime isolation bootstrap is missing the production server/scalability startup chain');
  }
  if (startScript === 'node --import ./scalability.js realtimeIsolationBootstrap.js' && !startScript.includes('--import ./scalability.js')) {
    failures.push('Realtime production start is missing the explicit scalability preload');
  }
  if (startScript === 'node --import ./renderScalabilityPreload.js realtimeIsolationBootstrap.js') {
    const preload = read('backend/renderScalabilityPreload.js');
    if (!preload.includes('import "./scalability.js"')) failures.push('Render production start is missing the explicit scalability preload module');
  }
}

if (failures.length) {
  console.error('FYNX Stage 14 verification FAILED');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}

console.log('FYNX Stage 14 verification PASSED');
console.log(`Verified ${checks.length} integrated areas plus backend startup and Android network recovery configuration.`);
