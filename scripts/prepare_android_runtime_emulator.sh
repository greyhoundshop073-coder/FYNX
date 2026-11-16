#!/usr/bin/env bash
# Wait for both the Android device transport and PackageManager to become
# responsive before Gradle attempts split-APK installation.
set -euo pipefail

artifact_dir="${FYNX_RUNTIME_ARTIFACT_DIR:-fynx-runtime-screenshots}"
mkdir -p "$artifact_dir"

for round in 1 2; do
  echo "ADB readiness round $round/2"
  timeout 15 adb start-server || true

  for attempt in $(seq 1 40); do
    devices="$(timeout 8 adb devices 2>/dev/null || true)"
    serial="$(printf '%s\n' "$devices" | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1; exit }')"

    if [[ -n "$serial" ]]; then
      boot_completed="$(timeout 8 adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
      if [[ "$boot_completed" == "1" ]] && (( attempt % 4 == 0 )); then
        if timeout 15 adb -s "$serial" shell pm list packages >/dev/null 2>&1; then
          adb devices -l | tee "$artifact_dir/adb-devices-ready.txt"
          echo "Emulator $serial is booted and PackageManager responds."
          exit 0
        fi
        echo "PackageManager is still busy; retrying (round $round, attempt $attempt)."
      fi
    fi
    sleep 3
  done

  echo "ADB did not become responsive in round $round; restarting the host ADB daemon."
  timeout 10 adb kill-server || true
done

timeout 15 adb devices -l > "$artifact_dir/adb-devices-preflight-failure.txt" 2>&1 || true
timeout 20 adb logcat -d -t 3000 > "$artifact_dir/emulator-preflight-logcat.txt" 2>&1 || true
echo "::error::Android emulator or PackageManager remained unresponsive after ADB recovery."
exit 1
