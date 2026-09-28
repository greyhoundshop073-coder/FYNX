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

  app.get('/api/statuses/archive', auth, async (req, res) => {
    try {
      const result = await pool.query(`
        SELECT s.id,s.owner_id,u.username,u.display_name,s.type,s.text,s.media_id,
               s.background_color,s.foreground_color,s.font,s.alignment,s.private_status,s.audience,
               s.voice_duration_ms,s.music_catalogue_id,s.music_title,s.music_artist,s.music_duration_ms,
               EXTRACT(EPOCH FROM s.created_at)*1000 AS created_at,
               EXTRACT(EPOCH FROM s.expires_at)*1000 AS expires_at
        FROM statuses s
        JOIN users u ON u.id=s.owner_id
        WHERE s.owner_id=$1 AND s.expires_at <= NOW()
        ORDER BY s.created_at DESC
        LIMIT 200
      `, [req.user.sub]);
      return res.json({
        statuses: result.rows.map(row => ({
          id:String(row.id), ownerUsername:row.username, ownerDisplayName:row.display_name,
          type:row.type, text:row.text || null,
          mediaId:row.media_id == null ? null : String(row.media_id),
          mediaUrl:row.media_id == null ? null : \`/api/media/\${row.media_id}\`,
          backgroundColor:Number(row.background_color), foregroundColor:Number(row.foreground_color),
          font:row.font, alignment:Number(row.alignment), privateStatus:Boolean(row.private_status),
          audience:row.audience || (row.private_status ? 'FRIENDS' : 'EVERYONE'),
          voiceDurationMs:Number(row.voice_duration_ms), musicCatalogueId:row.music_catalogue_id == null ? null : Number(row.music_catalogue_id),
          musicTitle:row.music_title || null, musicArtist:row.music_artist || null,
          musicDurationMs:Number(row.music_duration_ms || 0), createdAtMillis:Number(row.created_at),
          expiresAtMillis:Number(row.expires_at)
        }))
      });
    } catch (error) {
      console.error('status archive', error);
      return res.status(500).json({ error: 'status archive unavailable' });
    }
  });

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
