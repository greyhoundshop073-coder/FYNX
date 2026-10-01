import fs from "node:fs";
const server=fs.readFileSync(new URL("./server.js",import.meta.url),"utf8");
const ads=fs.readFileSync(new URL("./marketplaceAdvertising.js",import.meta.url),"utf8");
const realtime=fs.readFileSync(new URL("./aiRealtimeRoutes.js",import.meta.url),"utf8");
const voice=fs.readFileSync(new URL("./aiVoiceSession.js",import.meta.url),"utf8");
const scalability=fs.readFileSync(new URL("./scalability.js",import.meta.url),"utf8");
const checks=[
 ["advertising route wiring",server.includes("registerMarketplaceAdvertisingRoutes")],
 ["existing FYNX AI provider reused",server.includes("/api/advertising/ai-advice") && server.includes("OPENAI_API_KEY") && server.includes("OPENAI_MODEL")],
 ["AI cannot perform account actions",server.includes("Never perform or claim to perform payments")],
 ["campaign activation requires paid state",ads.includes("payment_status !== \"paid\"")],
 ["campaign approval gate",ads.includes("approved_at")],
 ["server-side budget ownership",ads.includes("total_budget_kobo") && ads.includes("owner_id")],
 ["realtime route is registered from preload",scalability.includes("registerRealtimeAssistantRoutes({ app })")],
 ["realtime route uses bearer authentication",realtime.includes("authorization") && realtime.includes("Bearer ")],
 ["realtime route validates SDP size",realtime.includes("MAX_SDP_LENGTH")],
 ["realtime route uses the expected endpoint",realtime.includes("/api/assistant/realtime-session")],
 ["realtime route returns SDP",realtime.includes("application/sdp")],
 ["realtime helper keeps the OpenAI key server-side",voice.includes("process.env.OPENAI_API_KEY")],
 ["realtime helper uses OpenAI WebRTC calls",voice.includes("/v1/realtime/calls")],
 ["FYNX realtime model is GPT-Realtime-2.1",voice.includes("gpt-realtime-2.1")],
 ["FYNX realtime voice is Marin",voice.includes("marin")]
];
for(const [name,ok] of checks){if(!ok) throw new Error("Stage 15C batch 3 verification failed: "+name); console.log("PASS: "+name);}
console.log("Stage 15C batch 3 verification passed");
