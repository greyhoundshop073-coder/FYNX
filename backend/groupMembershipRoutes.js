import { getFynxDatabasePool } from "./databasePool.js";
import pg from 'pg';
import jwt from 'jsonwebtoken';
import { randomUUID } from 'node:crypto';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = getFynxDatabasePool();
const validId = value => typeof value === 'string' && /^[A-Za-z0-9_-]{8,80}$/.test(value);

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
      ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS show_history_to_new_members BOOLEAN NOT NULL DEFAULT TRUE;
      CREATE TABLE IF NOT EXISTS fynx_group_invites (token TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), revoked_at TIMESTAMPTZ);
      CREATE INDEX IF NOT EXISTS fynx_group_invites_group_idx ON fynx_group_invites(group_id, created_at DESC);
      CREATE TABLE IF NOT EXISTS fynx_group_join_requests (group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY(group_id,user_id));
      CREATE INDEX IF NOT EXISTS fynx_group_join_requests_group_idx ON fynx_group_join_requests(group_id, created_at ASC);
      CREATE TABLE IF NOT EXISTS fynx_group_reports (id TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE, reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, target_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL, reason TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
      CREATE INDEX IF NOT EXISTS fynx_group_reports_group_idx ON fynx_group_reports(group_id, created_at DESC);
    `).catch(error => { schemaPromise = undefined; throw error; });
    return schemaPromise;
  };
  const member = async (groupId, userId) => (await pool.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1', [groupId, userId])).rows[0] || null;
  const target = async (groupId, username) => (await pool.query(`SELECT m.user_id,m.role,u.username FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1`, [groupId, String(username || '').trim().replace(/^@+/, '')])).rows[0] || null;
  const cleanUsername = value => String(value || '').trim().replace(/^@+/, '');
  const targetJoinRequest = async (groupId, username) => (await pool.query('SELECT r.user_id,u.username FROM fynx_group_join_requests r JOIN users u ON u.id=r.user_id WHERE r.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1', [groupId, cleanUsername(username)])).rows[0] || null;
  const isManager = role => ['ADMIN', 'MODERATOR'].includes(String(role));
  const isAdmin = role => String(role) === 'ADMIN';
  const canManageMembers = role => ['ADMIN', 'MODERATOR'].includes(String(role || '').toUpperCase());
  const insertMemberIfCapacity = async (db, groupId, userId) => {
    const inserted = await db.query(`WITH available_group AS (
      UPDATE fynx_groups SET updated_at=updated_at
      WHERE id=$1 AND (SELECT COUNT(*) FROM fynx_group_members WHERE group_id=$1) < 500
      RETURNING id
    )
    INSERT INTO fynx_group_members(group_id,user_id,role)
      SELECT available_group.id,$2,'MEMBER' FROM available_group
      ON CONFLICT(group_id,user_id) DO NOTHING RETURNING user_id`, [groupId,userId]);
    if (inserted.rowCount) return true;
    return Boolean((await db.query('SELECT 1 FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1', [groupId,userId])).rows[0]);
  };

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

  app.post('/api/groups/:groupId/members', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      if (!validId(groupId)) return res.status(400).json({ error: 'invalid group id' });
      const actor = await member(groupId, req.user.sub);
      if (!actor) return res.status(403).json({ error: 'group membership required' });
      const settings = (await pool.query('SELECT add_members, approve_new_members FROM fynx_groups WHERE id=$1 LIMIT 1', [groupId])).rows[0];
      if (!canManageMembers(actor.role) && !settings?.add_members) {
        return res.status(403).json({ error: 'group member additions are disabled', code: 'GROUP_ADD_MEMBERS_DISABLED' });
      }
      if (!canManageMembers(actor.role) && settings?.approve_new_members) {
        return res.status(403).json({ error: 'new members require admin approval', code: 'GROUP_APPROVAL_REQUIRED' });
      }
      const username = String(req.body?.username || '').trim().replace(/^@+/, '').slice(0, 32);
      if (!/^[A-Za-z0-9_]{2,32}$/.test(username)) return res.status(400).json({ error: 'valid username is required' });
      const target = (await pool.query('SELECT id, username FROM users WHERE lower(username)=lower($1) LIMIT 1', [username])).rows[0];
      if (!target) return res.status(404).json({ error: 'FYNX user not found' });
      if (String(target.id) === String(req.user.sub)) return res.status(400).json({ error: 'you are already in this group' });
      const existing = (await pool.query(`SELECT u.username, m.role, m.created_at AS joined_at FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 AND m.user_id=$2 LIMIT 1`, [groupId, target.id])).rows[0];
      if (existing) return res.json({ member: { username: existing.username, role: existing.role, joinedAt: new Date(existing.joined_at).getTime() }, alreadyMember: true });
      const count = Number((await pool.query('SELECT COUNT(*) AS count FROM fynx_group_members WHERE group_id=$1', [groupId])).rows[0]?.count || 0);
      if (count >= 500) return res.status(409).json({ error: 'group member limit reached' });
      const inserted = await insertMemberIfCapacity(pool, groupId, target.id);
      if (!inserted) return res.status(409).json({ error: 'group member limit reached', code: 'GROUP_MEMBER_LIMIT_REACHED' });
      const added = (await pool.query(`SELECT u.username, m.role, m.created_at AS joined_at FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 AND m.user_id=$2 LIMIT 1`, [groupId, target.id])).rows[0];
      return res.status(201).json({ member: { username: added.username, role: added.role, joinedAt: new Date(added.joined_at).getTime() }, alreadyMember: false });
    } catch (error) {
      console.error('group member add', error);
      return res.status(500).json({ error: 'group member could not be added' });
    }
  });

  app.get('/api/groups/:groupId/permissions', auth, async (req, res) => {
    try {
      await ensureSchema();
      if (!(await member(String(req.params?.groupId || ''), req.user.sub))) return res.status(403).json({ error: 'group membership required' });
      const row = (await pool.query(`SELECT send_messages,send_media,add_members,invite_links,approve_new_members,disappearing_seconds,show_history_to_new_members FROM fynx_groups WHERE id=$1 LIMIT 1`, [String(req.params?.groupId || '')])).rows[0];
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
      const current = (await pool.query(`SELECT send_messages,send_media,add_members,invite_links,approve_new_members,disappearing_seconds,show_history_to_new_members FROM fynx_groups WHERE id=$1 LIMIT 1`, [groupId])).rows[0];
      if (!current) return res.status(404).json({ error: 'group not found' });
      const next = {
        send_messages: bool(req.body?.sendMessages, current.send_messages),
        send_media: bool(req.body?.sendMedia, current.send_media),
        add_members: bool(req.body?.addMembers, current.add_members),
        invite_links: bool(req.body?.inviteLinks, current.invite_links),
        approve_new_members: bool(req.body?.approveNewMembers, current.approve_new_members),
        disappearing_seconds: int(req.body?.disappearingSeconds, current.disappearing_seconds),
        show_history_to_new_members: bool(req.body?.showHistoryToNewMembers, current.show_history_to_new_members)
      };
      const row = (await pool.query(`UPDATE fynx_groups SET send_messages=$1,send_media=$2,add_members=$3,invite_links=$4,approve_new_members=$5,disappearing_seconds=$6,show_history_to_new_members=$7,updated_at=NOW() WHERE id=$8 RETURNING send_messages,send_media,add_members,invite_links,approve_new_members,disappearing_seconds,show_history_to_new_members`, [next.send_messages,next.send_media,next.add_members,next.invite_links,next.approve_new_members,next.disappearing_seconds,next.show_history_to_new_members,groupId])).rows[0];
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
      const revoked = await pool.query('UPDATE fynx_group_invites SET revoked_at=NOW() WHERE group_id=$1 AND token=$2 AND revoked_at IS NULL RETURNING token', [groupId,String(req.params?.token || '')]);
      if (!revoked.rowCount) {
        const existing = (await pool.query('SELECT revoked_at FROM fynx_group_invites WHERE group_id=$1 AND token=$2 LIMIT 1', [groupId,String(req.params?.token || '')])).rows[0];
        if (!existing) return res.status(404).json({ error: 'group invite not found' });
        return res.json({ revoked: true, alreadyRevoked: true });
      }
      return res.json({ revoked: true, alreadyRevoked: false });
    } catch (error) {
      console.error('group invite revoke', error);
      return res.status(500).json({ error: 'group invite revoke failed' });
    }
  });

  app.get('/api/groups/:groupId/invites/:token', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const token = String(req.params?.token || '');
      const invite = (await pool.query('SELECT i.group_id,i.token,i.revoked_at,g.name,g.description,g.approve_new_members,g.invite_links FROM fynx_group_invites i JOIN fynx_groups g ON g.id=i.group_id WHERE i.group_id=$1 AND i.token=$2 LIMIT 1', [groupId, token])).rows[0];
      if (!invite || invite.revoked_at || !invite.invite_links) return res.status(404).json({ error: 'invite is invalid, revoked, or disabled' });
      const memberCount = Number((await pool.query('SELECT COUNT(*) AS count FROM fynx_group_members WHERE group_id=$1', [groupId])).rows[0]?.count || 0);
      const alreadyMember = Boolean(await member(groupId, req.user.sub));
      const pending = !alreadyMember && Boolean((await pool.query('SELECT 1 FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2 LIMIT 1', [groupId, req.user.sub])).rows[0]);
      res.set('Cache-Control', 'private, no-store');
      return res.json({ invite: { groupId, token, name: invite.name, description: invite.description || '', memberCount, approveNewMembers: Boolean(invite.approve_new_members), alreadyMember, pending } });
    } catch (error) {
      console.error('group invite preview', error);
      return res.status(500).json({ error: 'group invite preview failed' });
    }
  });

  app.post('/api/groups/:groupId/invites/:token/request', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const token = String(req.params?.token || '');
      const invite = (await pool.query('SELECT i.group_id,i.revoked_at,g.approve_new_members,g.invite_links FROM fynx_group_invites i JOIN fynx_groups g ON g.id=i.group_id WHERE i.group_id=$1 AND i.token=$2 LIMIT 1', [groupId, token])).rows[0];
      if (!invite || invite.revoked_at || !invite.invite_links) return res.status(404).json({ error: 'invite is invalid, revoked, or disabled' });
      if (await member(groupId, req.user.sub)) return res.json({ requested: false, alreadyMember: true, pending: false });
      if (!invite.approve_new_members) return res.status(409).json({ error: 'admin approval is not required for this group' });
      await pool.query('INSERT INTO fynx_group_join_requests(group_id,user_id) VALUES($1,$2) ON CONFLICT(group_id,user_id) DO NOTHING', [groupId, req.user.sub]);
      return res.status(202).json({ requested: true, pending: true });
    } catch (error) {
      console.error('group invite join request', error);
      return res.status(500).json({ error: 'group invite join request failed' });
    }
  });

  app.get('/api/groups/:groupId/join-requests', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!isAdmin(actor?.role)) return res.status(403).json({ error: 'group admin permission required' });
      const rows = (await pool.query('SELECT u.username,r.created_at FROM fynx_group_join_requests r JOIN users u ON u.id=r.user_id WHERE r.group_id=$1 ORDER BY r.created_at ASC,lower(u.username)', [groupId])).rows;
      return res.json({ requests: rows.map(row => ({ username: row.username, requestedAt: new Date(row.created_at).getTime() })) });
    } catch (error) {
      console.error('group join requests lookup', error);
      return res.status(500).json({ error: 'group join requests lookup failed' });
    }
  });

  app.post('/api/groups/:groupId/join-requests/:username/approve', auth, async (req, res) => {
    const client = await pool.connect();
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      await client.query('BEGIN');
      const group = (await client.query('SELECT id FROM fynx_groups WHERE id=$1 FOR UPDATE', [groupId])).rows[0];
      if (!group) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'group not found' });
      }
      const actor = (await client.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 FOR UPDATE', [groupId, req.user.sub])).rows[0];
      if (!isAdmin(actor?.role)) {
        await client.query('ROLLBACK');
        return res.status(403).json({ error: 'group admin permission required' });
      }
      const requested = (await client.query('SELECT r.user_id,u.username FROM fynx_group_join_requests r JOIN users u ON u.id=r.user_id WHERE r.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1', [groupId, cleanUsername(req.params?.username)])).rows[0];
      if (!requested) { await client.query('ROLLBACK'); return res.status(404).json({ error: 'join request not found' }); }
      const inserted = await insertMemberIfCapacity(client, groupId, requested.user_id);
      if (!inserted) { await client.query('ROLLBACK'); return res.status(409).json({ error: 'group member limit reached', code: 'GROUP_MEMBER_LIMIT_REACHED' }); }
      await client.query('DELETE FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2', [groupId, requested.user_id]);
      await client.query('COMMIT');
      return res.json({ approved: true, username: requested.username });
    } catch (error) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('group join request approval', error);
      return res.status(500).json({ error: 'group join request approval failed' });
    } finally {
      client.release();
    }
  });

  app.post('/api/groups/:groupId/join-requests/:username/reject', auth, async (req, res) => {
    let client;
    try {
      await ensureSchema();
      client = await pool.connect();
      const groupId = String(req.params?.groupId || '');
      await client.query('BEGIN');
      const group = (await client.query('SELECT id FROM fynx_groups WHERE id=$1 FOR UPDATE', [groupId])).rows[0];
      if (!group) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'group not found' });
      }
      const actor = (await client.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 FOR UPDATE', [groupId, req.user.sub])).rows[0];
      if (!isAdmin(actor?.role)) {
        await client.query('ROLLBACK');
        return res.status(403).json({ error: 'group admin permission required' });
      }
      const requested = (await client.query('SELECT r.user_id,u.username FROM fynx_group_join_requests r JOIN users u ON u.id=r.user_id WHERE r.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1 FOR UPDATE OF r', [groupId, cleanUsername(req.params?.username)])).rows[0];
      if (!requested) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'join request not found' });
      }
      await client.query('DELETE FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2', [groupId, requested.user_id]);
      await client.query('COMMIT');
      return res.json({ rejected: true, username: requested.username });
    } catch (error) {
      if (client) await client.query('ROLLBACK').catch(() => {});
      console.error('group join request rejection', error);
      return res.status(500).json({ error: 'group join request rejection failed' });
    } finally {
      client?.release();
    }
  });

  app.post('/api/groups/:groupId/invites/:token/join', auth, async (req, res) => {
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const invite = (await pool.query(`SELECT i.group_id,i.revoked_at,g.approve_new_members,g.name,g.invite_links FROM fynx_group_invites i JOIN fynx_groups g ON g.id=i.group_id WHERE i.group_id=$1 AND i.token=$2 LIMIT 1`, [groupId,String(req.params?.token || '')])).rows[0];
      if (!invite || invite.revoked_at || !invite.invite_links) return res.status(404).json({ error: 'invite is invalid, revoked, or disabled' });
      if (await member(groupId, req.user.sub)) return res.json({ joined: true, alreadyMember: true, pending: false });
      if (invite.approve_new_members) {
        await pool.query('INSERT INTO fynx_group_join_requests(group_id,user_id) VALUES($1,$2) ON CONFLICT(group_id,user_id) DO NOTHING', [groupId, req.user.sub]);
        return res.status(202).json({ joined: false, pending: true, message: 'Join request submitted for admin approval.' });
      }
      const inserted = await insertMemberIfCapacity(pool, groupId, req.user.sub);
      if (!inserted) return res.status(409).json({ error: 'group member limit reached', code: 'GROUP_MEMBER_LIMIT_REACHED' });
      await pool.query('DELETE FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2', [groupId,req.user.sub]);
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
    let client;
    try {
      await ensureSchema();
      const groupId = String(req.params?.groupId || '');
      const username = cleanUsername(req.body?.username);
      if (!username) return res.status(400).json({ error: 'target username is required' });
      client = await pool.connect();
      await client.query('BEGIN');
      const group = (await client.query('SELECT owner_id FROM fynx_groups WHERE id=$1 FOR UPDATE', [groupId])).rows[0];
      if (!group) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'group not found' });
      }
      const actor = (await client.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 FOR UPDATE', [groupId, req.user.sub])).rows[0];
      if (!isAdmin(actor?.role) || String(group.owner_id) !== String(req.user.sub)) {
        await client.query('ROLLBACK');
        return res.status(403).json({ error: 'group owner permission required' });
      }
      const targetUser = (await client.query(`SELECT m.user_id,u.username FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1 FOR UPDATE OF m`, [groupId, username])).rows[0];
      if (!targetUser) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'target group member not found' });
      }
      if (String(targetUser.user_id) === String(req.user.sub)) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: 'target must be another member' });
      }
      await client.query(`UPDATE fynx_groups SET owner_id=$1,updated_at=NOW() WHERE id=$2`, [targetUser.user_id,groupId]);
      await client.query(`UPDATE fynx_group_members SET role='MODERATOR' WHERE group_id=$1 AND user_id=$2`, [groupId,req.user.sub]);
      await client.query(`UPDATE fynx_group_members SET role='ADMIN' WHERE group_id=$1 AND user_id=$2`, [groupId,targetUser.user_id]);
      await client.query('COMMIT');
      client.release();
      client = undefined;
      return res.json({ transferred: true, ownerUsername: targetUser.username });
    } catch (error) {
      if (client) {
        await client.query('ROLLBACK').catch(() => {});
        client.release();
      }
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
      const hasTargetUsername = typeof req.body?.username === 'string' && req.body.username.trim().length > 0;
      const targetUser = hasTargetUsername ? await target(groupId, req.body.username) : null;
      if (hasTargetUsername && !targetUser) return res.status(404).json({ error: 'reported group member not found' });
      const id = randomUUID();
      await pool.query('INSERT INTO fynx_group_reports(id,group_id,reporter_id,target_user_id,reason) VALUES($1,$2,$3,$4,$5)', [id,groupId,req.user.sub,targetUser?.user_id||null,reason]);
      return res.status(201).json({ reportId:id, submitted:true });
    } catch (error) {
      console.error('group report', error);
      return res.status(500).json({ error: 'group report submission failed' });
    }
  });

  app.post('/api/groups/:groupId/leave', auth, async (req, res) => {
    let client;
    try {
      await ensureSchema();
      client = await pool.connect();
      const groupId = String(req.params?.groupId || '');
      await client.query('BEGIN');
      const group = (await client.query('SELECT owner_id FROM fynx_groups WHERE id=$1 FOR UPDATE', [groupId])).rows[0];
      if (!group) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'group not found' });
      }
      const current = (await client.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 FOR UPDATE', [groupId, req.user.sub])).rows[0];
      if (!current) {
        await client.query('ROLLBACK');
        return res.status(403).json({ error: 'group membership required' });
      }
      if (String(group.owner_id) === String(req.user.sub)) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'group owner must transfer ownership before leaving' });
      }
      await client.query('DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id=$2', [groupId, req.user.sub]);
      await client.query('COMMIT');
      return res.json({ left: true });
    } catch (error) {
      if (client) await client.query('ROLLBACK').catch(() => {});
      console.error('group leave', error);
      return res.status(500).json({ error: 'group leave failed' });
    } finally {
      client?.release();
    }
  });

  app.delete('/api/groups/:groupId/members/:username', auth, async (req, res) => {
    let client;
    try {
      await ensureSchema();
      client = await pool.connect();
      const groupId = String(req.params?.groupId || '');
      await client.query('BEGIN');
      const group = (await client.query('SELECT owner_id FROM fynx_groups WHERE id=$1 FOR UPDATE', [groupId])).rows[0];
      if (!group) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'group not found' });
      }
      const actor = (await client.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 FOR UPDATE', [groupId, req.user.sub])).rows[0];
      if (!actor || !isManager(actor.role)) {
        await client.query('ROLLBACK');
        return res.status(403).json({ error: 'group management permission required' });
      }
      const victim = (await client.query('SELECT m.user_id,m.role,u.username FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1 FOR UPDATE OF m', [groupId, String(req.params?.username || '').trim().replace(/^@+/, '')])).rows[0];
      if (!victim) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'member not found' });
      }
      if (String(victim.user_id) === String(req.user.sub)) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: 'use leave endpoint to leave the group' });
      }
      if (String(group.owner_id) === String(victim.user_id)) {
        await client.query('ROLLBACK');
        return res.status(403).json({ error: 'group owner cannot be removed' });
      }
      if (String(actor.role) === 'MODERATOR' && String(victim.role) !== 'MEMBER') {
        await client.query('ROLLBACK');
        return res.status(403).json({ error: 'moderators can only remove members' });
      }
      await client.query('DELETE FROM fynx_group_members WHERE group_id=$1 AND user_id=$2', [groupId, victim.user_id]);
      await client.query('COMMIT');
      return res.json({ removed: true, username: victim.username });
    } catch (error) {
      if (client) await client.query('ROLLBACK').catch(() => {});
      console.error('group member removal', error);
      return res.status(500).json({ error: 'group member removal failed' });
    } finally {
      client?.release();
    }
  });

}
