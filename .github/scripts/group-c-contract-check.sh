#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

failures=0

check_absent() {
    local label="$1"
    local pattern="$2"
    local path="$3"
    if grep -RInE --exclude-dir=.git "$pattern" "$path"; then
        printf 'FAIL: %s\n' "$label"
        failures=$((failures + 1))
    else
        printf 'PASS: %s\n' "$label"
    fi
}

check_absent "No Safe Zone, geofencing, or continuous GPS source" \
    'SafeZone|ZoneGuard|Geofence|Geofencing|continuous GPS' \
    app/src/main
check_absent "No camera, location collection, or network implementation" \
    'CameraX|ImageCapture|FusedLocationProvider|LocationManager|SMTP|OkHttp|Retrofit|Firebase|BillingClient' \
    app/src/main
check_absent "No confirmed event creation outside Domain" \
    'SecurityEvent|CONFIRMED_SECURITY_EVENT' \
    app/src/main/kotlin/com/shieldra/detection
check_absent "No confirmed event creation in UI" \
    'SecurityEvent|CONFIRMED_SECURITY_EVENT' \
    app/src/main/kotlin/com/shieldra/ui
check_absent "No Android API dependency in Domain" \
    'android\.content|android\.app|android\.location|android\.hardware|androidx\.' \
    app/src/main/kotlin/com/shieldra/domain
check_absent "No fake data, placeholders, or TODOs in app source" \
    'mock data|fake success|TODO|FIXME|hardcoded secret' \
    app/src/main

if [[ "$failures" -ne 0 ]]; then
    printf 'Group C boundary scan failed with %d finding(s).\n' "$failures"
    exit 1
fi

printf 'Group C boundary scan passed.\n'
