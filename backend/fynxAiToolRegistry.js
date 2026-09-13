import pg from "pg";
import jwt from "jsonwebtoken";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false, max: 4, min: 0, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 10_000, query_timeout: 12_000, keepAlive: true }) : null;

const TOOL_DEFINITIONS = [
  { type: "function", name: "get_my_profile", description: "Read the authenticated FYNX user's basic profile. Use this when the user asks about their own FYNX account or profile.", strict: true, parameters: { type: "object", properties: {}, additionalProperties: false } },
  { type: "function", name: "get_conversation", description: "Read the authenticated user's recent one-to-one FYNX messages with a named username. Use only when the user asks about that conversation or its messages.", strict: true, parameters: { type: "object", properties: { username: { type: "string", description: "The other FYNX user's username." } }, required: ["username"], additionalProperties: false } }
];

function normalizeUsername(value) { return String(value || "").trim().replace(/^@+/, "").toLowerCase(); }
function authenticate(req) {
  if (!JWT_SECRET) return null;
  const header = typeof req.headers.authorization === "string" ? req.headers.authorization : "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) return null;
  try { const payload = jwt.verify(match[1], JWT_SECRET); return payload?.sub ? String(payload.sub) : null; } catch { return null; }
}
export function getFynxAiTools() { return TOOL_DEFINITIONS; }

export async function executeFynxAiTool({ name, argumentsJson, userId, databasePool = pool }) {
  if (!databasePool) throw new Error("database is not configured");
  if (!userId) throw new Error("authenticated user is required");
  let args = {};
  try { args = argumentsJson ? JSON.parse(argumentsJson) : {}; } catch { throw new Error("invalid tool arguments"); }
  if (name === "get_my_profile") {
    const result = await databasePool.query("SELECT id,username,display_name FROM users WHERE id=$1 LIMIT 1", [userId]);
    const row = result.rows[0];
    if (!row) throw new Error("profile not found");
    return { id: String(row.id), username: row.username, displayName: row.display_name || "" };
  }
  if (name === "get_conversation") {
    const username = normalizeUsername(args.username);
    if (!username || username.length > 100) throw new Error("invalid username");
    const other = await databasePool.query("SELECT id,username,display_name FROM users WHERE lower(username)=lower($1) LIMIT 1", [username]);
    if (!other.rows[0]) throw new Error("user not found");
    const otherUser = other.rows[0];
    const blocked = await databasePool.query("SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1) LIMIT 1", [userId, otherUser.id]);
    if (blocked.rowCount) throw new Error("conversation unavailable");
    const messages = await databasePool.query(`SELECT m.id,m.sender_id,m.recipient_id,m.text,EXTRACT(EPOCH FROM m.created_at)*1000 AS timestamp,m.edited,m.deleted FROM messages m WHERE (m.sender_id=$1 AND m.recipient_id=$2) OR (m.sender_id=$2 AND m.recipient_id=$1) ORDER BY m.created_at DESC LIMIT 30`, [userId, otherUser.id]);
    return { username: otherUser.username, displayName: otherUser.display_name || "", messages: messages.rows.reverse().map(row => ({ id: String(row.id), fromMe: String(row.sender_id) === String(userId), text: row.deleted ? "[deleted]" : String(row.text || ""), timestamp: Number(row.timestamp), edited: Boolean(row.edited), deleted: Boolean(row.deleted) })) };
  }
  throw new Error(`unsupported FYNX AI tool: ${name}`);
}

async function runAssistantAgent({ message, userId }) {
  if (!OPENAI_API_KEY) throw new Error("AI provider is not configured");
  const input = [{ role: "user", content: [{ type: "input_text", text: message }] }];
  const instructions = "You are FYNX AI inside the FYNX social, communication, marketplace, planning and safety app. Be concise, helpful and friendly. You may use only the approved FYNX tools supplied to you. Never claim an action happened unless a tool actually completed it. Never expose secrets or private data. Reading private account data is allowed only through an approved tool for the authenticated user. Never perform payments, refunds, purchases, transfers, deletions, settings changes, or messages because those actions are not available as tools yet. If a requested action is unavailable, say so clearly.";
  for (let turn = 0; turn < 4; turn += 1) {
    const response = await fetch("https://api.openai.com/v1/responses", { method: "POST", headers: { Authorization: `Bearer ${OPENAI_API_KEY}`, "Content-Type": "application/json" }, body: JSON.stringify({ model: OPENAI_MODEL, instructions, input, tools: TOOL_DEFINITIONS, store: false }) });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data?.error?.message || "AI provider request failed");
    const calls = Array.isArray(data?.output) ? data.output.filter(item => item?.type === "function_call") : [];
    const text = typeof data?.output_text === "string" ? data.output_text.trim() : "";
    if (!calls.length) return text || "FYNX AI could not produce a response.";
    input.push(...data.output);
    for (const call of calls) {
      let result;
      try { result = await executeFynxAiTool({ name: call.name, argumentsJson: call.arguments || "{}", userId }); }
      catch (error) { result = { error: error?.message || "tool request failed" }; }
      input.push({ type: "function_call_output", call_id: call.call_id, output: JSON.stringify(result) });
    }
  }
  return "FYNX AI reached the tool-processing limit. Please try the request again.";
}

export function registerFynxAiRoutes({ app }) {
  if (!app) return;
  app.post("/api/assistant/agent", async (req, res) => {
    const userId = authenticate(req);
    if (!userId) return res.status(401).json({ error: "authentication required" });
    const message = typeof req.body?.message === "string" ? req.body.message.trim() : "";
    if (!message) return res.status(400).json({ error: "message is required" });
    if (message.length > 4000) return res.status(413).json({ error: "message too long" });
    try { return res.json({ reply: await runAssistantAgent({ message, userId }) }); }
    catch (error) { console.error("FYNX AI agent", error?.message || error); return res.status(502).json({ error: "FYNX AI is temporarily unavailable" }); }
  });

  app.post("/api/assistant/tools", async (req, res) => {
    const userId = authenticate(req);
    if (!userId) return res.status(401).json({ error: "authentication required" });
    const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
    if (!name || !TOOL_DEFINITIONS.some(tool => tool.name === name)) return res.status(400).json({ error: "unknown AI tool" });
    try { const result = await executeFynxAiTool({ name, argumentsJson: JSON.stringify(req.body?.arguments || {}), userId }); return res.json({ result }); }
    catch (error) { console.error("FYNX AI tool", name, error?.message || error); return res.status(403).json({ error: "AI tool request was not permitted" }); }
  });
}
