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

if [ "$TEST_EXIT" -eq 0 ]; then
  python3 scripts/verify_authenticated_runtime_navigation.py
  TEST_EXIT=$?
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
fi

# Certify a real server-authenticated Status lifecycle only after the existing
# emulator and navigation suite passes. An unavailable live backend is recorded
# as BLOCKED by that certification report; it is never claimed as successful.
if [ "$TEST_EXIT" -eq 0 ]; then
  python3 scripts/verify_authenticated_status_lifecycle.py
  TEST_EXIT=$?
  adb logcat -d -v threadtime >> fynx-runtime-screenshots/instrumentation-logcat.txt 2>&1
fi

exit "$TEST_EXIT"
