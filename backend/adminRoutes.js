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
      ALTER TABLE fynx_announcements ADD COLUMN IF NOT EXISTS media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL;
      ALTER TABLE fynx_announcements ADD COLUMN IF NOT EXISTS link_url TEXT NOT NULL DEFAULT '';
      ALTER TABLE fynx_announcements ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMPTZ;
      ALTER TABLE fynx_announcements ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;
      CREATE INDEX IF NOT EXISTS fynx_announcements_feed_idx ON fynx_announcements(published,scheduled_at,created_at DESC);
      CREATE TABLE IF NOT EXISTS fynx_account_safety (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, message_safety BOOLEAN NOT NULL DEFAULT TRUE, marketplace_safety BOOLEAN NOT NULL DEFAULT TRUE, login_alerts BOOLEAN NOT NULL DEFAULT TRUE, account_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(account_status IN ('ACTIVE','LIMITED','LOCKED')), status_note TEXT NOT NULL DEFAULT '', updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); CREATE TABLE IF NOT EXISTS safety_settings (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, message_safety BOOLEAN NOT NULL DEFAULT TRUE, marketplace_safety BOOLEAN NOT NULL DEFAULT TRUE, login_alerts BOOLEAN NOT NULL DEFAULT TRUE, account_status TEXT NOT NULL DEFAULT 'ACTIVE', status_note TEXT NOT NULL DEFAULT '', updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); CREATE TABLE IF NOT EXISTS safety_reports (id BIGSERIAL PRIMARY KEY, reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, target_username TEXT NOT NULL DEFAULT '', reason TEXT NOT NULL, details TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','RESOLVED','DISMISSED')), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); CREATE INDEX IF NOT EXISTS safety_reports_admin_idx ON safety_reports(status,created_at ASC); CREATE TABLE IF NOT EXISTS safety_appeals (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, report_id BIGINT REFERENCES safety_reports(id) ON DELETE SET NULL, subject TEXT NOT NULL, details TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','ACCEPTED','REJECTED')), decision_note TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()); DO $fynx$ BEGIN PERFORM pg_advisory_xact_lock(hashtext('fynx_safety_appeals_report_fk')); IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='safety_appeals_report_id_fkey' AND conrelid=to_regclass('safety_appeals')) THEN UPDATE safety_appeals a SET report_id=NULL WHERE a.report_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM safety_reports r WHERE r.id=a.report_id); ALTER TABLE safety_appeals ADD CONSTRAINT safety_appeals_report_id_fkey FOREIGN KEY(report_id) REFERENCES safety_reports(id) ON DELETE SET NULL; END IF; END $fynx$; CREATE INDEX IF NOT EXISTS safety_appeals_admin_idx ON safety_appeals(status,created_at DESC);
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
      CREATE TABLE IF NOT EXISTS fynx_admin_activity (id BIGSERIAL PRIMARY KEY, actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, actor_role TEXT NOT NULL, action TEXT NOT NULL, target_type TEXT NOT NULL DEFAULT '', target_id TEXT NOT NULL DEFAULT '', summary TEXT NOT NULL DEFAULT '', metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE INDEX IF NOT EXISTS fynx_admin_activity_created_idx ON fynx_admin_activity(created_at DESC);
      CREATE INDEX IF NOT EXISTS fynx_admin_activity_action_idx ON fynx_admin_activity(action,created_at DESC);
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
  // Accept a transaction-scoped executor so state changes and their audit row commit atomically.
  const recordAdminActivity = async (req, action, targetType = '', targetId = '', summary = '', metadata = {}, executor = pool) => {
    await executor.query(`INSERT INTO fynx_admin_activity(actor_user_id,actor_role,action,target_type,target_id,summary,metadata) VALUES($1,$2,$3,$4,$5,$6,$7::jsonb)`, [req.user.sub, req.fynxAdminRole || 'OWNER', action, targetType, String(targetId || ''), String(summary || '').slice(0,500), JSON.stringify(metadata || {})]);
  };
  const countRows = async (table, where = 'TRUE') => {
    const exists = await pool.query('SELECT 1 FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=$1 LIMIT 1',[table]);
    if (!exists.rowCount) return 0;
    const result = await pool.query(`SELECT COUNT(*)::int AS count FROM ${table} WHERE ${where}`);
    return Number(result.rows[0]?.count || 0);
  };

  app.get('/api/announcements',auth,async(req,res)=>{try{
    await ensureSchema();
    const result=await pool.query(`SELECT id,title,body,priority,media_id,link_url,CASE WHEN scheduled_at>NOW() THEN scheduled_at ELSE NULL END AS scheduled_at,published,published_at,CASE WHEN scheduled_at IS NOT NULL AND scheduled_at<=NOW() THEN scheduled_at ELSE published_at END AS effective_published_at,created_at,updated_at FROM fynx_announcements WHERE published=TRUE AND (scheduled_at IS NULL OR scheduled_at<=NOW()) ORDER BY CASE priority WHEN 'URGENT' THEN 0 WHEN 'IMPORTANT' THEN 1 ELSE 2 END,COALESCE(published_at,scheduled_at,created_at) DESC LIMIT 50`);
    res.set('Cache-Control','no-store');
    return res.json({announcements:result.rows.map(a=>({id:String(a.id),title:a.title,body:a.body,priority:a.priority,mediaId:a.media_id==null?null:String(a.media_id),linkUrl:a.link_url||'',scheduledAt:a.scheduled_at,published:Boolean(a.published),publishedAt:a.effective_published_at||a.created_at,createdAt:a.created_at,updatedAt:a.updated_at}))});
  }catch(error){console.error('announcements get',error);return res.status(500).json({error:'announcements unavailable'});}});

  app.get('/api/admin/dashboard',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const counts=await Promise.all([countRows('users'),countRows('safety_reports',"status IN ('OPEN','UNDER_REVIEW')"),countRows('safety_appeals',"status IN ('OPEN','UNDER_REVIEW')"),countRows('fynx_admin_activity',"action IN ('REPORT_REVIEWED','APPEAL_DECIDED','ACCOUNT_STATUS_CHANGED') AND created_at >= NOW()-INTERVAL '24 hours'")]);return res.json({role:req.fynxAdminRole,counts:{users:counts[0],openReports:counts[1],openAppeals:counts[2],safetyActions24h:counts[3]}});}catch(error){console.error('admin dashboard',error);return res.status(500).json({error:'admin dashboard unavailable'});}});

  app.get('/api/admin/activity',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const q=typeof req.query?.q==='string'?req.query.q.trim().slice(0,120):'';
    const action=typeof req.query?.action==='string'?req.query.action.trim().slice(0,80):'';
    const limitValue=Number(req.query?.limit);
    const limit=Number.isInteger(limitValue)?Math.min(100,Math.max(1,limitValue)):50;
    const offsetValue=Number(req.query?.offset);
    const offset=Number.isInteger(offsetValue)?Math.min(10000,Math.max(0,offsetValue)):0;
    const result=await pool.query(`SELECT a.id,a.actor_user_id,a.actor_role,a.action,a.target_type,a.target_id,a.summary,a.metadata,a.created_at,u.username,u.display_name FROM fynx_admin_activity a LEFT JOIN users u ON u.id=a.actor_user_id WHERE ($1='' OR a.action ILIKE '%'||$1||'%' OR a.summary ILIKE '%'||$1||'%' OR a.target_id ILIKE '%'||$1||'%' OR COALESCE(u.username,'') ILIKE '%'||$1||'%') AND ($2='' OR a.action=$2) ORDER BY a.created_at DESC,a.id DESC LIMIT $3 OFFSET $4`,[q,action,limit+1,offset]);
    const hasMore=result.rows.length>limit;
    const rows=hasMore?result.rows.slice(0,limit):result.rows;
    return res.json({activities:rows.map(a=>({id:String(a.id),actorUserId:a.actor_user_id==null?null:String(a.actor_user_id),actorRole:a.actor_role,actorUsername:a.username||'',actorDisplayName:a.display_name||'',action:a.action,targetType:a.target_type,targetId:a.target_id,summary:a.summary,metadata:a.metadata||{},createdAt:a.created_at})),limit,offset,hasMore});
  }catch(error){console.error('admin activity',error);return res.status(500).json({error:'admin activity unavailable'});}});

  app.get('/api/admin/revenue',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const result=await pool.query(`SELECT source,status,currency,COUNT(*)::int AS transactions,COALESCE(SUM(amount),0)::numeric(14,2) AS amount FROM fynx_revenue_transactions GROUP BY source,status,currency ORDER BY source,status,currency`);const totals=await pool.query(`SELECT currency,COALESCE(SUM(amount) FILTER (WHERE status='SETTLED'),0)::numeric(14,2) AS settled,COALESCE(SUM(amount) FILTER (WHERE status='REVERSED'),0)::numeric(14,2) AS reversed,COALESCE(SUM(amount) FILTER (WHERE status='REFUNDED'),0)::numeric(14,2) AS refunded FROM fynx_revenue_transactions GROUP BY currency ORDER BY currency`);return res.json({role:req.fynxAdminRole,breakdown:result.rows,totals:totals.rows,reconciliation:{source:'fynx_revenue_transactions derived from marketplace fee ledger',duplicateProtection:'unique source_key',protectedFundsExcluded:true}});}catch(error){console.error('admin revenue',error);return res.status(500).json({error:'revenue report unavailable'});}});

  app.get('/api/admin/ai-entitlements',auth,async(req,res)=>{try{await ensureSchema();if(!(await requireAdmin(req,res)))return;const result=await pool.query(`SELECT plan,active,COUNT(*)::int AS users FROM fynx_ai_entitlements GROUP BY plan,active ORDER BY plan,active`);return res.json({entitlements:result.rows,chargingEnabled:false,source:'entitlement foundation only'});}catch(error){console.error('ai entitlements',error);return res.status(500).json({error:'AI entitlement report unavailable'});}});

  app.get('/api/admin/admins',auth,async(req,res)=>{try{
    await ensureSchema();
    const actorRole=await requireAdmin(req,res);
    if(actorRole===null)return;
    if(actorRole!=='OWNER')return res.status(403).json({error:'owner access required',code:'OWNER_REQUIRED'});
    const result=await pool.query(`SELECT u.id,u.username,u.display_name,r.created_at FROM fynx_admin_roles r JOIN users u ON u.id=r.user_id WHERE r.role='ADMIN' AND u.id<>(SELECT id FROM users ORDER BY id ASC LIMIT 1) ORDER BY r.created_at ASC LIMIT 100`);
    return res.json({admins:result.rows.map(a=>({id:String(a.id),username:a.username,displayName:a.display_name||'',grantedAt:a.created_at}))});
  }catch(error){console.error('admin list',error);return res.status(500).json({error:'admin list unavailable'});}});

  app.get('/api/admin/announcements',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const result=await pool.query(`SELECT id,title,body,priority,media_id,link_url,CASE WHEN scheduled_at>NOW() THEN scheduled_at ELSE NULL END AS scheduled_at,published,CASE WHEN scheduled_at IS NOT NULL AND scheduled_at<=NOW() THEN scheduled_at ELSE published_at END AS published_at,created_at,updated_at FROM fynx_announcements ORDER BY COALESCE(scheduled_at,published_at,created_at) DESC LIMIT 100`);
    return res.json({announcements:result.rows.map(a=>({id:String(a.id),title:a.title,body:a.body,priority:a.priority,mediaId:a.media_id==null?null:String(a.media_id),linkUrl:a.link_url||'',scheduledAt:a.scheduled_at,published:Boolean(a.published),publishedAt:a.published_at,createdAt:a.created_at,updatedAt:a.updated_at}))});
  }catch(error){console.error('admin announcements list',error);return res.status(500).json({error:'admin announcements unavailable'});}});

  app.post('/api/admin/announcements',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const title=typeof req.body?.title==='string'?req.body.title.trim().slice(0,120):'';
    const body=typeof req.body?.body==='string'?req.body.body.trim().slice(0,4000):'';
    const priority=typeof req.body?.priority==='string'?req.body.priority.trim().toUpperCase():'NORMAL';
    const mediaIdRaw=req.body?.mediaId==null?'':String(req.body.mediaId).trim();
    const linkUrl=typeof req.body?.linkUrl==='string'?req.body.linkUrl.trim().slice(0,2048):'';
    const scheduledRaw=typeof req.body?.scheduledAt==='string'?req.body.scheduledAt.trim():'';
    const scheduledAt=scheduledRaw?new Date(scheduledRaw):null;
    const published=req.body?.published===undefined?true:req.body.published===true;
    if(title.length<2||body.length<2)return res.status(400).json({error:'title and body are required'});
    if(!['NORMAL','IMPORTANT','URGENT'].includes(priority))return res.status(400).json({error:'invalid announcement priority'});
    if(scheduledRaw&&(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?(?:Z|[+-]\d{2}:\d{2})$/.test(scheduledRaw)||!Number.isFinite(scheduledAt.getTime())||scheduledAt.getTime()<=Date.now()))return res.status(400).json({error:'scheduled time must be a future ISO 8601 date/time with an explicit timezone'});
    if(mediaIdRaw&&!/^[1-9]\d*$/.test(mediaIdRaw))return res.status(400).json({error:'invalid announcement image'});
    if(mediaIdRaw){
      const owned=await pool.query("SELECT id,mime_type FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1",[mediaIdRaw,req.user.sub]);
      if(!owned.rows[0]||!String(owned.rows[0].mime_type||'').toLowerCase().startsWith('image/'))return res.status(403).json({error:'announcement image must be an image uploaded by this administrator'});
    }
    if(linkUrl){try{const parsed=new URL(linkUrl);if(parsed.protocol!=='https:'||!parsed.hostname||parsed.username||parsed.password)return res.status(400).json({error:'announcement link must be a valid HTTPS URL without embedded credentials'});}catch{return res.status(400).json({error:'announcement link is invalid'});}}
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      const result=await client.query(`INSERT INTO fynx_announcements(title,body,priority,published,author_id,media_id,link_url,scheduled_at,published_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING id,title,body,priority,media_id,link_url,scheduled_at,published,published_at,created_at,updated_at`,[title,body,priority,published,req.user.sub,mediaIdRaw||null,linkUrl,scheduledAt,published&&!scheduledAt?new Date():null]);
      const row=result.rows[0];
      await recordAdminActivity(req,scheduledAt?'ANNOUNCEMENT_SCHEDULED':'ANNOUNCEMENT_PUBLISHED','announcement',row.id,title,{priority,published,scheduledAt:scheduledAt?.toISOString()||null,hasImage:Boolean(mediaIdRaw),hasLink:Boolean(linkUrl)},client);
      await client.query('COMMIT');
      return res.status(201).json({announcement:{id:String(row.id),title:row.title,body:row.body,priority:row.priority,mediaId:row.media_id==null?null:String(row.media_id),linkUrl:row.link_url||'',scheduledAt:row.scheduled_at,published:Boolean(row.published),publishedAt:row.published_at,createdAt:row.created_at,updatedAt:row.updated_at}});
    }catch(error){await client.query('ROLLBACK');throw error;}finally{client.release();}
  }catch(error){console.error('announcement create',error);return res.status(500).json({error:'announcement creation failed'});}});

  app.patch('/api/admin/announcements/:announcementId',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const announcementId=String(req.params.announcementId||'');
    const published=req.body?.published;
    if(!/^[1-9]\d*$/.test(announcementId)||typeof published!=='boolean')return res.status(400).json({error:'valid announcement id and published state are required'});
    const hasSchedule=Object.prototype.hasOwnProperty.call(req.body||{},'scheduledAt');
    const hasContent=['title','body','priority','mediaId','linkUrl'].some(key=>Object.prototype.hasOwnProperty.call(req.body||{},key));
    let scheduledAt;
    if(hasSchedule){
      const raw=typeof req.body.scheduledAt==='string'?req.body.scheduledAt.trim():'';
      scheduledAt=raw?new Date(raw):null;
      if(raw&&(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?(?:Z|[+-]\d{2}:\d{2})$/.test(raw)||!Number.isFinite(scheduledAt.getTime())||scheduledAt.getTime()<=Date.now()))return res.status(400).json({error:'scheduled time must be a future ISO 8601 date/time with an explicit timezone'});
    }
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      const existing=(await client.query('SELECT id,title,body,priority,media_id,link_url,scheduled_at,published FROM fynx_announcements WHERE id=$1 FOR UPDATE',[announcementId])).rows[0];
      if(!existing){await client.query('ROLLBACK');return res.status(404).json({error:'announcement not found'});}
      const title=Object.prototype.hasOwnProperty.call(req.body||{},'title')&&typeof req.body.title==='string'?req.body.title.trim().slice(0,120):existing.title;
      const body=Object.prototype.hasOwnProperty.call(req.body||{},'body')&&typeof req.body.body==='string'?req.body.body.trim().slice(0,4000):existing.body;
      const priority=Object.prototype.hasOwnProperty.call(req.body||{},'priority')&&typeof req.body.priority==='string'?req.body.priority.trim().toUpperCase():existing.priority;
      if(title.length<2||body.length<2||!['NORMAL','IMPORTANT','URGENT'].includes(priority)){await client.query('ROLLBACK');return res.status(400).json({error:'valid announcement title, body and priority are required'});}
      let mediaId=existing.media_id;
      if(Object.prototype.hasOwnProperty.call(req.body||{},'mediaId')){
        const raw=req.body.mediaId==null?'':String(req.body.mediaId).trim();
        if(raw&&!/^[1-9]\d*$/.test(raw)){await client.query('ROLLBACK');return res.status(400).json({error:'invalid announcement image'});}
        if(raw){
          const owned=await client.query("SELECT id,mime_type FROM message_media WHERE id=$1 AND owner_id=$2 LIMIT 1",[raw,req.user.sub]);
          if(!owned.rows[0]||!String(owned.rows[0].mime_type||'').toLowerCase().startsWith('image/')){await client.query('ROLLBACK');return res.status(403).json({error:'announcement image must be an image uploaded by this administrator'});}
          mediaId=raw;
        }else mediaId=null;
      }
      const linkUrl=Object.prototype.hasOwnProperty.call(req.body||{},'linkUrl')&&typeof req.body.linkUrl==='string'?req.body.linkUrl.trim().slice(0,2048):existing.link_url||'';
      if(linkUrl){try{const parsed=new URL(linkUrl);if(parsed.protocol!=='https:'||!parsed.hostname||parsed.username||parsed.password){await client.query('ROLLBACK');return res.status(400).json({error:'announcement link must be a valid HTTPS URL without embedded credentials'});}}catch{await client.query('ROLLBACK');return res.status(400).json({error:'announcement link is invalid'});}}
      const nextSchedule=hasSchedule?scheduledAt:existing.scheduled_at;
      const publishedAt=published&&!nextSchedule?new Date():null;
      const result=await client.query(`UPDATE fynx_announcements SET title=$1,body=$2,priority=$3,media_id=$4,link_url=$5,published=$6,scheduled_at=$7,published_at=$8,updated_at=NOW() WHERE id=$9 RETURNING id,title,body,priority,media_id,link_url,scheduled_at,published,published_at,updated_at`,[title,body,priority,mediaId,linkUrl,published,nextSchedule,publishedAt,announcementId]);
      const row=result.rows[0];
      const action=hasContent?'ANNOUNCEMENT_UPDATED':!published?'ANNOUNCEMENT_UNPUBLISHED':nextSchedule?'ANNOUNCEMENT_SCHEDULED':'ANNOUNCEMENT_PUBLISHED';
      await recordAdminActivity(req,action,'announcement',row.id,row.title,{published,scheduledAt:nextSchedule,previousPublished:existing.published,contentChanged:hasContent,hasImage:Boolean(mediaId),hasLink:Boolean(linkUrl)},client);
      await client.query('COMMIT');
      return res.json({announcement:{id:String(row.id),title:row.title,body:row.body,priority:row.priority,mediaId:row.media_id==null?null:String(row.media_id),linkUrl:row.link_url||'',scheduledAt:row.scheduled_at,published:Boolean(row.published),publishedAt:row.published_at,updatedAt:row.updated_at}});
    }catch(error){await client.query('ROLLBACK');throw error;}finally{client.release();}
  }catch(error){console.error('admin announcement update',error);return res.status(500).json({error:'announcement update failed'});}});

  app.patch('/api/admin/accounts/:userId/status',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const targetId=String(req.params.userId||'');
    const status=typeof req.body?.status==='string'?req.body.status.trim().toUpperCase():'';
    const note=typeof req.body?.note==='string'?req.body.note.trim().slice(0,500):'';
    if(!/^[1-9]\d*$/.test(targetId)||!['ACTIVE','LIMITED','LOCKED'].includes(status))return res.status(400).json({error:'valid user id and account status are required'});
    if(targetId===String(req.user.sub))return res.status(400).json({error:'administrators cannot change their own account status'});
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      const target=(await client.query('SELECT id FROM users WHERE id=$1 FOR UPDATE',[targetId])).rows[0];
      if(!target){await client.query('ROLLBACK');return res.status(404).json({error:'user not found'});}
      const targetRole=await role(targetId);
      if(targetRole==='OWNER'&&req.fynxAdminRole!=='OWNER'){await client.query('ROLLBACK');return res.status(403).json({error:'only the owner can manage the owner account'});}
      const result=await client.query(`INSERT INTO fynx_account_safety(user_id,account_status,status_note) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET account_status=EXCLUDED.account_status,status_note=EXCLUDED.status_note,updated_at=NOW() RETURNING user_id,account_status,status_note,updated_at`,[targetId,status,note]);
      await client.query(`INSERT INTO safety_settings(user_id,account_status,status_note) VALUES($1,$2,$3) ON CONFLICT(user_id) DO UPDATE SET account_status=EXCLUDED.account_status,status_note=EXCLUDED.status_note,updated_at=NOW()`,[targetId,status,note]);
      await recordAdminActivity(req,'ACCOUNT_STATUS_CHANGED','user',targetId,`Account status changed to ${status}`,{status,note},client);
      await client.query('COMMIT');
      return res.json({account:{userId:String(result.rows[0].user_id),status:result.rows[0].account_status,note:result.rows[0].status_note,updatedAt:result.rows[0].updated_at}});
    }catch(error){await client.query('ROLLBACK');throw error;}finally{client.release();}
  }catch(error){console.error('admin account status',error);return res.status(500).json({error:'account status update failed'});}});

  app.get('/api/admin/reports',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const limitValue=Number(req.query?.limit);
    const limit=Number.isInteger(limitValue)?Math.min(50,Math.max(1,limitValue)):50;
    const offsetValue=Number(req.query?.offset);
    const offset=Number.isInteger(offsetValue)?Math.min(10000,Math.max(0,offsetValue)):0;
    const result=await pool.query(`SELECT r.id,r.reporter_id,r.target_user_id,r.target_username,r.reason,r.details,r.status,r.created_at,reporter.username AS reporter_username,reporter.display_name AS reporter_display_name FROM safety_reports r JOIN users reporter ON reporter.id=r.reporter_id WHERE r.status IN ('OPEN','UNDER_REVIEW') ORDER BY CASE r.status WHEN 'OPEN' THEN 0 ELSE 1 END,r.created_at ASC,r.id ASC LIMIT $1 OFFSET $2`,[limit+1,offset]);
    const hasMore=result.rows.length>limit;
    const rows=hasMore?result.rows.slice(0,limit):result.rows;
    return res.json({reports:rows.map(r=>({id:String(r.id),reporterId:String(r.reporter_id),reporterUsername:r.reporter_username||'',reporterDisplayName:r.reporter_display_name||'',targetUserId:r.target_user_id==null?null:String(r.target_user_id),targetUsername:r.target_username||'',reason:r.reason,details:r.details||'',status:r.status,createdAt:r.created_at})),limit,offset,hasMore});
  }catch(error){console.error('admin reports',error);return res.status(500).json({error:'admin reports unavailable'});}});

  app.patch('/api/admin/reports/:reportId',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const reportId=String(req.params.reportId||'');
    const status=typeof req.body?.status==='string'?req.body.status.trim().toUpperCase():'';
    if(!/^[1-9]\d*$/.test(reportId)||!['UNDER_REVIEW','RESOLVED','DISMISSED'].includes(status))return res.status(400).json({error:'valid report id and review status are required'});
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      const result=await client.query(`UPDATE safety_reports SET status=$1,updated_at=NOW() WHERE id=$2 AND status IN ('OPEN','UNDER_REVIEW') RETURNING id,status,updated_at`,[status,reportId]);
      if(!result.rowCount){await client.query('ROLLBACK');return res.status(404).json({error:'open report not found'});}
      await recordAdminActivity(req,'REPORT_REVIEWED','safety_report',reportId,`Report moved to ${status}`,{status},client);
      await client.query('COMMIT');
      return res.json({report:{id:String(result.rows[0].id),status:result.rows[0].status,updatedAt:result.rows[0].updated_at}});
    }catch(error){await client.query('ROLLBACK');throw error;}finally{client.release();}
  }catch(error){console.error('admin report review',error);return res.status(500).json({error:'admin report review failed'});}});

  app.get('/api/admin/appeals',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const limitValue=Number(req.query?.limit);
    const limit=Number.isInteger(limitValue)?Math.min(50,Math.max(1,limitValue)):50;
    const offsetValue=Number(req.query?.offset);
    const offset=Number.isInteger(offsetValue)?Math.min(10000,Math.max(0,offsetValue)):0;
    const result=await pool.query(`SELECT a.id,a.user_id,a.report_id,a.subject,a.details,a.status,a.decision_note,a.created_at,u.username,u.display_name,COALESCE(fs.account_status,fas.account_status,'ACTIVE') AS account_status FROM safety_appeals a JOIN users u ON u.id=a.user_id LEFT JOIN safety_settings fs ON fs.user_id=a.user_id LEFT JOIN fynx_account_safety fas ON fas.user_id=a.user_id WHERE a.status IN ('OPEN','UNDER_REVIEW') ORDER BY CASE a.status WHEN 'OPEN' THEN 0 ELSE 1 END,a.created_at ASC,a.id ASC LIMIT $1 OFFSET $2`,[limit+1,offset]);
    const hasMore=result.rows.length>limit;
    const rows=hasMore?result.rows.slice(0,limit):result.rows;
    return res.json({appeals:rows.map(a=>({id:String(a.id),userId:String(a.user_id),username:a.username||'',displayName:a.display_name||'',reportId:a.report_id==null?null:String(a.report_id),subject:a.subject,details:a.details,status:a.status,decisionNote:a.decision_note||'',accountStatus:a.account_status||'ACTIVE',createdAt:a.created_at})),limit,offset,hasMore});
  }catch(error){console.error('admin appeals',error);return res.status(500).json({error:'admin appeals unavailable'});}});

  app.patch('/api/admin/appeals/:appealId',auth,async(req,res)=>{try{
    await ensureSchema();
    if(!(await requireAdmin(req,res)))return;
    const appealId=String(req.params.appealId||'');
    const status=typeof req.body?.status==='string'?req.body.status.trim().toUpperCase():'';
    const note=typeof req.body?.decisionNote==='string'?req.body.decisionNote.trim().slice(0,500):'';
    if(!/^[1-9]\d*$/.test(appealId)||!['UNDER_REVIEW','ACCEPTED','REJECTED'].includes(status))return res.status(400).json({error:'valid appeal id and decision are required'});
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      const appeal=(await client.query('SELECT id,user_id,report_id,status FROM safety_appeals WHERE id=$1 FOR UPDATE',[appealId])).rows[0];
      if(!appeal){await client.query('ROLLBACK');return res.status(404).json({error:'appeal not found'});}
      if(!['OPEN','UNDER_REVIEW'].includes(appeal.status)){
        await client.query('ROLLBACK');
        return res.status(409).json({error:'appeal has already received a final decision'});
      }
      if(status==='UNDER_REVIEW'&&appeal.status!=='OPEN'){
        await client.query('ROLLBACK');
        return res.status(409).json({error:'appeal is already under review'});
      }
      const updated=await client.query(`UPDATE safety_appeals SET status=$1,decision_note=$2,updated_at=NOW() WHERE id=$3 RETURNING id,user_id,status,decision_note,updated_at`,[status,note,appealId]);
      let restored=false;
      if(status==='ACCEPTED'&&appeal.report_id!=null){
        const linkedReport=(await client.query('SELECT target_user_id FROM safety_reports WHERE id=$1 FOR UPDATE',[appeal.report_id])).rows[0];
        if(linkedReport&&String(linkedReport.target_user_id)===String(appeal.user_id)){
          await client.query(`INSERT INTO fynx_account_safety(user_id,account_status,status_note) VALUES($1,'ACTIVE',$2) ON CONFLICT(user_id) DO UPDATE SET account_status='ACTIVE',status_note=EXCLUDED.status_note,updated_at=NOW()`,[appeal.user_id,note||'Account restored after approved appeal.']);
          await client.query(`INSERT INTO safety_settings(user_id,account_status,status_note) VALUES($1,'ACTIVE',$2) ON CONFLICT(user_id) DO UPDATE SET account_status='ACTIVE',status_note=EXCLUDED.status_note,updated_at=NOW()`,[appeal.user_id,note||'Account restored after approved appeal.']);
          restored=true;
        }
      }
      await recordAdminActivity(req,'APPEAL_DECIDED','safety_appeal',appealId,`Appeal ${status.toLowerCase()}`,{status,decisionNote:note,restored},client);
      await client.query('COMMIT');
      const row=updated.rows[0];
      return res.json({appeal:{id:String(row.id),userId:String(row.user_id),status:row.status,decisionNote:row.decision_note,updatedAt:row.updated_at,restored}});
    }catch(error){await client.query('ROLLBACK');throw error;}finally{client.release();}
  }catch(error){console.error('admin appeal decision',error);return res.status(500).json({error:'appeal decision failed'});}});

  app.post('/api/admin/admins',auth,async(req,res)=>{try{
    await ensureSchema();
    const actorRole=await role(req.user.sub);
    if(actorRole!=='OWNER')return res.status(403).json({error:'owner access required',code:'OWNER_REQUIRED'});
    req.fynxAdminRole=actorRole;
    const userId=Number(req.body?.userId);
    if(!Number.isSafeInteger(userId)||userId<1)return res.status(400).json({error:'valid user id is required'});
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      const exists=(await client.query('SELECT id FROM users WHERE id=$1 FOR UPDATE',[userId])).rows[0];
      if(!exists){await client.query('ROLLBACK');return res.status(404).json({error:'user not found'});}
      if(await role(userId)==='OWNER'){await client.query('ROLLBACK');return res.status(400).json({error:'owner already has owner access'});}
      const result=await client.query(`INSERT INTO fynx_admin_roles(user_id,role) VALUES($1,'ADMIN') ON CONFLICT(user_id) DO NOTHING RETURNING user_id`,[userId]);
      if(result.rowCount)await recordAdminActivity(req,'ADMIN_GRANTED','user',userId,'Administrator access granted',{},client);
      await client.query('COMMIT');
      return res.status(result.rowCount?201:200).json({ok:true,granted:result.rowCount===1,userId:String(userId),role:'ADMIN'});
    }catch(error){await client.query('ROLLBACK');throw error;}finally{client.release();}
  }catch(error){console.error('admin grant',error);return res.status(500).json({error:'admin grant failed'});}});

  app.delete('/api/admin/admins/:userId',auth,async(req,res)=>{try{
    await ensureSchema();
    const actorRole=await role(req.user.sub);
    if(actorRole!=='OWNER')return res.status(403).json({error:'owner access required',code:'OWNER_REQUIRED'});
    req.fynxAdminRole=actorRole;
    const userId=Number(req.params.userId);
    if(!Number.isSafeInteger(userId)||userId<1)return res.status(400).json({error:'valid user id is required'});
    if(String(userId)===String(req.user.sub))return res.status(400).json({error:'owner role cannot be removed'});
    if(await role(userId)==='OWNER')return res.status(403).json({error:'owner role cannot be removed'});
    const client=await pool.connect();
    try{
      await client.query('BEGIN');
      const result=await client.query('DELETE FROM fynx_admin_roles WHERE user_id=$1 RETURNING user_id',[userId]);
      if(result.rowCount)await recordAdminActivity(req,'ADMIN_REVOKED','user',userId,'Administrator access revoked',{},client);
      await client.query('COMMIT');
      return res.json({ok:true,removed:result.rowCount>0,userId:String(userId)});
    }catch(error){await client.query('ROLLBACK');throw error;}finally{client.release();}
  }catch(error){console.error('admin revoke',error);return res.status(500).json({error:'admin revoke failed'});}});
}
