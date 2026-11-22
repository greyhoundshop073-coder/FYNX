# FYNX Engineering Decision Log

This is the durable record of material engineering choices. New entries are append-only unless a decision is explicitly superseded. Each entry should record date, scope, evidence, alternatives, decision, risks and revisit triggers.

## 2026-11-22 — Adopt global research-led engineering as the FYNX builder standard

- **Scope:** All FYNX workstreams and future coding sessions.
- **Decision:** Adopt `docs/FYNX_GLOBAL_ENGINEERING_INTELLIGENCE.md` as the mandatory research, architecture and engineering operating system. `AGENTS.md` requires every contributor and ChatGPT session to load it before editing.
- **Evidence used:** Existing FYNX engineering standards and integration policy; Google SRE guidance; AWS Well-Architected pillars; Android architecture/offline-first guidance; UK NCSC secure development principles; Thoughtworks engineering practices and Technology Radar.
- **Alternatives considered:** (1) Keep informal guidance only in chat; rejected because it is not reliably available to future sessions. (2) Add broad country stereotypes or blindly copy large-platform architecture; rejected because practices must be attributed to real sources and adapted to FYNX. (3) Replace existing FYNX standards; rejected because the repository already contains valuable engineering, security and release controls.
- **Decision rationale:** Make research repeatable and persistent while preserving existing canonical branch, test, security, compliance and single-APK rules.
- **Constraints:** No direct changes to application source, backend, production data, login, payments or deployment in this documentation task.
- **Revisit when:** New official standards emerge, FYNX architecture or operating constraints materially change, or a decision is contradicted by measured production evidence.

## 2026-11-22 — Current continuation checkpoint: PR #580

- **Canonical branch:** `integration/fynx-single-apk-20261013`.
- **Latest base SHA observed in PR #580 metadata:** `dfb9b83d420b41512224ad7180eab797b9664f6d`. Re-check branch head at next session start; do not assume this remains current.
- **Latest work branch:** `fix/canonical-local-runtime-visual-modes-20261122`.
- **Latest work commit:** `2d0efb4861d49536781d6b2820769ef167ca9f23`.
- **PR:** [#580](https://github.com/greyhoundshop073-coder/FYNX/pull/580), OPEN and UNMERGED when checked. It is based on the canonical integration branch.
- **What the PR changes:** Normalize reused debug-local session classification so Home Feed local fixtures seed; fix camera Text-tool runtime selectors to distinguish clickable actions from visible editable nodes; enable canonical-base PRs to execute the intended local emulator/runtime workflow and retain screenshots/UI hierarchies; keep live backend probe separate.
- **Latest Android workflow:** [run #45047880919](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/45047880919), head SHA above, in progress at checkpoint. Fast build/test/lint and full build/test/lint completed successfully; KVM, FFmpeg and Pillow setup completed; actual Android instrumentation and UI capture was still running. Screenshot uploads, APK reassembly and exact-commit artifact checks had not yet completed.
- **Known previous failure:** Run #45036164862 reported missing Home inline video/comment fixtures due to a local-session label mismatch; camera Text overlay verifier targeted a non-clickable semantic label as a clickable control. Marketplace token, Wallet settlement and Product Details were unavailable in local-only mode and must remain explicitly backend/data BLOCKED, not fabricated as successful.
- **Next required action:** Poll run #45047880919; inspect instrumentation result, captured screenshots/UI hierarchy and failure logs. If the emulator journey passes, verify artifact provenance and remaining PR checks. Do not merge #580 until all required checks are green and runtime evidence is reviewed.
- **Do not claim:** PR #580 is merged; the canonical APK is certified; Marketplace/Wallet live backend flows passed; or any artifact is the shared canonical release APK.
- **Safety:** Preserve login and other workstreams; no production database, wallet, payment or deployment changes.

## Entry template

### YYYY-MM-DD — Short decision title
- **Scope:**
- **Problem / user impact:**
- **Evidence and sources:**
- **Options compared:**
- **Decision and rationale:**
- **Implementation / PR / SHA:**
- **Verification:**
- **Risks / blockers:**
- **Revisit trigger:**


## 2026-11-22 — Home, Chat/Groups and runtime integrity grouped repair

- **Scope:** PR #580, canonical candidate branch `fix/canonical-local-runtime-visual-modes-20261122`; no direct canonical merge.
- **Problem / user impact:** Installed-app certification found ambiguous Groups navigation, Home Feed detection that confused ordinary video actions with modal screens, nondeterministic Discovery fixture mode, and weak account/group state isolation in read-receipt handling.
- **Evidence:** Runtime reports from run #45036164862 and #45047880919; exact source and test inspection of `ChatsPanel.kt`, `FynxRemoteHomeSocialPanel.kt`, `FynxGroupRemoteClient.kt`, `FynxGroupsPanel.kt`, and the runtime verifier. The current exact-head Android run #45058308486 had passed source/contract gates and was in Fast build/test/lint at the checkpoint; Chat/Groups parity run #45058308496 had passed its static contracts and was in Android regression build; Backend CI #45058308499 passed, with live production reachability skipped.
- **Options compared:** (1) Relax runtime checks or silently skip inaccessible screens — rejected because it would produce false certification. (2) Use exact accessibility semantics, deterministic disposable fixtures, and explicit post-navigation restoration checks — selected. (3) Keep one process-wide receipt capability flag — rejected because it can leak capability state between accounts/groups; selected account-and-group-scoped state with stale-session guards and final snapshot persistence.
- **Decision and rationale:** Treat Home post actions as feed content, not modal markers; make the Groups tab announce a unique `Open Groups` action; seed `FOR_YOU` in the disposable emulator only; persist authoritative group receipt updates within the correct account/group namespace.
- **Implementation / PR / SHA:** PR #580, head `e1c3bcf149dff2246ca7123867910958323b0037`; grouped implementation commit `7b71223364c7bf4f457ae87e303ddb78440ed38f`, followed by harness/handoff/contract corrections `d2130fcf391d59c7fc5969a80c7b574790a8c8ec`, `781da8bbdef2abe2fdf60c6cdf033aaa2f0474ff`, `f31159cef1e962b672a5f5e785322e0958a5363e`, `e1c3bcf149dff2246ca7123867910958323b0037`.
- **Verification:** Latest exact-head source gates passed, including Camera runtime-script syntax, Home inline video/Discovery contracts, Home lifecycle/certification, Groups permission security, Status creator source contracts, Marketplace/Wallet contracts and H12. Backend CI passed on #45058308499. Chat/Groups parity static contracts passed on #45058308496. Android build/test/lint and parity Android regression build were still running; installed-app runtime evidence had not yet completed on this head.
- **Risks / blockers:** A previous installed-app run showed emulator/ADB `device offline` and loss of the emulator session; do not misreport this as an app crash without crash/ANR evidence. Real Railway/Neon, multi-device message delivery, FCM, remote media, Wallet and Paystack flows remain live-service/provider blocked. Production reachability was skipped in Backend CI.
- **Revisit trigger:** Complete exact-head build, instrumentation, emulator screenshots/hierarchy and runtime report; investigate any ADB device-offline event separately from app failures; then reconcile the remaining PRs before final canonical APK certification.

### 2026-11-22 — Installed-app runtime evidence and grouped follow-up

- **Run:** GitHub Actions #45059210784 on PR #580 head `7ef98a30519d4f6c50c8eafca589d4903707e402`; installed-app runtime result RED. Android instrumentation: 83 tests, 0 failed, 1 skipped. The backend reachability probe was skipped and is not certified.
- **Evidence correction:** The real Groups screen was captured. Its visible action was `＋ New group`, while the verifier required `Create group`; this was a selector/accessibility contract mismatch, not a missing Groups destination. The group button now exposes the stable `Create group` accessibility label.
- **Private chat:** The disposable offline message remained in the EditText after the previous coordinate tap. The send control now exposes an explicit accessibility click action; the verifier first exercises the declared IME Send action, then retries the accessible send control and distinguishes composer text from a rendered conversation bubble.
- **Home/Discovery:** Three account-scoped local video fixtures were present, but a three-post test feed placed Discovery after the third post. When Discovery fixtures are enabled and the feed has at most three posts, place the Discovery insertion after the first post so the destination is reachable in a short runtime pass.
- **Remaining red evidence:** Home video aspect-ratio scan measured only the portrait preview (0.562) and did not reach the Discovery viewer; the Home final-navigation assertion also failed. The Status creator pass found no running app process after the Camera capture stage. No FATAL/ANR marker was present in the captured logcat; investigate the camera/session handoff without claiming an app crash or a successful Status test.
- **Next:** Re-run exact-head Android build, instrumentation and emulator capture after these grouped fixes; inspect the next real screenshots/hierarchy and only then close the remaining Home/Discovery, Camera/Status and Chat runtime gates. Live backend/payment flows remain separate.
- **Release boundary:** This is a feature-branch candidate only. No canonical merge, production infrastructure/data change or official APK publication was performed.

### 2026-11-22 — Neon Object Storage candidate integration

- **Source:** PR #572, exact head `494be03a025955825ef75579fae1e4e574f224fe`; backend CI run #45059759732 passed. Android build and 83-test instrumentation completed with 0 failed and 1 skipped in run #45059764001; the installed-app visual job remained RED on unrelated Chat/Groups, Home video, Camera Text/style, and backend-authenticated Marketplace/Wallet checks.
- **Integrated into:** PR #580 candidate branch via a feature-to-feature merge commit. The nine storage files are isolated from the Chat/Home/Camera fixes; no production host, credentials, bucket or user data was changed.
- **Implementation:** Neon and R2 direct signed uploads, provider-pinned upload intents and completion/cleanup, object HEAD/size/MIME validation, trusted HTTPS host validation on Android, bearer-token exclusion from object-storage requests, PostgreSQL legacy fallback, and backend/Android tests.
- **Evidence boundary:** Backend contracts and Android compilation/instrumentation passed; live Neon PUT/HEAD/authorized GET/expiry/cleanup have NOT been exercised because a healthy configured testing service and branch-scoped Neon credentials are unavailable. Do not claim live storage is operational yet.
- **Next:** Run the combined candidate's backend, Android, and runtime gates; keep the live storage probe as a separate test-service gate.

### 2026-11-22 — Selective Chat reliability port from #535

- Reviewed #535 per-file rather than merging its 274-commit divergent history. Ported only three unique tests and two focused app repairs that are compatible with the current candidate.
- Private-chat history failure now checks the durable account-scoped local store before marking a conversation new; cached messages remain visible with a truthful refresh warning. Added focused JVM tests for saved-history and empty-history cases.
- In-app incoming-message alerts now share the same per-chat notification/mute policy as FCM, including the user's chat sound and vibration preferences. Added policy tests for disabled notifications and muted chats.
- Added local deterministic instrumentation for group reconnect drain: successful group sync replaces the pending message with the server-confirmed ID/status; failed group creation keeps both group and message queued and never sends the message prematurely.
- These tests use local injected transports and disposable account namespaces; no live backend, production group, or real user is involved.
- The new tests and source changes are now part of the PR #580 feature candidate. Exact-head Android and Chat closure workflows must verify them before acceptance.
