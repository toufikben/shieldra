#!/usr/bin/env bash
# Batch G / slice G4-A: run the measurement campaign on a real phone.
#
# This script is a driver, not a verifier. It installs the two APKs that CI built from ONE run,
# starts one instrumented phase at a time, puts the screen to sleep for the phases that ask for it,
# and saves every evidence line the harness prints — including the ones that look bad. It never
# builds anything: this machine has 2 cores and 4 GB and cannot assemble an APK, so the only APKs it
# may install are the ones the CI run published (the owner's rule that device evidence must come from
# the same build as the green CI run, plus amendment A-13).
#
# Amendment A-13 limits what the numbers can mean. The process being measured is an instrumented test
# process, which the platform keeps alive and prioritises; that is not a production background service
# under Doze, App Standby or OEM restrictions. While the cable is in for adb the device is also
# charging, which is itself a Doze blocker — so the charging state is recorded with every run instead
# of pretending the campaign measured a phone on battery.
#
# Nothing here decides whether the screen really went asleep, and nothing here keeps it awake or
# forces Doze: the harness checks PowerManager.isInteractive itself and marks the run contaminated if
# the platform did not agree.
#
# Usage:
#   APK=/path/to/app-debug.apk TEST_APK=/path/to/app-debug-androidTest.apk \
#     bash scripts/run-g4a-device-campaign.sh
#
# Optional: RUNS (repetitions per phase, default 3), PHASES (subset, default "A B C D"),
#           OUT_DIR, ANDROID_SERIAL, SCREEN_SLEEP_DELAY_MS, REQUESTED_PERIOD_US.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB:-adb}"
APK="${APK:?APK must point at the CI-built app-debug.apk}"
TEST_APK="${TEST_APK:?TEST_APK must point at the CI-built app-debug-androidTest.apk}"
RUNS="${RUNS:-3}"
PHASES="${PHASES:-A B C D}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/evidence/g4a/$(date +%Y%m%d-%H%M%S)}"
SCREEN_SLEEP_DELAY_MS="${SCREEN_SLEEP_DELAY_MS:-8000}"
REQUESTED_PERIOD_US="${REQUESTED_PERIOD_US:-20000}"

PACKAGE="com.shieldra.app"
TEST_PACKAGE="com.shieldra.app.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
CLASS="com.shieldra.detection.MotionBackgroundMeasurementInstrumentedTest"
TAG="ShieldraG4A"

# keyevent 223 = SLEEP, 224 = WAKEUP.
METHOD_A="phase_a_baseline_with_the_screen_on_measures_the_reference_rate"
METHOD_B="phase_b_screen_off_sixty_seconds_measures_whether_delivery_survives_the_screen_going_dark"
METHOD_C="phase_c_screen_off_five_minutes_measures_whether_delivery_survives_a_longer_interval"
METHOD_D="phase_d_screen_off_fifteen_minutes_looks_for_degradation_or_cessation_over_a_long_interval"

phase_window_ms() {
  case "$1" in
    A) echo 60000 ;;
    B) echo 60000 ;;
    C) echo 300000 ;;
    D) echo 900000 ;;
    *) echo "BLOCKED: unknown phase '$1' (expected A B C D)" >&2; exit 2 ;;
  esac
}

phase_method() {
  case "$1" in
    A) echo "$METHOD_A" ;;
    B) echo "$METHOD_B" ;;
    C) echo "$METHOD_C" ;;
    D) echo "$METHOD_D" ;;
  esac
}

# Phase A is the baseline with the screen lit; B, C and D are the dark windows.
phase_wants_screen_off() {
  case "$1" in
    A) echo 0 ;;
    *) echo 1 ;;
  esac
}

if ! command -v "$ADB_BIN" >/dev/null 2>&1; then
  echo "BLOCKED: adb is not available; install platform-tools." >&2
  exit 2
fi

mapfile -t DEVICES < <("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {print $1}')
if [ "${#DEVICES[@]}" -eq 0 ]; then
  echo "BLOCKED: no authorized Android device is available." >&2
  "$ADB_BIN" devices -l || true
  exit 3
fi
if [ "${#DEVICES[@]}" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
  echo "BLOCKED: more than one device is connected; set ANDROID_SERIAL." >&2
  printf 'Devices:\n%s\n' "${DEVICES[*]}" >&2
  exit 4
fi
if [ -z "${ANDROID_SERIAL:-}" ]; then
  export ANDROID_SERIAL="${DEVICES[0]}"
fi
SER="$ANDROID_SERIAL"

adb_sh() { "$ADB_BIN" -s "$SER" shell "$@"; }

mkdir -p "$OUT_DIR"
SUMMARY="$OUT_DIR/summary.tsv"
printf 'phase\trun\twindow_ms\tscreen_off_requested\tresult\tsamples\tmedian_ms\trate_hz\tmax_gap_ms\tdrops\tcontamination\ttiming\n' > "$SUMMARY"

echo "== preflight =="
{
  printf 'manufacturer='; adb_sh getprop ro.product.manufacturer
  printf 'model='; adb_sh getprop ro.product.model
  printf 'board='; adb_sh getprop ro.board.platform || true
  printf 'android='; adb_sh getprop ro.build.version.release
  printf 'sdk='; adb_sh getprop ro.build.version.sdk
  printf 'display='; adb_sh getprop ro.build.version.incremental
  printf 'fingerprint='; adb_sh getprop ro.build.fingerprint
  printf 'usb_state='; adb_sh getprop sys.usb.state || true
  printf 'screen_off_timeout='; adb_sh settings get system screen_off_timeout || true
} > "$OUT_DIR/preflight.txt" 2>&1
cat "$OUT_DIR/preflight.txt"
adb_sh dumpsys battery > "$OUT_DIR/preflight-battery.txt"
adb_sh dumpsys deviceidle > "$OUT_DIR/preflight-deviceidle.txt"
echo "Note: the cable is connected, so the phone is charging for the whole campaign. Charging holds Doze off; A-13 requires saying so rather than hiding it."

echo "== installing the CI-built pair =="
"$ADB_BIN" -s "$SER" install -r -g "$APK"
"$ADB_BIN" -s "$SER" install -r -g "$TEST_APK"

field() { sed -n "s/.*\"$1\":\"\{0,1\}\([^,}\"]*\)\"\{0,1\}.*/\1/p" "$2" | head -1; }

run_one() {
  local phase="$1" run="$2"
  local window_ms method wants_off log_dir json_file
  window_ms="$(phase_window_ms "$phase")"
  method="$(phase_method "$phase")"
  wants_off="$(phase_wants_screen_off "$phase")"
  log_dir="$OUT_DIR/phase-${phase}-run-${run}"
  json_file="$log_dir/evidence.json"
  mkdir -p "$log_dir"

  echo "-- phase $phase run $run (${window_ms} ms, screen off requested: $wants_off) --"
  adb_sh "am force-stop $TEST_PACKAGE" >/dev/null 2>&1 || true
  adb_sh "am force-stop $PACKAGE" >/dev/null 2>&1 || true
  if [ "$wants_off" = "1" ]; then
    # Start every dark run from the same visible state: awake screen, then the harness waits for the
    # platform to put it to sleep before it starts counting.
    adb_sh input keyevent 224 || true
    sleep 2
  fi
  # Phase A is left exactly as the phone is configured: no keyevent, no wake lock, no setting
  # changed. If its own screen-timeout puts the display to sleep mid-baseline, the evidence line
  # says so through screen_interactive_polls_during_window and the report states it.
  adb logcat -c || true
  "$ADB_BIN" -s "$SER" logcat -v threadtime "$TAG:I" '*:S' > "$log_dir/logcat.txt" 2>&1 &
  local logcat_pid=$!
  sleep 1

  local sleeper=""
  if [ "$wants_off" = "1" ]; then
    (
      sleep "$(awk -v ms="$SCREEN_SLEEP_DELAY_MS" 'BEGIN { printf "%.3f", ms/1000 }')"
      adb_sh input keyevent 223 || true
    ) &
    sleeper=$!
  fi

  local started instrument_status
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  set +e
  "$ADB_BIN" -s "$SER" shell am instrument -w \
    -e class "${CLASS}#${method}" \
    -e run_index "$run" \
    -e window_ms "$window_ms" \
    -e requested_period_us "$REQUESTED_PERIOD_US" \
    "$TEST_PACKAGE/$RUNNER" > "$log_dir/instrument.log" 2>&1
  instrument_status=$?
  set -e

  if [ -n "$sleeper" ]; then
    wait "$sleeper" 2>/dev/null || true
  fi
  sleep 1
  kill "$logcat_pid" 2>/dev/null || true
  wait "$logcat_pid" 2>/dev/null || true

  grep -h 'G4A_JSON=' "$log_dir/logcat.txt" | sed 's/.*G4A_JSON=//' > "$json_file" || true
  adb_sh dumpsys battery > "$log_dir/post-battery.txt" || true
  adb_sh dumpsys deviceidle > "$log_dir/post-deviceidle.txt" || true
  adb_sh dumpsys power | grep -E 'mWakefulness|mState|mScreenOn|Interactive' > "$log_dir/post-power.txt" || true

  local result samples median rate maxgap drops contamination
  if [ ! -s "$json_file" ]; then
    result="NO_EVIDENCE"
    samples=""; median=""; rate=""; maxgap=""; drops=""; contamination="instrument exit $instrument_status"
  else
    result="$(field delivery_state "$json_file")"
    samples="$(field window_sample_count "$json_file")"
    median="$(field median_interval_ms "$json_file")"
    rate="$(field derived_rate_hz "$json_file")"
    maxgap="$(field max_interval_ms "$json_file")"
    drops="$(field clockResolutionDrops "$json_file")"
    contamination="$(field contamination_reason "$json_file")"
    if [ "$(field contaminated "$json_file")" = "true" ]; then
      result="CONTAMINATED ($result)"
    fi
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$phase" "$run" "$window_ms" "$wants_off" "$result" "$samples" "$median" "$rate" "$maxgap" "$drops" \
    "$contamination" "started=$started exit=$instrument_status" >> "$SUMMARY"
  echo "   started=$started instrument_exit=$instrument_status result=$result samples=$samples"
  echo "   evidence: $json_file"
}

echo "== campaign (${RUNS} runs per phase: $PHASES) =="
for phase in $PHASES; do
  for run in $(seq 1 "$RUNS"); do
    run_one "$phase" "$run"
  done
done

echo
echo "== campaign summary =="
column -t -s "$(printf '\t')" "$SUMMARY" 2>/dev/null || cat "$SUMMARY"
echo
echo "Evidence saved under $OUT_DIR"
echo "Reminder (A-13): these runs describe an instrumented test process on a charging, adb-connected phone. They do not establish production background survivability under Doze, App Standby or OEM restrictions, and they do not establish whether a Foreground Service is required or unnecessary for SHIELDRA."
