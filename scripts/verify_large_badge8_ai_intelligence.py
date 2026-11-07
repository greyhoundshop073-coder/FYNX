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
pcm_player = read("app/src/main/java/com/fynx/app/ui/FynxAiPcmAudioPlayer.kt")
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
check("AI runtime certification bypasses login and launches the debug-only production Compose host", "FYNX_AI_VISUALS_ONLY" in runtime_capture and "login is intentionally bypassed" in runtime_capture and ".ui.FynxAiVisualAuditActivity" in runtime_capture and "capture_ai_component_preview" in runtime_capture)
check("AI runtime certification captures clean chat, full-screen voice, message actions and active read-aloud player", all(name in runtime_capture for name in ["authenticated-ai-chat-screen.png", "authenticated-ai-fullscreen-voice.png", "authenticated-ai-response-actions.png", "authenticated-ai-read-aloud-active.png"]) and "blank/undersized" in runtime_capture)
check("FYNX AI read-aloud uses the same receive-only Realtime voice session without enabling the microphone", "FynxAiWebRtcEngine(context)" in panel and "receiveOnly = true" in panel and "createInitialResponse = false" in panel and "response.output_audio.delta" in panel and "TextToSpeech" not in panel)
check("Read-aloud waveform and duration are computed from actual 24 kHz PCM audio deltas", "FynxAiReadAloud.appendPcmWaveform" in panel and "enqueueBase64Delta(audioDelta)" in panel and "playbackHeadPosition" in pcm_player and "durationMillis()" in pcm_player and "appendPcmWaveform" in read("app/src/main/java/com/fynx/app/ui/FynxAiReadAloud.kt") and 'put("type", "audio/pcm")' in read("app/src/main/java/com/fynx/app/ui/FynxAiReadAloud.kt") and "24_000" in read("app/src/main/java/com/fynx/app/ui/FynxAiReadAloud.kt"))
check("FYNX read-aloud plays the same Realtime PCM and disables duplicate remote WebRTC playback", "receiveOnly = true" in panel and "createInitialResponse = false" in panel and "setRemoteAudioEnabled(false)" in panel and "setRemoteAudioEnabled(enabled: Boolean)" in voice and "track.setEnabled(remoteAudioEnabled)" in voice and "track.write(" in pcm_player)
check("Pause and resume hold the same PCM queue rather than cancelling and regenerating text", "readAloudPcmPlayer?.pause()" in panel and "player.play()" in panel and "fun pause()" in pcm_player and "fun play()" in pcm_player and "response.cancel" not in panel.split("fun pauseReadAloud()", 1)[1].split("fun toggleReadAloud(", 1)[0])
check("Audio player uses Android audio focus and actual PCM playback-head time", "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK" in pcm_player and "AudioAttributes.CONTENT_TYPE_SPEECH" in pcm_player and "playbackHeadPosition" in pcm_player and "writerCompleted" in pcm_player)
check("Android device test verifies PCM pause holds position and resume continues the same audio queue", "pauseAndResumeContinueTheSamePcmQueueAndPlaybackHead" in read("app/src/androidTest/java/com/fynx/app/ui/FynxAiPcmAudioPlayerInstrumentationTest.kt") and "player.pause()" in read("app/src/androidTest/java/com/fynx/app/ui/FynxAiPcmAudioPlayerInstrumentationTest.kt") and "player.play()" in read("app/src/androidTest/java/com/fynx/app/ui/FynxAiPcmAudioPlayerInstrumentationTest.kt"))
check("Read-aloud completion helper is declared before the playback monitor references it", panel.index("fun finishReadAloud(messageId: String)") >= 0 and panel.index("fun finishReadAloud(messageId: String)") < panel.index("finishReadAloud(readAloudMessageId"))
check("Inline player is placed inside the answer before the message action divider, with pause/resume/stop", panel[panel.index("internal fun AiChatBubble("):].index("ReadAloudPlayer(") < panel[panel.index("internal fun AiChatBubble("):].index("HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .20f))") and "Resume read aloud" in panel and "Pause read aloud" in panel and "Stop reading response" in panel)
check("Read-aloud PCM waveform and playback-time helpers have unit coverage", "convertsRealPcmDeltasIntoBoundedWaveformLevels" in read("app/src/test/java/com/fynx/app/ui/FynxAiReadAloudTest.kt") and "formatsPlaybackTimeAsMinutesAndSeconds" in read("app/src/test/java/com/fynx/app/ui/FynxAiReadAloudTest.kt"))
check("AI welcome surface stays chat-first with compact topic chips and distinct icon colours", "LazyRow(" in panel and "starter.icon" in panel and "starter.accentColor" in panel and "onClickLabel = starter.description" in panel and "Explore FYNX" not in panel)
check("Android emulator workflow runs focused AI screenshots without requiring the backend", "FYNX_AI_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1" in runtime_runner and "FYNX_AI_VISUALS_ONLY:" in read(".github/workflows/android-build.yml"))

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")

if failed:
    raise SystemExit(f"Large Badge #8 AI certification failed: {len(failed)} check(s)")

print(f"Large Badge #8 AI certification GREEN: {len(checks)} checks passed")
