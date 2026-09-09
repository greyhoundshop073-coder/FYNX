import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

// Production bootstrap compatibility guard. It preserves the existing Stage 14
// scalability preload while making Render startup deterministic. The runtime
// server is prepared completely before it is imported, so API requests cannot
// race route installation during cold start.
const backendDir = path.dirname(fileURLToPath(import.meta.url));
const sourcePath = path.join(backendDir, "server.js");
const socialSourcePath = path.join(backendDir, "socialRoutes.js");
const runtimePath = path.join(backendDir, ".fynx-runtime-server.js");
const runtimeSocialPath = path.join(backendDir, ".fynx-runtime-socialRoutes.js");

let source = await readFile(sourcePath, "utf8");
const rateLimitReplacements = [
  ['app.use("/api/auth", rateLimit("auth", RATE_LIMITS.auth));', 'app.use("/api/auth", rateLimit("auth", 20));'],
  ['app.use("/api/assistant", rateLimit("assistant", RATE_LIMITS.assistant));', 'app.use("/api/assistant", rateLimit("assistant", 20));'],
  ['app.use("/api/media", rateLimit("media", RATE_LIMITS.media));', 'app.use("/api/media", rateLimit("media", 30));'],
  ['app.use("/api/messages", rateLimit("messages", RATE_LIMITS.messages));', 'app.use("/api/messages", rateLimit("messages", 120));']
];
for (const [from, to] of rateLimitReplacements) source = source.replace(from, to);
source = source.replace('from "./socialRoutes.js";', 'from "./.fynx-runtime-socialRoutes.js";\nimport { registerDiscoveryRoutes } from "./discoveryRoutes.js";\nimport { registerBusinessRoutes } from "./businessRoutes.js";\nimport { startMarketplaceInspectionReconciliation } from "./marketplaceInspectionReconciliation.js";\nimport { registerMarketplacePaystackWebhook } from "./marketplacePaystackWebhook.js";');
source = source.replace('registerSocialRoutes(app, { pool, auth, findUserByUsername });', 'registerSocialRoutes(app, { pool, auth, findUserByUsername });\nif (pool) registerDiscoveryRoutes({ app, pool, auth });\nif (pool) registerBusinessRoutes({ app, auth });\nif (pool) startMarketplaceInspectionReconciliation({ pool });\nif (pool) registerMarketplacePaystackWebhook({ app, pool });');
source = source.replace(
  'app.use(express.json({ limit: "18mb", strict: true }));',
  'app.use(express.json({ limit: "18mb", strict: true, verify: (req, _res, buf) => { req.rawBody = Buffer.from(buf); } }));'
);

let social = await readFile(socialSourcePath, "utf8");
const signature = 'export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {';
const compatibleSignature = 'export function registerSocialRoutes(config, legacyConfig) {\n  const { app, pool, auth, findUserByUsername } = legacyConfig ? { app: config, ...legacyConfig } : config;';
if (!social.includes(signature)) throw new Error("FYNX bootstrap could not locate social route signature");
social = social.replace(signature, compatibleSignature);

const socialSchemaNeedle = 'CREATE INDEX IF NOT EXISTS social_posts_created_idx ON social_posts(created_at DESC);';
if (!social.includes('CREATE TABLE IF NOT EXISTS social_post_media')) {
  social = social.replace(
    socialSchemaNeedle,
    `${socialSchemaNeedle}\n      CREATE TABLE IF NOT EXISTS social_post_media (post_id BIGINT NOT NULL REFERENCES social_posts(id) ON DELETE CASCADE, media_id BIGINT NOT NULL REFERENCES message_media(id) ON DELETE CASCADE, media_type TEXT NOT NULL, position INTEGER NOT NULL CHECK (position >= 0), PRIMARY KEY(post_id, media_id));\n      CREATE INDEX IF NOT EXISTS social_post_media_post_idx ON social_post_media(post_id, position);`
  );
}

const socialRouteMarker = "  app.delete('/api/social/posts/:id',auth,async(req,res)=>";
if (!social.includes("/api/social/posts/multi")) {
  const multiMediaRoute = `  app.post('/api/social/posts/multi',auth,async(req,res)=>{try{await ensureSocialSchema();const text=typeof req.body?.text==='string'?req.body.text.trim().slice(0,4000):'';const visibility=req.body?.visibility==='FRIENDS_ONLY'?'FRIENDS_ONLY':'PUBLIC';const mediaIds=Array.isArray(req.body?.mediaIds)?req.body.mediaIds.map(Number).filter(id=>Number.isInteger(id)&&id>0).slice(0,12):[];const mediaTypes=Array.isArray(req.body?.mediaTypes)?req.body.mediaTypes.map(v=>String(v).trim().toLowerCase()).slice(0,12):[];if(!text&&!mediaIds.length)return res.status(400).json({error:'post content is required'});if(!mediaIds.length||mediaIds.length!==mediaTypes.length)return res.status(400).json({error:'media ids and media types must match'});if(mediaTypes.some(type=>!['image','video','audio'].includes(type)))return res.status(400).json({error:'invalid post media type'});const owned=await pool.query('SELECT id FROM message_media WHERE id=ANY($1::bigint[]) AND owner_id=$2',[mediaIds,req.user.sub]);if(owned.rowCount!==mediaIds.length)return res.status(403).json({error:'one or more media files are not owned by this account'});const client=await pool.connect();try{await client.query('BEGIN');const post=await client.query('INSERT INTO social_posts(author_id,text,visibility,media_id,media_type) VALUES($1,$2,$3,$4,$5) RETURNING id',[req.user.sub,text,visibility,mediaIds[0],mediaTypes[0]]);const postId=post.rows[0].id;for(let i=0;i<mediaIds.length;i++)await client.query('INSERT INTO social_post_media(post_id,media_id,media_type,position) VALUES($1,$2,$3,$4)',[postId,mediaIds[i],mediaTypes[i],i]);await client.query('COMMIT');res.status(201).json({postId:String(postId),mediaIds:mediaIds.map(String)})}catch(error){await client.query('ROLLBACK');throw error}finally{client.release()}}catch(e){console.error('social multi-media create post',e);res.status(500).json({error:'multi-media post creation failed'})}});\n`;
  if (!social.includes(socialRouteMarker)) throw new Error("FYNX bootstrap could not locate social post route marker");
  social = social.replace(socialRouteMarker, multiMediaRoute + socialRouteMarker);
}

if (!social.includes("/api/social/posts/:id/media")) {
  const mediaRoute = `  app.get('/api/social/posts/:id/media',auth,async(req,res)=>{try{await ensureSocialSchema();const id=Number(req.params.id);if(!Number.isInteger(id)||!(await visibleSocialPost(id,req.user.sub)))return res.status(404).json({error:'post not found'});const r=await pool.query('SELECT spm.media_id,spm.media_type,spm.position FROM social_post_media spm WHERE spm.post_id=$1 ORDER BY spm.position ASC',[id]);res.json({media:r.rows.map(x=>({mediaId:String(x.media_id),mediaType:x.media_type,position:Number(x.position),mediaUrl:`/api/social/media/${x.media_id}`}))})}catch(e){res.status(500).json({error:'post media lookup failed'})}});\n`;
  const finalMarker = "\n}\n";
  const idx = social.lastIndexOf(finalMarker);
  if (idx < 0) throw new Error("FYNX bootstrap could not locate social route closing marker");
  social = social.slice(0, idx) + "\n" + mediaRoute + social.slice(idx);
}

await writeFile(runtimeSocialPath, social, "utf8");

// Do not let Render advertise a live API while its database initialization is
// still failing in the background. Waiting here makes startup fail fast instead
// of leaving Android clients connected to a server that cannot serve real data.
source = source.replace(
  'initDatabase().catch((error) => { console.error("database initialization failed", error); process.exitCode = 1; });',
  'await initDatabase();'
);
await writeFile(runtimePath, source, "utf8");

await import("./scalability.js");
await new Promise((resolve) => setImmediate(resolve));
await import(`${pathToFileURL(runtimePath).href}?boot=${Date.now()}`);