# FYNX backend migration engineering record

Status: historical Cloudflare assessment; no Worker was deployed.

> **Superseded architecture decision (9 November 2026):** Neon Functions is now the selected runtime for the FYNX test backend. The existing project is in a supported Neon Functions region, and Node.js 24 supports the current bcrypt, Node HTTP and WebSocket requirements. Cloudflare Free's 10 ms CPU ceiling is unsuitable for safely preserving the existing bcrypt login path. See `NEON_FUNCTIONS_DEPLOYMENT.md` for the active implementation and deployment steps.

## Current implementation status

- The source-patching bootstrap chain now has a build-only mode so generated server modules can be created before deployment rather than written at function startup.
- The canonical Express app and realtime connection handler are exported for the Neon Function adapter.
- `neon-function.js` adapts HTTP requests to the existing Express app and WebSocket upgrades to the existing authenticated realtime handler.
- Periodic process-local workers are skipped in the Neon request runtime. The `fynx-marketplace-maintenance` Neon schedule now runs payment/checkout expiry, interrupted-payment recovery, terminal financial reconciliation, inspection expiry, payout settlement scanning, up to 20 queued jobs, and stale realtime cleanup every minute.
- Cloudflare Worker scaffolding was removed rather than leaving two competing backend targets.
- The Neon runtime build, scheduled-task contracts, and PostgreSQL cross-isolate fanout integration check passed in GitHub Actions run `42602489728`. No Neon Function has been deployed yet; the existing Railway secrets are redacted to this connected account, so their exact values must be transferred through the GitHub Actions secrets and variables documented in `NEON_FUNCTIONS_DEPLOYMENT.md`.
- The Android backend URL, test-user sessions, and Neon database records have not been changed.


Canonical application branch: `integration/fynx-single-apk-20261013`

## Preservation rules

- Keep the existing FYNX repository, Android application, API paths, JWT contract, and Neon data.
- Do not point a test Worker at the protected recovery branch.
- Use the isolated Neon test branch for all connection and schema compatibility checks.
- Do not change the Android backend URL until the replacement has passed authenticated end-to-end tests.
- Do not run startup migrations or background reconciliation against the recovery branch.

## Findings from the existing backend

The current backend is not a small Express API. Its production startup is assembled through several Node scripts:

- `realtimeIsolationBootstrap.js` patches the WebSocket implementation and writes changes into `socialRoutes.js`.
- `serverBootstrap.js` rewrites `server.js` and `socialRoutes.js`, then writes generated runtime files.
- `phase1FoundationBootstrap.js`, `notificationBootstrap.js`, and `socialMultiMediaBootstrap.js` also patch source files.
- `renderScalabilityPreload.js` imports `scalability.js` and realtime compatibility before startup.
- `scalability.js` monkey-patches `http.createServer` to install many route families and starts background workers.
- `server.js` creates an Express app, a Node HTTP server, a `ws` WebSocket server, database pools, and calls `listen()`.

These transformations must become a deterministic build-time step. They must not run through filesystem writes during a Worker request or at Worker startup.

## Cloudflare compatibility decision

Cloudflare documents a Node HTTP server adapter (`httpServerHandler`) that can route Worker requests to a Node HTTP server. This offers a credible path to preserve the existing Express HTTP routes rather than rewrite all of them.

However, Cloudflare's Node HTTP implementation does not support the HTTP `upgrade` event or direct socket access. The existing `/realtime` WebSocket transport therefore cannot be passed through the HTTP adapter. It must be routed to a Durable Object that preserves the current authenticated message, delivery/read receipt, typing, presence, and call-signaling protocol.

References:
- https://developers.cloudflare.com/workers/runtime-apis/nodejs/http/
- https://developers.cloudflare.com/hyperdrive/examples/connect-to-postgres/postgres-database-providers/neon/
- https://developers.cloudflare.com/durable-objects/best-practices/websockets/

## Target runtime design

1. A build script runs the existing source-patching transformations in a controlled Node build environment and produces generated server/social runtime modules as build artifacts. The generated modules are checked for expected route registrations before bundling.
2. The Worker initializes Cloudflare environment configuration before importing the generated server modules. It supplies Hyperdrive's connection string through the existing shared database-pool configuration and supplies secrets only through Cloudflare bindings.
3. The Worker sends ordinary HTTP requests to the existing Express/Node HTTP server using `httpServerHandler`.
4. Requests to `/realtime` are routed to a Durable Object. The Durable Object must authenticate the existing JWT and check the current database session version before accepting the socket.
5. Existing scheduled reconciliation and durable job queue work is moved from process-local `setInterval` loops to explicit, idempotent scheduled invocations. Financial and order jobs must retain their existing locks, idempotency keys, and retry semantics.
6. Existing R2-compatible media logic is retained only after the AWS S3 client bundle and upload/download behavior are tested in Workers. No media provider or stored object is switched without a verified migration plan.

## Release blockers to resolve

- **Password hashing:** registration and password reset use `bcryptjs.hash(..., 12)`; login uses `bcryptjs.compare`. Cloudflare Workers Free currently has a 10 ms CPU-time limit per invocation. These operations must be benchmarked in the actual Worker runtime. Do not lower password-hash strength or change stored hashes without a reviewed dual-verification migration and rollback plan.
- **Runtime generation:** the existing bootstrap chain writes files at startup. A build-only pipeline must reproduce its route wiring deterministically without starting the server or connecting to a database.
- **Database pools:** several backend modules create or share pools from `DATABASE_URL`. Verify all pools use the Hyperdrive binding safely, with bounded connections and no accidental per-request pool explosion.
- **Background jobs:** marketplace payment expiry, inspection expiry, payout settlement/reconciliation, recovery, and generic queue processing must be invoked safely by Cron/Queues/Workflows rather than relying on process lifetime.
- **Realtime:** port the actual protocol, session-version checks, presence, typing, message acknowledgements/read receipts, and call signaling to Durable Objects; do not ship a placeholder socket.
- **AI and payments:** keep AI credentials and Paystack secrets server-side. Continue excluding wallet, payment, transfer, and private financial history from AI tools and context.
- **Free-tier capacity:** Workers Free currently lists 100,000 requests/day and 10 ms CPU/request. Hyperdrive Free lists 100,000 database statements/day. Durable Objects Free has daily request and duration allowances and SQLite-backed storage. Treat quota exhaustion as a controlled service error; do not assume unlimited free usage.

## Required verification before Android cutover

- Worker build and Wrangler dry-run.
- Build artifact route-manifest checks against the existing API contract.
- Password registration/login/reset benchmarks and security tests.
- Hyperdrive connectivity to the isolated Neon test branch only.
- Readiness, schema compatibility, and read-only data-count checks.
- Marketplace checkout, Paystack webhook signature verification, wallet ledger, order expiry, refunds, and settlement idempotency tests.
- Media upload/retrieval and access-control tests using a test R2 bucket.
- Durable Object WebSocket protocol tests with two authenticated test users.
- AI privacy-policy tests and secret-redaction checks.
- Android client compatibility tests against the candidate Worker URL.
- Successful CI and a real Cloudflare deployment health check before changing the app endpoint.

## Current safety correction

The canonical backend startup statement that reset every user's `verified` flag was removed. A regression assertion was added to `verify-runtime-bootstrap.mjs`. The expected FYNX official-account verification update remains separate; it is not a blanket reset.

## Deployment state

No Cloudflare account binding, Hyperdrive configuration, R2 bucket, Durable Object namespace, secret, or Worker deployment has been created from this environment. Do not report a deployment until Cloudflare confirms it and the deployed health endpoint is tested.
