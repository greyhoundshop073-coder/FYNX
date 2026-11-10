import { getFynxDatabasePool } from "./databasePool.js";
import pg from 'pg';
import jwt from 'jsonwebtoken';
const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = getFynxDatabasePool();

export function registerAdminRoutes({ app }) {
  if (!pool) return;
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_admin_roles (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, role TEXT NOT NULL CHECK(role IN ('ADMIN')), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE TABLE IF NOT EXISTS fynx_announcements (id BIGSERIAL PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL, priority TEXT NOT NULL DEFAULT 'NORMAL' CHECK(priority IN ('NORMAL','IMPORTANT','URGENT')), published BOOLEAN NOT NULL DEFAULT TRUE, author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE INDEX IF NOT EXISTS fynx_announcements_feed_idx ON fynx_announcements(published,created_at DESC);
      CREATE TABLE IF NOT EXISTS fynx_account_safety (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, message_safety BOOLEAN NOT NULL DEFAULT TRUE, marketplace_safety BOOLEAN NOT NULL DEFAULT TRUE, login_alerts BOOLEAN NOT NULL DEFAULT TRUE, account_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(account_status IN ('ACTIVE','LIMITED','LOCKED')), status_note TEXT NOT NULL DEFAULT '', updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); CREATE TABLE IF NOT EXISTS safety_settings (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, message_safety BOOLEAN NOT NULL DEFAULT TRUE, marketplace_safety BOOLEAN NOT NULL DEFAULT TRUE, login_alerts BOOLEAN NOT NULL DEFAULT TRUE, account_status TEXT NOT NULL DEFAULT 'ACTIVE', status_note TEXT NOT NULL DEFAULT '', updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); CREATE TABLE IF NOT EXISTS safety_appeals (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, report_id BIGINT, subject TEXT NOT NULL, details TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','ACCEPTED','REJECTED')), decision_note TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); CREATE INDEX IF NOT EXISTS safety_appeals_admin_idx ON safety_appeals(status,created_at DESC);
      CREATE TABLE IF NOT EXISTS safety_reports (id BIGSERIAL PRIMARY KEY, reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, target_username TEXT NOT NULL DEFAULT '', target_content_type TEXT NOT NULL DEFAULT 'ACCOUNT', target_content_id TEXT NOT NULL DEFAULT '', reason TEXT NOT NULL, details TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','RESOLVED','DISMISSED')), moderator_id BIGINT REFERENCES users(id) ON DELETE SET NULL, decision_note TEXT NOT NULL DEFAULT '', reviewed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      ALTER TABLE safety_reports ADD COLUMN IF NOT EXISTS target_content_type TEXT NOT NULL DEFAULT 'ACCOUNT';
      ALTER TABLE safety_reports ADD COLUMN IF NOT EXISTS target_content_id TEXT NOT NULL DEFAULT '';
      ALTER TABLE safety_reports ADD COLUMN IF NOT EXISTS moderator_id BIGINT REFERENCES users(id) ON DELETE SET NULL;
      ALTER TABLE safety_reports ADD COLUMN IF NOT EXISTS decision_note TEXT NOT NULL DEFAULT '';
      ALTER TABLE safety_reports ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;
      CREATE INDEX IF NOT EXISTS safety_reports_admin_idx ON safety_reports(status,created_at ASC);
      CREATE TABLE IF NOT EXISTS fynx_revenue_transactions (
        id UUID PRIMARY KEY,
        source TEXT NOT NULL CHECK(source IN ('MARKETPLACE_FEE','SELLER_PROMOTION','AI_PLAN')),
        source_key TEXT NOT NULL UNIQUE,
        order_id UUID REFERENCES marketplace_orders(id) ON DELETE SET NULL,
        listing_id BIGINT REFERENCES marketplace_listings(id) ON DELETE SET NULL,
        user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
        amount NUMERIC(14,2) NOT NULL CHECK(amount > 0),
        currency TEXT NOT NULL,
        status TEXT NOT NULL CHECK(status IN ('PENDING','SETTLED','REVERSED','REFUNDED')),
        metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS fynx_revenue_transactions_created_idx ON fynx_revenue_transactions(created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_revenue_transactions_source_idx ON fynx_revenue_transactions(source,status,created_at DESC);
      CREATE TABLE IF NOT EXISTS fynx_ai_entitlements (
        user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
        plan TEXT NOT NULL DEFAULT 'FREE' CHECK(plan IN ('FREE','PLUS','PRO')),
        active BOOLEAN NOT NULL DEFAULT TRUE,
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      DO $$
      BEGIN
        IF to_regclass('public.marketplace_ledger_entries') IS NOT NULL THEN
          CREATE OR REPLACE FUNCTION fynx_record_marketplace_revenue() RETURNS trigger AS $fn$
          BEGIN
            IF NEW.account='fynx_marketplace_fee' AND NEW.entry_type='FEE' THEN
              INSERT INTO fynx_revenue_transactions(id,source,source_key,order_id,amount,currency,status,metadata)
              VALUES(gen_random_uuid(),'MARKETPLACE_FEE',NEW.idempotency_key,NEW.order_id,NEW.amount,NEW.currency,'SETTLED',jsonb_build_object('ledgerId',NEW.id,'account',NEW.account,'entryType',NEW.entry_type))
              ON CONFLICT(source_key) DO NOTHING;
            END IF;
            RETURN NEW;
          END;
          $fn$ LANGUAGE plpgsql;
          DROP TRIGGER IF EXISTS marketplace_revenue_ledger_sync ON marketplace_ledger_entries;
          CREATE TRIGGER marketplace_revenue_ledger_sync AFTER INSERT ON marketplace_ledger_entries FOR EACH ROW EXECUTE FUNCTION fynx_record_marketplace_revenue();
        END IF;
      END $$;
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
  const countRows = async (table, where = 'TRUE') => {
    const exists = await pool.query('SELECT 1 FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=$1 LIMIT 1',[table]);
    if (!exists.rowCount) return 0;
    const result = await pool.query(`SELECT COUNT(*)::int AS count FROM ${table} WHERE ${where}`);
    return Number(result.rows[0]?.count || 0);
  };

  app.get('/api/announcements',auth,async(req,res)=>{try{await ensureSchema();const result=await pool.query(`SELECT id,title,body,priority,created_at,updated_at FROM fynx_announcements WHERE published=TRUE ORDER BY CASE priority WHEN 'URGENT' THEN 0 WHEN 'IMPORTANT' THEN 1 ELSE 2 END,created_at DESC LIMIT 50`);res.set('Cache-Control','no-store');return res.json({announcements:result.rows.map(a=>({id:String(a.id),title:a.title,body:a.body,priority:a.priority,createdAt:a.created_at,updatedAt:a.updated_at}))});}catch(error){console.error('announcements get',error);return res.status(500).json({error:'announcements unavailable'});}});

  app.get('/api/admin/dashboard',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const counts=await Promise.all([countRows('users'),countRows('safety_reports',"status IN ('OPEN','UNDER_REVIEW')"),countRows('safety_appeals',"status IN ('OPEN','UNDER_REVIEW')"),countRows('fynx_safety_events',"created_at >= NOW()-INTERVAL '24 hours'")]);return res.json({role:req.fynxAdminRole,counts:{users:counts[0],openReports:counts[1],openAppeals:counts[2],safetyEvents24h:counts[3]}});}catch(error){console.error('admin dashboard',error);return res.status(500).json({error:'admin dashboard unavailable'});}});

  app.get('/api/admin/reports',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const result=await pool.query(`SELECT r.id,r.reporter_id,r.target_user_id,r.target_username,r.target_content_type,r.target_content_id,r.reason,r.details,r.status,r.decision_note,r.created_at,r.updated_at,r.reviewed_at,reporter.username AS reporter_username,reporter.display_name AS reporter_display_name,moderator.username AS moderator_username FROM safety_reports r JOIN users reporter ON reporter.id=r.reporter_id LEFT JOIN users moderator ON moderator.id=r.moderator_id WHERE r.status IN ('OPEN','UNDER_REVIEW') ORDER BY CASE r.status WHEN 'OPEN' THEN 0 ELSE 1 END,r.created_at ASC LIMIT 100`);return res.json({reports:result.rows.map(x=>({id:String(x.id),reporterUsername:x.reporter_username||'',reporterDisplayName:x.reporter_display_name||'',targetUsername:x.target_username,contentType:x.target_content_type,contentId:x.target_content_id,reason:x.reason,details:x.details,status:x.status,decisionNote:x.decision_note||'',moderatorUsername:x.moderator_username||'',createdAt:x.created_at,updatedAt:x.updated_at,reviewedAt:x.reviewed_at}))})}catch(error){console.error('admin reports',error);return res.status(500).json({error:'admin reports unavailable'});}});

  app.patch('/api/admin/reports/:reportId',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const reportId=Number(req.params.reportId);const status=typeof req.body?.status==='string'?req.body.status.trim().toUpperCase():'';const note=typeof req.body?.decisionNote==='string'?req.body.decisionNote.trim().slice(0,500):'';if(!Number.isInteger(reportId)||reportId<1||!['UNDER_REVIEW','RESOLVED','DISMISSED'].includes(status))return res.status(400).json({error:'valid report id and review status are required'});const updated=await pool.query(`UPDATE safety_reports SET status=$1,moderator_id=$2,decision_note=$3,reviewed_at=CASE WHEN $1 IN ('RESOLVED','DISMISSED') THEN NOW() ELSE NULL END,updated_at=NOW() WHERE id=$4 RETURNING id,status,decision_note,moderator_id,reviewed_at,updated_at`,[status,req.user.sub,note,reportId]);if(!updated.rowCount)return res.status(404).json({error:'report not found'});const x=updated.rows[0];return res.json({report:{id:String(x.id),status:x.status,decisionNote:x.decision_note,moderatorId:String(x.moderator_id),reviewedAt:x.reviewed_at,updatedAt:x.updated_at}})}catch(error){console.error('admin report review',error);return res.status(500).json({error:'admin report review failed'});}});

  app.get('/api/admin/revenue',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const result=await pool.query(`SELECT source,status,currency,COUNT(*)::int AS transactions,COALESCE(SUM(amount),0)::numeric(14,2) AS amount FROM fynx_revenue_transactions GROUP BY source,status,currency ORDER BY source,status,currency`);const totals=await pool.query(`SELECT currency,COALESCE(SUM(amount) FILTER (WHERE status='SETTLED'),0)::numeric(14,2) AS settled,COALESCE(SUM(amount) FILTER (WHERE status='REVERSED'),0)::numeric(14,2) AS reversed,COALESCE(SUM(amount) FILTER (WHERE status='REFUNDED'),0)::numeric(14,2) AS refunded FROM fynx_revenue_transactions GROUP BY currency ORDER BY currency`);return res.json({role:req.fynxAdminRole,breakdown:result.rows,totals:totals.rows,reconciliation:{source:'fynx_revenue_transactions derived from marketplace fee ledger',duplicateProtection:'unique source_key',protectedFundsExcluded:true}});}catch(error){console.error('admin revenue',error);return res.status(500).json({error:'revenue report unavailable'});}});

  app.get('/api/admin/ai-entitlements',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const result=await pool.query(`SELECT plan,active,COUNT(*)::int AS users FROM fynx_ai_entitlements GROUP BY plan,active ORDER BY plan,active`);return res.json({entitlements:result.rows,chargingEnabled:false,source:'entitlement foundation only'});}catch(error){console.error('ai entitlements',error);return res.status(500).json({error:'AI entitlement report unavailable'});}});

  app.get('/api/admin/admins',auth,async(req,res)=>{try{await ensureSchema();if((await requireAdmin(req,res))===null)return;const result=await pool.query(`SELECT u.id,u.username,u.display_name,r.created_at FROM fynx_admin_roles r JOIN users u ON u.id=r.user_id ORDER BY r.created_at ASC LIMIT 100`);return res.json({admins:result.rows.map(a=>({id:String(a.id),username:a.username,displayName:a.display_name||'',grantedAt:a.created_at}))});}catch(error){console.error('admin list',error);return res.status(500).json({error:'admin list unavailable'});}});

  app.post('/api/admin/announcements',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const title=typeof req.body?.title==='string'?req.body.title.trim().slice(0,120):'';const body=typeof req.body?.body==='string'?req.body.body.trim().slice(0,4000):'';const priority=typeof req.body?.priority==='string'?req.body.priority.trim().toUpperCase():'NORMAL';const published=req.body?.published===undefined?true:Boolean(req.body.published);if(title.length<2||body.length<2)return res.status(400).json({error:'title and body are required'});if(!['NORMAL','IMPORTANT','URGENT'].includes(priority))return res.status(400).json({error:'invalid announcement priority'});const result=await pool.query(`INSERT INTO fynx_announcements(title,body,priority,published,author_id) VALUES($1,$2,$3,$4,$5) RETURNING id,title,body,priority,published,created_at,updated_at`,[title,body,priority,published,req.user.sub]);return res.status(201).json({announcement:{...result.rows[0],id:String(result.rows[0].id)}});}catch(error){console.error('announcement create',error);return res.status(500).json({error:'announcement creation failed'});}});

  app.patch('/api/admin/accounts/:userId/status',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const targetId=Number(req.params.userId);const status=typeof req.body?.status==='string'?req.body.status.trim().toUpperCase():'';const note=typeof req.body?.note==='string'?req.body.note.trim().slice(0,500):'';if(!Number.isInteger(targetId)||targetId<1||!['ACTIVE','LIMITED','LOCKED'].includes(status))return res.status(400).json({error:'valid user id and account status are required'});if(String(targetId)===String(req.user.sub))return res.status(400).json({error:'administrators cannot change their own account status'});const targetRole=await role(targetId);if(targetRole==='OWNER'&&req.fynxAdminRole!=='OWNER')return res.status(403).json({error:'only the owner can manage the owner account'});const result=await pool.query(`INSERT INTO fynx_account_safety(user_id,account_status,status_note) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET account_status=EXCLUDED.account_status,status_note=EXCLUDED.status_note,updated_at=NOW() RETURNING user_id,account_status,status_note,updated_at`,[targetId,status,note]);await pool.query(`INSERT INTO safety_settings(user_id,account_status,status_note) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET account_status=EXCLUDED.account_status,status_note=EXCLUDED.status_note,updated_at=NOW()`,[targetId,status,note]); return res.json({account:{userId:String(result.rows[0].user_id),status:result.rows[0].account_status,note:result.rows[0].status_note,updatedAt:result.rows[0].updated_at}});}catch(error){console.error('admin account status',error);return res.status(500).json({error:'account status update failed'});}});

  app.get('/api/admin/appeals',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const result=await pool.query(`SELECT a.id,a.user_id,a.report_id,a.subject,a.details,a.status,a.decision_note,a.created_at,u.username,u.display_name,COALESCE(fs.account_status,fas.account_status,'ACTIVE') AS account_status FROM safety_appeals a JOIN users u ON u.id=a.user_id LEFT JOIN safety_settings fs ON fs.user_id=a.user_id LEFT JOIN fynx_account_safety fas ON fas.user_id=a.user_id WHERE a.status IN ('OPEN','UNDER_REVIEW') ORDER BY CASE a.status WHEN 'OPEN' THEN 0 ELSE 1 END,a.created_at ASC LIMIT 100`);return res.json({appeals:result.rows.map(a=>({id:String(a.id),userId:String(a.user_id),username:a.username||'',displayName:a.display_name||'',reportId:a.report_id==null?null:String(a.report_id),subject:a.subject,details:a.details,status:a.status,decisionNote:a.decision_note||'',accountStatus:a.account_status||'ACTIVE',createdAt:a.created_at}))});}catch(error){console.error('admin appeals',error);return res.status(500).json({error:'admin appeals unavailable'});}});

  app.patch('/api/admin/appeals/:appealId',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const appealId=Number(req.params.appealId);const status=typeof req.body?.status==='string'?req.body.status.trim().toUpperCase():'';const note=typeof req.body?.decisionNote==='string'?req.body.decisionNote.trim().slice(0,500):'';if(!Number.isInteger(appealId)||appealId<1||!['UNDER_REVIEW','ACCEPTED','REJECTED'].includes(status))return res.status(400).json({error:'valid appeal id and decision are required'});const appeal=(await pool.query('SELECT id,user_id FROM safety_appeals WHERE id=$1 LIMIT 1',[appealId])).rows[0];if(!appeal)return res.status(404).json({error:'appeal not found'});const client=await pool.connect();try{await client.query('BEGIN');const updated=await client.query(`UPDATE safety_appeals SET status=$1,decision_note=$2,updated_at=NOW() WHERE id=$3 RETURNING id,user_id,status,decision_note,updated_at`,[status,note,appealId]);if(status==='ACCEPTED'){await client.query(`INSERT INTO fynx_account_safety(user_id,account_status,status_note) VALUES($1,'ACTIVE',$2) ON CONFLICT(user_id) DO UPDATE SET account_status='ACTIVE',status_note=EXCLUDED.status_note,updated_at=NOW()`,[appeal.user_id,note||'Account restored after approved appeal.']);await client.query(`INSERT INTO safety_settings(user_id,account_status,status_note) VALUES($1,'ACTIVE',$2) ON CONFLICT(user_id) DO UPDATE SET account_status='ACTIVE',status_note=EXCLUDED.status_note,updated_at=NOW()`,[appeal.user_id,note||'Account restored after approved appeal.']);}await client.query('COMMIT');const row=updated.rows[0];return res.json({appeal:{id:String(row.id),userId:String(row.user_id),status:row.status,decisionNote:row.decision_note,updatedAt:row.updated_at,restored:status==='ACCEPTED'}})}catch(error){await client.query('ROLLBACK');throw error}finally{client.release();}}catch(error){console.error('admin appeal decision',error);return res.status(500).json({error:'appeal decision failed'});}});

  app.post('/api/admin/admins',auth,async(req,res)=>{try{await ensureSchema();if((await role(req.user.sub))!=='OWNER')return res.status(403).json({error:'owner access required',code:'OWNER_REQUIRED'});const userId=Number(req.body?.userId);if(!Number.isInteger(userId)||userId<1)return res.status(400).json({error:'valid user id is required'});const exists=(await pool.query('SELECT id FROM users WHERE id=$1',[userId])).rows[0];if(!exists)return res.status(404).json({error:'user not found'});await pool.query(`INSERT INTO fynx_admin_roles(user_id,role) VALUES($1,'ADMIN') ON CONFLICT(user_id) DO NOTHING`,[userId]);return res.status(201).json({ok:true,userId:String(userId),role:'ADMIN'});}catch(error){console.error('admin grant',error);return res.status(500).json({error:'admin grant failed'});}});

  app.delete('/api/admin/admins/:userId',auth,async(req,res)=>{try{await ensureSchema();if((await role(req.user.sub))!=='OWNER')return res.status(403).json({error:'owner access required',code:'OWNER_REQUIRED'});const userId=Number(req.params.userId);if(!Number.isInteger(userId)||userId<1)return res.status(400).json({error:'valid user id is required'});if(String(userId)===String(req.user.sub))return res.status(400).json({error:'owner role cannot be removed'});const result=await pool.query('DELETE FROM fynx_admin_roles WHERE user_id=$1',[userId]);return res.json({ok:true,removed:result.rowCount>0,userId:String(userId)});}catch(error){console.error('admin revoke',error);return res.status(500).json({error:'admin revoke failed'});}});
}
