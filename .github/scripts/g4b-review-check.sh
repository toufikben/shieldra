#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

fail() { echo "FAIL: $1" >&2; exit 1; }
pass() { echo "PASS: $1"; }

[ "$(grep -c 'android:allowBackup="false"' app/src/main/AndroidManifest.xml)" -eq 1 ] || fail "application backup is not disabled"
pass "application-level backup disabled"

cloud_backup_block="$(sed -n '/<cloud-backup>/,/<\/cloud-backup>/p' app/src/main/res/xml/data_extraction_rules.xml)"
device_transfer_block="$(sed -n '/<device-transfer>/,/<\/device-transfer>/p' app/src/main/res/xml/data_extraction_rules.xml)"
grep -q '<exclude domain="database" path="\."' <<<"$cloud_backup_block" || fail "database cloud-backup exclusion missing"
grep -q '<exclude domain="file" path="\."' <<<"$cloud_backup_block" || fail "file cloud-backup exclusion missing"
grep -q '<exclude domain="database" path="\."' <<<"$device_transfer_block" || fail "database device-transfer exclusion missing"
grep -q '<exclude domain="file" path="\."' <<<"$device_transfer_block" || fail "file device-transfer exclusion missing"
pass "database and file backup/transfer exclusions present"

grep -q 'context.noBackupFilesDir' app/src/main/kotlin/com/shieldra/storage/LocalStorageFactory.kt || fail "evidence directory is not under noBackupFilesDir"
pass "evidence factory uses noBackupFilesDir"

if rg -n 'LocalShieldraStorage\.create' app/src/main/kotlin/com/shieldra/app app/src/main/kotlin/com/shieldra/navigation >/dev/null 2>&1; then
  fail "local storage factory is wired into application/navigation without runtime approval"
fi
pass "local storage factory is not auto-wired into app graph"

grep -q '^room = ' gradle/libs.versions.toml || fail "Room version is not declared"
grep -q '^androidx-room-runtime' gradle/libs.versions.toml || fail "Room runtime catalog entry is missing"
grep -q '^androidx-room-compiler' gradle/libs.versions.toml || fail "Room compiler catalog entry is missing"
[ -s app/schemas/com.shieldra.storage.room.ShieldraDatabase/1.json ] || fail "Room schema export missing"
grep -q '"foreignKeys"' app/schemas/com.shieldra.storage.room.ShieldraDatabase/1.json || fail "Room schema foreign-key evidence missing"
pass "Room schema export and foreign-key evidence present"

[ -s app/schemas/com.shieldra.storage.room.ShieldraDatabase/2.json ] || fail "Room schema v2 export missing"
grep -q '"key_version"' app/schemas/com.shieldra.storage.room.ShieldraDatabase/2.json || fail "rotation key_version column missing from schema v2"
grep -q '"aad_version"' app/schemas/com.shieldra.storage.room.ShieldraDatabase/2.json || fail "rotation aad_version column missing from schema v2"
grep -q 'MIGRATION_1_2' app/src/main/kotlin/com/shieldra/storage/room/ShieldraDatabaseMigrations.kt || fail "explicit Migration(1, 2) missing"
grep -q 'addMigrations' app/src/main/kotlin/com/shieldra/storage/LocalStorageFactory.kt || fail "explicit migrations are not wired into the Room builder"
pass "schema v2 rotation columns and explicit migration wiring present"

grep -q 'FEATURE_STRONGBOX_KEYSTORE' app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt || fail "StrongBox capability must be detected from the system feature, not the SDK level"
pass "StrongBox detection queries the runtime system feature"

grep -q 'no software fallback' app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt || fail "Keystore fallback boundary not documented"
rg -n '\.fallbackToDestructiveMigration\s*\(' app/src/main/kotlin app/src/main/res >/dev/null && fail "destructive migration fallback found in production source"
pass "Keystore fallback and destructive migration boundaries pass"

grep -q 'StorageRecoveryReason.UNREADABLE_STATE' app/src/main/kotlin/com/shieldra/storage/StorageRecovery.kt || fail "fail-closed recovery reason is missing"
grep -q 'openOrRecovery' app/src/main/kotlin/com/shieldra/storage/LocalStorageFactory.kt || fail "storage open is not wrapped in the recovery guard"
[ -s app/src/test/kotlin/com/shieldra/storage/StorageRecoveryTest.kt ] || fail "recovery decision tests are missing"
pass "RecoveryRequired open path is guarded and tested"

git diff --check
pass "git diff check"
echo "G4-B LOCAL REVIEW PASS — runtime/device evidence still required"
