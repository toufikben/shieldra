# A-47 — the safe zone is a silencer, the snatch is a signature, and unlocking is the proof (design)

Date: 2026-11-10 · Branch: `fix/general-app-gaps` · Owner approval: «موافق», given after the rebuilt
plan was read in full. The owner's correction is the premise of this document, in their own sense:
**safe zones exist to reduce alarms; leaving one must not start escalating alarms.** Outside a zone
every guard returns to its one normal behaviour, with no escalation. A snatch-and-run or a
motorcycle theft must make the Motion guard act, and escalate only when the owner does not respond
**by unlocking the phone** — the in-app stop button is a nuisance, not the proof.

This supersedes two A-46 decisions: "any confirmed Motion outside a zone opens the snatch" and "a
wrong PIN accelerates at its first attempt". It also removes the A-36 leave-zone alert. Everything
else in A-46 stands: the real lock, the ten-minute proof window, the public trilingual demand, the
vibration, the 5/10-minute ladder, the 10% battery floor, the safe-zone pause, and survival across
process death and reboot.

## 0. The defect that opened this design

The owner never created a safe zone, yet the Motion guard opened the full snatch. With the shipping
defaults — `motionEnabled=true`, `snatchLockEnabled=true`, `motionResponse=SILENT`,
`geofenceEnabled=false` — there is no zone, so `ProtectionRuntime.quietInSafeZone` is always false,
and `TableModeResponder.fire` sends **every** confirmed Motion to `SnatchCoordinator.begin`
(`TableModeResponder.kt:95`): lock, public demand, ten minutes of vibration, an immediate text, and
the ladder. The guard measures "strong movement for three seconds", not "a snatch", so a run for the
bus and a ride on the owner's motorcycle take the screen too.

Three more mismatches were verified against the source:

- **Unlocking does not stop it promptly.** Proof is read only at a ladder tick
  (`SnatchCoordinator.kt:143`) or when the app comes forward (`MainActivity.kt:101`). An owner who
  unlocks at the first second keeps the demand and the vibration until the tenth minute.
- **One wrong PIN escalates.** `LockGuardReceiver` calls `escalateNow` before its own counter
  (`LockGuardReceiver.kt:29`), so the first fumble sends evidence.
- **Leaving a zone is an alarm.** `ProtectionService.sampleSafeZoneNow` sends a `TRACKING` alert when
  a fresh fix crosses out of all zones (`ProtectionService.kt:820-829`), which is exactly the
  "zones make exits louder" behaviour the owner rejected.

## 1. The golden rule

1. **One base behaviour per guard, in every place.** A safe zone changes the volume of the noisy
   layer and nothing else. It never changes what a guard decides, never opens a snatch, and never
   delays evidence.
2. **Leaving a zone ignites nothing.** No alert, no challenge, no escalation. The next event is
   judged on its own by the guard's base rule.
3. **Escalation is a phase of the Motion snatch response, not a location effect.** Only a confirmed
   Motion that also matches the snatch signature may open it. SIM, battery and the Lock guard never
   open or accelerate a ladder except the one sanctioned accelerator below.
4. **Unlocking the phone is the proof.** The in-app "stop" control remains only as a fallback on a
   phone without a secure lock screen, where the unlock proof cannot exist in the first place.

## 2. Decisions

**D1 — per-guard base, zone, and snatch tie.**

| Guard | Base behaviour everywhere | Inside a fresh zone | Can it open a snatch? |
|---|---|---|---|
| Motion | A-33 response exactly as today: `SILENT` = capture + one alert; `SIREN` = + siren; `GRACE_THEN_SIREN` = wait for the owner's unlock, then siren. Evidence, not escalation. | Same capture; siren and message suppressed (existing 2026-11-08 rule). | **Only** with the snatch signature below, outside the zone, armed, snatch on, secure lock, lock succeeds. |
| Lock | Failed-unlock counter; at the owner's threshold one capture + one announcement; later attempts in the session gather evidence silently (`LockAlertPolicy`). | Unchanged — a wrong unlock at home is still evidence. | Never opens one. At the threshold attempt only, it accelerates one already open (D7). |
| SIM | Removed / foreign / network lost → one capture + one alert per transition. | Unchanged. | Never. |
| Battery | Last-breath evidence alert. | Unchanged. | Never; at ≤10% unplugged it slows an open ladder (A-46 D7). |

**D2 — the zone is a silencer.** `sampleSafeZoneNow` keeps writing `ZoneStateStore` (the quiet rule
needs the reading) and stops there. The `TRACKING` alert block is deleted. `quietInSafeZone`,
capture-inside-zone, the incident pause and the next-due resume all stay as A-46 built them.

**D3 — only a signature opens the challenge.** A confirmed Motion runs the base response; the
challenge is added only when the same sample window shows a **sharp grab after measured calm,
followed by sustained strong locomotion** (Section 3). Walk, run-for-the-bus with the phone already
in hand, lifting the phone and putting it down, and riding the owner's motorcycle with the phone
already mounted are ordinary: no lock, no demand, no ladder.

**D4 — the signature's numbers are measured, not invented.** `SnatchSignatureCalibration` has no
defaults, exactly like `MotionCalibration`. The shipping object is filled only from the on-device
probe in stage 3. JVM tests use test-local numbers and test the rule, never the device's physics.

**D5 — the challenge asks for the unlock.** The ten-minute demand keeps its trilingual public body
and adds, in ar/en/fr, the instruction that unlocking with the owner's own code is what stops it.
The app's stop control stays as the documented fallback only. The demand's current waiting line
already says "unlock it with your own code to stop this alert" (`values/strings.xml:262`); the
French demand set is extended in the same commit and the g4b key pin is updated with it.

**D6 — proof is immediate.** At `begin` the incident records `secureAtBegin`
(`KeyguardManager.isDeviceSecure`). While the process lives, a runtime `ACTION_USER_PRESENT`
receiver proves ownership the moment the keyguard goes away. Across process death, a chain of early
checkpoints — 20 s, 60 s, 3 min, 5 min after the lock, plus the 10-minute ladder tick as the ceiling
— reads the keyguard and proves with the existing `unlockedSince` rule. `onPasswordSucceeded`
remains a secondary signal, and `BOOT_COMPLETED` remains proof. The moment is persisted as
`proofSeenAtMs` so a killed process cannot lose it.

**D7 — a wrong PIN waits for the Lock guard's threshold.** The receiver counts first; only the
attempt that returns `FailedUnlockCounter.Verdict.Capture` at exactly the owner's threshold asks
`SnatchCoordinator.escalateNow`. Earlier attempts do nothing extra; later attempts keep the ordinary
lock-evidence path so they cannot turn the ladder into a per-PIN stream. Inside a zone the existing
pause wins.

**D8 — no lock, no challenge.** If `DeviceScreenLock.now` cannot take the screen (admin off, needs
re-grant) or the phone has no secure lock, `begin` refuses. A demand nobody must unlock is not a
challenge, and a phone without a credential cannot prove ownership by unlocking. The Motion base
response still runs; the Protection screen keeps showing the exact re-grant fix.

**D9 — the incident still survives.** `secureAtBegin` and `proofSeenAtMs` join `SnatchStore` in
device-protected storage; the early checks reuse `SnatchReceiver`'s exact alarms; `resumeLockedBoot`
and `confirmOwnerAfterBoot` are unchanged; the six-hour cap is unchanged.

**D10 — nothing new to request.** No permission, no service, no exported component. The
`ACTION_USER_PRESENT` receiver is runtime-registered and unregistered when the incident ends; the
check alarms are the same non-exported direct-boot-aware receiver with a second action.

## 3. The snatch signature (pure)

Input: the confirmed Motion's sample window (the existing rolling
`ApprovedGuardRules.motionRepetitionWindow` of 10 s) plus two calibrations: the approved
`MotionCalibration` for stream validity, and `SnatchSignatureCalibration` for the shape.

Fields of `SnatchSignatureCalibration`: `grabPeakDeltaMs2`, `calmBeforeGrabMs`,
`grabMaxDurationMs`, `followMinPeakDeltaMs2`, `followWindowMs`, `followMinMovementMs`,
`followMaxGapMs`. No defaults; constructor `require`s are positive and ordered.

`SnatchSignaturePolicy.classify(samples, motionCalibration, signatureCalibration)`:

1. **Refuse untrusted streams.** Fewer than two samples, a non-finite value, a non-increasing clock
   or a gap beyond `maxSampleGapMs` → `Ordinary`. A stream that cannot be read cannot accuse.
2. **Deviation** is `|√(x²+y²+z²) − g|`, the same orientation-free measure the Motion layer uses.
3. **Grab onset.** The first sample at or above `grabPeakDeltaMs2` whose preceding
   `calmBeforeGrabMs` lies inside the window and contains **no** sample at or above the noise floor.
   Onset not found → `Ordinary`.
4. **The sharp part ends** at `onset + grabMaxDurationMs`. A sustained episode that never has a
   sharp onset shorter than that is a ride, not a grab.
5. **Follow-up.** From that point, within `followWindowMs`, count movement: samples at or above
   `followMinPeakDeltaMs2`, grouped with no internal gap beyond `followMaxGapMs`. Total measured
   movement ≥ `followMinMovementMs` → `Snatch(onset)`; less → `Ordinary`.

Golden cases and their expected verdicts (these become JVM tests in stage 2):

| Case | Shape | Verdict |
|---|---|---|
| Walking with the phone in hand | moderate episodes, never a calm-then-grab onset | Ordinary |
| Running for the bus, phone already in hand | strong episodes but no calm before them | Ordinary |
| Lifting the phone from a table and putting it back | calm → one sharp lift, then quiet | Ordinary |
| Riding the owner's motorcycle, phone already mounted | vibration starts with the engine, no short sharp onset | Ordinary |
| Snatch: phone at rest → grab → run | calm → sharp onset → strong movement inside the window | Snatch |
| Snatch: phone at rest → grab → vehicle | calm → sharp onset → sustained strong movement | Snatch |

Known, accepted trade-offs: a calm thief who walks away slowly with the phone will not open the
challenge (the base response still records evidence); lifting the phone onto one's own motorcycle
mount and riding off may open it, and unlocking clears it in seconds. A signature that never fires
would protect nobody; the unlock proof is what keeps a false positive cheap.

## 4. Challenge and proof

Sequence for a confirmed, signed Motion outside a zone, armed, snatch on, secure lock:

1. `DeviceScreenLock.now` locks the screen; failure → D8, base response only.
2. `SnatchPolicy.begin(eventId, nowMs, locked, secureAtBegin)` stores the incident, posts the
   demand, starts the vibration, sends the one text line, cancels the periodic countdown, and
   schedules the early checks and the 10-minute ladder tick.
3. The runtime `ACTION_USER_PRESENT` receiver proves ownership on unlock. The checkpoints cover a
   killed process; each proves with `unlockedSince` and otherwise schedules the next.
4. Proof writes `proofSeenAtMs`, clears the whole incident, hides the demand, stops the vibration,
   alarm and ladder, and restarts the countdown. No evidence is sent. The persisted timestamp also
   makes the teardown idempotent: if the process died between the write and the clear, the next tick
   sees it and closes the incident instead of sending.
5. No proof at the tenth minute → the existing A-46 ladder (photo + fix, then 5/10 minutes, pause in
   a zone, 10-minute floor at ≤10% battery, six-hour cap).

`SnatchPolicy` gains a pure predicate for the receiver and tests:

```kotlin
fun canProveByUnlock(incident: Incident): Boolean =
    incident.active && incident.secureAtBegin && incident.lockedAtMs > 0L

fun proofSeen(incident: Incident, nowMs: Long): Incident =
    incident.copy(proofSeenAtMs = nowMs)
```

and a pure accelerator predicate:

```kotlin
fun acceleratesOnFailedUnlock(incident: Incident, failedAttempts: Int, lockThreshold: Int): Boolean =
    incident.active && failedAttempts == lockThreshold
```

The receiver turns a `true` into `escalateNow`; nothing else changes its timing.

## 5. Change map

| File | Change |
|---|---|
| `protection/SnatchPolicy.kt` | Incident gains `secureAtBegin`, `proofSeenAtMs`; `canProveByUnlock`, `proofSeen`, `acceleratesOnFailedUnlock`; keep `unlockedSince`, `begin` signature extended. |
| `protection/SnatchStore.kt` | Persist the two new fields. |
| `protection/SnatchCoordinator.kt` | `begin` refuses on lock failure / insecure device; registers the runtime proof receiver; schedules the early checks; `onTick` unchanged in substance; unregister on `end`. |
| `protection/SnatchReceiver.kt` | Second action `ACTION_PROOF_CHECK` with its own request code; schedules the 20 s / 60 s / 3 min / 5 min chain. |
| `protection/SnatchProof.kt` (new) | Runtime `ACTION_USER_PRESENT` receiver, one registration per incident, unregistered by `end`. |
| `protection/TableModeResponder.kt` | `onMotionConfirmed(..., snatchSignature: Boolean)`; `fire` calls `begin` only when true; base response otherwise, unchanged inside a zone. |
| `detection/MotionMonitoringService.kt` | Evaluates `SnatchSignaturePolicy` over the confirmed window and passes the verdict. |
| `protection/SnatchSignaturePolicy.kt` (new) | Pure classifier and `SnatchSignatureCalibration` (D4). |
| `detection/ApprovedSnatchSignatureCalibration.kt` (new) | The measured constants, stage 3 only. |
| `protection/LockGuardReceiver.kt` | Count first; accelerate only on the threshold `Capture` (D7). |
| `protection/ProtectionService.kt` | Delete the A-36 leave-zone `TRACKING` alert; keep the zone-state write. |
| `res/values/strings.xml`, `values-ar`, `values-fr` | Challenge/unlock copy (D5), French demand set extended. |
| `.github/scripts/g4b-review-check.sh` | Update the responder pin, the wrong-PIN pin, the 5i proof pins and the exact French key list — the old pins assert the behaviour this design replaces. |
| tests | New `SnatchSignaturePolicyTest` (Section 3 cases); `SnatchPolicyTest` additions for proof/accelerator; a test that SIM/battery/lock never reach `SnatchCoordinator.begin`; zone-silencing tests. |

## 6. Stages

1. This design is written and committed (no code).
2. Pure `SnatchSignaturePolicy` + JVM tests for the six golden cases (test-local numbers).
3. On-device calibration with the existing probe pattern: CI builds the debug and androidTest APKs;
   install without launching; the owner performs the safe movements; logcat gives the numbers; only
   then is `ApprovedSnatchSignatureCalibration` written. No PIN, no reboot, no lock-screen touch.
4. Challenge and instant proof (D6, D8) with timing tests.
5. Zone and guard conflict cleanup (D2, D7) with the no-ladder / no-silencing tests.
6. Gates, push, exact CI run id, install without launch, owner acceptance matrix, roadmap update,
   close our app at the end of the round.

## 7. Acceptance matrix (owner's phone)

| # | Scenario | Expected |
|---|---|---|
| 1 | Walk outside every zone, phone in hand | No lock, no demand, no ladder |
| 2 | Run for the bus, phone already in hand | No lock, no demand, no ladder |
| 3 | Lift the phone from a table and put it down | No lock, no demand, no ladder |
| 4 | Ride own motorcycle, phone mounted before start | No lock, no demand, no ladder |
| 5 | Grab from rest + run | Lock + demand + vibration (ar/en/fr) |
| 6 | Grab from rest + vehicle | Lock + demand + vibration |
| 7 | Unlock during the challenge | Demand gone, vibration stops, countdown back, nothing sent |
| 8 | No unlock for 10 minutes | First photo + fix, then every 5/10 minutes |
| 9 | Wrong PIN before the lock threshold | No acceleration |
| 10 | Wrong PIN at the threshold | Immediate escalation, one send |
| 11 | Confirmed Motion inside a zone | Capture only; no siren, no message, no challenge |
| 12 | Leave a zone with no event | Nothing is sent |
| 13 | Incident paused in a zone, then leaves | Next rung at its due time; no burst |
| 14 | SIM removed / swapped / network lost | Evidence alert always, no ladder, no zone silencing |
| 15 | Battery at threshold | Last-breath evidence alert; no ladder; ≤10% unplugged slows an open one |
| 16 | Lock failure / no secure lock | No challenge, no demand; base response; re-grant guidance visible |
| 17 | Disarm or switch the snatch guard off | Incident closed, demand gone |
| 18 | Reboot during an incident | Demand re-posted before first unlock; boot unlock proves; nothing duplicated |

## 8. Risks and honest limits

- **Signature blind spots.** A slow, calm theft never opens the challenge. The base Motion evidence
  still records it, and the owner's own review of events is the backstop.
- **Signature false positives.** A grab-and-run by the owner (catching a bus from a table) can open
  the challenge; unlocking clears it in seconds. This is the price of catching a real grab.
- **Device-specific numbers.** Until stage 3 measures them, no production calibration exists and the
  signature cannot ship enabled. CI unit tests use test-local values only.
- **Process death latency.** The runtime receiver makes proof immediate in the normal case; after a
  kill, the next checkpoint (≤ the next of 20 s / 60 s / 3 min / 5 min, with the 10-minute tick as
  the ceiling) closes it. The six-hour cap, demand and ladder are unchanged.
- **No secure lock.** No challenge is possible; this is deliberate, not a bug.
- **Gates move with the rule.** g4b's "every confirmed Motion opens the snatch" pin and the exact
  French key list are assertions about the old design; both are updated in the same commit as the
  behaviour, never bypassed.

## 9. Verification

Local gates: `g4b-review-check.sh` (updated pins), `phase3-review-check.sh` (permissions unchanged),
`locale-parity-check.sh` (ar/en parity; French limited to the demand), plus the JVM suite. CI is the
only build verifier and its exact run id and test count go into `docs/ROADMAP.md`. The physical
lock, demand, vibration, unlock proof, zone pause and ladder remain on the owner's phone checklist;
they cannot be proven by a build.
