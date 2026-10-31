#!/usr/bin/env bash
set -euo pipefail
# Run from repository root. No personal documents or phone required.
# Requires Android SDK, JDK 17 and Gradle; emulator mode needs a running ADB emulator.
MODE="${1:---all}"
if [[ "$MODE" != "--all" && "$MODE" != "--unit-only" && "$MODE" != "--emulator-only" ]]; then
  echo "Usage: bash scripts/run-android-regression.sh [--all|--unit-only|--emulator-only]" >&2
  exit 2
fi
if [[ "$MODE" != "--emulator-only" ]]; then
  gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
fi
if [[ "$MODE" != "--unit-only" ]]; then
  command -v adb >/dev/null || { echo "adb not found: install Android platform-tools" >&2; exit 1; }
  if ! adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {found=1} END{exit !found}'; then
    echo "No running Android emulator. Never fall back to a connected personal phone." >&2
    exit 1
  fi
  gradle --no-daemon :app:connectedDebugAndroidTest
fi
echo "Requested test tasks completed. Examine Gradle XML/HTML reports for assertions."
