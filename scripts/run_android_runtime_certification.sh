#!/usr/bin/env bash
# Run as one script file from android-emulator-runner. Its script input is
# dispatched as individual shell commands, so multiline control flow must not
# be embedded directly in the action's script field.
set +e
mkdir -p fynx-runtime-screenshots

./gradlew connectedDebugAndroidTest --no-daemon --build-cache
TEST_EXIT=$?

# Preserve emulator evidence even if instrumentation fails.
adb logcat -d -v threadtime > fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
adb shell dumpsys activity activities > fynx-runtime-screenshots/instrumentation-activities.txt 2>&1
adb shell dumpsys package com.fynx.app > fynx-runtime-screenshots/instrumentation-package.txt 2>&1

if [ "$TEST_EXIT" -eq 0 ]; then
  # Use server-authenticated navigation only when both CI credentials exist and the backend is ready.
  # Camera/media visual certification remains useful through the existing read-only debug-local UI
  # entry when secrets are absent, so an unavailable secret does not suppress emulator evidence.
  if [ -z "${FYNX_E2E_USERNAME:-}" ] || [ -z "${FYNX_E2E_PASSWORD:-}" ]; then
    echo "::notice::E2E credentials are not configured; running read-only camera/Marketplace UI certification through the debug-local path."
    FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    NAV_EXIT=$?
  elif node --input-type=module -e 'const base = (process.env.FYNX_PRODUCTION_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(new RegExp("/+$"), ""); try { const response = await fetch(`${base}/health`, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(8000) }); const body = await response.json().catch(() => null); if (!response.ok || body?.ok !== true || body?.service !== "fynx-backend" || body?.database !== "ready") { console.log(`Backend is not ready for authenticated runtime navigation (HTTP ${response.status})`); process.exitCode = 1; } else { console.log("Backend is ready for authenticated runtime navigation."); } } catch (error) { console.log(`Backend is unavailable for authenticated runtime navigation: ${error.message}`); process.exitCode = 1; }'; then
    python3 scripts/verify_authenticated_runtime_navigation.py
    NAV_EXIT=$?
  else
    echo "::notice::Live backend is unavailable; running read-only camera/Marketplace UI certification instead of server-authenticated navigation."
    FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    NAV_EXIT=$?
  fi

  if [ "$NAV_EXIT" -ne 0 ]; then
    echo "::error::Marketplace/My Shop runtime verification failed (exit $NAV_EXIT)."
    TEST_EXIT="$NAV_EXIT"
  fi

  # This PR is scoped to Marketplace/My Shop. Do not enter the Status creator journey
  # while another workstream owns it; its own PR/runtime workflow retains that coverage.
  if [ "${FYNX_MARKETPLACE_RUNTIME_ONLY:-0}" != "1" ]; then
    python3 scripts/verify_status_creator_runtime.py
    STATUS_EXIT=$?
    if [ "$STATUS_EXIT" -ne 0 ]; then
      echo "::error::Status creator runtime verification failed (exit $STATUS_EXIT)."
      if [ "$TEST_EXIT" -eq 0 ]; then TEST_EXIT="$STATUS_EXIT"; fi
    fi
  else
    echo "::notice::Marketplace-only runtime capture; leaving Status creator journey untouched."
  fi
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
else
  # A red instrumentation test must not suppress Marketplace/My Shop screenshot evidence.
  # The focused run is read-only and its result never replaces TEST_EXIT.
  FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
  VISUAL_EXIT=$?
  if [ "$VISUAL_EXIT" -ne 0 ]; then
    echo "::warning::Marketplace/My Shop visual capture did not complete (exit $VISUAL_EXIT); the instrumentation result remains $TEST_EXIT."
  fi
  if [ "${FYNX_MARKETPLACE_RUNTIME_ONLY:-0}" != "1" ]; then
    python3 scripts/verify_status_creator_runtime.py
    STATUS_EXIT=$?
    if [ "$STATUS_EXIT" -ne 0 ]; then
      echo "::warning::Status creator diagnostic capture did not complete (exit $STATUS_EXIT); the instrumentation result remains $TEST_EXIT."
    fi
  else
    echo "::notice::Marketplace-only runtime capture; leaving Status creator journey untouched."
  fi
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
fi

exit "$TEST_EXIT"
