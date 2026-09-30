# Batch F — §17 background-execution contract and the Batch E truthfulness fixes

**No continuous Guard detector was implemented in Batch F.**

## BATCH
`F-background-execution-contract` (protocol **Batch F**) — the owner's Batch F directive was
explicit and is the scope of this report: *"Batch F لا تضيف Motion/SIM/Lock detector … تبني
وتتحقق من طبقة تنفيذ الخلفية اللازمة لكي يمكن لاحقًا تشغيل Guard حقيقي بطريقة Android-compliant"*.
Two things were in scope, and only those two:
1. **F1 — close the three Batch E findings** (the fake `Email · WhatsApp · Telegram` delivery
   row, the stale dashboard "no production data" banner, the demo storage numbers), without
   implementing a delivery channel and without redesigning storage.
2. **F2/F3 — the smallest background-execution abstraction**, platform-neutral, with the five
   layers separated, and **no** foreground-service type, permission, `Service`, or WorkManager
   detector invented ahead of the first real detector.

## SCOPE
- **F2 — `BackgroundExecutionController`** (`app/src/main/kotlin/com/shieldra/runtime/`).
  `ExecutionPhase` (`Stopped | Running`), `Prerequisite` (`DetectorAvailable |
  PermissionGranted | StorageInstalled`), `StartResult` (`Started | AlreadyRunning |
  PrerequisitesMissing(missing) | Failed(reason)`), `StopResult` (`Stopped | AlreadyStopped`),
  and `MonitoringClaim` (`NoExecution | ExecutionWithoutDetector | ActiveGuardMonitoring`, where
  only the third has `claimsProtection == true`). Start and stop are idempotent; a failing start
  leaves the phase `Stopped` and carries the real reason; a controller rebuilt in a new process
  reports `Stopped`, never `Monitoring`.
- **F2 — the five layers stay distinct and are documented as such:** background execution ≠
  Guard detection ≠ event confirmation ≠ event persistence ≠ notification. Only the last two
  exist as shipped code today (persistence from Batch C/D, notification from Batch E); this
  batch supplies the first as a contract only, and layers two and three remain unwritten.
- **F3 — nothing was invented.** No `<service>`, no `FOREGROUND_SERVICE_*` permission, no FGS
  type string, no WorkManager request, no alarm, no boot receiver. The manifest still declares
  exactly one permission (`POST_NOTIFICATIONS`, amendment A-6), and a gate fails the build if a
  second one appears or if a service or WorkManager detector is added before a detector exists.
- **F1 — honest Settings/dashboard content.** `SettingsContent` is the single source for which
  rows exist, what each row's target really is (`SettingsRowTarget.NotificationSetup |
  NotAvailable`), what the storage row may say (`storageRowShowsFigures = false`), and what the
  dashboard may claim (`DashboardMode.claimsMonitoring = false`). The delivery row and its
  strings are deleted; a Notifications row with the live A-6 permission state replaces it.
- **F1 — no fabricated "last check".** `LockGuardDecision.lastCheckSeconds(state)` returns
  `null` for every state, and both locales gained `last_check_none` ("No check yet" /
  "لا يوجد فحص بعد"). The dashboard previously printed `Last check · 0s ago`, which is evidence
  of a check that never ran.

Deliberately out of scope (F6): Motion/SIM/Lock detection, camera, continuous location, audio,
battery percentage as a security event, cloud, Firebase, FCM, SMS, email, WhatsApp, Telegram,
analytics, and the Premium-screen delivery claims listed under SELF-ADVERSARIAL FINDINGS.

## FILES CHANGED
- app/src/main/kotlin/com/shieldra/runtime/BackgroundExecution.kt (new — the controller and its result states)
- app/src/test/kotlin/com/shieldra/runtime/BackgroundExecutionControllerTest.kt (new — 9 JVM tests)
- app/src/main/kotlin/com/shieldra/app/presentation/settings/SettingsContent.kt (new — pure row/claim policy)
- app/src/main/kotlin/com/shieldra/app/presentation/settings/HonestSettingsRows.kt (new — thin `@Composable` factory over `SettingsContent`)
- app/src/test/kotlin/com/shieldra/app/presentation/settings/SettingsContentTest.kt (new — 7 JVM tests)
- app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt (Settings built from `honestSettingsRows(storageInstalled, notificationState)`; `SettingsContent.targetOf(id)` routes the notification row and defers the dead ones; `DELIVERY_ROW_ID` deleted)
- app/src/main/kotlin/com/shieldra/app/presentation/screen/settings/SettingsScreen.kt (rows supplied by the caller; notifications section; previews updated)
- app/src/main/kotlin/com/shieldra/app/presentation/screen/dashboard/DashboardScreen.kt (honest last-check label; disclosure chosen from `SettingsContent.dashboardMode(...)`)
- app/src/main/kotlin/com/shieldra/app/design/components/domain/ShieldCore.kt (`lastCheckSeconds: Long?`, `formatLastCheck` handles `null`)
- app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDecision.kt (`lastCheckSeconds(state) = null`)
- app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDashboardFactory.kt (`lastCheckSeconds` from the decision; `storageInstalled` from the real state)
- app/src/main/kotlin/com/shieldra/app/presentation/model/PresentationModels.kt (`DashboardUiModel.storageInstalled`; `SettingsSection`/`SettingsIcon` `Delivery` → `Notifications`)
- app/src/main/kotlin/com/shieldra/app/design/icons/ShieldraIcons.kt (notifications icon)
- app/src/main/kotlin/com/shieldra/app/presentation/demo/DemoData.kt (`settingsRows()` deleted — demo data must not be able to define a shipped screen)
- app/src/main/res/values/strings.xml, values-ar/strings.xml (`settings_delivery_channels`, `demo_delivery_channels`, `settings_storage_summary`, `phase3_demo_disclosure`, `settings_section_delivery` removed; `settings_section_notifications`, `dashboard_events_disclosure`, `dashboard_no_storage_disclosure`, `settings_storage_in_use`, `settings_storage_none`, `last_check_none` added; the onboarding copy no longer says the app "quietly guards")
- app/src/test/kotlin/com/shieldra/app/presentation/guard/LockGuardDecisionTest.kt (+1 test: no state may report a last check)
- docs/capabilities/background-execution-limits.md (new — the F5 documentation requirement)
- .github/scripts/g4b-review-check.sh (gates 9–13: Settings truth, no premature service/permission/WorkManager, background-execution semantics, the owner's named tests, no claimed check/guard)

No file under `app/src/androidTest`, `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`,
or the Room schema was touched: this batch changes no runtime permission, no schema and no
dependency.

## DECISIONS ENFORCED
- **§17 / F4** — a foreground service is acceptable *for an active guard*, and WorkManager is
  prohibited as a real-time detector. Since no guard exists, neither was added; the capability
  document records WorkManager's status as "never a detector", not "deferred".
- **F3 + §23** — no permission may exist before the functionality that needs it. The manifest is
  unchanged at one permission and a gate counts them.
- **§31** — no UI may claim more than the state behind it: `MonitoringClaim.claimsProtection` is
  the only path to a protection claim and requires a running detector; `DashboardMode`
  cannot produce `Protected` or `Monitoring Active`; the delivery row, the "presentation demo —
  no production data" banner, the demo storage figures and the `0s ago` last-check are all gone.
- **§32** — every Settings row either navigates to a screen that exists or is honestly declared
  `NotAvailable` and shows the deferred message; nothing is wired to a no-op.
- **§14** — no policy was changed. `SHIELDRA_DECISIONS.md` and the §17 amendment log are
  untouched by this batch; the only owner decision this batch consumes is the Batch F directive
  itself, quoted in SCOPE.
- **§30** — no external provider, no ads, no analytics; the deleted delivery row is not replaced
  by any provider surface.
- **§2.5 / §4.3 / fail-closed** — untouched and still enforced: no software-key fallback, no
  destructive migration, and `a_failing_start_leaves_the_layer_stopped_and_carries_the_real_reason`
  keeps the new contract's failure path honest rather than optimistic.

## TESTS
CI workflow "Group A diagnostics" on `toufikben/shieldra` `main` (`clean test lint
assembleDebug assembleDebugAndroidTest` plus the review scripts):

- `36776730633` (`0e767b2`) — **RED, failure as intended**. The only errors are
  `Unresolved reference 'SettingsContent'`, `'SettingsRowTarget'`, `'MonitoringClaim'` in the new
  JVM test file — i.e. exactly the behaviour that did not exist yet.
- `36779189112` (`1502726`) — **success**: **247 tests, 247 passed, 0 failures, 0 errors, 0
  skipped** (Batch E ended at 231, so this commit adds 16), lint clean, both APKs, all gates green.
- `36781469794` (`cea844b`) — **RED, failure as intended**: the sole error is
  `Unresolved reference 'lastCheckSeconds'` in `LockGuardDecisionTest.kt:62-63`.
- `36782719559` (`d861922`, HEAD) — **success**: **248 tests, 248 passed, 0 failures, 0 errors, 0
  skipped**, lint clean, `assembleDebug` + `assembleDebugAndroidTest`, g4b (40 checks), Group C
  and Phase 3 (9 checks) all green.

Batch F adds **17 JVM tests**, including the scenarios the owner named in F7:

`BackgroundExecutionControllerTest` — `start_with_every_prerequisite_present_brings_the_layer_up_once`,
`starting_twice_reports_already_running_and_never_brings_the_layer_up_twice`,
`stopping_twice_reports_already_stopped_and_never_tears_the_layer_down_twice`,
`start_with_a_missing_prerequisite_is_refused_and_nothing_is_brought_up`,
`a_failing_start_leaves_the_layer_stopped_and_carries_the_real_reason`,
`a_recreated_controller_reports_stopped_not_monitoring`,
`a_running_execution_layer_without_a_detector_is_not_active_monitoring`,
`only_a_detector_on_a_running_layer_may_claim_active_monitoring`,
`stopping_after_a_failed_start_is_idempotent`.

`SettingsContentTest` — `the_settings_rows_have_a_notification_entry_and_no_delivery_entry`,
`the_notification_row_is_the_one_that_opens_the_real_permission_screen`,
`a_row_that_does_nothing_yet_is_declared_dead_instead_of_promising_a_screen`,
`the_storage_row_reflects_whether_storage_was_actually_installed`,
`the_storage_row_has_no_fabricated_event_or_byte_count`,
`the_dashboard_disclosure_stops_calling_real_storage_a_presentation_demo`,
`neither_dashboard_mode_claims_monitoring_without_a_detector`.

`LockGuardDecisionTest` — `no state may report a last check because nothing has ever checked`.

F7 requires that a failed start creates no Security Event. That is guaranteed structurally, not
by a new test: `BackgroundExecution.kt` contains **zero import statements** and no reference to
`SecurityEvent`, the pipeline, Room, or notifications (the only occurrences of those words in the
file are comments), and its three injected lambdas are its whole world — so a start that returns
`Failed` or `PrerequisitesMissing` has no channel through which to write an event. I am recording
this as a verified design property, not claiming a test I did not write. Note also that the Phase 3
boundary scan covers `com/shieldra/app`, not the new `com/shieldra/runtime` package, so this file
is currently protected by reading it and by the g4b semantics gate rather than by that scan.

## ANDROID TESTS
**No new instrumented test was written, because Batch F ships no runtime behaviour to run on a
device** — there is no service, no permission and no scheduling in this batch (F3). What *was*
run on the owner device **RMX3910** (`IR7X5DJ7KZGEHMS8`, Android 16 / API 36, **no StrongBox on
this device**) with the app APK and androidTest APK from the **same** green run `36782719559`,
after force-stopping and uninstalling both packages:

- **Full instrumented suite run twice on one install: `OK (55 tests)` then `OK (55 tests)`**, 0
  failures. This is F7 item 11 — the existing Panic, notification, storage and migration
  behaviour stays green on the fixed build. The same pair was run against `1502726` (APKs from
  `36779189112`) with the same result before the last-check fix, so the 55-test suite is green on
  both Batch F builds.

Observed on screen (uiautomator text dumps, `package="com.shieldra.app"` verified in every pulled
XML — never screenshots, and never a manifest grep in place of a device check):

1. Onboarding after a fresh install reads **"Security events are recorded on this phone."** and
   **"Continuous monitoring is not part of this build yet. What you trigger is stored here, on
   this device."** — the previous copy promised the app "quietly guarded" the device.
2. Dashboard reads **"Last check"** + **"No check yet"** (the fabricated `0s ago` is gone),
   alongside **"ATTENTION"** and **"Security events are recorded and stored on this phone. No
   continuous monitoring is running yet."** — no `Protected`, no `Monitoring Active`.
3. Settings shows a **Notifications** row reflecting the live A-6 permission state, **no**
   "Delivery channels · Email · WhatsApp · Telegram" row anywhere, and a **Storage** row whose
   subtitle is a real state word driven by `installedStorage != null`, not a count.
4. Tapping the Notifications row opens the real notification-setup screen; tapping a row declared
   `NotAvailable` shows the honest deferred message instead of doing nothing (§32).
5. Regression: a Panic press still posted a real local notification
   (`tag=a9d5b402-9a6b-47ef-a4a2-1cdeb99be65d`, channel `shieldra_security_events`) and History
   then listed **"30 seconds ago" · "Security event"** — Batch F did not break the Batch E loop.

**NOT RUN / not claimed:** there is no FGS, so there is no foreground-service device test, and I
make no claim about Doze, OEM battery management, Force Stop, reboot or process-death survival on
this device. `docs/capabilities/background-execution-limits.md` lists those as **requirements to
verify on the owner's device when the first detector lands**, not as results.

## BUILD
`assembleDebug` and `assembleDebugAndroidTest` succeeded in CI run `36782719559`; the APKs used
for the device verification above came from that run's `apks-36782719559` artifact (id
11128960093), so app and test APK share one debug keystore. No local build was attempted (owner
directive: CI is the verifier on this machine; grep-only static gates are permitted locally).

## STATIC AUDIT
Grep-based gates, run in CI on HEAD and reproduced locally:
- `.github/scripts/g4b-review-check.sh` — **PASS**, **40 checks** in run `36782719559`, including
  the five added in this batch: *"Settings rows claim only channels and counts the app really
  has"*, *"no service, no extra permission, and no WorkManager detector before a real guard
  exists"*, *"background execution is idempotent, refuses honestly, and cannot claim protection"*,
  *"the owner's named Batch F tests are present"*, *"no screen claims a check or a guard that does
  not exist"*.
- `.github/scripts/phase3-review-check.sh` — **PASS**, 9 checks, including *"runtime navigation
  does not import preview data"* and *"manifest declares only the A-6 notification permission"*.
- `.github/scripts/group-c-contract-check.sh` — **PASS**.
- `git diff --check` — clean.

## SECURITY AUDIT
- **Attack surface added: none at runtime.** No new permission, component, provider, file, key,
  network call or dependency. The new classes are pure Kotlin under `com.shieldra.runtime` and
  `…presentation.settings`, with three injected lambdas and no Android imports in the controller.
- **The claim-gate is the security property.** `MonitoringClaim` makes a protection claim
  unrepresentable without `detectorRunning == true`, so a future batch cannot light up
  `Protected` by wiring a service alone; the FGS type decision is deferred to the detector.
- **Failure semantics preserved.** `StartResult.Failed(reason)` carries the real exception
  message or class and leaves the phase `Stopped`; `PrerequisitesMissing` never calls `bringUp`,
  so nothing is partially brought up and then reported as running.
- **Deferred-surface risk recorded, not hidden:** the FGS type must be chosen from the actual
  detector's Android requirements (F8). Preselecting `specialUse`/`location`/`mediaPlayback` now
  would have baked in a Play-policy and permission surface for a capability that does not exist —
  which is the failure mode §31 and §23 both forbid.
- **Truthfulness residue found in surfaces this batch did not own** — see SELF-ADVERSARIAL
  FINDINGS 2–5. They are shipping-claim defects (Premium and event-detail copy), so they are
  recorded here with their exact strings rather than being silently rewritten or forgotten.

## SELF-ADVERSARIAL FINDINGS
1. **The dashboard was still printing evidence nothing produced.** After closing Batch E's
   findings 4/5, I re-read the dashboard dump and saw `Last check · 0s ago`, while no detector has
   ever checked — the same §31 class of defect the batch was fixing. Closed properly with a
   narrow RED (`cea844b`, run `36781469794` failed only on `Unresolved reference
   'lastCheckSeconds'`) then `d861922`, plus gate 13 so it cannot come back. Same pass also
   removed "quietly guarded" and "presentation demo for a future protection" from onboarding in
   both locales.
2. **`PremiumScreen` still promises the deleted channels at runtime.** `ShieldraNavHost` builds it
   from `DemoData.premium()`, which contains `premium_feature_channels_detail` = **"Email ·
   WhatsApp · Telegram"**, `premium_feature_receipts` = **"Advanced delivery receipts"**, and
   `premium_free_summary` = **"Free includes: Lock Guard · Motion Guard · SIM Guard · Panic ·
   Email delivery."** — reachable from the Premium tab, so removing the Settings row did not
   remove the claim. **NOT fixed here:** the Premium surface is the subject of the still-unanswered
   **Q-D5/Q-D7** billing-and-ads conflict, and rewriting product/pitch copy is a decision, not a
   defect repair. Recorded and asked as **Q-F1**.
3. **The event Detail screen renders an empty "DELIVERY" card.** `EventDetailScreen` builds a
   `DeliveryReceipt` from `model.delivery`, and `PersistedEventReader` sets `delivery =
   emptyList()`, so a real event shows a heading with nothing under it. Confirmed in the device
   dump. Left as-is: deleting the block means deciding whether remote delivery is still a product
   plan (Q-D2/Q-D5), not fixing a false number.
4. **Onboarding's "Allow" affordance is dead.** `PermissionsScreen`'s `onAllow = { defer() }`
   while it lists permission purposes. §32 says work, be honestly disabled, or be removed. Not in
   Batch F's file list, and §23 forbids requesting anything not needed yet, so the correct fix is
   a copy + state change that belongs to Batch H. Recorded under Q-F1 rather than patched here.
5. **Legacy "Phase 3" wording survives** in `event_unknown_message` and `phase3_deferred`, and the
   `ChannelId` enum plus `channel_email/whatsapp/telegram` strings still exist in code even though
   no screen lists them. Cosmetic/misleading only — no shipped UI asserts a channel from them — so
   I did not widen the batch to purge them.
6. **`DemoData` is still referenced by `@Preview`s** (EventCard, GuardTile, DashboardScreen
   previews). The Phase 3 gate distinguishes preview from runtime paths and *"runtime navigation
   does not import preview data"* passes, but the Premium case in finding 2 is exactly what
   happens when that distinction is not audited per screen. Every `DemoData.` reference is now
   listed, and finding 2 is the only non-preview one.
7. **I did not treat a green gate as device proof.** F7's "do not count a manifest grep as device
   verification" was honoured: the manifest/permission/service checks are reported under STATIC
   AUDIT only, and the ANDROID TESTS section reports only what ran or was dumped on the RMX3910.
   Conversely, the 55-test suite passing does **not** prove anything about background execution —
   there is no background execution shipped to prove.

## COMMIT
`0e767b2` (RED) → `1502726` (GREEN: Batch E findings 4/5 + the execution contract + gates 9–12) →
`cea844b` (RED for finding 1) → **`d861922`** (GREEN: last-check + onboarding copy + gate 13).
`ddf3010` is Batch E's last commit and the parent of this chain.

## REMOTE
`toufikben/shieldra`, branch `main`. Verified after the push:
`git rev-parse HEAD origin/main` both return `d8619227b1c0da8f0559c77acc05e5b9e92e1428`, and
`git status --short` is empty — every line of this report describes a commit that exists on the
remote.

## CI
Workflow "Group A diagnostics", run **`36782719559`** (HEAD `d861922`) — **success**:
**248 tests, 248 passed, 0 failures, 0 errors, 0 skipped**, lint clean, both APKs assembled, g4b
40 checks / Phase 3 9 checks / Group C green, evidence uploaded as `group-a-evidence-36782719559`.
Earlier in the chain: `36779189112` success (247/247); `36776730633` and `36781469794` are the two
deliberate RED failures whose only errors are the missing symbols named above.

## ROADMAP
`docs/ROADMAP.md`: row `F-background-execution-contract` **CLOSED (CI + DEVICE)**; Phase 7 status
changed from "RESOLVED IN PRINCIPLE / Batch F" to "CONTRACT LANDED / EXECUTION SURFACE DEFERRED",
recording that the FGS type is unselected on purpose; gate **G3** now reports the contract as
landed and the detector as remaining; **Batch G — Motion Guard (§18)** is the required next batch,
and its first task is to name the detector's real Android requirements, from which the FGS type
and permission will then be chosen (F8).

## STATUS
**Batch F CLOSED (CI + DEVICE).** **No continuous Guard detector was implemented in Batch F**, and
none is claimed anywhere in the shipped app: `Protected` and `Monitoring Active` are
unrepresentable without a running detector; the Settings delivery row, the "no production data"
banner, the demo storage figures and the fabricated last-check are gone; the background-execution
contract exists, is idempotent, refuses honestly when a prerequisite is missing, survives process
recreation as `Stopped`, and is covered by 9 named JVM tests plus a CI gate.

G4 is **not** accepted. §15 is **not** satisfied end to end: Motion/SIM/Lock runtime detection,
the actual foreground-service mechanism, the remaining guards, and the full audit suite (Batch K)
all stay open. What Batch F delivered is the contract those pieces plug into, not a piece of
monitoring itself. `StorageRuntimeApproval.approved` remains `true` (amendment A-5, untouched by
this batch), exactly one Android permission is declared, and nothing here changes crypto, storage,
schema, or the Panic/notification behaviour beyond UI wording.

## BLOCKERS
Nothing blocks Batch G. Owner-side items, none of which I will decide alone:
1. **Q-F1 (new)** — the remaining delivery claims: the Premium feature list ("Email · WhatsApp ·
   Telegram", "Advanced delivery receipts", `premium_free_summary`), the empty **DELIVERY** card on
   the event Detail screen, and the dead onboarding **Allow**. These are entangled with the open
   **Q-D5/Q-D7** ads-and-billing conflict, so one line there closes several of them.
2. **Q-C8** (unchanged) — destroying a *retired* evidence key version.
3. **Store copy** (outside this repository) — the listing still says "zero Android permissions"
   and advertises anti-theft capability that has no detector yet. The accurate sentence after A-6
   is "no permissions until you need them".

## NEXT BATCH
**Batch G — Motion Guard (§18)**, in the owner-mandated order. Its first deliverable is the
detector's actual Android requirement set (sensor, sampling, whether it must run with the screen
off, what the Play policy allows), because **the foreground-service type and permission must be
selected from the actual detector's Android requirements rather than preselected now** (F8). The
`BackgroundExecutionController` is where that detector plugs in: `Prerequisite.DetectorAvailable`
becomes real, and only then may `MonitoringClaim.ActiveGuardMonitoring` — and therefore any
`Protected` UI — exist. Until a detector is proven on the RMX3910, the dashboard keeps reporting
"No check yet" and `ATTENTION`.
