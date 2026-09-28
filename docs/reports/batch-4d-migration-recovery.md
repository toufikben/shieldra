# Batch 4d-migration-recovery — RecoveryRequired for unsupported version, downgrade, and migration failure

**Date:** 2026-09-28
**Depends on:** `4d-key-rotation` (CI green, runs `36419607669` / `36420875440`)
**Status:** **IMPLEMENTED — CI GREEN (RED `36422292927` → run `36423376061`: 111/111 JVM tests, lint clean, both APKs built, all review gates pass); DEVICE VERIFICATION OF REAL ROOM WORDING STILL OPEN**

## Batch

`4d-migration-recovery` — the remaining scope of decisions §4.2, §4.3, §4.4
and part of §4.6: what happens when the Room database cannot be opened.

## Scope

- A typed outcome for storage open failures: `StorageOpenResult.Ready` or
  `StorageOpenResult.RecoveryRequired(reason, detail)`.
- A classifier that maps an open failure to one of `DOWNGRADE`,
  `MIGRATION_FAILURE`, `UNSUPPORTED_VERSION`, `UNREADABLE_STATE`
  (decisions §4.2, §4.3, §4.4).
- A guard that converts any `Throwable` raised while opening into
  `RecoveryRequired`, never a crash and never a destructive action, and that
  reports if the database file disappeared during the failed attempt.
- `LocalShieldraStorage.openOrRecovery(...)` as the composition entry point
  that uses the guard.
- 12 JVM tests pinning the above, and a new CI review gate.

Out of scope and not claimed: no application-graph wiring, no UI recovery
screen, and no device-verified mapping against Room's real exception wording.

## Files changed

Production:

- `app/src/main/kotlin/com/shieldra/storage/StorageRecovery.kt` (new) —
  `StorageRecoveryReason`, `StorageOpenResult`, `StorageRecovery.classify`,
  `StorageRecoveryGuard.open`.
- `app/src/main/kotlin/com/shieldra/storage/LocalStorageFactory.kt` —
  added `openOrRecovery(...)`; `create(...)` is unchanged.

Tests:

- `app/src/test/kotlin/com/shieldra/storage/StorageRecoveryTest.kt` (new,
  12 tests).

CI:

- `.github/scripts/g4b-review-check.sh` — new gate requiring the fail-closed
  reason, the guarded open in the factory, and the recovery test file.

## Decisions enforced

- **§4.2 Unsupported versions → RecoveryRequired, no destructive fallback.**
  Unrecognized wording also lands in `UNREADABLE_STATE`, which is still
  `RecoveryRequired`, so an unknown failure can never be treated as healthy.
- **§4.3 Migration failure → keep the database.** The guard only reads
  `File.exists()`; it contains no delete, truncate, recreate, or
  `fallbackToDestructiveMigration()` path, and the existing CI scan still
  fails if a destructive fallback is added to production source.
- **§4.4 Downgrade → RecoveryRequired, no destructive downgrade.**
  `DOWNGRADE` is checked before `MIGRATION_FAILURE` because Room's downgrade
  failure text also mentions migrations.
- **§14 No silent policy change.** No owner decision was reopened; this batch
  only implements the outcome the decisions already mandate.

## Decision §4.6 test matrix

| §4.6 requirement | Test | Covered here? |
| --- | --- | --- |
| Unsupported future version | `future database version is classified as unsupported version` | yes (JVM) |
| Downgrade | `downgrade request is classified as downgrade`, `downgrade text in a nested cause is still classified as downgrade` | yes (JVM) |
| Migration failure | `missing or failed migration is classified as migration failure`, `sql exception raised while migrating is classified as migration failure` | yes (JVM) |
| No destructive fallback | `open failure returns RecoveryRequired and keeps the database file intact` | yes (JVM, file preserved byte-for-byte) |
| Unknown state fails closed | `an unexplained failure fails closed as unreadable state`, `a message-less failure fails closed instead of crashing the classifier`, `a non-exception throwable in the open path still fails closed` | yes (JVM) |
| Opening a v1 database | `ShieldraDatabaseMigrationInstrumentedTest` (previous batch) | instrumented — NOT RUN |
| Supported schema | same instrumented suite | NOT RUN |
| Foreign keys / event-evidence integrity | same instrumented suite | NOT RUN |

## Tests

12 new JVM tests. CI run
[`36423376061`](https://github.com/toufikben/shieldra/actions/runs/36423376061)
(commit `8fbf3b9`) executed the full JVM suite: **111 tests, 111 passed, 0
failures, 0 errors, 0 skipped** (99 before this batch + 12 new),
`BUILD SUCCESSFUL in 4m 6s`.

The tests were pushed before the production code, and the RED run
[`36422292927`](https://github.com/toufikben/shieldra/actions/runs/36422292927)
(commit `eef8df9`) failed exactly on the missing API
(`Unresolved reference 'StorageRecovery'`, `'StorageRecoveryReason'`), so the
suite is proven to exercise code that did not previously exist.

## Android tests

**NOT RUN.** No new instrumented test was added in this batch: expressing a
real Room downgrade or missing-migration failure needs a database declared at
a different version than `ShieldraDatabase`, which requires an androidTest
Room schema and its own KSP wiring. The earlier
`ShieldraDatabaseMigrationInstrumentedTest` is still compiled by CI and still
unexecuted.

## Build

`clean test lint assembleDebug assembleDebugAndroidTest` succeeded on run
`36423376061`, including the debug APK and the instrumented test APK. Not
built locally, per the owner's direction to verify on GitHub.

## Static audit

Run `36423376061`: `Group C boundary scan passed.`, `G4-B LOCAL REVIEW PASS —
runtime/device evidence still required` (including the new
`PASS: RecoveryRequired open path is guarded and tested`), `PHASE 3 STATIC
REVIEW PASS`.

## Security audit

- The fail-closed default is the classifier's last branch, so adding a new
  Room failure wording cannot silently produce `Ready`.
- `detail` carries the exception class and message. At this boundary those
  messages contain schema wording (table and column names), not stored event
  or evidence content, which is why surfacing them to a recovery screen is
  acceptable; this assumption is recorded here rather than left implicit.
- Catching `Throwable` at this boundary is deliberate: a narrower catch would
  let an open failure escape as a crash, and the most likely future "fix" for
  that crash would be recreating the database — exactly what §4.3 forbids.
- No new file path, permission, or key usage was introduced.

## Self-adversarial findings

1. **Marker strings are not a stable API.** The classifier matches observed
   English wording from Room and the framework SQLite layer. Room can reword
   these; the outcome would then degrade to `UNREADABLE_STATE`, which is
   still safe but less informative. Verified against real Room wording only
   on a device — recorded as open.
2. **Room does not open the database inside `openOrRecovery`.** The first
   real §4.2–§4.4 failure happens on first query, so today the guard covers
   synchronous composition failures only. Stated in the KDoc and here instead
   of pretending the version check runs at build time.
3. **The "file disappeared" branch is defensive, not reachable from this
   code.** It exists to detect a future destructive path in the open chain.
   Kept because the guarantee it protects is a owner decision, but it is not
   evidence that a destruction was observed.
4. **Precedence was chosen, not derived.** `DOWNGRADE` before
   `MIGRATION_FAILURE` because Room's downgrade text mentions migrations;
   a future wording change could flip that and the tests would catch it.
5. **No instrumented test for the recovery reasons** even though §4.6 lists
   these scenarios; this batch delivers the JVM half and leaves the device
   half explicitly open.
6. **One intermediate CI run failed on test syntax** (`assertTrue` argument
   order in `kotlin.test`, commit `47ad17d`). Fixed in `8fbf3b9`. Recorded
   because "CI was green" alone would hide that the first green attempt
   failed for a non-behavioral reason.

## Roadmap

- `4d-migration-recovery`: JVM half implemented and CI green; the device half
  of §4.6 (real Room wording, v1 open, foreign keys, integrity) remains open.
- G4 remains **not accepted**; Phase 4 remains open.

## Blockers

- Device execution of the instrumented suites on the owner RMX3910 (Room,
  AES-GCM/AAD, migration, StrongBox detection) — still NOT RUN.
- An androidTest-side Room schema to drive genuine downgrade and
  missing-migration failures.
- Security acceptance (G4) requires owner approval.

## Next batch

Add the instrumented recovery test: a test-owned Room database at a lower
declared version to force Room's real downgrade and missing-migration
exceptions, asserting `RecoveryRequired` and that the database file survives
on device. Then run all accumulated instrumented suites on the RMX3910 and
record the measured wording so the classifier markers can be replaced by
verified ones.
