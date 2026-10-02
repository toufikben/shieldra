# Batch G / slice G5 — closure: what Motion actually does, what it actually stores, and what the UI is now allowed to say

Owner protocol: **SHIELDRA — G5 Truthfulness Fix + Accelerated Closure Protocol** (2026-10-02).
This file is the §16 report for that protocol. It separates the six evidence levels the owner
demanded, because they are not one claim and must not be read as one.

**Verification target:** `18cc745` on `main`. CI run **`36939853591` → success**,
`CI TEST SUMMARY: 332 tests, 332 passed, 0 failures, 0 errors, 0 skipped`,
`Group C boundary scan passed`, `G4-B LOCAL REVIEW PASS — runtime/device evidence still required`,
`PHASE 3 STATIC REVIEW PASS`, `BUILD SUCCESSFUL in 7m 39s`.
Device: **RMX3910**, Android 16, serial `IR7X5DJ7KZGEHMS8`.

---

## 1. THE SIX LEVELS, KEPT APART

### 1.1 Motion monitoring implementation — **IMPLEMENTED AND DEVICE-PROVEN**

`MotionMonitoringService` (`com.shieldra.detection`) owns the single `MotionSensorAdapter`
(A-15's owner ruling), feeds `MotionGuardPipeline`, and runs as a real foreground service. The
start decision is `MotionMonitoringStartGate` and the truth read is `MotionMonitoringTruth` —
`Active` requires the service to be running **and** `adapter.isReporting`, never a button press.
The shipping calibration is the six owner-approved numbers in `ApprovedMotionCalibration`
(`4.6 / 0.8 / 500 / 100 / 20_000 / 11.0`), none of which this slice touched.

Landed in `17bc8a4` (amendment **A-19** moved the gates in that same commit, per A-18's rule),
CI run `36932332864` → success.

### 1.2 Motion event persistence — **IMPLEMENTED AND DEVICE-PROVEN**

A confirmed movement becomes a `MOTION_CONFIRMED_EVENT` row through `MotionIncidentBinder.confirm()`,
which returns `Recorded` only when the repository answered `RepositoryResult.Applied`. The row is
written by the same encrypted Room store Panic uses; nothing about encryption, IDs or the schema
changed in this slice.

### 1.3 Same-event deduplication — **IMPLEMENTED, AND OBSERVED ON THE DEVICE**

A second confirmation while the incident is open returns `BoundToActive` and reuses the open
Motion `EventId` instead of writing a new row (A-18's ruling; Motion-scoped, never a
Lock/SIM/Battery/Panic row). Observed: one ~4-second shake produced **exactly one** Motion row —
the History "All" list went from one persisted event to two (one Panic + one Motion), not more.
This is the check the owner asked for explicitly ("راجع السجلات وتأكد من عدم تكرار الحدث").

**Not claimed:** that a long or repeated sequence of movements collapses correctly under every
pattern. The JVM tests cover the state machine; the device observation covers one real episode.

### 1.4 Ongoing notification — **IMPLEMENTED AND READ FROM THE SYSTEM**

While the service runs, `dumpsys notification --noredact` shows
`NotificationRecord(pkg=com.shieldra.app id=20002 … Notification(channel=shieldra_motion_monitoring
flags=ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE)` with
`title=String (SHIELDRA protection is active)` — A-9's exact wording, no "24/7", no "guaranteed".
After Stop, the count of `shieldra_motion_monitoring` notification records is **0** and
`dumpsys activity services` reports no foreground service.

### 1.5 Device verification — **DONE, all ten points, below**

### 1.6 Truthfulness fixes — **DONE, exactly the four the owner authorized**

---

## 2. THE FOUR FIXES

### A — each History row carries its own guard's type

`PersistedEventReader` used to stamp every row with a type taken from the reader-wide labels, so a
Motion confirmation was filed under Panic by the filter, the icon and the detail screen alike.
`PersistedEventLabels.type` is gone; both projections now read the row's own guard through a pure,
framework-free mapper (`PersistedEventReader.kt:76`):

```kotlin
internal fun persistedEventTypeOf(type: SecurityEventType): EventType = when (type) {
    SecurityEventType.MOTION_CONFIRMED_EVENT -> EventType.Motion
    SecurityEventType.PANIC_ACTIVATION -> EventType.Panic
    SecurityEventType.CONFIRMED_SECURITY_EVENT -> EventType.Other
}
```

The `when` is exhaustive over the three values the domain enum actually has. **No new domain event
type was invented.** `EventType.Other` is a *presentation-only* bucket for the pre-existing generic
`CONFIRMED_SECURITY_EVENT`; it is reachable under the "All" filter and under no type chip, so a
generic row can never hide inside Panic's filter.

Tests: `a_persisted_motion_event_projects_as_motion`, `a_persisted_panic_activation_projects_as_panic`,
`every_persisted_guard_projects_to_its_own_history_type_and_none_to_another_s` (maps **all**
`SecurityEventType.entries` and asserts no two guards collapse onto one history type),
`a_reader_wide_label_can_never_retype_a_row`, `the_event_detail_keeps_the_same_guard_type_as_the_list`.

### B — a real Motion write refreshes the event lists

`MotionEventWrites` (`com.shieldra.runtime`) is a process-local `MutableStateFlow<Long>` that the
service bumps **only** on `MotionConfirmation.Recorded`; `ShieldraNavHost` keys its `produceState`
on it. No bus, no polling, no timer, no WorkManager, no persistence observer framework, and no
change to persistence semantics — the smallest mechanism the existing architecture already had.
A `BoundToActive`, `NotConfirmed` or `Rejected` result bumps nothing, because no row moved.

### C — onboarding says what the build does

`welcome_body` (EN and AR) no longer says continuous monitoring "is not part of this build yet" and
does not claim 24/7, guaranteed protection, all guards active, or an always-protected device. It now
states the two things that are true: what you trigger is recorded and stored on this device, and
Motion monitoring can be started from the dashboard. It claims nothing belonging to SIM, Lock,
camera, location or any future guard. Gate §17 fails the build if the stale sentence returns.

### D — the dashboard disclosure follows the Motion guard's own truth

`SettingsContent.dashboardMode(storageInstalled, motionMonitoring)` gains
`LocalEventsWithMotion`, and `DashboardScreen` derives `motionMonitoring` **once** from the Motion
tile's real status and feeds that single value to both the disclosure and the Start/Stop action.
There is no second `isMonitoring` boolean, the disclosure is not derived from a press, and no
monitoring state is duplicated in the presentation layer. While the service runs the disclosure says
"Motion monitoring is running"; when it does not, it says "No continuous monitoring is running yet."

---

## 3. THE RED → GREEN CHAIN, INCLUDING THE TWO FAILURES THAT WERE MINE

| Commit | CI run | Verdict | What it proves |
|---|---|---|---|
| `93a885f` RED | `36937454998` | **failure** | Fails at `:app:compileDebugUnitTestKotlin` on `Unresolved reference 'persistedEventTypeOf'`, `'MotionEventWrites'`, `'LocalEventsWithMotion'`, `No parameter with name 'motionMonitoring'`, `Unresolved reference 'Other'` — i.e. confined to the symbols GREEN was to create. Per the standing ruling, that is the accepted RED signal and no further analysis cycle was run |
| `3fd8abc` GREEN | `36938339807` | **failure** | `PersistedEventReaderTest.kt:250` passed a computed `Int` to `Duration.ofMinutes`. A defect in a test I wrote, not in the four fixes |
| `2bc6143` | `36939060424` | **failure** | `SettingsContentTest > only_the_monitoring_mode_claims_monitoring` used `mode.takeIf { it.claimsMonitoring }`, which is `null` for every non-claiming mode. Again a defect in my own assertion |
| `18cc745` | `36939853591` | **success** | 332/332, lint, `assembleDebug`, `assembleDebugAndroidTest`, Group C boundary scan, G4-B static, Phase 3 static |

Two of the four CI runs in this slice were spent repairing my own test code. Recording that is part
of the report, not an embarrassment to edit out.

---

## 4. THE TEN-POINT RMX3910 SEQUENCE — EVERY POINT, WITH WHAT WAS SEEN

A fresh install was required before this run: a stale non-terminal `MOTION_CONFIRMED_EVENT` from an
earlier session would have made any new confirmation return `BoundToActive`, persist nothing and bump
no token — which would have "verified" nothing. The uninstall was disclosed before it happened.

| # | Point | Evidence actually seen |
|---|---|---|
| 1 | Start Motion | `dumpsys activity services` → `ServiceRecord{… com.shieldra.app/com.shieldra.detection.MotionMonitoringService}`, `isForeground=true foregroundId=20002 types=0x40000000` (`specialUse`), `flags=ONGOING_EVENT\|FOREGROUND_SERVICE`, `channel=shieldra_motion_monitoring` |
| 2 | Motion displays Active | Tile `content-desc="Motion Guard. Motion monitoring is active. Status: Active."` |
| 3 | One Motion confirmation fires | One physical shake (~4 s) while the monitor ran |
| 4 | History shows it **without restarting the app** | The row appeared in the open History screen. `ps -A` shows `com.shieldra.app` at **pid 26771**, `/proc/26771` start time **2026-10-02 01:36:34** — the same process that was alive before the shake. No restart, no re-navigation, no pull-to-refresh |
| 5 | Its type is Motion, not Panic | The new row renders the running-person icon (`EventType.icon()` → `EventType.Motion -> Icons.AutoMirrored.Filled.DirectionsRun`) and appears under the **Motion** filter chip; under the Panic chip it is absent |
| 6 | The Panic event remains Panic | A Panic triggered from the dashboard quick action renders the warning icon and appears under the **Panic** chip only; under Motion the list reads "No events match" |
| 7 | Cold-start onboarding has no stale sentence | Onboarding read on a cold start; the sentence is gone in both languages |
| 8 | While active the disclosure does not deny monitoring | Disclosure reads exactly "Security events are recorded and stored on this phone. Motion monitoring is running." |
| 9 | Stop Motion | Foreground-service count drops to 0; `shieldra_motion_monitoring` notification records drop to 0 (only the channel definition remains) |
| 10 | UI returns to inactive state and wording | Tile `content-desc="Motion Guard. Not monitoring yet. Status: Disabled."`, button back to "Start Motion monitoring", disclosure back to "Security events are recorded and stored on this phone. No continuous monitoring is running yet." |

Point 4 is the one that could not have been proven any other way, and it is the reason fix B exists.

---

## 5. WHAT WAS DELIBERATELY NOT TOUCHED

The owner's do-not-touch list held, and `git diff --stat 93a885f..18cc745` is the proof: the only
production files this slice changed are `PersistedEventReader.kt`, `PresentationModels.kt`,
`ShieldraIcons.kt`, `ShieldraNavHost.kt`, `DashboardScreen.kt`, `SettingsContent.kt`,
`MotionMonitoringService.kt` (one line: raise the token), the new `MotionEventWrites.kt`, and the two
`strings.xml`. Threshold `4.6`, noise floor `0.8`, `minEpisodeMs 500`, `maxSampleGapMs 100`,
`requestedReportPeriodUs 20_000`, `stallToleranceFactor 11.0`, G1-02's confirmation policy, Motion
deduplication, FGS type and permissions, notification-permission policy, service lifecycle, reboot
behaviour, battery policy, Panic semantics, storage encryption, camera/location and the SIM/Lock
detectors are all byte-for-byte unchanged. **No authority amendment was needed and none was written.**

---

## 6. FINDINGS RECORDED, NOT FIXED (they are outside the four authorized fixes)

1. **The dashboard's empty recent-events state still says "No events yet" / "Demo events will appear
   here when they are provided to the presentation shell."** (`empty_events_title` /
   `empty_events_message`, `values/strings.xml:24-25`.) Storage is installed and real events
   exist, so "demo" is false wording under §31.
2. **History's filtered-empty state says "Try adjusting the filters for the demo events provided to
   this screen."** (`empty_history_message`, same file, line 27.) Same defect, second string.
3. **`EventDetailScreen.kt:157` renders the field labelled "Type" with `model.title`.** The detail
   model's `type` is now correct and is pinned by `the_event_detail_keeps_the_same_guard_type_as_the_list`,
   but the screen never displays it — the row under the "Type" label is the title. Fixing this is a
   fifth string/widget, not one of the four, so it waits.

All three are wording/widget truthfulness, they belong to the same family as fixes C and D, and they
should land as the next truthfulness slice rather than be smuggled into this one. The two "demo"
strings exist in **both** languages (`أحداث العرض` / `الأحداث التجريبية` at `values-ar/strings.xml:25,27`),
so a fix is four strings, not two.

---

## 7. WHAT G5 DOES **NOT** CLAIM

- **No 24/7 reliability claim.** The monitoring service runs while the user leaves it on and while
  the platform lets it run. That is not the same sentence as 24/7, and it is not said anywhere.
- **No universal OEM survivability claim.** The RMX3910 measurements already on record (screen dark ⇒
  the process receives no CPU; a 4 500-entry queue the platform keeps undrained) say the opposite
  about this OEM, and they stay in the file.
- **The start-latency / CPU / battery / screen-off campaign stays evidence, not a correctness gate** —
  the owner's ruling. It is not re-opened here and it does not block this closure.
- **No delivery.** Every event on the device today reads `Skipped`; §30 forbids an external channel,
  so "recorded and stored on this phone" is the whole truth the UI is allowed to tell.
- **Motion is the only guard that monitors.** Lock, SIM, Battery and Panic tiles read
  "Not monitoring yet / Disabled", and that is accurate.

---

## 8. STATUS

| Item | Status |
|---|---|
| Fix A — per-event History type | **DONE** — pure mapper, exhaustive over the three real domain values, 5 JVM tests |
| Fix B — Motion-triggered refresh | **DONE** — process-local token, device-proven with the process alive |
| Fix C — onboarding copy | **DONE** — EN + AR, pinned by gate §17 |
| Fix D — disclosure from `motionClaim` | **DONE** — one derived value, no second boolean |
| JVM tests | **332 / 332** on `18cc745`, run `36939853591` |
| lint / `assembleDebug` / `assembleDebugAndroidTest` | **PASS** in that run |
| Static gates (Group C, G4-B, Phase 3) | **PASS** in that run |
| Device verification | **10 / 10 points** on RMX3910, evidence above |
| Do-not-touch list | **UNCHANGED** — verified by diff |
| Authority amendment | **NONE** — none required, none written |
| Remaining findings | 3 wording/widget findings, §6, for the next truthfulness slice |

**G5 is closed on this evidence.** Next in the owner's sequence: **Batch H — decisions package only**
(no camera or location code, no invented owner policy), then I (SIM feasibility), J (Lock feasibility +
current official Play requirements), K (final security/privacy/device audit), L (release hardening).
