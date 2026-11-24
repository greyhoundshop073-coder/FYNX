#!/usr/bin/env bash
# The Android emulator runner executes each configured script line separately.
# Keep compound shell logic here, behind one command in android-build.yml.
set +e

bash scripts/prepare_android_runtime_emulator.sh
preflight_status=$?
runtime_status=0

if [ "$preflight_status" -eq 0 ]; then
  bash scripts/run_android_runtime_certification.sh
  runtime_status=$?
else
  runtime_status="$preflight_status"
fi

fixture_dir="fynx-runtime-screenshots/marketplace-test-fixtures"
mkdir -p "$fixture_dir"
fixture_status_file="$fixture_dir/capture-status.txt"
: > "$fixture_status_file"

shared_admin_fixture_remote="/sdcard/Pictures/FYNXAdminEvidence"
if adb shell test -d "$shared_admin_fixture_remote" >/dev/null 2>&1; then
  if adb pull "$shared_admin_fixture_remote/." "$fixture_dir/" >/dev/null 2>&1; then
    echo "CAPTURED: $shared_admin_fixture_remote (MediaStore PixelCopy evidence survives app teardown)" >> "$fixture_status_file"
  else
    echo "NOT_CAPTURED: adb pull failed for $shared_admin_fixture_remote" >> "$fixture_status_file"
  fi
else
  echo "NOT_CAPTURED: shared Admin MediaStore evidence directory was absent: $shared_admin_fixture_remote" >> "$fixture_status_file"
fi

for fixture_remote in \
  /sdcard/Android/data/com.fynx.app.test/files/marketplace-test-fixtures \
  /sdcard/Android/data/com.fynx.app/files/marketplace-test-fixtures \
  /sdcard/Android/data/com.fynx.app.test/files/admin-test-fixtures \
  /sdcard/Android/data/com.fynx.app/files/admin-test-fixtures; do
  if adb shell test -d "$fixture_remote" >/dev/null 2>&1; then
    if adb pull "$fixture_remote/." "$fixture_dir/" >/dev/null 2>&1; then
      echo "CAPTURED: $fixture_remote" >> "$fixture_status_file"
    else
      echo "NOT_CAPTURED: adb pull failed for $fixture_remote" >> "$fixture_status_file"
    fi
  else
    echo "NOT_CAPTURED: remote fixture directory was absent: $fixture_remote" >> "$fixture_status_file"
  fi
done


if [ "${FYNX_ADMIN_VISUALS_ONLY:-0}" = "1" ] && [ "$runtime_status" -eq 0 ]; then
  expected_admin_screenshots=(
    "FYNX-admin-owner-overview.png"
    "FYNX-admin-owner-overview-revenue.png"
    "FYNX-admin-owner-safety-account-reports.png"
    "FYNX-admin-owner-safety-content-reports.png"
    "FYNX-admin-owner-marketplace-campaigns.png"
    "FYNX-admin-owner-marketplace-protection.png"
    "FYNX-admin-owner-people-admins.png"
    "FYNX-admin-owner-people-account-management.png"
    "FYNX-admin-owner-announcement-editor.png"
    "FYNX-admin-owner-announcement-management.png"
    "FYNX-admin-owner-activity.png"
    "FYNX-admin-owner-music-library.png"
    "FYNX-admin-admin-marketplace-campaigns.png"
    "FYNX-marketplace-offline-banner.png"
  )
  missing_admin_screenshots=0
  for screenshot in "${expected_admin_screenshots[@]}"; do
    if [ ! -s "$fixture_dir/$screenshot" ]; then
      echo "::error::Required Admin emulator screenshot is missing: $fixture_dir/$screenshot"
      missing_admin_screenshots=1
    else
      echo "::notice::Verified Admin emulator screenshot: $fixture_dir/$screenshot"
    fi
  done
  if [ "$missing_admin_screenshots" -ne 0 ]; then
    runtime_status=1
  else
    {
      echo "# FYNX Admin emulator screenshot evidence"
      echo
      echo "- Source: Android instrumentation on this exact workflow commit."
      echo "- Fixtures: synthetic GET-only responses; no publishing or production mutations."
      echo "- Verified files:"
      for screenshot in "${expected_admin_screenshots[@]}"; do
        echo "  - `$screenshot`"
      done
      echo "- Result: ADMIN_SCREENSHOT_EVIDENCE=GREEN"
    } > fynx-runtime-screenshots/FYNX-admin-screenshot-evidence.md
  fi
elif [ "${FYNX_ADMIN_VISUALS_ONLY:-0}" = "1" ]; then
  echo "::error::Admin screenshot collection is not certified because Android instrumentation did not pass."
fi

exit "$runtime_status"
