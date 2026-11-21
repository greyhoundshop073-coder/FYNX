#!/usr/bin/env bash
# Run as one script file from android-emulator-runner. Its script input is
# dispatched as individual shell commands, so multiline control flow must not
# be embedded directly in the action's script field.
set +e
mkdir -p fynx-runtime-screenshots

if [ "${FYNX_CHAT_OFFLINE_INSTRUMENTATION_ONLY:-0}" = "1" ]; then
  # Run the focused Chat offline-outbox device tests without any server login,
  # Marketplace navigation, backend probe, or remote fixture dependency.
  ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.fynx.app.ui.FynxOfflineOutboxInstrumentationTest \
    --no-daemon --build-cache
  TEST_EXIT=$?
  adb logcat -d -v threadtime > fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
  adb shell dumpsys activity activities > fynx-runtime-screenshots/instrumentation-activities.txt 2>&1
  # Launch the actual FYNX app after the isolated offline test and capture its
  # rendered first-run screen. This is a read-only visual proof; no login,
  # backend request, account mutation, or publishing action is performed.
  if [ -s app/build/outputs/apk/debug/app-debug.apk ]; then
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    INSTALL_EXIT=$?
    if [ "$INSTALL_EXIT" -eq 0 ]; then
      adb shell am force-stop com.fynx.app
      adb shell am start -W -n com.fynx.app/.MainActivity
      LAUNCH_EXIT=$?
      if [ "$LAUNCH_EXIT" -eq 0 ]; then
        sleep 4
        adb exec-out screencap -p > fynx-runtime-screenshots/FYNX-app-launch-screen.png
        adb shell uiautomator dump /sdcard/fynx-app-launch-screen.xml >/dev/null 2>&1
        adb pull /sdcard/fynx-app-launch-screen.xml fynx-runtime-screenshots/FYNX-app-launch-screen.xml >/dev/null 2>&1
        if [ -s fynx-runtime-screenshots/FYNX-app-launch-screen.png ]; then
          echo "::notice::Captured the real FYNX app launch screen from the Android emulator."
        else
          echo "::warning::FYNX app launch screenshot was not created."
        fi
      else
        echo "::warning::FYNX app launch screen could not be opened for visual capture."
      fi
    else
      echo "::warning::FYNX debug APK could not be installed for visual capture."
    fi
  else
    echo "::warning::FYNX debug APK was unavailable for visual capture."
  fi
  {
    echo "# FYNX Chat offline attachment outbox instrumentation"
    echo
    echo "- Scope: local Android media staging, durable outbox persistence, account separation and cleanup."
    echo "- Network: not used; no server sign-in or backend request is required by the focused test."
    echo "- Instrumentation exit code: ${TEST_EXIT}"
    if [ "${TEST_EXIT}" -eq 0 ]; then
      echo
      echo "## Result: GREEN — focused Android offline outbox instrumentation passed."
    else
      echo
      echo "## Result: RED — focused Android offline outbox instrumentation failed; see the logcat and Gradle output."
    fi
  } > fynx-runtime-screenshots/FYNX-chat-offline-outbox-runtime.md
  if [ "${TEST_EXIT}" -ne 0 ]; then
    echo "::error::FYNX Chat offline attachment instrumentation failed."
  else
    echo "::notice::FYNX Chat offline attachment instrumentation passed without backend access."
  fi
  exit "${TEST_EXIT}"
fi

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=com.fynx.app.FynxRemoteConfigLiveFetchTest,com.fynx.app.ui.FynxRuntimeChatFixtureSeedTest \
  --no-daemon --build-cache
TEST_EXIT=$?

# Name the failing tests as CI annotations; this never alters TEST_EXIT.
python3 scripts/report_instrumentation_failures.py || true

# Preserve emulator evidence even if instrumentation fails.
adb logcat -d -v threadtime > fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
adb shell dumpsys activity activities > fynx-runtime-screenshots/instrumentation-activities.txt 2>&1
adb shell dumpsys package com.fynx.app > fynx-runtime-screenshots/instrumentation-package.txt 2>&1


if [ "${FYNX_CHAT_GROUPS_VISUALS_ONLY:-0}" = "1" ]; then
  echo "::notice::Running focused Chat and Groups installed-app certification; unrelated feature screenshots and backend probes are excluded."
  if [ "${TEST_EXIT}" -ne 0 ]; then
    echo "::error::Android instrumentation failed; Chat/Groups runtime was not attempted."
    exit "${TEST_EXIT}"
  fi
  APP_APK="app/build/outputs/apk/debug/app-debug.apk"
  TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  if [ ! -s "${APP_APK}" ] || [ ! -s "${TEST_APK}" ]; then
    echo "::error::The exact-head debug APK pair is missing; refusing to seed local Chat data into an unknown install."
    exit 1
  fi
  adb install -t -r "${APP_APK}"
  INSTALL_APP_EXIT=$?
  if [ "${INSTALL_APP_EXIT}" -ne 0 ]; then
    echo "::error::Could not install the exact-head FYNX debug APK before Chat fixture seeding."
    exit "${INSTALL_APP_EXIT}"
  fi
  adb install -t -r "${TEST_APK}"
  INSTALL_TEST_EXIT=$?
  if [ "${INSTALL_TEST_EXIT}" -ne 0 ]; then
    echo "::error::Could not install the exact-head Android test APK before Chat fixture seeding."
    exit "${INSTALL_TEST_EXIT}"
  fi
  FIXTURE_OUTPUT=$(adb shell am instrument -w \
    -e class com.fynx.app.ui.FynxRuntimeChatFixtureSeedTest \
    -e username "fynx_local_runtime" \
    com.fynx.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1)
  FIXTURE_EXIT=$?
  printf '%s\n' "${FIXTURE_OUTPUT}"
  FIXTURE_SUMMARY=$(printf '%s\n' "${FIXTURE_OUTPUT}" | grep -E '^[[:space:]]*OK \([1-9][0-9]* tests?\)[[:space:]]*$' | tail -n 1)
  if [ "${FIXTURE_EXIT}" -ne 0 ] || [ -z "${FIXTURE_SUMMARY}" ] || printf '%s' "${FIXTURE_OUTPUT}" | grep -q 'FAILURES!!!'; then
    echo "::error::The local private-chat fixture instrumentation did not report a successful JUnit summary."
    exit 1
  fi
  FIXTURE_DISK=$(adb shell run-as com.fynx.app cat shared_prefs/fynx_chat_store.xml 2>&1)
  if ! printf '%s' "${FIXTURE_DISK}" | grep -Fq 'FYNX offline private fixture: hello from the test peer' ||
     ! printf '%s' "${FIXTURE_DISK}" | grep -Fq 'FYNX local reply: the private conversation history restored' ||
     ! printf '%s' "${FIXTURE_DISK}" | grep -Fq 'FYNX Runtime Peer'; then
    echo "::error::The installed FYNX app's persisted Chat XML is missing the disposable peer or one of its two messages."
    exit 1
  fi
  echo "::notice::Verified the private peer and both persisted messages in the installed FYNX app's on-disk SharedPreferences."
  FYNX_CHAT_GROUPS_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
  NAV_EXIT=$?
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
  if [ "${NAV_EXIT}" -ne 0 ] && [ "${TEST_EXIT}" -eq 0 ]; then TEST_EXIT="${NAV_EXIT}"; fi
  exit "${TEST_EXIT}"
fi

if [ "${FYNX_ADMIN_VISUALS_ONLY:-0}" = "1" ]; then
  # Admin permissions and account queues require the live OWNER/ADMIN backend.
  # Do not let an unrelated Marketplace/Wallet capture turn this Admin PR red,
  # and never claim the Admin screen was runtime-certified without real role auth.
  BACKEND_STATUS=$(node --input-type=module -e 'const base=(process.env.FYNX_PRODUCTION_BASE_URL||"").replace(/\/+$/,""); if(!base){console.log("not-configured");process.exit(0)} try { const r=await fetch(base+"/health",{signal:AbortSignal.timeout(8000)}); console.log(String(r.status)); } catch { console.log("unreachable"); }')
  mkdir -p fynx-runtime-screenshots
  {
    echo "# FYNX Admin Control Center runtime certification"
    echo
    echo "- Scope: PR #527 Admin Control Center."
    echo "- Android instrumentation exit code: ${TEST_EXIT}"
    echo "- Backend health HTTP status: ${BACKEND_STATUS}"
    echo "- Admin UI runtime: BLOCKED until the backend is healthy and a real OWNER/ADMIN test session is available."
    echo "- Marketplace, My Shop and Wallet visual capture: NOT RUN; these are separate workstreams and are not used to fail this Admin-only PR."
    echo "- No role, account, moderation, announcement or publishing mutation was performed."
    echo
    if [ "${TEST_EXIT}" -eq 0 ]; then
      echo "## Result: BUILD_AND_INSTRUMENTATION_GREEN; ADMIN_RUNTIME_BLOCKED"
    else
      echo "## Result: RED (Android instrumentation failed)"
    fi
  } > fynx-runtime-screenshots/FYNX-admin-runtime-certification.md
  if [ "${TEST_EXIT}" -ne 0 ]; then
    echo "::error::Admin PR Android instrumentation failed; see instrumentation logs."
  else
    echo "::notice::Admin UI runtime remains explicitly blocked by backend/auth availability; Android instrumentation passed."
  fi
  exit "${TEST_EXIT}"
fi

if [ "${FYNX_MUSIC_VISUALS_ONLY:-0}" = "1" ]; then
  echo "::notice::Capturing the Home composer and Music picker through the debug-only UI entry; no backend account or publishing action is used."
  FYNX_MUSIC_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
  NAV_EXIT=$?
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
  if [ "$NAV_EXIT" -ne 0 ] && [ "$TEST_EXIT" -eq 0 ]; then TEST_EXIT="$NAV_EXIT"; fi
  exit "$TEST_EXIT"
fi

if [ "${FYNX_HOME_FEED_VISUALS_ONLY:-0}" = "1" ]; then
  # Home Feed and music-picker screenshot certification is read-only and can
  # use the explicit debug-local entry without requiring an active backend.
  if [ "${FYNX_LOCAL_VISUALS_ONLY:-0}" = "1" ]; then
    echo "::notice::Capturing the Home composer and Music picker through the explicit debug-local UI entry; no server account or backend writes are used."
    FYNX_HOME_FEED_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    NAV_EXIT=$?
  elif [ -n "${FYNX_E2E_USERNAME:-}" ] && [ -n "${FYNX_E2E_PASSWORD:-}" ] && node --input-type=module -e 'const base = (process.env.FYNX_PRODUCTION_BASE_URL || "https://fynx-backend-testing-testing.up.railway.app").replace(new RegExp("/+$"), ""); try { const response = await fetch(`${base}/health`, { headers: { Accept: "application/json" }, signal: AbortSignal.timeout(8000) }); const body = await response.json().catch(() => null); if (!response.ok || body?.ok !== true || body?.service !== "fynx-backend" || body?.database !== "ready") process.exitCode = 1; } catch { process.exitCode = 1; }'; then
    echo "::notice::Home Feed capture is using the existing CI test account because the backend is healthy."
    FYNX_HOME_FEED_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    NAV_EXIT=$?
  else
    echo "::notice::Backend is unavailable or E2E credentials are absent; capturing Home with explicitly local CI-only fixtures in the disposable emulator. No backend posts or uploads are created."
    FYNX_HOME_FEED_VISUALS_ONLY=1 FYNX_LOCAL_VISUALS_ONLY=1 python3 scripts/verify_authenticated_runtime_navigation.py
    NAV_EXIT=$?
  fi
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
  if [ "$NAV_EXIT" -ne 0 ] && [ "$TEST_EXIT" -eq 0 ]; then TEST_EXIT="$NAV_EXIT"; fi
  exit "$TEST_EXIT"
fi

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
