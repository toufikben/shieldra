# Motion as a guard — lock on a confirmed lift or snatch (amendment A-43)

Date: 2026-11-04 · Branch: `fix/audit-round-1` · Status: code complete, CI and device
verification recorded below (signed release deliberately deferred by the owner)

## 1. The owner decision

The Motion guard used to be a separate opt-in: a dashboard toggle, plus a settings flag that let
arming start it. On 2026-11-04 the owner asked for it to be a guard like the lock and SIM ones —
always on — and for its active state to lock the phone when it is snatched, if the change is
appropriate and logical. It is, so it is implemented and recorded as amendment A-43 in
`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`.

Two facts make the design safe to turn on by default:

- The guard leaves the response, the grace window and the switch in the owner's hands, exactly as
  the lock and SIM guards do. Turning it off while protection is armed weakens protection, so it
  asks for the owner's screen lock (`ProtectionChange`).
- The screen lock power is not new. It is the same credentialed administrator A-42 introduced
  (`watch-login` + `force-lock`, one guarded `lockNow()` caller). Motion only adds a second reason
  to use an already-approved, easily revocable power. No permission is added and the Play policy
  argument in `docs/reports/audit-round-1-force-lock.md` is unchanged.

## 2. What the owner controls

| Setting | Default | Meaning |
|---|---|---|
| Motion guard (`motionEnabled`) | **on** | On by default like the lock and SIM guards. Arming starts it; disarming stops it; a reboot with protection armed resumes it. |
| Response: Silent capture | selected | Capture a photo and a fix quietly; lock the screen at once. |
| Response: Loud siren | | The same, plus the siren; lock the screen at once. |
| Response: Grace then siren | | Wait the grace window. The owner's own unlock during it cancels the siren **and** the lock; if nobody unlocks, the siren sounds and the screen locks. |

The grace response is the answer to the obvious false positive — picking your own phone up from a
table. The immediate responses are for an owner who would rather a mistake lock the screen than let
a snatch through. Nothing here locks on its own without a confirmed Motion incident.

The retired `motion_with_arming` key is not read. Its old default was false and every settings save
persisted it, so it cannot be told apart from a deliberate off; carrying it forward would leave
existing installs with the guard off, the opposite of this decision. `motion_enabled` starts at true
and is the only switch.

## 3. Lifecycle

- **Arming** (`ProtectionRuntime.arm`) records the flag, starts `ProtectionService`, schedules the
  countdown and the patrol, and starts Motion monitoring when the guard is on. The platform facts
  still gate the start: the A-6 notification permission and installed storage. A refusal (or an
  unanswered permission) is logged and leaves arming intact; the dashboard keeps saying the truth.
- **Disarming** stops `ProtectionService`, cancels the countdown and patrol, stops Motion monitoring,
  and cancels a grace response that is still waiting — a response queued before the disarm must not
  siren or lock a phone the owner just chose to stop watching.
- **Reboot** (`BOOT_COMPLETED`, after the first unlock) re-arms protection and resumes Motion with
  it, only while armed and enabled. The locked-boot half still only records the interruption; it
  starts nothing.
- **Saving the settings while armed** starts the monitor when the guard was switched on and stops it
  when it was switched off, so the saved flag cannot disagree with what is running.

## 4. The lock contract

`TableModeResponder` turns the response into a pure `MotionResponsePlan`:

| Response | Siren now | Wait for the owner | Siren after grace | Lock at once | Lock after grace |
|---|---|---|---|---|---|
| SILENT | no | no | no | yes | no |
| SIREN | yes | no | no | yes | no |
| GRACE_THEN_SIREN | no | yes | yes | no | yes |

`fire()` always requests the capture (through the live protection service, or the guard's own
text-only alert when protection is not armed) **before** it locks, because the camera and the fix
need the moment the confirmation bought them. The lock itself goes through
`DeviceScreenLock.now(context)` — the single `lockNow()` caller — and only while the stored `armed`
flag is true. The four honest outcomes are unchanged: `LOCKED`, `ADMIN_OFF`, `NEEDS_REGRANT`,
`FAILED`. A non-locking outcome is logged, never retried silently and never reported as a lock.

Two intentional limits:

- With protection disarmed, Motion can still be started by hand from the dashboard, but its lock is
  refused. The screen-lock power belongs to armed protection; disarming is the owner saying stop
  watching.
- A phone whose administrator was granted before A-42, or which has no administrator at all, records
  and reports the incident but cannot lock until the role is granted (and re-granted once, for a
  pre-A-42 grant).

## 5. Tests and gates

JVM tests:

- `MotionResponsePlanTest` — silent and siren lock at once; grace waits for the owner's unlock and
  only then sirens and locks.
- `MotionGuardDefaultsTest` — the guard is on by default like the lock and SIM guards.
- `ProtectionChangeTest` — switching the guard off weakens protection and asks for the screen lock.

`g4b-review-check.sh` pins A-43: the default and the retired key, the pure plan's lock outcomes, the
lock through `DeviceScreenLock`, the armed gate around it, arming/boot starting Motion, disarming
stopping it, the settings-save sync, `ProtectionChange`, and the named tests. The single
`lockNow()` caller pin is unchanged.

## 5b. The defect the phone found (amendment A-44)

Measured on build 391 (upgrade over 389, old `motion_with_arming=false` preserved):

1. Protection was armed from the Protection screen; `ProtectionService` (action ARM) and
   `MotionMonitoringService` started at 07:41:12.
2. The screen showed “Armed — guarding in the background”, but `shieldra_protection.xml` held
   `armed=false`, and `dumpsys activity services` showed only `MotionMonitoringService`.
3. At 07:42:12 the service logged `protection is no longer armed: this instance is standing down` —
   one heartbeat after arming. The stored flag, not the screen, decided.

The cause is older than A-43: the screen is composed with a settings snapshot whose `armed` was
false; pressing Save writes the whole snapshot through `ProtectionSettingsStore.save`, so changing
any guard (the Motion response included) silently disarmed protection while the UI kept saying
“Armed”. A-43's save-path Motion sync also used that stale flag, so the monitor kept running.

A-44 fixes it at the source: `save()` no longer writes `KEY_ARMED`; `setArmed` is the flag's only
writer. `ProtectionRuntime.applySettings` reads the live flag back for its scheduling decisions, and
the NavHost replaces the draft's `armed` with `protectionArmed` and follows the live flow. `g4b`
pins all three. Device re-verification after the fix is recorded below.

## 6. Verification

The three local static gates were run before the push: g4b (`PASS: A-43 …`), Group C boundary scan,
Phase 3 static review. CI and device results are appended here and in `docs/ROADMAP.md` after they
run; nothing in this section is claimed before it is measured.

- **CI:** run `41782160333` on `9c30867` — **success**. `CI TEST SUMMARY: 578 tests, 578 passed,
  0 failures, 0 errors, 0 skipped` (the four new JVM tests included); `BUILD SUCCESSFUL in 9m 41s`;
  `Group C boundary scan passed`; `PASS: A-43: Motion is on by default, arming and boot start it,
  and a confirmed lift locks through DeviceScreenLock`; `G4-B LOCAL REVIEW PASS`;
  `PHASE 3 STATIC REVIEW PASS`. The branch APK pair and the phone APK were published to the draft
  release `test-41782160333`; the signed release build succeeded but was not published, per the
  owner's instruction to defer the signed version until the app is fully verified.
- **Device on 391:** partial round, stopped by the A-44 defect above. Measured: the upgrade kept
  the old `motion_with_arming=false` and the Motion guard still rendered **on** with the new
  description; the administrator still held exactly `watch-login` + `force-lock`; the permission
  list was unchanged. The arming started both services once, and the Save-while-armed test is what
  exposed A-44.
- **CI (A-44):** run `41788801231` on `a710c40` — **success**. `CI TEST SUMMARY: 578 tests, 578
  passed, 0 failures, 0 errors, 0 skipped`; `Group C boundary scan passed`; `PASS: A-43 …`;
  `PASS: A-44: the armed flag has one writer; a settings Save can no longer disarm a running
  protection`; `G4-B LOCAL REVIEW PASS`; `PHASE 3 STATIC REVIEW PASS`. The debug pair and phone APK
  were published to `test-41788801231`; the signed release built but was not published.
### Device round on build 392

Build 392 was installed over 391 in place (versionCode 392, `lastUpdateTime` 2026-11-04 08:09:40),
with the same preferences: `motion_with_arming=false` was still present and `motion_enabled=true`.
Measured on the phone:

- **Default-on and copy:** the Protection screen showed the Motion guard **on**, with the full
  description in English and Arabic; Help showed the four guards on by default in both languages.
- **Arming:** pressing **Arm** on the Protection screen started `ProtectionService` (intent action
  `com.shieldra.protection.ARM`) and `MotionMonitoringService` together, and the ongoing
  notification read “SHIELDRA protection is active”. The dashboard claimed `PROTECTED`, Guards
  `4/5` (Panic not monitoring), and the Motion card read “Motion monitoring is active” — the claim
  comes from the running service, not from the button that started it. The settings screen and the
  dashboard agree on the guard's state; the dashboard's stop row says the owner's screen lock is
  required.
- **A-44 regression path:** the Protection screen was opened while protection was disarmed, armed
  from that same screen, and **Save** was pressed. The disarmed starting point was produced without
  the owner's screen lock by stopping the app and clearing only the stored `armed` flag; UI Disarm
  was not usable because it demands the owner's screen lock. `armed` stayed `true`, both services stayed
  alive, the screen still read “Armed — guarding in the background”, and a check after one
  heartbeat found the same two ServiceRecords and no “standing down” line. The old code wrote the
  stale `armed=false` snapshot here; the fix holds.
- **Policy and permissions:** the administrator still holds exactly `watch-login` + `force-lock`
  (no wipe); the requested-permission list is identical to build 391's.
- **Instrumentation:** `run finished: 78 tests, 0 failed, 0 ignored`. The runner's line was read
  live from `logcat` at 09:33:07; the adb `-w` summary was lost when the command was moved to the
  background, and another process later cleared the log buffer, so the events buffer's
  `am_kill … due to finished inst` at 09:33:08 is kept beside it as the dispatch record.

**NOT RUN on the device, deliberately:** **Disarm** and switching the Motion guard off (both ask
for the owner's screen lock, which the owner's protocol forbids entering), the dashboard's stop
direction (same owner check), and the physical snatch/lock (it would leave the shared phone at the
lock screen and a real still-then-lift cannot be produced from here). These paths are covered by
the `ProtectionChange` tests, `MotionResponsePlanTest` and the g4b pins. Two environment facts are
recorded for honesty: the phone was rebooted at 08:35 by someone else (the app's own restart alert
in the owner's channel is the timestamp), and another app repeatedly held the foreground, including
one Telegram session that took it between a UI dump and a tap. The stored response read `SILENT` at
08:40, `SIREN` at 08:56 and `SILENT` again after the instrumented suite; this round tapped no
response chip before the `SIREN` reading, so that change came from outside it (the phone was in
other hands between steps, and the instrumented settings tests write their own fixtures). It does
not affect the armed-flag assertion, which is read from storage and from the service records.

## 7. A-45 — the snatch ladder, the safe zone, and the notice a stranger reads

Date: 2026-11-04 (design approved the same day) · Branch: `fix/audit-round-1` · Status: code and
static gates complete; the phone round and the motion constants are open, and nothing below claims a
device result it does not have.

### 7.1 What the owner reported after using the guard

Four observations, in the owner's own words, each one a real defect in what A-43 shipped:

- «ضع رقم هاتف او تلميح لوضع هاتف او ايمايل وايضا بالنسبة للجملة القصيرة ويجب ان تكون بكل اللغات حتى
  تفهم امر اخر» — the contact line needs a hint about what to write, and the short sentence has to be
  readable by *someone else*, in every language.
- «لاحضت ان حساسية التحريك للهاتف يحب ان تكون قوية بعض الشيء هل يمكنك القيام بتحسينها وقم بالبحث في
  المسالة عن افضل حساسية ممكنة» — a lift has to be moved deliberately before the guard notices.
- «لاحضت ان بتفعيلها يتم التقاط الاشارة مباشرة … فرضا ان السارق قد خطف الهاتف من الضحية لن تكون نافعة
  جدا … بعد الحركة يتم غلق شاشة الهاتف ثم اصدار رسالة بضرورة فتح الهاتف وتعطيل الاشارة ان لم يتم مثلا
  في وقت معين اكيد ان الهاتف قد سرق حقا ويتم تصعيد الامر الى الارسال الاشارة الكاملة يعني موقع صور
  ….الخ بشكل متكرر وفي اقل وقت كل 5 د او ما تراه افضل» — the picture taken at the moment of the snatch
  is worthless, and what is wanted is lock → demand → repeated evidence.
- «تفعيل موقع مثلا في بيت امن يعني او العمل هل يتم تعطيل الاشارة حتى لا يتم ازعاج المستعمل في بيته …
  ويتم اعادة تفعيل الحمايات عند الخروج من المنطقة، ماهو المنطق» — what should a home or work zone do.

### 7.2 What was measured first, before any of it was designed

From the spec (`docs/superpowers/specs/2026-11-04-snatch-ladder-design.md` §1), read out of the code
as it stood on 2026-11-04 — not assumed:

| Measured fact | Value | Source |
|---|---|---|
| The single shot at confirmation | default `SILENT` captured a photo + fix and then locked in the same call | `protection/TableModeResponder.kt:44-53,85,92-98` |
| A second movement inside the open incident | bound to the incident and triggered **nothing at all** | `domain/MotionIncidentBinder.kt:29,73-76`, `detection/MotionMonitoringService.kt:265-268` |
| The grace window | 5 s by default, invisible to whoever is holding the phone | `TableModeResponder.kt:56-70,126-132` |
| The lock-screen notice | shown only by `/lost`, never by Motion, in one language, in the owner's free text | `protection/LostNotice.kt:15-39` |
| Safe zones | exist (100/200/500/1000 m, off by default) and suppressed **nothing** | `protection/GeofenceLogic.kt:21-24,60-82` |
| The zone answer the guard could use | `wasInsideSafeZone` in memory only, no fix ⇒ silent no-op, a stale fix accepted | `ProtectionService.kt:95-134`, `protection/LocationFix.kt:37-43` |
| Motion rule today | episode at deviation ≥ 4.6 m/s², closes ≤ 0.8, kept ≥ 500 ms; confirmed at one episode ≥ 3 s or two within 10 s | `detection/ApprovedMotionCalibration.kt:11-20`, `domain/GuardContracts.kt:80-83` |
| Still-then-move | **does not exist** — no tilt measurement, no noise floor anywhere under `detection/` | the whole package |
| Shipped locales | `values` (English) and `values-ar` | `res/xml/locales_config.xml` |

### 7.3 The four decisions, each taken as recommended

**D1 — the ladder replaces the single shot.** A confirmed Motion now locks the screen, posts the
demand with a live countdown, and sends the text-and-location alert at `t=0`. The camera is not
fired at `t=0`: a frame taken while the holder is running is a blur, and the owner is better served
by the alert arriving immediately. The first evidence burst comes 30 s later, when nobody has
unlocked.

Why lock-first and why no automatic siren: Google's own Theft Detection Lock locks the screen at the
attempt (security.googleblog.com, 2024); Bhardwaj et al., USENIX Security 2025, found 5 of 20 victims
chased or confronted the attacker, so an automatic loud alarm escalates the danger to the person
holding the phone. The 30 s demand window matches ESET Anti-Theft's correction window; the repeat
cadence follows Prey's shipped constants.

**D2 — a safe zone mutes escalation, never detection, and unknown fails closed.** The most valuable
theft — a phone lifted off a bedside table — happens *inside* a zone. So the only fully quiet case is
inside and charging; inside and unplugged still locks and still sends evidence; anything unknown is
treated as outside.

**D3 — the notice is composed by the app, in three languages.** The holder of the phone never opens
the owner's settings, so one Arabic sentence from the owner is unreadable to most finders. The app
builds the notice from its own resources in Arabic, English and French, appends the owner's own
sentence when he wrote one, and puts the contact once with a call action.

**D4 — sensitivity is measured on the owner's handset before any constant moves.** A single force
threshold cannot give both recall and precision: Liu, Wagner & Egelman (ICISS 2018) gated on
|a| > 40 m/s² at 100 Hz for "grabbed from hand" but explicitly does not cover "left unattended and
walked off with"; fall-detection surveys (Stampfler et al., Frontiers in Public Health 2022) show
60.4–99.3 % sensitivity against 74.6–100 % specificity — the same trade-off. The published
low-power alternative is a gravity-direction test (AOSP's tilt detector fires at ≥ 35° change over
2 s). The instrumented calibration on this device still prints `q_g1=UNANSWERED
shipped_strong_threshold=false`, so the shipped 4.6 is not evidence-backed yet, and changing it from
here would be trading one unmeasured number for another.

**D5 — no new permission.** Tilt needs no permission, the foreground service already carries
`location`, and the zone tick reuses the already-declared alarm powers. CI still reports
`PASS: the installed APK declares exactly the reviewed permission set (21 permissions)` on run
`41971315120`. `ACTIVITY_RECOGNITION` stays out of A-45.

### 7.4 The ladder as built

`SnatchLadder` is pure (elapsed time and bursts already fired in, the next delay out), so its timing
is a JVM test rather than a phone run. `burstAtMs` gives:

| Burst | From incident start | Cadence |
|---|---|---|
| 1 | 30 s | the end of the unlock demand |
| 2–6 | 90, 150, 210, 270, 330 s | every 60 s — the fast phase, five bursts |
| 7–10 | 630, 930, 1,230, 1,530 s | every 5 min |
| 11 | 1,830 s | past the 30-minute window: the ladder settles instead |

Ten bursts inside half an hour, then nothing further until the incident changes. `TableModeResponder`
drives it: at confirmation it locks through `DeviceScreenLock.now(context)` while `armed` is true,
shows `TheftDemand`, sends the text-and-location alert through `AlertRuntime`, and schedules the
first burst. Before **every** step it re-reads the stored settings and the zone: an owner who
disarmed mid-ladder stops it, a phone that arrives home stops escalating. `ACTION_USER_PRESENT`
cancels every pending step and hides the demand — the owner unlocking his own phone is the end of the
story, not a detail. A movement that binds to the already-open incident is no longer silence: it
joins the running ladder, which keeps its cadence rather than restarting from the demand.

The grace response keeps the owner's own `motionGraceSeconds` — the ladder's 30 s is the demand
window, not the owner's setting.

### 7.5 The safe zone as built

| State | Response | Why |
|---|---|---|
| No zones defined, zone state unknown, or older than 2 × the sampling interval | **FULL** — the whole ladder | fail closed: the owner chose zones, he never chose "no protection when the phone cannot see itself" |
| Outside a zone (even charging) | **FULL** | a phone stolen from the desk at work is stolen |
| Inside a zone **and** charging | **MUTED** — the event is logged, no lock, no notice, no burst | the night stand and the desk; neighbours are not woken for a phone that is exactly where it sleeps |
| Inside a zone, unplugged | **QUIET** — lock, the owner's alert and one evidence capture, no siren and no repeating train | a lift inside the house still gets reported and still gets a picture |

The reading itself moved out of the settings file into `shieldra_zone_state`, because A-44 had just
proved that a settings `Save` rewrites a whole snapshot and must never be able to overwrite a live
decision made elsewhere. `ZoneSample.isFresh` refuses a sample from a clock that jumped backwards.
The sampling is driven by `GeofenceTicker` through an alarm and a non-exported receiver rather than
only the service's in-process handler, because the service is what the OEM kills first and the
ladder's decision depends on that state staying fresh. Arming schedules it, disarming cancels it.

### 7.6 The notice, the contact field, and a defect the design would have shipped

`TheftDemand` builds one public, ongoing heads-up notification (id 20 021, distinct from the lost
notice's 20 020 so both can be on screen and be cancelled apart) with a live chronometer counting the
demand window. It is composed at three locales at once — Arabic, English, French — because a
stranger's phone is not the owner's phone. It stays a notification, not a full-screen takeover:
Android 14 revokes full-screen-intent for apps without calling or alarm functionality
(source.android.com/docs/core/permissions/fsi-limits).

`OwnerContact` classifies the owner's line as a phone or an email, dials it without the spaces a
human types (`+213 555 12 34 56`) and mails an address; anything else is `UNKNOWN` and is shown as
text but never turned into an intent.

The defect: the contact and message fields were rendered only **inside** the "Control from Telegram"
block, which is a paid-tier toggle. A free install therefore had no way at all to fill the field the
new lock-screen notice reads — the notice would have gone out with no contact on exactly the installs
most likely to be stolen from. Both fields moved into an always-visible "What a stranger is shown"
section, with a hint naming both accepted forms and an error line that says plainly what will *not*
happen («Neither a phone number nor an email address: the notice will show this text, but there will
be no call button.»).

### 7.7 Sensitivity: unchanged on purpose, with the instrument to change it

`ApprovedMotionCalibration` constants are **not** touched in A-45 — that is D4, and it is the one part
of the owner's four asks that A-45 deliberately does not answer yet. What ships instead is the
measurement: `MotionTiltProbe` (pure gravity-direction maths: angle from the resting pose, magnitude,
resting baseline, noise sigma — 7 JVM tests) and `MotionLiftRecorderInstrumentedTest`, which holds the
phone still for 8 s to learn its resting pose, then logs one line per sample for 30 s:

```
SHIELDRA_LIFT t=1204 dev=1.37 tilt=7.4 mag=9.94
```

`rest_ms`, `window_ms` and `period_us` are instrumented-args overrides, so one build can be re-run at
different rates. The file contains no assertion about the device on purpose — it is a measurement, and
a gate that asserts what a handset does would only prove the test ran. The g4b pin checks exactly
that: the recorder must print `SHIELDRA_LIFT ` and must **not** contain a call-shaped assertion.

The task that sets the thresholds (plan Task 12) is left without values, because writing them now
would mean inventing numbers and then "confirming" them with tests. Every value it sets must be
quotable to a specific recorded line.

### 7.8 Deviations from the plan and the spec, recorded here

1. **The plan's alert call was wrong.** It used `AlertRuntime.snapshot(context, …).let {
   AlertRuntime.alert(context, it) }`, which is not the API. The responder uses the real
   `AlertRuntime.alert(context, AlertKind.MOTION, eventId)`.
2. **`BatteryLevel.isCharging` did not exist** and the zone needed it. It was added, reading the
   sticky `ACTION_BATTERY_CHANGED` intent rather than `BatteryManager.isCharging`, which is API 31
   while minSdk is 26.
3. **The plan gated the whole response on `armed`.** That would have silently stopped a *disarmed*
   dashboard-run Motion guard from capturing anything — a behaviour change the owner never asked for.
   The gate is on the **escalation** only: disarmed still gets the single response A-43 shipped.
4. **The grace lock keeps the owner's `motionGraceSeconds`**, not the ladder's 30 s.
5. **French is deliberately partial.** The app's own UI ships Arabic and English; `values-fr` carries
   the six holder-facing notice keys and nothing else, which is stated at the top of that file. The
   locale gate therefore checks AR↔EN key-for-key and FR for the notice keys — not "every string in
   the app in three languages".
6. **`locales_config.xml` was *not* given a `fr` entry**, which the spec's constants table asked for.
   Declaring French there would put "French" in Android's per-app language picker while
   `AppLanguage.shownFor` still renders English for it: a French owner would choose French and get an
   English app that claimed otherwise. The notice does not need the entry — `TheftDemand` composes its
   three copies with an explicit configuration context — so the spec line is dropped rather than
   implemented. `AppLanguage` and the picker stay honest at EN + AR.
7. **Two g4b pins were written wrong in the plan** and would have failed for the wrong reason:
   `grep -A 8` did not reach the `TableModeResponder` call in the `BoundToActive` branch (now `-A 12`),
   and the recorder's "no assertion" pattern matched the prose of its own doc comment (now a
   call-shaped pattern only).
8. **The lint suppression.** `tools:ignore="MissingTranslation"` is set on the root of
   `values/strings.xml` and `values/plurals.xml`, with the reason in a comment above it. The
   alternative — translating ~600 keys for a notice that needs 6 — and a repo-wide lint disable
   (which would silence the rule for every future file) were both rejected.

### 7.9 Tests and gates

New JVM tests (40 in total; they all use `kotlin.test`, which is what this module has — `org.junit`
imports in `app/src/test/**` do not compile here):

| File | Cases | What it holds |
|---|---|---|
| `SnatchLadderTest` | 8 | the approved cadence, the 30 s demand, the 30-min window, an overdue burst collapses to now, a burst past the window never happens, a restarted ladder begins at the demand again |
| `ZonePolicyTest` | 7 | no zones / unknown / outside ⇒ FULL; inside+charging ⇒ MUTED; inside+unplugged ⇒ QUIET; freshness, including a clock that jumped backwards |
| `TheftNoticeTextTest` | 5 | all three languages appear in order, separated not run together, the contact once and last, a blank owner sentence is skipped |
| `TheftDemandCopyTest` | 4 | every locale carries the same notice keys, the placeholders match, the three bodies are different copy, and the notice never claims something the app cannot do |
| `OwnerContactTest` | 4 | a spaced international number, a zero-prefixed dashed number, an address lowercased and mailed, anything else never turned into an intent |
| `MotionTiltProbeTest` | 7 | zero degrees for the same pose, 90 for a quarter turn, 180 whatever the magnitude, no division by a zero-length vector |
| `OwnerContactCopyTest` | 5 | the field copy exists in both UI locales, names both accepted forms, states the no-call-button consequence, and French stays the notice and nothing else |

`g4b-review-check.sh` gained section **5g**, pinning the five approved constants verbatim, the
responder's use of `SnatchLadder.next` / `TheftDemand.show` / `TheftDemand.hide` / the armed gate /
`ZoneResponse.MUTED`, the `BoundToActive` branch reaching the responder, the fail-closed literal
`!stateKnown || !inside -> ZoneResponse.FULL`, the zone reading living in `shieldra_zone_state` with a
freshness rule, the ticker being scheduled on arm and cancelled on disarm, `listOf("ar", "en", "fr")`
in the notice, and the recorder's existence, output line, and absence of device assertions. It prints
`PASS: A-45: a confirmed Motion locks, demands an unlock, escalates on the approved cadence, and a
zone never fails open`.

Most of §7.4–7.6 is still only provable on a screen, but one part of it is now an instrumented test:
`TheftDemandInstrumentedTest` (2 cases) posts the demand on the real handset, reads the notification
back out of the notification manager, and checks the three language copies, the owner's sentence, the
contact appearing exactly once in its normalized form, the public lock-screen visibility, the ongoing
flag, the countdown landing inside the 30 s window and the single call action; the second case gives it
a non-phone contact and expects no action at all. It never touches the keyguard — it tests what the
stranger reads, not what locks the screen — and it hides the notice and asserts it is gone before
returning.

`.github/scripts/locale-parity-check.sh` is new and runs in CI as its own step. It was proved to go
RED for a genuinely missing key (a real deletion produced `FAIL: values-ar is missing keys English
has`) and back to GREEN when restored, so it is a gate and not a formality.

### 7.10 Verification

- **CI (pure layer, plan Task 4):** run `41896697430` on `9ee813a` — **failure**, and it is worth
  recording why: `e: …/app/src/test/kotlin/com/***/protection/TheftDemandCopyTest.kt:60:45 Unresolved
  reference 's'.` — `Regex("%1\\$s")` was read as a Kotlin template, so the test source never compiled
  (`> Task :app:compileDebugUnitTestKotlin FAILED`, `BUILD FAILED in 5m 29s`).
- **CI (locale folder, plan Task 3/5):** run `41906989801` on `1d886ef` — **failure** at `lintDebug`:
  `598 errors, 85 warnings`, all `MissingTranslation`, because a partial `values-fr` makes lint demand
  every default-locale key in French — while the unit tests themselves passed: `CI TEST SUMMARY: 606
  tests, 606 passed, 0 failures, 0 errors, 0 skipped`. Fixed by the scoped suppression in 7.8/8.
- **CI:** run `41912020911` on `06ee3ec` — **success**. `CI TEST SUMMARY: 613 tests, 613 passed, 0
  failures, 0 errors, 0 skipped`; `G4-B LOCAL REVIEW PASS — runtime/device evidence still required`;
  `PHASE 3 STATIC REVIEW PASS`.
- **CI (the ladder wired, plan Task 8):** run `41971315120` on `3092ccf` — **success**. `BUILD
  SUCCESSFUL in 6m 16s`; `CI TEST SUMMARY: 613 tests, 613 passed, 0 failures, 0 errors, 0 skipped`;
  `G4-B LOCAL REVIEW PASS — runtime/device evidence still required`; `PHASE 3 STATIC REVIEW PASS`;
  `PASS: the installed APK declares exactly the reviewed permission set (21 permissions)` — A-45 added
  no permission, as D5 promised.
- **CI (the gates, plan Task 10):** run `41972919127` on `dcdb578` — **failure**, and not the gates'
  fault: `> Task :app:compileDebugUnitTestKotlin FAILED` with eight `None of the following candidates
  is applicable` errors on `OwnerContactCopyTest.kt` (lines 38, 46, 47, 53, 57, 67, 78, 80). The new
  test passed `assertTrue(message, condition)` — the JUnit 4 order — while this module tests with
  `kotlin.test`, whose overloads are `(condition, message)` and `(message, block)`. Because no local
  build is allowed here, the ordering could only surface in CI. Fixed in `f411f1e`.
- **CI (the gates, plan Task 10):** run `41973854219` on `f411f1e` — **success**, run number **398**,
  which is what a published build would carry as its `versionCode`. `BUILD SUCCESSFUL in 6m`;
  `Group C boundary scan passed.`; `PASS: A-45: a confirmed Motion locks, demands an unlock,
  escalates on the approved cadence, and a zone never fails open`; `G4-B LOCAL REVIEW PASS —
  runtime/device evidence still required`; `PHASE 3 STATIC REVIEW PASS`; `LOCALE PARITY PASS — Arabic
  equals English key for key, and the holder-facing notice is in all three languages`;
  `CI TEST SUMMARY: 618 tests, 618 passed, 0 failures, 0 errors, 0 skipped`; and the installed
  manifest still declares `21 permissions`. Both new gates ran in CI for the first time here, on the
  commit that fixed the test-source ordering, so the 613 → 618 step is the five `OwnerContactCopyTest`
  cases arriving — no logic changed between `3092ccf` and `f411f1e`.
- **Local (plan Task 10, before the push):** `bash .github/scripts/locale-parity-check.sh` → `LOCALE
  PARITY PASS — Arabic equals English key for key, and the holder-facing notice is in all three
  languages`; `bash .github/scripts/g4b-review-check.sh` → `PASS: A-45: …` then `G4-B LOCAL REVIEW
  PASS`, exit code 0.

**NOT RUN, and not claimed:** the A-45 phone round. Specifically — the real lock plus the demand
notification appearing on the lock screen and its countdown, an owner unlock cancelling the ladder and
hiding the notice, the burst train firing on schedule over 30 minutes, a second movement joining the
open incident, the three zone states against a real charging phone and a real geofence, and the notice
re-shown after a reboot. The lock path stays unmeasured because this owner's protocol forbids touching
his keyguard on a shared handset. The demand notice itself is no longer on that list in the same way:
`TheftDemandInstrumentedTest` posts it and reads it back on the device without locking anything, so
what the stranger sees is now measured where the ladder that produces it is not. The `SHIELDRA_LIFT`
samples are still missing, so the Motion thresholds are still unmeasured.
`qa/phone-checklist-snatch-ladder-2026-11-04.md`
(12 items) is written for that round; it is blocked because the phone is shared with another checker.
Everything in §7.4–7.6 except that one notice is compiled, unit-tested and pinned, which is not the
same as verified.


