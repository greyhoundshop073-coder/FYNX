# FYNX AI and Voice — Stage 4 Audit Report

**Project:** greyhoundshop073-coder/FYNX  
**Canonical Android integration branch:** integration/fynx-single-apk-20261013  
**Audit base commit:** 4382023be9dfd51d43728231504ce8fe9950e125 (26 November 2026)  
**Feature branch:** feat/fynx-ai-usage-budget-20261126  
**PR:** #611 — https://github.com/greyhoundshop073-coder/FYNX/pull/611 (open, unmerged)  
**Tested code commit:** 8856ea4584c0437298082be1353796b2e16ea50d  
**Stage status:** CONDITIONAL — BLOCKED
 
## Current verification update — 28 November 2026

This update supersedes the older endpoint and Realtime lifecycle notes below where they conflict.

- **Canonical endpoint:** PR [#621](https://github.com/greyhoundshop073-coder/FYNX/pull/621) was merged into `integration/fynx-single-apk-20261013` at merge commit `50f42b8d97486530f218ca4f7b7096a5750a4017`. Android production/debug defaults and legacy Railway/Render migration now target the active Neon Function. Explicit custom HTTPS configuration remains supported. Exact-head Android Build, Backend CI, Chat Final Closure and Chat Group Parity all passed before merge. This is not proof that every authenticated API journey or media transfer is live-certified.
- **Realtime call identity and lifecycle:** PR #611 now parses the provider call ID from the OpenAI response `Location` header, binds it to an authenticated PostgreSQL usage reservation, opens the server-side sideband stream before returning the SDP answer, and uses `response.done` usage events. The ledger records input/output and audio/text/cached token classes, provider/model summaries, and estimated cost. It enforces one active voice session per user, expires orphaned reservations older than two hours, rejects unsupported Realtime pricing models and refuses to treat incomplete token detail as zero-cost success.
- **Authenticated hangup:** the backend exposes an account-scoped Realtime hangup endpoint. The Android WebRTC engine invokes it when closing the voice engine, while local media is released immediately. The Android close flag is atomic to avoid duplicate provider hangup requests. A cold serverless instance leaves the reservation pending for the instance owning the usage sideband rather than falsely settling zero usage.
- **Regression coverage:** the feature branch adds tests for provider call-ID parsing, endpoint authorization, Realtime event aggregation/deduplication, cached token pricing, active-session conflicts, call-to-reservation binding, cross-instance hangup behavior, and single-run failure cleanup. The AI extension contract now asserts the active Neon endpoint and the new voice metering lifecycle.
- **Exact-head CI boundary:** latest reviewed PR #611 source head is `0dd7077679e1390919a07d7f5727e3fbc391ad89`. The prior successful AI Backend CI result predates these latest Realtime lifecycle additions; no exact-head workflow result had appeared for this newest head at the time this update was written. The current Android/calls emulator run is separate and does not certify AI voice.
- **Live-provider boundary:** the active Neon Function's environment-name inventory includes `GEMINI_API_KEY` but not `OPENAI_API_KEY`. Secret values were not read or modified. Gemini project validity/quota and an authenticated Gemini request remain unverified. OpenAI voice cannot be live-certified until the owner configures a valid server-side `OPENAI_API_KEY`. Optional user/owner monthly spending thresholds remain configurable; no owner budget amount has been assumed.
- **Remaining certification:** exact-head PR #611 CI; authenticated Gemini conversation/attachment flow against Neon; real OpenAI Realtime WebRTC plus sideband metering/hangup on the deployed Function; Android microphone permission, reconnect, screen-close and repeated-call testing; actual screenshots from the canonical app build; and owner acceptance.

The Stage 4 status remains **CONDITIONAL — BLOCKED**. Neither PR #611 source changes nor the AI feature are marked deployed or certified by this code update.


## 1. Executive finding

FYNX already contains an existing Gemini text/image assistant and a separate OpenAI Realtime WebRTC voice implementation. The current scope preserves those providers and the existing authenticated backend; it does not change login, create a second backend, move provider secrets into Android, or activate infrastructure.

PR #611 adds durable, per-user Gemini request limits and token/cost accounting using the existing FYNX PostgreSQL database. It also adds optional per-user and owner-wide monthly spending thresholds. The exact tested code commit 8856ea4584c0437298082be1353796b2e16ea50d passed Backend CI; the successful AI test and security details are recorded below. These source changes are not live-provider certified.

Stage 4 cannot be marked certified. The only Railway service visible to this audit is a testing service whose latest deployment is FAILED (3 November 2026). Its environment variable-name inventory includes GEMINI_API_KEY but does not include OPENAI_API_KEY. A variable name does not prove key validity, account tier, provider billing readiness or a successful request. The canonical Android workflow also has an unrelated instrumentation failure.

## 2. What was already connected

### Gemini text, image and conversation

- Backend source: backend/geminiAssistantProvider.js.
- Default model: gemini-3.8-flash. GEMINI_MODEL is an optional server-side override.
- The provider reads GEMINI_API_KEY only from the backend process environment and sends it in the x-goog-api-key header. No Android permanent provider key is required or added.
- The provider supports text and inline image input, bounded history, streaming text and multi-turn approved tool calls. Existing bounds include up to 12 history items, four images, a 15 MiB combined inline image limit, 4,000 characters of current message text and a 45-second provider timeout.
- Authenticated Android conversation transport uses the existing FynxBackendClient and FynxAiConversationClient. Conversation ownership, messages and attachment references use the existing authenticated FYNX backend and PostgreSQL/Neon database.

### Inline writing assistance

- The existing writing endpoint is authenticated and accepts a bounded writing task and selected text.
- It operates as a one-request draft operation without conversation history, account database tools or action tools.
- The existing normalizer redacts contact details and credentials. The returned suggestion is a draft; it is not automatically posted, sent or saved.

### Marketplace product-photo quality review

- The existing photo review endpoint is authenticated.
- Android compresses selected images locally. The backend accepts up to four JPEG, PNG or WebP images, subject to existing per-image and total payload bounds.
- The prompt is constrained to photo quality only, with a strict issue-code response. It excludes OCR and reading image text, product/person identity analysis, private account tools, listing changes, publishing or saving.
- The existing client/contract expects the user to explicitly choose photos for the quality review; the image payload is used for the request and is not an AI conversation attachment.

### Tool authorization and confirmation

The reviewed Gemini tool registry has five approved tools:
1. prepare_send_message — creates a pending message for explicit user confirmation; it does not send.
2. search_users — searches discoverable public profiles.
3. get_people_recommendations — reads eligible public profile data.
4. search_marketplace — searches active public listings visible to the authenticated user.
5. get_trending_posts — reads public trending posts visible to the authenticated user.

The backend validates tool names and arguments, derives ownership from the authenticated user ID, checks privacy/blocks where required and rejects tools outside the allowlist. The actual send is a separate authenticated endpoint after explicit confirmation.

Private wallet balances, wallet/PIN secrets, transaction and financial history, bank/payment/payout details, KYC/identity data, private chats, non-public profile data and other protected information are excluded by the existing privacy policy and tool allowlist.

### OpenAI Realtime voice

- Android uses the existing FYNX WebRTC voice client and the authenticated FYNX backend routes.
- Backend source: backend/aiVoiceSession.js and backend/aiRealtimeRoutes.js.
- Current default model is gpt-realtime-2.1 and voice is marin; optional server-side overrides are OPENAI_REALTIME_MODEL and OPENAI_REALTIME_VOICE.
- The backend requires OPENAI_API_KEY. The API key is never embedded in Android.
- Realtime receives the reviewed FYNX tool allowlist and the AI privacy instructions. Tool requests are sent back through authenticated FYNX backend authorization.

## 3. Stage 4 implementation added on the feature branch

### Durable Gemini usage accounting

- Added backend/fynxAiUsageBudget.js using the existing shared PostgreSQL database pool; no new database or provider is introduced.
- A usage event records authenticated user ID, provider, capability, model, status, input/output token counts, estimated USD cost and timestamps. It does not record prompt text, conversation content, image bytes, wallet data or provider credentials.
- Reservations are serialized with PostgreSQL advisory transaction locks and count toward the per-user daily usage limit across backend instances.
- Default limits: 30 Gemini-backed requests per authenticated user per day and six Marketplace photo reviews per user per day. The owner can override these using FYNX_AI_USER_DAILY_REQUEST_LIMIT and FYNX_AI_USER_DAILY_PHOTO_REVIEW_LIMIT.
- Optional monthly thresholds:
  - FYNX_AI_USER_MONTHLY_BUDGET_USD sets a per-user estimated monthly Gemini spend threshold.
  - FYNX_AI_MONTHLY_BUDGET_USD sets an owner-wide estimated monthly provider spend threshold.
- No monthly amount is preconfigured or activated in Railway by this change. The owner must choose the amount before setting either variable.
- Gemini usageMetadata is collected from normal and streaming responses and summed across tool-call turns. The cost calculation includes prompt tokens, tool-result prompt tokens, candidate output tokens and thinking tokens. Completed/failed usage events record token totals and estimated cost.
- Authenticated GET /api/assistant/usage returns only the current user's daily usage, remaining daily quota, monthly token totals, estimated spend and personal budget threshold.
- HTTP 429 responses are returned when configured request or spending thresholds have been reached.

### Cost accounting boundaries

The cost estimate uses the published standard paid-tier token rates for the default Gemini 3.8 Flash model. A custom model requires explicit input/output price environment variables; otherwise the event cost remains unpriced rather than silently applying the wrong price. Free-tier pricing is different, and the Railway variable inventory does not establish whether the Google project is on free or paid tier.

The monthly stop is based on settled Gemini provider usage recorded in this ledger. One request already in flight can cross a threshold by its own cost, and an abrupt process termination may leave a reservation without returned provider usage metadata. The current ledger records Gemini only; it does not yet record OpenAI voice usage. This is monitoring and a stop-new-requests threshold, not a guaranteed hard dollar cap. The owner should also configure billing alerts/limits in the Google provider console. When no owner-wide threshold is configured, only per-user locking is used; the shared lock is enabled only for the owner-wide check.

The new limits apply to Gemini-backed text, images, writing and Marketplace copy/photo-review operations. OpenAI voice remains separately measured in the provider billing console until its sideband usage monitor is implemented and validated.

## 4. Provider costs and external requirements

### Gemini 3.8 Flash

Google's published standard paid-tier pricing is:
- Through 31 December 2026: USD 0.75 per million input tokens and USD 3.75 per million output tokens.
- From 1 January 2027: USD 1.50 per million input tokens and USD 7.50 per million output tokens.

Image input tokenization contributes to input use. The free tier is separately priced at no charge, but Google states free-tier content may be used to improve products while paid-tier content is not. Confirm the active project tier and data-handling arrangement before sending production user content.

Official pricing: https://ai.google.dev/gemini-api/docs/pricing/

There is no fixed monthly Gemini subscription cost in the source integration. Monthly spend depends on input/output tokens and chosen tier/model. The newly added usage ledger calculates an estimate from reported token usage. No monthly budget value has been set; owner approval is required.

### OpenAI Realtime voice

The existing source default gpt-realtime-2.1 has published standard rates:
- Audio input: USD 32 per million audio tokens.
- Audio output: USD 64 per million audio tokens.
- Text input: USD 4 per million tokens.
- Text output: USD 24 per million tokens.
- Cached input and image input have separate rates.

The Realtime session also configures gpt-4o-mini-transcribe for input transcription; OpenAI currently lists this at an estimated USD 0.003/minute. Its model page currently labels gpt-4o-mini-transcribe as deprecated, so the owner must confirm a supported transcription model before live certification. These transcription costs are separate from Realtime audio/text token charges.

Official pricing: https://developers.openai.com/api/docs/pricing/
Transcription model: https://developers.openai.com/api/docs/models/gpt-4o-mini-transcribe

No defensible monthly voice estimate is possible without actual session durations and audio/text token usage. The current source creates a WebRTC session but does not retain the OpenAI call ID, collect sideband response.done usage, calculate per-user audio cost or enforce an owner-wide voice budget. The OpenAI docs describe a server-side sideband WebSocket using the call ID from the Realtime call Location header; response.done contains usage details, and the active call can be ended by the hangup endpoint. This is a valid implementation path, not an implemented or tested FYNX feature yet.

Official server-side voice controls: https://developers.openai.com/api/docs/guides/voice-server-controls/

External voice requirements:
- Owner-provided, valid server-side OPENAI_API_KEY and provider billing access.
- A healthy long-lived backend process able to keep the sideband connection open for the session. A short-lived serverless request cannot be assumed to provide reliable ongoing call monitoring.
- Implementation and tests for call ID capture, per-user session association, server-side session expiry/hangup, response.done usage collection, OpenAI token-rate calculation and clean disconnect/reconnect handling.

## 5. Mocked tests and CI evidence

Existing backend prestart already includes the Gemini provider, AI privacy, realtime-authentication, inline-writing, Marketplace listing-copy and Marketplace photo-review tests.

This branch adds:
- test-fynx-ai-usage-budget.mjs: fake PostgreSQL tests for daily per-user limits, photo-review cap, per-user and owner-wide budget thresholds, reservation locking, settlement and caller-only usage summary.
- Extensions to test-gemini-assistant-provider.mjs: mocked token usage assertions for non-streaming and streaming Gemini responses, including tool-use prompt tokens and thinking tokens; the no-op usage adapter keeps provider tests independent of PostgreSQL.
- Explicit Backend CI syntax-check and execution of the new usage test, plus the backend prestart entry.

**Verified on code commit 8856ea4584c0437298082be1353796b2e16ea50d:**
- FYNX Backend CI push run 45799721101: SUCCESS — https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45799721101
- FYNX Backend CI PR run 45799730710: SUCCESS — https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45799730710
- Gemini assistant provider tests: 7 suites passed.
- FYNX AI usage budget tests: GREEN; daily per-user limits, serialization, token/cost metering and usage summary passed.
- FYNX AI privacy policy tests: GREEN.
- AI extension contract: 91 checks passed.
- Large Badge #8 AI certification: 44 checks passed.
- Node syntax check for fynxAiUsageBudget.js passed; full backend syntax check passed.

The Chat final closure and Chat parity workflows were still running at report update time. The canonical baseline Android Build remains failed at appDeepLinkLaunchesIntoFynx due to an unsupported drawable. No AI live provider test or exact APK runtime screenshot has been completed.

Current canonical baseline evidence:
- Canonical head at audit: 4382023be9dfd51d43728231504ce8fe9950e125.
- Android Build run 45786365108 is red at instrumentation test appDeepLinkLaunchesIntoFynx. The failing test throws an IllegalArgumentException because an unsupported drawable type is loaded; 37 instrumentation tests ran, one failed and one was skipped. This is outside Stage 4.
- Chat Group Parity run 45786365225 passed on the canonical head.
- Open PR #597 proposes moving the Android default API endpoint to the existing Neon Function. Its 23 November Android, backend, Chat closure and parity workflows passed, including AI contract/security checks, but PR #597 is still open and those results are not evidence for the current canonical head or this Stage 4 feature branch.

## 6. Current backend and live-test status

The active Neon Function for the existing FYNX testing branch was rechecked on 28 November 2026. Function inventory shows `fynxapi`, deployment #3, Node.js 24, status `completed`, created 27 November 2026. Read-only HTTP GET probes against its deployed URL returned:
- `GET /health`: HTTP 200, `ok: true`, `service: "fynx-backend"`, `database: "ready"`.
- `GET /ready`: HTTP 200, `ok: true`, `service: "fynx-backend"`, `database: "ready"`.

The deployed Function's non-secret environment-variable-name inventory includes `GEMINI_API_KEY` and `JWT_SECRET`, but does not include `OPENAI_API_KEY`. Secret values were not read or disclosed. The Railway `fynx-backend-testing` service remains failed (last deployment 3 November 2026); it is not the active healthy endpoint used for this probe.

Important distinctions: the active Neon Function health check proves the service and database are reachable; it does not prove the Gemini key is valid, the Google project tier/quota is suitable, that the unmerged PR #611 usage-budget code is deployed, or that authenticated AI requests succeed. PR #611 remains open and unmerged. In addition, the canonical Android `FynxBackendClient.kt` still defaults to the failed Railway testing URL, so the installed app is not yet routed to this healthy Neon Function. PR #597 contains the intended Neon URL/legacy-URL migration, but that branch is now diverged from canonical (12 commits ahead / 13 behind); reconcile and selectively port only the endpoint and directly related contract changes rather than merging its branch wholesale. This endpoint cutover is a prerequisite for authenticated Android AI tests. No OpenAI voice test can pass on the current Function until a valid server-side `OPENAI_API_KEY` is configured.

Consequences:
- The active backend health blocker is cleared for read-only service/database readiness. The authenticated Gemini/AI journey remains unverified.
- OpenAI Realtime voice remains externally blocked by the missing server-side `OPENAI_API_KEY`.
- Confirm the Google project quota, billing/data-handling tier and key validity through a controlled authenticated test before claiming live Gemini certification.
- No deployment, credential edit, paid service activation, backend migration or infrastructure purchase was performed.
- No alternative backend or storage system was created.

## 7. Remaining work and certification gate

1. Complete exact-head Backend CI, Android build/test/lint and the Chat/Groups regression workflows for the latest PR #611 head.
2. Verify the deployed Neon Function contains the required Stage 4 backend changes after PR #611 is reviewed and integrated; do not deploy an unmerged feature branch.
3. Reconcile PR #597's Neon client endpoint/default migration onto the latest canonical branch; do not merge its diverged history wholesale. Then run authenticated Gemini text/image/conversation tests against the healthy Neon Function using an authorized test account; verify the key, quota, selected Google project tier and billing alerts.
4. Set owner-approved per-user and owner-wide daily/monthly Gemini spending values in the active existing backend environment. No monthly spend ceiling has been selected by this code change.
5. Add and test OpenAI Realtime call-ID capture, sideband monitoring, usage token aggregation, per-user voice accounting and server-enforced duration/cost controls.
6. Configure `OPENAI_API_KEY` only in the existing backend secret store, then run live OpenAI transcription and speech tests. Do not expose the key or switch production services as part of this source change.
7. Re-run the exact canonical Android instrumentation/runtime suite and capture actual application screenshots; confirm portrait crop, voice selection, microphone permission, echo/interrupt, reconnect and hangup.
8. Run authenticated end-to-end journeys for conversation history/media, text/image prompts, writing draft, Marketplace photo review, allowlisted tools, explicit message confirmation and protected-data denial.
9. Record the exact integrated canonical commit and APK provenance, then obtain owner acceptance before changing the module to CERTIFIED — CLOSED.

**Final status: CONDITIONAL — BLOCKED.** The existing Neon backend is currently healthy and its database readiness is confirmed. Stage 4 is not certified: the Gemini live journey has not been authenticated and tested, OpenAI Realtime lacks a configured server key and usage/session accounting, the latest exact-head CI is still running, and Android runtime/device evidence and owner approval remain outstanding.

## 8. Grouped voice orchestration implementation on the Stage 4 PR

The grouped Android/backend change separates OpenAI voice transport from assistant reasoning:

- OpenAI WebRTC transcription mode emits text transcription events only. Automatic responses are disabled, tool definitions are empty, and tool selection is disabled.
- Each completed microphone transcript is submitted through the existing authenticated `/api/assistant/conversations/:id/message` route. That route loads the same user-owned persisted history and invokes the existing Gemini provider; its normal database transaction persists both the user turn and Gemini answer.
- The current voice UI creates or reuses the active FYNX conversation before opening the microphone. Typed and spoken messages therefore share the same conversation ID, persisted history and Gemini request-budget controls.
- OpenAI speech mode has no tools and is used only to read the exact text from the saved Gemini answer. OpenAI does not independently answer, search FYNX data or perform tools in the current voice UI.
- A focused backend contract test verifies the modes, confirms transcription cannot generate a model response or call tools, confirms speech has no tools, and retains the legacy assistant mode for compatibility.
- Backend prestart now includes the focused voice-routing test.

This is source-level implementation and mocked contract coverage, not live OpenAI session certification. The current Gemini usage ledger records Gemini token usage; OpenAI transcription/audio usage is not yet recorded in FYNX's per-user ledger. Server-side OpenAI call-ID capture/sideband accounting, authoritative voice-session expiry, physical-device echo/interruption tests, and live provider tests remain outstanding. No new key, deployment, provider account, database or paid service was activated.


## User-selectable speech voices

The Stage 4 voice selector intentionally exposes four supported OpenAI Realtime built-in voices: `marin`, `cedar`, `coral`, and `ash`, keeping the choice simple and avoiding unnecessary bundled audio assets. The Android client persists the selected voice in app preferences, sends it only with speech-mode session creation, and provides a spoken preview using a fixed sample sentence. The backend validates the requested ID against the allowlist and uses the configured default when the value is invalid. This does not alter transcription mode or Gemini answer generation. Live voice quality and physical-device preview behavior still require runtime certification.


Voice selection follow-up: Android now provides a selectable OpenAI Realtime speech voice and a speech-only sample preview. The selected voice is persisted locally and applies only to spoken playback of saved Gemini answers; microphone transcription and Gemini answer generation remain unchanged. Physical-device voice-quality and preview checks remain part of runtime certification.


## Transcription model compatibility update

The live microphone path now defaults to `gpt-live-transcribe`, with the existing `OPENAI_TRANSCRIPTION_MODEL` environment override retained. This replaces the deprecated `gpt-4o-mini-transcribe` default ahead of its February 26, 2027 shutdown. The voice routing test asserts the selected default. Provider runtime behavior still requires a valid OpenAI key and live backend certification.


## Voice portrait integration

The Android voice orb now renders a distinct generated portrait for each of the four selectable voices from one 320×320 WebP 2×2 sprite sheet bundled at `app/src/main/res/drawable-nodpi/fynx_ai_voice_portraits.webp` (9,452 bytes). Marin maps to the top-left portrait, Cedar top-right, Coral bottom-left and Ash bottom-right. The outer Azure-blue circular frame remains in place. This is a single compressed asset rather than four separately packaged files; final visual crop and device-memory behavior still require APK/device verification.
