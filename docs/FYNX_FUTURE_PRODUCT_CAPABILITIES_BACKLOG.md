# FYNX Future Product Capabilities Backlog

**Status:** DEFERRED — recorded for future planning only  
**Owner approval:** Not approved for implementation  
**Created:** 2026-11-23  
**Canonical integration branch:** `integration/fynx-single-apk-20261013`

## 1. Why this document exists

FYNX has substantial work already in progress across its existing product areas. The owner has explicitly decided that the additional product capabilities and expansion work described here must **not** be started now.

The immediate priority remains completing, stabilising, integrating and testing the work already underway. Starting these future initiatives now would scatter engineering effort, create competing workstreams, increase integration risk and delay completion of the existing scope.

This document preserves the ideas and preliminary findings so that the team can return to them when the owner is ready.

> **This is a parking document, not an implementation instruction.** Recording an item here does not authorise development, create a commitment, change the active release scope or make it a blocker for current work.

## 2. Mandatory rules for every contributor and future agent

1. Do not start, implement, prototype, scaffold, branch, create a PR for, or otherwise begin any item in this backlog without explicit owner approval at that future time.
2. Do not interrupt, replace, expand, or reprioritise existing approved work because an item appears in this document.
3. Do not create parallel implementations of capabilities that already exist in FYNX.
4. When the owner authorises planning, first inspect the then-current canonical branch, active PRs, code, backend contracts, tests and existing feature implementations. This audit is a snapshot from 2026-11-23 and must not be treated as proof of future state.
5. Classify each capability as **already implemented and verified**, **partially implemented**, **missing**, or **blocked by an external dependency** before proposing changes.
6. Extend and connect existing FYNX systems wherever practical. Do not create duplicate navigation, identity, search, messaging, payment, notification, media or business systems.
7. Any future implementation must follow `AGENTS.md` and all mandatory policies it references, including the single-APK integration policy, development/platform compliance rules, Google Play readiness plan and backend-ready development/reconnection policy.
8. All approved implementation must target the one canonical integration branch and shared APK process. No separate APK or competing delivery branch.
9. Backend-dependent capabilities must have honest offline/local behaviour, typed contracts, failure states and explicit live-service verification. Never fake a successful payment, delivery, call, translation or AI action.
10. Before work begins, the owner and engineering lead must agree a finite scope, acceptance criteria, dependencies, test evidence and completion boundary. Group related changes into meaningful delivery units.

## 3. Deferred work packages

The following work packages were proposed after the existing foundation-stabilisation work. They are intentionally deferred.

### Work package 2 — Complete and improve the existing social experience

Future areas to assess and improve, only after approval:

- Home and Feed: relevance, ranking quality, user controls, performance, media loading and content discovery.
- Status: reliable creation/viewing, sharing and appropriate product/business promotion.
- Private Chat: delivery and read states, attachments, reconnect behaviour, privacy and multi-device consistency.
- Groups: complete membership and administration flows, reliable synchronisation, notifications and useful collaboration.
- Profiles and identity: consistent public identity and safe account controls across the app.
- Search and notifications: coherent results, reliable delivery, useful deep links and predictable settings.

These are improvement areas, not permission to reopen completed work indiscriminately. Preserve existing implementations and identify specific reproducible gaps first.

### Work package 3 — Complete and connect the commercial experience

Future areas to assess and improve:

- Marketplace buyer journey, discovery, product details and checkout.
- My Shop seller operations, product management and customer conversion.
- Seller verification, reputation, authentic product evidence and verified-purchase reviews.
- Buyer-to-seller live product inspection, connected to the actual listing and active call.
- Order lifecycle, delivery tracking, cancellations, refunds and disputes.
- Wallet ledger correctness, transaction history, payment-provider integration and reconciliation.
- Creator/business promotion and commerce attribution where legally and operationally appropriate.

Financial, escrow, wallet and payment changes require separate security, accounting, provider and legal/compliance review. Never imply that a local test or UI state represents a real deposit, withdrawal or settled transaction.

### Work package 4 — Develop FYNX AI into a useful, permission-controlled assistant

Existing AI foundations must be re-inspected before any extension. Future capabilities to consider:

- More complete task assistance across authorised FYNX features.
- Better parity between text and voice experiences.
- Web research with reliable source citations.
- Document and PDF understanding.
- User-consented memory and clear controls.
- Context-aware assistance for sellers, creators and ordinary users.
- Safe, confirmation-based execution of actions.

The AI must use least-privilege access. It must not expose private wallet data, secrets, private messages or another person's information without explicit, valid authorisation. Financial or consequential actions require clear user confirmation and server-side permission checks. AI must not fabricate task completion or transaction outcomes.

### Work package 5 — Connect the ecosystem without duplicating systems

Future integration opportunities:

- A shared, intelligent search experience across authorised people, posts, products, businesses, groups and other supported content.
- Video-to-commerce: connect content discovery, product listing, seller communication, live inspection, checkout and post-purchase support.
- Language Bridge: optional translation of messages, group conversations, voice/transcripts and captions, retaining access to the original content.
- A unified creator/business performance experience that reuses existing My Shop, Marketplace and analytics foundations.
- AI-assisted group/community summaries and moderation support with appropriate member privacy and admin permissions.
- Consistent notifications, deep links, identity, reporting/blocking and user controls across modules.

Architecture should remain modular: failure of one service must not unnecessarily disable unrelated experiences. Cross-feature integrations must use explicit contracts and permission checks.

### Work package 6 — Full product certification

After the owner authorises the preceding work and the existing product scope is stable, plan a consolidated certification programme covering:

- The actual canonical integrated Android APK, not screenshots or feature-branch builds alone.
- Complete end-to-end user journeys across social, messaging, commerce, wallet and AI.
- Two-account and multi-device scenarios where relevant.
- Backend, database, storage, realtime and notification behaviour.
- Security, privacy, moderation, reporting and blocking.
- Android performance, memory, startup, rendering, battery and low-bandwidth behaviour.
- Accessibility, localisation and supported-device coverage.
- Google Play and relevant third-party policy compliance.
- Regression evidence, reproducible test records, APK provenance and SHA-256.

Certification must distinguish local tests, automated CI, emulator/device evidence, staging integration and real production-provider verification.

### Work package 7 — Cross-feature performance and resource isolation

**Status: DEFERRED — investigation recorded; no implementation authorised.**

The purpose is to make FYNX feel responsive when people move between or use multiple areas. For example, heavy Home/Feed activity should not unnecessarily delay Chat, Groups, Marketplace, Wallet or active calls. Because Android features share a process and device resources, the engineering goal is controlled resource use, workload isolation and priority—not a claim that one feature can have absolutely zero impact on another.

Preliminary code-review observations from 2026-11-23 (not runtime certification):

- FYNX currently uses a single Android app module and central app/navigation state. The selected destination is displayed; this does not mean every screen is continuously rendered. Shared app-level state and effects nevertheless create common resource and change-impact paths.
- Backend requests already use background IO dispatching and a shared concurrency limit. This is a useful safety foundation, but a global request limit can let optional Home requests compete with time-sensitive Chat, call or commerce requests.
- Home performs multiple content and supporting-data loads. Existing request and profile-photo limits, lazy lists, media caching, sampled image decoding and poster-based video previews are positive foundations.
- A signed-in notification refresh currently runs on a periodic interval and should be reviewed for lifecycle, efficiency and delivery requirements.
- The activity has a fixed launch-screen delay that should be measured before deciding whether it is still necessary.
- The inspected Android build setup did not show a dedicated Android Macrobenchmark/performance-certification suite. Existing backend performance checks do not prove Android UI, startup, memory or frame performance.
- No representative physical-device stress measurements were available during this source review. Therefore this review does not certify that the app is currently fast or slow.

When authorised, investigate and implement only evidence-backed improvements, considering:

1. **Priority-aware network scheduling:** reserve capacity for incoming/active calls, Chat delivery and essential financial/order operations; prioritise the visible screen; schedule ordinary content next; keep recommendations, speculative prefetch and background refresh low-priority, cancellable and deferrable.
2. **Feature-scoped work:** ensure each feature's asynchronous work is lifecycle-aware, cancellable when no longer relevant, and does not leave unnecessary observers, timers or requests running after navigation.
3. **Media budgets:** use appropriately sized thumbnails, bounded image/video work, cache limits and controlled prefetch. Avoid decoding or retaining large media unnecessarily.
4. **Main-thread discipline:** keep network, disk, parsing and expensive computation off the UI thread; investigate slow frames, recomposition/layout churn, memory pressure and ANRs using Android profiling tools.
5. **Startup and background efficiency:** measure cold/warm/hot startup; review fixed delays, periodic polling, work scheduling and battery/network use before changing behaviour.
6. **Independent failure behaviour:** optional Home recommendations or discovery failures must not block core messaging, Marketplace browsing or other unrelated features.
7. **Device/network coverage:** test affordable and lower-memory Android devices, constrained connectivity and reconnect scenarios, not only a high-end emulator.

Required future verification:

- Add Android Macrobenchmark coverage for startup and representative navigation/scroll interactions; assess Baseline Profiles using measured results.
- Use Android Studio Memory Profiler and Perfetto/JankStats or equivalent evidence to inspect memory, CPU contention, frame timing and jank.
- Run a controlled simultaneous-use scenario: scroll Home and load media while browsing Marketplace images/details, sending and receiving Chat messages, opening Groups and using an AI request. Include active-call scenarios when the call stack is ready for reliable testing.
- Compare isolated-feature baselines against concurrent-use results on representative devices and normal/weak networks. Record cold/warm startup, frame/jank behaviour, memory/peak memory, request latency, dropped/failed work, battery/network impact and recovery after navigation.
- Verify that critical Chat/call/commerce work remains responsive under optional Home/feed load. Define numeric acceptance thresholds before implementation rather than inventing them after tests.
- Run regression tests on the actual canonical integrated APK and record APK provenance. A source change, passing unit test or screenshot alone is not performance certification.

This work must improve the existing architecture rather than introduce unnecessary Android processes, duplicate networking stacks or a broad rewrite. First map current ownership and concurrency, then make the smallest coherent changes supported by profiling evidence.

**Reopen condition:** only when the owner explicitly authorises this performance work after the currently approved FYNX scope is in a suitable state. Until then, do not create a performance branch, PR, benchmark module or code change on the strength of this note alone.

## 4. Additional future capabilities — ideas only

These ideas are not established as complete product requirements and must not be implemented until separately researched, prioritised and approved.

| Idea | Future planning question |
|---|---|
| FYNX Universal Search expansion | How can existing search be extended to more authorised content types with ranking, pagination and privacy? |
| FYNX Language Bridge | Which translation and speech services are affordable, reliable and suitable for the target markets? |
| Creator and Business Performance Centre | What should be shared, and what should remain in My Shop or existing admin tools? |
| AI Group Assistant | What summaries and moderation assistance are useful without violating member privacy? |
| Video-to-Commerce | How should the complete listing-to-call-to-order journey work, including seller consent and call reliability? |
| FYNX Communities and Events | Is there a validated user need beyond existing Groups and calendar functionality? |
| Market Agents | Can trusted local agents help traditional businesses digitise products and storefronts? Validate operational, fraud and fee models before planning implementation. |
| Jobs, gigs and services | Is there sufficient demand and a safe, manageable trust and dispute model? |
| Airtime and data purchases | Which licensed providers, payment flows and support obligations would be required? |
| Further international expansion | What localisation, currency, tax, privacy, payment and delivery requirements apply in each market? |

## 5. Product principles to preserve

FYNX should become more powerful and meaningful, not merely accumulate features.

Every future proposal must demonstrate:

- A clear user problem and measurable benefit.
- A reason it belongs inside FYNX rather than being a disconnected feature.
- Reuse of existing foundations and a clear integration path.
- A simple, mature and consistent user experience.
- Good performance on affordable Android devices and constrained networks.
- Privacy, safety, accessibility and platform-policy considerations from the beginning.
- A sustainable operating cost and a realistic maintenance plan.
- Evidence that the capability improves user outcomes, trust, retention or legitimate business value.

Do not optimise for feature count, screen count or time spent in the app as goals in themselves.

## 6. When this backlog may be reopened

Reopen this document only when the owner explicitly says the current approved work has reached a suitable completion point and authorises planning for future capabilities.

At that time:

1. Review the latest canonical branch and current project status.
2. Reconcile this backlog against everything already delivered since 2026-11-23.
3. Remove duplicates and obsolete ideas; do not assume the old audit remains accurate.
4. Validate user demand, technical feasibility, costs, risks, policy obligations and external dependencies.
5. Propose a small number of coherent, prioritised initiatives with acceptance criteria.
6. Obtain owner approval for the chosen scope before implementation.
7. Add approved work to the active project plan in a controlled way; leave unapproved items deferred.

## 7. Current decision

**Owner decision as of 2026-11-23:**

- Existing approved FYNX work remains the priority.
- Work packages 2–7 and the additional ideas in this document are deferred for future planning.
- No new product expansion or performance implementation work is authorised by this document.
- This document is a reference for later; it must not create scattered work or delay current delivery.

---

*End of deferred backlog. No implementation is authorised by this document.*
