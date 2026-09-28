# Batch 4d-key-rotation — Rotation algorithm, resumable state, Room schema v2, and StrongBox detection correction

**Date:** 2026-09-28
**Depends on:** `4d-aad-binding` (closed, commit `6a13d59`)
**Status:** **IMPLEMENTED — CI VERIFICATION PENDING; DEVICE/KEYSTORE RUNTIME ACCEPTANCE STILL OPEN**

## Batch

`4d-key-rotation` plus two corrections found by the pre-batch audit:
the StrongBox runtime-detection defect (decision §2.3) and the missing
Room migration instrumented test (decision §4.6).

## Scope

- Per-evidence key rotation algorithm (decision §3.3).
- Resumable rotation state and stale-temp handling (decision §3.4).
- The complete decision §3.5 rotation test matrix as JVM unit tests.
- Room schema version 2 with `key_version` / `aad_version` columns and an
  explicit non-destructive `Migration(1, 2)` (decisions §3.2, §4.5).
- Instrumented migration test that builds a real v1 database from the
  exported schema and validates the migration against the exported v2
  schema (decision §4.6).
- StrongBox capability detection corrected to query the dedicated system
  feature at runtime instead of inferring availability from the SDK level
  (decision §2.3).

No Android Keystore runtime claim is made in this batch. No production
wiring into the application graph was added.

## Files changed

Production:

- `app/src/main/kotlin/com/shieldra/storage/EvidenceKeyRotator.kt` (new) —
  rotation algorithm, `RotationItemState`, `RotationResult`, the four
  rotation gateways, `FileSystemRotationGateway`, `RotationAadVersions`,
  `KeystoreRotationCipherFactory`.
- `app/src/main/kotlin/com/shieldra/storage/room/RoomRotationMetadata.kt` (new) —
  Room-backed `RotationMetadataGateway`; a mark must update exactly one row.
- `app/src/main/kotlin/com/shieldra/storage/room/ShieldraDatabaseMigrations.kt` (new) —
  explicit `MIGRATION_1_2`; no destructive fallback exists.
- `app/src/main/kotlin/com/shieldra/storage/EvidenceKeyPolicy.kt` —
  v1/v2 key versions and aliases, AAD-v1 label, 90-day rotation trigger,
  fail-closed `aliasForKeyVersion`, `rotationDue`.
- `app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt` —
  optional application context; `isStrongBoxAvailable()` now queries
  `PackageManager.FEATURE_STRONGBOX_KEYSTORE` and fails closed without a
  context.
- `app/src/main/kotlin/com/shieldra/storage/LocalStorageFactory.kt` —
  `.addMigrations(*ShieldraDatabaseMigrations.all)` and a context-bearing
  default encryptor.
- `app/src/main/kotlin/com/shieldra/storage/StorageSchema.kt`,
  `room/ShieldraDatabase.kt`, `room/StorageEntities.kt`, `room/StorageDaos.kt` —
  schema version 2, the two rotation columns, `findById`, `updateKeyVersion`.
- `app/src/main/kotlin/com/shieldra/storage/room/RoomEventRepository.kt` —
  new evidence rows are written with the policy constants
  `EvidenceKeyPolicy.KEY_VERSION_V1` / `AAD_VERSION_V1` instead of literals.
- `app/build.gradle.kts` — `androidTest` assets include `app/schemas` so
  `MigrationTestHelper` can read the exported schemas.
- `app/schemas/com.shieldra.storage.room.ShieldraDatabase/2.json` — exported
  v2 schema.

Tests:

- `app/src/test/kotlin/com/shieldra/storage/EvidenceKeyRotatorTest.kt` (new) —
  14 tests over in-memory fakes.
- `app/src/androidTest/kotlin/com/shieldra/storage/room/ShieldraDatabaseMigrationInstrumentedTest.kt` (new).
- `app/src/androidTest/kotlin/com/shieldra/storage/RealAesGcmAadTest.kt` —
  the placeholder StrongBox assertion (it asserted `assertTrue(true)`) was
  replaced with a real comparison against `PackageManager`.
- `app/src/test/kotlin/com/shieldra/storage/EvidenceKeyPolicyTest.kt`,
  `StorageSchemaTest.kt` — rotation policy and schema-version expectations.

## Decisions enforced

- §3.1 rotation trigger 90 days or manual; target alias `shieldra_evidence_v2`.
- §3.2 every evidence row carries `key_version` and `aad_version`.
- §3.3 read → decrypt old → verify → encrypt new → write temp → verify temp →
  atomic commit → verify replacement → mark; the old key is never deleted.
- §3.4 resumability: completed items are skipped; a valid leftover temp is
  committed; an invalid temp is discarded and the item is redone;
  committed-but-unmarked evidence is adopted, never re-encrypted; corruption,
  missing key, and missing metadata fail closed with the evidence untouched.
- §3.5 the full test matrix below.
- §4.3/§4.5 explicit forward migration only; no `fallbackToDestructiveMigration`.
- §4.6 the migration is exercised against the real exported schemas.
- §2.3 StrongBox preferred, never required, and detected at runtime from the
  actual hardware feature.

## Decision §3.5 test matrix

| Required case | Test |
|---|---|
| Normal rotation | `normal rotation migrates evidence and marks metadata (decision 3-5)` |
| Interruption before encryption | `interruption before encryption resumes and completes cleanly (decision 3-5)` |
| Interruption after encryption | `interruption after encryption commits leftover valid temp (decision 3-5)` |
| Interruption before rename | `interruption before rename commits leftover verified temp (decision 3-5)` |
| Interruption after rename | `interruption after rename adopts committed file and marks metadata (decision 3-5)` |
| Corrupted old evidence | `corrupted old evidence fails closed with evidence untouched (decision 3-5)` |
| Corrupted new evidence | `corrupted new temp is discarded and rotation redone (decision 3-5)` |
| Duplicate retry | `duplicate retry returns AlreadyMigrated without re-touching evidence (decision 3-5)` |
| Already migrated | `already migrated evidence is skipped (decision 3-5)` |
| Missing old key | `missing old key fails closed before touching evidence (decision 3-5)` |
| Missing metadata | `missing metadata fails closed (decision 3-5)` |
| Mixed v1/v2 evidence | `mixed v1 and v2 evidence rotate independently (decision 3-5)` |

Two additional guards beyond the required list:
`unexpected stored versions fail closed` and
`commit succeeded but mark failed is adoptable on retry`.

## Tests

- 14 new JVM rotation tests plus the updated policy and schema tests:
  **WRITTEN — EXECUTION NOT COMPLETED LOCALLY.** A local
  `:app:testDebugUnitTest` run was started and interrupted; the owner
  directed verification to GitHub Actions. CI runs `test`, which executes
  this suite. No pass claim is made until the CI run is green.
- Previous local evidence in this working tree (before the rotation tests
  existed): full `testDebugUnitTest` BUILD SUCCESSFUL with the rotation
  production code in place. That run did not include these new tests.

## Android tests

- New `ShieldraDatabaseMigrationInstrumentedTest` (2 tests) and the corrected
  StrongBox test in `RealAesGcmAadTest`: **NOT RUN**. CI compiles them with
  `assembleDebugAndroidTest`; execution needs the attached device.
- Earlier device evidence on the owner Realme RMX3910 (2026-09-27):
  7/7 Room instrumented tests and 11/11 real AES-GCM/AAD tests passed. That
  evidence predates this batch and does not cover the new tests.
- Device fact measured in this batch: `adb shell pm has-feature
  android.hardware.strongbox_keystore` → `false` on the RMX3910. The previous
  implementation reported "available" on that device because it only checked
  `SDK_INT >= P`; the corrected code and test now agree with the hardware.

## Build

**NOT RUN locally for this final state.** CI runs
`clean test lint assembleDebug assembleDebugAndroidTest`.

## Static audit

CI enforces the existing gates: `.github/scripts/group-a-diagnostics.sh`,
`group-c-contract-check.sh`, `g4b-review-check.sh` (extended in this batch to
require the exported v2 schema and the explicit `addMigrations` wiring), and
`phase3-review-check.sh`. Locally only `git diff --check` style review of the
new files was performed by reading them.

## Security audit

- Temporary rotation files are never valid evidence: they use a distinct
  `.<id>.rotate.tmp` name and are verified with the new key before commit.
- No rotation path deletes a Keystore key; `KeystoreRotationCipherFactory`
  only resolves aliases.
- Unknown key versions and unsupported AAD versions fail closed
  (`aliasForKeyVersion`, `RotationAadVersions`).
- `FileSystemRotationGateway` validates the evidence id against a bounded
  path-safe pattern, rejects symlinks for committed files, fsyncs before the
  atomic rename, and refuses to fall back silently when atomic move is
  unsupported.
- No software key fallback path exists; no destructive migration exists.
- The Room mark asserts exactly one affected row, so a missing row cannot be
  reported as migrated.

## Self-adversarial findings

1. **Placeholder test removed.** The prior device test asserted
   `assertTrue(true)`; it passed regardless of behavior and hid the StrongBox
   defect. Replaced with a real equality check against `PackageManager`.
2. **StrongBox over-claim corrected.** Runtime detection now matches the
   measured device capability; no batch may claim hardware-backed storage on
   devices such as the RMX3910.
3. **Test-first deviation recorded.** The rotation production code was
   written before its §3.5 tests existed in this working tree. The suite now
   pins the behavior, but the process deviation is recorded rather than
   glossed over.
4. **Interruption-point equivalence.** "After encryption" and "before rename"
   are the same observable on-disk state; both required tests converge on the
   resumed-commit path. This is stated in the tests instead of pretending the
   cases differ.
5. **Missing predecessor report.** `4d-aad-binding` was closed without a
   §16-format batch report; this report and the roadmap row carry the
   missing audit trail for its StrongBox item.

## Roadmap

- `4d-key-rotation`: implementation and tests complete in this commit; CI
  verification pending.
- `4d-migration-recovery`: partially advanced — schema v2, the explicit
  `Migration(1, 2)`, and its instrumented test now exist. Downgrade,
  unsupported-version, and migration-failure → `RecoveryRequired` behavior
  remains open.
- G4 remains open; Phase 4 remains open.

## Blockers

- Device execution of the new instrumented tests (owner device available;
  runs were not performed for this batch on the owner's instruction to use
  CI).
- Keystore runtime rotation behavior on a real device (needs the instrumented
  rotation run, not just the fakes).
- Security acceptance (G4) remains unapproved.

## Next batch

`4d-migration-recovery` — finish decision §4 scope: unsupported schema
version, downgrade, and migration-failure paths must produce
`RecoveryRequired` with tests, then run the accumulated instrumented suites on
the owner device.
