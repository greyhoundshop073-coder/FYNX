# FYNX AI Extension Contract

This is the protected extension point for future FYNX AI capabilities.

## Rule

Future AI features must be added as a new capability behind the existing FYNX AI boundaries. Do not replace or fork the current text, realtime voice, authentication, database ownership, or UI transport.

## Stable layers

1. **Android AI UI** — `FynxAiAssistantPanel.kt`
   - presentation, input, attachment selection, voice controls and confirmation UI only
   - no provider credentials
   - no direct provider networking

2. **Android AI transport** — `FynxAiConversationClient.kt` and `FynxAiVoiceSession.kt`
   - all AI traffic goes through the authenticated FYNX backend
   - keep the central `FynxBackendClient` transport boundary

3. **FYNX AI backend** — `aiConversationRoutes.js`, `aiRealtimeRoutes.js`
   - authenticate the FYNX user before AI access
   - keep provider credentials server-side
   - keep request/resource limits and existing abuse guards

4. **Capability/tool registry** — `fynxAiToolRegistry.js`
   - the only approved place for AI access to real FYNX data or FYNX actions
   - every new data/action capability must be represented here
   - preserve authenticated user scoping and server-authoritative state
   - sensitive writes remain confirmation-gated or unavailable until explicitly designed and verified

5. **Provider adapters**
   - text provider calls remain server-side
   - realtime provider calls remain server-side
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
- preserve current AI conversations and stored messages
- preserve existing message confirmation behavior
- preserve realtime voice session authentication
- preserve the central network transport
- add a focused verifier for the new capability
- run the existing AI security gate and full Android build before declaring GREEN

## Future-proofing

This contract intentionally creates a stable seam for later additions such as smarter FYNX search, recommendations, translation, media assistance, planning, and other approved AI tools. Those additions should extend the registry/capability layer rather than rewrite the foundation.
