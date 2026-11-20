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

fixture_remote="/sdcard/Android/data/com.fynx.app/files/marketplace-test-fixtures"
if adb shell test -d "$fixture_remote" >/dev/null 2>&1; then
  if adb pull "$fixture_remote/." "$fixture_dir/" >/dev/null 2>&1; then
    echo "CAPTURED: $fixture_remote" >> "$fixture_status_file"
    if ! find "$fixture_dir" -maxdepth 1 -type f -name '*.png' -size +0c | grep -q .; then
      echo "NOT_CAPTURED: target app screenshot directory contained no PNG files" >> "$fixture_status_file"
      echo "::error::Marketplace instrumentation did not produce screenshot PNGs."
      runtime_status=1
    fi
  else
    echo "NOT_CAPTURED: adb pull failed for $fixture_remote" >> "$fixture_status_file"
    echo "::error::Marketplace instrumentation screenshots could not be pulled from $fixture_remote"
    runtime_status=1
  fi
else
  echo "NOT_CAPTURED: target app screenshot directory was absent: $fixture_remote" >> "$fixture_status_file"
  echo "::error::Marketplace instrumentation screenshot directory was absent: $fixture_remote"
  runtime_status=1
fi

exit "$runtime_status"
