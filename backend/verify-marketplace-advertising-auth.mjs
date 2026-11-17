import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createSessionAwareBearerAuthenticator } from "./sessionAwareAuth.js";
const advertising=await readFile(new URL("./marketplaceAdvertising.js",import.meta.url),"utf8");
const response=()=>({statusCode:null,body:null,sends:0,status(code){this.statusCode=code;return this;},json(body){this.body=body;this.sends++;return this;}});
const jwt={verify(token){const all={"valid-current":{sub:"9007199254740993",sv:2},"revoked-session":{sub:"314",sv:4},"deleted-account":{sub:"999",sv:0},"out-of-range":{sub:"9223372036854775808",sv:2}};if(!all[token])throw Error("invalid token");return all[token];}};
const run=async({token,pool,secret="test-secret"})=>{const res=response();const authenticate=createSessionAwareBearerAuthenticator({pool,jwt,jwtSecret:secret});const value=await authenticate({headers:token?{authorization:"Bearer "+token}:{}},res);return {value,res};};
{
 const {value,res}=await run({token:"valid-current",secret:""});assert.equal(value,null);assert.equal(res.statusCode,503);assert.equal(res.sends,1);
}
{
 const {value,res}=await run({pool:{query:async()=>({rows:[]})}});assert.equal(value,null);assert.equal(res.statusCode,401);assert.equal(res.sends,1);
}
{
 const {value,res}=await run({token:"invalid",pool:{query:async()=>({rows:[]})}});assert.equal(value,null);assert.equal(res.statusCode,401);assert.equal(res.sends,1);
}
{
 let id;const {value,res}=await run({token:"valid-current",pool:{query:async(_s,p)=>{id=p[0];return {rows:[{session_version:2}]};}}});assert.deepEqual(value,{userId:"9007199254740993"});assert.equal(id,"9007199254740993");assert.equal(res.sends,0);
}
{
 const {value,res}=await run({token:"revoked-session",pool:{query:async()=>({rows:[{session_version:5}]})}});assert.equal(value,null);assert.equal(res.statusCode,401);assert.equal(res.body.error,"session expired; sign in again");
}
{
 const {value,res}=await run({token:"deleted-account",pool:{query:async()=>({rows:[]})}});assert.equal(value,null);assert.equal(res.statusCode,401);
}
{
 let queryCount=0;
 const {value,res}=await run({token:"out-of-range",pool:{query:async()=>{queryCount++;throw Error("should not query");}}});
 assert.equal(value,null);assert.equal(res.statusCode,401);assert.equal(queryCount,0);
}
{
 const {value,res}=await run({token:"valid-current",pool:{query:async()=>{throw Error("DB down");}}});assert.equal(value,null);assert.equal(res.statusCode,503);
}
{
 const {value,res}=await run({token:"valid-current",pool:null});assert.equal(value,null);assert.equal(res.statusCode,503);
}
const awaited=advertising.match(/const auth = await authenticate\(req, res\);/g)||[];
const unawaited=advertising.match(/const auth = authenticate\(req, res\);/g)||[];
const calls=advertising.match(/authenticate\(req, res\)/g)||[];
assert.ok(awaited.length>0);assert.equal(unawaited.length,0);assert.equal(awaited.length,calls.length);
assert.ok(advertising.includes("createSessionAwareBearerAuthenticator"));
console.log("Marketplace advertising auth passed: current/revoked/deleted/out-of-range sessions, missing/invalid credentials, database outage, exact BIGINT and awaited route guards.");
