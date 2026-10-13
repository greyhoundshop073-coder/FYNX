# FYNX Test Login — Locked User Requirement

**Status: LOCKED — do not change without explicit approval from the app owner.**

The owner is actively testing FYNX and wants every newly downloaded test APK to open easily without requiring a verification code or a live backend connection just to log in. Preserve the current user-approved test/offline login behavior as it is.

## Rules for every contributor and future ChatGPT session

1. Do not alter login UI, authentication/session restoration, or the test/offline login path while working on Marketplace, Home, Chat, camera, or other features.
2. Do not add a mandatory OTP/email/SMS code, backend-online prerequisite, or account setup step to the test APK flow.
3. Do not “fix” unrelated CI/runtime failures by changing login. Investigate the specific failing screen, navigation, test harness, or session state instead.
4. Keep any test/offline bypass restricted to the existing intended debug/test build; never extend it into a production/release build or weaken production authentication.
5. If a login/auth/session change seems necessary, stop and ask the owner before editing or pushing it.
6. This document records a user instruction; it does not itself change app behavior. Verify the actual implementation before making any claims about an APK.

## Current investigation note (2026-10-13)

The runtime certification log for run [38357888864](https://github.com/greyhoundshop073-coder/FYNX/actions/runs/38357888864) reports that initial real-account authentication passed and several authenticated surfaces were captured, but later Chat/Friends/Stories/Marketplace checks saw the Sign In screen. The same run reported a camera control marker missing. **Do not respond by modifying login.** Investigate whether the runtime journey loses session state between screens and inspect the camera screen/control hierarchy separately. Keep the login contract above unchanged.
