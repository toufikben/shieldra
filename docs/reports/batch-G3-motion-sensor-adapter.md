# Batch G, slice G3 — the Motion sensor adapter and the sampling/quality layer

**Nothing in this slice monitors anything. No foreground service, no permission, no manifest change,
no screen change. On the device this build was installed from, Motion Guard still reads
"Not monitoring yet" — see the screenshot line under ANDROID TESTS.**

## BATCH
`G-motion-sensor-adapter` (third slice of protocol **Batch G — Motion Guard**, §18). It is the
first slice of Batch G that puts Android code in `app/src/main`, and it exists only inside the
boundary the owner drew in amendment **A-12** (`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`
§17): *"Motion Sensor Adapter + Sampling/Quality Layer"* — signal data, nothing else.

## SCOPE
- **`MotionSensorAdapter`** (`app/src/main/kotlin/com/shieldra/detection/MotionSensorAdapter.kt`) —
  looks up `TYPE_ACCELEROMETER` and `TYPE_SIGNIFICANT_MOTION`, registers **only** the continuous
  accelerometer through `registerListener(listener, sensor, periodUs, handler)` on its own
  `HandlerThread`, converts each `SensorEvent` into the existing pure `MotionSample` stamped with the
  sensor's own clock, and unregisters plus ends the thread in `stopReporting()`. Reports
  `Started` / `NoAccelerometer` / `RefusedByPlatform` / `AlreadyReporting` instead of throwing.
  Counters: `deliveredSampleCount`, `clockResolutionDrops` (readings whose millisecond stamp
  collided with the previous one — dropped rather than emitted out of order, and counted).
- **`MotionSignalNormalizer`** (`.../MotionSignal.kt`) — turns a list of samples into
  `MotionSignal`: readable `MotionSignalPoint`s (vector, magnitude, deviation from rest gravity),
  the **median interval measured from the delivered timestamps**, the rate derived from it, the
  longest measured interval, and a `MotionSignalQuality` of `EMPTY` / `INVALID` / `IRREGULAR` /
  `USABLE` with reasons that quote the numbers actually seen. The stall tolerance is an injected
  factor with **no default** (`require(finite && > 1.0)`).
- **Shared magnitude maths** — `accelerationMagnitudeMs2` / `gravityDeltaMs2` moved into
  `MotionSignal.kt` and `MotionWindowEvaluator.deviation` now calls them, so the G1 rule and the G3
  signal layer cannot grow two different definitions of "distance from rest".
- **Gate section 15** in `.github/scripts/g4b-review-check.sh`, and the pre-G2 blanket ban on
  `SensorManager` in `app/src/main/kotlin` narrowed to "the judgement file must not register".

Explicitly **not** in this slice, because A-12 lists them as not approved: Motion Security Event,
HIGH severity, Panic, notification, Room write, evidence, camera, location, SMS/email/cloud,
cooldown, any "X m/s² = attack" threshold, a foreground service to exercise the adapter,
`specialUse`, and treating 106 Hz as a requirement.

## FILES CHANGED
| File | What happened |
|---|---|
| `app/src/main/kotlin/com/shieldra/detection/MotionSignal.kt` | new: signal types + `MotionSignalNormalizer` + shared magnitude helpers |
| `app/src/main/kotlin/com/shieldra/detection/MotionSensorAdapter.kt` | new: the Android adapter (availability, register, unregister, sensor-clock stamps) |
| `app/src/main/kotlin/com/shieldra/detection/MotionDetection.kt` | `deviation()` now delegates to the shared `gravityDeltaMs2`; no behaviour change (its 11 tests still pass) |
| `app/src/test/kotlin/com/shieldra/detection/MotionSignalNormalizerTest.kt` | new: 11 JVM tests |
| `app/src/androidTest/kotlin/com/shieldra/detection/MotionSensorAdapterInstrumentedTest.kt` | new: 7 on-device tests |
| `.github/scripts/g4b-review-check.sh` | section 15 added; the A-8 sensor grep scoped to the judgement file |
| `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` | amendment **A-12** recorded before the code was written |

## DECISIONS ENFORCED
- **A-12 rule 1 (no fused layers).** The tripwire is *looked up*, never armed: the gate fails if
  `requestTriggerSensor` / `cancelTriggerSensor` / `TriggerEventListener` appear in either G3 file,
  and fails if any `SIGNIFICANT_MOTION` line in the adapter is not a `getDefaultSensor` lookup or a
  comment.
- **A-12 rule 2 + 3 (measured numbers are not contracts).** No `106`, no `0.52`, no `0.285/0.395/
  0.386/0.505`, no `20_000` and no default reporting period may appear in the G3 files' code lines;
  the rate is computed from timestamps, which the gate asserts by requiring `median` in the same
  code.
- **A-12 rule 4 (Signal ≠ Confirmed Security Event).** The gate fails if a code line in the G3 files
  mentions `SecurityEvent`, `EventId`, `Panic`, `Notification`, `Room`, `Evidence`, `Camera`,
  `Location`, `Sms`, `Mail`, `firebase`, `WorkManager`, `RequestQueue`, a cooldown spelling, or
  `confirm`.
- **A-8 / A-10 / A-6 unchanged.** `<service>`, `FOREGROUND_SERVICE*`, `BOOT_COMPLETED` and a second
  `<uses-permission>` all still fail the gate, and `MotionSensorAdapter(` may not be referenced
  anywhere in `app/src/main` outside its own file — nothing may start a stream yet.
- **A-11 untouched** — no battery wording appeared anywhere in this slice.

## TESTS
**270 JVM tests, 0 failures, 0 errors, 0 skipped**, read from that run's own
`unit-test-results-36814463442` artifact (259 from before this slice + the 11 new
`MotionSignalNormalizerTest` cases). The new suite covers: magnitude and deviation per point;
orientation-free deviation; `EMPTY` for one sample and for none (with no invented rate); a non-finite
sample dropped and the stream `INVALID`; two samples sharing a millisecond; a sample that arrives
backwards in time; the rate derived from delivered timestamps (10 ms → 100 Hz); a 300 ms stall
against a 10 ms median reported as `IRREGULAR` with the measured number quoted; jitter inside the
tolerance staying `USABLE`; a stream with no usable points carrying no rate; and the tolerance
guard rejecting `0.0`, `1.0`, `-2.0`, `NaN`, `Infinity`.

## ANDROID TESTS
RMX3910 (`IR7X5DJ7KZGEHMS8`), both APKs downloaded from CI run `36814463442` (the same run that
built them; both packages uninstalled first because per-runner debug keystores make a cross-run
upgrade impossible):

- `MotionSensorAdapterInstrumentedTest` → **`OK (7 tests)`**, run twice.
- Full instrumented suite → **`OK (68 tests)`** pass 1 and **`OK (68 tests)`** pass 2 on the same
  install (61 before this slice + 7 new).
- Measured on-device, logged not asserted (A-12): requested period 20 000 µs → **measured median
  interval 9.0 ms → 111.1 Hz**, longest gap 10 ms, **159 samples delivered in a 1.5 s window**,
  `clockResolutionDrops=0`, `significant_motion_available=true`.
- Screenshot after installing this build: onboarding says *"Continuous monitoring is not part of
  this build yet"*, and the guards grid shows **Motion Guard — "Not monitoring yet"** (with Lock
  Guard, SIM Guard, Panic and Battery). G3 changed no UI and the honest projection is unchanged.

## BUILD
`:app:assembleDebug` and `:app:assembleDebugAndroidTest` succeeded in run `36814463442`; artifacts
`debug/app-debug.apk` (18 489 506 B) and `androidTest/debug/app-debug-androidTest.apk` (595 857 B)
were downloaded from that run and installed. `:app:test` and `:app:lint` ran to completion in the
same job.

## STATIC AUDIT
`g4b-review-check.sh` → **G4-B LOCAL REVIEW PASS** including the new section 15 line
*"the G3 adapter and signal layer stay inside amendment A-12: signal data only, measured rates, no
tripwire arming"*; `phase3-review-check.sh` → PHASE 3 STATIC REVIEW PASS;
`group-c-contract-check.sh` → passed. All three also ran green as CI steps in `36814463442`.

## SECURITY AUDIT
Nothing in this slice can produce a security claim: the adapter is unreachable from the app
(gate-enforced), it holds no state across a process restart, it registers no sensor until a caller
hands it a sink, and its output type has no field that could be read as an event, a severity or a
notification. The one genuinely sensitive property that changed is that the repository now contains
code that can register a hardware sensor — which is exactly what A-12 approved, and only that.

## SELF-ADVERSARIAL FINDINGS
1. **Three CI round trips were self-inflicted.** `36813397127` failed because `MotionDetection.kt`
   still called `abs`/`sqrt` after the maths moved; `36813935519` failed because one JVM `assertTrue`
   was written JUnit-style (message first) in a kotlin.test suite (message last); `36814029302` then
   failed with **exactly the same line-76 error**, because the commit I pushed with it fixed a
   different defect (a reason string that did not match the code's wording) and left the argument
   order alone — I had identified both problems and shipped only one. All three are
   re-read-the-file-after-editing mistakes, not design problems; each cost ~6 minutes of CI.
2. **The `RefusedByPlatform` and `AlreadyReporting` branches have never executed.** `android.hardware.Sensor`
   is `final` with no public constructor (verified with `javap` against `android-35/android.jar`), so
   an instrumented test cannot hand the adapter a sensor the platform would refuse. Those two results
   are covered by reading and by the `NoAccelerometer` test's shape, **not** by a test that runs.
3. **The clock-collision path was not exercised on this device.** `clockResolutionDrops=0` in both
   windows, so the drop-and-count rule is proven only by the JVM suite's synthetic input, not by
   RMX3910 behaviour.
4. **The gate's scan reads code lines only.** Comment lines are excluded so a KDoc may cite a
   measurement; that keeps the greps honest today but means drift can hide in prose. The
   `median`/`isFinite` presence checks are equally shallow — they prove a word is there, not that
   the computation is correct.
5. **`confirm` in the forbidden-word list is a tripwire, not a proof.** A future legitimate method
   named `confirmedSomething` in this package would fail the gate for the wrong reason; the list is
   deliberately blunt and will need re-reading when a decision layer is approved.
6. **`deliveredSampleCount` counts for the lifetime of the adapter object, not per stream.** After a
   `stop()`/`start()` cycle it keeps accumulating, so it must not be read as "samples in this
   window" — the on-device test compares it against the sink's own list, which is the same object's
   lifetime.
7. **No assertion pins the delivered rate, on purpose.** A device that dropped to 5 Hz would still
   pass every G3 test, because A-12 forbids turning a measurement into a contract. The suite proves
   the *method* (measured median equals an independently computed median), not the *value*. That is
   the correct trade today and a real coverage hole tomorrow.
8. **The reinstall wiped Batch D–F on-device state again** (per-runner debug keystores). Pass 2
   therefore proves repeatability on a clean database, not continuity with data from an earlier
   build.
9. **The Q-G1 number this slice needed is still missing.** G3 measured rest-timing only; the strong
   movement magnitude still requires one deliberate carry of the phone by the owner, so the shipping
   calibration stays unset.
10. **G3 answered G2's advice differently than G2 recommended.** Slice G2 concluded "the adapter must
   carry sub-millisecond time" because two live samples in one millisecond made the pure rule reject
   a whole burst as `Unreliable`. This adapter keeps millisecond stamps and instead **drops and
   counts** a colliding reading, so the stream reaching the rule is always strictly increasing. That
   is a real trade, not a fix: nothing was lost on this device (`clockResolutionDrops=0`), but
   `MotionSample` still cannot express two readings inside one millisecond, so a calibration that
   needs every sample will require microseconds in the sample type. It is recorded here rather than
   hidden behind the passing tests.

## COMMIT
- `d664783` — RED: the two suites + amendment A-12 recorded first
- `2cb1275` — GREEN: adapter + normalizer + gate section 15
- `0b28f6b` — fix: judgement compiles against the shared maths; adapter counters `@Volatile`
- `c22d9a4` — test: the quality-reason assertion quotes the sentence the code actually produces
- `742a117` — test: kotlin.test message-last argument order in the JVM suite
- (this report + ROADMAP/requirements/register)

## REMOTE
`toufikben/shieldra`, branch `main`.

## CI
| Run | Commit | Verdict |
|---|---|---|
| `36812820209` | `d664783` | **failure** — 72 compile errors, all in `MotionSignalNormalizerTest.kt` (unresolved `MotionSignalNormalizer`, `points`, `droppedSampleCount`, …) |
| `36813397127` | `2cb1275` | failure — `Unresolved reference 'abs'` / `'sqrt'` in `MotionDetection.kt:146` |
| `36813935519` | `0b28f6b` | failure — `MotionSignalNormalizerTest.kt:76` `assertTrue` argument order |
| `36814029302` | `c22d9a4` | failure — the **same** `MotionSignalNormalizerTest.kt:76` `assertTrue` error; that commit fixed the reason wording but not the argument order |
| `36814463442` | `742a117` | **success** — every step green: tests + lint + both APKs, Group C, G4-B, Phase 3 |

## ROADMAP
`docs/ROADMAP.md` §"Required next batch" now records slice `G-motion-sensor-adapter` as landed and
names the next decision. `docs/capabilities/motion-detection-requirements.md` gained the adapter
path's measured numbers. `docs/OWNER_QUESTIONS.md` Q-G1 is unchanged in status: still open, still the
owner's.

## STATUS
**LANDED (CI verified + device verified).** The Android sensor path is available and produces usable
accelerometer samples on the tested device; G3 does not yet establish motion-detection accuracy or
security-event correctness.

## BLOCKERS
None for the slice. Two measurements still belong to the device and not to any layer: delivery with
the screen off / a real background state, and one deliberate carried-phone magnitude (Q-G1's strong
half). A-8's foreground-service question stays unanswered until the first exists.

## NEXT BATCH
**G4 — the layer above signal data**: `Signal → Validation → Confirmation → Severity → Confirmed
Security Event`, per the flow the owner wrote into A-12. It needs a decision that is the owner's, not
a design choice of mine: the strong-magnitude half of **Q-G1** (one deliberate carry of the phone on
RMX3910) and the still-unmeasured screen-off/background delivery. Until those exist, G4 can only be
built as pure judgement code with injected calibration, and no screen may show Motion as `Active`.
