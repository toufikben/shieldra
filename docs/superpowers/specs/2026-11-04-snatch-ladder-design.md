# A-45 — Snatch ladder, safe-zone muting, multilingual theft notice (design)

Date: 2026-11-04 · Branch: `fix/audit-round-1` · Owner approval: given in the same session, all four
decisions taken as recommended (ladder as described, sensitivity measured on the device first, safe
zone mutes escalation not detection, notice languages = AR + EN + FR).

## 1. What is measured today (not assumed)

| Fact | Value | Source |
|---|---|---|
| Sensor in use | `TYPE_ACCELEROMETER` only, requested 20 000 µs; delivered rate measured from timestamps | `detection/MotionSensorAdapter.kt:46,117`, `detection/ApprovedMotionCalibration.kt:23` |
| `TYPE_SIGNIFICANT_MOTION` | fetched for availability, **never registered** | `MotionSensorAdapter.kt:47,52-53` |
| Movement rule | episode opens at deviation ≥ 4.6 m/s², closes at ≤ 0.8, kept when ≥ 500 ms | `ApprovedMotionCalibration.kt:11-20` |
| Confirmation | one episode ≥ 3 000 ms **or** 2 episodes within 10 000 ms | `domain/GuardContracts.kt:80-83`, `domain/ProtectionStateEngine.kt:79-82` |
| Still-then-move precondition | **does not exist**; no tilt measurement, no noise floor | every file under `detection/` |
| User-facing sensitivity | **does not exist** (only the guard switch, 3 response chips, a 3–30 s grace slider) | `presentation/screen/settings/ProtectionSetupScreen.kt:212-277` |
| Repeat movement inside the open incident | binds and triggers **no response at all** | `domain/MotionIncidentBinder.kt:29,73-76`, `detection/MotionMonitoringService.kt:265-268` |
| Response at confirmation | default `SILENT`: capture (photo+fix) then `lockNow` in the same call | `protection/TableModeResponder.kt:44-53,85,92-98`, `protection/ProtectionSettings.kt:21` |
| Grace mode | 5 s default, **invisible to whoever holds the phone**; a valid unlock cancels everything | `TableModeResponder.kt:56-70,126-132` |
| Post-incident trail | location only, every 2 min for 30 min, widened ×2 at ≤30 % and ×4 at ≤15 % battery — but `liveTrackingEnabled = false` by default | `ProtectionService.kt:151-180`, `protection/TrackingCadence.kt:17-35`, `ProtectionSettings.kt:25-27` |
| Siren escalation | `escalationEnabled = false` by default | `ProtectionSettings.kt:29`, `protection/EscalationPolicy.kt:13-18` |
| Lock-screen notice | a public notification shown **only** by `/lost`, never by Motion, never re-shown after boot; the owner's free text is stored verbatim and rendered in one language | `protection/LostNotice.kt:15-39`, `protection/RemoteCommandPoller.kt:102-110`, `ProtectionSettings.kt:124-125` |
| Locales shipped | `values` (en) and `values-ar` only | `res/xml/locales_config.xml`, `ls app/src/main/res` |
| Safe zones | exist (100/200/500/1000 m, default off) and **suppress nothing**; they send one text alert on a confirmed crossing out | `protection/GeofenceLogic.kt:21-24,60-82`, `ProtectionService.kt:100-134` |
| Zone sampler mechanics | in-process `Handler` every 3 min, `wasInsideSafeZone` in memory only, silent no-op without a fix, stale `lastLocation` accepted | `ProtectionService.kt:95-134`, `protection/LocationFix.kt:37-43` |
| Background location permission | **not needed** for this work: `ProtectionService` already runs with `foregroundServiceType="camera|location|microphone"` | `app/src/main/AndroidManifest.xml:111` |

## 2. Decisions

**D1 — the ladder replaces the single shot.** A confirmed Motion now runs, in order:
`t=0` lock the screen + post the theft notice with a live timer + send a text-and-location alert.
No camera at `t=0`: a frame taken while the holder is running is a blur and the owner is better
served by the alert arriving immediately.
`t=30 s` (no owner unlock) first evidence burst: photo + short audio + fix, attached to the incident.
Then a burst every **60 s, five times**, then every **5 min** until **30 min** from the incident start
or until disarming. A movement that binds to the open incident **re-runs the ladder** instead of
producing nothing, so a phone still being carried keeps producing evidence.
The siren stays an opt-in chip; it is not part of the default ladder.

Why lock-first and why no automatic siren: Google's Theft Detection Lock locks the screen at the
theft attempt (security.googleblog.com, 2024); Bhardwaj et al., USENIX Security 2025, report 5 of 20
victims chased or confronted the attacker, so an automatic loud alarm escalates danger to the person.
The 30 s demand window matches ESET Anti-Theft's 30 s correction window, and the repeat cadence
follows Prey's real constants (scheduled reports every 5 min, tightened to 10 s/400 ms while the
device is flagged missing).

**D2 — fail closed when the zone is unknown.** Safe zones mute *escalation*, never *detection*:
inside a zone and charging → log the event, no lock, no notice, no burst (night stand, desk);
inside a zone and not charging → lock and send the quiet alert and evidence, but no siren and no
repeated trail; outside a zone, or when the zone state is unknown or stale → the full ladder with
zero grace. Detection inside a zone is kept because the most valuable theft — a phone taken from a
bedside table — happens inside one.

**D3 — the notice is composed by the app in three languages, not by the owner in one.** The holder of
the stolen phone does not open the owner's settings, so a free-text Arabic sentence is unreadable to
most finders. The app composes the notice itself from its own resources in AR + EN + FR, repeats the
contact once, and adds a call action; the owner's own sentence is appended when he writes one. The
notice is shown by the ladder, cleared on owner unlock and on disarm, and re-shown after a reboot.
Android 14 revokes full-screen-intent for apps without calling or alarm functionality
(source.android.com/docs/core/permissions/fsi-limits), so this stays a public heads-up notification
with a live chronometer, not a takeover activity.

**D4 — sensitivity is measured on the owner's handset before any constant moves.** A single force
threshold cannot give both recall and precision: Liu, Wagner & Egelman (ICISS 2018) gated on
|a| > 40 m/s² at 100 Hz and reported 0.09 % false positives at 100 % detection of 120 simulated
thefts, but that work explicitly does not cover "left unattended and walked off with"; fall-detection
surveys (Stampfler et al., Frontiers in Public Health 2022) show sensitivity 60.4–99.3 % against
specificity 74.6–100 % for thresholds above 2 g and 3 g at 40–60 Hz. The published low-power
alternative is a gravity-direction test: AOSP's tilt detector fires when the 2 s average gravity
vector changes by ≥ 35°. The instrumented calibration on this very device still prints
`q_g1=UNANSWERED shipped_strong_threshold=false`, so the shipped number is not evidence-backed yet.
Therefore: ship a recorder, collect real lift/snatch samples on the Realme RMX3910, then set the
constants from what was measured.

**D5 — no new permission is requested.** Tilt and linear acceleration need no permission; the
foreground service already carries `location`; exact alarms are already declared
(`SCHEDULE_EXACT_ALARM`). `ACTIVITY_RECOGNITION` (walking vs in-vehicle discrimination) is deliberately
out of scope for A-45 and stays a separate owner decision.

## 3. Constants this design fixes

| Name | Value | Note |
|---|---|---|
| `DEMAND_MS` | 30 000 ms | time from lock to the first evidence burst |
| `BURST_INTERVAL_MS` | 60 000 ms | fast phase |
| `FAST_BURSTS` | 5 | after these the cadence slows |
| `SLOW_INTERVAL_MS` | 300 000 ms | 5 min |
| `LADDER_WINDOW_MS` | 1 800 000 ms | 30 min from incident start, then settled |
| `ZONE_STALE_MS` | 2 × `geofenceIntervalMinutes` | unknown or older than this ⇒ treated as outside |
| `THEFT_DEMAND_NOTIFICATION_ID` | 20 021 | distinct from the lost notice's 20 020 |
| Notice locales | ar, en, fr | `values-fr` added; `locales_config.xml` gains `<locale android:name="fr"/>` |

## 4. Explicitly out of scope for A-45

Any change to the shipped motion thresholds (they wait for D4's measurement); `ACTIVITY_RECOGNITION`;
Android `GeofencingApi` migration; per-zone editing UI; real billing tiers (still PREMIUM in code);
the signed release upload, which the owner deferred.
