# Batch report — 4d-storage-wiring-gate (decision §15 wiring slice, behind a runtime gate)

## BATCH
4d-storage-wiring-gate — install `LocalShieldraStorage` into the application
graph *only* behind an explicit runtime approval gate that is off by default.
This is a gated wiring slice, **not** the §15 production gate itself and **not**
G4 sign-off.

## SCOPE
Provide a compile-time owner approval (`StorageRuntimeApproval.approved`,
default `false`) and a pure `decideStorageInstall` that maps the storage open
result to `NotApproved` / `Installed` / `RecoveryRequired`. Wire `ShieldraApp`
to consult that gate and open exclusively through `openOrRecovery` (never the
raw factory). With the gate off (the shipped default) no open is attempted, so a
default build creates no database and touches no Keystore key. No new security
policy was invented; the storage internals are unchanged.

## FILES CHANGED
- `app/src/main/kotlin/com/shieldra/storage/StorageRuntimeApproval.kt` (NEW) —
  `approved = false` constant, `StorageInstallDecision` sealed outcome, and the
  pure `decideStorageInstall`.
- `app/src/main/kotlin/com/shieldra/app/ShieldraApp.kt` — `onCreate` now runs
  `decideStorageInstall(approved = StorageRuntimeApproval.approved) { openOrRecovery(...) }`
  and keeps the decision (and, on success only, the installed storage) as state;
  a `RecoveryRequired` is retained, never crashes, and never recreates the DB.
- `app/src/test/kotlin/com/shieldra/storage/StorageRuntimeGateTest.kt` (NEW) —
  3 JVM tests: off → `NotApproved` and `open` not invoked; on + `Ready` →
  `Installed`; on + failure → `RecoveryRequired` with no storage.
- `app/src/androidTest/kotlin/com/shieldra/storage/StorageWiringGateOffInstrumentedTest.kt`
  (NEW) — 3 device tests: the flag is false; off never opens and creates no
  `shieldra.db`; off adds no Keystore key (before/after alias-set comparison).
- `.github/scripts/g4b-review-check.sh` — replaced the blanket "not auto-wired"
  rule with a §15 wiring gate (flag off by default, `ShieldraApp` consults the
  gate and uses `openOrRecovery`, raw `create` still forbidden in the app graph),
  **and** fixed three `rg`-based gates that were silent no-ops on the runner
  (raw-create, `fallbackToDestructiveMigration` §4.3, and the §5.5 no-egress
  export check) by switching them to `grep`.

## DECISIONS ENFORCED
- §14: the only way storage attaches is the owner flipping `approved` in a
  visible commit; the shipped default stays off.
- §4.2–§4.4: a failed open surfaces as `RecoveryRequired` and is retained, with
  no destructive recreate.
- §2.5 / §4.3 / §5.5: the enforcement gates for these are now genuinely active
  in CI (grep), not `rg` no-ops.

## TESTS
154 JVM tests green in CI (151 before this batch + 3 new `StorageRuntimeGateTest`).

## ANDROID TESTS
On the owner RMX3910 with APKs from the same green run (`36537846580`):
`StorageWiringGateOffInstrumentedTest` **`OK (3 tests)`**, and the full
instrumented suite **`OK (29 tests)`** (was 26; +3 new) — no regressions. Proves
the default build never opens storage, never creates `shieldra.db`, and never
adds or removes an evidence Keystore key.

## BUILD
`assembleDebug` and `assembleDebugAndroidTest` both built in CI
(runs `36525983600` and `36537846580`).

## STATIC AUDIT
`g4b-review-check.sh` PASS in CI. New §15 wiring gate PASS, and the
raw-create / destructive-migration / §5.5-export gates now actually run (PASS)
instead of silently skipping when `rg` is absent.

## SECURITY AUDIT
Gate-off path has zero storage side effects (device-verified). Gate-on path
opens only through `openOrRecovery`; recovery is non-destructive. No software
key fallback and no egress introduced.

## SELF-ADVERSARIAL FINDINGS
1. RED was proven by symbol absence: commit `bef65a4` pushed only the test, and
   CI run `36525682066` failed to compile because `decideStorageInstall` /
   `StorageInstallDecision` did not exist yet — the test cannot pass before the
   code lands.
2. While writing the §15 gate I found the pre-existing `rg`-based checks were
   **no-ops on the GitHub runner** (no `rg` installed; `rg … && fail` / `if rg …`
   silently evaluated false). The §4.3 destructive-migration and §5.5 export
   prohibitions were therefore not being enforced in CI. Fixed by switching to
   `grep` (`0901c5c`) and re-verified green.
3. The device Keystore assertion compares the alias set before/after rather than
   asserting absolute absence, because other instrumented tests legitimately
   create the evidence aliases in the same process; the gate only needs to change
   nothing.

## COMMIT
`bef65a4` (RED test) → `8e2accc` (gated wiring) → `0901c5c` (rg→grep enforcement), lineage on `main`.

## REMOTE
`toufikben/shieldra` @ `main`, pushed to `0901c5c`.

## CI
"Group A diagnostics": `36525682066` FAILED (compile — expected RED) →
`36525983600` SUCCESS (154 tests, APKs, review scripts) →
`36537846580` SUCCESS (final, commit `0901c5c`; grep gates confirmed PASS).

## ROADMAP
`4d-storage-wiring-gate` CLOSED (CI + DEVICE). §15 production gate and overall
G4 sign-off remain **OPEN**.

## STATUS
CLOSED for this gated slice only. Does **not** close §15 or G4.

## BLOCKERS
§15/G4 still need: the Lock Guard adapter, phase 5 local alerts, phase 6/7,
and the storage/crypto/guard/evidence/alert/permission/privacy audits plus the
final self-adversarial audit — all owner-decision/policy gates requiring explicit
approval before new design work. Flipping `StorageRuntimeApproval.approved` to
`true` is the owner's future, visible sign-off and is deliberately NOT done here.

## NEXT BATCH
Present the next genuine code-unblocking phase (Lock Guard adapter) for owner
approval; do not start new spec/code without an explicit yes.
