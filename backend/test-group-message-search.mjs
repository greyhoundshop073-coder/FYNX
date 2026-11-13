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
  console.log("GROUP MESSAGE SEARCH POSTGRESQL: GREEN (full-text, group isolation, cursor stability, empty results, parameter binding)");
 }finally{client.release();}
}
run().catch(error=>{console.error("GROUP MESSAGE SEARCH POSTGRESQL: RED",error);process.exitCode=1;}).finally(async()=>{await pool.end();});
