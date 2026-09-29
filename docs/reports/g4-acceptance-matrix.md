# G4 Acceptance Matrix

**Repository:** `toufikben/shieldra`
**Scope:** Local Room/evidence foundation and G4-B closure evidence
**Reconciled against actual repository state:** 2026-09-29, commit `22603d2` (protocol Batch B;
CI `36612265938` — `BUILD SUCCESSFUL`, `CI TEST SUMMARY: 194 tests, 194 passed, 0 failures,
0 errors, 0 skipped`; full instrumented suite `OK (40 tests)` on the owner RMX3910). Previous
reconciliations: `fcfcd96` / CI `36559760906` (171 JVM tests).
**Current conclusion:** **PARTIAL — storage, crypto and the key-lifecycle trigger/retirement
are DEVICE VERIFIED; independent security review, threat-model sign-off and a multi-device
matrix are not.** G4 is substantially verified and *not* production-approved.

This matrix deliberately separates implementation from verification. A `YES` in the Code column
does not imply runtime security verification, and `DEVICE VERIFIED` never means
`SECURITY REVIEWED` or `PRODUCTION APPROVED`.

## Status vocabulary used below

| Level | Asserted only from |
|---|---|
| IMPLEMENTED | source exists |
| TESTED | a named test asserts the behaviour |
| CI VERIFIED | a green run on `main` executed that test |
| DEVICE VERIFIED | the instrumented test actually ran on RMX3910 (`IR7X5DJ7KZGEHMS8`) using app + androidTest APKs from the **same** run id |
| SECURITY REVIEWED | an independent reviewer signed off — **nobody has yet** |
| CLOSED / PRODUCTION APPROVED | owner decision — **not granted** |

## Matrix

| Requirement | Code | Test | CI | Device | Status | Evidence or blocker |
|---|---:|---:|---:|---:|---|---|
| Room entities and exported schema exist | YES | YES | YES | YES | DEVICE VERIFIED | `RoomEventRepositoryInstrumentedTest` executes real Room create/insert/read on the phone. |
| Foreign keys and indexes are declared | YES | YES | YES | YES | DEVICE VERIFIED | Runtime insert/reopen executes against the declared schema on RMX3910. |
| Event state persistence and expected-state update | YES | YES | YES | YES | DEVICE VERIFIED | `RoomEventRepositoryInstrumentedTest` + `PersistedEventReadingInstrumentedTest` (read-back of persisted events). |
| Event ID duplicate protection | YES | YES | YES | YES | DEVICE VERIFIED | Repository idempotency asserted on the device database. |
| Evidence encrypted-file write/read | YES | YES | YES | YES | DEVICE VERIFIED | `RealAesGcmAadTest` performs real AES-256-GCM through Android Keystore on the phone. |
| AAD binding to evidence identity | YES | YES | YES | YES | DEVICE VERIFIED | `RealAesGcmAadTest` proves correct AAD succeeds and wrong version / wrong class / wrong evidenceId / modified length fail. AAD-v1 is the approved canonical format. |
| Android Keystore key generation and AES-GCM policy | YES | YES | YES | YES | DEVICE VERIFIED — **with a capability caveat** | Works on RMX3910. **RMX3910 has NO StrongBox**, so StrongBox-backed keys are *not* device-verified anywhere. Runtime capability detection uses `PackageManager.FEATURE_STRONGBOX_KEYSTORE`; no batch may claim StrongBox support it has not measured. |
| Key ownership, invalidation, rotation algorithm, loss recovery, **trigger (§9)** | YES | YES | YES | YES | DEVICE VERIFIED — algorithm **and trigger** | `EvidenceKeyRotationInstrumentedTest` (algorithm), `StorageRecoveryInstrumentedTest` (loss), and `EvidenceKeyLifecycleInstrumentedTest` (trigger). The trigger now has a production caller: `EvidenceRotationOrchestrator` + `LocalShieldraStorage.rotateEvidenceIfNeeded()` + the interval check in `ShieldraApp.onCreate`. **Honest limits:** it is reachable only while `StorageRuntimeApproval.approved` is true (still `false`, Batch D), the manual path has no UI affordance, and the approved successor chain ends at v2 — a second rotation reports `NO_APPROVED_SUCCESSOR` rather than inventing a key version. |
| Old-key retirement lifecycle (§10) | YES | YES | YES | YES | DEVICE VERIFIED **as a state transition only** | `FileBackedKeyLifecycle` (durable active/retired/clock record in `noBackupFilesDir`, schema-checked, fail-closed when damaged) + `EvidenceRotationOrchestratorTest` + `FileBackedKeyLifecycleTest` (23 JVM) + the on-device `production_wiring_rotates_real_keystore_evidence_and_retires_the_old_generation`. Retirement happens **only** after a whole-set re-scan finds zero surviving references, and a `g4b` line-order gate enforces that ordering mechanically. **Destruction of a retired key is NOT implemented and is a held owner decision (Q-C8).** |
| Room migration and recovery | YES | YES | YES | **PARTIAL** | DEVICE VERIFIED (upgrade only) | `ShieldraDatabaseMigrationInstrumentedTest` has exactly two tests: `migration_1_2_preserves_rows_and_defaults_rotation_versions` and `migration_1_2_is_registered_for_the_storage_factory`. **Downgrade is NOT tested and NOT implemented** — it stays fail-closed by policy (§4.3 forbids destructive migration), and Q-D17 (reinstall/downgrade/backup-restore on a real device) remains `NOT RUN`. |
| Corruption classification and recovery state | YES | YES | YES | YES | DEVICE VERIFIED | `StorageRecoveryInstrumentedTest`. Approved semantics: corruption / AAD mismatch / missing evidence → `UNAVAILABLE`; unexpected missing key → `RECOVERY_REQUIRED`; migration failure → `RECOVERY_REQUIRED`; unsupported database / unknown state → fail closed. |
| Orphan/reference deletion lifecycle | YES | YES | YES | PARTIAL | TESTED / CI VERIFIED | Evidence lifecycle exists per §5 with amendments A-1…A-4; missing-reference and parent-delete behaviour beyond what the named tests assert is still not device-verified. |
| Factory-reset key destruction (§5.7, A-2/A-3) | YES | YES | YES | YES | DEVICE VERIFIED | `EvidenceKeyResetInstrumentedTest` — explicit, auditable, removal precedes verification, failure tombstoned not success. |
| Temporary-file cleanup and atomic replacement intent | YES | YES JVM | YES | NOT RUN | TESTED (JVM) | Deterministic single-writer JVM tests pass in CI. Concurrent writer, crash and filesystem-fallback behaviour remain **NOT RUN** on device. |
| Symlink evidence payload rejection | YES | YES JVM | YES | NOT RUN | TESTED (JVM) | `deleteIfExists` behaviour on non-empty directories is a known device-side caveat; no instrumented symlink test exists. |
| Room child-row collision is fail-closed | YES | PARTIAL | YES | NOT RUN | PARTIAL | No instrumented collision/fault injection test exists. |
| Backup and device-transfer exclusions | YES | YES STATIC | YES | NOT RUN | STATIC + CI VERIFIED | Manifest/XML and the enforced G4-B review gate pass. Actual Android backup/restore behaviour on a real device is **NOT RUN** (Q-D17). |
| `noBackupFilesDir` evidence boundary | YES | YES | YES | PARTIAL | DEVICE VERIFIED (partly) | `StorageWiringGateOffInstrumentedTest` proves the default-off path creates no database, no Keystore alias, and (since Batch B) no key-lifecycle record on the device. |
| G4-B review script is enforced by CI | YES | YES FIXTURE | YES | NOT REQUIRED | CLOSED | Runs on every `main` push and fails the build on regression. |
| Storage wiring gate (§15) | YES | YES | YES | YES | DEVICE VERIFIED, **CLOSED AS INTENDED** | `StorageRuntimeApproval.approved == false` is the correct state for a shell that has no producer. It flips only in the same verified batch as the first real detector (protocol §11, §14). |
| Connected Android instrumented tests | YES | **EXECUTED** | COMPILE ONLY in CI | **YES** | DEVICE VERIFIED | Superseded row: earlier revisions said "no authorized device". 40 instrumented tests across 9 classes have been executed on RMX3910 (Batch B, APKs from CI `36612265938`). CI still **never runs** them, so device evidence is manual and is not reproducible from a run id. |
| Production wiring into `ShieldraApp` | YES (behind gate) | YES | YES | YES | IMPLEMENTED, DELIBERATELY INACTIVE | See the row above: the gate is the product decision, not a missing integration. |
| First real detector (Panic) | **NO** | NO | NO | NO | **NOT IMPLEMENTED** | The pure rule exists (`DefaultProtectionStateEngine` maps `GuardKind.PANIC` → confirmed `CRITICAL`, `ProtectionStateEngine.kt:32`), but neither that class nor `createConfirmedEvent` has **any caller in `app/src/main`** — verified by grep, so no runtime path turns a user action into a `SecurityEvent`. Batch C. |

## Still genuinely open (do not read the DEVICE VERIFIED rows as "G4 done")

1. ~~**Rotation trigger / orchestration**~~ — **CLOSED by Batch B** (`22603d2`, CI
   `36612265938`, device `OK (5 tests)`). It stays *inactive* until Batch D flips the §15 gate.
2. ~~**Old-key retirement**~~ — **CLOSED by Batch B as a state transition.** Destruction of a
   retired key still needs an owner answer (Q-C8) and is deliberately not implemented.
   Related residue: `rotateEvidenceIfNeeded(manual = true)` is device-proven but has no UI
   affordance, so a manual rotation cannot yet be requested by the owner.
3. **Independent security review (G6)** — nobody has signed the threat model
   (`docs/security/g4-threat-model.md` is still `PROPOSED — REQUIRES SECURITY/PRIVACY REVIEW`).
   No amount of green CI substitutes for it.
4. **Single-device evidence** — every device row above comes from one phone, and one phone that
   lacks StrongBox. Samsung/Xiaomi/Google background and Keystore behaviour is unmeasured
   (Q-D14).
5. **Backup/restore, reinstall, downgrade on a real device** — `NOT RUN` (Q-D17).
6. **Concurrency / crash / filesystem-fallback** — `NOT RUN` on device.

## Gate conclusion

G4-B is **IMPLEMENTED AND SUBSTANTIALLY DEVICE-VERIFIED**, and after protocol Batch B the
key-lifecycle trigger (§9) and the retirement state (§10) are part of that verified set.
G4 as a whole remains **OPEN — NOT PRODUCTION APPROVED**, because an independent security
review, the threat-model sign-off, and a multi-device matrix are still missing, because the
app still has no detection at all, and because rotation itself stays inactive behind the
§15 gate. Per the owner protocol §6, "G4 overall is NOT automatically
production-approved"; per §11, `StorageRuntimeApproval` must not flip until the Panic producer
exists and is device-verified in the same batch.

## Revision note (2026-09-29)

This file was written when the repository had no device access and no AAD decision, and it
was never brought forward. Rows corrected here, each against a named instrumented test that
actually exists in `app/src/androidTest`:

- `Connected Android instrumented tests: BLOCKED — no authorized AVD, emulator, or device is
  available` → **false today**; 34 tests in 8 classes have run on RMX3910.
- `Android Keystore … BLOCKED` → device-verified, with the explicit no-StrongBox caveat.
- `AAD binding: NO … G4-A decision is not approved` → **false**; AAD-v1 is approved,
  implemented, and device-tested including the negative cases.
- `Key ownership / rotation: NO` → algorithm implemented and device-verified; the **trigger**
  is the real gap and is recorded as such rather than folded into a "rotation done" claim.
- `Room migration: NO PRODUCTION MIGRATIONS / BLOCKED` → superseded; migrations and recovery
  are implemented and device-tested.
- `Corruption classification: BLOCKED` → superseded; §6 failure semantics are implemented,
  tested, and enforced by the CI review gate.
- `Deferred work` items 1–7 → removed; they were all completed and this file simply went stale.
  Item 8 (guard adapters) is still true and now names Panic as Batch C.
