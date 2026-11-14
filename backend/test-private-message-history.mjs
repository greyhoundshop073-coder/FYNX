import pg from "pg";
const { Pool } = pg;
const pool = new Pool({
  host: process.env.PGHOST || "localhost",
  port: Number(process.env.PGPORT || 5432),
  user: process.env.PGUSER || "fynx_test",
  password: process.env.PGPASSWORD || "fynx_test",
  database: process.env.PGDATABASE || "fynx_media_test",
  max: 1
});
const assert = (condition, message) => { if (!condition) throw new Error(message); };

async function run() {
  const client = await pool.connect();
  try {
    await client.query(`CREATE TEMP TABLE private_message_history_fixture(
      id BIGINT PRIMARY KEY, sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
      text TEXT NOT NULL DEFAULT '', deleted BOOLEAN NOT NULL DEFAULT FALSE
    ) ON COMMIT PRESERVE ROWS`);
    const fixtures = [
      [101, 1, 2, "Delivery update one", false],
      [102, 2, 1, "Delivery update two", false],
      [103, 1, 2, "Delivery update three", false],
      [104, 1, 3, "Delivery from another conversation", false],
      [105, 1, 2, "Delivery deleted from conversation", true],
      [106, 1, 2, "Different topic", false]
    ];
    for (const row of fixtures) {
      await client.query(
        "INSERT INTO private_message_history_fixture(id,sender_id,recipient_id,text,deleted) VALUES($1,$2,$3,$4,$5)",
        row
      );
    }
    async function searchPage({ userId, otherId, query, beforeId = null, limit = 2 }) {
      const result = await client.query(`
        SELECT id,text FROM private_message_history_fixture m
        WHERE ((m.sender_id=$1 AND m.recipient_id=$2) OR (m.sender_id=$2 AND m.recipient_id=$1))
          AND m.deleted=FALSE
          AND strpos(lower(COALESCE(m.text,'')),lower($3))>0
          AND ($4::bigint IS NULL OR m.id<$4::bigint)
        ORDER BY m.id DESC LIMIT $5
      `, [userId, otherId, query, beforeId, limit + 1]);
      const hasMore = result.rows.length > limit;
      const messages = result.rows.slice(0, limit).reverse();
      return { messages, hasMore, nextBeforeId: messages.length ? String(messages[0].id) : null };
    }
    const first = await searchPage({ userId: 1, otherId: 2, query: "delivery" });
    assert(first.messages.map(row => String(row.id)).join(",") === "102,103",
      "first page must order the newest page oldest-to-newest");
    assert(first.hasMore && first.nextBeforeId === "102", "first page must expose its oldest-result cursor");
    const second = await searchPage({ userId: 1, otherId: 2, query: "delivery", beforeId: first.nextBeforeId });
    assert(second.messages.length === 1 && String(second.messages[0].id) === "101",
      "second page must return the remaining older match");
    assert(!second.hasMore && second.nextBeforeId === "101", "last page must close the cursor");
    const hostile = await searchPage({ userId: 1, otherId: 2, query: "' OR 1=1 --", limit: 10 });
    assert(hostile.messages.length === 0, "SQL-shaped input must remain a bound text value");
    const noMatch = await searchPage({ userId: 1, otherId: 2, query: "no such phrase", limit: 10 });
    assert(noMatch.messages.length === 0 && !noMatch.hasMore, "no-match query must return an empty terminal page");
    console.log("PRIVATE MESSAGE HISTORY SEARCH + CURSOR + PARTICIPANT ISOLATION + DELETED-MESSAGE FILTER: GREEN");
  } finally {
    client.release();
  }
}
run().catch(error => {
  console.error("PRIVATE MESSAGE HISTORY TEST FAILED:", error);
  process.exitCode = 1;
}).finally(async () => { await pool.end(); });
