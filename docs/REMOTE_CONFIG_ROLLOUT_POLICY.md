# FYNX Remote Configuration and Safe Rollout Policy

**Canonical app branch:** `integration/fynx-single-apk-20261013`  
**Provider:** Firebase Remote Config using the Firebase project already configured for the Android package `com.fynx.app`  
**Client foundation:** integrated in this feature PR; production template publication and live Console verification remain external release steps.

## Purpose and exact capability

Remote Config lets FYNX change approved configuration values and switch optional experiences that are already implemented and shipped inside the installed APK. This allows a developer to adjust a supported flag or approved text/config value without requiring a Play Store download for that particular adjustment.

It does **not** download or execute new Kotlin/Java code. A new native screen, new app capability, new SDK, permission, resource, or substantial navigation change still requires an APK/AAB update. Server-fed announcements, promotional content, or catalogue data can be refreshed without an APK only when the installed app already contains the code to display them.

## Client behavior

- `FynxApplication` initializes Remote Config at process startup; `MainActivity` and the existing sign-in flow are unchanged.
- Initialization and network fetches are asynchronous and do not block the first screen.
- Three typed boolean parameters are allowlisted. All in-app defaults are `false`.
- Debug builds use a zero-second minimum fetch interval for controlled testing. Production uses a 12-hour minimum interval.
- Cached activated values remain available offline; a failed fetch or realtime connection does not prevent app startup.
- A realtime listener activates changes to allowlisted parameters while the process is connected. If realtime is unavailable, the app can receive changes on a later fetch.
- A read-only Kotlin `StateFlow` exposes only the allowlisted flags to future UI consumers.
- Unknown parameter names always evaluate to `false`.

## Allowlisted parameters

| Parameter | Type | In-app default | Intended future scope |
|---|---|---:|---|
| `home_feed_experiment_enabled` | Boolean | `false` | Optional Home Feed experiment |
| `marketplace_discovery_experiment_enabled` | Boolean | `false` | Optional Marketplace discovery experiment |
| `ai_voice_experiment_enabled` | Boolean | `false` | Optional AI voice experiment |

These keys are reserved. No existing Home, Marketplace or AI behavior is currently controlled by them. A flag must remain false until its consumer code and enabled/disabled regression tests are included in a separate reviewed change.

The source-controlled client template is `firebase/remoteconfig.template.json`. It is a prepared template file, **not proof that the parameters have been published** to Firebase.

## Non-negotiable safety rules

1. Never use Remote Config for authorization, identity, access control, payment verification or a security boundary.
2. Never remotely disable login, account recovery, reporting/blocking, Wallet, payments, orders, refunds, disputes or other core user protections.
3. Never place secrets, API keys, access tokens, payment details, balances, private histories or personal data in parameter names or values. Client-delivered configuration is inspectable by app users.
4. Only add a key to the client allowlist after review, type definition, in-app default and tests exist.
5. Keep in-app defaults safe and preserve the last successfully activated configuration through network outages.
6. Test enabled, disabled, offline and rollback behavior before activating any consumer for users.
7. Do not use Remote Config to bypass Google Play review, payments policy, permissions or privacy disclosures.
8. Activate screen-affecting changes at a safe UI lifecycle point; do not surprise users by changing a critical interaction mid-task.

## Firebase and privacy inventory

- The canonical repository already contains `app/google-services.json` for Firebase project `fynx-f0868` and Android package `com.fynx.app`.
- The Firebase Remote Config Android SDK uses Firebase Installations for an app-installation identifier and communicates with Firebase to fetch configuration. This must be included in the app's full SDK/data-flow inventory and Google Play Data safety assessment before public release.
- This change adds Firebase Remote Config only. It does not add Firebase Analytics, Analytics audiences, user properties or analytics-based personalization.
- No separate staging Firebase project has been verified for this feature.
- The Remote Config client template and the Firebase Realtime Remote Config API/service must be checked and enabled in the Firebase/Google Cloud project before remote publication and live realtime behavior can be certified.
- No Firebase Console publication is claimed by this repository change. The template must be imported/reviewed in the correct Firebase project and its published version recorded as release evidence.

## Operational rollout

1. Confirm the selected Firebase project and open Remote Config for the Android app.
2. Import/review `firebase/remoteconfig.template.json`; retain all three values as `false`.
3. Validate and publish the client template in Firebase Console (or the approved Firebase release process), then record its template version and publication date.
4. Verify the Realtime Remote Config API is enabled for the project; test a config update from a debug/internal build.
5. Add a consumer only after the corresponding feature is already present in the APK and the feature change supplies tests for both flag states.
6. Roll back an optional experiment by publishing its flag as `false`. Connected clients can receive realtime changes; offline clients retain their last activated value until they reconnect.
7. Keep Data safety and privacy disclosures synchronized with the complete app/SDK inventory before production release.

## Verification status

- Repository has the existing Firebase project configuration for `com.fynx.app`: verified from `app/google-services.json`.
- Android SDK dependency, application initializer, allowlist and fail-closed tests: included in this PR and subject to CI.
- Firebase Console parameter publication: not verified from this repository.
- Realtime Remote Config API enabled state: not verified from this repository.
- Production remote flag activation on a real device: not yet certified.
- Public Play Data safety/privacy declarations for the new SDK data flow: require an owner-controlled inventory review before release.

## Official references

- Android SDK setup and realtime updates: https://firebase.google.com/docs/remote-config/android/get-started
- Client parameter priority, data types and defaults: https://firebase.google.com/docs/remote-config/parameters/client
- Client template import, version history and rollback: https://firebase.google.com/docs/remote-config/templates/client
- Firebase Remote Config API reference: https://firebase.google.com/docs/reference/android/com/google/firebase/remoteconfig/FirebaseRemoteConfig
