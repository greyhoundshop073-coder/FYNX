from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path):
    return (ROOT / path).read_text(encoding="utf-8")

doc = read("docs/FYNX_AI_EXTENSION_CONTRACT.md")
registry = read("backend/fynxAiToolRegistry.js")
privacy_policy = read("backend/fynxAiPrivacyPolicy.js")
privacy_test = read("backend/test-fynx-ai-privacy-policy.mjs")
marketplace_ai_test = read("backend/test-marketplace-ai-listing-copy.mjs")
marketplace_ai_client = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceAiClient.kt")
marketplace_ai_photo_test = read("backend/test-marketplace-ai-photo-review.mjs")
marketplace_ai_photo_client = read("app/src/main/java/com/fynx/app/ui/FynxMarketplaceAiPhotoReviewClient.kt")
marketplace_panel = read("app/src/main/java/com/fynx/app/ui/FynxMarketplacePanel.kt")
gemini_provider = read("backend/geminiAssistantProvider.js")
provider_test = read("backend/test-gemini-assistant-provider.mjs")
conversation = read("backend/aiConversationRoutes.js")
realtime = read("backend/aiRealtimeRoutes.js")
assistant_parser = read("backend/assistantJsonBodyParser.js")
assistant_parser_test = read("backend/test-assistant-json-body-parser.mjs")
voice = read("backend/aiVoiceSession.js")
android_client = read("app/src/main/java/com/fynx/app/ui/FynxAiConversationClient.kt")
android_voice = read("app/src/main/java/com/fynx/app/ui/FynxAiVoiceSession.kt")
android_panel = read("app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt")
backend_client = read("app/src/main/java/com/fynx/app/ui/FynxBackendClient.kt")

checks = [
    ("mandatory privacy boundary documents protected data", "wallet balances" in doc and "private FYNX chat messages" in doc and "KYC" in doc and "saved-post history" in doc),
    ("privacy policy is centralized and enforced by the registry", "assertFynxAiToolAllowed(name);" in registry and "FYNX_AI_PRIVACY_INSTRUCTIONS" in registry and "FYNX_AI_PRIVACY_INSTRUCTIONS" in privacy_policy),
    ("private-data tools are absent from the tool registry", all(f'name: "{name}"' not in registry for name in ["get_my_profile", "get_conversation", "get_my_friends", "get_my_saved_posts"])),
    ("private tool rejection is covered by regression tests", "databaseCalls, 0" in privacy_test and "realtime voice must use the same reviewed public-data tool set" in privacy_test),
    ("AI profile search respects Everyone visibility", "LEFT JOIN privacy_settings ps" in registry and "COALESCE(ps.profile_visibility" in registry and "'Everyone'" in registry),
    ("AI recommendations do not use private social graphs", "get_people_recommendations" in registry and "FROM friendships" not in registry and "JOIN friendships" not in registry and "FROM social_follows" not in registry and "JOIN social_follows" not in registry),
    ("AI message preparation respects profile and messaging visibility", "COALESCE(ps.profile_visibility" in registry and "COALESCE(ps.messages_visibility" in registry and "messages_visibility" in registry),
    ("Marketplace seller copy is an authenticated text-only AI capability", 'app.post("/api/assistant/marketplace/listing-copy"' in registry and "const userId = authenticate(req);" in registry and "toolDefinitions: []" in registry),
    ("Marketplace seller copy uses only current product-form facts", "input.title" in registry and "input.category" in registry and "input.condition" in registry and "input.specifications" in registry and "input.wallet" not in registry and "input.location" not in registry),
    ("Marketplace seller copy redacts contacts, precise addresses and credentials", all(term in registry for term in ["[contact details omitted]", "[address omitted]", "[private details omitted]"]) and "plot addresses must be removed" in marketplace_ai_test and "sanitizedReply.description" in marketplace_ai_test and "parseMarketplaceAiListingCopyReply" in marketplace_ai_test),
    ("Marketplace seller copy regression tests cover privacy and invalid outputs", "seller copy must not have database or action tools" in marketplace_ai_test and "price must not be sent to Gemini" in marketplace_ai_test and "email addresses must be removed before provider context" in marketplace_ai_test),
    ("Android seller copy helper uses the central authenticated transport", "FynxBackendClient.postJson" in marketplace_ai_client and "/api/assistant/marketplace/listing-copy" in marketplace_ai_client),
    ("Seller must review and apply AI copy before publishing", "Review your AI listing draft" in marketplace_panel and "Use suggestion" in marketplace_panel and "listingCopyBusy" in marketplace_panel),
    ("Seller AI drafts are discarded when the product form changes", "latestListingCopyInputs.value == requestedInputSnapshot" in marketplace_panel and "Please generate a fresh suggestion" in marketplace_panel),
    ("AI copy does not bypass the existing Marketplace publish flow", "FynxRemoteSocialClient.createMarketplaceListing" in marketplace_panel and "listingCopySuggestion" in marketplace_panel),

    ("Marketplace photo review requires seller consent", "Review product photos with FYNX AI?" in marketplace_panel and "Review photos" in marketplace_panel),
    ("Marketplace photo review uses temporary bounded image inputs", 'app.post("/api/assistant/marketplace/photo-review"' in registry and "dataBase64" in registry and "MARKETPLACE_AI_PHOTO_REVIEW_MAX_TOTAL_BYTES = 9 * 1024 * 1024" in registry),
    ("Marketplace photo review is authenticated without private tools/history", 'const userId = authenticate(req);' in registry and "history: []" in registry and "context: {}" in registry and "toolDefinitions: []" in registry),
    ("Marketplace photo review blocks OCR and people/product inference", all(term in registry for term in ["Do not read, transcribe, interpret or summarize text", "Do not identify or describe the product or any person", "Never edit, save, publish"])),
    ("Marketplace photo review results use allowlisted issue codes and fixed advice", "MARKETPLACE_AI_PHOTO_REVIEW_ISSUES" in registry and "issueAdvice" in marketplace_ai_photo_client and "Photo check results" in marketplace_panel),
    ("Marketplace photo review client compresses locally and avoids media-storage uploads", "BitmapFactory" in marketplace_ai_photo_client and "FynxBackendClient.postJson" in marketplace_ai_photo_client and "FynxProductionMessaging.uploadMedia" not in marketplace_ai_photo_client),
    ("Marketplace photo review has backend regression tests", "authenticated temporary inputs" in marketplace_ai_photo_test and "not base64!" in marketplace_ai_photo_test and "parseMarketplaceAiPhotoReviewReply" in registry),
    ("extension contract exists", "FYNX AI Extension Contract" in doc),
    ("assistant JSON parser is strict and bounded", 'express.json({' in assistant_parser and 'limit: "18mb"' in assistant_parser and 'strict: true' in assistant_parser),
    ("persistent conversation routes parse JSON before handlers", 'app.post("/api/assistant/conversations", assistantJsonBodyParser,' in conversation and 'app.post("/api/assistant/conversations/:id/message", assistantJsonBodyParser,' in conversation),
    ("assistant agent and tools routes parse JSON before handlers", 'app.post("/api/assistant/agent", assistantJsonBodyParser,' in registry and 'app.post("/api/assistant/tools", assistantJsonBodyParser,' in registry),
    ("realtime assistant routes parse JSON before handlers", 'app.post("/api/assistant/realtime-session", assistantJsonBodyParser,' in realtime and 'app.post("/api/assistant/realtime-tool", assistantJsonBodyParser,' in realtime),
    ("assistant parser regression covers all six JSON routes", "jsonRoutes" in assistant_parser_test and "parser-probe" in assistant_parser_test),

    ("registry remains the approved capability seam", "export function getFynxAiTools()" in registry and "executeFynxAiTool" in registry),
    ("text AI uses the existing conversation route", "/api/assistant/conversations/:id/message" in conversation and "runAssistantAgent" in conversation),
    ("realtime AI uses the existing authenticated session route", "/api/assistant/realtime-session" in realtime and "authenticate(req, res)" in realtime),
    ("realtime tools use the same approved registry", "getFynxAiTools" in realtime and "executeFynxAiTool" in realtime),
    ("Gemini text provider credentials stay server-side", "process.env.GEMINI_API_KEY" in gemini_provider and "GEMINI_API_KEY" not in android_client and "GEMINI_API_KEY" not in android_panel),
    ("Gemini text requests use the official server-side API-key header", "x-goog-api-key" in gemini_provider and "generativelanguage.googleapis.com" in gemini_provider),
    ("text assistant delegates to the Gemini provider adapter", "runGeminiAssistant" in registry and "geminiAssistantProvider.js" in registry),
    ("voice provider credentials stay server-side", "process.env.OPENAI_API_KEY" in voice and "OPENAI_API_KEY" not in android_voice and "OPENAI_API_KEY" not in android_panel),
    ("Android text AI stays on FYNX backend transport", "FynxBackendClient.get" in android_client and "FynxBackendClient.postJson" in android_client),
    ("Android realtime session stays on FYNX backend transport", "FynxBackendClient.postJson" in android_voice),
    ("central HTTPS transport remains the Android network boundary", "PRODUCTION_BASE_URL = \"https://fynx-backend-testing-testing.up.railway.app\"" in backend_client and "startsWith(\"https://\")" in backend_client),
    ("AI panel remains presentation layer", "FynxAiConversationClient" in android_panel and "FynxAiWebRtcEngine" in android_panel),
    ("future sensitive writes remain confirmation-gated", "confirmationRequired" in registry and "NEVER sends the message" in registry),
    ("future financial/destructive writes remain blocked by policy", "Never perform payments, refunds, purchases, transfers, deletions, or settings changes" in registry),
    ("AI capability additions cannot silently bypass authentication", "authenticate(req)" in conversation and "authenticate(req, res)" in realtime and "authenticated user is required" in registry),
    ("Gemini streaming uses the server-side streamGenerateContent SSE endpoint", ":streamGenerateContent?alt=sse" in gemini_provider and "onTextChunk" in gemini_provider),
    ("Gemini thought-only parts are excluded from user-visible output", "part.thought !== true" in gemini_provider and "thought !== true" in gemini_provider),
    ("Gemini streaming tests cover real incremental chunks and thought filtering", "ReadableStream" in provider_test and "testStreamingTextResponse" in provider_test and "internal thought" in provider_test and "Hello from FYNX." in provider_test),
    ("AI conversation route negotiates SSE without changing the default JSON contract", 'req.headers?.accept' in conversation and "text/event-stream" in conversation and 'type:"delta"' in conversation and 'type:"complete"' in conversation and 'type:"error"' in conversation and "return res.json(responsePayload)" in conversation),
    ("AI route persists the completed assistant reply before its stream completion event", conversation.index('await db.query("COMMIT")') < conversation.index('type:"complete"') and "persist_assistant_reply" in conversation),
    ("Android central transport adds authenticated SSE support without a direct Gemini client", "postJsonStream" in backend_client and 'setRequestProperty("Accept", "text/event-stream")' in backend_client and 'setRequestProperty("Authorization", "Bearer $token")' in backend_client and "executeStreamingRequest" in backend_client),
    ("Android stream transport falls back to bounded JSON for older backends", "Older backends may ignore Accept: text/event-stream" in backend_client and "MAX_RESPONSE_BYTES" in backend_client and "connection.contentType" in backend_client),
    ("Android conversation client keeps legacy JSON AI responses usable", "if (!response.has(\"assistantMessage\"))" in android_client and "response.optString(\"type\") == \"complete\"" in android_client),
    ("Android conversation client parses delta and completion events through FYNX transport", "FynxBackendClient.postJsonStream" in android_client and "suspend fun sendStreaming" in android_client and '"delta"' in android_client and '"complete"' in android_client),
    ("Android AI panel renders streamed deltas and restores failed prompts for retry", "FynxAiConversationClient.sendStreaming" in android_panel and "withContext(Dispatchers.Main)" in android_panel and "failedPrompt = prompt" in android_panel),
    ("AI extension contract documents optional SSE transport and JSON compatibility", "Server-Sent Events (SSE)" in doc and "streamGenerateContent" in doc and "default request/JSON response contract remains compatible" in doc),
]

failed = [name for name, ok in checks if not ok]
for name, ok in checks:
    print(f"{'PASS' if ok else 'FAIL'}: {name}")
if failed:
    raise SystemExit(f"AI extension contract failed: {len(failed)} check(s)")
print(f"AI extension contract GREEN: {len(checks)} checks passed")
