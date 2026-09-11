import pg from 'pg';
import jwt from 'jsonwebtoken';

const { Pool } = pg;
const DATABASE_URL = process.env.DATABASE_URL || '';
const JWT_SECRET = process.env.JWT_SECRET || '';
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? { rejectUnauthorized: false } : false,
  max: 2,
  min: 0,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  keepAlive: true
}) : null;

export function registerStatusManagementRoutes({ app }) {
  if (!pool) return;
  const auth = (req, res, next) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
    if (!token || !JWT_SECRET) return res.status(401).json({ error: 'authentication required' });
    try { req.user = jwt.verify(token, JWT_SECRET); return next(); }
    catch { return res.status(401).json({ error: 'invalid or expired token' }); }
  };

  app.delete('/api/statuses/:statusId', auth, async (req, res) => {
    try {
      const statusId = String(req.params?.statusId || '').trim();
      if (!/^[0-9a-f-]{36}$/i.test(statusId)) return res.status(400).json({ error: 'invalid status id' });
      const result = await pool.query(
        'DELETE FROM statuses WHERE id=$1 AND owner_id=$2 RETURNING id',
        [statusId, req.user.sub]
      );
      if (!result.rows[0]) return res.status(404).json({ error: 'status not found or not owned by this account' });
      return res.json({ deleted: true, id: statusId });
    } catch (error) {
      console.error('status delete', error);
      return res.status(500).json({ error: 'status deletion failed' });
    }
  });
}
