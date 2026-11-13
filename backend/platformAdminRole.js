export function createPlatformAdminRoleResolver(pool) {
  let schemaPromise;
  const ensureOwnerSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = (async () => {
        await pool.query(`CREATE TABLE IF NOT EXISTS fynx_platform_owner (
          singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK(singleton=TRUE),
          owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )`);
        await pool.query(`
          WITH lock AS (SELECT pg_advisory_xact_lock(hashtext('fynx_platform_owner_bootstrap'))),
          candidate AS (SELECT id FROM users ORDER BY id ASC LIMIT 1)
          INSERT INTO fynx_platform_owner(singleton, owner_user_id)
          SELECT TRUE, candidate.id FROM candidate, lock ON CONFLICT(singleton) DO NOTHING
        `);
      })().catch(error => { schemaPromise = undefined; throw error; });
    }
    return schemaPromise;
  };
  return async function resolvePlatformAdminRole(userId) {
    if (!pool) return null;
    const id = String(userId ?? "").trim();
    // Keep PostgreSQL BIGINT identities as strings; JavaScript Number is not exact for all BIGINTs.
    if (!/^[1-9]\d*$/.test(id)) return null;
    await ensureOwnerSchema();
    const owner = await pool.query("SELECT owner_user_id FROM fynx_platform_owner WHERE singleton=TRUE");
    if (owner.rows?.[0] && String(owner.rows[0].owner_user_id) === id) return "OWNER";
    const admin = await pool.query("SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 AND role='ADMIN' LIMIT 1", [id]);
    return admin.rowCount ? "ADMIN" : null;
  };
}
