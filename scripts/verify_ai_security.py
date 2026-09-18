from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


checks = []

def check(name, condition):
    checks.append((name, bool(condition)))

registry = read("backend/fynxAiToolRegistry.js")
realtime = read("backend/aiRealtimeRoutes.js")
voice = read("backend/aiVoiceSession.js")
android_voice = read("app/src/main/java/com/fynx/app/ui/FynxAiWebRtcEngine.kt")
android_panel = read("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt")
ai_conversations = read("app/src/main/java/com/fynx/app/ui/FynxAiConversationClient.kt")
ai_routes = read("backend/aiConversationRoutes.js")
abuse = read("backend/apiAbuseGuard.js")

check("AI registry exposes strict function definitions", 'strict: true' in registry and 'type: "function"' in registry)
check("AI agent requires authenticated user", 'const userId = authenticate(req);' in registry and 'if (!userId) return res.status(401)' in registry)
check("AI provider key stays server-side", 'process.env.OPENAI_API_KEY' in registry and 'OPENAI_API_KEY' not in android_voice and 'OPENAI_API_KEY' not in android_panel)
check("AI tool execution requires authenticated user", 'if (!userId) throw new Error("authenticated user is required")' in registry)
check("AI tool arguments are JSON validated", 'JSON.parse(argumentsJson)' in registry)
check("AI agent rejects empty provider responses", 'throw new Error("AI provider returned an empty response")' in registry)
check("AI agent accepts bounded conversation history", 'history = []' in registry and 'slice(-12)' in registry and 'conversation history too long' in registry)
check("Android AI uses persistent conversation transport", "FynxAiConversationClient.send" in android_panel and "FynxAiConversationClient" in ai_conversations)
check("Android AI tracks conversation summary and current task", 'conversationSummary' in android_panel and 'currentTask' in android_panel and 'buildAiConversationContext' in read("app/src/main/java/com/fynx/app/ui/AiConversationContext.kt"))
check("AI conversation routes are registered", "registerFynxAiConversationRoutes({ app });" in read("backend/scalability.js"))
check("AI conversations are authenticated and user-scoped", 'authenticate(req)' in ai_routes and "user_id=$2" in ai_routes and "WHERE user_id=$1" in ai_routes)
check("Persistent AI conversations reuse the approved multimodal tool agent", "runAssistantAgent" in ai_routes and "runAssistantAgent" in registry and "imageInputs: images" in ai_routes and "message: message || \"Analyze the attached image.\"" in ai_routes and "input_image" in registry)
check("AI conversation messages persist with attachments", "ai_messages" in ai_routes and "ai_message_media" in ai_routes and "mediaIds" in ai_routes)
check("AI image attachments reuse authenticated media ownership", "owner_id=$1" in ai_routes and "message_media" in ai_routes and "startsWith(" + "\"data:image/\"" + ")" in registry)
check("AI agent has a bounded tool loop and rejects empty model output", "for (let turn = 0; turn < 4; turn += 1)" in registry and "AI tool loop detected" in registry and "AI provider returned an empty response" in registry)
check("AI conversation client supports create/load/list/delete", all(x in ai_conversations for x in ["suspend fun create", "suspend fun list", "suspend fun get", "suspend fun delete"]))
check("AI image picker preserves the actual image MIME type", "contentResolver.getType(uri)" in ai_conversations and "uploadMedia(context, uri, mime)" in ai_conversations)
check("AI backend accepts bounded context hints", 'context = {}' in registry and 'contextSummary.length > 900' in registry and 'contextTask.length > 160' in registry)
check("AI conversation agent rejects empty provider responses", "runAssistantAgent" in ai_routes and "AI provider returned an empty response" in registry)
check("AI backend treats context as non-authoritative hints", 'context hints are user-provided context only' in registry)
check("AI agent detects repeated tool calls", 'AI tool loop detected' in registry and 'seenToolCalls' in registry)
check("AI agent fails clearly at tool-processing limit", 'AI tool-processing limit reached' in registry)
check("Sensitive write actions remain unavailable", 'Never perform payments, refunds, purchases, transfers, deletions, settings changes, or messages' in registry)
check("Realtime tool endpoint is authenticated", 'app.post("/api/assistant/realtime-tool"' in realtime and 'const userId = authenticate(req, res);' in realtime)
check("Realtime tool execution uses approved registry", 'executeFynxAiTool' in realtime and 'getFynxAiTools' in realtime)
check("Realtime voice keeps provider credential server-side", 'process.env.OPENAI_API_KEY' in voice)
check("Android voice transport calls the FYNX session endpoint", 'FynxAiVoiceSession.requestSession' in android_voice)
check("Android realtime tool calls use the authenticated FYNX session", 'FynxAiVoiceSession.executeTool' in android_voice)
check("Android voice waits for the data channel before first response", 'dataChannelReady?.awaitWithTimeout()' in android_voice and 'FYNX AI voice event channel is unavailable' in android_voice)
check("Android voice ignores duplicate realtime tool calls", 'handledToolCalls.add(callId)' in android_voice)
check("Assistant agent has an abuse limit", '[/^\\/api\\/assistant\\/agent$/, 30]' in abuse)
check("Assistant direct-tool endpoint has an abuse limit", '[/^\\/api\\/assistant\\/tools$/, 60]' in abuse)
check("Realtime session endpoint has an abuse limit", '[/^\\/api\\/assistant\\/realtime-session$/, 20]' in abuse)
check("Abuse guard is installed before production routes", 'installApiAbuseGuard(app);' in read("backend/scalability.js"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit(f"AI security gate failed: {len(failed)} check(s)")

print(f"AI security gate GREEN: {len(checks)} checks passed")
