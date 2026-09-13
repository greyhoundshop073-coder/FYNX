import pg from 'pg';
import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({ connectionString: DATABASE_URL, ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false, max: 2, min: 0, idleTimeoutMillis: 30_000, connectionTimeoutMillis: 5_000, statement_timeout: 10_000, query_timeout: 12_000, keepAlive: true }) : null;

export function registerGroupMembershipRoutes({ app }) {
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
      ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS send_messages BOOLEAN NOT NULL DEFAULT TRUE;
      ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS send_media BOOLEAN NOT NULL DEFAULT TRUE;
      ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS add_members BOOLEAN NOT NULL DEFAULT TRUE;
      ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS invite_links BOOLEAN NOT NULL DEFAULT TRUE;
      ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS approve_new_members BOOLEAN NOT NULL DEFAULT FALSE;
      ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS disappearing_seconds INTEGER NOT NULL DEFAULT 0;
      CREATE TABLE IF NOT EXISTS fynx_group_invites (token TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), revoked_at TIMESTAMPTZ);
      CREATE INDEX IF NOT EXISTS fynx_group_invites_group_idx ON fynx_group_invites(group_id, created_at DESC);
      CREATE TABLE IF NOT EXISTS fynx_group_reports (id TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, reason TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE INDEX IF NOT EXISTS fynx_group_reports_group_idx ON fynx_group_reports(group_id, created_at DESC);
    `).catch(error => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };
  const member = async (groupId, userId) => (await pool.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1', [groupId, userId])).rows[0] || null;
  const target = async (groupId, username) => (await pool.query(`SELECT m.user_id,m.role,u.username FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1`, [groupId, String(username || '').trim().replace(/^@+/, '')])).rows[0] || null;
  const cleanUsername = value => String(value || '').trim().replace(/^@+/, '');
  const isManager = role => ['ADMIN', 'MODERATOR'].includes(String(role));
  const isAdmin = role => String(role) === 'ADMIN';

  app.get('/api/groups/:groupId/members', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      if (!(await member(groupId, req.user.sub))) return res.status(403).json({ error: 'group membership required' });
      const rows = (await pool.query(`SELECT u.username,m.role,m.created_at FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 ORDER BY CASE m.role WHEN 'ADMIN' THEN 0 WHEN 'MODERATOR' THEN 1 ELSE 2 END, lower(u.username)`, [groupId])).rows;
      return res.json({ members: rows.map(row => ({ username: row.username, role: row.role, joinedAt: new Date(row.created_at).getTime() })) });
    } catch (error) {
      console.error('group members list', error);
      return res.status(500).json({ error: 'group members lookup failed' });
    }
  });

  app.get('/api/groups/:groupId/permissions', auth, async (req, res) => {
    try {
      await ensureSchema();
      if (!(await member(String(req.params?.groupId || ''), req.user.sub))) return res.status(403).json({ error: 'group membership required' });
      const row = (await pool.query(`SELECT send_messages,send_media,add_members,invite_links,approve_new_members,disappearing_seconds FROM fynx_groups WHERE id=$1 LIMIT 1`, [String(req.params?.groupId || '')])).rows[0];
      if (!row) return res.status(404).json({ error: 'group not found' });
      return res.json({ permissions: row });
    } catch (error) {
      console.error('group permissions get', error);
      return res.status(500).json({ error: 'group permissions lookup failed' });
    }
  });

  app.patch('/api/groups/:groupId/permissions', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!isAdmin(actor?.role)) return res.status(403).json({ error: 'group admin permission required' });
      const bool = (value, fallback) => typeof value === 'boolean' ? value : fallback;
      const int = (value, fallback) => Number.isInteger(value) && value >= 0 && value <= 90 * 24 * 60 * 60 ? value : fallback;
      const current = (await pool.query(`SELECT send_messages,send_media,add_members,invite_links,approve_new_members,disappearing_seconds FROM fynx_groups WHERE id=$1 LIMIT 1`, [groupId])).rows[0];
      if (!current) return res.status(404).json({ error: 'group not found' });
      const next = {
        send_messages: bool(req.body?.sendMessages, current.send_messages),
        send_media: bool(req.body?.sendMedia, current.send_media),
        add_members: bool(req.body?.addMembers, current.add_members),
        invite_links: bool(req.body?.inviteLinks, current.invite_links),
        approve_new_members: bool(req.body?.approveNewMembers, current.approve_new_members),
        disappearing_seconds: int(req.body?.disappearingSeconds, current.disappearing_seconds)
      };
      const row = (await pool.query(`UPDATE fynx_groups SET send_messages=$1,send_media=$2,add_members=$3,invite_links=$4,approve_new_members=$5,disappearing_seconds=$6,updated_at=NOW() WHERE id=$7 RETURNING send_messages,send_media,add_members,invite_links,approve_new_members,disappearing_seconds`, [next.send_messages,next.send_media,next.add_members,next.invite_links,next.approve_new_members,next.disappearing_seconds,groupId])).rows[0];
      return res.json({ permissions: row });
    } catch (error) {
      console.error('group permissions update', error);
      return res.status(500).json({ error: 'group permissions update failed' });
    }
  });

  app.post('/api/groups/:groupId/invites', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      const settings = (await pool.query('SELECT invite_links FROM fynx_groups WHERE id=$1 LIMIT 1', [groupId])).rows[0];
      if (!settings) return res.status(404).json({ error: 'group not found' });
      if (!isManager(actor?.role) || !settings.invite_links) return res.status(403).json({ error: 'group invite permission required' });
      const token = randomUUID().replaceAll('-', '');
      await pool.query('INSERT INTO fynx_group_invites(token,group_id,created_by) VALUES($1,$2,$3)', [token,groupId,req.user.sub]);
      return res.status(201).json({ token, inviteUri: `fynx://group/${groupId}/invite/${token}` });
    } catch (error) {
      console.error('group invite create', error);
      return res.status(500).json({ error: 'group invite creation failed' });
    }
  });

  app.post('/api/groups/:groupId/invites/:token/revoke', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!isManager(actor?.role)) return res.status(403).json({ error: 'group management permission required' });
      await pool.query('UPDATE fynx_group_invites SET revoked_at=NOW() WHERE group_id=$1 AND token=$2 AND revoked_at IS NULL', [groupId,String(req.params?.token || '')]);
      return res.json({ revoked: true });
    } catch (error) {
      console.error('group invite revoke', error);
      return res.status(500).json({ error: 'group invite revoke failed' });
    }
  });

  app.post('/api/groups/:groupId/invites/:token/join', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const invite = (await pool.query(`SELECT i.group_id,i.revoked_at,g.approve_new_members,g.name FROM fynx_group_invites i JOIN fynx_groups g ON g.id=i.group_id WHERE i.group_id=$1 AND i.token=$2 LIMIT 1`, [groupId,String(req.params?.token || '')])).rows[0];
      if (!invite || invite.revoked_at) return res.status(404).json({ error: 'invite is invalid or revoked' });
      if (await member(groupId, req.user.sub)) return res.json({ joined: true, alreadyMember: true, pending: false });
      if (invite.approve_new_members) return res.status(202).json({ joined: false, pending: true, message: 'Join request submitted for admin approval.' });
      await pool.query(`INSERT INTO fynx_group_members(group_id,user_id,role) VALUES($1,$2,'MEMBER') ON CONFLICT(group_id,user_id) DO NOTHING`, [groupId,req.user.sub]);
      return res.json({ joined: true, pending: false, groupName: invite.name });
    } catch (error) {
      console.error('group invite join', error);
      return res.status(500).json({ error: 'group invite join failed' });
    }
  });

  app.post('/api/groups/:groupId/members/:username/promote', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!isAdmin(actor?.role)) return res.status(403).json({ error: 'group admin permission required' });
      const victim = await target(groupId, req.params?.username);
      if (!victim) return res.status(404).json({ error: 'member not found' });
      if (String(victim.role) === 'ADMIN') return res.json({ promoted: true, role: 'ADMIN' });
      await pool.query(`UPDATE fynx_group_members SET role='MODERATOR' WHERE group_id=$1 AND user_id=$2`, [groupId,victim.user_id]);
      return res.json({ promoted: true, role: 'MODERATOR', username: victim.username });
    } catch (error) {
      console.error('group member promote', error);
      return res.status(500).json({ error: 'group member promotion failed' });
    }
  });

  app.post('/api/groups/:groupId/members/:username/demote', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!isAdmin(actor?.role)) return res.status(403).json({ error: 'group admin permission required' });
      const victim = await target(groupId, req.params?.username);
      if (!victim) return res.status(404).json({ error: 'member not found' });
      if (String(victim.role) === 'ADMIN') return res.status(403).json({ error: 'group owner/admin cannot be demoted' });
      await pool.query(`UPDATE fynx_group_members SET role='MEMBER' WHERE group_id=$1 AND user_id=$2`, [groupId,victim.user_id]);
      return res.json({ demoted: true, role: 'MEMBER', username: victim.username });
    } catch (error) {
      console.error('group member demote', error);
      return res.status(500).json({ error: 'group member demotion failed' });
    }
  });

  app.post('/api/groups/:groupId/ownership', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      const targetUser = await target(groupId, req.body?.username);
      const group = (await pool.query('SELECT owner_id FROM fynx_groups WHERE id=$1 LIMIT 1', [groupId])).rows[0];
      if (!group || !targetUser) return res.status(404).json({ error: 'group or target member not found' });
      if (!isAdmin(actor?.role) || String(group.owner_id) !== String(req.user.sub)) return res.status(403).json({ error: 'group owner permission required' });
      if (String(targetUser.user_id) === String(req.user.sub)) return res.status(400).json({ error: 'target must be another member' });
      await pool.query('BEGIN');
      try {
        await pool.query(`UPDATE fynx_groups SET owner_id=$1,updated_at=NOW() WHERE id=$2`, [targetUser.user_id,groupId]);
        await pool.query(`UPDATE fynx_group_members SET role='MODERATOR' WHERE group_id=$1 AND user_id=$2`, [groupId,req.user.sub]);
        await pool.query(`UPDATE fynx_group_members SET role='ADMIN' WHERE group_id=$1 AND user_id=$2`, [groupId,targetUser.user_id]);
        await pool.query('COMMIT');
      } catch (error) { await pool.query('ROLLBACK'); throw error; }
      return res.json({ transferred: true, ownerUsername: targetUser.username });
    } catch (error) {
      console.error('group ownership transfer', error);
      return res.status(500).json({ error: 'group ownership transfer failed' });
    }
  });

  app.post('/api/groups/:groupId/reports', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      if (!(await member(groupId, req.user.sub))) return res.status(403).json({ error: 'group membership required' });
      const reason = String(req.body?.reason || '').trim().slice(0,500);
      if (!reason) return res.status(400).json({ error: 'report reason is required' });
      const targetUser = req.body?.username ? await target(groupId, req.body.username) : null;
      const id = randomUUID();
      await pool.query('INSERT INTO fynx_group_reports(id,group_id,reporter_id,target_user_id,reason) VALUES($1,$2,$3,$4,$5)', [id,groupId,req.user.sub,targetUser?.user_id||null,reason]);
      return res.status(201).json({ reportId:id, submitted:true });
    } catch (error) {
      console.error('group report', error);
      return res.status(500).json({ error: 'group report submission failed' });
    }
  });

  app.post('/api/groups/:groupId/leave', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const current = await member(groupId, req.user.sub);
      if (!current) return res.status(403).json({ error: 'group membership required' });
      const owner = (await pool.query('SELECT owner_id FROM fynx_groups WHERE id=$1 LIMIT 1', [groupId])).rows[0];
      if (!owner) return res.status(404).json({ error: 'group not found' });
      if (String(owner.owner_id) === String(req.user.sub)) return res.status(409).json({ error: 'group owner must transfer ownership before leaving' });
      await pool.query('DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id=$2', [groupId, req.user.sub]);
      return res.json({ left: true });
    } catch (error) {
      console.error('group leave', error);
      return res.status(500).json({ error: 'group leave failed' });
    }
  });

  app.delete('/api/groups/:groupId/members/:username', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor || !isManager(actor.role)) return res.status(403).json({ error: 'group management permission required' });
      const victim = await target(groupId, req.params?.username);
      if (!victim) return res.status(404).json({ error: 'member not found' });
      if (String(victim.user_id) === String(req.user.sub)) return res.status(400).json({ error: 'use leave endpoint to leave the group' });
      const group = (await pool.query('SELECT owner_id FROM fynx_groups WHERE id=$1 LIMIT 1', [groupId])).rows[0];
      if (!group) return res.status(404).json({ error: 'group not found' });
      if (String(group.owner_id) === String(victim.user_id)) return res.status(403).json({ error: 'group owner cannot be removed' });
      if (String(actor.role) === 'MODERATOR' && String(victim.role) !== 'MEMBER') return res.status(403).json({ error: 'moderators can only remove members' });
      await pool.query('DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id=$2', [groupId, victim.user_id]);
      return res.json({ removed: true, username: victim.username });
    } catch (error) {
      console.error('group remove member', error);
      return res.status(500).json({ error: 'group member removal failed' });
    }
  });
}
