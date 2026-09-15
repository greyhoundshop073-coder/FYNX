import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const read = (p) => fs.readFileSync(path.join(root, p), 'utf8');
const admin = read('backend/adminRoutes.js');
const settlement = read('backend/marketplaceSettlement.js');
const worker = read('backend/marketplaceSettlementWorker.js');

const checks = [
  ['revenue table is separate from protected ledger', admin.includes('fynx_revenue_transactions') && admin.includes('marketplace_ledger_entries')],
  ['marketplace fee revenue is ledger-derived', admin.includes("NEW.account='fynx_marketplace_fee'") && admin.includes("NEW.entry_type='FEE'")],
  ['revenue source is idempotent', admin.includes('source_key TEXT NOT NULL UNIQUE') && admin.includes('ON CONFLICT(source_key) DO NOTHING')],
  ['revenue records preserve order linkage', admin.includes('order_id UUID REFERENCES marketplace_orders')],
  ['seller promotion foundation exists', admin.includes('fynx_seller_promotions') && admin.includes('/api/marketplace/promotions')],
  ['seller promotion ownership is enforced', admin.includes('seller_id=$2 AND active=TRUE')],
  ['promotion billing cannot fabricate a charge', admin.includes("budget>0)return res.status(409)") && admin.includes('PROMOTION_BILLING_NOT_ENABLED')],
  ['admin revenue reporting is protected', admin.includes("app.get('/api/admin/revenue',auth") && admin.includes('requireAdmin(req,res)')],
  ['revenue report separates settled/reversed/refunded', admin.includes("status='SETTLED'") && admin.includes("status='REVERSED'") && admin.includes("status='REFUNDED'")],
  ['protected funds remain in marketplace escrow', settlement.includes('marketplace_escrows') && settlement.includes('marketplace_ledger_entries')],
  ['seller payout remains based on seller net', worker.includes('sellerNetAmount = Number(order.seller_net_amount')],
  ['marketplace fee is recorded once during settlement', worker.includes("'fynx_marketplace_fee','FEE'") && worker.includes('ON CONFLICT (idempotency_key) DO NOTHING')],
  ['AI monetization is entitlement-only for now', admin.includes('fynx_ai_entitlements') && admin.includes('chargingEnabled:false')],
  ['no payment secret is stored in revenue records', !admin.includes('PAYSTACK_SECRET_KEY') && !admin.includes('SECRET_KEY')],
];

for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Revenue infrastructure verification failed: ${name}`);
  console.log(`GREEN: ${name}`);
}
console.log(`Revenue infrastructure verification passed: ${checks.length}/${checks.length}`);
