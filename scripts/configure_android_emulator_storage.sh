#!/usr/bin/env bash
# Normalize the AVD data partition before QEMU reads config.ini.
# android-emulator-runner appends its disk-size setting after avdmanager creates
# the device; remove any profile-generated duplicate so the 3G cap is unambiguous.
set -euo pipefail

avd_config="${ANDROID_AVD_HOME:?ANDROID_AVD_HOME must be set}/test.avd/config.ini"
if [[ ! -f "$avd_config" ]]; then
  echo "::error::Expected Android AVD config was not created: $avd_config"
  exit 1
fi

sed -i '/^[[:space:]]*disk\.dataPartition\.size[[:space:]]*=/d' "$avd_config"
printf '\ndisk.dataPartition.size=3G\n' >> "$avd_config"

partition_entries="$(grep -c '^[[:space:]]*disk\.dataPartition\.size[[:space:]]*=' "$avd_config" || true)"
partition_value="$(sed -n 's/^[[:space:]]*disk\.dataPartition\.size[[:space:]]*=[[:space:]]*//p' "$avd_config")"
if [[ "$partition_entries" != "1" || "$partition_value" != "3G" ]]; then
  echo "::error::AVD data partition must have exactly one 3G setting (found $partition_entries entries: $partition_value)"
  exit 1
fi

available_mb="$(df -Pm "$ANDROID_AVD_HOME" | awk 'NR==2 { print $4 }')"
if [[ ! "$available_mb" =~ ^[0-9]+$ || "$available_mb" -lt 3500 ]]; then
  echo "::error::Insufficient free AVD storage before launch: ${available_mb:-unknown} MB; at least 3500 MB is required."
  exit 1
fi

echo "AVD data partition normalized to 3G (one setting); free storage: ${available_mb} MB."
