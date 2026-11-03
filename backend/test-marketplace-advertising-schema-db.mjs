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

  const destinationColumn = await db.query(`
    SELECT column_default, is_nullable
    FROM information_schema.columns
    WHERE table_schema = $1
      AND table_name = 'marketplace_seasonal_promotions'
      AND column_name = 'destination_type'
  `, [schema]);
  assert.equal(destinationColumn.rowCount, 1, "legacy seasonal table must gain destination_type");
  assert.match(destinationColumn.rows[0].column_default, /MARKETPLACE/, "legacy rows must default to the Marketplace destination");
  assert.equal(destinationColumn.rows[0].is_nullable, "NO", "campaign destination type must be non-null");

  const destinationValueColumn = await db.query(`
    SELECT is_nullable
    FROM information_schema.columns
    WHERE table_schema = $1
      AND table_name = 'marketplace_seasonal_promotions'
      AND column_name = 'destination_value'
  `, [schema]);
  assert.equal(destinationValueColumn.rowCount, 1, "legacy seasonal table must gain destination_value");
  assert.equal(destinationValueColumn.rows[0].is_nullable, "YES", "Marketplace-wide campaigns may omit a destination value");

  const destinationConstraint = await db.query(`
    SELECT 1 FROM pg_constraint
    WHERE conname = 'marketplace_seasonal_promotions_destination_type_check'
      AND conrelid = 'marketplace_seasonal_promotions'::regclass
      AND contype = 'c'
  `);
  assert.equal(destinationConstraint.rowCount, 1, "campaign destination type check constraint must exist");

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
  const legacyDestination = await db.query("SELECT destination_type, destination_value FROM marketplace_seasonal_promotions WHERE occasion_key='legacy'");
  assert.equal(legacyDestination.rows[0].destination_type, "MARKETPLACE", "legacy campaigns must default to a Marketplace destination");
  assert.equal(legacyDestination.rows[0].destination_value, null, "legacy Marketplace destinations must not invent a target");

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
  await db.query(
    "INSERT INTO marketplace_seasonal_promotions (promotion_type,destination_type,destination_value,occasion_key,headline,starts_at,ends_at,created_by) VALUES ('ANNOUNCEMENT','CATEGORY','Electronics','category-test','Electronics campaign',NOW(),NOW()+INTERVAL '1 day',1)"
  );
  await assert.rejects(
    db.query(
      "INSERT INTO marketplace_seasonal_promotions (promotion_type,destination_type,occasion_key,headline,starts_at,ends_at,created_by) VALUES ('ANNOUNCEMENT','EXTERNAL','bad-destination','Invalid destination',NOW(),NOW()+INTERVAL '1 day',1)"
    ),
    (error) => error?.code === "23514",
    "unsupported campaign destinations must be rejected by PostgreSQL"
  );
  const cpcColumns = await db.query(`
    SELECT column_name, column_default, is_nullable
    FROM information_schema.columns
    WHERE table_schema=$1 AND table_name='marketplace_ad_campaigns'
      AND column_name IN ('cost_per_click_kobo','daily_spent_kobo','daily_spend_date')
    ORDER BY column_name
  `, [schema]);
  assert.equal(cpcColumns.rowCount, 3, "campaigns must persist CPC and daily-budget accounting fields");
  const cpcDefault = cpcColumns.rows.find(row => row.column_name === "cost_per_click_kobo");
  assert.match(cpcDefault.column_default, /500/, "new campaigns must receive the documented ₦5 pilot CPC default in kobo");
  const clickLedger = await db.query(`
    SELECT 1 FROM information_schema.tables
    WHERE table_schema=$1 AND table_name='marketplace_ad_click_events'
  `, [schema]);
  assert.equal(clickLedger.rowCount, 1, "deduplicated CPC click ledger must be installed");

  await db.query("INSERT INTO users(id) VALUES(2)");
  const campaign = await db.query(`
    INSERT INTO marketplace_ad_campaigns (owner_id,name,creative_type,daily_budget_kobo,total_budget_kobo)
    VALUES (1,'CPC migration test','product',2000,5000)
    RETURNING id,cost_per_click_kobo,daily_spent_kobo
  `);
  assert.equal(Number(campaign.rows[0].cost_per_click_kobo), 500, "CPC is stored in kobo, not trusted from metric calls");
  assert.equal(Number(campaign.rows[0].daily_spent_kobo), 0, "new campaigns start with no daily spend");
  await db.query(
    `INSERT INTO marketplace_ad_click_events (campaign_id,viewer_id,click_day,click_key,charge_kobo)
     VALUES ($1,2,(NOW() AT TIME ZONE 'Africa/Lagos')::date,'click-key-buyer-0001',500)`,
    [campaign.rows[0].id]
  );
  await assert.rejects(
    db.query(
      `INSERT INTO marketplace_ad_click_events (campaign_id,viewer_id,click_day,click_key,charge_kobo)
       VALUES ($1,2,(NOW() AT TIME ZONE 'Africa/Lagos')::date,'click-key-buyer-0002',500)`,
      [campaign.rows[0].id]
    ),
    error => error?.code === "23505",
    "a buyer must not be billed twice for the same campaign on the same Lagos day"
  );
  await assert.rejects(
    db.query(
      `INSERT INTO marketplace_ad_click_events (campaign_id,viewer_id,click_day,click_key,charge_kobo)
       VALUES ($1,1,(NOW() AT TIME ZONE 'Africa/Lagos')::date,'click-key-buyer-0001',500)`,
      [campaign.rows[0].id]
    ),
    error => error?.code === "23505",
    "a retried click idempotency key must not create a second charge"
  );
  await assert.rejects(
    db.query("UPDATE marketplace_ad_campaigns SET cost_per_click_kobo=0 WHERE id=$1", [campaign.rows[0].id]),
    error => error?.code === "23514",
    "database must reject a zero/negative CPC"
  );

  console.log("PostgreSQL Marketplace advertising schema migration passed (legacy upgrade, repeat migration, campaign destinations, CPC fields and click-ledger deduplication).");
} finally {
  await db.query("SET search_path TO public").catch(() => {});
  await db.query(`DROP SCHEMA IF EXISTS "${schema}" CASCADE`).catch(() => {});
  await db.end();
}
