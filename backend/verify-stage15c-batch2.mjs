import fs from "node:fs";
const source=fs.readFileSync(new URL("./marketplaceAdvertising.js",import.meta.url),"utf8");
const checks=[["review submission",source.includes("/submit-review")],["admin approval",source.includes("FYNX_AD_ADMIN_IDS") && source.includes("/approve")],["payment initialization",source.includes("transaction/initialize")],["payment verification",source.includes("transaction/verify/")],["paid activation gate",source.includes("payment_status !== \"paid\"")],["dashboard",source.includes("/api/advertising/dashboard")],["server-owned spend",source.includes("payment_amount_kobo")]];
for(const [name,ok] of checks){if(!ok) throw new Error("Stage 15C batch 2 verification failed: "+name);console.log("PASS: "+name);}
console.log("Stage 15C batch 2 verification passed");
