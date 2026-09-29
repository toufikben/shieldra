# Batch report — 4d-failure-semantics (decision §6)

## BATCH
4d-failure-semantics — enforce the §6 security failure semantics, including the
owner's §6-vs-A-2 reconciliation recorded as amendment A-4.

## SCOPE
Make the evidence read path distinguish a **missing Keystore key**
(RECOVERY_REQUIRED — operator must act) from **corruption / AAD-mismatch /
missing-file** (UNAVAILABLE — permanently unreadable), without ever laundering
one into the other. No new security policy was invented; this encodes decisions
§6 and the owner's "استرجاع عند غياب غير متعمّد" answer.

## FILES CHANGED
- `app/src/main/kotlin/com/shieldra/storage/EncryptedEvidenceFileStore.kt`
  - added `EvidenceKeyRecoveryRequiredException` (IllegalStateException, NOT a
    subtype of `EvidenceUnavailableException`);
  - `read()` now rethrows a recovery-required signal before the generic catch;
  - `EvidenceCipher.decrypt` KDoc documents the new exception.
- `app/src/main/kotlin/com/shieldra/storage/KeystoreEvidenceCipherWithAad.kt`
  - `decrypt()` catches `KeyMaterialUnavailableException` before the generic
    catch and raises `EvidenceKeyRecoveryRequiredException`; AEAD/GCM and other
    crypto failures still map to `EvidenceUnavailableException`.
- `app/src/test/kotlin/com/shieldra/storage/EvidenceAccessFailureSemanticsTest.kt`
  (NEW) — 5 JVM tests + 3 test doubles.
- `app/src/androidTest/kotlin/com/shieldra/storage/EvidenceKeyRotationInstrumentedTest.kt`
  — warm the v2 key so the pre-rotation cross-key assertion proves wrong-key
  rejection (unavailable) rather than a missing key (recovery-required).
- `.github/scripts/g4b-review-check.sh` — new §6/A-4 static gate (exception
  exists, cipher raises it, store rethrows it before the generic catch).
- `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` — amendment A-4 in §17.
- `docs/ROADMAP.md` — 4d-failure-semantics row CLOSED; next-batch section.

Note: the §6 classifier (`SecurityFailureSemantics`) and its 12 JVM tests landed
earlier (commit `3d5a48d`, CI green run `36498029431`). This batch wires the
**runtime read path** to that table.

## DECISIONS ENFORCED
- §6: every failure outcome is blocking; no silent continuation.
- §2.5: no software-key fallback (missing key never opens a fallback path).
- A-2: a confirmed factory reset deletes the evidence files too, so post-reset
  reads stay UNAVAILABLE (permanently unreadable) — not recovery.
- A-4 (new): a non-deliberate missing key on a read = RECOVERY_REQUIRED.

## TESTS
151 JVM tests green in CI (was 134 before the §6/A-4 work; +12 classifier +5
read-path). The new `EvidenceAccessFailureSemanticsTest` asserts: missing key on
decrypt → recovery-required; integrity failure → unavailable; the store does not
launder a recovery-required read; a missing file is still unavailable; and the
MISSING_EVIDENCE→UNAVAILABLE vs MISSING_KEY→RECOVERY_REQUIRED table.

## ANDROID TESTS
Full instrumented suite **`OK (26 tests)`** on the owner RMX3910, APKs from the
same green run. The rotation test regression (missing v2 key on a fresh install
correctly raised recovery-required) was fixed by a warm-up encrypt so the
assertion tests distinct-key rejection.

## BUILD
`assembleDebug` and `assembleDebugAndroidTest` both built in CI (run
`36521881590`).

## STATIC AUDIT
`g4b-review-check.sh` PASS in CI — all existing gates plus the new §6/A-4
read-path gate (presence + catch-ordering in `read()`).

## SECURITY AUDIT
Read path fail-closed on every branch; recovery-required is a distinct,
non-launderable signal; no egress and no software fallback introduced.

## SELF-ADVERSARIAL FINDINGS
1. First test version passed `byteArrayOf(1,2,3)`, which `EncryptedPayload`
   rejects before the fake encryptor runs — the integrity test passed for the
   wrong reason and the missing-key test failed. Fixed with a valid serialized
   payload (`f62d28d`).
2. Device run exposed a genuine regression: the rotation test relied on the old
   laundering. Correctly re-scoped the test to create the v2 key first
   (`400194a`); the production classification is correct and left intact.

## COMMIT
`2c35ba8` (enforce §6/A-4) → `f62d28d` (test payload) → `400194a` (rotation test
warm-up), lineage on `main`.

## REMOTE
`toufikben/shieldra` @ `main`, pushed to `400194a`.

## CI
"Group A diagnostics": `36519322364` failed (1 test) → `36520224847` SUCCESS →
`36521881590` SUCCESS (final, commit `400194a`).

## ROADMAP
`4d-failure-semantics` CLOSED (CI + DEVICE).

## STATUS
CLOSED.

## BLOCKERS
None for §6. Remaining roadmap items (G4 sign-off, §15 production gate, phase 5,
phase 6, phase 7) are owner-decision/policy gates requiring explicit approval
before new design work — not unblocked code tasks.

## NEXT BATCH
Present the next-phase plan (G4 sign-off / §15 production wiring) for owner
approval; do not start new spec/code without an explicit yes.
