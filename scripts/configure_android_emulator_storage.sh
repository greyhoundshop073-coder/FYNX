#!/usr/bin/env bash
# Keep the CI AVD within the hosted runner's available disk budget.
# Pixel 2 is sufficient for accessibility-driven UI certification and avoids
# the Pixel 7 Pro profile's 7.3 GB userdata requirement on this runner.
set -euo pipefail

avd_config="${ANDROID_AVD_HOME:?ANDROID_AVD_HOME must be set}/test.avd/config.ini"
if [[ ! -f "$avd_config" ]]; then
  echo "::error::Expected Android AVD config was not created: $avd_config"
  exit 1
fi

# Remove all profile/runner duplicates and enforce one small, explicit config.
sed -i '/^[[:space:]]*disk\.dataPartition\.size[[:space:]]*=/d; /^[[:space:]]*hw\.ramSize[[:space:]]*=/d; /^[[:space:]]*vm\.heapSize[[:space:]]*=/d' "$avd_config"
printf '\nhw.ramSize=2048\nvm.heapSize=256\ndisk.dataPartition.size=2G\n' >> "$avd_config"

partition_entries="$(grep -c '^[[:space:]]*disk\.dataPartition\.size[[:space:]]*=' "$avd_config" || true)"
partition_value="$(sed -n 's/^[[:space:]]*disk\.dataPartition\.size[[:space:]]*=[[:space:]]*//p' "$avd_config")"
ram_value="$(sed -n 's/^[[:space:]]*hw\.ramSize[[:space:]]*=[[:space:]]*//p' "$avd_config")"
if [[ "$partition_entries" != "1" || "$partition_value" != "2G" || "$ram_value" != "2048" ]]; then
  echo "::error::AVD must have one 2G userdata partition and 2048 MB RAM (partition entries=$partition_entries, partition=$partition_value, RAM=$ram_value)"
  exit 1
fi

available_mb="$(df -Pm "$ANDROID_AVD_HOME" | awk 'NR==2 { print $4 }')"
if [[ ! "$available_mb" =~ ^[0-9]+$ || "$available_mb" -lt 2500 ]]; then
  echo "::error::Insufficient free AVD storage before launch: ${available_mb:-unknown} MB; at least 2500 MB is required."
  exit 1
fi

echo "AVD normalized: 2G userdata, 2048 MB RAM, 256 MB heap; free storage: ${available_mb} MB."
