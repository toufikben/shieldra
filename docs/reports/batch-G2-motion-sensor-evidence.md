# Batch G, slice G2 — what the RMX3910 actually does when a sensor is asked

**Nothing in `app/src/main` changed. No adapter, no foreground service, no `<service>`, no
permission, no manifest entry, no screen. Motion is still not `Active` anywhere, the dashboard
still says "No check yet", and this slice claims none of that away — it only measured.**

## BATCH
`G-motion-sensor-evidence` (second slice of protocol **Batch G — Motion Guard**, §18). Amendment
**A-8** allows a foreground service and its permission only as an architectural decision backed by
device evidence, and `docs/PHASE4_ANDROID_CAPABILITY_MATRIX.md:10` marks Motion CONDITIONAL pending
exactly that evidence. Slice G1 built the pure judgement that turns samples into the approved
observation; G2 asks the prior question the owner's ruling insists on: **what does this device
actually deliver**, before any of it is wired to a sensor.

## SCOPE
One instrumented class, `MotionSensorEvidenceInstrumentedTest`, six tests, deliberately split in
two kinds:

- **assertions** only for what must hold on any device that could run G1-02 at all — an
  accelerometer exists and can sample at least every 200 ms; a visible app receives events; what
  is received is well-formed (finite axes, a clock that never runs backwards); the tripwire is a
  one-shot wake source; a trigger delivers at most once per request; and live samples are judged
  by the same pure rule the JVM suite tests;
- **measurements** (`Log.i` with a `MEASURED` prefix) for everything that is a decision or a
  quantity rather than a fact: delivered rate, background delivery, resting deviation, whether the
  tripwire fired while untouched.

`FIXTURE_CALIBRATION` in the test mirrors the JVM suite's fixture. **No threshold ships** — Q-G1
belongs to the owner.

## FILES CHANGED
- `app/src/androidTest/kotlin/com/shieldra/detection/MotionSensorEvidenceInstrumentedTest.kt` (new)
- `docs/capabilities/motion-detection-requirements.md` — the measured section, the trigger-API
  constraint, and the UNVERIFIED rows this slice closed or narrowed
- this report, `docs/ROADMAP.md`, `docs/OWNER_QUESTIONS.md`

## DECISIONS ENFORCED
- **A-8** — evidence before service: this slice produces the delivery numbers A-8 asks for and
  writes **no** service, `FOREGROUND_SERVICE*` or `<uses-permission>`. The g4b gate that fails a
  `SensorManager` reference inside `app/src/main/kotlin` still passes; the harness is in
  `androidTest`, which that gate deliberately does not cover.
- **A-10** — no `RECEIVE_BOOT_COMPLETED`, no `BOOT_COMPLETED`; the boot row stays deferred.
- **A-6** — `POST_NOTIFICATIONS` remains the only declared permission (checked in the manifest).
- **G1-02** — unchanged; the time rule is still the owner's approved one and this slice touches
  only the sensing questions around it.
- **Q-G1** — still open, but no longer unquantified: a resting noise floor now exists.

## TESTS
JVM suite untouched: **259 tests, 0 failures, 0 errors, 0 skipped** (32 result classes) on both CI
runs of this slice — G1's 11 `MotionWindowEvaluatorTest` plus the previous 248.

## ANDROID TESTS
`MotionSensorEvidenceInstrumentedTest`, RMX3910 (`IR7X5DJ7KZGEHMS8`), APK pair downloaded from CI
run `36793076936` (the same run that built them):

```
com.shieldra.detection.MotionSensorEvidenceInstrumentedTest:......
Time: 8.992
OK (6 tests)
```

Full suite on the same install, twice: `OK (61 tests)` and `OK (61 tests)` (13 s each).

### Measured, in the device's own words

| | |
|---|---|
| `SIGNIFICANT_MOTION` runtime shape | `wakeUp=true`, `reportingMode=2` (`REPORTING_MODE_ONE_SHOT`), `fifoMaxEventCount=0`, `minDelay=-1` |
| `registerListener(SIGNIFICANT_MOTION, …)` | **`false`** + platform log `E/SensorManager: Trigger Sensors should use the requestTriggerSensor.` — seen with run `36791998789`'s APK (the build that used that API); the final harness never calls it |
| `requestTriggerSensor(SIGNIFICANT_MOTION)` | **accepted** (`true`); `trigger_fired_untouched=0` in 1500 ms |
| Accelerometer | `sc7a20 ACCELEROMETER`/SILAN, `maximumRange=78.453606`, `minDelayUs=5000`, `maxDelayUs=100000`, `powerMa=0.001`, `wakeUp=false`, `reportingMode=0` |
| Foreground delivery, 20 000 µs requested | **159 events / 1500 ms** (≈106 Hz), identical across runs |
| Delivery with no resumed activity | **159 events / 1500 ms** — upper bound, see finding 5 |
| Resting deviation, untouched phone | 210 samples/1980 ms → min 0.2853, median 0.3948, max **0.5237** · 213 samples/1995 ms → min 0.2943, median 0.3858, max **0.5050** m/s² |
| Same rest samples through the pure rule | once `Movements([])`, once **`Unreliable`** — "samples at 152336237 ms and 152336237 ms are not in increasing time order" |

## BUILD
`:app:lint` ran to completion (analyze + report for main, androidTest and unitTest sources) and
both APKs were produced by run `36793076936`.

## STATIC AUDIT
`G4-B LOCAL REVIEW PASS` and `PHASE 3 STATIC REVIEW PASS` in that run's log. Counted from the log:
g4b printed **47** `PASS:` lines there against **41** locally — six checks exist only where the
build outputs are present, so the two numbers are quoted rather than reconciled into one. Phase 3
is **9** in both. The Motion gate is among them and passes: no Android import in the judgement, no
`SensorManager`/listener/`getDefaultSensor` in `app/src/main/kotlin`, no default value on any
`MotionCalibration` parameter, `Unreliable` still reachable, no boot receiver, all 11 named JVM
tests present.

## SECURITY AUDIT
Nothing new can leak: no new component, permission, export or network path. The harness registers
listeners only inside a test window and unregisters them in a `finally` (verified in `logcat`:
`unRegisterListener by …MotionSensorEvidenceInstrumentedTest$Collector@…`). Samples never leave the
device — nothing persists them, and the app has no storage path for raw sensor data.

## SELF-ADVERSARIAL FINDINGS
1. **Two of the four Android APIs I wrote first do not exist.** `Sensor.getMaxRange` is
   `getMaximumRange`, and `Sensor.isOneShotSensor` / `SensorManager.isMaxReportLatencySupported`
   are not in the android-35 jar at all. CI caught it (`compileDebugAndroidTestKotlin` failed on
   exactly three unresolved references); both should have been checked against the jar locally
   first, which costs seconds and no Gradle.
2. **The batching assertion was reasoning, not design.** "A one-shot tripwire must support batched
   delivery or a cold process cannot be woken" is not what wakes anything — `isWakeUpSensor` and
   one-shot `reportingMode` are. The invented API hid an unfounded claim.
3. **The device caught a test that would have lied.** `registerListener` for SIGNIFICANT_MOTION is
   *refused on this build*, so the original "the tripwire delivered 0 events untouched" log line
   would have read as "the tripwire does not fire" while actually meaning "the wrong API was used".
   Rewritten onto `requestTriggerSensor`, which the platform accepts.
4. **The first `deliveredCount <= 1` assertion measured a `Burst.samples` filter, not a delivery
   rule**; and `values.size < 3` was the wrong filter — a listener also sees the platform's own
   `TYPE_META_DATA` flush events on the same sensor. Now filtered by sensor type.
5. **"Background delivery works" is NOT established.** 159 events with no resumed activity is an
   **upper bound**: the instrumentation holds the process alive and the screen was on. The Android
   9+ background-suppression question stays open (requirements UNVERIFIED row 1), and A-8's
   foreground-service decision must not be read from this number.
6. **Millisecond timestamps lose real samples.** At the delivered ~100 Hz the platform produced two
   samples in the same millisecond, and the pure rule then rejected the whole burst as
   `Unreliable` — correctly per its own contract, so the test passed and the problem stayed
   invisible. `MotionSample.elapsedMs` cannot carry this sensor faithfully; that is G3's plumbing
   decision, and it is now recorded rather than discovered under pressure.
7. **The requested rate is not the delivered rate.** Asking for 50 Hz cost ~106 Hz: `SensorService`
   actuated the HAL at 10 ms. Any battery statement that assumes the requested rate would be wrong.
8. **`real_samples_are_judged_by_the_same_rule…` can pass vacuously** if the device delivers
   nothing (0 samples → `Insufficient` → `assertTrue(rest.size < 2)` succeeds). Two other tests in
   the same class assert delivery ≥2, so a silent-noise device fails the class — but this
   individual test does not prove delivery on its own.
9. **`trigger_fired_untouched=0` proves nothing about a carry.** It is the expected reading for a
   still phone; the requirements doc keeps "does it fire when the phone is picked up" as an open
   row requiring one deliberate carry by the owner.
10. **Reinstalling for each CI run wipes device state again.** Per-runner debug keystores make
    `install -r` fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, so both packages were uninstalled;
    the Security Events and activation counts recorded for Batches D–F on the previous install are
    gone from the phone (their reports and run ids are not).

## COMMIT
`390569e` harness (RED) → `7082987` the three API names → `12bf081` trigger-API rewrite →
docs commit for this report.

## REMOTE
`toufikben/shieldra`, branch `main`.

## CI
- `36791176374` (`390569e`) — **failure**, `:app:compileDebugAndroidTestKotlin`, exactly
  `Unresolved reference 'maxRange'`, `'isOneShotSensor'`, `'isMaxReportLatencySupported'`. This is
  the proof the harness is compiled by CI and was never written against a compiler.
- `36791998789` (`7082987`) — **success**, 259/259 JVM, APKs downloaded and run: the Motion class
  came back `Tests run: 6, Failures: 1` with `the platform refused to register SIGNIFICANT_MOTION`
  — finding 3, from the device, not from a document.
- `36793076936` (`12bf081`) — **success**, **259 tests / 0 failures / 0 errors / 0 skipped**, lint
  run to completion, both APKs, g4b and Phase 3 `PASS`. The device numbers above are from this
  run's APKs.

## ROADMAP
Batch G stays **IN PROGRESS**: G1 (pure judgement) and G2 (device evidence) landed; the Android
sensor adapter (G3) is next and still has no code.

## STATUS
`DONE — measured, not wired`. Six instrumented tests pass on the owner's phone; the requirements
document now separates what RMX3910 was observed doing from what is still assumed; **no monitoring
claim changed anywhere in the app**, and no foreground-service decision has been taken on the
strength of an upper-bound number.

## BLOCKERS
None for G3. Two inputs G3 must not lose: the trigger API is the only route to the tripwire, and
`MotionSample` needs sub-millisecond time or the burst will intermittently read `Unreliable`.
Q-G1's strong level still needs one real carry from the owner; the resting half is measured.

## NEXT BATCH
**G3 — the Android adapter**: a `SensorManager` continuous listener for the burst and a
`requestTriggerSensor`/`cancelTriggerSensor` pair for the tripwire, feeding the already-tested pure
rule, with timestamps that survive ~100 Hz. It stays behind "app opened → user enables →
monitoring starts" (A-8) and takes no foreground-service decision until the screen-off measurement
exists.
