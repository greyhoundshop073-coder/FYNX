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

screenshot_dir="$(while IFS= read -r line; do
  case "$line" in
    *FYNX_MARKETPLACE_TEST_SCREENSHOT*directory=*) echo "${line##*directory=}" ;;
  esac
done < fynx-runtime-screenshots/instrumentation-logcat.txt | tail -n 1)"
if [ -n "$screenshot_dir" ] && adb shell test -d "$screenshot_dir" >/dev/null 2>&1; then
  if adb pull "$screenshot_dir/." "$fixture_dir/" >/dev/null 2>&1; then
    echo "CAPTURED: $screenshot_dir" >> "$fixture_status_file"
  else
    echo "NOT_CAPTURED: adb pull failed for $screenshot_dir" >> "$fixture_status_file"
    echo "::warning::Marketplace test screenshots could not be pulled from $screenshot_dir"
  fi
else
  echo "NOT_CAPTURED: no Marketplace instrumentation screenshot directory was reported" >> "$fixture_status_file"
  echo "::warning::No Marketplace instrumentation screenshot directory was reported by the tests"
fi

exit "$runtime_status"
