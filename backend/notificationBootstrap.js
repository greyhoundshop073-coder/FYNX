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
    replacement: '    await broadcastMessage(message);\n    await queueFynxNotification(pool, { userId: recipient.id, type: "MESSAGE", title: `Message from @${sender.rows[0]?.username || req.user.username || "FYNX user"}`, message: "You have a new message.", targetId: recipient.username, sourceUsername: sender.rows[0]?.username || req.user.username || null, route: `fynx://chat/${encodeURIComponent(sender.rows[0]?.username || req.user.username || "")}`, notificationId: `message-${message.id}` });\n    return res.status(201).json({ message });'
  },
  {
    marker: 'if (signalType === "offer" || signalType === "answer") {',
    replacement: 'if (signalType === "offer" || signalType === "answer") {'
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
  }
]);

console.log("FYNX notification bootstrap: route wiring ready");
await import("./realtimeIsolationBootstrap.js");
