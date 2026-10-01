import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const admin = read('backend/adminRoutes.js');
const settlement = read('backend/marketplaceSettlement.js');
const worker = read('backend/marketplaceSettlementWorker.js');
const accounting = read('backend/verify-marketplace-accounting.mjs');
const advertising = read('backend/marketplaceAdvertising.js');

const checks = [
  ['revenue table is separate from protected ledger', admin.includes('fynx_revenue_transactions') && admin.includes('marketplace_ledger_entries')],
  ['marketplace fee revenue is ledger-derived', admin.includes("NEW.account='fynx_marketplace_fee'") && admin.includes("NEW.entry_type='FEE'")],
  ['revenue source is idempotent', admin.includes('source_key TEXT NOT NULL UNIQUE') && admin.includes('ON CONFLICT(source_key) DO NOTHING')],
  ['revenue records preserve order linkage', admin.includes('order_id UUID REFERENCES marketplace_orders')],
  ['listing linkage uses existing marketplace listing type', admin.includes('listing_id BIGINT REFERENCES marketplace_listings(id)')],
  ['existing seller advertising foundation is preserved', advertising.includes('marketplace_ad_campaigns') && advertising.includes('/api/advertising/campaigns')],
  ['advertising payment already has verified payment state', advertising.includes("payment_status='paid'") && advertising.includes('payment_reference')],
  ['admin revenue reporting is protected', admin.includes("app.get('/api/admin/revenue',auth") && admin.includes('requireAdmin(req,res)')],
  ['revenue report separates settled/reversed/refunded', admin.includes("status='SETTLED'") && admin.includes("status='REVERSED'") && admin.includes("status='REFUNDED'")],
  ['protected funds remain in marketplace escrow', settlement.includes('marketplace_escrows') && settlement.includes('marketplace_ledger_entries')],
  ['seller payout remains based on authoritative seller net', accounting.includes('sellerNetAmount') && worker.includes('seller_payout')],
  ['marketplace fee is recorded once during settlement', worker.includes("'fynx_marketplace_fee','FEE'") && worker.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['AI monetization is entitlement-only for now', admin.includes('fynx_ai_entitlements') && admin.includes('chargingEnabled:false')],
  ['revenue schema does not store provider secrets', !admin.includes('PAYSTACK_SECRET_KEY') && !admin.includes('SECRET_KEY')],
];

for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Revenue infrastructure verification failed: ${name}`);
  console.log(`GREEN: ${name}`);
}
console.log(`Revenue infrastructure verification passed: ${checks.length}/${checks.length}`);
