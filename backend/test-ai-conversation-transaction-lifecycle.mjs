import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const route = await readFile(new URL("./aiConversationRoutes.js", import.meta.url), "utf8");
const build = await readFile(new URL("../app/build.gradle.kts", import.meta.url), "utf8");
const engine = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxAiWebRtcEngine.kt", import.meta.url), "utf8");
const panel = await readFile(new URL("../app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt", import.meta.url), "utf8");

const userCommit = route.indexOf('await db.query("COMMIT");', route.indexOf('stage="persist_user_message"'));
const geminiCall = route.indexOf("await runAssistantAgent({", route.indexOf('stage="gemini_agent"'));
const assistantBegin = route.indexOf('await db.query("BEGIN");', route.indexOf('stage="persist_assistant_reply"'));
const assistantCommit = route.indexOf('await db.query("COMMIT");', assistantBegin);
assert.ok(userCommit >= 0 && geminiCall > userCommit, "user turn must commit before Gemini network work");
assert.ok(assistantBegin > geminiCall && assistantCommit > assistantBegin, "assistant persistence must use a separate short transaction");
assert.match(route, /DELETE FROM ai_messages WHERE id=\$1 AND user_id=\$2/, "failed Gemini turns must roll back their persisted user message");
assert.doesNotMatch(build, /genai-speech-recognition/, "Google on-device GenAI speech recognition must not be packaged");
assert.match(engine, /fun dispose\(\) \{ close\(\); factory\.dispose\(\) \}/, "WebRTC factory must expose final native disposal");
assert.match(panel, /readAloudEngine\.dispose\(\)/, "read-aloud engine must be disposed on screen teardown");
assert.match(panel, /voiceEngine\.dispose\(\)/, "microphone engine must be disposed on screen teardown");
console.log("FYNX AI lifecycle checks GREEN: short DB transactions, provider rollback, no local Google speech package, native WebRTC disposal");
