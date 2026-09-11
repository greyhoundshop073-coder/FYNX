import pg from 'pg';
import jwt from 'jsonwebtoken';

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
  const member = async (groupId, userId) => (await pool.query('SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2 LIMIT 1', [groupId, userId])).rows[0] || null;
  const target = async (groupId, username) => (await pool.query(`SELECT m.user_id,m.role,u.username FROM fynx_group_members m JOIN users u ON u.id=m.user_id WHERE m.group_id=$1 AND lower(u.username)=lower($2) LIMIT 1`, [groupId, String(username || '').trim().replace(/^@+/, '')])).rows[0] || null;

  app.post('/api/groups/:groupId/leave', auth, async (req, res) => {
    try {
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
      const groupId = String(req.params?.groupId || '');
      const actor = await member(groupId, req.user.sub);
      if (!actor || !['ADMIN', 'MODERATOR'].includes(String(actor.role))) return res.status(403).json({ error: 'group management permission required' });
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
