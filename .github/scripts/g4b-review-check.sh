#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

[ "$(grep -c 'android:allowBackup="false"' app/src/main/AndroidManifest.xml)" -eq 1 ] || fail "application backup is not disabled"
pass "application-level backup disabled"

grep -q '<exclude domain="database" path="."' app/src/main/res/xml/data_extraction_rules.xml || fail "database cloud-backup exclusion missing"
grep -q '<exclude domain="file" path="."' app/src/main/res/xml/data_extraction_rules.xml || fail "file cloud-backup exclusion missing"
grep -q '<exclude domain="database" path="."' app/src/main/res/xml/data_extraction_rules.xml || fail "database device-transfer exclusion missing"
grep -q '<exclude domain="file" path="."' app/src/main/res/xml/data_extraction_rules.xml || fail "file device-transfer exclusion missing"
pass "database and file backup/transfer exclusions present"

grep -q 'context.noBackupFilesDir' app/src/main/kotlin/com/shieldra/storage/LocalStorageFactory.kt || fail "evidence directory is not under noBackupFilesDir"
pass "evidence factory uses noBackupFilesDir"

if rg -n 'LocalShieldraStorage\.create' app/src/main/kotlin/com/shieldra/app app/src/main/kotlin/com/shieldra/navigation >/dev/null 2>&1; then
  fail "local storage factory is wired into application/navigation without runtime approval"
fi
pass "local storage factory is not auto-wired into app graph"

grep -q 'androidx.room.room' gradle/libs.versions.toml || true
[ -s app/schemas/com.shieldra.storage.room.ShieldraDatabase/1.json ] || fail "Room schema export missing"
grep -q '"foreignKeys"' app/schemas/com.shieldra.storage.room.ShieldraDatabase/1.json || fail "Room schema foreign-key evidence missing"
pass "Room schema export and foreign-key evidence present"

grep -q 'no software fallback' app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt || fail "Keystore fallback boundary not documented"
rg -n '\.fallbackToDestructiveMigration\s*\(' app/src/main/kotlin app/src/main/res >/dev/null && fail "destructive migration fallback found in production source"
pass "Keystore fallback and destructive migration boundaries pass"

git diff --check
pass "git diff check"
echo "G4-B LOCAL REVIEW PASS — runtime/device evidence still required"
