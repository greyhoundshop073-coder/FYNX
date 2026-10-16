import fs from 'node:fs';

const read = (relativePath) => fs.readFileSync(new URL(relativePath, import.meta.url), 'utf8');
const client = read('../app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt');
const social = read('../app/src/main/java/com/fynx/app/ui/FynxRemoteSocialClient.kt');

const checks = [
  ['bounded GET is a no-retry catalogue read', client.includes('suspend fun getBounded(context: Context, path: String): Result<String> = request(context, "GET", path, null, maxRetries = 0, connectTimeoutMs = 8_000, readTimeoutMs = 12_000)')],
  ['request retry count remains configurable with the existing default', client.includes('maxRetries: Int = MAX_IDEMPOTENT_RETRIES') && client.includes('attempt >= maxRetries')],
  ['bounded timeout overrides are applied to the HTTP connection', client.includes('connectTimeoutOverrideMs ?: if (weakNetwork) WEAK_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS') && client.includes('readTimeoutOverrideMs ?: if (weakNetwork) WEAK_READ_TIMEOUT_MS else READ_TIMEOUT_MS')],
  ['Marketplace listing search uses bounded GET', /suspend fun listings[\s\S]*?FynxBackendClient\.getBounded\(context, "\/api\/marketplace\/listings/.test(social)],
  ['Marketplace discovery pagination uses bounded GET', /suspend fun marketplaceDiscoveryPage[\s\S]*?FynxBackendClient\.getBounded\(context, path\)/.test(social)],
  ['nearby Marketplace discovery uses bounded GET', /suspend fun nearbyMarketplaceListings[\s\S]*?FynxBackendClient\.getBounded\(context, "\/api\/marketplace\/discovery/.test(social)],
  ['seller listing management and order writes remain on existing request APIs', social.includes('suspend fun myListings(context: Context): Result<List<MarketplaceListing>> = FynxBackendClient.get(context, "/api/marketplace/my-listings")') && social.includes('FynxBackendClient.postJson(context, "/api/marketplace/orders"')]
];

const failed = checks.filter(([, ok]) => !ok).map(([name]) => name);
for (const [name, ok] of checks) console.log((ok ? 'PASS: ' : 'FAIL: ') + name);
if (failed.length) process.exit(1);
console.log('Marketplace bounded-read verification passed: ' + checks.length + '/' + checks.length);
