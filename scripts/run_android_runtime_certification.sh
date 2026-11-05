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

if [ "${TEST_EXIT}" -eq 0 ]; then
  if [ "${FYNX_AI_VISUALS_ONLY:-0}" = "1" ]; then
    # This assistant-dashboard visual audit is intentionally backend-independent:
    # open the real screen through the disposable debug UI entry and never submit
    # a prompt or claim that a Gemini/backend response was verified.
    echo "::notice::Capturing the actual FYNX Assistant dashboard and attachment menu through the debug-only UI session; no AI prompt is submitted."
    FYNX_AI_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    NAV_EXIT=$?
  else
    # Prefer real server authentication whenever CI credentials and a ready backend exist.
    # The Phase 1 switch narrows the inspected surfaces only after readiness succeeds;
    # it never silently downgrades a credentialed run to a local-only session.
    if [ -z "${FYNX_E2E_USERNAME:-}" ] || [ -z "${FYNX_E2E_PASSWORD:-}" ]; then
      echo "::notice::E2E credentials are not configured; running read-only camera/Marketplace UI certification through the debug-local path."
      FYNX_MARKETPLACE_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
      NAV_EXIT=$?
    elif node --input-type=module -e 'const base = (process.env.FYNX_PRODUCTION_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(new RegExp("/+$"), ""); try { const response = await fetch(`${base}/health`, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(8000) }); const body = await response.json().catch(() => null); if (!response.ok || body?.ok !== true || body?.service !== "fynx-backend" || body?.database !== "ready") { console.log(`Backend is not ready for authenticated runtime navigation (HTTP ${response.status})`); process.exitCode = 1; } else { console.log("Backend is ready for authenticated runtime navigation."); } } catch (error) { console.log(`Backend is unavailable for authenticated runtime navigation: ${error.message}`); process.exitCode = 1; }'; then
      if [ "${FYNX_MARKETPLACE_VISUALS_ONLY:-0}" = "1" ]; then
        echo "::notice::Backend is ready; running the focused Phase 1 walk with the supplied server credentials."
        FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
      else
        python3 scripts/verify_authenticated_runtime_navigation.py
      fi
      NAV_EXIT=$?
    else
      echo "::notice::Live backend is unavailable; running read-only camera/Marketplace UI certification instead of server-authenticated navigation."
      FYNX_MARKETPLACE_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
      NAV_EXIT=$?
    fi
  fi

  if [ "${NAV_EXIT}" -ne 0 ]; then
    echo "::error::FYNX runtime visual verification failed (exit ${NAV_EXIT})."
    TEST_EXIT="${NAV_EXIT}"
  fi

  # The focused AI or Marketplace visual passes deliberately exclude the separate Status workstream.
  if [ "${FYNX_AI_VISUALS_ONLY:-0}" != "1" ] && [ "${FYNX_MARKETPLACE_VISUALS_ONLY:-0}" != "1" ]; then
    python3 scripts/verify_status_creator_runtime.py
    STATUS_EXIT=$?
    if [ "${STATUS_EXIT}" -ne 0 ]; then
      echo "::error::Status creator runtime verification failed (exit ${STATUS_EXIT})."
      if [ "${TEST_EXIT}" -eq 0 ]; then TEST_EXIT="${STATUS_EXIT}"; fi
    fi
  fi
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
else
  # A red instrumentation test must not suppress the requested real UI screenshot.
  if [ "${FYNX_AI_VISUALS_ONLY:-0}" = "1" ]; then
    FYNX_AI_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    VISUAL_EXIT=$?
    if [ "${VISUAL_EXIT}" -ne 0 ]; then
      echo "::warning::FYNX Assistant visual capture did not complete (exit ${VISUAL_EXIT}); the instrumentation result remains ${TEST_EXIT}."
    fi
  else
    # Preserve the existing Marketplace/My Shop visual fallback for its workstream.
    FYNX_LOCAL_VISUALS_ONLY=1 FYNX_MARKETPLACE_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    VISUAL_EXIT=$?
    if [ "${VISUAL_EXIT}" -ne 0 ]; then
      echo "::warning::Marketplace/My Shop visual capture did not complete (exit ${VISUAL_EXIT}); the instrumentation result remains ${TEST_EXIT}."
    fi
  fi
  if [ "${FYNX_AI_VISUALS_ONLY:-0}" != "1" ] && [ "${FYNX_MARKETPLACE_VISUALS_ONLY:-0}" != "1" ]; then
    python3 scripts/verify_status_creator_runtime.py
    STATUS_EXIT=$?
    if [ "${STATUS_EXIT}" -ne 0 ]; then
      echo "::warning::Status creator diagnostic capture did not complete (exit ${STATUS_EXIT}); the instrumentation result remains ${TEST_EXIT}."
    fi
  fi
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
fi
exit "$TEST_EXIT"
