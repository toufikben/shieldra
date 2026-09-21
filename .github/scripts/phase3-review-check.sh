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

if grep -RInE 'CameraX|ImageCapture|FusedLocationProvider|LocationManager|BillingClient|Firebase|Retrofit|OkHttp|SafeZone|Geofenc|RoomDatabase|Cipher\.getInstance|KeyStore\.getInstance' app/src/main/kotlin/com/shieldra/app; then
  printf 'FAIL: forbidden Phase 3 implementation dependency/pattern found\n'; fail=1
else
  printf 'PASS: no forbidden Phase 3 implementation patterns\n'
fi

if grep -qE '<uses-permission|@mipmap/ic_launcher' app/src/main/AndroidManifest.xml; then
  printf 'FAIL: Phase 3 manifest declares permissions or missing launcher resource\n'; fail=1
else
  printf 'PASS: Phase 3 manifest has no permissions or broken launcher reference\n'
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
