import pg from 'pg';
import jwt from 'jsonwebtoken';
import { inspectTrustSafetyText } from './trustSafety.js';
const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 4, min: 0, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 10_000, query_timeout: 12_000, keepAlive: true }) : null;

export function registerGroupRoutes({ app }) {
  if (!pool) return;
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_groups (id TEXT PRIMARY KEY, owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', visibility TEXT NOT NULL DEFAULT 'PRIVATE', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE TABLE IF NOT EXISTS fynx_group_members (group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, role TEXT NOT NULL DEFAULT 'MEMBER', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(group_id,user_id));
      CREATE TABLE IF NOT EXISTS fynx_group_messages (id TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, text TEXT NOT NULL DEFAULT '', attachment_media_id BIGINT, attachment_type TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE TABLE IF NOT EXISTS fynx_account_safety (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, message_safety BOOLEAN NOT NULL DEFAULT TRUE, marketplace_safety BOOLEAN NOT NULL DEFAULT TRUE, login_alerts BOOLEAN NOT NULL DEFAULT TRUE, account_status TEXT NOT NULL DEFAULT 'ACTIVE', status_note TEXT NOT NULL DEFAULT '', CHECK(account_status IN ('ACTIVE','LIMITED','LOCKED')));
      CREATE INDEX IF NOT EXISTS fynx_group_members_user_idx ON fynx_group_members(user_id,group_id);
      CREATE INDEX IF NOT EXISTS fynx_group_messages_group_idx ON fynx_group_messages(group_id,created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_group_messages_media_idx ON fynx_group_messages(attachment_media_id);
    `).catch(error => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };
  const auth = (req,res,next) => { const header=req.get('authorization')||''; const token=header.startsWith('Bearer ')?header.slice(7).trim():''; if(!token||!JWT_SECRET)return res.status(401).json({error:'authentication required'}); try{req.user=jwt.verify(token,JWT_SECRET);return next();}catch{return res.status(401).json({error:'invalid or expired token'});} };
  const validId = value => typeof value === 'string' && /^[A-Za-z0-9_-]{8,80}$/.test(value);
  const findUsers = async members => {
    const entries=[...new Map((Array.isArray(members)?members:[]).map(value=>{const username=typeof value==='object'&&value!==null?String(value.username||''):String(value||'');return [username.trim().toLowerCase(),{username:username.trim(),requestedRole:typeof value==='object'&&value!==null?String(value.role||'MEMBER').toUpperCase():'MEMBER'}];}).filter(([key])=>Boolean(key.replace(/^@+/,'').trim()))).values()];
    const clean=entries.map(entry=>entry.username.toLowerCase().replace(/^@+/,'')).filter(Boolean); if(!clean.length)return [];
    const rows=(await pool.query(`SELECT id,username FROM users WHERE lower(username)=ANY($1::text[])`,[clean])).rows;
    return rows.map(user=>({...user,requestedRole:entries.find(entry=>entry.username.toLowerCase().replace(/^@+/,'')===String(user.username).toLowerCase())?.requestedRole||'MEMBER'}));
  };
  const member = async (groupId,userId) => (await pool.query(`SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1`,[groupId,userId])).rows[0] || null;
  const canManageMembers = role => role === 'ADMIN' || role === 'MODERATOR';
  const normalizeRole = role => ['MEMBER','MODERATOR'].includes(String(role||'').toUpperCase()) ? String(role).toUpperCase() : 'MEMBER';
  const messageJson = row => ({id:String(row.id),text:row.text||'',senderUsername:row.sender_username||'',timestamp:new Date(row.created_at).getTime(),attachmentMediaId:row.attachment_media_id==null?null:String(row.attachment_media_id),attachmentType:row.attachment_type||null,attachmentUrl:row.attachment_media_id==null?null:`/api/media/${row.attachment_media_id}`});

  app.post('/api/groups/:groupId/sync',auth,async(req,res)=>{const client=await pool.connect();
    try{
      await ensureSchema(); const groupId=String(req.params?.groupId||''); if(!validId(groupId))return res.status(400).json({error:'invalid group id'});
      const currentUser=(await pool.query(`SELECT id,username FROM users WHERE id=$1 LIMIT 1`,[req.user.sub])).rows[0]; if(!currentUser)return res.status(401).json({error:'account not found'});
      const groupAccess=await member(groupId,req.user.sub); const requestedOwner=String(req.body?.ownerUsername||'').trim().toLowerCase().replace(/^@+/,'');
      if(!groupAccess && requestedOwner!==String(currentUser.username||'').toLowerCase())return res.status(403).json({error:'group membership required'});
      if(groupAccess && !canManageMembers(groupAccess.role))return res.status(403).json({error:'group management permission required'});
      const name=String(req.body?.name||'').trim().slice(0,120); const description=String(req.body?.description||'').trim().slice(0,500); const visibility=String(req.body?.visibility||'PRIVATE').toUpperCase()==='PUBLIC'?'PUBLIC':'PRIVATE'; if(name.length<2)return res.status(400).json({error:'group name is required'});
      const users=await findUsers(req.body?.members); const requested=new Map(users.map(u=>[String(u.username).toLowerCase(),u])); requested.set(String(currentUser.username).toLowerCase(),{...currentUser,requestedRole:groupAccess?.role||'ADMIN'});
      await client.query('BEGIN');
      const existing=(await client.query(`SELECT id,owner_id FROM fynx_groups WHERE id=$1 FOR UPDATE`,[groupId])).rows[0];
      let groupOwner=currentUser;
      if(existing){
        groupOwner=(await client.query(`SELECT id,username FROM users WHERE id=$1 LIMIT 1`,[existing.owner_id])).rows[0] || currentUser;
        if(String(existing.owner_id)!==String(req.user.sub) && !groupAccess){await client.query('ROLLBACK');return res.status(403).json({error:'group management permission required'});}
        if(String(req.body?.ownerUsername||'').trim().replace(/^@+/,'').toLowerCase()!==String(groupOwner.username||'').toLowerCase()){await client.query('ROLLBACK');return res.status(409).json({error:'group owner mismatch'});}
        requested.set(String(groupOwner.username).toLowerCase(),{...groupOwner,requestedRole:'ADMIN'});
      }
      if(existing){
        await client.query(`UPDATE fynx_groups SET name=$1,description=$2,visibility=$3,updated_at=NOW() WHERE id=$4`,[name,description,visibility,groupId]);
        await client.query(`DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id<>$2 AND user_id<>ALL($3::bigint[])`,[groupId,groupOwner.id,users.map(u=>u.id)]);
      }else{
        await client.query(`INSERT INTO fynx_groups(id,owner_id,name,description,visibility) VALUES($1,$2,$3,$4,$5)`,[groupId,currentUser.id,name,description,visibility]);
      }
      for(const user of requested.values()){
        const role=String(user.id)===String(groupOwner.id)?'ADMIN':normalizeRole(user.requestedRole);
        await client.query(`INSERT INTO fynx_group_members(group_id,user_id,role) VALUES($1,$2,$3) ON CONFLICT(group_id,user_id) DO UPDATE SET role=EXCLUDED.role`,[groupId,user.id,role]);
      }
      await client.query('COMMIT'); return res.json({group:{id:groupId,name,description,visibility,ownerUsername:groupOwner.username,members:[...requested.values()].map(u=>({username:u.username,role:String(u.id)===String(groupOwner.id)?'ADMIN':normalizeRole(u.requestedRole)}))}});
    }catch(error){await client.query('ROLLBACK').catch(()=>{});console.error('group sync',error);return res.status(500).json({error:'group synchronization failed'});}finally{client.release();}
  });

  app.get('/api/groups/:groupId/messages',auth,async(req,res)=>{try{await ensureSchema();const groupId=String(req.params?.groupId||'');if(!validId(groupId))return res.status(400).json({error:'invalid group id'});if(!(await member(groupId,req.user.sub)))return res.status(403).json({error:'group membership required'});const limit=Math.min(Math.max(Number(req.query?.limit)||50,1),100);const rows=(await pool.query(`SELECT m.id,m.text,m.attachment_media_id,m.attachment_type,m.created_at,u.username AS sender_username FROM fynx_group_messages m JOIN users u ON u.id=m.sender_id WHERE m.group_id=$1 ORDER BY m.created_at DESC LIMIT $2`,[groupId,limit])).rows.reverse();res.set('Cache-Control','no-store');return res.json({messages:rows.map(messageJson)});}catch(error){console.error('group messages get',error);return res.status(500).json({error:'group messages lookup failed'});}});
  app.post('/api/groups/:groupId/messages',auth,async(req,res)=>{try{await ensureSchema();const groupId=String(req.params?.groupId||'');if(!validId(groupId))return res.status(400).json({error:'invalid group id'});if(!(await member(groupId,req.user.sub)))return res.status(403).json({error:'group membership required'});const id=String(req.body?.id||'');if(!validId(id))return res.status(400).json({error:'invalid message id'});const text=typeof req.body?.text==='string'?req.body.text.trim().slice(0,4000):'';const attachmentMediaId=req.body?.attachmentMediaId==null?null:Number(req.body.attachmentMediaId);const attachmentType=req.body?.attachmentType==null?null:String(req.body.attachmentType).slice(0,20);if(!text&&attachmentMediaId==null)return res.status(400).json({error:'message content is required'});if(attachmentMediaId!==null&&(!Number.isInteger(attachmentMediaId)||attachmentMediaId<1))return res.status(400).json({error:'invalid attachment'});if(attachmentMediaId!==null){const owned=await pool.query(`SELECT id FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1`,[attachmentMediaId,req.user.sub]);if(!owned.rows[0])return res.status(403).json({error:'attachment is not owned by this account'});}const safety=(await pool.query(`SELECT message_safety,account_status FROM fynx_account_safety WHERE user_id=$1 LIMIT 1`,[req.user.sub])).rows[0]||{message_safety:true,account_status:'ACTIVE'};if(String(safety.account_status)==='LOCKED')return res.status(403).json({error:'account is locked',code:'ACCOUNT_LOCKED'});if(String(safety.account_status)==='LIMITED')return res.status(403).json({error:'account is temporarily limited from sending group messages',code:'ACCOUNT_LIMITED'});if(safety.message_safety&&text){const safetyResult=inspectTrustSafetyText(text);if(safetyResult.shouldBlock)return res.status(422).json({error:'message blocked by safety protection',code:'SAFETY_BLOCK',safety:{risk:safetyResult.risk,scamSignals:safetyResult.scamSignals,spamSignals:safetyResult.spamSignals}});}await pool.query(`INSERT INTO fynx_group_messages(id,group_id,sender_id,text,attachment_media_id,attachment_type) VALUES($1,$2,$3,$4,$5,$6) ON CONFLICT(id) DO NOTHING`,[id,groupId,req.user.sub,text,attachmentMediaId,attachmentType]);const row=(await pool.query(`SELECT m.id,m.text,m.attachment_media_id,m.attachment_type,m.created_at,u.username AS sender_username FROM fynx_group_messages m JOIN users u ON u.id=m.sender_id WHERE m.id=$1 AND m.group_id=$2 LIMIT 1`,[id,groupId])).rows[0];res.status(201).json({message:messageJson(row)});}catch(error){console.error('group message send',error);return res.status(500).json({error:'group message send failed'});}});
}
