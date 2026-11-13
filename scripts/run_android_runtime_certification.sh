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

if [ "${FYNX_ADMIN_VISUALS_ONLY:-0}" = "1" ]; then
  # The actual server-authorized OWNER/ADMIN journey still requires the live backend.
  # Separately capture the production Admin layout in a debug-only, read-only preview
  # so the PR supplies genuine emulator pixels without inventing live account data.
  BACKEND_STATUS=$(node --input-type=module -e 'const base=(process.env.FYNX_PRODUCTION_BASE_URL||"").replace(/\\/+$/,""); if(!base){console.log("not-configured");process.exit(0)} try { const r=await fetch(base+"/health",{signal:AbortSignal.timeout(8000)}); console.log(String(r.status)); } catch { console.log("unreachable"); }')
  mkdir -p fynx-runtime-screenshots
  if [ "${TEST_EXIT}" -eq 0 ]; then
    printf '%s' '<map><boolean name="fynx_admin_visual_preview" value="true" /></map>' | adb shell run-as com.fynx.app sh -c 'cat > shared_prefs/fynx_preferences.xml'
    adb shell am force-stop com.fynx.app
    adb shell monkey -p com.fynx.app 1 >/dev/null
    sleep 5
    adb shell uiautomator dump /sdcard/fynx-admin-preview.xml >/dev/null
    adb exec-out cat /sdcard/fynx-admin-preview.xml > fynx-runtime-screenshots/admin-control-center-overview.xml
    adb exec-out screencap -p > fynx-runtime-screenshots/admin-control-center-overview.png
    python3 - <<'PY'
from pathlib import Path
p=Path("fynx-runtime-screenshots/admin-control-center-overview.png")
xml=Path("fynx-runtime-screenshots/admin-control-center-overview.xml").read_text(encoding="utf-8",errors="replace")
assert p.is_file() and p.stat().st_size > 10000, "Admin emulator screenshot is missing or unexpectedly small"
assert p.read_bytes().startswith(bytes.fromhex("89504e470d0a1a0a")), "Admin screenshot is not a valid PNG"
assert "FYNX CONTROL CENTER" in xml, "Admin Control Center was not visible in the emulator hierarchy"
assert "LOCAL VISUAL PREVIEW" in xml, "Preview-only safety banner was not visible"
print(f"ADMIN_PREVIEW_SCREENSHOT=CAPTURED ({p.stat().st_size} bytes)")
PY
    CAPTURE_STATUS=$?
  else
    CAPTURE_STATUS=1
  fi
  {
    echo "# FYNX Admin Control Center runtime certification"
    echo
    echo "- Scope: PR #536 Admin Control Center hardening."
    echo "- Android instrumentation exit code: ${TEST_EXIT}"
    echo "- Backend health HTTP status: ${BACKEND_STATUS}"
    if [ "${CAPTURE_STATUS}" -eq 0 ]; then
      echo "- Admin visual preview: CAPTURED from the running debug APK on the CI emulator."
      echo "- Preview data: clearly labelled sample dashboard counts; no live user, report, appeal, revenue or moderation data is fabricated."
      echo "- Screenshot: admin-control-center-overview.png"
      echo "- UI hierarchy: admin-control-center-overview.xml"
    else
      echo "- Admin visual preview: FAILED; no screenshot is claimed."
    fi
    echo "- Live OWNER/ADMIN authorization: NOT CERTIFIED while backend health/auth is unavailable."
    echo "- No role, account, moderation, announcement or publishing mutation was performed."
    echo
    if [ "${TEST_EXIT}" -eq 0 ] && [ "${CAPTURE_STATUS}" -eq 0 ]; then
      echo "## Result: BUILD_AND_INSTRUMENTATION_GREEN; ADMIN_VISUAL_PREVIEW_CAPTURED; LIVE_ROLE_AUTH_BLOCKED"
    else
      echo "## Result: RED (instrumentation or Admin visual capture failed)"
    fi
  } > fynx-runtime-screenshots/FYNX-admin-runtime-certification.md
  if [ "${TEST_EXIT}" -ne 0 ] || [ "${CAPTURE_STATUS}" -ne 0 ]; then
    echo "::error::Admin instrumentation or visual capture failed; see the runtime artifact."
    exit 1
  fi
  exit 0
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
