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

for fixture_remote in \
  /sdcard/Android/data/com.fynx.app.test/files/admin-test-fixtures \
  /sdcard/Android/data/com.fynx.app/files/admin-test-fixtures; do
  if adb shell test -d "$fixture_remote" >/dev/null 2>&1; then
    if adb pull "$fixture_remote/." "$admin_fixture_dir/" >/dev/null 2>&1; then
      echo "CAPTURED: $fixture_remote" >> "$admin_fixture_status"
    else
      echo "NOT_CAPTURED: adb pull failed for $fixture_remote" >> "$admin_fixture_status"
    fi
  else
    echo "NOT_CAPTURED: remote Admin fixture directory was absent: $fixture_remote" >> "$admin_fixture_status"
  fi
done

exit "$runtime_status"
