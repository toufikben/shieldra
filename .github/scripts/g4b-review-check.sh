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

# §15 production-wiring gate: local storage may attach to the running app only
# behind the runtime approval flag. The owner flipped it to true in a visible
# commit (amendment A-5, §14) after the first detector's device verification
# passed (Q-C2 option 1). Reverting it to false is itself a policy change and
# must arrive as another recorded amendment, so the gate here enforces true.
# Opening must go through openOrRecovery so a failed open becomes
# RecoveryRequired, never a destructive recreate (§4.2-§4.4).
GATE=app/src/main/kotlin/com/shieldra/storage/StorageRuntimeApproval.kt
APP=app/src/main/kotlin/com/shieldra/app/ShieldraApp.kt
[ -s "$GATE" ] || fail "storage runtime approval gate is missing"
grep -Eq 'const val approved:[[:space:]]*Boolean[[:space:]]*=[[:space:]]*true' "$GATE" \
  || fail "storage runtime approval was flipped back to false without a recorded owner amendment (sec 14/15, A-5)"
pass "storage install gate is the owner-flipped true (A-5)"

grep -q 'StorageRuntimeApproval.approved' "$APP" \
  || fail "ShieldraApp does not consult the storage runtime approval gate"
grep -q 'decideStorageInstall' "$APP" \
  || fail "ShieldraApp does not route the storage install through decideStorageInstall"
grep -q 'LocalShieldraStorage.openOrRecovery' "$APP" \
  || fail "ShieldraApp must open storage through openOrRecovery, not the raw factory"
grep -q 'storageDecision' "$APP" \
  || fail "ShieldraApp must retain the install decision (RecoveryRequired) as state, not crash"
pass "ShieldraApp install is gated and uses openOrRecovery"

# The raw factory must never be reachable from the app graph: that path skips
# the approval gate and the RecoveryRequired handling. grep (not rg, which is
# absent on the CI runner) so the check actually runs instead of a silent no-op.
if grep -rn 'LocalShieldraStorage\.create' app/src/main/kotlin/com/shieldra/app app/src/main/kotlin/com/shieldra/navigation >/dev/null 2>&1; then
  fail "local storage raw factory is wired into application/navigation, bypassing the approval gate"
fi
pass "local storage raw factory is not reachable from the app graph"

# Honest guard projection (§15/phase-5 boundary): the runtime UI must never
# render a fabricated "Protected" dashboard. DemoData may still back @Preview
# files, but the NavHost production path must go through LockGuardDecision,
# which cannot claim protection because no detector exists yet.
NAVHOST=app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt
if grep -Eq 'DemoData\.(dashboard|recentEvents|eventDetail)[[:space:]]*\(' "$NAVHOST"; then
  fail "NavHost renders a fabricated protection dashboard/event from DemoData at runtime"
fi
grep -q 'lockGuardDashboard(' "$NAVHOST" || fail "NavHost does not use the honest lockGuardDashboard projection"
grep -q 'LockGuardDecision.decide' "$NAVHOST" || fail "NavHost does not derive the guard state from LockGuardDecision"
GUARD=app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDecision.kt
[ -s "$GUARD" ] || fail "honest guard projection (LockGuardDecision) is missing"
grep -q 'GuardStatus.Disabled' "$GUARD" || fail "guards must not be projected as active without detection"
grep -q 'ProtectionVisualState.Disabled' "$GUARD" || fail "the not-active state must map to Disabled, never Protected"
pass "runtime guard projection is honest (no fabricated protection)"

# Q-K-1 (owner answer 2026-10-02): the Premium tab and its route leave this
# release. The screen showed a price, "Buy Lifetime" and five feature claims that
# no code implements, while §30 bars the billing SDK that would make any of it
# real — a reachable false advertisement (§31). Forbidden, not discouraged: the
# tab must be gone, the NavHost must not reference it, and no price string may
# come back without a purchase flow behind it.
ROUTES=app/src/main/kotlin/com/shieldra/app/navigation/ShieldraRoutes.kt
if grep -Eq 'PremiumScreen\(|DemoData\.premium\(|ShieldraRoutes\.PREMIUM' "$NAVHOST"; then
  fail "NavHost renders the Premium screen the owner removed from this release (Q-K-1)"
fi
if grep -qi 'premium' "$ROUTES"; then
  fail "the released bottom tab bar still carries a Premium destination (Q-K-1)"
fi
if grep -q 'demo_premium_price' app/src/main/res/values/strings.xml; then
  fail "a Premium price string is shipped with no purchase flow behind it (Q-K-1)"
fi
pass "no purchasable-looking Premium surface is reachable in this release"

# Persisted events must reach the UI through the pure reader, and the Room
# read must be dispatched off the main thread (Room throws if queried there).
READER=app/src/main/kotlin/com/shieldra/app/presentation/guard/PersistedEventReader.kt
[ -s "$READER" ] || fail "persisted event reader is missing"
grep -q 'storageInstalled' "$READER" || fail "the reader must stay inert when storage is not installed"
grep -Eq 'if \(!storageInstalled\)[[:space:]]+return' "$READER" ||
  fail "the reader must return nothing when storage is not installed"
grep -q 'Dispatchers.IO' "$NAVHOST" || fail "persisted event reads must be dispatched off the main thread"
grep -q 'eventReader.readRecent' "$NAVHOST" || fail "NavHost does not read real persisted events"
grep -q 'eventReader.readOne' "$NAVHOST" || fail "NavHost does not resolve event detail from real persisted events"
pass "persisted events are read off the main thread and only when storage is installed"

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
grep -rn '\.fallbackToDestructiveMigration[[:space:]]*(' app/src/main/kotlin app/src/main/res >/dev/null 2>&1 && fail "destructive migration fallback found in production source"
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
if grep -rEn 'ACTION_SEND|FileProvider|ClipboardManager|Retrofit|OkHttp|MultipartBody|UploadManager' app/src/main/kotlin/com/shieldra/storage >/dev/null 2>&1; then
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

# Protocol §9/§10 rotation trigger and retirement gates. grep, never rg: the CI
# runner has no rg, and `rg … && fail` under set -e silently evaluates false.
ROTATION_LIFECYCLE=app/src/main/kotlin/com/shieldra/storage/EvidenceKeyLifecycle.kt
DURABLE_LIFECYCLE=app/src/main/kotlin/com/shieldra/storage/FileBackedKeyLifecycle.kt
ROTATOR=app/src/main/kotlin/com/shieldra/storage/EvidenceKeyRotator.kt
ROOM_METADATA=app/src/main/kotlin/com/shieldra/storage/room/RoomRotationMetadata.kt
for rotation_file in "$ROTATION_LIFECYCLE" "$DURABLE_LIFECYCLE" "$ROTATOR" "$ROOM_METADATA"; do
  [ -s "$rotation_file" ] || fail "rotation/lifecycle source is missing: $rotation_file"
done

# §10 retirement is a state transition. Nothing on the rotation path may destroy
# Keystore material: the temporary-file cleanup in FileSystemRotationGateway
# deletes evidence temps, never a key, so the gate names key-destruction verbs.
if grep -Eq 'deleteKey|destroyKey|removeKey|deleteAlias|deleteEntries|unloadKey' \
  "$ROTATION_LIFECYCLE" "$DURABLE_LIFECYCLE" "$ROTATOR" "$ROOM_METADATA"; then
  fail "§10: the rotation/retirement path must never destroy a key (destruction is open decision Q-C8)"
fi
pass "§10: retirement marks a version and never destroys a key"

# The trigger policy is exactly the approved one: 90 days OR an explicit manual
# request, and the interval comes from the approved constant, not a literal.
grep -q 'EvidenceKeyPolicy.rotationDue' "$ROTATION_LIFECYCLE" \
  || fail "§3.1: the rotation trigger must use the approved 90-day policy"
grep -q 'RotationTrigger.MANUAL' "$ROTATION_LIFECYCLE" \
  || fail "§3.1: the explicit manual trigger must be a first-class trigger"
pass "§3.1: rotation triggers are the approved interval or an explicit manual request"

# §10 ordering: retirement may only happen after the references have been
# re-scanned. The re-scan must therefore come before lifecycle.completeRotation.
first_scan_line="$(grep -n 'scope.all()' "$ROTATION_LIFECYCLE" | head -1 | cut -d: -f1)"
rescan_line="$(grep -n 'scope.all()' "$ROTATION_LIFECYCLE" | awk -F: -v f="$first_scan_line" '$1 > f { print $1; exit }')"
retire_line="$(grep -n 'lifecycle.completeRotation' "$ROTATION_LIFECYCLE" | head -1 | cut -d: -f1)"
[ -n "$rescan_line" ] && [ -n "$retire_line" ] && [ "$rescan_line" -lt "$retire_line" ] \
  || fail "§10: a key version may only be retired after a surviving-reference re-scan"
pass "§10: retirement is gated behind a surviving-reference re-scan"

# A damaged key lifecycle record must not be papered over: the durable record
# fails closed and the app keeps the failure instead of guessing a version.
grep -q 'check(lines.firstOrNull() == SCHEMA)' "$DURABLE_LIFECYCLE" \
  || fail "the key lifecycle record must reject an unknown schema instead of assuming v1"
grep -q 'lastRotationFailure' "$APP" \
  || fail "ShieldraApp must retain a failed rotation check, not swallow it (§6)"
grep -q 'rotateEvidenceIfNeeded' "$APP" \
  || fail "§9: the approved rotation trigger is not wired into the running app"
grep -q 'checkEvidenceRotation' "$APP" \
  || fail "§9: the rotation check must run from the installed-storage branch only"
pass "§9: the rotation trigger is wired behind the §15 install gate and keeps its failures"

# Batch C (owner directive 2026-09-29): the event pipeline may never assume a
# stage succeeded, and panic must become one persisted event per incident.
PIPELINE=app/src/main/kotlin/com/shieldra/domain/InMemoryEventPipeline.kt
CONTRACTS=app/src/main/kotlin/com/shieldra/domain/EventPipelineContracts.kt
ROOM_REPO=app/src/main/kotlin/com/shieldra/storage/room/RoomEventRepository.kt
PANIC=app/src/main/kotlin/com/shieldra/domain/PanicIncidentCoordinator.kt

# 1. No fabricated success: stages are required constructor arguments, so no
#    caller can run the pipeline with a stage that completes by default.
if grep -Eq '(evidence|delivery): (EvidenceStage|DeliveryStage) = ' "$PIPELINE"; then
  fail "the pipeline still has an always-completing default stage (phantom success)"
fi
grep -Eq 'private val evidence: EvidenceStage,' "$PIPELINE" || fail "the evidence stage must be a required dependency"
grep -Eq 'private val delivery: DeliveryStage,' "$PIPELINE" || fail "the delivery stage must be a required dependency"
pass "pipeline has no default success-producing stages"

# 2. A stage is believed only when its artifact is really in the repository.
grep -q 'stored.evidenceReferences.isEmpty()' "$PIPELINE" ||
  fail "evidence completion is not verified against a persisted evidence reference"
grep -q 'stored.deliveryInformation.none { it.status == DeliveryStatus.SUCCESS }' "$PIPELINE" ||
  fail "delivery completion is not verified against a persisted successful attempt"
pass "stage results are verified against persisted artifacts"

# 3. Artifacts have a real recording path, in the contract and in Room storage.
grep -q 'fun recordEvidence(eventId: EventId, reference: EvidenceReference)' "$CONTRACTS" ||
  fail "the repository contract cannot record collected evidence"
grep -q 'fun recordDeliveryAttempt(eventId: EventId, information: DeliveryInformation)' "$CONTRACTS" ||
  fail "the repository contract cannot record a delivery attempt"
grep -q 'override fun recordEvidence' "$ROOM_REPO" || fail "Room storage cannot record collected evidence"
grep -q 'override fun recordDeliveryAttempt' "$ROOM_REPO" || fail "Room storage cannot record a delivery attempt"
pass "evidence and delivery artifacts are recordable and persisted"

# 4. A fact the database cannot store is refused, never silently dropped.
grep -q 'persists no metadata keys' "$ROOM_REPO" ||
  fail "Room updateState must reject metadata it cannot persist instead of dropping it"
pass "unpersistable metadata fails closed instead of disappearing"

# 5. Panic is an explicit activation with no counter, delay, cooldown or window.
#    The prohibition is checked on code, not prose: the KDoc states the same
#    words to explain what the coordinator must not do.
[ -s "$PANIC" ] || fail "panic incident coordinator is missing"
PANIC_CODE="$(grep -vE '^[[:space:]]*(\*|//|/\*)' "$PANIC")"
if grep -qiE 'cooldown|attemptThreshold|confirmationDelay|timeWindow|debounce|Duration\.of' <<<"$PANIC_CODE"; then
  fail "panic introduced an invented counter, delay, cooldown or time window"
fi
if grep -qE 'Thread\.sleep|delay\(' "$PANIC"; then
  fail "panic must confirm immediately, not after a wait"
fi
grep -q 'GuardKind.PANIC' "$PANIC" || fail "panic must be confirmed through the approved guard engine"
# The invariant is "the confirmed event reaches storage before any response work",
# so accept either the original local name or the Panic-tagged one introduced by
# amendment A-7; both persist the event as the first action.
grep -qE 'repository\.save\((event|panicked)\)' "$PANIC" || fail "the panic event must be persisted before any response work"
grep -q 'isTerminal()' "$PANIC" || fail "the incident boundary must be the event lifecycle, not a time window"
if grep -q 'EventPipeline\|InMemoryEventPipeline' "$PANIC"; then
  fail "the panic coordinator must not run pipeline stages that have not been implemented"
fi
pass "panic confirms, persists first, and bounds the incident by lifecycle only"

# 5b. Amendment A-7: the activation count is durable metadata on the Panic event,
# not a session tally, and it must never be smuggled through the generic metadata
# channel that schema v2 deliberately refuses.
STORE_DURABLE=app/src/main/kotlin/com/shieldra/domain/SecurityEventContracts.kt
ROOM_REPO=app/src/main/kotlin/com/shieldra/storage/room/RoomEventRepository.kt
[ -s "$STORE_DURABLE" ] || fail "the durable Panic activation store is missing"
grep -q 'interface PanicActivationStore' "$STORE_DURABLE" || fail "no durable Panic activation contract exists"
grep -q 'panicActivationCount' "$STORE_DURABLE" || fail "the event does not carry a durable activation count"
grep -q 'PANIC_ACTIVATION' "$STORE_DURABLE" || fail "Panic events are not distinguishable from other security events"
grep -q 'SecurityEventType.PANIC_ACTIVATION' "$PANIC" || fail "a Panic press must only bind to a Panic incident, not any non-terminal event"
grep -q 'recordPanicActivation' "$ROOM_REPO" || fail "Room does not persist Panic activations"
if grep -q 'deferredStage.*activationCount\|activationCount.*deferredStage' "$ROOM_REPO"; then
  fail "the activation count must not ride the generic metadata key"
fi
if grep -qE 'panicActivationCount.*COALESCE|COALESCE.*panicActivationCount' app/src/main/kotlin/com/shieldra/storage/room/StorageDaos.kt && \
   ! grep -q "state NOT IN" app/src/main/kotlin/com/shieldra/storage/room/StorageDaos.kt; then
  fail "the atomic activation increment must refuse a terminal event"
fi
grep -q 'MIGRATION_2_3' app/src/main/kotlin/com/shieldra/storage/room/ShieldraDatabaseMigrations.kt \
  || fail "the v2->v3 explicit migration is missing"
# A real call sets the flag; StorageSchema.requireNoDestructiveFallback() merely
# names the parameter in order to reject it, so match the call, not the word.
if grep -rqE '\.fallbackToDestructiveMigration\(|fallbackToDestructiveMigration[[:space:]]*=[[:space:]]*true' app/src/main/kotlin/; then
  fail "no destructive migration may exist (decision 4-3)"
fi
pass "Panic activation count is durable, Panic-scoped, atomically guarded, and migrated"

# 5c. Amendment A-6: notifications are local, permission-gated, and never claim
# delivery. A local post must never be recorded as a successful delivery, because
# that would advance an event to DELIVERED on the strength of a notification the
# user may never have seen.
NOTIFY=app/src/main/kotlin/com/shieldra/notification/NotificationState.kt
[ -s "$NOTIFY" ] || fail "the notification state model is missing"
grep -q 'enum class NotificationPermissionState' "$NOTIFY" || fail "permission state is not modelled"
grep -q 'sealed interface LocalPostResult' "$NOTIFY" || fail "local posting result is not modelled separately from permission"
# Comment lines are excluded: the KDoc deliberately names DELIVERED to explain
# why a local result must never be called that.
NOTIFY_CODE="$(grep -vE '^[[:space:]]*(\*|//|/\*)' "$NOTIFY")"
if grep -qE '\b(DELIVERED|SENT)\b' <<<"$NOTIFY_CODE"; then
  fail "a local notification result must never be named delivered or sent"
fi
if grep -rqE 'FirebaseMessaging|firebase-messaging|Firebase|Retrofit|OkHttp|smtp|SmtpClient' app/src/main/kotlin/com/shieldra/notification/; then
  fail "Batch E is local notifications only; no remote or external provider may be introduced"
fi
grep -q 'android.permission.POST_NOTIFICATIONS' app/src/main/AndroidManifest.xml \
  || fail "the A-6 notification permission is not declared"
# Amendment A-19 licenses exactly two more permissions, and they belong to the one service that
# carries them. Anything outside this named three is a new amendment, not a manifest edit.
DECLARED="$(grep -oE 'android:name="android\.permission\.[A-Z_]+"' app/src/main/AndroidManifest.xml \
  | sed 's/.*android\.permission\.//;s/"//' | sort | tr '\n' ' ')"
EXPECTED="FOREGROUND_SERVICE FOREGROUND_SERVICE_SPECIAL_USE POST_NOTIFICATIONS "
if [ "$DECLARED" != "$EXPECTED" ]; then
  fail "the manifest declares permissions outside the A-6/A-19 named three; got [$DECLARED]"
fi
grep -q 'RequestPermission' "$NAVHOST" || fail "the permission is never requested from the UI"
if grep -q 'RequestPermission' "$PANIC"; then
  fail "the permission must never be requested from the Panic path"
fi
# "Never asked about" and "refused" are different answers, and the platform
# reports DENIED for both, so the app has to remember that it asked.
NOTIFIER=app/src/main/kotlin/com/shieldra/notification/LocalEventNotifier.kt
[ -s "$NOTIFIER" ] || fail "the local notifier is missing"
grep -q 'hasBeenRequested()' "$NOTIFIER" \
  || fail "the notifier cannot tell a permission it never asked about from a refusal"
grep -q 'recordPermissionRequest()' "$NAVHOST" \
  || fail "answering the system dialog is never recorded, so silence and refusal look alike"
pass "notifications are local, permission-gated, and never claim delivery"

# 6. The dashboard action reaches the real coordinator off the main thread.
grep -q 'PanicIncidentCoordinator(repository' "$NAVHOST" || fail "NavHost does not build the panic coordinator"
grep -q 'coordinator.activate()' "$NAVHOST" || fail "NavHost does not activate the panic coordinator"
if grep -Eq 'onPanic = \{ defer\(\) \}' "$NAVHOST"; then
  fail "NavHost panic action is still a no-op deferral"
fi
pass "the panic button is wired to real persistence off the main thread"

# 7. History labels state what really happened: a state that never delivered is
#    never labelled as sending or delivered.
grep -q 'eventStateLabelRes' "$NAVHOST" || fail "NavHost does not use the honest state label projection"
grep -Eq 'EventState.INITIATED -> .*status_recorded' "$READER" ||
  fail "an INITIATED event must be labelled as recorded, not as sending"
grep -Eq 'EventState.COLLECTING -> .*status_collecting' "$READER" ||
  fail "a COLLECTING event must be labelled as collecting evidence"
pass "event state labels claim only what the state really did"

# 8. The owner's named tests exist.
[ -s app/src/test/kotlin/com/shieldra/domain/PanicIncidentCoordinatorTest.kt ] ||
  fail "panic coordinator tests are missing"
for name in first_panic_creates_exactly_one_real_event_id \
  repeated_panic_while_the_event_is_not_terminal_reuses_the_same_event_id \
  repeated_panic_does_not_start_duplicate_delivery_or_evidence_work \
  activation_count_and_last_activation_stay_consistent_with_the_presses \
  a_new_panic_after_the_previous_event_is_terminal_creates_a_new_event_id \
  concurrent_panic_presses_cannot_create_two_active_events; do
  grep -q "fun $name" app/src/test/kotlin/com/shieldra/domain/PanicIncidentCoordinatorTest.kt ||
    fail "named panic test is missing: $name"
done
[ -s app/src/androidTest/kotlin/com/shieldra/storage/room/RoomPanicPersistenceInstrumentedTest.kt ] ||
  fail "on-device panic persistence test is missing"
pass "the owner's named panic tests and the on-device persistence test are present"

# 9. Batch F — the Settings screen may not promise a channel that does not exist,
#    and the storage row may not print figures the app cannot read (findings 4/5).
RES=app/src/main/res
for key in settings_delivery_channels demo_delivery_channels settings_storage_summary phase3_demo_disclosure settings_section_delivery; do
  if grep -RIn "name=\"$key\"" "$RES" >/dev/null 2>&1; then
    fail "the fabricated Settings claim $key is back"
  fi
done
if grep -RIn '"12 events\|12 حدث' "$RES" >/dev/null 2>&1; then
  fail "a demo storage figure is being shipped as a real count"
fi
grep -q 'honestSettingsRows(' "$NAVHOST" \
  || fail "the Settings screen is built from demo rows again"
grep -q 'SettingsContent.targetOf(id)' "$NAVHOST" \
  || fail "the notification setting has no honest Settings entry point"
if grep -q '"delivery"' app/src/main/kotlin/com/shieldra/app/presentation/settings/SettingsContent.kt; then
  fail "a Settings row still claims a delivery channel"
fi
grep -q 'val storageRowShowsFigures: Boolean = false' app/src/main/kotlin/com/shieldra/app/presentation/settings/SettingsContent.kt \
  || fail "the storage row must not present figures the app cannot read"
pass "Settings rows claim only channels and counts the app really has"

# 10. Batch F — background execution is a contract, not a claim. Amendment A-19 replaced the flat
#     service/FGS bans with a named allowance: exactly one <service>, the approved Motion monitoring
#     class, the `specialUse` type A-17 chose after reviewing the candidates, and its subtype
#     property. Nothing else may appear, and WorkManager stays banned (directives F3/§17).
SERVICES="$(grep -c '<service' app/src/main/AndroidManifest.xml || true)"
if [ "$SERVICES" -ne 1 ]; then
  fail "A-19 allows exactly one <service> element (the Motion monitoring service); found $SERVICES"
fi
grep -q 'android:name="com.shieldra.detection.MotionMonitoringService"' app/src/main/AndroidManifest.xml \
  || fail "the one allowed service is the approved Motion monitoring service (A-19)"
grep -q 'android:foregroundServiceType="specialUse"' app/src/main/AndroidManifest.xml \
  || fail "A-17 approved specialUse as the service type; the manifest no longer declares it"
grep -q 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' app/src/main/AndroidManifest.xml \
  || fail "the specialUse subtype property A-17 requires for the Play review statement is missing"
FGS_PERMS="$(grep -c 'android.permission.FOREGROUND_SERVICE' app/src/main/AndroidManifest.xml || true)"
if [ "$FGS_PERMS" -ne 2 ]; then
  fail "A-19 licenses exactly the two named foreground-service permissions; found $FGS_PERMS"
fi
if grep -RIn 'androidx\.work\|WorkManager\.getInstance\|EnqueueWorker\|\.enqueue(' app/src/main/kotlin; then
  fail "WorkManager is being used in app sources; §17 forbids it as a real-time detector"
fi
if grep -RIn 'androidx.work' gradle/libs.versions.toml app/build.gradle.kts; then
  fail "the WorkManager dependency was added; no batch up to F has a use for it"
fi
pass "one named service with the approved type, the A-19 permission set, and no WorkManager detector"

# 11. Batch F — the execution layer must stay separated from detection, events,
#     persistence and notification (directive F2). It owns none of them.
BG=app/src/main/kotlin/com/shieldra/runtime/BackgroundExecution.kt
[ -s "$BG" ] || fail "the background execution contract is missing"
for forbidden in 'com.shieldra.domain' 'com.shieldra.storage' 'com.shieldra.notification' 'SecurityEvent' 'EventRepository'; do
  if grep -q "$forbidden" "$BG"; then
    fail "the background layer reaches into $forbidden; execution is not detection, persistence, or delivery"
  fi
done
grep -q 'StartResult.AlreadyRunning' "$BG" || fail "start is not idempotent"
grep -q 'StopResult.AlreadyStopped' "$BG" || fail "stop is not idempotent"
grep -q 'StartResult.PrerequisitesMissing' "$BG" || fail "a missing prerequisite is not reported honestly"
grep -q 'val claimsProtection: Boolean get() = this == ActiveGuardMonitoring' "$BG" \
  || fail "a protection claim is available without an active detector"
pass "background execution is idempotent, refuses honestly, and cannot claim protection"

# 12. The owner's named Batch F tests exist.
for name in start_with_every_prerequisite_present_brings_the_layer_up_once \
  starting_twice_reports_already_running_and_never_brings_the_layer_up_twice \
  stopping_twice_reports_already_stopped_and_never_tears_the_layer_down_twice \
  start_with_a_missing_prerequisite_is_refused_and_nothing_is_brought_up \
  a_failing_start_leaves_the_layer_stopped_and_carries_the_real_reason \
  a_recreated_controller_reports_stopped_not_monitoring \
  a_running_execution_layer_without_a_detector_is_not_active_monitoring; do
  grep -q "fun $name" app/src/test/kotlin/com/shieldra/runtime/BackgroundExecutionControllerTest.kt ||
    fail "named background execution test is missing: $name"
done
for name in the_settings_rows_have_a_notification_entry_and_no_delivery_entry \
  the_storage_row_reflects_whether_storage_was_actually_installed \
  the_dashboard_disclosure_stops_calling_real_storage_a_presentation_demo; do
  grep -q "fun $name" app/src/test/kotlin/com/shieldra/app/presentation/settings/SettingsContentTest.kt ||
    fail "named Settings honesty test is missing: $name"
done
pass "the owner's named Batch F tests are present"

# 13. Batch F — the first-run screen and the shield may not claim a watch that no
#     detector performs (directives F2/F8: execution is not monitoring, and a check
#     timestamp is evidence of a check).
for claim in 'quietly guarded' 'presentation demo for a future protection'; do
  if grep -RIn "$claim" app/src/main/res >/dev/null 2>&1; then
    fail "a shipped screen claims protection the app does not provide: $claim"
  fi
done
for locale in values values-ar; do
  grep -q 'name="last_check_none"' "app/src/main/res/$locale/strings.xml" \
    || fail "$locale has no honest wording for a check that never happened"
done
grep -q 'fun lastCheckSeconds(state: LockGuardState): Long? = null' \
  app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDecision.kt \
  || fail "the guard decision must report that no check has ever run"
grep -q 'lastCheckSeconds = LockGuardDecision.lastCheckSeconds(state)' \
  app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDashboardFactory.kt \
  || fail "the dashboard is inventing a last-check time again"
pass "no screen claims a check or a guard that does not exist"

# 14. Batch G — Motion judgement is pure until the device proves delivery (A-8). The
#     magnitude thresholds are the owner's (Q-G1), so no shipping default may exist here,
#     and no boot receiver may appear while reboot auto-start is rejected (A-10).
MOTION=app/src/main/kotlin/com/shieldra/detection/MotionDetection.kt
[ -s "$MOTION" ] || fail "the Motion measurement layer is missing"
if grep -q '^import android\.' "$MOTION"; then
  fail "Motion judgement reached into Android; the sensor adapter is a separate file by A-12"
fi
# The judgement file must stay registrable-free: the Android surface is MotionSensorAdapter.kt.
if grep -qE 'SensorManager|registerListener|getDefaultSensor' "$MOTION"; then
  fail "Motion judgement registers sensors itself; the adapter owns registration (A-12)"
fi
for default in 'strongDeltaMs2: Double = ' 'noiseFloorDeltaMs2: Double = ' 'minEpisodeMs: Long = ' 'maxSampleGapMs: Long = '; do
  if grep -q "$default" "$MOTION"; then
    fail "a Motion magnitude threshold has a shipping default value ($default); Q-G1 belongs to the owner"
  fi
done
grep -q 'MotionReading.Unreliable' "$MOTION" \
  || fail "Motion can no longer report unreliable samples instead of a quiet device"
if grep -RIn 'RECEIVE_BOOT_COMPLETED\|BOOT_COMPLETED' app/src/main; then
  fail "boot auto-start was added while amendment A-10 rejects it for this phase"
fi
for name in a_strong_movement_that_last_longer_than_the_approved_three_seconds_confirms_a_security_event \
  a_burst_shorter_than_the_minimum_episode_is_isolated_noise_and_never_an_occurrence \
  two_strong_bursts_inside_the_repetition_window_confirm_and_a_single_one_does_not \
  two_bursts_eleven_seconds_apart_are_outside_the_approved_window_and_stay_signal_only \
  a_silence_longer_than_the_sampling_limit_is_unreliable_rather_than_a_quiet_device \
  a_non_finite_sample_is_unreliable_and_can_never_confirm \
  no_samples_are_insufficient_and_are_never_reported_as_a_still_device \
  the_same_movement_on_a_phone_turned_on_its_side_measures_the_same \
  values_between_the_noise_floor_and_the_strong_level_start_no_episode \
  a_movement_that_never_settles_reports_the_full_measured_duration_not_zero \
  the_peak_recorded_for_an_episode_is_the_largest_deviation_it_saw; do
  grep -qE "fun $name\(" app/src/test/kotlin/com/shieldra/detection/MotionWindowEvaluatorTest.kt \
    || fail "the Motion test $name was removed or renamed"
done
pass "Motion judgement is pure, injected-calibrated, fail-closed, and covered by the owner's named tests"

# 15. Batch G slice G3 — the adapter and the sampling/quality layer, and only those. Amendment A-12
#     limits this slice to signal data: no Security Event, severity, Panic, notification, storage
#     write, evidence, camera, location, messaging or cloud path may appear in the shipping code,
#     the one-shot tripwire may not be armed from the continuous layer, nothing that was *measured*
#     on RMX3910 may become a constant, and nothing may instantiate the adapter until a monitoring
#     surface exists (A-8). Comment lines are excluded: they are where the measurements are cited.
SIGNAL=app/src/main/kotlin/com/shieldra/detection/MotionSignal.kt
ADAPTER=app/src/main/kotlin/com/shieldra/detection/MotionSensorAdapter.kt
[ -s "$SIGNAL" ] || fail "the Motion sampling/quality layer (G3) is missing"
[ -s "$ADAPTER" ] || fail "the Motion sensor adapter (G3) is missing"
grep -q 'fun normalize(' "$SIGNAL" || fail "the signal layer no longer describes a stream"
grep -q 'MotionSignalQuality.EMPTY' "$SIGNAL" \
  || fail "the signal layer can no longer separate 'nothing measured' from 'device at rest'"
grep -q 'fun startReporting(' "$ADAPTER" || fail "the adapter no longer owns registration"
grep -q 'fun stopReporting(' "$ADAPTER" \
  || fail "the adapter cannot stop, so a stream would outlive whoever started it"

G3_CODE="$(grep -hvE '^[[:space:]]*(/\*|\*|//)' "$SIGNAL" "$ADAPTER")"
if printf '%s\n' "$G3_CODE" | grep -qE 'SecurityEvent|EventId|Panic|Notification|Room|Evidence|Camera|Location|Sms|Mail|firebase|WorkManager|RequestQueue|cool[A-Za-z]*down|confirm'; then
  fail "the G3 signal layer reaches a decision, a channel, or storage; A-12 limits it to signal data"
fi
printf '%s\n' "$G3_CODE" | grep -q 'isFinite' \
  || fail "the signal layer no longer rejects samples that are not finite accelerations"
if ! printf '%s\n' "$G3_CODE" | grep -q 'median'; then
  fail "the sampling interval is no longer measured from delivered timestamps"
fi

# A-12 rule 1: SIGNIFICANT_MOTION may only be looked up, never armed, from this layer.
if grep -RInE 'requestTriggerSensor|cancelTriggerSensor|TriggerEventListener' "$SIGNAL" "$ADAPTER"; then
  fail "G3 armed the one-shot tripwire; it belongs to its own layer, not to continuous sampling (A-12)"
fi
SIG_LINES="$(grep -n 'SIGNIFICANT_MOTION' "$ADAPTER" | grep -vE 'getDefaultSensor|^[0-9]+:[[:space:]]*(/\*|\*|//)' || true)"
[ -z "$SIG_LINES" ] || fail "the tripwire is used beyond an availability lookup in G3: $SIG_LINES"

# A-12 rule 3: numbers measured on one phone are not shipping constants.
for measured in '106' '0\.52' '0\.285' '0\.395' '0\.386' '0\.505' '20_000'; do
  if printf '%s\n' "$G3_CODE" | grep -qE "$measured"; then
    fail "a value measured on RMX3910 in slice G2 became a constant in shipping code ($measured)"
  fi
done
if grep -q 'requestedReportPeriodUs: Int = ' "$ADAPTER"; then
  fail "the adapter ships a default reporting period; the rate is the owner's calibration decision"
fi

# A-19, with A-18 correction (أ): the approved service is the adapter's only owner in the shipping
# app — a second owner would mean two `isReporting` flags and two unregistrations. The boot ban (A-10)
# and the G3 signal-data rules above are untouched by that transition.
SERVICE=app/src/main/kotlin/com/shieldra/detection/MotionMonitoringService.kt
[ -s "$SERVICE" ] || fail "the Motion monitoring service A-19 was written for is missing"
grep -q 'MotionSensorAdapter(' "$SERVICE" \
  || fail "the monitoring service no longer owns the adapter, so nothing registers the accelerometer"
USES="$(grep -RIn 'MotionSensorAdapter(' app/src/main/kotlin --include='*.kt' | grep -v "$ADAPTER" | grep -v "$SERVICE" || true)"
[ -z "$USES" ] || fail "a second Motion adapter owner appeared in the shipping app; A-19 allows the service alone: $USES"
for forbidden in 'RECEIVE_BOOT_COMPLETED' 'BOOT_COMPLETED'; do
  if grep -q "$forbidden" app/src/main/AndroidManifest.xml; then
    fail "$forbidden appeared while A-10 holds boot auto-start back"
  fi
done

for name in fewer_than_two_samples_is_reported_as_nothing_measured_not_as_a_still_device \
  a_sample_that_is_not_a_finite_acceleration_is_dropped_and_the_stream_is_marked_invalid \
  two_samples_that_share_one_millisecond_are_invalid_because_the_clock_cannot_separate_them \
  the_sampling_rate_is_measured_from_the_timestamps_that_arrived_not_from_what_was_requested \
  a_delivery_that_stops_beyond_the_injected_tolerance_is_irregular_and_reports_the_measured_numbers \
  the_stall_tolerance_has_to_be_a_real_number_above_one; do
  grep -qE "fun $name\(" app/src/test/kotlin/com/shieldra/detection/MotionSignalNormalizerTest.kt \
    || fail "the G3 signal test $name was removed or renamed"
done
for name in starting_reporting_registers_the_accelerometer_and_nothing_else \
  the_adapter_delivers_accelerometer_samples_stamped_by_the_sensor_clock \
  stopping_the_adapter_stops_the_stream \
  the_measured_rate_is_computed_from_delivered_timestamps_not_from_the_request \
  a_device_without_an_accelerometer_is_reported_as_unavailable_and_never_registers; do
  grep -qE "fun $name\(" app/src/androidTest/kotlin/com/shieldra/detection/MotionSensorAdapterInstrumentedTest.kt \
    || fail "the G3 on-device test $name was removed or renamed"
done
pass "the G3 adapter and signal layer stay inside amendment A-12: signal data only, measured rates, no tripwire arming"

# 16. Batch G slice G4-A — a measurement campaign, not a feature. Amendment A-13 allows one extra
#     test-only vehicle and nothing else: it may not gain a wake lock, a battery exemption, a
#     service, an FGS type or a permission, and it must not become a production monitoring path.
#     The four phase windows, the test-only delivery_state rule and the evidence line are pinned so
#     a later edit cannot quietly shorten the campaign or invent a different "stopped" definition.
G4A=app/src/androidTest/kotlin/com/shieldra/detection/MotionBackgroundMeasurementInstrumentedTest.kt
[ -s "$G4A" ] || fail "the G4-A measurement vehicle is missing"
for name in phase_a_baseline_with_the_screen_on_measures_the_reference_rate \
  phase_b_screen_off_sixty_seconds_measures_whether_delivery_survives_the_screen_going_dark \
  phase_c_screen_off_five_minutes_measures_whether_delivery_survives_a_longer_interval \
  phase_d_screen_off_fifteen_minutes_looks_for_degradation_or_cessation_over_a_long_interval; do
  grep -qE "fun $name\(" "$G4A" || fail "the G4-A phase test $name was removed or renamed"
done
for window in 'PHASE_A_WINDOW_MS = 60_000L' 'PHASE_B_WINDOW_MS = 60_000L' \
  'PHASE_C_WINDOW_MS = 300_000L' 'PHASE_D_WINDOW_MS = 900_000L'; do
  grep -q "$window" "$G4A" || fail "G4-A lost an owner-approved phase duration ($window)"
done
for state in 'NO_DATA' 'CONTINUING' 'INTERRUPTED' 'STALL_TOLERANCE_FACTOR = 3.0' 'G4A_JSON=' \
  'clockResolutionDrops' 'screen_off_confirmed_before_window'; do
  grep -q "$state" "$G4A" || fail "the G4-A evidence no longer carries $state"
done

G4A_CODE="$(grep -hvE '^[[:space:]]*(/\*|\*|//)' "$G4A")"
if printf '%s\n' "$G4A_CODE" | grep -qE 'SecurityEvent|EventId|Panic|Notification|Room|Evidence|Camera|Location|Sms|Mail|firebase|WorkManager|RequestQueue|cool[A-Za-z]*down'; then
  fail "the G4-A vehicle reaches a decision, a channel, or storage; A-13 allows measurement only"
fi
# 'confirm' is deliberately not on this list: the vehicle must say whether the screen was *confirmed*
# asleep, and that word is evidence about the experiment, not a security confirmation.

# A-13: the campaign must run under today's execution conditions, so nothing that would change them
# may appear anywhere in the app's sources — a wake lock in particular would answer a different
# question than the one the owner asked.
for forbidden in 'WAKE_LOCK' 'WakeLock' 'newWakeLock' 'IGNORE_BATTERY_OPTIMIZATIONS' \
  'requestIgnoreBatteryOptimizations' 'isIgnoringBatteryOptimizations' 'setSustainedPerformanceMode'; do
  if grep -RIn "$forbidden" app/src --include='*.kt' --include='*.xml' ; then
    fail "$forbidden appeared while G4-A is only a measurement (A-13 forbids changing execution conditions)"
  fi
done
# A-19 replaced this manifest ban once the campaign and its report were closed: what A-13 actually
# forbade was adding a service or a permission to make a measurement pass. That rule is still
# enforced, by §10 allowing exactly one named service and nothing else. The execution conditions the
# campaign measured under remain untouched — no wake lock, no battery exemption anywhere.
for forbidden in 'RECEIVE_BOOT_COMPLETED' 'BOOT_COMPLETED'; do
  if grep -q "$forbidden" app/src/main/AndroidManifest.xml; then
    fail "$forbidden appeared while A-10 holds boot auto-start back"
  fi
done

# The vehicle stays a vehicle: nothing in the running app may reference it.
MAIN_G4A="$(grep -RIn 'MotionBackgroundMeasurement' app/src/main --include='*.kt' --include='*.xml' || true)"
[ -z "$MAIN_G4A" ] || fail "the G4-A measurement vehicle is wired into production code: $MAIN_G4A"
pass "the G4-A vehicle stays a measurement: four owner phases, fixed evidence rule, no wake lock, no FGS, no production path"

# 17. Onboarding copy must say what the onboarding screens actually do. Found on the device
#     pass over `adf68cc`: the permission step reused the A-6 Settings sentence that claims
#     SHIELDRA asks for the notification permission "only here", while that step's Continue is
#     `defer()` (ShieldraNavHost.kt) and never asks; and the protection step printed the same
#     sentence twice, because lock_guard_window already contains attempt_recording_note.
for locale in values values-ar; do
  STRINGS="app/src/main/res/$locale/strings.xml"
  onboarding_reason="$(grep -o 'name="onboarding_notification_reason">[^<]*' "$STRINGS" | sed 's/^[^>]*>//' || true)"
  [ -n "$onboarding_reason" ] || fail "$locale has no onboarding_notification_reason for the deferred onboarding step"
  if printf '%s' "$onboarding_reason" | grep -qE 'only here|هنا فقط'; then
    fail "$locale onboarding copy claims the permission is asked on a screen whose Continue is deferred"
  fi
done
grep -q 'reason = stringResource(R.string.onboarding_notification_reason)' \
  app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt \
  || fail "the onboarding permission step still borrows the A-6 Settings claim"
PROTECTION_STEP=app/src/main/kotlin/com/shieldra/app/presentation/screen/onboarding/ProtectionSetupScreen.kt
if grep -q 'R.string.lock_guard_window' "$PROTECTION_STEP" && grep -q 'R.string.attempt_recording_note' "$PROTECTION_STEP"; then
  fail "the protection step prints one sentence twice (lock_guard_window already contains attempt_recording_note)"
fi
# Since amendment A-19 the build does run continuous Motion monitoring, so the welcome screen may
# not say it does not. Found on the A-19 device pass: the sentence survived the service landing.
for locale in values values-ar; do
  STRINGS="app/src/main/res/$locale/strings.xml"
  welcome_body="$(grep -o 'name="welcome_body">[^<]*' "$STRINGS" | sed 's/^[^>]*>//' || true)"
  [ -n "$welcome_body" ] || fail "$locale has no welcome_body"
  if printf '%s' "$welcome_body" | grep -qE 'not part of this build|ليست جزءًا من هذا الإصدار'; then
    fail "$locale welcome_body denies continuous monitoring that amendment A-19 shipped"
  fi
done
pass "onboarding copy claims only what the onboarding screens really do"

# 18. Batch G slice G4/G5 — the pure validation seam and, since amendment A-19, the service that
#     finally calls it. A-15 recorded the foreground-service architecture and withheld its code; A-17
#     closed Q-G1; A-18 approved the design and its three corrections; A-19 licensed the code with the
#     named gate transition. The seam's contract is unchanged: the stream quality G3 measured still
#     arrives as a required argument, it may only ever lower what the observation claims, and the
#     foreground-service surface may appear in the approved service and manifest and nowhere else.
DETECTION=app/src/main/kotlin/com/shieldra/detection/MotionDetection.kt
RUNTIME=app/src/main/kotlin/com/shieldra/runtime/MotionRuntime.kt
FGS_CARRIERS="$(grep -RIn 'startForeground\|FOREGROUND_SERVICE\|foregroundServiceType\|SPECIAL_USE\|specialUse\|<service' app/src/main --include='*.kt' --include='*.xml' | grep -v "$SERVICE" | grep -v "$RUNTIME" | grep -v 'app/src/main/AndroidManifest.xml' || true)"
[ -z "$FGS_CARRIERS" ] || fail "the foreground-service surface appeared outside the service, its runtime handle and the manifest (A-19): $FGS_CARRIERS"
grep -q 'streamQuality: MotionSignalQuality' "$DETECTION" \
  || fail "the Motion seam no longer receives what the signal layer measured about the stream, so G3's verdict is dropped on the way to the engine"
if grep -Eq 'streamQuality: MotionSignalQuality[[:space:]]*=' "$DETECTION"; then
  fail "the seam defaults the stream quality: a caller could ask for a judgement without ever saying whether the stream could be believed"
fi
for quality in 'MotionSignalQuality.EMPTY' 'MotionSignalQuality.INVALID' 'MotionSignalQuality.IRREGULAR' 'MotionSignalQuality.USABLE'; do
  grep -q "$quality" "$DETECTION" || fail "the seam no longer names $quality, so that kind of stream would fall through to a default"
done
PIPELINE=app/src/main/kotlin/com/shieldra/detection/MotionGuardPipeline.kt
[ -s "$PIPELINE" ] || fail "the approved Motion pipeline is missing"
SEAM_CALLERS="$(grep -RIn 'MotionGuardObservation' app/src/main --include='*.kt' | grep -v "$DETECTION" | grep -v "$PIPELINE" || true)"
[ -z "$SEAM_CALLERS" ] || fail "the Motion seam is called from outside the approved pipeline (A-19): $SEAM_CALLERS"
for name in a_stream_the_signal_layer_measured_as_irregular_is_never_reported_with_valid_sensor_quality \
  a_stream_the_signal_layer_measured_as_invalid_is_never_reported_with_valid_sensor_quality \
  a_stream_with_nothing_measured_is_reported_as_unavailable_rather_than_as_a_still_device \
  an_untrusted_stream_reports_no_movement_at_all_rather_than_a_movement_it_cannot_believe \
  a_repeated_movement_on_an_untrusted_stream_never_reaches_the_owner_s_repetition_rule \
  three_seconds_of_strong_movement_on_an_untrusted_stream_stays_signal_only \
  the_same_movement_on_a_trusted_stream_still_confirms \
  an_untrusted_stream_is_refused_for_every_injected_calibration \
  changing_the_injected_calibration_changes_the_decision_deterministically \
  a_deviation_below_the_noise_floor_starts_no_episode \
  a_deviation_just_under_the_noise_floor_starts_no_episode \
  a_deviation_between_the_noise_floor_and_the_strong_level_starts_no_episode \
  a_deviation_just_under_the_strong_level_starts_no_episode \
  a_deviation_above_the_strong_level_starts_an_episode; do
  grep -qE "fun $name\(" app/src/test/kotlin/com/shieldra/detection/MotionStreamQualityTest.kt \
    || fail "the G4 stream-quality test $name was removed or renamed"
done
for locale in values values-ar; do
  grep -q 'name="guard_subtitle_inactive">' "app/src/main/res/$locale/strings.xml" \
    || fail "$locale lost the 'not monitoring yet' copy every guard still needs (§9: a guard that is not running says so)"
  grep -q 'name="guard_subtitle_motion_active">' "app/src/main/res/$locale/strings.xml" \
    || fail "$locale has no Motion copy for a service that really reports (A-18)"
done
grep -q 'enum class MonitoringClaim' app/src/main/kotlin/com/shieldra/runtime/BackgroundExecution.kt \
  || fail "MonitoringClaim was removed: §9 keeps measured signal data separate from any monitoring claim"
pass "the G4 seam still refuses a stream the signal layer could not believe, and the FGS surface stays named"

# 19. Batch G slice Q-G1 — the one manual calibration the owner authorized in the A-15 directive. It
#     is a measurement harness and nothing more: it must go through the adapter G3 already verified
#     (A-12 keeps registration there), it must not reach a decision, a channel, storage or a sensor
#     the owner has not approved, it must not move the phone by itself (the owner performs the single
#     deliberate carry), and its aids must be named as probes so no number can be mistaken for the
#     shipping strong-motion level the owner still owes (Q-G1).
Q1=app/src/androidTest/kotlin/com/shieldra/detection/MotionCalibrationInstrumentedTest.kt
[ -s "$Q1" ] || fail "the Q-G1 calibration harness is missing"
grep -q 'MotionSensorAdapter(' "$Q1" \
  || fail "the Q-G1 harness no longer measures through the verified adapter"
if grep -qE 'registerListener|getDefaultSensor' "$Q1"; then
  fail "the Q-G1 harness registers sensors itself; the adapter owns registration (A-12)"
fi
Q1_CODE="$(grep -hvE '^[[:space:]]*(/\*|\*|//)' "$Q1")"
if printf '%s\n' "$Q1_CODE" | grep -qE 'SecurityEvent|EventId|Panic|Notification|Room|Evidence|Camera|Location|Sms|Mail|firebase|WorkManager|startForeground|RequestQueue'; then
  fail "the Q-G1 harness reaches a decision, a channel, storage or an unapproved sensor; §5 allows measurement only"
fi
if printf '%s\n' "$Q1_CODE" | grep -qE 'executeShellCommand|injectEvents|getUiAutomation'; then
  fail "the Q-G1 harness drives the device itself; §5 forbids that — the owner performs the one deliberate carry"
fi
for aid in 'PHASE_REST_MS = 8_000L' 'PHASE_CARRY_MS = 20_000L' 'PHASE_SETTLE_MS = 6_000L' 'PROBE' '"RAW' '"DERIVED'; do
  grep -q "$aid" "$Q1" || fail "the Q-G1 harness no longer carries $aid, so the §5 sequence or the raw/derived split is gone"
done
for field in 'sample_count' 'deviation_max' 'deviation_median' 'deviation_p90' 'deviation_p99' \
  'median_interval_ms' 'longest_gap_ms' 'clock_resolution_drops' 'sensor_quality' \
  'deliberate_movement_identified' 'gravity_deviation' 'screen_interactive_at_end'; do
  grep -q "$field" "$Q1" || fail "the Q-G1 evidence no longer reports $field (§6)"
done
HARNESS_IN_MAIN="$(grep -RIn 'MotionCalibrationInstrumented' app/src/main --include='*.kt' --include='*.xml' || true)"
[ -z "$HARNESS_IN_MAIN" ] || fail "the Q-G1 measurement harness is wired into production code: $HARNESS_IN_MAIN"
pass "the Q-G1 harness stays a test-only measurement: adapter-owned registration, §5 sequence, §6 fields, no device driving"

# 20. Batch G slice G5 — the monitoring surface amendment A-19 licensed. These are the minimum
#     assertions that protect the decisions which came with that transition and nothing more (the
#     owner's «التسريع المنضبط»: no barriers without benefit). The six approved calibration figures
#     are stated once, A-9 fixes the ongoing notice's wording, the notification permission is still
#     asked only from Settings (A-6), and the dashboard's Motion state is derived only from the claim
#     a live service publishes (A-18 corrections أ/ب).
CAL=app/src/main/kotlin/com/shieldra/detection/ApprovedMotionCalibration.kt
[ -s "$CAL" ] || fail "the object that carries the approved Motion calibration is missing"
for approved in 'strongDeltaMs2: Double = 4.6' 'noiseFloorDeltaMs2: Double = 0.8' 'minEpisodeMs: Long = 500L' \
  'maxSampleGapMs: Long = 100L' 'requestedReportPeriodUs: Int = 20_000' 'stallToleranceFactor: Double = 11.0'; do
  grep -q "$approved" "$CAL" || fail "A-17/A-18 lost an approved calibration figure ($approved)"
done
DUPLICATE_FIGURES="$(grep -RIn '4\.6\|0\.8\|20_000\|11\.0\|500L\|100L' app/src/main/kotlin/com/shieldra/detection app/src/main/kotlin/com/shieldra/runtime --include='*.kt' | grep -v "$CAL" || true)"
[ -z "$DUPLICATE_FIGURES" ] || fail "an approved calibration figure was stated outside the named object, so it could drift or a seventh could appear: $DUPLICATE_FIGURES"
# A-9: the exact wording an ongoing notice may carry while this service really runs.
grep -q '<string name="notification_motion_ongoing_title">SHIELDRA protection is active</string>' app/src/main/res/values/strings.xml \
  || fail "the ongoing notice no longer carries A-9's approved English wording"
grep -q '<string name="notification_motion_ongoing_title">حماية SHIELDRA نشِطة</string>' app/src/main/res/values-ar/strings.xml \
  || fail "the ongoing notice no longer carries A-9's approved Arabic wording"
if grep -RIn '24/7\|مضمونة' app/src/main/res --include='strings.xml' | grep '<string'; then
  fail "a shipped string claims permanent or guaranteed protection (A-9)"
fi
# A-6/Q-G5-b: monitoring refuses to start without a visible notice, and asking for the permission
# stays in the notification setup screen — never in the monitoring path and never in Panic.
if grep -RIn 'RequestPermission' app/src/main/kotlin/com/shieldra/detection app/src/main/kotlin/com/shieldra/runtime; then
  fail "the monitoring path asks for POST_NOTIFICATIONS itself; A-6 keeps the request in Settings"
fi
TRUTH=app/src/main/kotlin/com/shieldra/runtime/MotionMonitoringTruth.kt
grep -q 'fun claim(serviceRunning: Boolean, sensorReporting: Boolean)' "$TRUTH" \
  || fail "the monitoring claim no longer reads the two platform facts A-18 (ب) named"
grep -q 'MotionMonitoringPresentation.guardStatus(motionClaim)' \
  app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDashboardFactory.kt \
  || fail "the dashboard no longer derives Motion's state from the published claim"
grep -q 'MotionRuntime.state.collectAsState()' "$NAVHOST" \
  || fail "the running app does not read the monitoring claim it renders"
for name in a_service_that_is_not_running_renders_the_motion_guard_as_disabled \
  a_running_service_without_a_live_sensor_registration_renders_disabled_not_active \
  only_a_live_registration_renders_the_motion_guard_as_active \
  no_claim_ever_renders_a_signal_or_a_locked_guard; do
  grep -q "fun $name" app/src/test/kotlin/com/shieldra/app/presentation/guard/MotionMonitoringPresentationTest.kt ||
    fail "the named Motion projection test is missing: $name"
done
pass "A-19's licensed surface stays named: one calibration object, A-9 wording, no permission ask in monitoring, and a claim-derived dashboard"

# 21. Batch G slice G5 — the four presentation-truth defects found on the A-19 device pass. These are
#     display-path rules only: no threshold, no lifecycle, and no persistence semantics live here.
READER=app/src/main/kotlin/com/shieldra/app/presentation/guard/PersistedEventReader.kt
grep -q 'type = persistedEventTypeOf(this.type)' "$READER" \
  || fail "a persisted row's history type is not read from the row itself, so a Motion event can be filed under another guard (A-19 device pass)"
if grep -q 'val type: EventType' "$READER"; then
  fail "PersistedEventLabels still carries one type for every row, which is the fixed-Panic defect"
fi
for guard_type in CONFIRMED_SECURITY_EVENT PANIC_ACTIVATION MOTION_CONFIRMED_EVENT; do
  grep -q "SecurityEventType.$guard_type" "$READER" \
    || fail "the history-type mapping no longer covers $guard_type, so that row would fall through to a default"
done
grep -q 'MotionEventWrites.record(confirmation)' "$SERVICE" \
  || fail "the monitoring service no longer invalidates the event lists when it writes an event, so a Motion event only appears after a restart"
grep -q 'MotionEventWrites.state.collectAsState()' "$NAVHOST" \
  || fail "the app never reads the Motion write token, so the reload it pins a key on is dead"
grep -Eq 'produceState\(initialValue = emptyList<EventUiModel>\(\)[^)]*motionEventWrites' "$NAVHOST" \
  || fail "the recent-event load has no Motion-write key, so History and the dashboard keep a stale list"
DASHBOARD_SCREEN=app/src/main/kotlin/com/shieldra/app/presentation/screen/dashboard/DashboardScreen.kt
MONITORING_DERIVATIONS="$(grep -c 'val motionMonitoring =' "$DASHBOARD_SCREEN" || true)"
[ "$MONITORING_DERIVATIONS" = "1" ] \
  || fail "the dashboard derives Motion's monitoring state $MONITORING_DERIVATIONS times; one tile, one derivation (A-18)"
grep -Eq 'dashboardMode\(model.storageInstalled, motionMonitoring = motionMonitoring\)' "$DASHBOARD_SCREEN" \
  || fail "the dashboard disclosure does not follow the same Motion truth the tile and the button follow"
for name in a_newly_written_motion_event_raises_the_reload_token \
  a_movement_that_joined_the_open_incident_writes_no_row_so_the_token_stays \
  the_dashboard_disclosure_stops_denying_monitoring_while_motion_is_monitoring; do
  grep -rq "fun $name" app/src/test || fail "the named G5 truthfulness test is missing: $name"
done
pass "the event list shows each row's own guard, reloads on a real Motion write, and the disclosure follows the published claim"

git diff --check
pass "git diff check"
echo "G4-B LOCAL REVIEW PASS — runtime/device evidence still required"
