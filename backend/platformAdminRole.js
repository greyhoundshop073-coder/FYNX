import { isPositivePostgresBigint } from "./postgresIds.js";

export function createPlatformAdminRoleResolver(pool) {
  let schemaPromise;
  const ensureOwnerSchema = async () => {
    if (!schemaPromise) {
      schemaPromise = pool.query(`CREATE TABLE IF NOT EXISTS fynx_platform_owner (
        singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK(singleton=TRUE),
        owner_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      )`).catch(error => {
        schemaPromise = undefined;
        throw error;
      });
    }
    return schemaPromise;
  };

  const bootstrapLegacyOwner = async () => pool.query(`
    WITH lock AS (
      SELECT pg_advisory_xact_lock(hashtext('fynx_platform_owner_bootstrap'))
    ),
    candidate AS (
      SELECT id FROM users ORDER BY id ASC LIMIT 1
    )
    INSERT INTO fynx_platform_owner(singleton, owner_user_id)
    SELECT TRUE, candidate.id FROM candidate, lock
    ON CONFLICT(singleton) DO NOTHING
  `);

  return async function resolvePlatformAdminRole(userId) {
    if (!pool) return null;
    if (!isPositivePostgresBigint(userId)) return null;
    // Preserve valid decimal strings exactly; never round PostgreSQL BIGINT IDs through Number.
    const id = String(userId).trim();

    await ensureOwnerSchema();
    let owner = await pool.query(
      "SELECT owner_user_id FROM fynx_platform_owner WHERE singleton=TRUE"
    );
    // Deployments may start before the first user is registered. Retry the
    // legacy seed on later role checks until a user exists or an Owner is set.
    if (!owner.rows?.[0]) {
      await bootstrapLegacyOwner();
      owner = await pool.query(
        "SELECT owner_user_id FROM fynx_platform_owner WHERE singleton=TRUE"
      );
    }
    if (owner.rows?.[0] && String(owner.rows[0].owner_user_id) === id) return "OWNER";

    const admin = await pool.query(
      "SELECT 1 FROM fynx_admin_roles WHERE user_id=$1 AND role='ADMIN' LIMIT 1",
      [id]
    );
    return admin.rowCount ? "ADMIN" : null;
  };
}
