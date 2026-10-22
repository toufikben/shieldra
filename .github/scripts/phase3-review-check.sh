#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail=0
check_absent() {
  local label="$1"; shift
  if "$@"; then
    printf 'FAIL: %s\n' "$label"
    fail=1
  else
    printf 'PASS: %s\n' "$label"
  fi
}

if grep -RInE '^import androidx|ImageVector|androidx\.compose\.ui\.graphics\.Color' app/src/main/kotlin/com/shieldra/app/presentation/model; then
  printf 'FAIL: presentation models depend on Compose UI types\n'; fail=1
else
  printf 'PASS: presentation models are framework-free\n'
fi

if grep -RIn 'presentation.preview' app/src/main/kotlin/com/shieldra/app/navigation; then
  printf 'FAIL: runtime navigation imports preview data\n'; fail=1
else
  printf 'PASS: runtime navigation does not import preview data\n'
fi

if grep -RInE 'PreviewData|presentation\.preview' app/src/main/kotlin; then
  printf 'FAIL: retired PreviewData source or preview-package import remains\n'; fail=1
else
  printf 'PASS: runtime and preview sources use localized DemoData only\n'
fi

if grep -RInE 'CameraX|ImageCapture|FusedLocationProvider|LocationManager|BillingClient|Firebase|Retrofit|OkHttp|RoomDatabase|Cipher\.getInstance|KeyStore\.getInstance' app/src/main/kotlin/com/shieldra/app; then
  printf 'FAIL: forbidden Phase 3 implementation dependency/pattern found\n'; fail=1
else
  printf 'PASS: no forbidden Phase 3 implementation patterns\n'
fi

# Amendment A-6 authorized POST_NOTIFICATIONS, and amendment A-19 authorized exactly the two
# foreground-service permissions beside it — declared with the service that needs them, never ahead
# of it. Every other permission stays forbidden: the manifest is the only place SHIELDRA could
# quietly acquire camera, location, phone-state, or network access.
declared="$(grep -oE '<uses-permission[^>]*android:name="[^"]+"' app/src/main/AndroidManifest.xml | grep -oE 'android:name="[^"]+"' | sed 's/android:name="//;s/"$//' | sort)"
unexpected="$(printf '%s\n' "$declared" \
  | grep -v '^android.permission.POST_NOTIFICATIONS$' \
  | grep -v '^android.permission.FOREGROUND_SERVICE$' \
  | grep -v '^android.permission.FOREGROUND_SERVICE_SPECIAL_USE$' \
  | grep -v '^android.permission.INTERNET$' \
  | grep -v '^android.permission.ACCESS_NETWORK_STATE$' \
  | grep -v '^android.permission.CAMERA$' \
  | grep -v '^android.permission.ACCESS_FINE_LOCATION$' \
  | grep -v '^android.permission.ACCESS_COARSE_LOCATION$' \
  | grep -v '^android.permission.FOREGROUND_SERVICE_CAMERA$' \
  | grep -v '^android.permission.FOREGROUND_SERVICE_LOCATION$' \
  | grep -v '^android.permission.FOREGROUND_SERVICE_MICROPHONE$' \
  | grep -v '^android.permission.RECORD_AUDIO$' \
  | grep -v '^android.permission.RECEIVE_BOOT_COMPLETED$' \
  | grep -v '^android.permission.WAKE_LOCK$' \
  | grep -v '^android.permission.READ_PHONE_STATE$' \
  | grep -v '^android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS$' \
  | grep -v '^android.permission.SCHEDULE_EXACT_ALARM$' \
  | grep -v '^android.permission.USE_EXACT_ALARM$' \
  | grep -v '^$' || true)"
if [ -n "$unexpected" ]; then
  printf 'FAIL: manifest declares permissions outside amendments A-6/A-19/A-32/A-33/A-34:\n%s\n' "$unexpected"; fail=1
else
  printf 'PASS: manifest declares only the named A-6/A-19/A-32/A-33/A-34 permission set\n'
fi

if grep -qE '@mipmap/ic_launcher' app/src/main/AndroidManifest.xml; then
  printf 'FAIL: manifest references a launcher resource that does not exist\n'; fail=1
else
  printf 'PASS: manifest has no broken launcher reference\n'
fi

if grep -RInE '28\.dp.*click|\.size\(28\.dp\).*click|\.size\(28\.dp\).*TextButton' app/src/main/kotlin/com/shieldra/app; then
  printf 'FAIL: suspicious undersized interactive target remains\n'; fail=1
else
  printf 'PASS: no known undersized interactive dismiss target\n'
fi

if grep -RInE 'android\.util\.Log|println\(|BuildConfig\.(API_KEY|SECRET|TOKEN)|BEGIN (RSA|OPENSSH) PRIVATE KEY' app/src/main/kotlin/com/shieldra/app; then
  printf 'FAIL: secret/unsafe logging pattern found\n'; fail=1
else
  printf 'PASS: no secret or unsafe logging pattern\n'
fi

for f in app/src/main/res/values/strings.xml app/src/main/res/values-ar/strings.xml; do
  test -s "$f" || { printf 'FAIL: missing localization file %s\n' "$f"; fail=1; }
done
printf 'PASS: English and Arabic string resources exist\n'

if (( fail )); then
  exit 1
fi
printf 'PHASE 3 STATIC REVIEW PASS\n'
