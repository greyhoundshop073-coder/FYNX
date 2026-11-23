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

for fixture_remote in \
  /sdcard/Android/data/com.fynx.app.test/files/marketplace-test-fixtures \
  /sdcard/Android/data/com.fynx.app/files/marketplace-test-fixtures; do
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

admin_fixture_dir="fynx-runtime-screenshots/admin-test-fixtures"
mkdir -p "$admin_fixture_dir"
admin_fixture_status="$admin_fixture_dir/capture-status.txt"
: > "$admin_fixture_status"
admin_capture_status=0

# Android 11+ restricts adb pull from /sdcard/Android/data. The test writes
# into the debuggable target app's internal files directory, which is exported
# reliably through run-as instead.
for screenshot in FYNX-admin-owner-overview.png FYNX-admin-admin-overview.png; do
  local_file="$admin_fixture_dir/$screenshot"
  if adb exec-out run-as com.fynx.app cat "files/admin-test-fixtures/$screenshot" > "$local_file" 2>/dev/null &&
      [ -s "$local_file" ] &&
      python3 -c 'import pathlib,sys; p=pathlib.Path(sys.argv[1]); assert p.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")' "$local_file"; then
    echo "CAPTURED: $screenshot ($(stat -c %s "$local_file") bytes)" | tee -a "$admin_fixture_status"
  else
    rm -f "$local_file"
    echo "NOT_CAPTURED: $screenshot missing or invalid in com.fynx.app internal files" | tee -a "$admin_fixture_status"
    admin_capture_status=1
  fi
done

if [ "$admin_capture_status" -ne 0 ]; then
  echo "ADMIN_SCREENSHOT_EXPORT=FAIL" | tee -a "$GITHUB_STEP_SUMMARY"
else
  echo "ADMIN_SCREENSHOT_EXPORT=PASS" | tee -a "$GITHUB_STEP_SUMMARY"
fi

exit "$((runtime_status != 0 ? runtime_status : admin_capture_status))"
