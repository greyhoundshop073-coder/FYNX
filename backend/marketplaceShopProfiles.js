const schemaPromises = new WeakMap();

/** Shared, idempotent schema readiness for persistent Marketplace shop identity. */
export function ensureMarketplaceShopProfileSchema(pool) {
  if (!pool || (typeof pool !== "object" && typeof pool !== "function")) {
    return Promise.reject(new Error("Marketplace database pool is unavailable"));
  }
  let pending = schemaPromises.get(pool);
  if (!pending) {
    pending = pool.query(`
      CREATE TABLE IF NOT EXISTS marketplace_shop_profiles (
        seller_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
        shop_name TEXT NOT NULL DEFAULT '',
        description TEXT NOT NULL DEFAULT '',
        location TEXT NOT NULL DEFAULT '',
        contact_phone TEXT NOT NULL DEFAULT '',
        logo_media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL,
        cover_media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        CHECK (length(shop_name) <= 120),
        CHECK (length(description) <= 800),
        CHECK (length(location) <= 160),
        CHECK (length(contact_phone) <= 40)
      );
      CREATE INDEX IF NOT EXISTS marketplace_shop_profiles_updated_idx
        ON marketplace_shop_profiles (updated_at DESC);
    `).then(() => pool.query(`
      ALTER TABLE marketplace_shop_profiles ADD COLUMN IF NOT EXISTS logo_media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL;
      ALTER TABLE marketplace_shop_profiles ADD COLUMN IF NOT EXISTS cover_media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL;
    `)).catch((error) => {
      schemaPromises.delete(pool);
      throw error;
    });
    schemaPromises.set(pool, pending);
  }
  return pending;
}

export function registerMarketplaceShopProfileRoutes({ app, pool, auth }) {
  const ensureSchema = () => ensureMarketplaceShopProfileSchema(pool);

  const publicProfile = (row) => ({
    username: row.username,
    displayName: row.display_name || '',
    shopName: row.shop_name || '',
    description: row.description || '',
    location: row.location || '',
    contactPhone: row.contact_phone || '',
    logoMediaId: row.logo_media_id == null ? null : String(row.logo_media_id),
    coverMediaId: row.cover_media_id == null ? null : String(row.cover_media_id),
    updatedAt: row.updated_at || null
  });

  app.get('/api/marketplace/my-shop', auth, async (req, res) => {
    try {
      await ensureSchema();
      const result = await pool.query(`
        SELECT u.username, u.display_name, p.shop_name, p.description, p.location,
               p.contact_phone, p.logo_media_id, p.cover_media_id, p.updated_at
        FROM users u
        LEFT JOIN marketplace_shop_profiles p ON p.seller_id = u.id
        WHERE u.id = $1
        LIMIT 1
      `, [req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: 'seller account not found' });
      return res.json({ shop: publicProfile(result.rows[0]) });
    } catch (error) {
      console.error('marketplace shop profile lookup', error);
      return res.status(500).json({ error: 'shop profile could not be loaded' });
    }
  });

  app.patch('/api/marketplace/my-shop', auth, async (req, res) => {
    const shopName = typeof req.body?.shopName === 'string' ? req.body.shopName.trim().slice(0, 120) : '';
    const description = typeof req.body?.description === 'string' ? req.body.description.trim().slice(0, 800) : '';
    const location = typeof req.body?.location === 'string' ? req.body.location.trim().slice(0, 160) : '';
    const contactPhone = typeof req.body?.contactPhone === 'string' ? req.body.contactPhone.trim().slice(0, 40) : '';
    const logoMediaId = req.body?.logoMediaId == null || req.body.logoMediaId === '' ? null : String(req.body.logoMediaId).trim();
    const coverMediaId = req.body?.coverMediaId == null || req.body.coverMediaId === '' ? null : String(req.body.coverMediaId).trim();
    const mediaIds = [logoMediaId, coverMediaId].filter(Boolean);
    if (mediaIds.some((id) => !/^[1-9][0-9]{0,18}$/.test(id))) return res.status(400).json({ error: 'invalid shop image reference' });
    if (shopName.length < 2) return res.status(400).json({ error: 'shop name must contain at least 2 characters' });

    try {
      await ensureSchema();
      if (mediaIds.length) {
        const owned = await pool.query('SELECT id, mime_type FROM message_media WHERE id = ANY($1::bigint[]) AND owner_id = $2', [mediaIds, req.user.sub]);
        if (owned.rows.length !== mediaIds.length || owned.rows.some((row) => !String(row.mime_type || '').toLowerCase().startsWith('image/'))) {
          return res.status(400).json({ error: 'shop logo and cover must be images uploaded by this account' });
        }
      }
      const result = await pool.query(`
        INSERT INTO marketplace_shop_profiles (seller_id, shop_name, description, location, contact_phone, logo_media_id, cover_media_id)
        VALUES ($1, $2, $3, $4, $5, $6, $7)
        ON CONFLICT (seller_id) DO UPDATE SET
          shop_name = EXCLUDED.shop_name,
          description = EXCLUDED.description,
          location = EXCLUDED.location,
          contact_phone = EXCLUDED.contact_phone,
          logo_media_id = EXCLUDED.logo_media_id,
          cover_media_id = EXCLUDED.cover_media_id,
          updated_at = NOW()
        RETURNING shop_name, description, location, contact_phone, logo_media_id, cover_media_id, updated_at
      `, [req.user.sub, shopName, description, location, contactPhone, logoMediaId, coverMediaId]);
      const user = await pool.query('SELECT username, display_name FROM users WHERE id=$1 LIMIT 1', [req.user.sub]);
      if (!user.rows[0]) return res.status(404).json({ error: 'seller account not found' });
      return res.json({ shop: publicProfile({ ...user.rows[0], ...result.rows[0] }) });
    } catch (error) {
      console.error('marketplace shop profile save', error);
      return res.status(500).json({ error: 'shop profile could not be saved' });
    }
  });

  app.get('/api/marketplace/shops/:username', auth, async (req, res) => {
    const username = typeof req.params?.username === 'string' ? req.params.username.trim().replace(/^@/, '') : '';
    if (!/^[a-zA-Z0-9_.-]{1,40}$/.test(username)) return res.status(400).json({ error: 'invalid seller username' });
    try {
      await ensureSchema();
      const result = await pool.query(`
        SELECT u.username, u.display_name, p.shop_name, p.description, p.location,
               p.contact_phone, p.logo_media_id, p.cover_media_id, p.updated_at
        FROM users u
        LEFT JOIN marketplace_shop_profiles p ON p.seller_id = u.id
        WHERE lower(u.username) = lower($1)
          AND NOT EXISTS (
            SELECT 1 FROM blocks b
            WHERE (b.blocker_id = $2 AND b.blocked_id = u.id)
               OR (b.blocker_id = u.id AND b.blocked_id = $2)
          )
        LIMIT 1
      `, [username, req.user.sub]);
      if (!result.rows[0]) return res.status(404).json({ error: 'seller not found' });
      return res.json({ shop: publicProfile(result.rows[0]) });
    } catch (error) {
      console.error('marketplace public shop profile', error);
      return res.status(500).json({ error: 'public shop profile could not be loaded' });
    }
  });
}
