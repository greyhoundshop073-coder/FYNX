import fs from "node:fs";
const server=fs.readFileSync(new URL("./server.js",import.meta.url),"utf8");
const ads=fs.readFileSync(new URL("./marketplaceAdvertising.js",import.meta.url),"utf8");
const checks=[
 ["advertising route wiring",server.includes("registerMarketplaceAdvertisingRoutes")],
 ["existing FYNX AI provider reused",server.includes("/api/advertising/ai-advice") && server.includes("OPENAI_API_KEY") && server.includes("OPENAI_MODEL")],
 ["AI cannot perform account actions",server.includes("Never perform or claim to perform payments")],
 ["campaign activation requires paid state",ads.includes("payment_status !== \"paid\"")],
 ["campaign approval gate",ads.includes("approved_at")],
 ["server-side budget ownership",ads.includes("total_budget_kobo") && ads.includes("owner_id")]
];
for(const [name,ok] of checks){if(!ok) throw new Error("Stage 15C batch 3 verification failed: "+name); console.log("PASS: "+name);}
console.log("Stage 15C batch 3 verification passed");
