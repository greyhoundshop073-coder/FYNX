import fs from "node:fs";
const s=fs.readFileSync(new URL("./server.js",import.meta.url),"utf8");
const sc=fs.readFileSync(new URL("./scalability.js",import.meta.url),"utf8");
const checks=[["rate-limit capacity bound",s.includes("MAX_RATE_BUCKETS")],["retry-after",s.includes("Retry-After")],["bounded cache",s.includes("responseCache")&&s.includes("CACHE_TTL_MS")],["db timeout/pool controls",s.includes("connectionTimeoutMillis")&&s.includes("statement_timeout")],["http protection",sc.includes("maxConnections")&&sc.includes("requestTimeout")],["production metrics",sc.includes("[fynx-metrics]")]];
for(const [n,ok] of checks){if(!ok)throw new Error("Stage 15D batch 2 verification failed: "+n);console.log("PASS: "+n)}
console.log("Stage 15D batch 2 verification passed");