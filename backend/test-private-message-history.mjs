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
    await client.query(`CREATE TEMP TABLE private_message_index_users(
      id BIGINT PRIMARY KEY, username TEXT NOT NULL, display_name TEXT, profile_photo_media_id BIGINT
    ) ON COMMIT PRESERVE ROWS`);
    await client.query(`CREATE TEMP TABLE private_message_index_blocks(
      blocker_id BIGINT NOT NULL, blocked_id BIGINT NOT NULL, PRIMARY KEY(blocker_id,blocked_id)
    ) ON COMMIT PRESERVE ROWS`);
    await client.query(`CREATE TEMP TABLE private_message_index_fixture(
      id BIGINT PRIMARY KEY, sender_id BIGINT NOT NULL, recipient_id BIGINT NOT NULL,
      text TEXT NOT NULL DEFAULT '', media_type TEXT, deleted BOOLEAN NOT NULL DEFAULT FALSE,
      created_at TIMESTAMPTZ NOT NULL, read_at TIMESTAMPTZ
    ) ON COMMIT PRESERVE ROWS`);
    await client.query(
      "INSERT INTO private_message_index_users(id,username,display_name,profile_photo_media_id) VALUES($1,$2,$3,$4),($5,$6,$7,$8),($9,$10,$11,$12),($13,$14,$15,$16)",
      [1,"owner","Owner",null,2,"friend","Friend",22,3,"photo_friend","Photo Friend",33,4,"blocked_friend","Blocked Friend",44]
    );
    await client.query(
      "INSERT INTO private_message_index_blocks(blocker_id,blocked_id) VALUES($1,$2)",
      [1,4]
    );
    const baseTime = Date.now();
    const recentRows = [
      [201,2,1,"Unread one","text",false,new Date(baseTime-5000),null],
      [202,2,1,"Unread two","text",false,new Date(baseTime-4000),null],
      [203,2,1,"Deleted unread","text",true,new Date(baseTime-3000),null],
      [204,1,2,"Sent latest","text",false,new Date(baseTime-1000),null],
      [205,3,1,"","image",false,new Date(baseTime-2000),null],
      [206,4,1,"Blocked conversation","text",false,new Date(baseTime),null]
    ];
    for (const row of recentRows) {
      await client.query(
        "INSERT INTO private_message_index_fixture(id,sender_id,recipient_id,text,media_type,deleted,created_at,read_at) VALUES($1,$2,$3,$4,$5,$6,$7,$8)",
        row
      );
    }
    const recentIndex = await client.query(`
      WITH latest AS (
        SELECT DISTINCT ON (CASE WHEN m.sender_id=$1 THEN m.recipient_id ELSE m.sender_id END)
          m.id,m.sender_id,m.recipient_id,m.text,m.media_type,m.deleted,m.created_at
        FROM private_message_index_fixture m
        WHERE m.sender_id=$1 OR m.recipient_id=$1
        ORDER BY CASE WHEN m.sender_id=$1 THEN m.recipient_id ELSE m.sender_id END,
          m.created_at DESC,m.id DESC
      )
      SELECT u.username,u.display_name,u.profile_photo_media_id,
        CASE
          WHEN latest.deleted THEN 'Message deleted'
          WHEN COALESCE(NULLIF(latest.text,''),'') <> '' THEN
            CASE WHEN latest.sender_id=$1 THEN 'You: ' ELSE '' END || latest.text
          WHEN latest.media_type='image' THEN 'Photo'
          WHEN latest.media_type IN ('video','video_note') THEN 'Video'
          WHEN latest.media_type='audio' THEN 'Voice message'
          WHEN latest.media_type='document' THEN 'File'
          ELSE 'Message'
        END AS last_message,
        EXTRACT(EPOCH FROM latest.created_at) * 1000 AS last_message_timestamp,
        (SELECT COUNT(*) FROM private_message_index_fixture unread
          WHERE unread.sender_id=u.id AND unread.recipient_id=$1
            AND unread.read_at IS NULL AND unread.deleted=FALSE) AS unread_count
      FROM latest
      JOIN private_message_index_users u ON u.id=CASE WHEN latest.sender_id=$1 THEN latest.recipient_id ELSE latest.sender_id END
      WHERE NOT EXISTS (
        SELECT 1 FROM private_message_index_blocks b
        WHERE (b.blocker_id=$1 AND b.blocked_id=u.id)
           OR (b.blocker_id=u.id AND b.blocked_id=$1)
      )
      ORDER BY latest.created_at DESC,latest.id DESC
      LIMIT 200
    `, [1]);
    assert(recentIndex.rows.length === 2, "recent conversation index must be scoped to current user and exclude blocked pairs");
    assert(recentIndex.rows[0].username === "friend" && recentIndex.rows[0].last_message === "You: Sent latest",
      "recent index must choose the newest row per other participant and prefix the current user's latest message");
    assert(Number(recentIndex.rows[0].unread_count) === 2 && Number(recentIndex.rows[0].profile_photo_media_id) === 22,
      "recent index must return the other account's avatar and only undeleted unread incoming messages");
    assert(recentIndex.rows[1].username === "photo_friend" && recentIndex.rows[1].last_message === "Photo",
      "recent index must describe a media-only latest message without exposing another participant's conversation");
    console.log("PRIVATE MESSAGE RECENT CONVERSATION INDEX + ACCOUNT ISOLATION + BLOCK FILTER + UNREAD COUNT: GREEN");
    console.log("PRIVATE MESSAGE HISTORY SEARCH + CURSOR + PARTICIPANT ISOLATION + DELETED-MESSAGE FILTER: GREEN");
  } finally {
    client.release();
  }
}
run().catch(error => {
  console.error("PRIVATE MESSAGE HISTORY TEST FAILED:", error);
  process.exitCode = 1;
}).finally(async () => { await pool.end(); });
