import pg from 'pg';
import jwt from 'jsonwebtoken';
const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 3, min: 0, idleTimeoutMillis: 30000, connectionTimeoutMillis: 5000, statement_timeout: 10000, query_timeout: 12000, keepAlive: true }) : null;

export function registerAdminRoutes({ app }) {
  if (!pool) return;
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_admin_roles (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, role TEXT NOT NULL CHECK(role IN ('ADMIN')), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE TABLE IF NOT EXISTS fynx_announcements (id BIGSERIAL PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, priority TEXT NOT NULL DEFAULT 'NORMAL' CHECK(priority IN ('NORMAL','IMPORTANT','URGENT')), published BOOLEAN NOT NULL DEFAULT TRUE, author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE INDEX IF NOT EXISTS fynx_announcements_feed_idx ON fynx_announcements(published,created_at DESC);
    `).catch(error => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };
  const auth = (req,res,next) => { const header=req.get('authorization')||''; const token=header.startsWith('Bearer ')?header.slice(7).trim():''; if(!token||!JWT_SECRET)return res.status(401).json({error:'authentication required'}); try{req.user=jwt.verify(token,JWT_SECRET);return next();}catch{return res.status(401).json({error:'invalid or expired token'});} };
  const role = async userId => {
    const first=(await pool.query('SELECT id FROM users ORDER BY id ASC LIMIT 1')).rows[0];
    if(first && String(first.id)===String(userId)) return 'OWNER';
    const admin=(await pool.query('SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 LIMIT 1',[userId])).rowCount;
    return admin ? 'ADMIN' : null;
  };
  const requireAdmin = async (req,res) => { const value=await role(req.user.sub); if(!value){res.status(403).json({error:'administrator access required',code:'ADMIN_REQUIRED'});return null;} req.fynxAdminRole=value; return value; };

  app.get('/api/announcements',auth,async(req,res)=>{try{await ensureSchema();const result=await pool.query(`SELECT id,title,body,priority,created_at,updated_at FROM fynx_announcements WHERE published=TRUE ORDER BY CASE priority WHEN 'URGENT' THEN 0 WHEN 'IMPORTANT' THEN 1 ELSE 2 END,created_at DESC LIMIT 50`);res.set('Cache-Control','no-store');return res.json({announcements:result.rows.map(a=>({id:String(a.id),title:a.title,body:a.body,priority:a.priority,createdAt:a.created_at,updatedAt:a.updated_at}))});}catch(error){console.error('announcements get',error);return res.status(500).json({error:'announcements unavailable'});}});

  app.get('/api/admin/dashboard',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const counts=await Promise.all([pool.query('SELECT COUNT(*)::int AS count FROM users'),pool.query("SELECT COUNT(*)::int AS count FROM fynx_user_reports WHERE status IN ('OPEN','IN_REVIEW')"),pool.query("SELECT COUNT(*)::int AS count FROM fynx_appeals WHERE status IN ('OPEN','IN_REVIEW')"),pool.query("SELECT COUNT(*)::int AS count FROM fynx_safety_events WHERE created_at >= NOW()-INTERVAL '24 hours'")]);return res.json({role:req.fynxAdminRole,counts:{users:counts[0].rows[0].count,openReports:counts[1].rows[0].count,openAppeals:counts[2].rows[0].count,safetyEvents24h:counts[3].rows[0].count}});}catch(error){console.error('admin dashboard',error);return res.status(500).json({error:'admin dashboard unavailable'});}});

  app.post('/api/admin/announcements',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const title=typeof req.body?.title==='string'?req.body.title.trim().slice(0,120):'';const body=typeof req.body?.body==='string'?req.body.body.trim().slice(0,4000):'';const priority=typeof req.body?.priority==='string'?req.body.priority.trim().toUpperCase():'NORMAL';const published=req.body?.published===undefined?true:Boolean(req.body.published);if(title.length<2||body.length<2)return res.status(400).json({error:'title and body are required'});if(!['NORMAL','IMPORTANT','URGENT'].includes(priority))return res.status(400).json({error:'invalid announcement priority'});const result=await pool.query(`INSERT INTO fynx_announcements(title,body,priority,published,author_id) VALUES($1,$2,$3,$4,$5) RETURNING id,title,body,priority,published,created_at,updated_at`,[title,body,priority,published,req.user.sub]);return res.status(201).json({announcement:{...result.rows[0],id:String(result.rows[0].id)}});}catch(error){console.error('announcement create',error);return res.status(500).json({error:'announcement creation failed'});}});

  app.patch('/api/admin/accounts/:userId/status',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const targetId=Number(req.params.userId);const status=typeof req.body?.status==='string'?req.body.status.trim().toUpperCase():'';const note=typeof req.body?.note==='string'?req.body.note.trim().slice(0,500):'';if(!Number.isInteger(targetId)||targetId<1||!['ACTIVE','LIMITED','LOCKED'].includes(status))return res.status(400).json({error:'valid user id and account status are required'});if(String(targetId)===String(req.user.sub))return res.status(400).json({error:'administrators cannot change their own account status'});const targetRole=await role(targetId);if(targetRole==='OWNER'&&req.fynxAdminRole!=='OWNER')return res.status(403).json({error:'only the owner can manage the owner account'});const result=await pool.query(`INSERT INTO fynx_account_safety(user_id,account_status,status_note) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET account_status=EXCLUDED.account_status,status_note=EXCLUDED.status_note RETURNING user_id,account_status,status_note,updated_at`,[targetId,status,note]);return res.json({account:{userId:String(result.rows[0].user_id),status:result.rows[0].account_status,note:result.rows[0].status_note,updatedAt:result.rows[0].updated_at}});}catch(error){console.error('admin account status',error);return res.status(500).json({error:'account status update failed'});}});

  app.post('/api/admin/admins',auth,async(req,res)=>{try{await ensureSchema();if((await role(req.user.sub))!=='OWNER')return res.status(403).json({error:'owner access required',code:'OWNER_REQUIRED'});const userId=Number(req.body?.userId);if(!Number.isInteger(userId)||userId<1)return res.status(400).json({error:'valid user id is required'});const exists=(await pool.query('SELECT id FROM users WHERE id=$1',[userId])).rows[0];if(!exists)return res.status(404).json({error:'user not found'});await pool.query(`INSERT INTO fynx_admin_roles(user_id,role) VALUES($1,'ADMIN') ON CONFLICT(user_id) DO NOTHING`,[userId]);return res.status(201).json({ok:true,userId:String(userId),role:'ADMIN'});}catch(error){console.error('admin grant',error);return res.status(500).json({error:'admin grant failed'});}});
}
