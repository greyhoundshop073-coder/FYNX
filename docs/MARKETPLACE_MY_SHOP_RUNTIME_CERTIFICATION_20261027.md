# Marketplace and My Shop — Runtime Certification

Date: 2026-10-27
Canonical branch: `integration/fynx-single-apk-20261013`

## Scope

This certification run is for the existing Marketplace and My Shop implementation only. It must not modify Avatar/Profile, Chat/Groups, Status/Story, shared backend configuration, database settings, credentials, or billing.

## Why a full runtime run is required

The Android build for checkout quote validation completed green at workflow run [#40469451620](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/40469451620), but its workflow condition skipped the full build, emulator instrumentation, and runtime screenshot upload because this was not a `main` push or an explicit `workflow_dispatch` with `full_runtime=true`. The APK artifact exists, but that run did not capture runtime screenshots.

The Android workflow's supported `[home-cert]` push condition enables the full build, emulator and screenshot-upload steps. This documentation-only commit requests runtime certification after the latest Marketplace/My Shop selected-state UI changes; it does not change application code or weaken tests.

## Required evidence

- Full Gradle build, unit tests, lint, and Android instrumentation results.
- Genuine emulator screenshots and UI hierarchy files for Marketplace discovery, product detail, and My Shop/seller center, if the current debug-only navigation can reach them.
- If navigation is blocked, preserve the failing step and exact reason; do not fabricate product listings, payments, seller records, or successful backend states.
- Exact checked-out commit and APK SHA-256.
- Explicitly distinguish UI/navigation evidence from authenticated live backend, payment, payout, and delivery verification.

## Changes included in current certification source

- PR #376 merged checkout retry idempotency and strict server-quote validation.
- PR #379 merged accessible Azure selected-state colors across Marketplace and My Shop controls.

## Current known limitations

- The latest normal Android run's Marketplace-specific static/source contract checks passed, but its screenshot steps were skipped.
- An earlier APK artifact was built from synthetic merge commit `3bb71ec5f9434959d2f67c4002cb1d56a9933d59`; APK SHA-256: `d2c7e7ae7dbea02ce40c72e298719030a57cf3e444b63997949d9d2c5c135f4d`. It is not the latest post-PR-#379 canonical APK and is not yet the full runtime-certified APK.
- Live transaction and backend/database verification remain separate from this Android-only certification.

## Completion rule

Do not mark Marketplace/My Shop fully complete until runtime evidence is inspected and remaining app-side defects are fixed in grouped, tested changes. Do not claim live backend or transaction success without authoritative evidence.
