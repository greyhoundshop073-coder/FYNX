import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const backendDir = path.dirname(fileURLToPath(import.meta.url));

async function patchOnce(fileName, patches) {
  const filePath = path.join(backendDir, fileName);
  let source = await readFile(filePath, "utf8");
  let changed = false;
  for (const patch of patches) {
    if (source.includes(patch.marker)) {
      source = source.replace(patch.marker, patch.replacement);
      changed = true;
    } else if (patch.required && !source.includes(patch.already)) {
      throw new Error(`Notification bootstrap could not locate ${fileName} marker: ${patch.already}`);
    }
  }
  if (changed) await writeFile(filePath, source);
}

await patchOnce("server.js", [
  {
    marker: 'import { registerMarketplaceAdvertisingRoutes } from "./marketplaceAdvertising.js";',
    replacement: 'import { registerMarketplaceAdvertisingRoutes } from "./marketplaceAdvertising.js";\nimport { queueFynxNotification, registerFynxPushRoutes } from "./notificationPush.js";'
  },
  {
    marker: 'registerSocialRoutes(app, { pool, auth, findUserByUsername });',
    replacement: 'registerSocialRoutes(app, { pool, auth, findUserByUsername });\nregisterFynxPushRoutes({ app, pool, auth });'
  },
  {
    marker: '    await broadcastMessage(message);\n    return res.status(201).json({ message });',
    replacement: '    await broadcastMessage(message);\n    await queueFynxNotification(pool, { userId: recipient.id, type: "MESSAGE", title: "New message", message: "You have a new message.", targetId: recipient.username, sourceUsername: req.user.username || null, route: "fynx://chat/" + encodeURIComponent(req.user.username || ""), notificationId: "message-" + message.id });\n    return res.status(201).json({ message });'
  }
]);

await patchOnce("groupRoutes.js", [
  {
    marker: "import { inspectTrustSafetyText } from './trustSafety.js';",
    replacement: "import { inspectTrustSafetyText } from './trustSafety.js';\nimport { queueFynxNotification } from './notificationPush.js';"
  },
  {
    marker: "res.status(201).json({message:messageJson(row)});",
    replacement: "const groupMembers=(await pool.query('SELECT gm.user_id,u.username FROM fynx_group_members gm JOIN users u ON u.id=gm.user_id WHERE gm.group_id=$1 AND gm.user_id<>$2',[groupId,req.user.sub])).rows; for(const member of groupMembers){await queueFynxNotification(pool,{userId:member.user_id,type:'GROUP',title:'New group message',message:'You have a new message in a FYNX group.',targetId:groupId,sourceUsername:row.sender_username,route:`fynx://group/${encodeURIComponent(groupId)}`,notificationId:`group-message-${row.id}-${member.user_id}`});}res.status(201).json({message:messageJson(row)});"
  }
]);

await patchOnce("socialRoutes.js", [
  {
    marker: 'export function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {',
    replacement: 'import { queueFynxNotification } from "./notificationPush.js";\n\nexport function registerSocialRoutes({ app, pool, auth, findUserByUsername }) {'
  },
  {
    marker: "      return res.status(201).json({ request: result.rows[0] });",
    replacement: "      await queueFynxNotification(pool,{userId:target.id,type:'FRIEND_REQUEST',title:'New friend request',message:`@${req.user.username || 'A FYNX user'} sent you a friend request.`,targetId:result.rows[0].id,sourceUsername:req.user.username || null,route:`fynx://profile/${encodeURIComponent(req.user.username || '')}`,notificationId:`friend-request-${result.rows[0].id}`});\n      return res.status(201).json({ request: result.rows[0] });"
  },
  {
    marker: "      return res.json({ request: result.rows[0] });",
    replacement: "      await queueFynxNotification(pool,{userId:result.rows[0].user_id,type:'FRIEND_REQUEST',title:'Friend request accepted',message:`@${req.user.username || 'A FYNX user'} accepted your friend request.`,targetId:result.rows[0].id,sourceUsername:req.user.username || null,route:`fynx://profile/${encodeURIComponent(req.user.username || '')}`,notificationId:`friend-accepted-${result.rows[0].id}`});\n      return res.json({ request: result.rows[0] });"
  },
  {
    marker: "      const author = await pool.query('SELECT username,display_name FROM users WHERE id=$1', [req.user.sub]);\n      return res.status(201).json({ comment:{ id:String(result.rows[0].id), text, timestamp:Number(result.rows[0].timestamp), authorId:String(req.user.sub), authorUsername:author.rows[0]?.username || '', authorDisplayName:author.rows[0]?.display_name || '' } });",
    replacement: "      const author = await pool.query('SELECT username,display_name FROM users WHERE id=$1', [req.user.sub]);\n      const postOwner = await pool.query('SELECT author_id FROM social_posts WHERE id=$1', [id]);\n      if (postOwner.rows[0] && String(postOwner.rows[0].author_id) !== String(req.user.sub)) {\n        await queueFynxNotification(pool,{userId:postOwner.rows[0].author_id,type:'COMMENT',title:`@${author.rows[0]?.username || 'A FYNX user'} commented on your post`,message:text,targetId:id,sourceUsername:author.rows[0]?.username || null,route:'fynx://home',notificationId:`comment-${result.rows[0].id}`});\n      }\n      return res.status(201).json({ comment:{ id:String(result.rows[0].id), text, timestamp:Number(result.rows[0].timestamp), authorId:String(req.user.sub), authorUsername:author.rows[0]?.username || '', authorDisplayName:author.rows[0]?.display_name || '' } });"
  }
]);

await patchOnce("realtimeIsolationBootstrap.js", [
  {
    marker: 'import { installSocialPostReactions } from "./socialPostReactionBootstrap.js";',
    replacement: 'import { installSocialPostReactions } from "./socialPostReactionBootstrap.js";\nimport { queueFynxNotification } from "./notificationPush.js";'
  },
  {
    marker: "      await client.query('COMMIT');\n      const row = inserted.rows[0];",
    replacement: "      await client.query('COMMIT');\n      const row = inserted.rows[0];\n      const postOwner = await pool.query('SELECT author_id FROM social_posts WHERE id=$1', [postId]);\n      const replyRecipients = new Set();\n      const parentAuthorId = parent.rows[0]?.author_id;\n      if (parentAuthorId && String(parentAuthorId) !== String(req.user.sub)) replyRecipients.add(String(parentAuthorId));\n      const postOwnerId = postOwner.rows[0]?.author_id;\n      if (postOwnerId && String(postOwnerId) !== String(req.user.sub)) replyRecipients.add(String(postOwnerId));\n      for (const recipientId of replyRecipients) {\n        await queueFynxNotification(pool,{userId:recipientId,type:'COMMENT',title:`@${author.rows[0].username || 'A FYNX user'} replied to a comment`,message:text,targetId:postId,sourceUsername:author.rows[0].username || null,route:'fynx://home',notificationId:`reply-${row.id}-${recipientId}`});\n      }"
  }
]);

console.log("FYNX notification bootstrap: route wiring ready");
