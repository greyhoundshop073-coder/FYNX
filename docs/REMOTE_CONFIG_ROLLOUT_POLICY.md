# FYNX Remote Configuration and Safe Rollout Policy

**Canonical app:** `integration/fynx-single-apk-20261013`  
**Provider selected:** Firebase Remote Config, using the existing Firebase project and Android Firebase BoM  
**Status:** Android client foundation implemented; Firebase Console parameter publication and release verification remain pending.

## Purpose

Allow FYNX to change approved configuration values and enable/disable optional experiences that are already present in the installed APK, without requiring a Play Store download for every adjustment.

Remote Config does **not** download or execute new application code. New native Android capabilities, new screens, resources, permissions, SDKs, or substantial navigation changes still require a normal APK/AAB release.

## Client behavior

- The app starts with in-APK defaults; all experimental switches default to `false`.
- Remote Config initializes from the process-level `FynxApplication` class and never blocks the first screen.
- Production fetches use a 12-hour minimum interval; debug builds may fetch on every launch for controlled testing.
- The client also listens for Firebase real-time configuration updates while its process is active.
- Successful updates are published through a read-only Kotlin `StateFlow` for future UI consumers.
- A failed fetch, unavailable Firebase service, invalid key, or offline device leaves the app on defaults or the last successfully activated values.
- Unknown keys always evaluate to `false`.

## Allowlisted experiment parameters

| Parameter | Default | Intended scope |
|---|---:|---|
| `home_feed_experiment_enabled` | `false` | Future optional Home experiments |
| `marketplace_discovery_experiment_enabled` | `false` | Future optional Marketplace experiments |
| `ai_voice_experiment_enabled` | `false` | Future optional AI voice experiments |

These keys are reserved only. No existing Home, Marketplace, or AI feature is hidden or changed by this foundation. A flag must not be turned on until its consuming code and regression tests are merged.

## Non-negotiable safety rules

1. Never use Remote Config as authorization or as a security boundary.
2. Never remotely disable login, account recovery, safety reporting/blocking, Wallet, payment verification, orders, refunds, disputes, or other core user protections.
3. Never put secrets, API keys, access tokens, payment details, balances, user histories, or personal data in parameter names or values. Client configuration is inspectable by users.
4. Use Remote Config only for typed, allowlisted values and optional experiences already shipped in the APK.
5. Keep local defaults safe and preserve the last activated values during outages.
6. Test every flag both enabled and disabled before gradual rollout; keep a documented rollback value.
7. Do not use remote flags to bypass Google Play review, payments rules, privacy disclosures, or app permissions.

## Privacy and Google Play inventory

The Remote Config Android SDK uses Firebase Installations to create an installation ID required for fetch requests and periodically communicates with Firebase. This implementation does not add Firebase Analytics and does not use Analytics audiences or user properties.

Before public release, reconcile this SDK and its Firebase Installations data flow in the canonical privacy/data inventory and Play Console Data safety answers. The exact classification, disclosure, retention, and deletion treatment must be verified against the complete app/SDK inventory; this document is not a substitute for that review.

Official Firebase references:
- Android setup and Remote Config: https://firebase.google.com/docs/remote-config/android/get-started
- Remote Config API and Firebase Installation ID behavior: https://firebase.google.com/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig

## Operational rollout

1. Publish the three parameters in Firebase Remote Config with the documented defaults (`false`).
2. Verify the configuration with an internal/debug build before enabling a consumer.
3. Add a feature consumer only in a separate reviewed change with enabled/disabled and offline regression tests.
4. Enable for a limited, controlled audience only after confirming the applicable Firebase condition does not require an unapproved Analytics data flow.
5. Monitor crash/error and support signals, then expand deliberately.
6. Roll back by setting the relevant optional flag to `false`; real-time clients can receive the change while connected. Offline devices keep their last activated value until they reconnect, so flags must never protect critical flows.

## Current limitations

- The Firebase project and SDK configuration exist in the repository, but Remote Config parameter publication has not been verified in the Firebase Console.
- There is no separate Firebase staging project verified for this work; do not claim one exists.
- This foundation does not change existing screens until a future feature explicitly consumes one of the allowlisted flags.
- The expired FYNX API backend is not required for Remote Config, but Firebase service availability and configuration are still required for remote changes.
