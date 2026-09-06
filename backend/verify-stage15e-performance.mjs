import fs from 'node:fs';

const jobs = fs.readFileSync('backgroundJobs.js', 'utf8');
const orders = fs.readFileSync('marketplaceTransactions.js', 'utf8');
const scale = fs.readFileSync('scalability.js', 'utf8');

const checks = [
  ['background-job claim uses one UPDATE/CTE transition', /WITH picked AS[\s\S]*UPDATE background_jobs/, jobs],
  ['background-job claim preserves SKIP LOCKED', /FOR UPDATE SKIP LOCKED/, jobs],
  ['marketplace seller status index exists', /marketplace_orders_seller_status_idx/, orders],
  ['marketplace order list uses separate buyer/seller limits', /WHERE o\.buyer_id = \$1[\s\S]*LIMIT 100[\s\S]*UNION ALL[\s\S]*WHERE o\.seller_id = \$1[\s\S]*LIMIT 100/, orders],
  ['HTTP connection controls are configurable', /FYNX_KEEP_ALIVE_TIMEOUT_MS[\s\S]*FYNX_MAX_CONNECTIONS/, scale],
  ['metrics sampling is configurable', /FYNX_METRICS_SAMPLE_RATE/, scale]
];

for (const [name, pattern, source] of checks) {
  if (!pattern.test(source)) {
    throw new Error(`Stage 15E performance check failed: ${name}`);
  }
  console.log(`PASS: ${name}`);
}

console.log('Stage 15E performance verification passed');
