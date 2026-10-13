# More Tools Reliability Release Gate

## Scope ownership
This work is isolated to More/Features, Extra Tools, Calendar, To-Do, Calls entry points, and local Money Center records. Do not edit Home or Marketplace feature implementations in this workstream; those areas have a separate active owner. Do not merge this branch into `main` without explicit approval.

## Current branch
- Branch: `fix/more-tools-professional-reliability-20261013`
- Pull request: #151 (draft)
- Backend: intentionally not reconnected; Render is unavailable and backend-dependent journeys are not certified.

## Required checks before calling this complete
1. Run `python3 scripts/verify_more_tools_reliability.py`.
2. Run `./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon --build-cache`.
3. Run connected Android instrumentation and authenticated navigation verification in the emulator.
4. Confirm the workflow uploads an APK from the exact final commit and records the SHA.
5. Inspect runtime screenshots/UI hierarchy for navigation and changed More/Money Center screens.
6. Verify on a device that the APK actually installs and that Quick Notes persist/delete, Calendar validation rejects invalid input, overdue tasks remain visible, local money records survive reopening/account switching, and visible/system Back both follow the route history.
7. Confirm the existing Home interaction verifier checks the history-aware header back action without dropping the Saved Posts return-to-Features behavior.
7. Keep the PR draft and unmerged until the user explicitly authorizes a merge.

## Known limits
- Quick Notes and listed Money Center records are local account-scoped records, not cloud-synced data.
- The Money Lock/Vault is only a UI demo; it does not encrypt or secure sensitive records.
- Money transaction entries are local records and do not transfer money.
- Calls, AI, business/advertising, seller tools, and other live backend-dependent features cannot be certified while the backend is unavailable.
- A green build or static source contract is not proof of real-device runtime correctness.
