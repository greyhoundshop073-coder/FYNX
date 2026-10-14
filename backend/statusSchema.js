export async function ensureStatusSchema(pool) {
  await pool.query(`CREATE TABLE IF NOT EXISTS statuses (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('TEXT','PHOTO','VIDEO','VOICE')),
    text TEXT NOT NULL DEFAULT '',
    media_id BIGINT REFERENCES message_media(id) ON DELETE SET NULL,
    background_color BIGINT NOT NULL DEFAULT 4279308561,
    foreground_color BIGINT NOT NULL DEFAULT 4294967295,
    font TEXT NOT NULL DEFAULT 'CLASSIC',
    alignment INTEGER NOT NULL DEFAULT 1 CHECK (alignment BETWEEN 0 AND 2),
    private_status BOOLEAN NOT NULL DEFAULT FALSE,
    audience TEXT NOT NULL DEFAULT 'EVERYONE' CHECK (audience IN ('EVERYONE','FRIENDS','ONLY_ME')),
    voice_duration_ms BIGINT NOT NULL DEFAULT 0,
    music_catalogue_id BIGINT,
    music_title TEXT,
    music_artist TEXT,
    music_duration_ms BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
  );
  `);
  await pool.query("ALTER TABLE statuses ADD COLUMN IF NOT EXISTS audience TEXT");
  await pool.query("UPDATE statuses SET audience = CASE WHEN private_status THEN 'FRIENDS' ELSE 'EVERYONE' END WHERE audience IS NULL");
  await pool.query("ALTER TABLE statuses ALTER COLUMN audience SET DEFAULT 'EVERYONE'");
  await pool.query("ALTER TABLE statuses ALTER COLUMN audience SET NOT NULL");
  await pool.query(`DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'statuses_audience_check') THEN
      ALTER TABLE statuses ADD CONSTRAINT statuses_audience_check CHECK (audience IN ('EVERYONE','FRIENDS','ONLY_ME'));
    END IF;
  END $$;`);
  await pool.query("ALTER TABLE statuses ADD COLUMN IF NOT EXISTS music_catalogue_id BIGINT");
  await pool.query("ALTER TABLE statuses ADD COLUMN IF NOT EXISTS music_title TEXT");
  await pool.query("ALTER TABLE statuses ADD COLUMN IF NOT EXISTS music_artist TEXT");
  await pool.query("ALTER TABLE statuses ADD COLUMN IF NOT EXISTS music_duration_ms BIGINT NOT NULL DEFAULT 0");
  await pool.query("CREATE INDEX IF NOT EXISTS statuses_owner_idx ON statuses(owner_id,created_at DESC)");
  await pool.query("CREATE INDEX IF NOT EXISTS statuses_expiry_idx ON statuses(expires_at)");
}
