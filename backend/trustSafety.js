const SCAM_PATTERNS = [
  /\b(?:send|pay|transfer)\b.{0,40}\b(?:crypto|bitcoin|usdt|gift card|voucher)\b/i,
  /\b(?:whatsapp|telegram|signal)\b.{0,50}\b(?:pay|payment|deposit|fee)\b/i,
  /\b(?:verification|unlock|release)\b.{0,50}\b(?:fee|payment|deposit|money)\b/i,
  /\b(?:otp|one[- ]time password|verification code|2fa code)\b/i,
  /\b(?:password|passcode|seed phrase|private key)\b.{0,40}\b(?:send|share|give|tell)\b/i,
  /\b(?:pay|send|transfer)\b.{0,25}\b(?:first|upfront|advance)\b/i
];

const HARD_BLOCK_PATTERNS = [
  /\b(?:send|share|give|tell)\b.{0,35}\b(?:otp|one[- ]time password|verification code|2fa code|seed phrase|private key)\b/i,
  /\b(?:pay|send|transfer)\b.{0,35}\b(?:bitcoin|crypto|usdt|gift card|voucher)\b.{0,60}\b(?:whatsapp|telegram|signal|outside|off[- ]platform)\b/i,
  /\b(?:verification|unlock|release)\b.{0,45}\b(?:fee|payment|deposit)\b.{0,45}\b(?:whatsapp|telegram|signal|outside|off[- ]platform)\b/i
];

const SPAM_PATTERNS = [
  /(.)\1{9,}/,
  /(?:https?:\/\/\S+\s*){4,}/i,
  /\b(?:click|claim|win|free)\b.{0,20}\b(?:now|today|urgent)\b/i
];

function normalizeText(value) {
  return typeof value === "string" ? value.replace(/[\u0000-\u001f\u007f]/g, " ").trim().slice(0, 4000) : "";
}

export function inspectTrustSafetyText(value) {
  const text = normalizeText(value);
  const scamSignals = SCAM_PATTERNS.reduce((count, pattern) => count + (pattern.test(text) ? 1 : 0), 0);
  const spamSignals = SPAM_PATTERNS.reduce((count, pattern) => count + (pattern.test(text) ? 1 : 0), 0);
  const hardBlock = HARD_BLOCK_PATTERNS.some((pattern) => pattern.test(text));
  const risk = scamSignals >= 2 || hardBlock ? "HIGH" : scamSignals === 1 || spamSignals >= 2 ? "MEDIUM" : "LOW";
  return {
    risk,
    scamSignals,
    spamSignals,
    shouldWarn: risk !== "LOW",
    shouldBlock: hardBlock || scamSignals >= 2
  };
}

const safetySchemaPromises = new WeakMap();
async function ensureSafetySchema(pool) {
  if (!pool || (typeof pool !== "object" && typeof pool !== "function")) {
    throw new TypeError("a database pool or client is required");
  }
  let schemaPromise = safetySchemaPromises.get(pool);
  if (!schemaPromise) {
    schemaPromise = pool.query(`
    CREATE TABLE IF NOT EXISTS safety_content_reports (
      id BIGSERIAL PRIMARY KEY,
      reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      target_type TEXT NOT NULL CHECK(target_type IN ('SOCIAL_POST','SOCIAL_COMMENT','MARKETPLACE_LISTING')),
      target_id BIGINT NOT NULL CHECK(target_id > 0),
      target_owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      reason TEXT NOT NULL,
      details TEXT NOT NULL DEFAULT '',
      status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','RESOLVED','DISMISSED')),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS safety_content_reports_queue_idx ON safety_content_reports(status,created_at ASC);
    CREATE INDEX IF NOT EXISTS safety_content_reports_reporter_idx ON safety_content_reports(reporter_id,created_at DESC);
    CREATE UNIQUE INDEX IF NOT EXISTS safety_content_reports_open_duplicate_idx
      ON safety_content_reports(reporter_id,target_type,target_id)
      WHERE status IN ('OPEN','UNDER_REVIEW');
    CREATE TABLE IF NOT EXISTS safety_content_moderation_audit (
      id BIGSERIAL PRIMARY KEY,
      report_id BIGINT NOT NULL REFERENCES safety_content_reports(id) ON DELETE RESTRICT,
      moderator_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
      previous_status TEXT NOT NULL,
      new_status TEXT NOT NULL CHECK(new_status IN ('UNDER_REVIEW','RESOLVED','DISMISSED')),
      decision_note TEXT NOT NULL DEFAULT '',
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
    CREATE INDEX IF NOT EXISTS safety_content_moderation_audit_report_idx ON safety_content_moderation_audit(report_id,created_at DESC);
    CREATE TABLE IF NOT EXISTS safety_settings (user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, message_safety BOOLEAN NOT NULL DEFAULT TRUE, marketplace_safety BOOLEAN NOT NULL DEFAULT TRUE, login_alerts BOOLEAN NOT NULL DEFAULT TRUE, account_status TEXT NOT NULL DEFAULT 'ACTIVE', status_note TEXT NOT NULL DEFAULT '', updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
    CREATE TABLE IF NOT EXISTS safety_reports (id BIGSERIAL PRIMARY KEY, reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, target_username TEXT NOT NULL DEFAULT '', reason TEXT NOT NULL, details TEXT NOT NULL DEFAULT '', status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','RESOLVED','DISMISSED')), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
    CREATE INDEX IF NOT EXISTS safety_reports_reporter_idx ON safety_reports(reporter_id,created_at DESC);
    CREATE INDEX IF NOT EXISTS safety_reports_target_idx ON safety_reports(target_user_id,created_at DESC);
    CREATE TABLE IF NOT EXISTS safety_appeals (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, report_id BIGINT REFERENCES safety_reports(id) ON DELETE SET NULL, subject TEXT NOT NULL, details TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','UNDER_REVIEW','ACCEPTED','REJECTED')), decision_note TEXT NOT NULL DEFAULT '', created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
    CREATE INDEX IF NOT EXISTS safety_appeals_user_idx ON safety_appeals(user_id,created_at DESC);
  `).catch(error => {
      safetySchemaPromises.delete(pool);
      throw error;
    });
    safetySchemaPromises.set(pool, schemaPromise);
  }
  return schemaPromise;
}
export function registerTrustSafetyRoutes({ app, pool, auth }) {
  if (!pool) return;
  app.post("/api/social/content-reports", auth, async (req,res) => {
    try {
      await ensureSafetySchema(pool);
      const targetType=typeof req.body?.targetType==="string"?req.body.targetType.trim().toUpperCase():"";
      const targetId=Number(req.body?.targetId);
      const reason=typeof req.body?.reason==="string"?req.body.reason.trim().slice(0,80):"";
      const details=typeof req.body?.details==="string"?req.body.details.trim().slice(0,2000):"";
      if(!["SOCIAL_POST","SOCIAL_COMMENT","MARKETPLACE_LISTING"].includes(targetType)||!Number.isSafeInteger(targetId)||targetId<1||reason.length<3)
        return res.status(400).json({error:"valid content type, content id and report reason are required"});
      let target;
      if(targetType==="SOCIAL_POST") {
        target=(await pool.query(`SELECT p.id,p.author_id AS owner_id FROM social_posts p WHERE p.id=$1 AND (
          p.visibility='PUBLIC' OR p.author_id=$2
          OR (p.visibility='FRIENDS_ONLY' AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=p.author_id)) AND f.status='accepted'))
          OR (p.visibility='SELECTED_PEOPLE' AND EXISTS(SELECT 1 FROM social_post_audience a WHERE a.post_id=p.id AND a.user_id=$2)
            AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=p.author_id)) AND f.status='accepted'))
        ) LIMIT 1`,[targetId,req.user.sub])).rows[0];
      } else if(targetType==="SOCIAL_COMMENT") {
        target=(await pool.query(`SELECT c.id,c.author_id AS owner_id FROM social_post_comments c JOIN social_posts p ON p.id=c.post_id
          WHERE c.id=$1 AND (p.visibility='PUBLIC' OR p.author_id=$2 OR c.author_id=$2
            OR (p.visibility='FRIENDS_ONLY' AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=p.author_id)) AND f.status='accepted'))
            OR (p.visibility='SELECTED_PEOPLE' AND EXISTS(SELECT 1 FROM social_post_audience x WHERE x.post_id=p.id AND x.user_id=$2)
              AND EXISTS(SELECT 1 FROM friendships f WHERE ((f.user_id=p.author_id AND f.friend_id=$2) OR (f.user_id=$2 AND f.friend_id=p.author_id)) AND f.status='accepted')))
          LIMIT 1`,[targetId,req.user.sub])).rows[0];
      } else {
        target=(await pool.query("SELECT id,seller_id AS owner_id FROM marketplace_listings WHERE id=$1 AND active=TRUE LIMIT 1",[targetId])).rows[0];
      }
      if(!target)return res.status(404).json({error:"content not found"});
      if(String(target.owner_id)===String(req.user.sub))return res.status(400).json({error:"you cannot report your own content"});
      const result=await pool.query(`INSERT INTO safety_content_reports(reporter_id,target_type,target_id,target_owner_id,reason,details)
        VALUES($1,$2,$3,$4,$5,$6)
        ON CONFLICT (reporter_id,target_type,target_id) WHERE status IN ('OPEN','UNDER_REVIEW') DO NOTHING
        RETURNING id,target_type,target_id,reason,status,created_at`,[req.user.sub,targetType,targetId,target.owner_id,reason,details]);
      if(!result.rowCount)return res.status(409).json({error:"you already have an open report for this content"});
      const row=result.rows[0];
      return res.status(201).json({report:{id:String(row.id),targetType:row.target_type,targetId:String(row.target_id),reason:row.reason,status:row.status,createdAt:row.created_at}});
    } catch(error) {
      console.error("content report submit",error);
      return res.status(500).json({error:"content report submission failed"});
    }
  });
  app.get("/api/social/content-reports/mine",auth,async(req,res)=>{
    try {
      await ensureSafetySchema(pool);
      const result=await pool.query("SELECT id,target_type,target_id,reason,status,created_at,updated_at FROM safety_content_reports WHERE reporter_id=$1 ORDER BY created_at DESC LIMIT 100",[req.user.sub]);
      return res.json({reports:result.rows.map(row=>({id:String(row.id),targetType:row.target_type,targetId:String(row.target_id),reason:row.reason,status:row.status,createdAt:row.created_at,updatedAt:row.updated_at}))});
    } catch(error) { console.error("content report history",error); return res.status(500).json({error:"content report history unavailable"}); }
  });
  app.get("/api/safety", auth, async (req,res)=>{try{await ensureSafetySchema(pool);const r=await pool.query("SELECT message_safety,marketplace_safety,login_alerts,account_status,status_note FROM safety_settings WHERE user_id=$1",[req.user.sub]);const x=r.rows[0]||{message_safety:true,marketplace_safety:true,login_alerts:true,account_status:"ACTIVE",status_note:""};return res.json({safety:{messageSafety:Boolean(x.message_safety),marketplaceSafety:Boolean(x.marketplace_safety),loginAlerts:Boolean(x.login_alerts),accountStatus:x.account_status,statusNote:x.status_note}})}catch(e){console.error("safety settings",e);return res.status(500).json({error:"safety settings failed"})}});
  app.patch("/api/safety", auth, async (req,res)=>{try{await ensureSafetySchema(pool);const keys=["messageSafety","marketplaceSafety","loginAlerts"];const provided=keys.filter(k=>typeof req.body?.[k]==="boolean");if(provided.length!==1)return res.status(400).json({error:"provide exactly one safety setting"});const key=provided[0],column=key==="messageSafety"?"message_safety":key==="marketplaceSafety"?"marketplace_safety":"login_alerts";await pool.query(`INSERT INTO safety_settings(user_id,${column}) VALUES($1,$2) ON CONFLICT(user_id) DO UPDATE SET ${column}=EXCLUDED.${column},updated_at=NOW()`,[req.user.sub,req.body[key]]);const x=(await pool.query("SELECT message_safety,marketplace_safety,login_alerts,account_status,status_note FROM safety_settings WHERE user_id=$1",[req.user.sub])).rows[0];return res.json({safety:{messageSafety:Boolean(x.message_safety),marketplaceSafety:Boolean(x.marketplace_safety),loginAlerts:Boolean(x.login_alerts),accountStatus:x.account_status,statusNote:x.status_note}})}catch(e){console.error("safety update",e);return res.status(500).json({error:"safety setting update failed"})}});
  app.get("/api/social/reports/mine",auth,async(req,res)=>{try{await ensureSafetySchema(pool);const r=await pool.query("SELECT id,target_username,reason,status,created_at FROM safety_reports WHERE reporter_id=$1 ORDER BY created_at DESC LIMIT 100",[req.user.sub]);return res.json({reports:r.rows.map(x=>({id:String(x.id),targetUsername:x.target_username,reason:x.reason,status:x.status,createdAt:x.created_at}))})}catch(e){return res.status(500).json({error:"report history failed"})}});
  app.post("/api/social/reports",auth,async(req,res)=>{try{await ensureSafetySchema(pool);const targetUsername=typeof req.body?.targetUsername==="string"?req.body.targetUsername.trim().toLowerCase().replace(/^@+/,"").slice(0,64):"",reason=typeof req.body?.reason==="string"?req.body.reason.trim().slice(0,80):"",details=typeof req.body?.details==="string"?req.body.details.trim().slice(0,4000):"";if(targetUsername.length<2||reason.length<3)return res.status(400).json({error:"target username and report reason are required"});const target=(await pool.query("SELECT id,username FROM users WHERE lower(username)=lower($1) LIMIT 1",[targetUsername])).rows[0];if(!target)return res.status(404).json({error:"reported user not found"});if(String(target.id)===String(req.user.sub))return res.status(400).json({error:"you cannot report your own account"});const recent=await pool.query("SELECT id FROM safety_reports WHERE reporter_id=$1 AND target_user_id=$2 AND created_at>NOW()-INTERVAL '24 hours' AND status IN ('OPEN','UNDER_REVIEW') LIMIT 1",[req.user.sub,target.id]);if(recent.rowCount)return res.status(409).json({error:"you already have an open report for this account"});const r=await pool.query("INSERT INTO safety_reports(reporter_id,target_user_id,target_username,reason,details) VALUES($1,$2,$3,$4,$5) RETURNING id,target_username,reason,status,created_at",[req.user.sub,target.id,target.username,reason,details]);const x=r.rows[0];return res.status(201).json({report:{id:String(x.id),targetUsername:x.target_username,reason:x.reason,status:x.status,createdAt:x.created_at}})}catch(e){console.error("report submit",e);return res.status(500).json({error:"report submission failed"})}});
  app.get("/api/safety/appeals",auth,async(req,res)=>{try{await ensureSafetySchema(pool);const r=await pool.query("SELECT id,report_id,subject,status,decision_note,created_at FROM safety_appeals WHERE user_id=$1 ORDER BY created_at DESC LIMIT 100",[req.user.sub]);return res.json({appeals:r.rows.map(x=>({id:String(x.id),reportId:x.report_id==null?null:String(x.report_id),subject:x.subject,status:x.status,decisionNote:x.decision_note,createdAt:x.created_at}))})}catch(e){return res.status(500).json({error:"appeal history failed"})}});
  app.post("/api/safety/appeals",auth,async(req,res)=>{try{await ensureSafetySchema(pool);const reportId=req.body?.reportId==null||Number(req.body.reportId)<1?null:Number(req.body.reportId),subject=typeof req.body?.subject==="string"?req.body.subject.trim().slice(0,120):"",details=typeof req.body?.details==="string"?req.body.details.trim().slice(0,4000):"";if(subject.length<3||details.length<10)return res.status(400).json({error:"appeal subject and details are required"});if(reportId!=null){const r=await pool.query("SELECT id FROM safety_reports WHERE id=$1 AND reporter_id=$2",[reportId,req.user.sub]);if(!r.rowCount)return res.status(404).json({error:"report not found for this account"})}const r=await pool.query("INSERT INTO safety_appeals(user_id,report_id,subject,details) VALUES($1,$2,$3,$4) RETURNING id,report_id,subject,status,decision_note,created_at",[req.user.sub,reportId,subject,details]);const x=r.rows[0];return res.status(201).json({appeal:{id:String(x.id),reportId:x.report_id==null?null:String(x.report_id),subject:x.subject,status:x.status,decisionNote:x.decision_note,createdAt:x.created_at}})}catch(e){return res.status(500).json({error:"appeal submission failed"})}});
  app.post("/api/safety/content-check", auth, async (req, res) => {
    try {
      const text = normalizeText(req.body?.text);
      if (!text) return res.status(400).json({ error: "text is required" });
      return res.json({ safety: inspectTrustSafetyText(text) });
    } catch (error) {
      console.error("safety content check", error);
      return res.status(500).json({ error: "safety check failed" });
    }
  });
}
