# Batch 4d (B) — Rotation trigger and old-key retirement (§9/§10)

## BATCH
`4d-rotation-orchestration-and-retirement` — the second batch of the owner's final
master execution protocol. It closes the two genuine G4 gaps that Batch A's trace
found: rotation had an algorithm but **no trigger**, and there was **no retirement
state** for a superseded key generation.

## SCOPE
- **§9 — the trigger that actually runs.** `EvidenceRotationOrchestrator` decides
  *whether* the set is due (approved interval or an explicit manual request), which
  generation succeeds the active one, which evidence entries are eligible, and when
  the run may retire the old generation. It drives the already-device-verified
  `EvidenceKeyRotator` entry by entry.
- **§10 — retirement as a state, never as a deletion.** `FileBackedKeyLifecycle` is
  the durable record of the active key generation, the retired ones, and the 90-day
  clock. A generation is marked retired **only** after a re-scan proves zero
  remaining references to it. Nothing in this batch can destroy a key.
- **Production wiring.** `LocalShieldraStorage.rotateEvidenceIfNeeded(manual)` is
  the composition point; `ShieldraApp.onCreate` runs the interval check on a background
  thread behind the existing §15 install gate and keeps both the report and any failure.
- **Static enforcement** of the ordering, the no-destruction rule, and the trigger's
  existence in `.github/scripts/g4b-review-check.sh`.

Out of scope, deliberately: the **destruction** of a retired key (owner decision Q-C8,
still unanswered), a UI affordance for the manual trigger, any key version beyond
v1→v2 (no v3 alias is approved, decisions §3.1), and flipping
`StorageRuntimeApproval.approved` (Batch D).

## FILES CHANGED
- app/src/main/kotlin/com/shieldra/storage/EvidenceKeyLifecycle.kt (new — trigger + retirement decision point)
- app/src/main/kotlin/com/shieldra/storage/FileBackedKeyLifecycle.kt (new — durable retirement record)
- app/src/main/kotlin/com/shieldra/storage/room/RoomRotationMetadata.kt (`RotationScopeGateway.all()` — whole-set eligibility scan)
- app/src/main/kotlin/com/shieldra/storage/LocalStorageFactory.kt (rotation composition + `rotateEvidenceIfNeeded`)
- app/src/main/kotlin/com/shieldra/app/ShieldraApp.kt (§9 interval trigger behind the §15 gate)
- app/src/test/kotlin/com/shieldra/storage/EvidenceRotationOrchestratorTest.kt (new, 12 tests)
- app/src/test/kotlin/com/shieldra/storage/FileBackedKeyLifecycleTest.kt (new, 11 tests)
- app/src/androidTest/kotlin/com/shieldra/storage/EvidenceKeyLifecycleInstrumentedTest.kt (new, 5 on-device tests)
- app/src/androidTest/kotlin/com/shieldra/storage/StorageWiringGateOffInstrumentedTest.kt (+1 boundary pin: gate-off writes no lifecycle record)
- .github/scripts/g4b-review-check.sh (§9/§10 gates)

## DECISIONS ENFORCED
§9 (a real trigger, interval or explicit manual), §10 (identify all eligible → migrate
all → verify all → zero remaining references → mark RETIRED → only then *consider*
destruction), decisions §3.1–§3.4 (approved key versions and rotation semantics),
§2.5 (no software-key fallback), §4.3 (no destructive path), §6 (a damaged record fails
closed instead of assuming a version), §14 (no silent policy change — retirement is a
state transition, the destruction question stays open), §15 (the trigger is reachable
only through the owner-flipped install gate).

## TESTS
CI `testDebugUnitTest` on run `36612265938`: **194 tests, 194 passed, 0 failures,
0 errors, 0 skipped**. This batch contributes 23 new JVM tests.

- `EvidenceRotationOrchestratorTest` — 12: not-due does nothing; an interval-elapsed run
  rotates every eligible entry; a manual run rotates even when not due; a run with no
  eligible entry reports `NO_ELIGIBLE_EVIDENCE` and retires nothing; a partially failed
  run reports `PARTIALLY_MIGRATED` and keeps the old generation active; **retirement is
  refused while any reference still points at the old version**; a failing re-scan blocks
  retirement; a failing lifecycle write blocks retirement; an unknown successor stops the
  run instead of inventing a key version; a scan that throws is reported, not swallowed;
  entries already under the new version are counted, not re-encrypted; the 90-day boundary.
- `FileBackedKeyLifecycleTest` — 11: first use opens the installed generation at the
  creation time (the clock starts at key creation, not at the epoch); a relaunch does not
  reset the baseline; completing a rotation is durable and marks the old version retired;
  only the installed generation has an approved successor; retiring a non-active version
  is rejected with the record untouched; a negative completion timestamp is rejected; a
  damaged record fails closed; an unknown schema version fails closed; a completed write
  leaves no partial file beside the record; the record lives outside the encrypted evidence
  directory; and the class exposes no key-destruction surface (reflection over declared
  methods).

## ANDROID TESTS
RUN on the owner device RMX3910 (`IR7X5DJ7KZGEHMS8`, Android 16 / API 36, **no StrongBox
on this device**) with the app and androidTest APKs from the **same** CI run `36612265938`
(commit `22603d2`), after uninstalling both packages:

- `EvidenceKeyLifecycleInstrumentedTest` — **`OK (5 tests)`**:
  - the eligibility scan lists real Room rows under both key versions;
  - the lifecycle record is durable across a reopen on the real `noBackupFilesDir`;
  - a damaged record fails closed on the device and is not rewritten over itself;
  - **production wiring rotates real Keystore evidence and retires the old generation**:
    evidence is seeded under the real `shieldra_evidence_v1` key, a manual run through
    `LocalShieldraStorage.rotateEvidenceIfNeeded(manual = true)` reports `ROTATED`, Room
    records v2 only after the file verified, the durable record shows v2 active with v1
    retired, the rotated bytes decrypt under v2 to the original plaintext, v1 **rejects**
    them with `EvidenceUnavailableException` (re-encryption, not a metadata relabel), and
    no `.tmp` survives the run;
  - a not-due run touches nothing: same committed bytes, Room still v1, record still v1.
- Full instrumented suite — **`OK (40 tests)`** (was 35; +5 new). `StorageWiringGateOffInstrumentedTest`
  is now 4 tests and passes, including the new pin that the gate-off path writes no
  lifecycle record.

Honest note: that gate-off pin was added **alongside** the GREEN commit, as a boundary
assertion, not as a RED-first behavior proof. The five lifecycle tests were preceded by a
real RED (`b71877e`, CI `36604518608` failed on the unresolved `FileBackedKeyLifecycle`).

## BUILD
CI `clean test lint assembleDebug assembleDebugAndroidTest` — **SUCCESS** (run `36612265938`),
including `assembleDebugAndroidTest`, which is the only task that compiles the instrumented
source set.

## STATIC AUDIT
`g4b-review-check.sh` extends the G4-B gate with, all **PASS** locally and on CI:
- the four rotation sources exist;
- `grep -Eq 'deleteKey|destroyKey|removeKey|deleteAlias|deleteEntries|unloadKey'` over the
  rotation/lifecycle sources must find nothing (§10, Q-C8 held);
- `EvidenceKeyPolicy.rotationDue` and `RotationTrigger.MANUAL` are both referenced (the
  trigger is the approved interval or an explicit request — nothing else);
- **line-order gate**: the second `scope.all()` re-scan must appear *before*
  `lifecycle.completeRotation` in the orchestrator — retirement is proven to follow a
  surviving-reference check, mechanically, not by review comment;
- the record's schema line is checked on read (`lines.firstOrNull() == SCHEMA`);
- `rotateEvidenceIfNeeded` and `checkEvidenceRotation` exist, and `ShieldraApp` keeps
  `lastRotationFailure` instead of swallowing it.

GitHub runners have no `rg`, so every gate uses `grep`.

## SECURITY AUDIT
Self-review of the new surface (not a substitute for the independent review G4 still needs):
- The durable record contains **no key material and no plaintext identifiers** — only
  version labels (`v1`, `v2`), and two epoch millis. Leaking it reveals nothing decryptable.
- It is written as temp + `fd.sync()` + atomic move, and is read only after a schema and
  completeness check; a truncated or unknown-schema record throws instead of defaulting to
  a generation, so a rollback attempt cannot silently re-open rotation on an assumed version.
- It lives in the `noBackupFilesDir` **root**, outside the encrypted evidence directory,
  because `EncryptedEvidenceFileStore.reconcile()` deletes every `*.tmp` inside that
  directory — placing it there would have invited periodic record loss.
- Residual risk, stated rather than hidden: the record is **not authenticated or
  encrypted at rest**. An attacker with write access to that file could rename a generation
  label. The consequence is bounded — the ciphertext is still bound to the real Keystore key
  and AAD, so a forged label yields decryption failure (`RETIREMENT_BLOCKED` /
  recovery-required per §6/A-4), never plaintext. Closing the residual (a Keystore-bound
  MAC over the record) is a candidate for the independent review, not something to invent here.
- No new permission, no new network path, no new export surface.

## SELF-ADVERSARIAL FINDINGS
1. **Instrumented compilation was unproven until CI.** `663f414` looked green locally and
   failed CI (`36605868791`) with three `assembleDebugAndroidTest` errors: `RoomRotationMetadata`
   needed its `.room` import, and two asserts used kotlin.test's message-**last** order in a
   JUnit4 source set. Fixed in `22603d2`. This is the repo's most repeated CI failure mode.
2. **The harness almost proved the opposite of the rule.** With `lastRotationEpochMs = 0`,
   "not due before 90 days" was unpassable: the interval fired on first launch. Corrected by
   starting the clock at key creation (decisions §3.1) — in the test *and* in
   `FileBackedKeyLifecycle.load()`, so production matches the rule it is being tested against.
3. **A faked clock is not a function.** An object with `operator fun invoke()` is not a
   `() -> Long`; CI caught it (`36601603916`) and the harness now exposes `val millis: () -> Long`.
4. **Record location was chosen from a real side effect,** not convenience: `reconcile()`
   sweeps `*.tmp` in the evidence directory, so a record written there would eventually be
   deleted and the 90-day clock would silently reset.
5. **The ceiling is real and enforced.** Only v1→v2 has an approved alias, so the second
   rotation of a long-lived install returns `NO_APPROVED_SUCCESSOR` and does nothing. That is
   the honest state of §9 on this repository, and it is asserted by test rather than
   papered over with an invented v3.
6. **Not finished in this batch:** `rotateEvidenceIfNeeded(manual = true)` has no UI
   affordance, and the interval path is behind `StorageRuntimeApproval.approved = false`.
   A default-installed build therefore still performs no rotation, opens no database and
   writes no lifecycle record — now pinned on the device. Batch D flips the gate.

## COMMIT
`f88bdb6` RED (trigger + retirement tests) → `f318752` GREEN (orchestrator) → `2aefc06`
harness clock fix → `b71877e` RED (durable record + device lifecycle) → `663f414` GREEN
(record, scope scan, real trigger) → `22603d2` instrumented-compilation repair.

## REMOTE
`toufikben/shieldra`, branch `main`. HEAD `22603d2`.

## CI
- `36600532162` (`f88bdb6`) — RED #1, **failure**: unresolved orchestrator symbols, the
  intended failure.
- `36601603916` (`f318752`) — GREEN #1, **failure**: the harness clock was not a
  `() -> Long`. The production sources compiled (`compileDebugKotlin`/`kspDebugKotlin`
  passed); exactly one `e:` line in the whole log.
- `36603299441` (`2aefc06`) — **success**, 183 JVM tests (171 before the batch + 12).
- `36604518608` (`b71877e`) — RED #2, **failure**: unresolved `FileBackedKeyLifecycle`.
- `36605868791` (`663f414`) — GREEN #2, **failure**: `:app:compileDebugAndroidTestKotlin`,
  three errors (JVM tests in the same run passed: 194/194).
- `36612265938` (`22603d2`) — repair, **success**: 194 JVM tests, 194 passed; lint,
  `assembleDebug`, `assembleDebugAndroidTest`, Group C boundary scan, G4-B review and
  Phase 3 review all green; `apks-36612265938` was used for the device run.

## ROADMAP
`docs/ROADMAP.md`: the two "Real remaining G4 gaps" rows (no rotation trigger; no
retirement lifecycle) are closed with the evidence above, and Batch B is marked CLOSED.
G4 stays **PARTIAL / NOT PRODUCTION-APPROVED**: what remains is the independent security
review, the threat model, an OEM matrix beyond this single device, and sign-off.
`docs/reports/g4-acceptance-matrix.md` rows 38–39 and its open-items list are updated to
say the trigger and retirement state now exist and are device-verified — with the
destruction decision still held.

## STATUS
**IMPLEMENTED, TESTED, CI VERIFIED, DEVICE VERIFIED.**
Not SECURITY REVIEWED. Not PRODUCTION APPROVED. `G4` is not accepted by this batch.

## BLOCKERS
None for this batch. Held owner decisions that bound it: **Q-C8** (what may destroy a
retired key, and when) and the unresolved **§30 vs. store-launch** monetization conflict
(ads/IAP must not be built in Batches B–J without the owner's one-line confirmation).

## NEXT BATCH
**Batch C — Panic Guard end-to-end** (protocol §12–§15): dashboard action → `GuardSignal`
→ immediate `CONFIRMED` CRITICAL event → `EventId` → `SecurityEvent` → Room → reload →
History → Detail, with repeat-activation dedup, and **no** camera, location, recipient or
evidence invention (§21–§22; the Panic response/evidence rows are still UNKNOWN in the
approved rule). Batch D flips the storage gate inside that verified batch.
