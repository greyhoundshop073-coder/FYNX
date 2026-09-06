import fs from 'node:fs';

const jobs = fs.readFileSync('backgroundJobs.js', 'utf8');
const orders = fs.readFileSync('marketplaceTransactions.js', 'utf8');
const scale = fs.readFileSync('scalability.js', 'utf8');

const checks = [
  ['background-job claim uses one UPDATE/CTE transition', /WITH picked AS[\s\S]*UPDATE fynx_background_jobs AS j[\s\S]*FROM picked[\s\S]*WHERE j\.id = picked\.id/, jobs],
  ['background-job claim preserves SKIP LOCKED', /FOR UPDATE SKIP LOCKED/, jobs],
  ['marketplace seller status index exists', /marketplace_orders_seller_status_idx/, orders],
  ['marketplace buyer order list is independently limited', /SELECT o\.\* FROM marketplace_orders o WHERE o\.buyer_id = \$1[\s\S]*LIMIT 100/, orders],
  ['marketplace seller order list is independently limited', /SELECT o\.\* FROM marketplace_orders o WHERE o\.seller_id = \$1[\s\S]*LIMIT 100/, orders],
  ['HTTP keep-alive timeout is bounded', /server\.keepAliveTimeout = 65_000/, scale],
  ['HTTP headers timeout is bounded', /server\.headersTimeout = 70_000/, scale],
  ['HTTP request timeout is bounded', /server\.requestTimeout = 30_000/, scale],
  ['HTTP per-socket request cap is bounded', /server\.maxRequestsPerSocket = 1_000/, scale],
  ['HTTP connection cap is bounded', /server\.maxConnections = 500/, scale],
  ['production request metrics are recorded', /server\.on\("request"[\s\S]*fynx-metrics.*avgLatencyMs/, scale]
];

for (const [name, pattern, source] of checks) {
  if (!pattern.test(source)) {
    throw new Error(`Stage 15E performance check failed: ${name}`);
  }
  console.log(`PASS: ${name}`);
}

console.log('Stage 15E performance verification passed');
