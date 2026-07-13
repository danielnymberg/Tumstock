#!/usr/bin/env bash
# Bygger debug-APK lokalt. Kräver JDK 17 + Android SDK (platforms;android-35,
# build-tools;35.0.0). På web/CI byggs den automatiskt via
# .github/workflows/build.yml.
set -euo pipefail
cd "$(dirname "$0")"

./gradlew assembleDebug "$@"

APK="app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo
  echo "APK: $(pwd)/$APK"
  echo "Installera: adb install -r '$APK'"
fi
