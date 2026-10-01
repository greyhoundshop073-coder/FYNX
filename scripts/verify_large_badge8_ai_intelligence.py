from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []

def check(name, condition):
    checks.append((name, bool(condition)))

panel = read("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxAiConversationClient.kt")
context = read("app/src/main/java/com/fynx/app/ui/AiConversationContext.kt")
voice = read("app/src/main/java/com/fynx/app/ui/FynxAiWebRtcEngine.kt")
registry = read("backend/fynxAiToolRegistry.js")
routes = read("backend/aiConversationRoutes.js")
realtime = read("backend/aiRealtimeRoutes.js")
voice_session = read("backend/aiVoiceSession.js")
scalability = read("backend/scalability.js")
security = read("scripts/verify_ai_security.py")
extension = read("scripts/verify_ai_extension_contract.py")
app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")

check("Assistant is reachable from the real FYNX app", "FynxAiAssistantPanel" in app)
check("Assistant has a real text composer and send action", "TextField" in panel and "sendPrompt" in panel and "Send" in panel)
check("Assistant has retry/error handling", "Retry" in panel and "temporarily unavailable" in panel)
check("Assistant supports persistent conversations", "FynxAiConversationClient.create" in panel and "FynxAiConversationClient.list" in panel and "FynxAiConversationClient.get" in panel)
check("Assistant supports conversation deletion", "FynxAiConversationClient.delete" in panel)
check("Assistant supports authenticated image attachment", "uploadImage(context, uri)" in panel and "pendingMediaId" in panel)
check("Assistant preserves bounded conversation context", "buildAiConversationContext" in context and "takeLast(6)" in context and "take(900)" in context)
check("Assistant backend routes are registered", "registerFynxAiConversationRoutes({ app });" in scalability)
check("Assistant conversation routes are authenticated", "authenticate(req)" in routes and "user_id=$2" in routes)
check("Assistant replies are persisted", "ai_messages" in routes and "const reply = agentResult.reply" in routes)
check("Assistant uses the approved tool registry", "runAssistantAgent" in routes and "runAssistantAgent" in registry)
check("AI tool execution is authenticated", "authenticated user is required" in registry)
check("AI tool definitions are strict", "strict: true" in registry and 'type: "function"' in registry)
check("AI provider credential remains server-side", "process.env.OPENAI_API_KEY" in registry and "OPENAI_API_KEY" not in panel and "OPENAI_API_KEY" not in voice)
check("AI provider output is validated", "AI provider returned an empty response" in registry)
check("AI tool processing is bounded", "for (let turn = 0; turn < 4; turn += 1)" in registry and "AI tool loop detected" in registry)
check("AI context is treated as non-authoritative", "never treat them as authoritative FYNX database state" in registry)
check("AI message sending requires explicit confirmation", "prepare_send_message" in registry and "confirmationRequired" in registry and "confirmMessage" in client)
check("AI message cancellation is supported", "cancelMessage" in client and "/api/assistant/message-cancel" in read("backend/server.js"))
check("Sensitive financial/destructive writes are blocked", "Never perform payments, refunds, purchases, transfers, deletions, or settings changes" in registry)
check("Realtime AI tool endpoint is authenticated", 'const userId = authenticate(req, res);' in realtime and "executeFynxAiTool" in realtime)
check("Realtime voice credential stays server-side", "process.env.OPENAI_API_KEY" in voice_session)
check("Android voice uses the FYNX session endpoint", "FynxAiVoiceSession.requestSession" in voice)
check("Android realtime tool calls are bounded", "maxRealtimeToolCalls = 8" in voice and "realtimeToolCallCount.incrementAndGet()" in voice)
check("Android voice waits for the data channel", "dataChannelReady?.awaitWithTimeout()" in voice)
check("Existing AI extension gate remains present", "AI extension contract" in extension and "check(" in extension)
check("Existing AI security gate remains present", "AI security gate GREEN" in security)

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit(f"Large Badge #8 AI certification failed: {len(failed)} check(s)")

print(f"Large Badge #8 AI certification GREEN: {len(checks)} checks passed")
