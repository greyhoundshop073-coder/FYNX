import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import pg from "pg";

const { Client } = pg;
assert.ok(process.env.PGDATABASE, "CI PostgreSQL connection environment is required");

const source = await readFile(new URL("./marketplaceAdvertising.js", import.meta.url), "utf8");
const migrationStart = source.indexOf("async function ensureAdvertisingSchema()");
const queryStart = source.indexOf("await pool.query(`", migrationStart);
const sqlStart = queryStart + "await pool.query(`".length;
const sqlEnd = source.indexOf("`);", sqlStart);
assert.ok(migrationStart >= 0 && queryStart > migrationStart && sqlEnd > sqlStart, "advertising schema migration extraction failed");
const migration = source.slice(sqlStart, sqlEnd);
assert.ok(migration.includes("DO " + "$".repeat(2)) && migration.includes("END " + "$".repeat(2) + ";"), "typed campaign constraint migration must use a valid PostgreSQL dollar-quoted block");

const db = new Client();
const schema = `fynx_ads_migration_test_${process.pid}_${Date.now()}`;
await db.connect();
try {
  await db.query(`CREATE SCHEMA "${schema}"`);
  await db.query(`SET search_path TO "${schema}"`);
  await db.query("CREATE TABLE users (id BIGINT PRIMARY KEY)");
  // Simulate a database created by the pre-campaign seasonal-promotion schema.
  await db.query(`
    CREATE TABLE marketplace_seasonal_promotions (
      id BIGSERIAL PRIMARY KEY,
      occasion_key TEXT NOT NULL,
      headline TEXT NOT NULL,
      body TEXT NOT NULL DEFAULT '',
      action_label TEXT NOT NULL DEFAULT 'Explore Marketplace',
      starts_at TIMESTAMPTZ NOT NULL,
      ends_at TIMESTAMPTZ NOT NULL,
      active BOOLEAN NOT NULL DEFAULT TRUE,
      created_by BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      CHECK (ends_at > starts_at)
    )
  `);
  await db.query(migration);
  // Running the same production migration again must be safe.
  await db.query(migration);

  const column = await db.query(`
    SELECT column_default, is_nullable
    FROM information_schema.columns
    WHERE table_schema = $1
      AND table_name = 'marketplace_seasonal_promotions'
      AND column_name = 'promotion_type'
  `, [schema]);
  assert.equal(column.rowCount, 1, "legacy seasonal table must gain promotion_type");
  assert.match(column.rows[0].column_default, /SEASONAL/, "legacy rows must default to the SEASONAL content type");
  assert.equal(column.rows[0].is_nullable, "NO", "campaign type must be non-null");

  const constraint = await db.query(`
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'marketplace_seasonal_promotions_type_check'
      AND conrelid = 'marketplace_seasonal_promotions'::regclass
      AND contype = 'c'
  `);
  assert.equal(constraint.rowCount, 1, "campaign type check constraint must exist on the migrated table");

  await db.query("INSERT INTO users(id) VALUES(1)");
  await db.query(
    "INSERT INTO marketplace_seasonal_promotions (occasion_key,headline,starts_at,ends_at,created_by) VALUES ('legacy','Legacy campaign',NOW(),NOW()+INTERVAL '1 day',1)"
  );
  const legacy = await db.query("SELECT promotion_type FROM marketplace_seasonal_promotions WHERE occasion_key='legacy'");
  assert.equal(legacy.rows[0].promotion_type, "SEASONAL", "legacy campaign rows must remain seasonal after migration");

  await db.query(
    "INSERT INTO marketplace_seasonal_promotions (promotion_type,occasion_key,headline,starts_at,ends_at,created_by) VALUES ('ANNOUNCEMENT','notice','FYNX notice',NOW(),NOW()+INTERVAL '1 day',1)"
  );
  await assert.rejects(
    db.query(
      "INSERT INTO marketplace_seasonal_promotions (promotion_type,occasion_key,headline,starts_at,ends_at,created_by) VALUES ('UNSUPPORTED','bad','Invalid campaign',NOW(),NOW()+INTERVAL '1 day',1)"
    ),
    (error) => error?.code === "23514",
    "unsupported campaign types must be rejected by PostgreSQL"
  );
  console.log("PostgreSQL Marketplace advertising schema migration passed (legacy upgrade, repeat migration, default, and type-constraint cases).");
} finally {
  await db.query("SET search_path TO public").catch(() => {});
  await db.query(`DROP SCHEMA IF EXISTS "${schema}" CASCADE`).catch(() => {});
  await db.end();
}
