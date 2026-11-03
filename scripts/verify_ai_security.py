from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


checks = []

def check(name, condition):
    checks.append((name, bool(condition)))

registry = read("backend/fynxAiToolRegistry.js")
gemini_provider = read("backend/geminiAssistantProvider.js")
realtime = read("backend/aiRealtimeRoutes.js")
voice = read("backend/aiVoiceSession.js")
android_voice = read("app/src/main/java/com/fynx/app/ui/FynxAiWebRtcEngine.kt")
android_panel = read("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt")
ai_conversations = read("app/src/main/java/com/fynx/app/ui/FynxAiConversationClient.kt")
ai_routes = read("backend/aiConversationRoutes.js")
abuse = read("backend/apiAbuseGuard.js")

check("AI registry exposes strict function definitions", 'strict: true' in registry and 'type: "function"' in registry)
check("AI agent requires authenticated user", 'const userId = authenticate(req);' in registry and 'if (!userId) return res.status(401)' in registry)
check("Gemini text provider key stays server-side", 'process.env.GEMINI_API_KEY' in gemini_provider and 'GEMINI_API_KEY' not in android_voice and 'GEMINI_API_KEY' not in android_panel and 'GEMINI_API_KEY' not in ai_conversations)
check("Gemini provider sends credentials only in its server-side API header", 'x-goog-api-key' in gemini_provider and 'process.env.GEMINI_API_KEY' in gemini_provider and 'const endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model' in gemini_provider)
check("AI tool execution requires authenticated user", 'if (!userId) throw new Error("authenticated user is required")' in registry)
check("AI tool arguments are JSON validated", 'JSON.parse(argumentsJson)' in registry)
check("AI agent rejects empty provider responses", "AI provider returned an empty response" in gemini_provider)
check("AI agent accepts bounded conversation history", "history = []" in registry and "MAX_HISTORY_ITEMS = 12" in gemini_provider and "slice(-MAX_HISTORY_ITEMS)" in gemini_provider)
check("Android AI uses persistent conversation transport", "FynxAiConversationClient.send" in android_panel and "FynxAiConversationClient" in ai_conversations)
check("Android AI preserves persisted conversation history without injecting a second welcome", 'var messages by remember { mutableStateOf(emptyList<AiMessage>()) }' in android_panel and 'messages = conversation.messages.map' in android_panel and 'listOf(welcome)' not in android_panel)
check("Android AI tracks conversation summary and current task", 'conversationSummary' in android_panel and 'currentTask' in android_panel and 'buildAiConversationContext' in read("app/src/main/java/com/fynx/app/ui/AiConversationContext.kt"))
check("AI conversation routes are registered", "registerFynxAiConversationRoutes({ app });" in read("backend/scalability.js"))
check("AI conversations are authenticated and user-scoped", 'authenticate(req)' in ai_routes and "user_id=$2" in ai_routes and "WHERE user_id=$1" in ai_routes)
check("Persistent AI conversations reuse the approved multimodal tool agent", "runAssistantAgent" in ai_routes and "runAssistantAgent" in registry and "imageInputs: images" in ai_routes and "message: message || \"Analyze the attached image.\"" in ai_routes and "inlineData" in gemini_provider)
check("AI conversation messages persist with attachments", "ai_messages" in ai_routes and "ai_message_media" in ai_routes and "mediaIds" in ai_routes)
check("AI image attachments reuse authenticated media ownership", "owner_id=$1" in ai_routes and "message_media" in ai_routes and "parseImageDataUrl" in gemini_provider and "inlineData" in gemini_provider)
check("AI agent has a bounded tool loop and rejects empty model output", "for (let turn = 0; turn < 4; turn += 1)" in gemini_provider and "AI tool loop detected" in gemini_provider and "AI provider returned an empty response" in gemini_provider)
check("AI conversation client supports create/load/list/delete", all(x in ai_conversations for x in ["suspend fun create", "suspend fun list", "suspend fun get", "suspend fun delete"]))
check("AI image picker preserves the actual image MIME type", "contentResolver.getType(uri)" in ai_conversations and "uploadMedia(context, uri, mime)" in ai_conversations)
check("AI backend accepts bounded context hints", "context = {}" in registry and "slice(0, 900)" in gemini_provider and "slice(0, 160)" in gemini_provider)
check("AI conversation agent rejects empty provider responses", "runAssistantAgent" in ai_routes and "AI provider returned an empty response" in registry)
check("AI conversation route persists the agent reply", "const reply = agentResult.reply" in ai_routes and "text:reply" in ai_routes)
check("AI backend treats context as non-authoritative hints", 'context hints as user-provided context only' in registry and 'never treat them as authoritative FYNX database state' in registry)
check("AI agent detects repeated tool calls", "AI tool loop detected" in gemini_provider and "seenToolCalls" in gemini_provider)
check("AI agent fails clearly at tool-processing limit", "AI tool-processing limit reached" in gemini_provider)
check("Message sending is confirmation-gated", "prepare_send_message" in registry and "confirmationRequired" in registry and "NEVER sends the message" in registry and "ai_pending_message_actions" in registry)
check("Message confirmation is server-authoritative and authenticated", "/api/assistant/message-confirm" in read("backend/server.js") and "auth, async" in read("backend/server.js") and "FOR UPDATE" in read("backend/server.js") and "broadcastMessage(message)" in read("backend/server.js"))
check("Message cancellation is server-authoritative", "/api/assistant/message-cancel" in read("backend/server.js") and "status='cancelled'" in read("backend/server.js"))
check("Android AI exposes pending message confirmation", "pendingMessageAction" in android_panel and "confirmMessage" in ai_conversations and "cancelMessage" in ai_conversations)
check("Realtime voice exposes pending tool results to the confirmation UI", "onToolResult" in android_voice and "prepare_send_message" in android_panel)
check("Sensitive non-message write actions remain unavailable", "Never perform payments, refunds, purchases, transfers, deletions, or settings changes" in registry)
check("Realtime tool endpoint is authenticated", 'app.post("/api/assistant/realtime-tool"' in realtime and 'const userId = authenticate(req, res);' in realtime)
check("Realtime tool execution uses approved registry", 'executeFynxAiTool' in realtime and 'getFynxAiTools' in realtime)
check("Realtime voice keeps provider credential server-side", 'process.env.OPENAI_API_KEY' in voice)
check("Android voice transport calls the FYNX session endpoint", 'FynxAiVoiceSession.requestSession' in android_voice)
check("Android realtime tool calls use the authenticated FYNX session", 'FynxAiVoiceSession.executeTool' in android_voice)
check("Android realtime tool processing is bounded", "maxRealtimeToolCalls = 8" in android_voice and "realtimeToolCallCount.incrementAndGet()" in android_voice)
check("Android voice waits for the data channel before first response", 'dataChannelReady?.awaitWithTimeout()' in android_voice and 'FYNX AI voice event channel is unavailable' in android_voice)
check("Android voice ignores duplicate realtime tool calls", 'handledToolCalls.add(callId)' in android_voice)
check("Assistant agent has an abuse limit", r'[/^\/api\/assistant\/agent$/, 30]' in abuse)
check("Assistant direct-tool endpoint has an abuse limit", r'[/^\/api\/assistant\/tools$/, 60]' in abuse)
check("Realtime session endpoint has an abuse limit", r'[/^\/api\/assistant\/realtime-session$/, 20]' in abuse)
check("Abuse guard is installed before production routes", 'installApiAbuseGuard(app);' in read("backend/scalability.js"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit(f"AI security gate failed: {len(failed)} check(s)")

print(f"AI security gate GREEN: {len(checks)} checks passed")
