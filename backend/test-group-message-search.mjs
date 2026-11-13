import pg from "pg";
const { Pool } = pg;
const pool = new Pool({host:process.env.PGHOST||"localhost",port:Number(process.env.PGPORT||5432),user:process.env.PGUSER||"fynx_test",password:process.env.PGPASSWORD||"fynx_test",database:process.env.PGDATABASE||"fynx_media_test",max:1});
const assert=(condition,message)=>{if(!condition)throw new Error(message);};
async function run(){
 const client=await pool.connect();
 try{
  await client.query(`CREATE TEMP TABLE group_message_search_fixture(id TEXT PRIMARY KEY,group_id TEXT NOT NULL,text TEXT NOT NULL DEFAULT '',created_at TIMESTAMPTZ NOT NULL) ON COMMIT PRESERVE ROWS`);
  await client.query(`CREATE INDEX group_message_search_fixture_idx ON group_message_search_fixture USING GIN(to_tsvector('simple',COALESCE(text,'')))`);
  const fixtures=[
   ["group-a-0001","group-a","Marketplace delivery status one","2026-11-13T10:00:00.000Z"],
   ["group-a-0002","group-a","Marketplace delivery status two","2026-11-13T10:01:00.000Z"],
   ["group-a-0003","group-a","Marketplace delivery status three","2026-11-13T10:02:00.000Z"],
   ["group-a-0004","group-a","Marketplace return requested","2026-11-13T10:03:00.000Z"],
   ["group-b-0001","group-b","Marketplace delivery from another group","2026-11-13T10:04:00.000Z"]
  ];
  for(const [id,groupId,text,createdAt] of fixtures)await client.query("INSERT INTO group_message_search_fixture(id,group_id,text,created_at) VALUES($1,$2,$3,$4)",[id,groupId,text,createdAt]);
  const searchPage=async({groupId,query,before=null,beforeId="",limit=2})=>{
   const result=await client.query(`SELECT id,text,created_at FROM group_message_search_fixture
     WHERE group_id=$1 AND ($2::text IS NULL OR to_tsvector('simple',COALESCE(text,'')) @@ plainto_tsquery('simple',$2))
     AND ($3::bigint IS NULL OR created_at < to_timestamp($3::double precision/1000.0)
       OR (date_trunc('milliseconds',created_at)=to_timestamp($3::double precision/1000.0) AND id<$4))
     ORDER BY created_at DESC,id DESC LIMIT $5`,[groupId,query,before,beforeId,limit+1]);
   const hasMore=result.rows.length>limit,pageRows=result.rows.slice(0,limit),oldest=pageRows[pageRows.length-1];
   return {messages:pageRows.reverse(),hasMore,nextCursor:oldest?{timestamp:new Date(oldest.created_at).getTime(),id:oldest.id}:null};
  };
  const first=await searchPage({groupId:"group-a",query:"marketplace delivery"});
  assert(first.messages.length===2,"first search page must return two hits");
  assert(first.messages.map(x=>x.id).join(",")==="group-a-0002,group-a-0003","first page ordering must be stable");
  assert(first.hasMore&&first.nextCursor?.id==="group-a-0002","first page must expose its oldest-row cursor");
  assert(first.messages.every(x=>x.id.startsWith("group-a-")),"search must not leak messages from another group");
  const second=await searchPage({groupId:"group-a",query:"marketplace delivery",before:first.nextCursor.timestamp,beforeId:first.nextCursor.id});
  assert(second.messages.length===1&&second.messages[0].id==="group-a-0001","second page must return the older matching message");
  assert(!second.hasMore,"last cursor page must have no more results");
  const empty=await searchPage({groupId:"group-a",query:"missing phrase"});
  assert(empty.messages.length===0&&!empty.hasMore,"no-match query must return empty results");
  const all=await searchPage({groupId:"group-a",query:null,limit:10});
  assert(all.messages.length===4&&all.messages.every(x=>x.id.startsWith("group-a-")),"q omitted must keep group scoping");
  const hostile=await searchPage({groupId:"group-a",query:"' OR 1=1 --",limit:10});
  assert(hostile.messages.length===0,"SQL-shaped input must remain a bound text value");
  await client.query(`CREATE TEMP TABLE group_message_receipt_source(id TEXT PRIMARY KEY,group_id TEXT NOT NULL,sender_id BIGINT NOT NULL) ON COMMIT PRESERVE ROWS`);
  await client.query(`CREATE TEMP TABLE group_message_receipt_fixture(message_id TEXT NOT NULL REFERENCES group_message_receipt_source(id) ON DELETE CASCADE,user_id BIGINT NOT NULL,read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(message_id,user_id)) ON COMMIT PRESERVE ROWS`);
  await client.query("INSERT INTO group_message_receipt_source(id,group_id,sender_id) VALUES ('receipt-a-0001','group-a',10),('receipt-a-0002','group-a',11),('receipt-b-0001','group-b',12)");
  const markRead=async(groupId,messageIds,userId)=>client.query(`INSERT INTO group_message_receipt_fixture(message_id,user_id)
    SELECT message.id,$3 FROM group_message_receipt_source message
    WHERE message.group_id=$1 AND message.id=ANY($2::text[]) AND message.sender_id<>$3
    ON CONFLICT(message_id,user_id) DO NOTHING RETURNING message_id`,[groupId,messageIds,userId]);
  const firstRead=await markRead("group-a",["receipt-a-0001","receipt-a-0002","receipt-b-0001"],10);
  assert(firstRead.rows.length===1&&firstRead.rows[0].message_id==="receipt-a-0002","read receipts must exclude the reader's own messages and other groups");
  const duplicateRead=await markRead("group-a",["receipt-a-0002"],10);
  assert(duplicateRead.rows.length===0,"repeated read marking must be idempotent");
  const secondReader=await markRead("group-a",["receipt-a-0002"],13);
  assert(secondReader.rows.length===1,"each group member must have an independent read receipt");
  const receiptCount=await client.query("SELECT COUNT(*)::int AS count FROM group_message_receipt_fixture WHERE message_id='receipt-a-0002'");
  assert(receiptCount.rows[0].count===2,"the message must retain one read receipt per distinct reader");
  console.log("GROUP MESSAGE SEARCH + READ RECEIPTS POSTGRESQL: GREEN (full-text, group isolation, cursor stability, idempotent per-member receipts, sender exclusion)");
 }finally{client.release();}
}
run().catch(error=>{console.error("GROUP MESSAGE SEARCH POSTGRESQL: RED",error);process.exitCode=1;}).finally(async()=>{await pool.end();});
