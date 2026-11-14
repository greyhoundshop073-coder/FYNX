import assert from "node:assert/strict";
import pg from "pg";
import jwt from "jsonwebtoken";
import { randomUUID } from "node:crypto";
const { Client } = pg;
const host=process.env.PGHOST||"localhost", port=Number(process.env.PGPORT||5432);
const user=process.env.PGUSER||"fynx_test", password=process.env.PGPASSWORD||"fynx_test", database=process.env.PGDATABASE||"fynx_media_test";
process.env.DATABASE_URL ||= "postgresql://"+encodeURIComponent(user)+":"+encodeURIComponent(password)+"@"+host+":"+port+"/"+database;
process.env.JWT_SECRET ||= "fynx-group-membership-test-secret";
const { registerGroupMembershipRoutes }=await import("./groupMembershipRoutes.js");
const { registerGroupRoutes }=await import("./groupRoutes.js");
const { getFynxDatabasePool }=await import("./databasePool.js");
const client=new Client({host,port,user,password,database});
const suffix=randomUUID().replaceAll("-",""), base=BigInt(Date.now())*10000n;
const admin=base+1n, requester=base+2n, second=base+3n, capacityRequester=base+4n;
const group="grp_"+suffix.slice(0,28), capGroup="cap_"+suffix.slice(0,28), syncGroup="sync_"+suffix.slice(0,24), overflowGroup="overflow_"+suffix.slice(0,20);
const routes=new Map(), app={get:(p,...h)=>routes.set("GET "+p,h),post:(p,...h)=>routes.set("POST "+p,h),patch:(p,...h)=>routes.set("PATCH "+p,h),delete:(p,...h)=>routes.set("DELETE "+p,h)};
const token=id=>jwt.sign({sub:String(id)},process.env.JWT_SECRET);
async function call(method,path,params,userId,body={}) {
 const handlers=routes.get(method+" "+path); assert.ok(handlers,"registered "+method+" "+path);
 const req={params,body,get:()=>"Bearer "+token(userId)};
 const res={statusCode:200,body:null,status(c){this.statusCode=c;return this;},json(v){this.body=v;return this;},set(){return this;}};
 await handlers[0](req,res,()=>handlers[1](req,res)); return res;
}
await client.connect();
try {
 await client.query(`
 CREATE TABLE IF NOT EXISTS users(id BIGINT PRIMARY KEY,username TEXT NOT NULL UNIQUE,password_hash TEXT NOT NULL DEFAULT '',display_name TEXT NOT NULL DEFAULT '',phone TEXT NOT NULL DEFAULT '',verified BOOLEAN NOT NULL DEFAULT FALSE,created_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
 CREATE TABLE IF NOT EXISTS fynx_groups(id TEXT PRIMARY KEY,owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,name TEXT NOT NULL,description TEXT NOT NULL DEFAULT '',visibility TEXT NOT NULL DEFAULT 'PRIVATE',created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
 ALTER TABLE fynx_groups ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
 CREATE TABLE IF NOT EXISTS fynx_group_members(group_id TEXT NOT NULL REFERENCES fynx_groups(id) ON DELETE CASCADE,user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,role TEXT NOT NULL DEFAULT 'MEMBER',created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),PRIMARY KEY(group_id,user_id));
 ALTER TABLE fynx_group_members ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
 `);
 await client.query("INSERT INTO users(id,username,password_hash) VALUES($1,$2,''),($3,$4,''),($5,$6,''),($7,$8,'')",
 [admin,"owner_"+suffix.slice(0,12),requester,"requester_"+suffix.slice(0,12),second,"second_"+suffix.slice(0,12),capacityRequester,"capacity_"+suffix.slice(0,12)]);
 await client.query("INSERT INTO fynx_groups(id,owner_id,name) VALUES($1,$2,'Approval group'),($3,$2,'Capacity group')",[group,admin,capGroup]);
 await client.query("INSERT INTO fynx_group_members(group_id,user_id,role) VALUES($1,$2,'ADMIN'),($3,$2,'ADMIN')",[group,admin,capGroup]);
 registerGroupMembershipRoutes({app});
 registerGroupRoutes({app});
 const initialPermissions=await call("GET","/api/groups/:groupId/permissions",{groupId:group},admin);
 assert.equal(initialPermissions.statusCode,200);
 assert.equal(initialPermissions.body.permissions.show_history_to_new_members,true,"new groups must preserve the existing show-history default");
 const historyPolicy=await call("PATCH","/api/groups/:groupId/permissions",{groupId:group},admin,{showHistoryToNewMembers:false});
 assert.equal(historyPolicy.statusCode,200);
 assert.equal(historyPolicy.body.permissions.show_history_to_new_members,false,"group admins must be able to disable prior history for new members");
 assert.equal((await call("PATCH","/api/groups/:groupId/permissions",{groupId:group},requester,{showHistoryToNewMembers:true})).statusCode,403,"non-admin members must not change new-member history visibility");
 const created=await call("POST","/api/groups/:groupId/invites",{groupId:group},admin);
 assert.equal(created.statusCode,201);
 const invite=created.body.token;
 assert.equal((await call("GET","/api/groups/:groupId/invites/:token",{groupId:group,token:invite},requester)).statusCode,200);
 await client.query("UPDATE fynx_groups SET invite_links=FALSE WHERE id=$1",[group]);
 assert.equal((await call("GET","/api/groups/:groupId/invites/:token",{groupId:group,token:invite},requester)).statusCode,404);
 assert.equal((await call("POST","/api/groups/:groupId/invites/:token/request",{groupId:group,token:invite},requester)).statusCode,404);
 await client.query("UPDATE fynx_groups SET invite_links=TRUE,approve_new_members=TRUE WHERE id=$1",[group]);
 assert.equal((await call("POST","/api/groups/:groupId/invites/:token/request",{groupId:group,token:invite},requester)).statusCode,202);
 assert.equal((await client.query("SELECT 1 FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2",[group,requester])).rowCount,1);
 assert.equal((await call("GET","/api/groups/:groupId/join-requests",{groupId:group},requester)).statusCode,403);
 const listed=await call("GET","/api/groups/:groupId/join-requests",{groupId:group},admin);
 assert.equal(listed.statusCode,200);
 assert.equal(listed.body.requests[0]?.username,"requester_"+suffix.slice(0,12));
 assert.equal((await call("POST","/api/groups/:groupId/join-requests/:username/approve",{groupId:group,username:"requester_"+suffix.slice(0,12)},admin)).statusCode,200);
 assert.equal((await client.query("SELECT role FROM fynx_group_members WHERE group_id=$1 AND user_id=$2",[group,requester])).rows[0].role,"MEMBER");
 assert.equal((await client.query("SELECT 1 FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2",[group,requester])).rowCount,0);

 // Exercise the real authenticated report route with distinct member identities.
 const memberReport=await call("POST","/api/groups/:groupId/reports",{groupId:group},requester,{
   reason:"A member sent abusive messages.",
   username:"owner_"+suffix.slice(0,12)
 });
 assert.equal(memberReport.statusCode,201,"an authenticated group member can report another member");
 assert.ok(memberReport.body.reportId,"member report returns a stable report ID");
 const storedMemberReport=(await client.query(
   "SELECT reporter_id,target_user_id,reason FROM fynx_group_reports WHERE id=$1",[memberReport.body.reportId]
 )).rows[0];
 assert.equal(String(storedMemberReport.reporter_id),String(requester),"the authenticated member is recorded as reporter");
 assert.equal(String(storedMemberReport.target_user_id),String(admin),"the selected group member is recorded as target");
 assert.equal(storedMemberReport.reason,"A member sent abusive messages.");
 assert.equal((await call("POST","/api/groups/:groupId/reports",{groupId:group},requester,{
   reason:"This target is not in the group.",username:"second_"+suffix.slice(0,12)
 })).statusCode,404,"a user outside the group cannot be selected as the reported member");
 assert.equal((await call("POST","/api/groups/:groupId/reports",{groupId:group},requester,{reason:"   "}))
   .statusCode,400,"a group report must contain a nonblank reason");
 const groupReport=await call("POST","/api/groups/:groupId/reports",{groupId:group},admin,{
   reason:"This group requires safety review."
 });
 assert.equal(groupReport.statusCode,201,"an authenticated group member can report the group");
 const storedGroupReport=(await client.query(
   "SELECT reporter_id,target_user_id,reason FROM fynx_group_reports WHERE id=$1",[groupReport.body.reportId]
 )).rows[0];
 assert.equal(String(storedGroupReport.reporter_id),String(admin));
 assert.equal(storedGroupReport.target_user_id,null,"a group report must not invent a member target");
 assert.equal((await call("POST","/api/groups/:groupId/reports",{groupId:group},second,{
   reason:"I am not a member."
 })).statusCode,403,"nonmembers cannot submit group or member reports");
 assert.equal((await call("POST","/api/groups/:groupId/invites/:token/join",{groupId:group,token:invite},second)).statusCode,202);
 assert.equal((await client.query("SELECT 1 FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2",[group,second])).rowCount,1);
 assert.equal((await call("POST","/api/groups/:groupId/join-requests/:username/reject",{groupId:group,username:"second_"+suffix.slice(0,12)},admin)).statusCode,200);
 assert.equal((await client.query("SELECT 1 FROM fynx_group_members WHERE group_id=$1 AND user_id=$2",[group,second])).rowCount,0);
 const bulk=await client.query("INSERT INTO users(id,username,password_hash) SELECT $1::bigint+g::bigint,'capacity_'||g||'_'||$2,'' FROM generate_series(1,499) AS series(g)",[base+100n,suffix.slice(0,8)]);
 assert.equal(bulk.rowCount,499);
 await client.query("INSERT INTO fynx_group_members(group_id,user_id,role) SELECT $1,id,'MEMBER' FROM users WHERE id BETWEEN $2 AND $3",[capGroup,base+101n,base+599n]);
 const capacityMemberCount=await client.query("SELECT COUNT(*)::int AS count FROM fynx_group_members WHERE group_id=$1",[capGroup]);
 assert.equal(capacityMemberCount.rows[0].count,500,"capacity fixture must contain the owner plus 499 members before approval");
 await client.query("UPDATE fynx_groups SET approve_new_members=TRUE WHERE id=$1",[capGroup]);
 const capInvite=await call("POST","/api/groups/:groupId/invites",{groupId:capGroup},admin);
 assert.equal((await call("POST","/api/groups/:groupId/invites/:token/request",{groupId:capGroup,token:capInvite.body.token},capacityRequester)).statusCode,202);
 assert.equal((await call("POST","/api/groups/:groupId/join-requests/:username/approve",{groupId:capGroup,username:"capacity_"+suffix.slice(0,12)},admin)).statusCode,409);
 assert.equal((await client.query("SELECT 1 FROM fynx_group_join_requests WHERE group_id=$1 AND user_id=$2",[capGroup,capacityRequester])).rowCount,1);
 const oversizedMembers=Array.from({length:499},(_,index)=>"capacity_"+(index+1)+"_"+suffix.slice(0,8));
 const exactCapacity=await call("POST","/api/groups/:groupId/sync",{groupId:syncGroup},admin,{ownerUsername:"owner_"+suffix.slice(0,12),name:"Exact capacity group",members:oversizedMembers});
 assert.equal(exactCapacity.statusCode,200,"group creation with owner plus 499 members must allow exactly 500 members");
 assert.equal(exactCapacity.body.group.members.length,500,"the exact-capacity group must contain 500 persisted members");
 const overCapacity=await call("POST","/api/groups/:groupId/sync",{groupId:overflowGroup},admin,{ownerUsername:"owner_"+suffix.slice(0,12),name:"Over capacity group",members:[...oversizedMembers,"capacity_"+suffix.slice(0,12)]});
 assert.equal(overCapacity.statusCode,409,"group creation with owner plus 500 requested members must be rejected");
 assert.equal(overCapacity.body.code,"GROUP_MEMBER_LIMIT_REACHED");
 assert.equal((await client.query("SELECT 1 FROM fynx_groups WHERE id=$1",[overflowGroup])).rowCount,0,"over-capacity group creation must not leave a partial group");
 console.log("GROUP MEMBERSHIP POSTGRESQL ROUTE INTEGRATION: GREEN (invite preview, disabled links, persisted requests, admin authorization, approval, rejection, legacy join, and exact/over-capacity creation guards)");
} finally {
 await client.query("DELETE FROM fynx_groups WHERE id=ANY($1::text[])",[[group,capGroup,syncGroup,overflowGroup]]).catch(()=>{});
 await client.query("DELETE FROM users WHERE id >= $1 AND id < $2",[base,base+1000n]).catch(()=>{});
 await client.end();
 await getFynxDatabasePool()?.end();
}
