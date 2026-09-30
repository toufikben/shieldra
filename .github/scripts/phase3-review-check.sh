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

if grep -RInE 'CameraX|ImageCapture|FusedLocationProvider|LocationManager|BillingClient|Firebase|Retrofit|OkHttp|SafeZone|Geofenc|RoomDatabase|Cipher\.getInstance|KeyStore\.getInstance' app/src/main/kotlin/com/shieldra/app; then
  printf 'FAIL: forbidden Phase 3 implementation dependency/pattern found\n'; fail=1
else
  printf 'PASS: no forbidden Phase 3 implementation patterns\n'
fi

# Amendment A-6 authorizes exactly one permission: POST_NOTIFICATIONS, because a
# local event notification is impossible on Android 13+ without it, and it is
# requested from the notification setup screen rather than from a Panic press.
# Every other permission stays forbidden — the manifest is the only place SHIELDRA
# could quietly acquire camera, location, phone-state, or network access.
declared="$(grep -oE '<uses-permission[^>]*android:name="[^"]+"' app/src/main/AndroidManifest.xml | grep -oE 'android:name="[^"]+"' | sed 's/android:name="//;s/"$//' | sort)"
unexpected="$(printf '%s\n' "$declared" | grep -v '^android.permission.POST_NOTIFICATIONS$' | grep -v '^$' || true)"
if [ -n "$unexpected" ]; then
  printf 'FAIL: manifest declares permissions outside amendment A-6:\n%s\n' "$unexpected"; fail=1
else
  printf 'PASS: manifest declares only the A-6 notification permission\n'
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
