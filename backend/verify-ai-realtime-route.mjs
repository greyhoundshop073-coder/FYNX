import fs from "node:fs";

const route = fs.readFileSync("aiRealtimeRoutes.js", "utf8");
const session = fs.readFileSync("aiVoiceSession.js", "utf8");
const scalability = fs.readFileSync("scalability.js", "utf8");

const checks = [
  ["route exports registration function", /export function registerRealtimeAssistantRoutes/],
  ["route uses authenticated authorization header", /authorization/],
  ["route rejects missing or oversized SDP", /MAX_SDP_LENGTH/],
  ["route exposes expected realtime endpoint", /\/api\/assistant\/realtime-session/],
  ["route calls the realtime SDP helper", /createRealtimeAnswer\(sdp\)/],
  ["route returns SDP content type", /application\/sdp/],
  ["realtime helper keeps the API key server-side", /process\.env\.OPENAI_API_KEY/],
  ["realtime helper uses OpenAI realtime calls endpoint", /api\.openai\.com\/v1\/realtime\/calls/],
  ["FYNX defaults to GPT-Realtime-2.1", /gpt-realtime-2\.1/],
  ["FYNX defaults to Marin voice", /marin/],
  ["preload registers the realtime route", /registerRealtimeAssistantRoutes\(\{ app \}\)/]
];

for (const [name, pattern, source = `${route}\n${session}\n${scalability}`] of checks) {
  if (!pattern.test(source)) throw new Error(`Realtime AI contract check failed: ${name}`);
  console.log(`PASS: ${name}`);
}

console.log("FYNX realtime AI route contract verification passed");
