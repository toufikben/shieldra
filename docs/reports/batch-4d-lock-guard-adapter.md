# Batch report — 4d+-lock-guard-adapter (honest runtime guard projection)

## BATCH
4d+-lock-guard-adapter — replace the fabricated demo dashboard that the app
rendered at runtime with an honest Lock Guard projection derived from real
runtime state. This batch closes **only** the honest-projection slice; it does
**not** close §15 and does **not** close G4.

## SCOPE
The app previously called `DemoData.dashboard()` / `recentEvents()` /
`eventDetail(id)` from `ShieldraNavHost`, so a shipped build displayed a
"Protected" shield, four "Active" guards, a fabricated 84% battery, a Wi-Fi
network label, and two invented security events. None of that came from a real
detection — there is no live sensor detection and no background monitoring yet
(phase 5 / G3 is owner-policy-blocked), so the runtime UI was asserting
protection the app does not have.

This batch adds a pure decision core that maps runtime state to a guard state,
and a projection factory that builds a `DashboardUiModel` from that state alone.
The invariant enforced is the security invariant: **without detection, the app
must never render "Protected" or an "Active" guard.**

Deliberately **out of scope** (and why):
- **Reading real persisted events** — `EventRepository` exposes only
  `find`/`save`/`updateState`; there is no list method, and Room cannot be
  queried on the main thread. Adding a real read means adding a query method, a
  background dispatcher, and a ViewModel/lifecycle — a separate, larger slice
  that should be approved on its own.
- **Detection, services, permissions, local alerts** — all owner-policy items
  gated behind G2/G3 answers that have not been given.
- **Any storage gate flip** — §14 forbids a silent policy change.

The result is deliberately a *degraded* dashboard: the honest state of a build
with no detection running.

## FILES CHANGED
- `app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDecision.kt`
  (NEW) — `LockGuardState` (`NotActive` / `StorageIdle`), the pure
  `LockGuardDecision.decide(storageInstalled)`, plus
  `protectionState(...)` (never `Protected`) and
  `guardStatuses(...)` (every `GuardKind` → `GuardStatus.Disabled`).
- `app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDashboardFactory.kt`
  (NEW) — `lockGuardDashboard(state): DashboardUiModel` and
  `lockGuardEventDetail(id): EventDetailUiModel?` (returns `null` until a real
  reader exists).
- `app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt` — reads
  `installedStorage` off the `ShieldraApp` application, derives `guardState`
  via `LockGuardDecision.decide`, and feeds `lockGuardDashboard(guardState)` to
  the dashboard, `emptyList()` to History, and `lockGuardEventDetail(id)` to
  the detail route. `DemoData` is no longer used for dashboard/events at
  runtime (it remains for `premium()`/`settingsRows()` and for previews).
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ar/strings.xml`
  — added `guard_subtitle_inactive` and `status_network_unknown`.
- `app/src/test/kotlin/com/shieldra/app/presentation/guard/LockGuardDecisionTest.kt`
  (NEW) — 5 JVM tests (see TESTS).
- `.github/scripts/g4b-review-check.sh` — new "honest projection" gate (see
  STATIC AUDIT).

## DECISIONS ENFORCED
- **No fabricated protection:** with no detection, the runtime dashboard must
  not show `Protected` or an `Active` guard. Enforced in code *and* in the CI
  review script.
- §14: no policy change; the storage gate stays off and no demo data reaches
  the runtime protection path.
- §2.5 / §4.3 / §5.5: no new key material, no migration, no egress — this batch
  only changes what is *rendered* from existing state.

## TESTS
5 new JVM tests in `LockGuardDecisionTest`, green in CI. They assert: not-active
storage → `NotActive`; installed storage → `StorageIdle` (never `Protected`);
the not-active state maps to `ProtectionVisualState.Disabled`; storage-idle maps
to an attention visual state and never `Protected`; and — across all
`GuardKind.entries` — no guard is ever `Active` or `Signal`. The last test is
the one that would fail if someone later reintroduced a fabricated "Active"
guard.

## ANDROID TESTS
On the owner RMX3910 with APKs from the same green run (`36542577529`):
`StorageWiringGateOffInstrumentedTest` **`OK (3 tests)`** (regression — the
default build still creates no DB and no Keystore key).

Runtime visual check on the same installed APK (via `adb` + `uiautomator`
dump, since a text dump was all that was available in this environment):
- `Protection disabled. Last check: 0s ago`
- `DISABLED`
- `Last check 0s ago. Battery unknown. Offline` (battery shows `—`, network
  `Offline` — no fabricated 84% / Wi-Fi)
- `Lock Guard. Not monitoring yet. Status: Disabled.` and
  `Motion Guard. Not monitoring yet. Status: Disabled.`

This is the honest projection actually rendered on the owner device — no
"Protected", no "Active" guard, no invented battery/network/event numbers.

## BUILD
`assembleDebug` and `assembleDebugAndroidTest` both built in CI
(run `36542577529`, `BUILD SUCCESSFUL in 4m 8s`).

## STATIC AUDIT
`g4b-review-check.sh` PASS in CI, including the new gate
`PASS: runtime guard projection is honest (no fabricated protection)`. The gate
fails if `ShieldraNavHost` renders a fabricated dashboard/event from `DemoData`
at runtime, if it does not use `lockGuardDashboard` / `LockGuardDecision.decide`,
if `LockGuardDecision` is missing, if it does not contain `GuardStatus.Disabled`,
or if it does not contain `ProtectionVisualState.Disabled`.

## SECURITY AUDIT
The change strictly reduces what the app claims. No new platform capability,
no new permission, no network, no storage mutation. The pre-existing gates
(raw-create, §4.3 destructive-migration, §5.5 no-egress) remain green.

## SELF-ADVERSARIAL FINDINGS
1. RED was proven by symbol absence: commit `bbdffd6` pushed only the test, and
   the CI run failed to compile because `LockGuardDecision` did not exist yet.
   The test cannot pass before the code lands.
2. The `DemoData` import intentionally **remains** in `ShieldraNavHost` for
   `premium()` / `settingsRows()`. The new CI gate therefore greps specifically
   for `DemoData.(dashboard|recentEvents|eventDetail)(` rather than the bare
   symbol, so it does not over-constrain legitimate non-protection demo data.
3. `lockGuardEventDetail` currently returns `null` for every id, so the detail
   route always lands on the existing "unknown event" error state. This is
   honest for a build with no real event reader, but it means the detail screen
   is not reachable at runtime yet; that is covered by the next batch.
4. A real event read was scoped **out** rather than half-implemented, because
   it would require a new `EventRepository` list method plus main-thread
   threading, and doing it partially would create a main-thread Room access bug.

## COMMIT
`bbdffd6` (RED test) → `71f74ca` (honest projection + NavHost wiring + CI gate),
lineage on `main`.

## REMOTE
`toufikben/shieldra` @ `main`, pushed to `71f74ca`.

## CI
"Group A diagnostics": RED run FAILED (compile — expected) → GREEN run
`36542577529` **SUCCESS** (BUILD SUCCESSFUL, review scripts PASS including the
new honest-projection gate, APKs published).

## ROADMAP
`4d+-lock-guard-adapter` CLOSED (CI + DEVICE) for the honest-projection slice.
§15 production gate and overall G4 sign-off remain **OPEN**.

## STATUS
CLOSED for this slice. The runtime UI no longer fabricates protection.

## BLOCKERS
§15/G4 still require: the real persisted-event reader (a new `EventRepository`
list method + ViewModel + threading), the actual Lock Guard detection path and
phase 5 local alerts (G2/G3 owner-policy answers), phase 6/7, and the
storage/crypto/guard/evidence/alert/permission/privacy audits plus the final
self-adversarial audit. Flipping `StorageRuntimeApproval.approved` to `true` is
the owner's future, visible sign-off and is deliberately NOT done here.

## NEXT BATCH
Present the next genuine code-unblocking phase for owner approval. The two
candidates are (a) the real persisted-event reader behind the already-wired
storage (purely technical, no policy questions), or (b) the actual Lock Guard
detection path, which needs G2/G3 owner-policy answers first. Do not start new
design/code without an explicit yes.
