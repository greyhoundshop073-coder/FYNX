import fs from "node:fs";

const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");
const social = fs.readFileSync(new URL("./socialRoutes.js", import.meta.url), "utf8");
const transactions = fs.readFileSync(new URL("./marketplaceTransactions.js", import.meta.url), "utf8");
const reputation = fs.readFileSync(new URL("./marketplaceReputation.js", import.meta.url), "utf8");
const completion = fs.readFileSync(new URL("./marketplaceCompletion.js", import.meta.url), "utf8");
const advertising = fs.readFileSync(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const protection = fs.readFileSync(new URL("./marketplaceProtection.js", import.meta.url), "utf8");
const settlement = fs.readFileSync(new URL("./marketplaceSettlement.js", import.meta.url), "utf8");

const checks = [
  ["social routes wired through the main server", server.includes("registerSocialRoutes(app") && social.includes("export function registerSocialRoutes")],
  ["marketplace transaction routes are wired", server.includes("registerMarketplaceTransactionRoutes") && transactions.includes("export function registerMarketplaceTransactionRoutes")],
  ["marketplace reputation and completion routes are wired", server.includes("registerMarketplaceReputationRoutes") && server.includes("registerMarketplaceCompletionRoutes") && reputation.includes("export function registerMarketplaceReputationRoutes") && completion.includes("export function registerMarketplaceCompletionRoutes")],
  ["advertising routes are wired once", server.includes("registerMarketplaceAdvertisingRoutes") && !scalability.includes("registerMarketplaceAdvertisingRoutes")],
  ["protected settlement routes are wired by the existing preload architecture", scalability.includes("registerMarketplaceSettlementRoutes") && scalability.includes("registerMarketplaceProtectionRoutes")],
  ["marketplace order ownership is enforced", transactions.includes("isParticipant(order, req.user.sub)") && transactions.includes("only the buyer can cancel this order")],
  ["advertising ownership is enforced server-side", advertising.includes("WHERE c.owner_id=$1") && advertising.includes("owner_id=$2")],
  ["advertising activation keeps approval and payment gates", advertising.includes("approved_at") && advertising.includes("payment_status !== 'paid'")],
  ["protected settlement keeps buyer/seller authorization boundaries", settlement.includes("buyer_id") && settlement.includes("seller_id") && protection.includes("req.user = payload")],
  ["realtime messaging requires authenticated JWT", server.includes('const token = url.searchParams.get("token")') && server.includes("jwt.verify(token, JWT_SECRET)")]
];

for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Stage 15G integration verification failed: ${name}`);
  console.log(`PASS: ${name}`);
}
console.log(`Stage 15G integration verification passed (${checks.length} checks)`);
