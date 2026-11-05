import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { applyNotificationBootstrapPatch } from "./notificationBootstrapPatch.js";

const marker = "export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {";
const importLine = 'import { queueFynxNotification } from "./notificationPush.js";';
const replacement = importLine + "\n\n" + marker;
const patch = { marker, replacement, already: importLine, required: true };

// Reproduce the failure state: an equivalent import already exists but is not
// adjacent to the function marker searched by the older bootstrap implementation.
const existingImportFixture = importLine + "\nimport { Buffer } from \"node:buffer\";\n\n" + marker;
assert.equal(applyNotificationBootstrapPatch(existingImportFixture, patch), existingImportFixture,
  "an existing equivalent import must not be inserted a second time");

const cleanFixture = marker + "\n  // route body\n}\n";
const firstPass = applyNotificationBootstrapPatch(cleanFixture, patch);
assert.notEqual(firstPass, cleanFixture, "a missing import is added");
assert.equal(applyNotificationBootstrapPatch(firstPass, patch), firstPass,
  "running the patch twice is idempotent");
assert.equal(firstPass.split(importLine).length - 1, 1, "the import appears exactly once");

const routePatch = { marker: "res.status(201).json({message:messageJson(row)});",
  replacement: "await notifyGroupMembers(); res.status(201).json({message:messageJson(row)});" };
const routeFixture = "async function handler() { " + routePatch.marker + " }";
const routeFirstPass = applyNotificationBootstrapPatch(routeFixture, routePatch);
assert.equal(applyNotificationBootstrapPatch(routeFirstPass, routePatch), routeFirstPass,
  "route insertion is idempotent too");

assert.throws(() => applyNotificationBootstrapPatch("const untouched = true;", {
  marker: "missing marker", replacement: "missing marker plus insertion", required: true
}, "fixture.js"), /Notification bootstrap could not locate fixture.js marker/,
"required missing markers must fail loudly");


const bootstrap = await readFile(new URL("./notificationBootstrap.js", import.meta.url), "utf8");
const socialRoutes = await readFile(new URL("./socialRoutes.js", import.meta.url), "utf8");

const commentRouteMarker = "const u=await pool.query('SELECT username,display_name FROM users WHERE id=$1',[req.user.sub]);res.status(201).json({comment:{id:String(r.rows[0].id),text,timestamp:Number(r.rows[0].timestamp),authorId:String(req.user.sub),authorUsername:u.rows[0]?.username||'',authorDisplayName:u.rows[0]?.display_name||''}})";
const commentRouteReplacement = "const u=await pool.query('SELECT username,display_name FROM users WHERE id=$1',[req.user.sub]);const postOwner=await pool.query('SELECT author_id FROM social_posts WHERE id=$1',[id]);if(postOwner.rows[0]&&String(postOwner.rows[0].author_id)!==String(req.user.sub)){await queueFynxNotification(pool,{userId:postOwner.rows[0].author_id,type:'COMMENT',title:'@'+(u.rows[0]?.username||'A FYNX user')+' commented on your post',message:text,targetId:id,sourceUsername:u.rows[0]?.username||null,route:'fynx://post/'+id+'?comment='+r.rows[0].id,notificationId:'comment-'+r.rows[0].id});}res.status(201).json({comment:{id:String(r.rows[0].id),text,timestamp:Number(r.rows[0].timestamp),authorId:String(req.user.sub),authorUsername:u.rows[0]?.username||'',authorDisplayName:u.rows[0]?.display_name||''}})";
const commentRouteFixture = commentRouteMarker;
const commentRoutePatch = { marker: commentRouteMarker, replacement: commentRouteReplacement };
const notifiedCommentRoute = applyNotificationBootstrapPatch(commentRouteFixture, commentRoutePatch);
assert.ok(notifiedCommentRoute.includes("notificationId:'comment-' + r.rows[0].id") || notifiedCommentRoute.includes("notificationId:'comment-'+r.rows[0].id") || notifiedCommentRoute.includes("notificationId:'comment-'"), "new top-level comments enqueue a notification for the post owner");
assert.equal(applyNotificationBootstrapPatch(notifiedCommentRoute, commentRoutePatch), notifiedCommentRoute,
  "top-level comment notification insertion is idempotent");

assert.ok(bootstrap.includes("postOwner=await pool.query('SELECT author_id FROM social_posts WHERE id=$1',[id])"),
  "the bootstrap notification patch targets the live top-level comment route");
assert.ok(bootstrap.includes("notificationId:'comment-'+r.rows[0].id"),
  "top-level comment notifications use a stable id");
const commentPatchStart = bootstrap.indexOf('marker: "const u=await pool.query');
const commentPatchEnd = bootstrap.indexOf("\n  }\n]);", commentPatchStart);
const commentPatch = bootstrap.slice(commentPatchStart, commentPatchEnd);
assert.equal((commentPatch.match(/replacement:/g) || []).length, 1,
  "top-level comment patch must have exactly one effective replacement");
assert.ok(commentPatch.includes("r.rows[0].id") && !commentPatch.includes("result.rows[0].id"),
  "top-level comment patch must use the live route's r result variable");

assert.ok(bootstrap.includes('import { applyNotificationBootstrapPatch } from "./notificationBootstrapPatch.js";'),
  "the live bootstrap must use the tested patch helper");
assert.ok(bootstrap.includes("already: 'import { queueFynxNotification } from \"./notificationPush.js\";'"),
  "the social route import patch must declare its already-present equivalent import");
assert.equal(socialRoutes.split(importLine).length - 1, 1,
  "the actual canonical social routes file should have a single notification import");
assert.ok(socialRoutes.includes(commentRouteMarker),
  "the top-level comment notification patch marker must match the actual social route implementation");

const realtimeBootstrap = await readFile(new URL("./realtimeIsolationBootstrap.js", import.meta.url), "utf8");
const replyRouteMarker = "await client.query('COMMIT');\n      const row = inserted.rows[0];";
assert.ok(realtimeBootstrap.includes(replyRouteMarker),
  "the reply notification patch marker must match the real reply route bootstrap");
assert.ok(bootstrap.includes("notificationId:'reply-' + row.id + '-' + recipientId"),
  "reply notifications use a stable recipient-specific id");

console.log("Notification bootstrap idempotency: PASS (top-level comments, replies, existing import, repeat starts and required markers)");
