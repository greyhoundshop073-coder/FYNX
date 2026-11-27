# FYNX Cloudflare TURN credential route

**Status:** implemented as an isolated, locally testable backend route module; not yet registered in the canonical server or enabled in any live environment.

## Purpose

The current FYNX WebRTC engines use public STUN servers. STUN discovers direct peer addresses but cannot relay media through restrictive carrier-grade NATs or firewalls. Cloudflare Realtime TURN provides UDP, TCP and TLS relay endpoints. Cloudflare documents that TURN is free when used together with Realtime SFU; stand-alone TURN is billed by outbound real-time GB.

This module prepares the missing server-side credential issue boundary without changing the active call-engine work in PRs #589/#599.

## Added files

- `backend/cloudflareTurnRoutes.js` — authenticated TURN credential endpoint and active-call authorization.
- `backend/test-cloudflare-turn-routes.mjs` — deterministic Node tests using fake pool, auth, rate-limit and Cloudflare API responses.
- `.github/workflows/cloudflare-turn-ci.yml` — focused CI for these files.

## Endpoint contract

`POST /api/calls/ice-servers`

Request:

```json
{ "callId": "call_..." }
```

Requires the existing FYNX auth middleware and shared rate-limit middleware. The authenticated user must be a participant in the exact active call. The route accepts:

- a direct call recorded in `fynx_realtime_calls` with status `ACTIVE`, where the user is caller or callee; this is also the identity boundary for Marketplace Live Proof calls after the existing Live Proof call context has validated the buyer, listing and seller;
- an active group call with that user recorded as `JOINED` in `fynx_realtime_group_call_participants`.

The route denies absent, ended, invited-only and unrelated calls. It does not trust a caller-supplied user ID or TTL.

Response contains only Cloudflare's short-lived TURN username/password and allowed ICE URLs, plus the credential expiration and recommended refresh time. Cache headers are `private, no-store`. Port 53 relay URLs are removed. The long-lived TURN key secret is used only by the backend to request ephemeral credentials; it is never returned.

## Required environment names

Set these in the backend secret/config store only:

- `CLOUDFLARE_TURN_KEY_ID` — Cloudflare Realtime TURN key UID (32 hexadecimal characters).
- `CLOUDFLARE_TURN_KEY_SECRET` — the associated Cloudflare TURN key secret (64 hexadecimal characters).
- `CLOUDFLARE_TURN_CREDENTIAL_TTL_SECONDS` — optional; defaults to 3600 seconds and accepts 300–172800 seconds (48 hours maximum).

**Do not reuse `CLOUDFLARE_SFU_APP_ID` or `CLOUDFLARE_SFU_APP_SECRET` as TURN credentials.** SFU application credentials and TURN key credentials are different resources. Create a TURN key only from the Cloudflare account after owner approval, and store the returned secret securely.

## Integration boundary

The active calling workstream owns `backend/server.js`, `backend/package.json`, the Android WebRTC engines and call UI. To integrate this module, that owner should:

1. Import `registerCloudflareTurnRoutes` from `./cloudflareTurnRoutes.js`.
2. Register it once with the existing shared `app`, `pool`, authenticated `auth` middleware, and the server's shared durable/edge rate-limit middleware (for example, the existing `rateLimit("calls", 30)` policy after adding that named limit).
3. Keep the route registration after the auth middleware is defined; do not register a second API client or backend.
4. Add `node test-cloudflare-turn-routes.mjs` to the existing backend CI/startup verification sequence.
5. Update the Android direct/group call repositories to request this endpoint for the active call, use the returned `PeerConnection.IceServer` list, and refresh credentials before expiry with WebRTC `setConfiguration()`. Do not place the TURN key secret in the APK.
6. Keep the existing Cloudflare SFU session flow separate. TURN provides relay connectivity; it does not implement SFU publish/subscribe or make a four-person P2P mesh scalable by itself.

The route requires a shared rate-limit middleware. Do not register it without one. A per-process-only limiter is not a complete abuse-control strategy across multiple backend instances; keep edge/host rate protection enabled as well.

## Local and CI verification

Run:

```sh
cd backend
node --check cloudflareTurnRoutes.js
node test-cloudflare-turn-routes.mjs
```

No Cloudflare key is needed for these tests. They mock the provider and prove authorization, no-store headers, TTL validation, port filtering, fail-closed behavior and secret non-disclosure.

## Live-only work remaining

- Create a Cloudflare TURN key in the correct FYNX account.
- Store its ID and secret in the intended Railway testing environment after the owner verifies the key and rotates any previously exposed secret.
- Register this module in the current calling server implementation.
- Integrate Android ICE server retrieval and in-call refresh.
- Verify private, Marketplace Live Proof and group calls over Wi-Fi/mobile networks, including restrictive NAT/TLS relay.
- Confirm Cloudflare dashboard reports expected traffic and pricing before any broader release.

Do not mark calls live-verified based only on this module or CI. Successful credential issuance is not proof of media flow.
