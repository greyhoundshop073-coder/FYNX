#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
conversation = (ROOT / "app/src/main/java/com/fynx/app/ui/ConversationPanel.kt").read_text()
groups = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupsPanel.kt").read_text()
messaging = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxProductionMessaging.kt").read_text()
emoji = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxChatEmojiPanel.kt").read_text()
server = (ROOT / "backend/server.js").read_text()
group_client = (ROOT / "app/src/main/java/com/fynx/app/ui/FynxGroupRemoteClient.kt").read_text()

checks = {
    "emoji panel exists": 'fun FynxChatEmojiPanel' in emoji,
    "emoji categories": 'FynxEmojiCategories' in emoji and 'Smileys' in emoji,
    "private emoji entry": 'showEmojiPanel' in conversation and 'FynxChatEmojiPanel' in conversation,
    "private quick reactions": 'reactionMessageId' in conversation and 'listOf("❤️","😂","👍","🙏","🔥","😮","😢","👏")' in conversation,
    "private reaction client": 'reactToMessage' in messaging and '"/api/messages/$id/reaction"' in messaging,
    "private reaction model": 'reaction: String?' in messaging and 'optString("reaction")' in messaging,
    "private reaction schema": 'ADD COLUMN IF NOT EXISTS reaction TEXT' in server,
    "private reaction projection": 'm.reaction' in server and 'reaction: row.reaction' in server,
    "private reaction endpoint": 'app.patch("/api/messages/:id/reaction"' in server,
    "group emoji entry": 'FynxChatEmojiPanel' in groups and 'showEmojiPanel' in groups,
    "group quick reactions": 'reactionMessageId' in groups and 'listOf("❤️","😂","👍","🙏","🔥","😮","😢","👏")' in groups and 'FynxGroupRemoteClient.reactToMessage' in groups,
    "group reply affordance": 'replyToId = message.id' in groups,
    "group reaction backend already connected": 'reactToMessage' in group_client,
}
failed=[name for name,ok in checks.items() if not ok]
if failed:
    raise SystemExit("Batch 11 RED: " + ", ".join(failed))
print("Batch 11 chat expressions verification GREEN")
for name in checks: print("PASS:", name)
