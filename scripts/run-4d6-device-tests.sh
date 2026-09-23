#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
ADB_BIN="${ADB:-adb}"

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "BLOCKED: adb is not available. Set ANDROID_HOME/ANDROID_SDK_ROOT and install platform-tools." >&2
  exit 2
fi

mapfile -t DEVICES < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {print $1}')
if [ "${#DEVICES[@]}" -eq 0 ]; then
  echo "BLOCKED: no authorized Android device or running emulator is available." >&2
  "$ADB_BIN" devices -l || true
  exit 3
fi
if [ "${#DEVICES[@]}" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
  echo "BLOCKED: more than one device is connected; set ANDROID_SERIAL." >&2
  printf 'Devices:\n%s\n' "${DEVICES[*]}" >&2
  exit 4
fi

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export PATH="$JAVA_HOME/bin:$PATH"
printf 'sdk.dir=%s\n' "${SDK_DIR:-/tmp/shieldra-sdk}" > "$ROOT_DIR/local.properties"
trap 'rm -f "$ROOT_DIR/local.properties"' EXIT

cd "$ROOT_DIR"
./gradlew assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shieldra.storage.room.RoomEventRepositoryInstrumentedTest

printf '\n4d-6 runtime test completed for commit %s\n' "$(git rev-parse HEAD)"
"$ADB_BIN" shell getprop ro.product.manufacturer
"$ADB_BIN" shell getprop ro.product.model
"$ADB_BIN" shell getprop ro.build.version.sdk
"$ADB_BIN" shell getprop ro.build.fingerprint
