import fs from "node:fs";

const server = fs.readFileSync(new URL("./server.js", import.meta.url), "utf8");
const scalability = fs.readFileSync(new URL("./scalability.js", import.meta.url), "utf8");
const checks = [
  ["database pool is bounded", /max:\s*Number\(process\.env\.DB_POOL_MAX \|\| 20\)/.test(server)],
  ["database connection timeout", /connectionTimeoutMillis:\s*5_000/.test(server)],
  ["database statement timeout", /statement_timeout:\s*15_000/.test(server)],
  ["database query timeout", /query_timeout:\s*20_000/.test(server)],
  ["conversation index present", /messages_conversation_idx/.test(server)],
  ["recipient index present", /messages_recipient_idx/.test(server)],
  ["delivery index present", /messages_delivery_idx/.test(server)],
  ["HTTP keep-alive tuning retained", /keepAliveTimeout\s*=\s*65_000/.test(scalability)],
  ["HTTP request timeout retained", /requestTimeout\s*=\s*30_000/.test(scalability)],
  ["duplicate marketplace registration removed", !/registerMarketplaceSettlementRoutes|registerMarketplaceProtectionRoutes|registerMarketplaceAdvertisingRoutes/.test(scalability)]
];
for (const [name, ok] of checks) {
  if (!ok) throw new Error(`Stage 15E batch 1 verification failed: ${name}`);
  console.log(`PASS: ${name}`);
}
console.log("Stage 15E batch 1 verification passed");
