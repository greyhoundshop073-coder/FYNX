# FYNX Canonical Android App and APK Policy

**This policy is mandatory for every contributor, automation, AI coding agent, and ChatGPT workstream working in this repository.**

## The one official app

FYNX is **one Android application**, not a collection of separate Marketplace, Wallet, Banner, Network, Chat, or other APKs.

- **Canonical Android source branch:** `integration/fynx-single-apk-20261013`
- **Repository:** `greyhoundshop073-coder/FYNX`
- **Official installable APK name:** `FYNX-Android-Official.apk`
- **Official release lane:** Android builds produced from the canonical branch only.

Every feature belongs in this same Android application and must ultimately be integrated into the canonical source branch. Workstream names describe ownership or scope; they do not authorize separate apps or APK release tracks.

## Instructions for every ChatGPT chat, agent, and contributor

1. Before editing, pushing, opening a PR, or building an APK, read this policy and inspect the latest canonical branch. Do not rely on an old branch, old APK, prior chat summary, or remembered commit as the current source of truth.
2. Treat `integration/fynx-single-apk-20261013` as the only integration target for Android app work. Feature branches are permitted for safe parallel work, but every Android feature PR **must target this canonical branch**, not `main`, an old release branch, or another workstream branch.
3. Rebase or reconcile feature work against the latest canonical branch before merging. Never force-push over other work, overwrite another workstream's changes, or merge a stale branch without reviewing its complete diff and resolving conflicts.
4. Do not create, publish, label, or distribute a workstream-specific APK. In particular, do not publish separate Marketplace, Wallet, Banner, Network, Chat, Store, or other feature APKs.
5. Pull-request and feature-branch CI may compile and test code, but it must not present its APK as the official app or publish a distributable official APK.
6. Only a successful Android build from the canonical branch may publish the official installable APK. Give that artifact the fixed name `FYNX-Android-Official.apk`. Include the exact source branch, full commit SHA, build run, and test result alongside it.
7. A build from any other branch or commit is a **test build only**. Label it clearly as non-release/test output and do not tell the owner to install it as the current FYNX APK.
8. Do not call an APK “latest”, “official”, “shared”, or “complete” unless its source commit is verified against the current canonical branch and its required checks passed.
9. Before announcing completion, verify that the feature is present in the canonical branch and in the official APK build. A green feature-branch build alone is not proof that the feature reached the official app.
10. If the canonical branch is unavailable, blocked, or changed during work, stop the release step, report the exact blocker, and coordinate integration. Do not silently switch to another branch.

## Required pull-request description

Every Android feature PR must state:

- **Target branch:** `integration/fynx-single-apk-20261013`
- **Workstream:** the feature area being changed
- **Canonical base commit:** the full SHA used when the work was prepared
- **Files and behavior changed:** concise summary
- **Protected areas checked:** confirm unrelated workstreams were not changed
- **Validation:** exact tests and workflow run links/results
- **APK status:** “No separate APK published; this change is intended for the single canonical FYNX APK.”

## Release rule

There is one official FYNX Android APK release lane. A release is valid only when the canonical branch build succeeds and its artifact is traceable to the exact canonical commit. Old APKs remain historical test artifacts; they must never be confused with the current official APK.

**If in doubt: do not create another APK. Integrate the work into the canonical FYNX app and use the single official release lane.**
