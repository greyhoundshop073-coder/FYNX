# FYNX AI Assistant — Engineering Handoff (3 November 2026)

## Purpose and ownership boundary

This document is the handoff record for upgrading the existing FYNX AI Assistant toward a more capable, ChatGPT-like assistant **without rebuilding it or disturbing other active workstreams**.

This workstream is limited to the AI Assistant: Android assistant panel/client/context, authenticated assistant conversation routes, Gemini text provider, shared FYNX AI tool registry, realtime voice integration, AI-specific tests/docs, and the minimum media/file plumbing needed for assistant attachments. Do not modify camera, Status, Marketplace, Groups, general chat, or unrelated backend features unless a verified AI dependency requires it and the change is isolated.

**Status at handoff creation:** investigation completed; implementation has not yet been committed. A dedicated branch has been created from the canonical integration branch. This document records the observed architecture and the next implementation sequence. It is not evidence that the listed upgrades are already implemented or runtime-certified.

## Repository and branch baseline

- Repository: `greyhoundshop073-coder/FYNX`
- Base branch: `integration/fynx-single-apk-20261013`
- Base commit inspected: `def609c094af7d515045255c51adee27d1b73895`
- Isolated work branch: `feat/fynx-ai-context-grounding-20261103`
- Branch was created from the base commit above.
- No AI code changes, build, tests, or push of implementation have been completed as of this document.
- Latest commit status lookup returned no status entries and no workflow runs; do not interpret an empty result as a successful build.

Before continuing, fetch the latest base and branch heads and inspect open PRs/commits. Do not assume this snapshot is still current. If the canonical branch advanced, rebase/merge carefully and preserve changes from other chats.

## Existing architecture — how the assistant currently works

### Text conversation path

1. Android UI: `app/src/main/java/com/fynx/app/ui/FynxAiAssistantPanel.kt`
   - Compose assistant screen, conversation list/history, current messages, prompt composer, image picker, pending action confirmation UI, and voice controls.
   - Uses `FynxAiConversationClient` for persisted text conversations.
   - Has `conversationSummary` and `currentTask` state, but the normal persisted send path does not pass those values to the backend.
2. Android transport: `app/src/main/java/com/fynx/app/ui/FynxAiConversationClient.kt`
   - Creates, lists, loads and deletes conversations.
   - Sends turns to `/api/assistant/conversations/{id}/message` with `message` and `mediaIds`.
   - Supports cancellation and confirmation endpoints for pending message actions.
   - Image upload reuses the existing authenticated FYNX media upload path.
3. Authenticated backend persistence: `backend/aiConversationRoutes.js`
   - Checks authentication and conversation ownership.
   - Persists user and assistant messages in `ai_messages`; associates uploads through `ai_message_media`.
   - Validates that attached image media belongs to the requesting user.
   - Calls the shared `runAssistantAgent` rather than using a separate assistant implementation.
4. Shared capability boundary: `backend/fynxAiToolRegistry.js`
   - Defines strict function schemas and executes approved tools against authenticated FYNX data.
   - Existing tools include profile, conversation, user/friend discovery, recommendations, Marketplace search, trending posts, saved posts, and confirmation-gated message preparation (verify the complete live registry before adding or changing tools).
   - Tool execution requires an authenticated user. Sensitive writes are constrained; sending a message requires a separate server-authoritative confirmation.
5. Text provider: `backend/geminiAssistantProvider.js`
   - Calls Gemini from the backend using `GEMINI_API_KEY`; credentials must remain server-side.
   - Maintains a bounded history (`MAX_HISTORY_ITEMS = 12`), bounded tool loop (four turns), duplicate tool-call protection and empty-response checks.
   - Supports image inputs through Gemini inline data.
6. Route registration and abuse controls:
   - Conversation routes are registered from `backend/scalability.js`.
   - `backend/apiAbuseGuard.js` applies rate limits to assistant endpoints.
   - Keep route registration, authentication, body-size limits and abuse protection intact.

### Realtime voice path

- Android voice engine: `app/src/main/java/com/fynx/app/ui/FynxAiWebRtcEngine.kt`
- Android voice session client: `app/src/main/java/com/fynx/app/ui/FynxAiVoiceSession.kt`
- Backend routes/session: `backend/aiRealtimeRoutes.js` and `backend/aiVoiceSession.js`
- Voice uses an OpenAI realtime session created by the backend; provider credentials remain server-side.
- Voice tool execution uses the same approved FYNX tool registry as text.
- Existing protections include authenticated sessions, bounded tool calls, duplicate call protection and confirmation handling.
- Do not fork a second tool registry for voice. Any new safe capability should be exposed through the shared registry and tested in both text and voice where supported.

### Existing media upload constraints

- Android's shared upload implementation is in `FynxProductionMessaging.uploadMedia`.
- It already supports a range of image, video, audio and office/document MIME types for general messaging.
- The backend `/api/media` upload route in `backend/server.js` currently has a narrower MIME allowlist (image/video/audio) and a 12 MB cap.
- The assistant conversation route currently parses image inputs, not arbitrary documents.
- Therefore PDF/document assistant support is **not** a UI-only change. It requires a deliberate, bounded backend media policy, safe extraction/provider file-input strategy, user ownership checks, limits, cleanup/retention behavior and tests. Do not widen the global media route casually; prefer a scoped, explicit assistant document path if appropriate.

## Confirmed gaps to address

### 1. Context continuity is incomplete

- The Android panel tracks summary/task state, and `AiConversationContext.kt` contains `buildAiConversationContext`.
- The persistent conversation send call currently sends only the current prompt and media IDs.
- The persistent conversation route calls `runAssistantAgent` without meaningful context hints (default empty context).
- The Gemini provider has only the latest bounded message history. It does not implement durable user-approved long-term memory.
- Result: old conversation details can fall outside the 12-message window, and the context helper/state does not currently improve normal persisted turns.

**Implementation direction**
- First wire a compact, bounded conversation summary/current-task context through the existing Android client → authenticated conversation route → `runAssistantAgent` → provider prompt path.
- Prefer server-derived summaries from persisted conversation history over trusting arbitrary client claims. If client context is accepted, treat it only as an untrusted hint, label it as such, cap length, and never let it override authenticated FYNX database state.
- Add explicit tests that prove context is scoped to the correct conversation/user and cannot cross accounts.
- Treat long-term memory as a separate opt-in/transparent feature: define what is stored, how users inspect/edit/clear it, retention, and consent before implementing durable profile memory. Do not silently infer or store sensitive user attributes.

### 2. Web research and sourced answers are missing

- No web-search/retrieval tool was found in the inspected shared registry.
- Add a backend-only search/retrieval adapter as an approved registry tool, with citations/source metadata returned to Android.
- Apply strict timeouts, result count and content limits, source allowlists/quality rules where possible, prompt-injection isolation (web content is untrusted data), and rate/cost controls.
- The assistant should distinguish sourced facts from its own reasoning and show clickable source references in the Android response.
- Never expose search-provider secrets in the APK.

### 3. File and document understanding is image-only in the assistant

- Existing general messaging upload code supports more MIME types than the assistant endpoint.
- Add PDF/document support only after designing the end-to-end flow: picker (document MIME types), upload allowlist and size cap, ownership verification, safe parsing/provider file input, unsupported/corrupt file errors, and attachment display.
- Do not assume Gemini's provider file support can accept FYNX media IDs directly. The backend must securely resolve the owned file bytes or use an approved provider upload flow and enforce cleanup/retention.
- Test PDF, text, office formats if explicitly supported, oversized files, malformed files, MIME spoofing and cross-user media IDs.

### 4. Response experience and streaming need review

- Current assistant messages are persisted and displayed as plain text bubbles with copy/share actions.
- Review streaming support against the existing route/transport before adding it. Streaming must preserve persisted final messages, cancellation semantics, error recovery, and bounded output.
- Rich text/Markdown and source links can be added in the existing message presentation layer; do not introduce an unrelated UI framework or break copy/share/accessibility.

### 5. Text/voice capability parity needs verification

- Text and voice share tools but use different provider adapters (Gemini text, OpenAI realtime voice).
- Test each tool's schema and execution in both modes. Where voice cannot present a confirmation or rich result safely, it must ask the user to continue in text rather than bypass the guard.
- Preserve current voice session security, bounded call counts and server-side credential handling.

## Recommended delivery order

1. **Baseline and isolate:** refresh branch/base/PR/CI state; inspect current diffs; confirm no concurrent AI changes. Keep this branch AI-only.
2. **Context continuity:** connect the existing context helper to persisted sends, make summary generation bounded and server-grounded, and add user/conversation isolation tests.
3. **Sourced web research:** implement the server-side search adapter through the shared registry; return structured sources and render links in Android.
4. **Document understanding:** design and implement a scoped PDF/document upload and provider-input path with strict limits and ownership enforcement.
5. **Response quality:** improve Markdown/source rendering and evaluate streaming without regressing persistence/cancel/retry.
6. **Voice parity:** add new approved tools to realtime voice where supported; verify confirmation and tool bounds.
7. **Full verification:** run focused Node/Python AI checks, Android compile/unit tests, relevant backend tests, then full CI. Inspect logs and fix failures before requesting review.

Do not bundle unrelated product changes into this branch. Prefer one coherent AI feature slice per reviewed commit rather than speculative broad refactors.

## Existing verification gates and useful files

- `scripts/verify_ai_security.py` — authentication, server-side secrets, bounded history/tool loops, media ownership, confirmation gates, voice protections and abuse limits.
- `scripts/verify_ai_extension_contract.py` — verifies the existing extension architecture and approved capability seams.
- `scripts/verify_large_badge8_ai_intelligence.py` — additional AI capability/integration checks; inspect its current expectations before changing the AI contract.
- `backend/test-gemini-assistant-provider.mjs` — provider behavior tests.
- `backend/test-assistant-json-body-parser.mjs` — assistant JSON route parser regression coverage.
- `.github/workflows/android-build.yml` and `.github/workflows/fynx-backend-ci.yml` — CI gates; inspect current workflow definitions before relying on them.
- `docs/FYNX_AI_EXTENSION_CONTRACT.md` — existing architecture contract; update it when the implementation contract changes.

Run the actual repository commands from the workflow/package scripts; do not report a test as passed unless it was run and its exit status/output was checked. Static regex verifiers are useful guards but do not replace runtime/provider integration tests.

## Non-negotiable safety and compatibility rules

- Never put Gemini/OpenAI/search-provider API keys in Android source, BuildConfig, resources or APK assets.
- Every conversation, media, memory and tool read/write must be authenticated and scoped to the requesting user.
- Treat uploaded files, web pages and client-provided context as untrusted input.
- Preserve the existing server-authoritative confirmation flow for sending messages. Do not add payments, purchases, transfers, deletions or settings changes as autonomous actions.
- Keep tool schemas strict, arguments validated, tool loops/calls bounded, duplicate calls rejected, and API endpoints rate-limited.
- Keep the existing conversation persistence and ownership model; no parallel AI conversation system.
- Keep the shared tool registry as the capability seam for text and voice.
- Do not alter unrelated chat workstreams or merge another chat's unreviewed changes.
- Do not claim the assistant is equivalent to ChatGPT or fully certified until the capabilities are implemented and verified end-to-end.

## Handoff checklist for the next chat/engineer

- [ ] Fetch latest base and branch heads; inspect all AI-related commits and open PRs.
- [ ] Confirm this branch is still based on the intended integration head; preserve concurrent work.
- [ ] Read this document and `docs/FYNX_AI_EXTENSION_CONTRACT.md`.
- [ ] Trace the exact current Android → backend → provider path before editing.
- [ ] Implement context continuity first with tests.
- [ ] Add web research/citations through the shared tool registry.
- [ ] Design safe document ingestion before enabling PDFs.
- [ ] Verify text and voice parity, confirmation gates and account isolation.
- [ ] Run all relevant AI/backend/Android checks and monitor CI to completion.
- [ ] Record commit SHA, changed files, test results, CI run URL/status and any remaining runtime limitations in this document or the PR.
