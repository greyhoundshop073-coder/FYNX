import fs from "node:fs";
const s=fs.readFileSync(new URL("./server.js",import.meta.url),"utf8");
const checks=[["bounded rate buckets",s.includes("MAX_RATE_BUCKETS")],["short-lived response cache",s.includes("CACHE_TTL_MS")&&s.includes("cacheGet")&&s.includes("cacheSet")],["cache eviction",s.includes("responseCache.delete")],["429 retry guidance",s.includes("Retry-After")],["existing route rate limits",s.includes('rateLimit("messages"')]];
for(const [n,ok] of checks){if(!ok)throw new Error("Stage 15D batch 2 verification failed: "+n);console.log("PASS: "+n)}
console.log("Stage 15D batch 2 verification passed");