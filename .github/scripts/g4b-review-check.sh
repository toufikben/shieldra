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

ROTATION_TEST=app/src/androidTest/kotlin/com/shieldra/storage/EvidenceKeyRotationInstrumentedTest.kt
[ -s "$ROTATION_TEST" ] || fail "instrumented Keystore rotation test (§3 runtime) is missing"
grep -q 'KeystoreRotationCipherFactory' "$ROTATION_TEST" || fail "rotation test must use the real Keystore cipher factory, not a fake"
grep -q 'KEY_VERSION_V1' "$ROTATION_TEST" && grep -q 'KEY_VERSION_V2' "$ROTATION_TEST" || fail "rotation test must exercise both v1 and v2 key aliases"
pass "Keystore runtime rotation is covered by an instrumented test"

RESET_TEST=app/src/androidTest/kotlin/com/shieldra/storage/EvidenceKeyResetInstrumentedTest.kt
[ -s "$RESET_TEST" ] || fail "instrumented factory-reset Keystore test (§5.7 runtime) is missing"
grep -q 'deleteEvidenceKeyIrreversibly' "$RESET_TEST" || fail "reset test must use the real Keystore key deletion, not a fake"
grep -q 'KeyMaterialUnavailableException' "$RESET_TEST" || fail "reset test must prove the deleted key never decrypts again (no fallback)"
pass "on-device factory-reset Keystore key destruction is covered by an instrumented test"

# §5.4 centralized retention policy (owner amendment A-1)
[ -s app/src/main/kotlin/com/shieldra/storage/EvidenceRetentionPolicy.kt ] || fail "centralized retention policy layer (§5.4) is missing"
grep -qE 'QUARANTINE_RETENTION_DAYS[^=]*= 30L' app/src/main/kotlin/com/shieldra/storage/EvidenceRetentionPolicy.kt || fail "A-1: quarantine retention must be 30 days"
pass "retention policy layer is centralized with the A-1 30-day quarantine window"

# §5.7 confirmed factory reset (owner amendments A-2, A-3)
[ -s app/src/main/kotlin/com/shieldra/storage/FactoryReset.kt ] || fail "confirmed factory reset (§5.7) is missing"
grep -q 'explicit destructive confirmation' app/src/main/kotlin/com/shieldra/storage/FactoryReset.kt || fail "A-2: reset must require an explicit destructive confirmation"
grep -q 'deleteEvidenceKeyIrreversibly' app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt || fail "A-2: evidence key deletion entrypoint is missing"
pass "factory reset requires destructive confirmation and deletes the evidence key (A-2/A-3)"

# §5.5 evidence export is disabled by default, with no hidden bypass
[ -s app/src/main/kotlin/com/shieldra/storage/EvidenceExportPolicy.kt ] || fail "evidence export policy (§5.5) is missing"
grep -q 'val enabled: Boolean = false' app/src/main/kotlin/com/shieldra/storage/EvidenceExportPolicy.kt || fail "§5.5: export must be disabled by default"
if rg -n 'ACTION_SEND|FileProvider|ClipboardManager|Retrofit|OkHttp|MultipartBody|UploadManager' app/src/main/kotlin/com/shieldra/storage >/dev/null 2>&1; then
  fail "§5.5: a hidden evidence-egress path was found in the storage package"
fi
pass "evidence export is disabled by default with no hidden bypass (§5.5)"

# §6 security failure semantics
SEMANTICS=app/src/main/kotlin/com/shieldra/storage/SecurityFailureSemantics.kt
[ -s "$SEMANTICS" ] || fail "security failure semantics table (§6) is missing"
for c in CORRUPTION MISSING_EVIDENCE MISSING_KEY AAD_MISMATCH MIGRATION_ERROR UNSUPPORTED_DB UNKNOWN_STATE; do
  grep -q "$c" "$SEMANTICS" || fail "§6 condition $c is not represented in the semantics table"
done
grep -q 'FAIL_CLOSED' "$SEMANTICS" || fail "§6 unknown-state fail-closed outcome is missing"
# §6 forbids turning a failure into a silent continuation: the outcome enum must
# have no ALLOW/SUCCESS/CONTINUE/RETRY value.
if grep -qE '^\s*(ALLOW|ALLOWED|SUCCESS|CONTINUE|CONTINU|RESUME_OK|RETRY)\b' "$SEMANTICS"; then
  fail "§6: a non-blocking failure outcome was found (silent continuation is forbidden)"
fi
[ -s app/src/test/kotlin/com/shieldra/storage/SecurityFailureSemanticsTest.kt ] || fail "§6 failure semantics tests are missing"
pass "§6 failure semantics table is exhaustive, fail-closed, and has no silent-continuation outcome"

# §6 / amendment A-4: a missing key on read is RECOVERY_REQUIRED, never laundered
# into "unavailable". The recovery exception must exist, the cipher must classify
# a missing Keystore key as recovery, and the store read path must not swallow it.
CIPHER=app/src/main/kotlin/com/shieldra/storage/KeystoreEvidenceCipherWithAad.kt
STORE=app/src/main/kotlin/com/shieldra/storage/EncryptedEvidenceFileStore.kt
grep -q 'class EvidenceKeyRecoveryRequiredException' "$STORE" || fail "A-4: recovery-required exception is missing"
grep -q 'catch (error: KeyMaterialUnavailableException)' "$CIPHER" || fail "A-4: the cipher must classify a missing key as recovery-required"
grep -q 'throw EvidenceKeyRecoveryRequiredException' "$CIPHER" || fail "A-4: the cipher must raise EvidenceKeyRecoveryRequiredException on a missing key"
grep -q 'catch (error: EvidenceKeyRecoveryRequiredException)' "$STORE" || fail "A-4: the store read must rethrow recovery-required, not launder it"
# Ordering guard: the recovery catch must precede the generic Exception catch that
# would otherwise swallow it into EvidenceUnavailableException. Scope to the read
# path by locating the first generic catch AFTER the recovery catch.
recovery_catch_line="$(grep -n 'catch (error: EvidenceKeyRecoveryRequiredException)' "$STORE" | head -1 | cut -d: -f1)"
generic_catch_line="$(grep -n 'catch (error: Exception)' "$STORE" | awk -F: -v r="$recovery_catch_line" '$1 > r { print $1; exit }')"
[ -n "$recovery_catch_line" ] && [ -n "$generic_catch_line" ] \
  || fail "A-4: recovery-required and generic catches must both be present in the read path"
[ -s app/src/test/kotlin/com/shieldra/storage/EvidenceAccessFailureSemanticsTest.kt ] || fail "A-4 read-path failure semantics tests are missing"
pass "§6/A-4: missing key surfaces as recovery-required and is never laundered into unavailable"

git diff --check
pass "git diff check"
echo "G4-B LOCAL REVIEW PASS — runtime/device evidence still required"
