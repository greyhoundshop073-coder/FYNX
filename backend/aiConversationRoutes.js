import pg from "pg";
import { randomUUID } from "node:crypto";
import jwt from "jsonwebtoken";
import { runAssistantAgent } from "./fynxAiToolRegistry.js";

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || "";
const JWT_SECRET = process.env.JWT_SECRET || "";
const OPENAI_API_KEY = process.env.OPENAI_API_KEY || "";
const OPENAI_MODEL = process.env.OPENAI_MODEL || "gpt-5.6-luna";
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === "production" ? { rejectUnauthorized: false } : false,
  max: 4, min: 0, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000,
  statement_timeout: 15_000, query_timeout: 20_000, keepAlive: true
}) : null;

function authenticate(req) {
  if (!JWT_SECRET) return null;
  const header = typeof req.headers.authorization === "string" ? req.headers.authorization : "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) return null;
  try { const payload = jwt.verify(match[1], JWT_SECRET); return payload?.sub ? String(payload.sub) : null; } catch { return null; }
}
function requireDb() { if (!pool) throw new Error("database is not configured"); return pool; }
function validId(value) { return typeof value === "string" && /^[0-9]+$/.test(value) && value.length <= 24; }
function ensureMessageText(value) { return typeof value === "string" ? value.trim().slice(0, 4000) : ""; }

async function ensureSchema(db) {
  await db.query(`
    CREATE TABLE IF NOT EXISTS ai_conversations (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      title TEXT NOT NULL DEFAULT 'New conversation',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS ai_conversations_user_updated_idx ON ai_conversations(user_id, updated_at DESC);
    CREATE TABLE IF NOT EXISTS ai_messages (
      id BIGSERIAL PRIMARY KEY,
      conversation_id BIGINT NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
      user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      role TEXT NOT NULL CHECK (role IN ('user','assistant')),
      text TEXT NOT NULL DEFAULT '',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS ai_messages_conversation_idx ON ai_messages(conversation_id, created_at ASC);
    CREATE TABLE IF NOT EXISTS ai_message_media (
      message_id BIGINT NOT NULL REFERENCES ai_messages(id) ON DELETE CASCADE,
      media_id BIGINT NOT NULL REFERENCES message_media(id) ON DELETE CASCADE,
      media_type TEXT NOT NULL,
      PRIMARY KEY (message_id, media_id)
    );
  `);
}

async function ownedConversation(db, id, userId) {
  if (!validId(id)) return null;
  const result = await db.query("SELECT id,title,created_at,updated_at FROM ai_conversations WHERE id=$1 AND user_id=$2 LIMIT 1", [id, userId]);
  return result.rows[0] || null;
}

async function readConversation(db, id, userId) {
  const conversation = await ownedConversation(db, id, userId);
  if (!conversation) return null;
  const result = await db.query(`
    SELECT m.id,m.role,m.text,EXTRACT(EPOCH FROM m.created_at)*1000 AS timestamp,
      COALESCE(json_agg(json_build_object('id',mm.media_id,'type',mm.media_type))
        FILTER (WHERE mm.media_id IS NOT NULL),'[]'::json) AS attachments
    FROM ai_messages m
    LEFT JOIN ai_message_media mm ON mm.message_id=m.id
    WHERE m.conversation_id=$1 AND m.user_id=$2
    GROUP BY m.id
    ORDER BY m.created_at ASC
  `, [id, userId]);
  return {
    id:String(conversation.id), title:conversation.title, createdAt:conversation.created_at,
    updatedAt:conversation.updated_at,
    messages:result.rows.map(row=>({
      id:String(row.id), role:row.role, text:row.text, timestamp:Number(row.timestamp),
      attachments:Array.isArray(row.attachments)?row.attachments.map(item=>({id:String(item.id),type:item.type})): []
    }))
  };
}

async function loadImageInputs(db, mediaIds, userId) {
  const ids=[...new Set((Array.isArray(mediaIds)?mediaIds:[]).map(String).filter(validId))].slice(0,4);
  if (!ids.length) return [];
  const result=await db.query(`
    SELECT id,mime_type,data,byte_size FROM message_media
    WHERE owner_id=$1 AND id=ANY($2::bigint[]) ORDER BY id ASC
  `,[userId,ids]);
  if (result.rows.length!==ids.length) throw new Error("one or more attachments are unavailable");
  return result.rows.map(row=>{
    const mime=String(row.mime_type||"").toLowerCase();
    if (!mime.startsWith("image/")) throw new Error("FYNX AI currently accepts image attachments");
    return {id:String(row.id),type:"image",dataUrl:`data:${mime};base64,${Buffer.from(row.data).toString("base64")}`};
  });
}

export function registerFynxAiConversationRoutes({ app }) {
  if (!app) return;
  app.get("/api/assistant/conversations", async (req,res)=>{
    const userId=authenticate(req); if(!userId) return res.status(401).json({error:"authentication required"});
    try {
      const db=requireDb(); await ensureSchema(db);
      const result=await db.query(`SELECT id,title,created_at,updated_at FROM ai_conversations WHERE user_id=$1 ORDER BY updated_at DESC LIMIT 100`,[userId]);
      return res.json({conversations:result.rows.map(row=>({id:String(row.id),title:row.title,createdAt:row.created_at,updatedAt:row.updated_at}))});
    } catch(error) { console.error("AI conversations list",error); return res.status(500).json({error:"conversation history unavailable"}); }
  });

  app.post("/api/assistant/conversations", async (req,res)=>{
    const userId=authenticate(req); if(!userId) return res.status(401).json({error:"authentication required"});
    try {
      const db=requireDb(); await ensureSchema(db);
      const title=ensureMessageText(req.body?.title).slice(0,120)||"New conversation";
      const result=await db.query("INSERT INTO ai_conversations(user_id,title) VALUES($1,$2) RETURNING id,title,created_at,updated_at",[userId,title]);
      const row=result.rows[0];
      return res.status(201).json({conversation:{id:String(row.id),title:row.title,createdAt:row.created_at,updatedAt:row.updated_at,messages:[]}});
    } catch(error) { console.error("AI conversation create",error); return res.status(500).json({error:"conversation creation failed"}); }
  });

  app.get("/api/assistant/conversations/:id", async (req,res)=>{
    const userId=authenticate(req); if(!userId) return res.status(401).json({error:"authentication required"});
    try {
      const db=requireDb(); await ensureSchema(db);
      const conversation=await readConversation(db,req.params.id,userId);
      if(!conversation) return res.status(404).json({error:"conversation not found"});
      return res.json({conversation});
    } catch(error) { console.error("AI conversation read",error); return res.status(500).json({error:"conversation unavailable"}); }
  });

  app.delete("/api/assistant/conversations/:id", async (req,res)=>{
    const userId=authenticate(req); if(!userId) return res.status(401).json({error:"authentication required"});
    try {
      const db=requireDb(); await ensureSchema(db);
      const result=await db.query("DELETE FROM ai_conversations WHERE id=$1 AND user_id=$2 RETURNING id",[req.params.id,userId]);
      if(!result.rows[0]) return res.status(404).json({error:"conversation not found"});
      return res.json({ok:true});
    } catch(error) { console.error("AI conversation delete",error); return res.status(500).json({error:"conversation deletion failed"}); }
  });

  app.post("/api/assistant/conversations/:id/message", async (req,res)=>{
    const userId=authenticate(req); if(!userId) return res.status(401).json({error:"authentication required"});
    const message=ensureMessageText(req.body?.message);
    const mediaIds=Array.isArray(req.body?.mediaIds)?req.body.mediaIds:[];
    if(!message && !mediaIds.length) return res.status(400).json({error:"message or attachment is required"});
    if(message.length>4000 || mediaIds.length>4) return res.status(413).json({error:"message or attachment limit exceeded"});
    try {
      const db=requireDb(); await ensureSchema(db);
      const conversation=await ownedConversation(db,req.params.id,userId);
      if(!conversation) return res.status(404).json({error:"conversation not found"});
      const images=await loadImageInputs(db,mediaIds,userId);
      const prior=await db.query("SELECT role,text FROM ai_messages WHERE conversation_id=$1 AND user_id=$2 ORDER BY created_at DESC LIMIT 12",[conversation.id,userId]);
      const history=prior.rows.reverse().map(row=>({role:row.role,text:row.text})).filter(row=>row.text);
      const userMessage=await db.query("INSERT INTO ai_messages(conversation_id,user_id,role,text) VALUES($1,$2,'user',$3) RETURNING id,created_at",[conversation.id,userId,message]);
      const userMessageId=String(userMessage.rows[0].id);
      for(const image of images) await db.query("INSERT INTO ai_message_media(message_id,media_id,media_type) VALUES($1,$2,$3)",[userMessageId,image.id,image.type]);
      let reply;
      if (!images.length) {
        reply = await runAssistantAgent({ message, userId, history, context: {}, imageInputs: images });
      }
      const assistant=await db.query("INSERT INTO ai_messages(conversation_id,user_id,role,text) VALUES($1,$2,'assistant',$3) RETURNING id,created_at",[conversation.id,userId,reply]);
      await db.query("UPDATE ai_conversations SET updated_at=NOW(),title=CASE WHEN title='New conversation' AND $2<>'' THEN LEFT($2,120) ELSE title END WHERE id=$1",[conversation.id,message]);
      return res.json({conversationId:String(conversation.id),userMessageId,assistantMessage:{id:String(assistant.rows[0].id),text:reply,timestamp:new Date(assistant.rows[0].created_at).getTime()}});
    } catch(error) {
      console.error("AI conversation message",error);
      return res.status(502).json({error:"FYNX AI is temporarily unavailable"});
    }
  });
}
