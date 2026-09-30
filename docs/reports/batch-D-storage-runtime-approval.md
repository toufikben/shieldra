# Batch D — §15 production storage install gate

## BATCH
`D-storage-runtime-approval` (protocol **Batch D**) — flip
`StorageRuntimeApproval.approved` to `true` as a visible owner commit, so the approved
build opens local storage at process start. Bound to Batch C by the owner's own answer to
Q-C2 (option 1: flip in the same controlled batch as the first genuine detector, and only
after that detector passes device verification).

## SCOPE
- **The §15 gate flip itself.** `StorageRuntimeApproval.approved = false → true`, recorded
  as **amendment A-5** in the §17 amendment log of
  `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` with its date, its replaced text,
  and its reason. Nothing else about storage policy changed.
- **Failure handling is unchanged.** Opening still goes exclusively through
  `LocalShieldraStorage.openOrRecovery`; a failed open is retained as
  `StorageInstallDecision.RecoveryRequired` state, never a crash and never a destructive
  recreate (§4.2–§4.4). The raw factory stays unreachable from the app graph.
- **The g4b gate was inverted, not deleted.** It previously enforced `approved = false`.
  It now enforces `approved = true`, so an *unrecorded* revert fails CI instead of passing
  silently — the mechanism that keeps §14's "no silent policy change" mechanically true.
- **Device-level proof that the flip is real**, not just a constant: a new instrumented
  suite exercises the running application itself.

Out of scope, deliberately: the remaining §15 work (phase 5/6/7 detectors, services, the
audit suite), the durable activation count (Q-C9), retired-key destruction (Q-C8), and the
manual-rotation UI affordance. §15 is **not** closed by this batch and **G4 is not
accepted**.

## FILES CHANGED
- app/src/main/kotlin/com/shieldra/storage/StorageRuntimeApproval.kt (`approved = true`, KDoc → A-5)
- app/src/main/kotlin/com/shieldra/app/ShieldraApp.kt (KDoc only — no logic change)
- app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDecision.kt (stale KDoc: "the shipped default" is no longer the off state)
- app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDashboardFactory.kt (stale KDoc)
- .github/scripts/g4b-review-check.sh (§15 gate inverted: enforces the owner-flipped `true`)
- docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md (amendment **A-5** row + note update)
- docs/OWNER_QUESTIONS.md (Q-A15, Q-A16, Q-C2 status)
- docs/ROADMAP.md (Batch D closed, next batch named)
- app/src/test/kotlin/com/shieldra/storage/StorageRuntimeGateTest.kt (+1 JVM gate test)
- app/src/androidTest/kotlin/com/shieldra/storage/StorageRuntimeInstallInstrumentedTest.kt (new — 6 device tests)
- app/src/androidTest/kotlin/com/shieldra/storage/StorageWiringGateOffInstrumentedTest.kt (deleted — its premise, "a default build never opens storage", is now inverted by A-5)

## DECISIONS ENFORCED
§14 (no silent policy change — the flip is a visible commit **and** a recorded amendment),
§11 (do not flip the gate alone; Batch D is bound to Batch C), §4.2–§4.4 (a failed open
keeps the database and reports `RecoveryRequired`, never a destructive fallback), §9/§10
(the rotation trigger and the retirement clock become *reachable* for the first time), and
§31 (no UI may claim `Protected`/`Active`/`Alert sent` without real state behind it).

## TESTS
CI `clean test lint assembleDebug assembleDebugAndroidTest`:

- `36743674044` (`986cb00`) — RED, **failure on the intended assertion**:
  `StorageRuntimeGateTest > the owner has flipped the production storage gate() FAILED`
  at `StorageRuntimeGateTest.kt:24`, in `:app:testDebugUnitTest`. The failure was exactly
  "the flag is still `false`" and nothing else.
- `36745562314` (`36e04c1`) — GREEN, **success**: 213 JVM tests, 213 passed, 0 failures,
  0 errors, 0 skipped; lint, `assembleDebug`, `assembleDebugAndroidTest` all green.
- `36752967234` (`28d708c`) — device-found test fix, **success** (see SELF-ADVERSARIAL
  FINDINGS 1).

This batch contributes **1 new JVM test** and **6 new instrumented tests**, and removes a
3-test instrumented class whose premise the amendment invalidated.

- `StorageRuntimeGateTest` (+1): the_owner_has_flipped_the_production_storage_gate.
  The three pre-existing pure-decision tests are unchanged, so `NotApproved` remains
  tested as a pure function even though production now takes the other branch.

## ANDROID TESTS
RUN on the owner device RMX3910 (`IR7X5DJ7KZGEHMS8`, Android 16 / API 36, **no StrongBox
on this device**), with the app and androidTest APKs from the **same** CI run, after
uninstalling **both** packages and confirming `NO_SHIELDRA` packages remained:

- `StorageRuntimeInstallInstrumentedTest` — **OK (6 tests)** (run `36745562314`):
  - `gate_is_on_by_default` — the constant is `true` in the shipped APK;
  - `the_running_application_installs_storage_through_production_wiring` — the *running*
    `ShieldraApp` reports `Installed`, not `NotApproved`;
  - `an_installed_storage_creates_the_real_database` — after one query (Room touches the
    file only on first access), `shieldra.db` exists;
  - `the_rotation_clock_record_exists_once_storage_is_installed` — the production §9 entry
    point runs and the lifecycle record appears;
  - `a_panic_press_persists_a_real_event_through_production_storage` — a press persists a
    real event through the *production* storage instance; the repeat press binds to the
    same incident and exactly one row exists;
  - `an_install_alone_creates_no_keystore_key` — the evidence aliases are compared before
    and after an install: unchanged. A key exists only once real evidence is encrypted.
- Full instrumented suite on run `36752967234` — **OK (48 tests)**, executed **twice in a
  row on the same install** (48 + 48, both OK). The second run is the case that previously
  failed; see SELF-ADVERSARIAL FINDINGS 1.

### Direct filesystem evidence read off the phone
- `/data/data/com.shieldra.app/databases/shieldra.db` (4096 B) plus an 86 KB WAL — the real
  production database, holding the persisted events.
- `/data/data/com.shieldra.app/no_backup/shieldra-key-lifecycle.bin` (107 B), whose
  contents are:
  ```
  shieldra-key-lifecycle/1
  active=v1
  retired=
  lastRotationEpochMs=1790789771505
  createdEpochMs=1790789771505
  ```
  The §9 rotation clock exists on a real device for the first time. `retired=` is empty,
  which is correct: no generation has been retired.
- `no_backup/shieldra-evidence/` — the evidence directory, present and empty, because no
  evidence has been encrypted yet.

### Visual verification (screen was unlocked this session)
Launched via the launcher, dismissed first-run onboarding ("Skip for now"), then read the
rendered UI via a `uiautomator` hierarchy dump rather than a photograph:

- Dashboard shows **`ATTENTION`**, never `Protected`.
- Every guard renders **`—`** — no `Active` guard anywhere.
- Battery is **`—`** and network is **`Offline`** — no invented reading.
- `Silent Panic` / "Trigger an emergency event" is the real, storage-backed button.

Pressing it once, then opening **History**, shows a real row: **`Security event`**,
**`7 minutes ago`**, **`Skipped`**. Opening its detail shows
**`Event ID 319647f5-1c8f-44fe-98ef-cd4cf5a5cc90`**, **`No photo available`**,
**`Location unavailable`**, and a `DELIVERY` section that claims no receipt.

So the full production loop §13 asks for — UI → signal → confirmation → `EventId` →
`SecurityEvent` → pipeline → Room → reload → History → Detail — now actually runs on the
real device, and every field it shows is real.

## BUILD
CI `clean test lint assembleDebug assembleDebugAndroidTest` — **SUCCESS** on both
`36745562314` and `36752967234`, including `assembleDebugAndroidTest`, the only task that
compiles the instrumented source set. This is what caught the new instrumented class for
the first time: the RED run aborted at `testDebugUnitTest`, so the androidTest source set
had never been compiled until the GREEN run.

## STATIC AUDIT
`g4b-review-check.sh` — all gates **PASS** locally and on CI. The §15 block changed from
"must default to false until the owner signs off" to "was flipped back to false without a
recorded owner amendment (sec 14/15, A-5)". Everything downstream of it is unchanged and
still enforced: the app consults the gate, routes through `decideStorageInstall`, opens via
`openOrRecovery`, retains `storageDecision` as state, and the raw factory is unreachable
from `com.shieldra.app` / `navigation`. Group C boundary scan and Phase 3 static review
also PASS.

GitHub runners have no `rg`, so every gate uses `grep`.

## SECURITY AUDIT
Self-review of the new surface (not a substitute for the independent review G4 still needs):
- **The flip grants no new capability that was not already built and tested.** The gate
  existed since `4d-storage-wiring-gate`; this batch changes a constant, not a policy. The
  encryptor, the AAD binding, the migration set, the rotation trigger, and the retirement
  clock are all unchanged code.
- **No silent revert is possible.** The static gate now fails if the constant is set back
  to `false`, and the JVM test fails if it is not `true`. Reverting requires a deliberate
  amendment recorded in §17 — the §14 requirement is enforced by CI, not by convention.
- **Fail-closed behavior is preserved.** `openOrRecovery` still wraps the factory, and
  `StorageRecoveryGuard` still leaves the database file on disk on failure. No
  `fallbackToDestructiveMigration` was introduced anywhere; `addMigrations` remains
  explicit-only.
- **No Keystore key is minted by installing.** Proven on device by alias comparison before
  and after an install. A key is created only when real evidence is encrypted, so a build
  with no detection cannot accumulate crypto material for nothing.
- **The database is in `noBackupFilesDir`-adjacent, backup-excluded locations.** The app
  still declares `allowBackup="false"` and the database/file exclusions in
  `data_extraction_rules.xml` are untouched.
- **Honesty boundary re-verified visually.** Storage being installed does **not** produce a
  "Protected" claim: `LockGuardDecision` maps an installed store to `StorageIdle`, which
  renders as `Attention`, and every guard stays `Disabled`. This is the §31 rule, and the
  on-device dump confirms it.

## SELF-ADVERSARIAL FINDINGS
1. **My own new device test was order-dependent, and the device run caught it.** The first
   press asserted `PanicActivation.Recorded`. But `PanicIncidentCoordinator.activate()`
   binds to any non-terminal incident already persisted, and Panic deliberately has no
   cooldown and no time window. The isolated run passed on a clean install; the **full suite
   on the same install failed** with `the first press must record a real event:
   BoundToActive(...)`. The production behavior was correct — binding is the required
   idempotency — and the *test* was wrong to assume an empty database. Fixed in `28d708c`:
   the opening press now accepts either outcome and the test still requires the repeat press
   to bind to the same incident with exactly one persisted row. Proven by running the full
   suite twice on one install: 48 OK, 48 OK.
2. **There is no JVM test for that seam.** `PanicIncidentCoordinatorTest` covers repeat
   presses within one coordinator, but not "a fresh coordinator binds to an incident left in
   the repository by a previous process". That is the exact case that bit the device test,
   and it is only reachable on real storage. It is a gap, not a bug; closing it belongs with
   the Q-C9 durability work, where the session-only tally stops being session-only.
3. **The RED run never compiled the androidTest source set.** `clean test …` aborts at
   `testDebugUnitTest`, so the RED run proved only the JVM assertion. First-time compilation
   of the new instrumented class happened in the GREEN run. That is a property of the CI task
   order, not a defect, but it means a GREEN failure could surface as a compile error rather
   than an assertion failure.
4. **A first uninstall silently failed** (`DELETE_FAILED_INTERNAL_ERROR` on
   `com.shieldra.app`) because the app process was alive. The device had to be force-stopped
   and re-uninstalled, and a leftover `shieldra.db` would have made "the install creates the
   database" assertion meaningless. Verified `NO_DATA_DIR` and then `NO_SHIELDRA` before
   installing. Worth remembering for the next device batch: uninstall failure is not a no-op.
5. **Not finished in this batch:** §15 remains open (phases 5/6/7 detectors, services, and
   the audit suite are still missing); the activation count is still session-only (Q-C9);
   retired-key destruction is still unimplemented (Q-C8); and the manual-rotation trigger
   still has no UI affordance.

## COMMIT
`986cb00` RED (JVM gate test + new device suite, old gate-off suite deleted) →
`36e04c1` GREEN (flip, KDoc, g4b inversion, amendment A-5) →
`28d708c` test fix (device-found order dependency).

## REMOTE
`toufikben/shieldra`, branch `main`. HEAD `28d708c`.

## CI
- `36743674044` (`986cb00`) — RED, **failure**: exactly
  `the owner has flipped the production storage gate` at `StorageRuntimeGateTest.kt:24`.
- `36745562314` (`36e04c1`) — GREEN, **success**: 213 tests, 213 passed; lint,
  `assembleDebug`, `assembleDebugAndroidTest`, Group C, G4-B, Phase 3 all green.
- `36752967234` (`28d708c`) — device-found test fix, **success**:
  `apks-36752967234` used for the twice-repeated device run.

## ROADMAP
`docs/ROADMAP.md`: Batch D marked **CLOSED — CI VERIFIED + DEVICE VERIFIED** in both the
dependency-ordered batch table and the "Required next batch" section, with the required
next batch moved to **Batch E — real notifications** (§16). The two paragraphs that said
the gate "stays `false` until the Panic producer is device-verified" are replaced with the
record that A-5 satisfied exactly that condition. Q-A15/Q-A16/Q-C2 updated in
`docs/OWNER_QUESTIONS.md`.

## STATUS
**IMPLEMENTED, TESTED, CI VERIFIED, DEVICE VERIFIED (including visual verification).**
Not SECURITY REVIEWED. Not PRODUCTION APPROVED. **§15 is not closed** — phases 5/6/7 and
the audit suite are outstanding. **`G4` is not accepted by this batch.**

## BLOCKERS
None for this batch. Held owner decisions, all non-blocking: **Q-C8** (when a retired key
may be destroyed), **Q-C9** (whether the activation count becomes durable), and the
unresolved **§30 vs. store-launch** monetization conflict (AdMob/IAP are authorized but
unbuilt, and §30 prohibits ad and analytics providers during the core batches).

## NEXT BATCH
Protocol Batch E — real notifications: request `POST_NOTIFICATIONS` only when needed,
handle granted/denied/disabled honestly, and never render "alert sent" without a
permission and a persisted delivery receipt (§16). Carry in the Q-C9 durability decision,
since Batch E is the first batch where a real delivery row must be persisted.
