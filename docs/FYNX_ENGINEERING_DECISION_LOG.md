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
