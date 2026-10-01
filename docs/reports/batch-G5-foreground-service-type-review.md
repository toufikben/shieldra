# Batch G / slice G5, step 1 — which Android foreground-service type SHIELDRA's monitoring service actually is

**This is a review, not an implementation.** No `<service>`, no `FOREGROUND_SERVICE`, no
`FOREGROUND_SERVICE_SPECIAL_USE`, no `foregroundServiceType`, no `<property>`, no notification
change, no runtime monitoring activation and no permission of any kind was added by this commit.
`app/src/main/AndroidManifest.xml` still declares exactly one permission (`POST_NOTIFICATIONS`,
amendment A-6), `g4b-review-check.sh` §18 still fails the build if any of the forbidden FGS tokens
appears, and the dashboard still reads **"Motion Guard — Not monitoring yet"**. Nothing was
weakened to make a future slice easier to land.

## WHY THIS STEP COMES FIRST

Amendment A-8 rules that `specialUse` *"is chosen only if nothing more specific fits"*, and
amendment A-15 restates it as condition (7): *"`specialUse` is not a pre-decision: it is chosen only
if the repository and API review confirms that no more specific FGS type fits the actual
motion-monitoring case."* The owner's Q-G1 answer added the correction verbatim: **"لا نختار `specialUse`
بالاسم قبل مراجعة نوع الخدمة الفعلي"** and fixed the order inside the slice as
**API/type review → Service → user start/stop → ongoing notification → wiring the Motion detector**.

So the review has exactly one job: enumerate every type Android requires an app to choose between,
quote what each is documented to be for, and eliminate each one on the record. Whatever it lands on
is a finding shown to the owner, not a decision this file takes.

Q-G1's strong half was approved first and is recorded as **amendment A-16** (`strongDeltaMs2 = 4.6`,
G1-02's time rule unchanged, `MotionCalibration` still injectable) — that approval is what unmutes
this slice, and A-16 explicitly does **not** relax any gate.

## THE FACTS THAT CONSTRAIN THE CHOICE

| Fact | Value | Where it comes from |
|---|---|---|
| App SDK levels | `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` | `app/build.gradle.kts:9-14` |
| Test device | RMX3910, Android **16**, SDK **36** | `dumpsys` captured in the Q-G1 evidence |
| Permissions declared today | one: `POST_NOTIFICATIONS` | `AndroidManifest.xml` |
| What the service would do | read the accelerometer continuously through `MotionSensorAdapter`, judge it with `MotionWindowEvaluator`, hand the observation to the already-shipped `DefaultProtectionStateEngine`, and show one ongoing notification | A-15 + G1/G3/G4 slices as they stand |
| Sensor shape measured | `sc7a20` accelerometer, continuous, **non-wakeUp**, 10–200 Hz; requested 20 000 µs delivered ~111 Hz median interval 9.0 ms | `docs/reports/batch-G3-motion-sensor-adapter.md`, Q-G1 harness `ENVIRONMENT` |
| What it must never need | camera, location, microphone, phone state, network, boot auto-start | §21–§23, A-10 |
| Who starts it | the user, from a visible screen inside the app, and the user stops it | A-8, A-15 (1)(2) |

Because `targetSdk = 35`, **both** rule sets apply: the Android 14 (API 34) "a type is mandatory"
requirement, and the Android 15 (API 35) background-start and per-type timeout tightening. And
because the device runs Android 16, the Android 16 changes apply to SHIELDRA **regardless of target
level** — the relevant one being that background work *scheduled from* a foreground service now
obeys its runtime quota (this design schedules no `JobScheduler`/WorkManager work; Batch F's gate
forbids a WorkManager detector, so that change costs nothing here and must not be traded for one).

Android throws `MissingForegroundServiceTypeException` if a service is started in the foreground
with no type at target 34+, and `SecurityException` if the type's permission is missing — so the
type is not a paperwork choice made at release time; it is the first thing the code needs.

## THE TYPE-BY-TYPE ELIMINATION

Quoted purposes are the platform's own wording (see Sources). The verdict column is the review's
conclusion; the reason column is what in the documented definition forces it.

| Type | Documented purpose (abridged quote) | Precondition the platform enforces | Verdict for SHIELDRA |
|---|---|---|---|
| `camera` | "continued camera access in background" | runtime `CAMERA`, while-in-use restricted | **No** — §21 forbids camera collection in these batches; there is no camera code to keep alive |
| `location` | "long-running use cases requiring location access (navigation, location sharing)" | location services on + `ACCESS_COARSE/FINE_LOCATION` | **No** — §22 forbids location collection; SHIELDRA asks no location permission and must not acquire one to satisfy a type |
| `microphone` | "continued microphone use in background" | runtime `RECORD_AUDIO` | **No** — audio recording is explicitly not approved |
| `phoneCall` | "continue an ongoing call using ConnectionService APIs" | `MANAGE_OWN_CALLS` or default-dialer role | **No** — SHIELDRA places no calls; ConnectionService is not in the design |
| `connectedDevice` | "interactions with external devices requiring Bluetooth, NFC, IR, USB or network connections" | one of the Bluetooth/NFC/IR/network declarations | **No** — the sensor is the phone's own, not a connected device; claiming this type would mis-describe the use case to the platform |
| `mediaPlayback` | "continue audio or video playback in background" | none | **No** — SHIELDRA plays no media |
| `mediaProjection` | "project content to an external screen with MediaProjection" | `createScreenCaptureIntent()` grant first | **No** — no projection, and no screen capture anywhere in the app |
| `mediaProcessing` | "time-consuming operations on media assets (e.g. format conversion)" | none, but a per-24h time limit applies on Android 15+ | **No** — there is no media asset to process; the time limit would also cap a monitoring service that must run as long as the user leaves protection on |
| `dataSync` | "data transfer operations: upload/download, backup and restore, import/export, local file processing" | none, **plus a new Android 15 time limit** | **No** — reading a motion sensor is not a data-transfer operation, and the documented type would carry a timeout SHIELDRA cannot accept. Choosing it to avoid a Play review would be exactly the "type that does not match the use case" A-8 forbids |
| `remoteMessaging` | "transfer messages between devices" | none | **No** — no external channel is approved (§30); no SMS/email/cloud/push exists in the app |
| `shortService` | "finalize essential tasks quickly that cannot be interrupted or postponed (~3 min max), incompatible with persistent services" | plain `FOREGROUND_SERVICE` | **No** — monitoring is by definition longer than ~3 minutes and must survive as long as the user leaves it on; `START_STICKY` is disallowed for this type |
| `health` | "long-running use cases supporting fitness applications (e.g. training coaches, exercise trackers)" | declare `HIGH_SAMPLING_RATE_SENSORS` **or** hold a health/body-sensor/`ACTIVITY_RECOGNITION` runtime permission | **No — and this is the only genuinely close candidate, so it is worth stating why.** Its shape (continuous sensor work) does resemble ours; its *documented purpose* is fitness/exercise tracking, and SHIELDRA's monitoring is anti-theft security, not a workout. Taking this type would require either asserting a fitness use case to Google or adding `ACTIVITY_RECOGNITION`, a runtime permission that §23 and A-6 forbid because the feature does not need it. (Note also that the sampling-rate declaration is about rates above the platform's 200 Hz ceiling concern; the measured path runs ~111 Hz, so even the sensor-shape argument for `health` is weak.) |
| `systemExempted` | "reserved for system apps and specific system integrations" — demo mode, device owner, profile owner, `ROLE_EMERGENCY` security app, device admin, exact-alarm holders, configured VPN | matching one of those criteria, else `ForegroundServiceTypeNotAllowedException` | **No** — SHIELDRA is none of them. An ordinary Play app can technically qualify by holding `SCHEDULE_EXACT_ALARM`, but that would be buying a type with a permission the product does not use; A-8's "never add permission-only code" bars it |
| `specialUse` | "valid foreground service use cases not covered by any other type" | `FOREGROUND_SERVICE_SPECIAL_USE` + a `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">` explanation + Play Console review | **The only type whose own definition covers the case.** Continuous on-device motion monitoring for theft detection is not messaging, not media, not a transfer, not fitness, not a device pairing, and not a system role |

## WHAT THE CONCLUSION COMMITS TO IF THE OWNER CONFIRMS IT

`specialUse` is not a free slot. The platform and Play both attach obligations, and they are part of
the design, not a later chore:

1. Manifest: `<service … android:foregroundServiceType="specialUse">` plus
   `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="…"/>`.
2. Permissions: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` — the first *second*
   `<uses-permission>` in this app's history after `POST_NOTIFICATIONS`, which is why
   `g4b-review-check.sh`'s "exactly one `<uses-permission>`" assertion must move **in the same
   recorded amendment as the service**, never silently.
3. Play Console: apps targeting API 34+ must declare the foreground-service types they use (Rules →
   App contents) and a `specialUse` declaration is reviewed on the strength of the `<property>` text.
4. Runtime: the type's prerequisites are checked *before* `startForeground()`; getting this wrong
   throws, it does not degrade quietly — which is why the slice must be device-verified on RMX3910,
   not just compiled.
5. Nothing about `specialUse` changes A-9's wording duty: while the service really runs, the ongoing
   notification says **"SHIELDRA protection is active"** — no "24/7", no "guaranteed".
6. Nothing about `specialUse` re-opens A-10: no `RECEIVE_BOOT_COMPLETED`, no boot start. The Android 15
   `BOOT_COMPLETED` type restrictions are therefore moot for this slice.
7. Start path stays inside the documented exemption: the user taps in a visible SHIELDRA screen.
   Android's background-start exemption list includes *"the user performs an action on a UI element
   related to your app"* and *"your app transitions from a user-visible state"*, which is exactly the
   A-15 (1) requirement — so SHIELDRA needs **no** `SYSTEM_ALERT_WINDOW` and no overlay trick.

### DRAFT subtype explanation — NOT OWNER APPROVED, not in any manifest

Play-facing copy, offered so the owner can edit the claim rather than write it from scratch:

> `Continuous on-device motion monitoring for the user's own anti-theft protection. While the user
> keeps SHIELDRA's protection switch on, the app reads the accelerometer locally to decide whether
> the phone was moved or snatched, and shows an ongoing notification while it does. No other
> foreground-service type fits: the service collects no location, uses no camera or microphone,
> transfers no data, plays no media, pairs with no accessory, and is not a fitness tracker.`

## WHAT THIS REVIEW DOES NOT DECIDE

- **The remaining four calibration numbers.** `MotionCalibration` needs `strongDeltaMs2` (approved:
  4.6), `noiseFloorDeltaMs2`, `minEpisodeMs` and `maxSampleGapMs`, and the adapter still takes an
  injected `requestedReportPeriodUs`. A-16 leaves those to the owner, so no shipping
  `MotionCalibration(...)` literal may appear in `app/src/main` yet. §37 makes the meaning of a
  guard the owner's, not an engineer's.
- **The start/stop surface** — which screen holds the switch, what it says before and after, what a
  refused notification permission does to it (A-6: a refusal never blocks recording).
- **Whether Motion may ever read `Active`.** Not until the service exists and is proven on the
  device, and then only through the real state, never from the execution layer alone
  (`MonitoringClaim` stays in force).

## STATUS

| Item | Status |
|---|---|
| Type review | **DONE — documented above, outcome presented for the owner's confirmation** |
| Manifest / Kotlin changes in this slice | **NONE** (verified with `git show --name-only`) |
| Gates | **UNCHANGED** — §15, §16, §18, §19 and `group-c-contract-check.sh` all still enforce what they enforced before this commit |
| FGS code | **NOT RUN / NOT WRITTEN** |
| Blocking inputs | the owner's four calibration numbers + confirmation of the type + the G5 design approval |

Order that follows A-16 and the owner's own sequence: **owner answers the remaining numbers → G5
design presented and approved → amendment recorded that moves the gate in the same commit as the
service → `Service` + start/stop → ongoing notification → wire the detector → CI + device verify.**

## SOURCES

- Foreground service types required (Android 14 / API 34):
  <https://developer.android.com/about/versions/14/changes/fgs-types-required>
- Foreground service types, per-type permissions and preconditions:
  <https://developer.android.com/develop/background-work/services/fgs/service-types>
- Changes to foreground services (Android 15 / 16):
  <https://developer.android.com/develop/background-work/services/fgs/changes>
- Background-start restrictions and exemptions:
  <https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start>
- Google Play's foreground-service policy page returned **404** when read on 2026-10-01, so it is
  **not** quoted here; the Play Console declaration requirement cited above comes from the
  `fgs-types-required` page.
