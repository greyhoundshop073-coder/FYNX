# FYNX — Mandatory Google Play and Platform Compliance Rules for All Workstreams

**Applies to:** Every FYNX feature, bug fix, refactor, backend change, UI change, AI integration, release, and parallel ChatGPT/Codex/human workstream.  
**Effective:** 2026-11-09  
**Canonical Android delivery branch:** `integration/fynx-single-apk-20261013`

This is a standing engineering requirement, not a launch-only checklist. Every new feature must be designed and implemented so that FYNX can truthfully satisfy Google Play policies and applicable platform, privacy, security, consumer-protection, and payment-provider requirements. A feature is not complete merely because its screen or button exists.

## 1. Required workflow for every contributor

Before starting work:
1. Read the root `AGENTS.md`, `docs/SINGLE_APK_INTEGRATION_POLICY.md`, and this document.
2. Inspect the latest canonical branch head, open PRs, relevant source, tests, and current feature behavior.
3. Identify whether the change touches personal data, sensitive permissions, user-generated content, payments, financial features, children/age targeting, AI, ads, subscriptions, or third-party SDKs.
4. Check current official policy requirements when the change involves a regulated or policy-sensitive feature. Prefer official Google Play/Android/provider documentation; record source links and the date checked.
5. Do not assume an existing screen, endpoint, dependency, or prior PR is compliant without verifying its actual behavior.

For every PR:
- State the user-facing behavior and the policy/security implications.
- Include tests for success, failure, denied permission, abuse/misuse, offline, and deletion paths as applicable.
- Update this compliance register or the relevant detailed checklist when a feature introduces/changes data collection, sharing, retention, permissions, moderation, payments, AI processing, or SDKs.
- Record unresolved risks as blockers; never mark an item compliant because it is planned.
- Do not silently disable, hide, or misrepresent functionality to bypass policy.
- Preserve canonical branch, PR, grouped-delivery, CI, and verification rules in `AGENTS.md`.

## 2. Privacy and user data — applies to all features

- Collect only data necessary for a clearly explained user-facing purpose.
- Do not collect, access, upload, share, or retain data merely because a permission or SDK makes it technically possible.
- Request runtime permissions contextually, after explaining the feature need; handle denial and revocation gracefully.
- Prefer Android Photo Picker/scoped access over broad media permissions where suitable.
- Never collect contacts, location, microphone, camera, photos, files, or device identifiers without a legitimate feature need and appropriate user notice/permission.
- Do not access location in the background unless an approved core feature genuinely requires it and all policy requirements are met.
- Use secure transport, authorization, least privilege, server-side validation, safe storage, and appropriate retention.
- Never put passwords, OTPs, access tokens, payment secrets, private keys, or other credentials in source code, logs, analytics, crash reports, AI prompts, screenshots, or client-visible configuration.
- Every feature that introduces data processing must document: data category, source, purpose, legal/consent basis where applicable, recipient/processor, storage location, retention, deletion behavior, and security controls.
- Data safety answers and privacy disclosures must match the actual app, backend, and every included SDK—not assumptions or generic templates.

## 3. Account creation, privacy policy, and deletion

- If users can create FYNX accounts, provide a discoverable in-app path to request account deletion and a public web resource that lets users request deletion without reinstalling the app.
- Deletion must cover associated account data across FYNX-controlled systems and include appropriate deletion requests to processors. Clearly disclose any lawful/security/fraud-prevention retention and its duration.
- Logout, suspension, hiding a profile, or deactivation is not account deletion.
- Keep a public HTTPS privacy-policy webpage accessible worldwide without login or geographic restriction; link it in the app and Play listing.
- The policy must accurately identify FYNX/developer, data categories, collection/use/sharing, processors, security, retention, user controls, deletion, and privacy contact.
- Never invent a privacy policy or deletion URL. Do not publish a policy until the app/backend/SDK data inventory is verified.
- Update privacy policy, Data safety, and deletion disclosures whenever implementation or vendors change.

Official reference: https://support.google.com/googleplay/android-developer/answer/13327111

## 4. Social features and user-generated content

For posts, comments, replies, profiles, messages, groups, live/audio/video interactions, status/stories, marketplace listings, and AI-generated/shared content, as applicable:
- Provide accessible reporting mechanisms for objectionable content and abusive users.
- Provide user blocking and enforce the block across relevant interactions.
- Publish and enforce terms/community standards prohibiting objectionable content and abusive behavior.
- Operate a real moderation/review process with escalation, response, and repeat-offender handling.
- Use appropriate safeguards for direct interactions and user-generated content.
- Do not ship report/block buttons that do not submit actionable reports or enforce the block.
- Test report/block behavior, including edge cases and abuse attempts.

Official reference: https://support.google.com/googleplay/android-developer/answer/9876937

## 5. Marketplace, wallet, payments, and financial functionality

- Classify every transaction before implementation: physical goods/services, digital goods/features, peer-to-peer transfers, stored value, escrow, credit, investment, crypto, or other financial service.
- Apply the current Google Play Payments policy to the exact product/transaction type. Do not assume one payment provider or checkout approach is permitted for every category.
- Financial products/services may trigger mandatory organization-account and additional policy requirements. Escalate before shipping.
- Do not activate real-money wallet funding, transfers, withdrawals, escrow, or other financial services until provider approval, applicable Nigerian and other target-market legal/regulatory requirements, and Google Play policy treatment have been checked.
- Confirm transactions from trusted server/provider webhooks or authenticated server-to-server verification; never trust client redirects or client-supplied success flags.
- Use idempotency, immutable/auditable ledger entries, reconciliation, clear pending/failed/reversed states, and appropriate fraud controls.
- Explain fees, seller identity, product condition, delivery, cancellation, refund, disputes, and support.
- Never send balances, transaction histories, payment credentials, or wallet secrets to FYNX AI.

Official reference: https://support.google.com/googleplay/android-developer/answer/10281818

## 6. AI features and external processors

- Document each AI provider, data sent, purpose, retention, training/use controls, and user disclosure.
- Minimize prompts and remove unrelated personal/sensitive data.
- Exclude wallet/payment secrets, authentication data, and private financial histories from model context and telemetry.
- Make AI output clearly identifiable where appropriate; provide reporting and safety controls for harmful output.
- Handle provider/backend failure honestly. Never show fabricated output, false success, or a fake connected state.
- Re-check provider terms and Google policy when adding a model, SDK, or new AI capability.

## 7. Ads, subscriptions, and monetization

- Declare ads accurately in Play Console and ensure ad SDK behavior matches privacy/Data safety disclosures.
- Do not disguise ads as ordinary content or mislead users about sponsored placements.
- Make pricing, renewal, cancellation, trial, and refund terms clear before purchase.
- Apply the current Google Play billing requirements to digital features/content and subscriptions; do not hardcode a bypass.
- Re-check policy and regional availability before enabling a monetization method.

## 8. Children, age rating, and content

- Do not select a child target audience unless FYNX genuinely targets children and meets the applicable Families requirements.
- Complete the content-rating and target-audience declarations truthfully.
- Gate or restrict age-sensitive content/features as required.
- Do not expose children to prohibited ads, data practices, or unsafe interactions.
- Reassess age rating when adding new content, live interaction, user-generated content, or monetization.

## 9. Permissions, Android behavior, and quality

- Keep manifest permissions minimal and justified; remove unused permissions and SDKs.
- Explain sensitive access in context and support permission denial/revocation.
- Test current Android versions, target SDK behavior, notification permission, media picker, camera/mic, location, background work, exact alarms, and app links.
- Verify accessibility, readable text, touch targets, loading/error/empty states, offline recovery, crash-free startup, ANRs, battery/data use, and low-storage behavior.
- Every visible action must work or be clearly unavailable with an honest explanation.
- Never claim a test/build/deployment is green without actual evidence.

## 10. Play Console and release requirements

Before production submission, verify:
- Correct developer account type, identity, contact, and device verification.
- Correct app/package ID and signed Android App Bundle (AAB).
- Privacy policy URL in app and store listing.
- Account deletion in app and public deletion web resource.
- Accurate Data safety and data-deletion declarations covering all SDKs and backend processing.
- Content rating, target audience, ads, financial features, app access, and other required declarations.
- Store listing text, icon, feature graphic, and genuine release-candidate screenshots.
- Reviewer instructions and working test credentials for restricted features.
- Stable backend, required integrations, support contact, and operational moderation/support.
- Current Play policies checked immediately before submission.

For new personal accounts created after 2023-11-13, Google currently requires at least 12 testers continuously opted in to a closed test for at least 14 days before applying for production access. Confirm the current rule and account eligibility in Play Console; do not start the qualifying clock with a broken or incomplete release candidate.

Official references:
- Account types: https://support.google.com/googleplay/android-developer/answer/13634885
- Account registration and fee: https://support.google.com/googleplay/android-developer/answer/6112435
- New personal-account testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Prepare app for review: https://support.google.com/googleplay/android-developer/answer/9859455
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Developer policy center: https://play.google.com/about/developer-content-policy/

## 11. FYNX-specific release gates

The following must be verified against actual implementation before production:
- Account deletion end-to-end, including public web request path and backend/storage cleanup.
- Public privacy policy based on a complete app/backend/SDK data-flow inventory.
- Report/block/moderation coverage across every user-generated-content surface.
- Permission justification and runtime behavior for location, contacts, microphone, camera, media, notifications, and exact alarms.
- Marketplace listing safety, seller transparency, order/refund/dispute flows.
- Wallet/payment classification, provider approvals, server-side verification, ledger correctness, and regulatory review.
- AI data boundaries, provider disclosures, failure states, and no financial-secret exposure.
- Support channel, reviewer test access, genuine screenshots, AAB signing, and stable production backend.
- Functional regression audit across all major user journeys and devices.

## 12. Contributor handoff rule

Every new chat/agent must be told to read:
1. `AGENTS.md`
2. `docs/SINGLE_APK_INTEGRATION_POLICY.md`
3. `docs/DEVELOPMENT_POLICY_COMPLIANCE.md`
4. `docs/GOOGLE_PLAY_READINESS.md`

The next contributor must inspect the latest canonical branch and current PRs, then continue unresolved items. Do not duplicate work, create competing APK branches, or assume checklist entries are complete.

**Definition of done:** feature works as intended, required policy/security controls are implemented, tests/evidence pass, disclosures are updated, and unresolved risks are explicitly reported. A policy checklist is not a substitute for legal advice or Google approval.
