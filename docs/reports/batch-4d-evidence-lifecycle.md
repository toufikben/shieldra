# Batch 4d — Evidence lifecycle (§5.4 retention, §5.5 export, §5.7 factory reset)

## BATCH
4d-evidence-lifecycle (storage slice): the centralized retention policy layer,
the export-disabled policy, and the confirmed factory reset, implementing owner
amendments A-1, A-2 and A-3 recorded in decisions §17.

## SCOPE
- §5.4 (A-1): `EvidenceRetentionPolicy` as the single retention source of truth;
  quarantine window unified to 30 days (the 7-day ceiling is revoked).
- §5.5: `EvidenceExportPolicy` as the single export source of truth — disabled
  by default, permitted only when all six mandatory gates are satisfied.
- §5.7 (A-2): confirmed factory reset deletes the Keystore evidence key
  irreversibly (the only permitted Keystore-key deletion in Shieldra).
- §5.7 (A-3): removal precedes verification; a failed cleanup is tombstoned,
  never reported as success.
- Static enforcement of the above in `g4b-review-check.sh`.

Out of scope (already implemented in earlier batches, referenced not rewritten):
§5.1 UNAVAILABLE, §5.2 QUARANTINE, §5.6 evidence-file deletion live in
`EncryptedEvidenceFileStore`; §5.3 event metadata deletion lives at the Room
repository. This batch closes the parts of §5 that were still open and pins them
to the owner amendments.

## FILES CHANGED
- app/src/main/kotlin/com/shieldra/storage/EvidenceRetentionPolicy.kt (policy layer)
- app/src/main/kotlin/com/shieldra/storage/EvidenceExportPolicy.kt (new)
- app/src/main/kotlin/com/shieldra/storage/FactoryReset.kt (confirmed reset orchestration)
- app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt (+deleteEvidenceKeyIrreversibly)
- app/src/test/kotlin/com/shieldra/storage/EvidenceRetentionPolicyTest.kt (12 tests)
- app/src/test/kotlin/com/shieldra/storage/FactoryResetTest.kt (6 tests)
- app/src/test/kotlin/com/shieldra/storage/EvidenceExportPolicyTest.kt (5 tests)
- .github/scripts/g4b-review-check.sh (§5.4/§5.5/§5.7 gates)

## DECISIONS ENFORCED
§5.4, §5.5, §5.7, §14 (amendment log in §17: A-1/A-2/A-3), §2.5 (no software
fallback — reset only deletes the Keystore key, never substitutes one),
§4.3 (documented exception: retention-on-failure applies to migration, not to
an explicit confirmed reset).

## TESTS
CI `testDebugUnitTest`: 134 tests, 134 passed, 0 failures, 0 errors, 0 skipped
(run 36494249786). Of these, this batch contributes 23 new JVM tests:
- EvidenceRetentionPolicyTest — 12 (boundary at 30/30/90 days, backwards clock
  never expires, negative inputs rejected, no permanent retention, custom layer
  can change a window without touching call sites)
- FactoryResetTest — 6 (completes with both aliases deleted, idempotent,
  failure tombstoned and never success yet key still deleted per A-3, refuses
  without confirmation, never runs from the constructor alone, empty alias list
  rejected)
- EvidenceExportPolicyTest — 5 (disabled by default, enabling alone refuses,
  dropping any single gate refuses, all six gates is the only permitted state,
  gate set matches the six §5.5 bullets)

## ANDROID TESTS
NOT RUN on the device for this batch. The retention, export and reset logic is
pure JVM (injected `keyDeletion`, file/temp-dir based) and needs no Android
runtime; `assembleDebugAndroidTest` still compiles and packages the instrumented
APK in CI. The real on-device Keystore deletion during a physical reset
(`deleteEvidenceKeyIrreversibly`) is NOT yet exercised by an instrumented test —
this is the remaining device gap for §5.7 and is carried as a blocker below.

## BUILD
CI BUILD SUCCESSFUL — `clean test lint assembleDebug assembleDebugAndroidTest`
all pass (run 36494249786).

## STATIC AUDIT
`g4b-review-check.sh` PASS in CI and locally. New gates added:
- §5.4: retention layer exists and quarantine window is 30 days (A-1).
- §5.7: reset requires an explicit destructive confirmation and the evidence-key
  deletion entrypoint exists (A-2).
- §5.5: export policy exists, `enabled` defaults to false, and no evidence
  egress API (ACTION_SEND / FileProvider / ClipboardManager / Retrofit / OkHttp
  / MultipartBody / UploadManager) is present in the storage package.

## SECURITY AUDIT
- Fail closed: an unconfirmed reset and an empty alias list both return Refused
  and touch nothing; export returns not-allowed unless every gate is satisfied.
- A-3 verified: on cleanup failure the key is still deleted (removal precedes
  verification) but the result is Tombstoned, so "reset succeeded" is never
  claimed on a partial wipe.
- No plaintext/export path exists; export is structurally disabled, not merely
  hidden.

## SELF-ADVERSARIAL FINDINGS
1. First CI attempt (this batch's tests) reported only 128/129 passing because
   `metadataStore()` returned a directory with the DB inside it; a non-empty
   directory is not removable via `Files.deleteIfExists`, so the reset correctly
   tombstoned rather than completing. This was a TEST modeling error, not a
   production bug — the production behavior (refuse to claim success on an
   incomplete wipe) is exactly A-3 and is now asserted directly. Fixed by
   pointing the metadata target at the DB file.
2. Three reset tests omitted `confirmed = true` and were silently returning
   Refused, so the Completed/Tombstoned assertions never ran. A green-looking
   happy path was in fact testing the refusal branch. Fixed.
3. The tombstone test originally pointed at a file whose parent directory did
   not exist — such a file reports `exists() == false`, so it produced no real
   failure and the test would have "passed" for the wrong reason. Replaced with
   a genuinely unreachable external file.
4. `kotlin.test` argument order (message LAST) differs from JUnit and broke the
   first CI run of these suites; corrected in an earlier commit in the lineage.

## COMMIT
7e3fef0 (fixes in this lineage: 4cfe115 → 9d9c4a2 → 7896ee0 → b4f7833 → 7e3fef0)

## REMOTE
toufikben/shieldra — main

## CI
Run 36494249786 — SUCCESS. 134 JVM tests, lint, both APKs, all review scripts.

## ROADMAP
4d-evidence-lifecycle updated from BLOCKED to IMPLEMENTED (CI GREEN) for the
§5.4/§5.5/§5.7 storage slice under owner amendments A-1/A-2/A-3.

## STATUS
Storage slice of §5 CLOSED at the CI/JVM level. G4 (device/security sign-off)
remains OPEN overall.

## BLOCKERS
- Device execution of a real factory reset that irreversibly deletes the on-
  device Keystore evidence key (`deleteEvidenceKeyIrreversibly`) is NOT yet
  covered by an instrumented test. Needs an androidTest + RMX3910 run.
- Overall G4 acceptance and the §15 production gate remain open.

## NEXT BATCH
Either (a) an instrumented factory-reset/Keystore-key-deletion test executed on
the RMX3910 to close the §5.7 device gap, or (b) proceed to §6 security failure
semantics enforcement.
