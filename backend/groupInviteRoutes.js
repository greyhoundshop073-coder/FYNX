import pg from 'pg';
import jwt from 'jsonwebtoken';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 2, min: 0, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 10_000, query_timeout: 12_000, keepAlive: true }) : null;

export function registerGroupInviteRoutes({ app }) {
  if (!pool) return;
  const auth = (req, res, next) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
    try { req.user = jwt.verify(token, JWT_SECRET); return next(); }
    catch { return res.status(401).json({ error: 'invalid or expired token' }); }
  };
  let schemaPromise;
  const ensureSchema = async () => {
    if (!schemaPromise) schemaPromise = pool.query(`
      CREATE TABLE IF NOT EXISTS fynx_group_join_requests (
        group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE,
        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        PRIMARY KEY (group_id,user_id)
      );
      CREATE INDEX IF NOT EXISTS fynx_group_join_requests_group_idx ON fynx_group_join_requests(group_id,created_at ASC);
    `).catch(error => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };
  const member = async (groupId, userId) => (await pool.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1', [groupId,userId])).rows[0] || null;
  const isAdmin = role => String(role) === 'ADMIN';

  app.get('/api/groups/:groupId/invites/:token', auth, async (req,res) => {
    try {
      await ensureSchema();
      const groupId=String(req.params?.groupId||'');
      const token=String(req.params?.token||'');
      const row=(await pool.query(`SELECT i.token,i.revoked_at,g.name,g.description,g.approve_new_members,(SELECT COUNT(*) FROM fynx_group_members m WHERE m.group_id=g.id) AS member_count FROM fynx_group_invites i JOIN fynx_groups g ON g.id=i.group_id WHERE i.group_id=$1 AND i.token=$2 LIMIT 1`,[groupId,token])).rows[0];
      if(!row || row.revoked_at) return res.status(404).json({error:'invite is invalid or revoked'});
      const current=await member(groupId,req.user.sub);
      const pending=(await pool.query('SELECT 1 FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2 LIMIT 1',[groupId,req.user.sub])).rowCount>0;
      return res.json({invite:{groupId,token:row.token,name:row.name,description:row.description,memberCount:Number(row.member_count),approveNewMembers:row.approve_new_members},alreadyMember:Boolean(current),pending});
    }catch(error){
      console.error('group invite preview',error);
      return res.status(500).json({error:'group invite preview failed'});
    }
  });

  app.get('/api/groups/:groupId/join-requests', auth, async (req,res) => {
    try {
      await ensureSchema();
      const groupId=String(req.params?.groupId||'');
      const actor=await member(groupId,req.user.sub);
      if(!isAdmin(actor?.role)) return res.status(403).json({error:'group admin permission required'});
      const rows=(await pool.query(`SELECT u.username,r.created_at FROM fynx_group_join_requests r JOIN users u ON u.id=r.user_id WHERE r.group_id=$1 ORDER BY r.created_at ASC`,[groupId])).rows;
      return res.json({requests:rows.map(row=>({username:row.username,requestedAt:new Date(row.created_at).getTime()}))});
    }catch(error){
      console.error('group join requests list',error);
      return res.status(500).json({error:'group join requests lookup failed'});
    }
  });

  const decideRequest = async (req,res,approved) => {
    try {
      await ensureSchema();
      const groupId=String(req.params?.groupId||'');
      const username=String(req.params?.username||'').trim().replace(/^@+/,'');
      const actor=await member(groupId,req.user.sub);
      if(!isAdmin(actor?.role)) return res.status(403).json({error:'group admin permission required'});
      const request=(await pool.query(`SELECT r.user_id,u.username FROM fynx_group_join_requests r JOIN users u ON u.id=r.user_id WHERE r.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1`,[groupId,username])).rows[0];
      if(!request) return res.status(404).json({error:'join request not found'});
      if(approved) await pool.query(`INSERT INTO fynx_group_members(group_id,user_id,role) VALUES($1,$2,'MEMBER') ON CONFLICT(group_id,user_id) DO NOTHING`,[groupId,request.user_id]);
      await pool.query('DELETE FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2',[groupId,request.user_id]);
      return res.json({approved,username:request.username});
    }catch(error){
      console.error('group join request decision',error);
      return res.status(500).json({error:'group join request decision failed'});
    }
  };

  app.post('/api/groups/:groupId/join-requests/:username/approve',auth,(req,res)=>decideRequest(req,res,true));
  app.post('/api/groups/:groupId/join-requests/:username/reject',auth,(req,res)=>decideRequest(req,res,false));
}
