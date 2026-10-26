import fs from "node:fs";
const server=fs.readFileSync(new URL("./server.js",import.meta.url),"utf8");
const databasePool=fs.readFileSync(new URL("./databasePool.js",import.meta.url),"utf8");
const scalability=fs.readFileSync(new URL("./scalability.js",import.meta.url),"utf8");
const checks=[
 ["shared bounded database pool",server.includes("getFynxDatabasePool") && databasePool.includes("DB_POOL_MAX") && databasePool.includes("connectionTimeoutMillis: 5_000")],
 ["query timeouts",databasePool.includes("statement_timeout: 15_000") && databasePool.includes("query_timeout: 20_000")],
 ["readiness endpoint",server.includes('app.get("/ready"')],
 ["graceful shutdown",server.includes('process.once("SIGTERM"')],
 ["http connection controls",scalability.includes("keepAliveTimeout") && scalability.includes("maxConnections")],
 ["production metrics",scalability.includes("[fynx-metrics]")]
];
for(const [name,ok] of checks){if(!ok) throw new Error("Stage 15D batch 1 verification failed: "+name);console.log("PASS: "+name);}
console.log("Stage 15D batch 1 verification passed");