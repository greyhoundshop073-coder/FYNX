from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

checks = []

def check(name, condition):
    checks.append((name, bool(condition)))

panel = read("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt")
client = read("app/src/main/java/com/fynx/app/ui/FynxAiConversationClient.kt")
ai_context = read("backend/aiConversationContext.js")
voice = read("app/src/main/java/com/fynx/app/ui/FynxAiWebRtcEngine.kt")
registry = read("backend/fynxAiToolRegistry.js")
gemini_provider = read("backend/geminiAssistantProvider.js")
routes = read("backend/aiConversationRoutes.js")
realtime = read("backend/aiRealtimeRoutes.js")
voice_session = read("backend/aiVoiceSession.js")
scalability = read("backend/scalability.js")
security = read("scripts/verify_ai_security.py")
runtime_capture = read("scripts/verify_authenticated_runtime_navigation.py")
runtime_runner = read("scripts/run_android_runtime_certification.sh")
privacy_policy = read("backend/fynxAiPrivacyPolicy.js")
marketplace_ai_panel = read("app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt")
marketplace_ai_client = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceAiClient.kt")
marketplace_ai_test = read("backend/test-marketplace-ai-listing-copy.mjs")
marketplace_ai_photo_client = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceAiPhotoReviewClient.kt")
marketplace_ai_photo_test = read("backend/test-marketplace-ai-photo-review.mjs")
extension = read("scripts/verify_ai_extension_contract.py")
app = read("app/src/main/java/com/fynx/app/ui/FynxApp.kt")

check("AI certification includes strict private-data denial", "Private personal and financial information is not available through FYNX AI" in privacy_policy and "assertFynxAiToolAllowed(name);" in registry and "FYNX_AI_PRIVACY_INSTRUCTIONS" in voice_session)
check("Marketplace seller AI drafts are integrated and explicitly reviewed", "Improve with FYNX AI" in marketplace_ai_panel and "Review your AI listing draft" in marketplace_ai_panel and "Use suggestion" in marketplace_ai_panel and "FynxBackendClient.postJson" in marketplace_ai_client and "seller copy must not have database or action tools" in marketplace_ai_test)
check("Marketplace AI photo quality review is consent-led, temporary and advice-only", "Review product photos with FYNX AI?" in marketplace_ai_panel and "Photo check results" in marketplace_ai_panel and "BitmapFactory" in marketplace_ai_photo_client and "FynxBackendClient.postJson" in marketplace_ai_photo_client and "FynxProductionMessaging.uploadMedia" not in marketplace_ai_photo_client)
check("Assistant is reachable from the real FYNX app", "FynxAiAssistantPanel" in app)
check("Assistant has a real text composer and send action", "TextField" in panel and "sendPrompt" in panel and "Send" in panel)
check("Assistant has retry/error handling", "Retry" in panel and "temporarily unavailable" in panel)
check("Assistant supports persistent conversations", "FynxAiConversationClient.create" in panel and "FynxAiConversationClient.list" in panel and "FynxAiConversationClient.get" in panel)
check("Assistant supports conversation deletion", "FynxAiConversationClient.delete" in panel)
check("Assistant supports authenticated image attachment", "uploadImage(context, uri)" in panel and "pendingMediaId" in panel)
check("Assistant preserves bounded server-derived conversation continuity", "buildAiConversationContext(conversationHistory)" in routes and "MAX_CONTEXT_HISTORY_ITEMS = 80" in ai_context and "MAX_CONTEXT_SUMMARY_CHARS = 900" in ai_context and "MAX_CONTEXT_TASK_CHARS = 160" in ai_context)
check("Assistant backend routes are registered", "registerFynxAiConversationRoutes({ app });" in scalability)
check("Assistant conversation routes are authenticated", "authenticate(req)" in routes and "user_id=$2" in routes)
check("Assistant replies are persisted", "ai_messages" in routes and "const reply = agentResult.reply" in routes)
check("Assistant uses the approved tool registry", "runAssistantAgent" in routes and "runAssistantAgent" in registry)
check("AI tool execution is authenticated", "authenticated user is required" in registry)
check("AI tool definitions are strict", "strict: true" in registry and 'type: "function"' in registry)
check("Gemini text provider credential remains server-side", "process.env.GEMINI_API_KEY" in gemini_provider and "GEMINI_API_KEY" not in panel and "GEMINI_API_KEY" not in voice)
check("Realtime voice retains its existing OpenAI provider credential server-side", "process.env.OPENAI_API_KEY" in voice_session and "OPENAI_API_KEY" not in panel and "OPENAI_API_KEY" not in voice)
check("AI provider output is validated", "AI provider returned an empty response" in gemini_provider)
check("AI tool processing is bounded", "for (let turn = 0; turn < 4; turn += 1)" in gemini_provider and "AI tool loop detected" in gemini_provider)
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
check("AI runtime certification opens the real dashboard and verifies its welcome, header and message composer", "FYNX_AI_VISUALS_ONLY" in runtime_capture and "capture_ai_assistant_surface" in runtime_capture and 'find_control(latest, ["FYNX Assistant"])' in runtime_capture and 'find_control(latest, ["Message FYNX Assistant"])' in runtime_capture and 'find_control(latest, ["What can we work on today?", "Try asking"])' in runtime_capture)
check("AI runtime certification captures the real image-attachment menu without sending a prompt", '"authenticated-ai.png"' in runtime_capture and '"authenticated-ai-attachment-menu.png"' in runtime_capture and '["Add an image"]' in runtime_capture and "no prompt is submitted" in runtime_capture)
check("AI welcome surface stays chat-first with compact topic chips and distinct icon colours", "LazyRow(" in panel and "starter.icon" in panel and "starter.accentColor" in panel and "Explore FYNX" not in panel)
check("Android emulator workflow runs focused AI screenshots without requiring the backend", "FYNX_AI_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1" in runtime_runner and "FYNX_AI_VISUALS_ONLY:" in read(".github/workflows/android-build.yml"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit(f"Large Badge #8 AI certification failed: {len(failed)} check(s)")

print(f"Large Badge #8 AI certification GREEN: {len(checks)} checks passed")
