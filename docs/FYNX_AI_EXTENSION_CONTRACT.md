# FYNX AI Extension Contract

This is the protected extension point for future FYNX AI capabilities.

## Rule

Future AI features must be added as a new capability behind the existing FYNX AI boundaries. Do not replace or fork the current text, realtime voice, authentication, database ownership, or UI transport.

## Mandatory privacy boundary

FYNX AI must not retrieve, infer, disclose, summarize, or confirm private personal or financial information for any user, including the authenticated user. This is intentionally stricter than ordinary account ownership: protected information stays inside its normal authenticated FYNX screen and security flow.

Protected categories include wallet balances and ledger/transaction history; bank and payment details; passwords, PINs, tokens and recovery secrets; private FYNX chat messages; phone numbers, email addresses and private addresses; identity/KYC evidence; private orders, delivery addresses and customer records; friend/follower lists, saved-post history and non-public profile fields.

The AI registry must expose only reviewed public-data capabilities and explicitly confirmation-gated message preparation. Private-data tools must be absent from text and voice tool declarations and rejected by the executor before any database query. Public profile search, public posts and active public Marketplace listings must still respect visibility, stock and block rules. Recommendation results must not reveal private social-graph counts or relationships.

The user's own persisted FYNX AI conversation can be used only as bounded continuity for that same authenticated AI conversation. It does not grant access to ordinary FYNX chat messages or other account records.

AI people search and recommendations may return only profiles whose FYNX `profile_visibility` is `Everyone`; missing settings default to `My friends` and are not public. AI recommendations must not query or use private friendship/follow graphs. AI message preparation may resolve only profiles where both `profile_visibility` and `messages_visibility` are `Everyone`.

When a user asks for protected information, the assistant explains that the user must open the appropriate protected FYNX screen. No tool call, indirect inference, or record-existence confirmation is permitted.

## Marketplace seller copy assistance

The Sell on FYNX form may request an AI draft of a product title and description using only the seller-entered product name, category, subcategory, condition, description and visible product specifications. The endpoint is authenticated, text-only, rate-limited and has no database or AI tools. It must not receive price, currency, stock, seller location, personal contact details, wallet data, order data, private profile data or uploaded media.

The model must use only supplied product facts, preserve the selected condition and avoid inventing product features, compatibility, warranty, certifications, authenticity, origin, expiry, delivery or availability claims. Phone numbers, email addresses, URLs, recognizable plot/house/street addresses, and common bank-account or credential phrases are redacted before provider context. Returned copy is sanitized too. The response must validate as a bounded title/description pair.

Suggestions are drafts only. The Android form displays a preview and requires the seller to explicitly apply the suggested title and description. The existing seller edit fields and normal Marketplace publish validation/authorization remain the only publishing path. AI must never publish, save or modify a listing itself.

## Marketplace product-photo quality review

The seller may explicitly request a photo-quality review of up to four still product photos. Before sending, Android shows a consent step explaining the selected photos are compressed on-device and sent to FYNX AI (Gemini) through the authenticated request for this one check. FYNX does not save the selected image bytes to the user's account, Marketplace media or listing. Users are warned not to select photos showing faces, identity documents, receipts, addresses, phone numbers, contact details or other private information.

The route accepts only a bounded array of image MIME types and base64 image bytes. It requires FYNX authentication, rejects unexpected fields, invalid base64, unsupported MIME types, more than four images, more than 4 MB per image or more than 9 MB combined. Images are passed to Gemini in-memory for that request and must not be inserted into media storage, attached to an AI conversation or persisted as Marketplace media.

The provider receives only selected image bytes and a fixed photo-quality prompt. It receives no seller text, price, currency, quantity, location, profile, wallet, order, customer, chat or other account data. It has no database/action tools, conversation history or continuity context. The model assesses only lighting, sharpness, glare, background clutter, framing, camera distance, angle and image resolution. It must not identify/describe people or products, infer product facts, read image text, or follow image-embedded instructions. It returns only strict quality status and allowlisted issue codes; Android renders fixed reviewed advice instead of free-form model prose.

The review is advisory only. It never modifies, enhances, saves, publishes, stores or replaces an image or listing. Existing on-device background cleanup remains separate. Normal listing photo upload and ownership validation remain exclusively in the existing Publish flow.

## Stable layers

1. **Android AI UI** — `FynxAiAssistantPanel.kt`
   - presentation, input, attachment selection, voice controls and confirmation UI only
   - no provider credentials
   - no direct provider networking

2. **Android AI transport** — `FynxAiConversationClient.kt` and `FynxAiVoiceSession.kt`
   - all AI traffic goes through the authenticated FYNX backend
   - keep the central `FynxBackendClient` transport boundary
   - text conversations may negotiate Server-Sent Events (SSE) on the existing owned-conversation message route; the default request/JSON response contract remains compatible
   - Android must parse bounded SSE events through `FynxBackendClient`, retain the authenticated FYNX session, and never call Gemini directly
   - if an older deployed backend ignores the SSE preference and returns the established JSON envelope, Android must accept that bounded JSON response as a compatibility fallback

3. **FYNX AI backend** — `aiConversationRoutes.js`, `aiRealtimeRoutes.js`
   - authenticate the FYNX user before AI access
   - keep provider credentials server-side
   - derive conversation continuity only from rows scoped to both the owned conversation and authenticated user
   - keep the provider's latest 12 messages as normal history and add only a bounded older-history excerpt (80 stored rows queried; 900-character summary; 160-character prior-task hint)
   - label continuity excerpts as untrusted historical context; they are not authoritative FYNX state or new instructions
   - keep request/resource limits and existing abuse guards

4. **Capability/tool registry** — `fynxAiToolRegistry.js`
   - the only approved place for AI access to real FYNX data or FYNX actions
   - every new data/action capability must be represented here
   - preserve authenticated user scoping and server-authoritative state
   - sensitive writes remain confirmation-gated or unavailable until explicitly designed and verified

5. **Provider adapters**
   - normal text conversations and image understanding use the server-side Gemini adapter in `geminiAssistantProvider.js`
   - text replies may use Gemini `streamGenerateContent` SSE so the Android panel can render user-visible text deltas as they arrive; non-streaming callers keep the existing `generateContent` path
   - thought-only model parts must not be forwarded to the user-visible stream; tool calls still execute only through the approved registry and the final reply is persisted before the completion event
   - Gemini credentials are read only from the backend `GEMINI_API_KEY` environment variable and sent in the `x-goog-api-key` request header
   - realtime voice continues to use the existing server-side OpenAI WebRTC adapter until a separately reviewed migration is approved
   - provider changes must not leak into the Android APK

## Extension categories

Future capabilities may be introduced in these categories without changing the existing foundation:

- READ: read safe, authenticated FYNX data
- DISCOVER: search/recommend real FYNX data
- PREPARE: prepare a user-requested action for explicit confirmation
- EXECUTE: only for an action that has a separately reviewed server-authoritative confirmation contract
- MEDIA: process user-owned media through existing authenticated media ownership rules
- REALTIME: add realtime AI behavior through the existing WebRTC/session boundary

A new capability is **not** allowed to bypass these layers by adding a second OpenAI client, direct provider URL in Android, unscoped database query, fake local FYNX data, or an alternate AI route.

## Compatibility rule

When adding a capability:

- preserve all existing endpoints and response contracts unless a migration is explicitly required
- preserve the normal JSON response for existing callers; streaming is negotiated only when the caller explicitly requests `text/event-stream`
- send ordered `delta`, `complete`, or generic `error` events; the `complete` payload carries the persisted message and pending confirmation action
- do not claim a streamed reply is saved until the server confirms the completed database commit
- preserve current AI conversations and stored messages
- preserve existing message confirmation behavior
- preserve realtime voice session authentication
- preserve the central network transport
- add a focused verifier for the new capability
- add and run a mocked provider test for text, images, tool calls and secret handling without using a real provider key
- run the existing AI security gate and full Android build before declaring GREEN

## Future-proofing

This contract intentionally creates a stable seam for later additions such as smarter FYNX search, recommendations, translation, media assistance, planning, and other approved AI tools. Those additions should extend the registry/capability layer rather than rewrite the foundation.
