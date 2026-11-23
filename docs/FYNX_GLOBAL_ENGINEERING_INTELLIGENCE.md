# FYNX Global Engineering Intelligence & Builder System

**Status:** Mandatory engineering operating standard for every FYNX contributor, human developer, coding agent and ChatGPT session.
**Canonical delivery branch:** `integration/fynx-single-apk-20261013`
**Product ambition:** Build one integrated FYNX product that serves Nigeria and Africa exceptionally well and can scale responsibly for users worldwide.

This is not a country-ranking exercise or a collection of slogans. It is a repeatable research-and-engineering method that draws on credible work published by leading engineering organizations and practitioners around the world. Do not assume that every developer in a country works the same way. Attribute practices to their actual source, author or organization.

## 1. The builder's mandate

Treat this document as the persistent engineering memory and decision framework for FYNX. Every session must load it before making changes and must update the project records when meaningful decisions, findings, fixes or certification results change.

The builder must:
- Continue from the repository's real current state; never restart from an old chat summary.
- Research the engineering problem outside this chat when external evidence can materially improve the solution.
- Compare credible approaches, test assumptions against FYNX's code and constraints, and explain the selected trade-offs.
- Preserve existing working behavior and other workstreams' contributions.
- Deliver one integrated product through the canonical branch and the single-APK process.
- Prefer correctness, security, user trust and maintainability over speed or impressive claims.
- Never promise perfection. Identify residual risk, measure quality and improve it continuously.

## 2. Global engineering knowledge map

Use these as research directions, not stereotypes or mandatory vendors. Search for primary engineering material from the named organizations, their engineering blogs, technical documentation, conference talks and published books. Use the most relevant source for the problem, wherever it comes from.

| Research lens | Organizations and knowledge sources to investigate | Practices to evaluate for FYNX |
|---|---|---|
| United States — platform scale, product engineering, AI and cloud reliability | Google/Android and Google SRE; Meta Engineering; Apple Developer; AWS Architecture; Microsoft Engineering; Stripe Engineering | Reliable distributed systems, product experimentation, platform APIs, AI safety, observability, performance, secure payments and operational excellence |
| China — high-scale consumer platforms and commerce | Alibaba Cloud/Alibaba Tech; Tencent/WeChat engineering; ByteDance engineering and open-source projects; Apache projects originating from or maintained by these ecosystems | High-volume feeds, messaging, commerce workflows, recommendation systems, caching, distributed storage, media delivery and graceful degradation |
| India — engineering delivery, digital public infrastructure and cost-aware scale | Thoughtworks India; engineering publications from Indian product companies; India Stack/NPCI official technical material when relevant; Infosys/TCS engineering publications where primary technical detail is available | Maintainable delivery, fast feedback, automation, API interoperability, cost-aware architecture, payment rails and resilient services |
| South Korea — mobile ecosystems, devices and consumer experience | Samsung Developers; Naver D2; Kakao engineering publications; relevant Korean university and industry research | Android/device behavior, mobile performance, media experience, interaction quality, accessibility and efficient client engineering |
| United Kingdom — security, fintech and dependable digital services | UK NCSC; GOV.UK Service Manual; FCA and Bank of England publications where applicable; engineering publications from UK fintech firms | Secure-by-design delivery, privacy, threat modelling, financial controls, identity, auditability, service design and incident readiness |
| Nigeria and Africa — the operating environment FYNX must excel in | CBN, NIBSS, NDPC, NCC and relevant African regulators; GSMA; AfricaNenda; local payment operators' official developer docs; field evidence from Nigerian/African users | Intermittent connectivity, affordable data, low/mid-range devices, local currencies and payment methods, fraud prevention, consumer protection, language/accessibility, logistics and regulatory realities |

Do not force a country lens into every task. The best source may be an Android design guideline, an academic paper, a standard, an open-source implementation, a security advisory or a production postmortem from anywhere in the world.

## 3. Mandatory research loop for each significant task

For every material feature, defect, architecture decision, security concern, performance issue or scaling question:

1. **Frame the problem.** State the user impact, current observed behavior, expected behavior, acceptance criteria and what must not change.
2. **Inspect FYNX first.** Read the current canonical code, relevant callers, tests, architecture, PRs, issues, workflow results, project decisions and runtime evidence. Do not research a generic solution before understanding the actual failure.
3. **Find external evidence.** Search primary sources first: official documentation, engineering blogs, standards, source code, conference talks, books and peer-reviewed work. Use secondary commentary only to discover leads or compare experiences.
4. **Compare at least two viable approaches** for significant architectural or high-risk decisions. Record benefits, costs, failure modes, operational burden, migration risks and fit with FYNX.
5. **Check source quality and freshness.** Record author/organization, title, URL, publication/update date when available, access date and the exact principle used. Distinguish verified facts from interpretation. Do not cite search snippets as if a full source was read.
6. **Adapt to FYNX.** Consider Nigerian/African network quality, device capabilities, data cost, local payment rules, fraud patterns, privacy law, support capacity and the future global user base.
7. **Do the engineering maths.** Where relevant calculate storage growth, media sizes, bandwidth, request rates, peak concurrency, latency budgets, cache hit rates, database load, retry amplification, costs and capacity headroom. State assumptions and units; use estimates only as estimates.
8. **Choose and document.** Explain why the selected option is safer or more suitable than alternatives. Record rejected alternatives and revisit triggers.
9. **Implement in a bounded change.** Trace dependencies and callers; preserve API/data compatibility; avoid unrelated refactors and feature duplication.
10. **Verify proportionately.** Run static/contract, unit, integration, security, performance and emulator/device tests appropriate to the risk. Verify the exact commit being proposed.
11. **Update persistent memory.** Update this charter's linked decision/research records, workstream handoff and certification ledger when facts change.
12. **Report honestly.** Separate source-reviewed, code-inspected, build-passed, emulator-tested, backend-tested, security-reviewed and release-certified evidence.

For a trivial copy change or isolated low-risk fix, a short source check may be enough. Research depth must match risk; research is not ceremony.

## 4. Engineering quality model

Use these cross-cutting quality attributes for every feature:

- **Correctness:** explicit state transitions, invariants, validation and predictable failure behavior.
- **Security and privacy:** least privilege, server-side authorization, secure storage, safe networking, data minimization, threat modelling and auditability.
- **Reliability:** timeouts, bounded retries with backoff, idempotency, recovery, lifecycle handling, observability and graceful degradation.
- **Performance:** measured startup, rendering, memory, battery, network, database and media behavior.
- **Scalability:** capacity assumptions, pagination, backpressure, queue limits, partitioning only when justified, and load testing.
- **Maintainability:** clear ownership, modular boundaries, readable code, typed contracts, migration paths and useful tests.
- **Accessibility and usability:** clear navigation, readable layouts, accessible controls, feedback and consistency across screen sizes.
- **Cost efficiency:** total cost of ownership, data-transfer costs, storage lifecycle, compute utilization and operational effort.
- **Compliance and trust:** applicable Google Play, Android, Nigerian and international requirements; honest disclosures and user controls.
- **Recoverability:** backups, restore tests, migrations, rollback/forward-fix plans and incident response.

Security, privacy and financial integrity are never traded away to improve speed or reduce cost.

## 5. FYNX-specific design principles

1. **One product, one canonical Android delivery lane.** The canonical branch remains `integration/fynx-single-apk-20261013`. Feature branches and PR artifacts are not the shared release APK.
2. **Nigeria-first, world-ready.** Build for unreliable/expensive mobile connectivity and a wide range of Android devices, while using internationally sound contracts, security and scalability practices.
3. **Offline-aware, not fake-online.** Core screens should present trustworthy local state where appropriate. Clearly distinguish queued, pending, confirmed, failed and server-authoritative states. Never imply a payment, order, message or upload succeeded before authoritative confirmation.
4. **Money needs stronger controls.** Wallet, escrow, fees, refunds, payouts and payments require server-side authorization, idempotency, immutable/auditable transaction records, reconciliation and provider-confirmed results. AI must not access private wallet or financial history.
5. **Media is a capacity and privacy concern.** Measure image/video size, upload/download bytes, caching, retention, compression, lifecycle cleanup and access control. Use resumable/retry-safe uploads where justified.
6. **Social trust is core.** Protect private chats and account data; enforce blocking, reporting, moderation and admin permissions on the server; design abuse and recovery paths.
7. **AI is bounded and accountable.** Use least-privilege tools, explicit data boundaries, safe output handling, user-visible actions and tests for prompt injection/tool misuse. Never expose private financial information.
8. **Keep login stable.** Do not redesign or weaken the existing login flow as a shortcut. Authentication changes need explicit scope and security review.
9. **Preserve the approved visual system.** Current primary blue is Azure `#007FFF`; success green `#008000`; error crimson `#DC143C`; keep UI professional, uncluttered, accessible and responsive.
10. **No fake certification.** A local fixture proves a local journey only. A skipped backend probe does not prove live connectivity. A successful compile does not prove runtime behavior.

## 6. Architecture and decision method

- Prefer clear separation of UI, domain/business rules and data sources; follow current Android architecture guidance where it fits.
- Keep network access behind repositories/data boundaries. Use typed API models and explicit mapping between local, domain and wire representations.
- Introduce modules or services only when a stable boundary, dependency direction, ownership and measurable benefit are demonstrated.
- Prefer simple, reversible changes over speculative abstraction or premature microservices.
- Use expand/migrate/contract for database evolution where feasible; maintain backward compatibility and a rollback or forward-repair plan.
- Make retries bounded and idempotent. Avoid retry storms and duplicate financial or social actions.
- Treat observability, diagnostics and supportability as product requirements, while redacting credentials and private user content.
- Use feature flags only with clear ownership, safe defaults, expiry/removal plans and server-side enforcement where required.

## 7. Test and evidence ladder

A claim must name the highest evidence level actually achieved:

| Level | Meaning |
|---|---|
| L0 — Not inspected | No meaningful evidence yet |
| L1 — Source reviewed | Relevant implementation and dependencies inspected |
| L2 — Automated checks passed | Applicable static, contract and unit tests passed on the exact SHA |
| L3 — Build verified | Android/backend build and lint passed on the exact SHA |
| L4 — Runtime verified | Actual emulator/device journey and evidence inspected |
| L5 — Integration verified | Relevant controlled backend/provider integration verified |
| L6 — Release certified | Exact canonical release artifact, security/compliance, critical journeys, provenance and rollback evidence approved |

A skipped or unavailable dependency must be marked **BLOCKED / NOT VERIFIED**, never silently promoted to passed. Keep local-only and live-backend results separate.

## 8. Persistent records and session startup

Every session MUST, before editing:
1. Read `AGENTS.md`.
2. Read this document.
3. Read `docs/SINGLE_APK_INTEGRATION_POLICY.md`, `docs/DEVELOPMENT_POLICY_COMPLIANCE.md`, `docs/BACKEND_READY_DEVELOPMENT_POLICY.md` and the relevant feature completion/continuation contracts.
4. Inspect the latest canonical branch SHA, open PRs, latest workflow runs, changed-file overlap and current merge state.
5. Read the current workstream ledger/handoff and verify that its status is not stale.
6. Identify the last actual merged canonical change and the latest unmerged work. Never treat a PR head as merged.
7. State the exact point from which work will continue, the known blockers and the evidence still needed.

Maintain these records:
- `docs/FYNX_GLOBAL_ENGINEERING_INTELLIGENCE.md` — this research and builder standard.
- `docs/FYNX_ENGINEERING_DECISION_LOG.md` — dated decisions, alternatives, evidence, assumptions, owner/workstream and revisit triggers.
- `docs/FYNX_RESEARCH_REGISTER.md` — curated source register with topic, source quality, last checked date and practical FYNX application.
- `docs/FYNX_FINAL_INTEGRATION_LEDGER.md` and relevant workstream contracts — integration and certification status.

Do not duplicate existing ledgers. Update the authoritative existing record for that workstream and add a cross-reference where useful. Every new session must check the latest commit and workflow evidence rather than trusting an old handoff.

## 9. Source register — starting points

These are seed references, not an exhaustive or permanent whitelist. Re-check current versions and relevant subpages for each task.

### United States / global platform engineering
- Google SRE books and production engineering: https://sre.google/books/ and https://sre.google/sre-book/table-of-contents/
- Google Android architecture: https://developer.android.com/topic/architecture
- Google Android architecture recommendations and testing/performance: https://developer.android.com/topic/architecture/recommendations
- AWS Well-Architected Framework: https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html
- Meta Engineering: https://engineering.fb.com/
- Apple Developer documentation: https://developer.apple.com/documentation/

### China / consumer platform and distributed systems research
- Alibaba Cloud documentation: https://www.alibabacloud.com/help
- Alibaba Cloud architecture center: https://www.alibabacloud.com/architecture
- Tencent Cloud architecture and product documentation: https://www.tencentcloud.com/document
- ByteDance open-source projects: https://github.com/bytedance
- Apache Software Foundation projects and documentation: https://www.apache.org/

### India / delivery engineering and digital infrastructure
- Thoughtworks Technology Radar: https://www.thoughtworks.com/radar
- Thoughtworks India engineering insights: https://www.thoughtworks.com/en-in/engineering
- Thoughtworks engineering values/practices: https://www.thoughtworks.com/en-in/insights/blog/what-are-our-core-values-and-practices-building-software
- NPCI official products and technical ecosystem: https://www.npci.org.in/

### South Korea / mobile and consumer product engineering
- Samsung Developers: https://developer.samsung.com/
- Naver D2 engineering: https://d2.naver.com/
- Kakao Tech: https://tech.kakao.com/

### United Kingdom / security, privacy and service design
- UK NCSC secure development principles: https://www.ncsc.gov.uk/collection/developers-collection/principles
- UK NCSC software security guidance: https://www.ncsc.gov.uk/collection/software-security-code-of-practice-implementation-guidance
- GOV.UK Service Manual: https://www.gov.uk/service-manual
- FCA Handbook: https://www.handbook.fca.org.uk/

### Nigeria / Africa
- Central Bank of Nigeria: https://www.cbn.gov.ng/
- Nigeria Inter-Bank Settlement System: https://nibss-plc.com.ng/
- Nigeria Data Protection Commission: https://ndpc.gov.ng/
- Nigerian Communications Commission: https://www.ncc.gov.ng/
- GSMA: https://www.gsma.com/
- AfricaNenda: https://www.africanenda.org/

### Cross-cutting standards
- OWASP Mobile Application Security Verification Standard: https://mas.owasp.org/MASVS/
- OWASP Application Security Verification Standard: https://owasp.org/www-project-application-security-verification-standard/
- DORA capabilities and delivery research: https://dora.dev/capabilities/
- Android offline-first architecture: https://developer.android.com/topic/architecture/data-layer/offline-first

## 10. Research discipline and prohibited shortcuts

- Do not fabricate sources, quotations, benchmarks, company practices or claims that a source was read.
- Do not treat nationality as proof of expertise. Cite the actual engineering team, author or institution.
- Do not rely on vendor marketing alone for a high-risk decision; seek standards, implementation details, independent evidence or production experience.
- Do not copy a large-company architecture without accounting for FYNX's current scale, team capacity, budget and operating environment.
- Do not over-engineer, prematurely split services, or add dependencies without lifecycle and cost analysis.
- Do not weaken tests, hide failures, invent test data that looks like production, or label a blocked flow as complete.
- Do not make production, payment, account, or database changes without the required explicit authorization and safety plan.
- Do not disclose secrets in research notes, commits, logs or reports.

**Final operating principle:** Research broadly, verify locally, calculate honestly, engineer carefully, test the real journey, preserve the canonical product, and leave every session with a more accurate shared understanding of FYNX.
