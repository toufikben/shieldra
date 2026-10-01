# Motion Guard — the detector's real Android requirements (Batch G, first deliverable)

Written **before** any Motion code, because amendment A-8 forbids choosing a foreground-service
type or adding a permission before the detector's actual requirements are known. Everything under
"MEASURED" was read off the owner's device; everything under "UNVERIFIED" is a requirement to
test, not a result.

## MEASURED on RMX3910 (`IR7X5DJ7KZGEHMS8`, Android 16 / API 36) — 2026-09-30 23:33

`adb shell dumpsys sensorservice` and `adb shell pm list features`:

| Sensor | type | permission | mode | rates | wake-up |
|---|---|---|---|---|---|
| `sc7a20 ACCELEROMETER` (SILAN) | `android.sensor.accelerometer(1)` | **`perm: n/a`** | continuous | minRate **10 Hz**, maxRate **200 Hz** | **non-wakeUp** (FIFO 4500/3000) |
| `LINEARACCEL` (MTK) | `android.sensor.linear_acceleration(10)` | `n/a` | continuous | 12.5–200 Hz | non-wakeUp |
| `GRAVITY` (MTK) | `android.sensor.gravity(9)` | `n/a` | continuous | 50–200 Hz | non-wakeUp |
| `oem-pseudo-gyro` (virtual) | `android.sensor.gyroscope(4)` | `n/a` | continuous | 10–200 Hz | non-wakeUp |
| **`SIGNIFICANT_MOTION` (MTK)** | `android.sensor.significant_motion(17)` | **`n/a`** | **one-shot** | maxDelay 0 | **wakeUp** |
| `STATIONARY_DETECT` | `android.sensor.stationary_detect(29)` | `n/a` | one-shot | — | **wakeUp** |
| `PICKUP_DETECT` (OPLUS, id 65611) | vendor | `n/a` | on-change | 1 Hz | **wakeUp** |
| `STEP_DETECTOR` / `STEP_COUNTER` | 18 / 19 | **`android.permission.ACTIVITY_RECOGNITION`** | special-trigger / on-change | — | non-wakeUp |

Device features include `android.hardware.sensor.accelerometer`. Battery saver was **off**
(`settings get global low_power` → `0`), and the app's only runtime permission is
`POST_NOTIFICATIONS: granted=true` (amendment A-6).

### What those rows decide

1. **Motion needs no runtime permission.** The accelerometer, linear acceleration and gravity
   sensors all report `perm: n/a`. `ACTIVITY_RECOGNITION` appears only on the step sensors, which
   this design does not use. So Batch G adds **no** `<uses-permission>` for sensing, and the
   "exactly one permission" gate stays untouched.
2. **Sampling stays at or under the accelerometer's 200 Hz ceiling**, and far below it in
   practice, so `HIGH_SAMPLING_RATE_SENSORS` is not needed either. Measured caveat below: this HAL
   answers a 50 Hz request with ~106 Hz, so the *requested* rate is not the *delivered* rate.
3. **A raw continuous accelerometer watch is the wrong shape on this device.** The accelerometer
   is **non-wakeUp**: it cannot bring the device out of suspend, so an "always sampling" guard
   would have to hold the CPU awake — a battery cost the owner has not approved and §31 forbids
   implying is free.
4. **`SIGNIFICANT_MOTION` is the Android-sanctioned tripwire**: one-shot, **wakeUp**, no
   permission, and it is the platform's own "the device was moved" signal. The shape Batch G
   should therefore take is: `SIGNIFICANT_MOTION` fires → a short accelerometer burst samples
   the movement → the approved G1-02 rule decides → a Security Event is persisted through the
   existing pipeline. **The API that shape must use is `SensorManager.requestTriggerSensor`, not
   `registerListener`** — measured, see the next section. **That shape is still not built**: under
   amendment A-12 slice G3 ships only the continuous accelerometer adapter and its signal-quality
   layer, so the trigger remains an availability lookup here. Arming it is a later slice, and the
   `Signal → Confirmation → Security Event` half belongs to G4.

## MEASURED by the instrumented harness — RMX3910, 2026-10-01 00:56

`MotionSensorEvidenceInstrumentedTest`, six tests, on the APK pair from CI run `36793076936`;
full suite `OK (61 tests)` twice on one install. These are runtime readings, not datasheet values.

| Measurement | Result |
|---|---|
| `SIGNIFICANT_MOTION` at runtime | `wakeUp=true`, `reportingMode=2` (`REPORTING_MODE_ONE_SHOT`), `fifoMaxEventCount=0`, `minDelay=-1` |
| `registerListener(SIGNIFICANT_MOTION, …)` | **refused** — returned `false`, platform logged `E/SensorManager: Trigger Sensors should use the requestTriggerSensor.` (observed with run `36791998789`'s APK, the build that still used the wrong API; nothing in the final harness calls it) |
| `requestTriggerSensor(SIGNIFICANT_MOTION)` from an ordinary app | **accepted** (`true`); fired **0** times in 1500 ms with the phone untouched |
| Accelerometer identity | `sc7a20 ACCELEROMETER` / SILAN, `maximumRange=78.45` m/s², `minDelay=5000 µs`, `maxDelay=100000 µs`, `power=0.001 mA`, `wakeUp=false`, `reportingMode=0` (continuous) |
| Delivered rate for a 20 000 µs (50 Hz) request | **159 events in 1500 ms ≈ 106 Hz**, twice; `SensorService` shows the HAL actuated at `10000000` ns, i.e. an ODR-locked 100 Hz |
| Delivery with **no resumed activity** | **159 events in 1500 ms** — full rate continued |
| Resting deviation, phone untouched | 210 samples / 1980 ms: min **0.2853**, median **0.3948**, max **0.5237** m/s² · 213 samples / 1995 ms: min **0.2943**, median **0.3858**, max **0.5050** m/s² |
| Live rest samples through the pure rule | once `Movements([])`, once **`Unreliable("samples at 152336237 ms and 152336237 ms are not in increasing time order")`** |

### What those readings decide

5. **The tripwire is a trigger, and it is reachable.** `SensorManager` refuses a listener for
   SIGNIFICANT_MOTION and names the API to use instead; `requestTriggerSensor` accepts the
   registration from this unsigned, non-system app. So the wake-capable shape in row 4 has a real
   entry point on this device — and the adapter must arm it once per movement, because the trigger
   delivers once per request.
6. **A burst costs ~100 Hz whether it is asked for or not.** The evaluator's `maxSampleGapMs` and
   any battery claim must be argued against the delivered rate, not the requested one.
7. **Millisecond-resolution timestamps are not enough for this sensor.** At the delivered rate the
   platform handed two samples inside the same millisecond, and the approved pure rule then
   refused the whole burst as `Unreliable` — correctly, by its own contract, and intermittently in
   practice. The adapter must carry sub-millisecond times (the platform's `event.timestamp` is
   nanoseconds) or the ordering rule must admit equal stamps; that is a **G3** decision about
   plumbing, not a change to G1-02's thresholds.
8. **The resting noise floor is now a number, not an assumption**: ~0.52 m/s² worst case over two
   independent rests. The `1.2 m/s²` noise floor used in every JVM test sits ~2.3× above it, which
   is the shape of an argument for Q-G1 — but the *strong* level still has no measured support,
   because no test moved the phone.

## MEASURED through the G3 adapter — RMX3910, 2026-10-01 04:10

`MotionSensorAdapterInstrumentedTest`, seven tests, on the APK pair from CI run `36814463442`
(commit `742a117`); the adapter class ran `OK (7 tests)` twice and the full instrumented suite ran
`OK (68 tests)` twice on one install. This is the same physical path the app would use
(`MotionSensorAdapter` → `SensorManager.getDefaultSensor` → `registerListener` on a
`HandlerThread`), not a hand-fed fixture.

| Measurement | Result |
|---|---|
| Availability, no registration | `accelerometer_available=true`, `significant_motion_available=true` — the adapter can see that the trigger **exists** without arming it (A-12 rule 1) |
| `registerListener` return value | **`true`**, and `registeredSensorType` became `TYPE_ACCELEROMETER` — the continuous half of G2's refused/accepted split now verified from the correct API |
| Samples delivered for a 20 000 µs request | **159 samples in 1500 ms**, all finite and strictly increasing in time |
| Rate computed from delivered timestamps | median interval **9.0 ms** → **111.1 Hz**, longest gap **10 ms** — the test compares this against an interval median it computes itself, so it proves the adapter measures rather than assumes |
| Millisecond collisions dropped by the adapter | `clockResolutionDrops = 0` in these windows |
| After `stopReporting()` | second callback count frozen, `registeredSensorType = null`, a repeated `stopReporting()` returned `false` |
| Fresh adapter on a fresh process | unregistered and silent (`deliveredSampleCount = 0`) — the process-recreation case |

### What the adapter readings decide

9. **106 Hz was a good test result, and it reproduced.** The same device, asked the same way,
   delivered 9.0 ms between samples this time (111.1 Hz against G2's ≈106 Hz). The two readings
   differ by more than the request's `maxDelay` interpretation would suggest, which is exactly why
   A-12 rule 3 forbids turning either number into a constant: the rate is an output of the stream,
   never an input to it.
10. **G2's sub-millisecond question was answered by *dropping*, not by carrying more precision.**
    Item 7 asked the adapter to carry `event.timestamp`'s nanoseconds; `MotionSample.elapsedMs` is
    still a `Long` millisecond, so the adapter converts to ms, and a reading that is not strictly
    later than the previous one is discarded and counted instead of poisoning the stream. On these
    windows nothing was dropped (`clockResolutionDrops = 0`), so **the collision path is not
    proven on this device** — it is proven by JVM tests only, and the two-share-one-millisecond
    burst that G2 actually observed would now silently cost a sample. That trade-off is recorded as
    finding 10 in `docs/reports/batch-G3-motion-sensor-adapter.md` and is a real decision for G4.
11. **What still has no measurement**: the strong-magnitude half of Q-G1 (no test may carry the
    phone), and delivery with the screen off or a real background state. G2's "no resumed
    activity" row held the process alive through instrumentation and left the screen on, so it
    bounds the rate; it does not answer A-8's foreground-service question, and G3 did not either.

## The approved rule this must implement (G1-02, unchanged)

`SHIELDRA_DECISIONS.md:21`: confirm after strong/unusual movement lasting **≈3 seconds**, or
**two suspicious movements within 10 seconds**, **after sensor-quality validation**, ignoring
isolated noise. `docs/PHASE4_DECISION_REGISTER.md:29` restates it and requires API feasibility to
precede any adapter.

The pure engine already encodes the decision half:
`ProtectionStateEngine.evaluateMotion` (`app/src/main/kotlin/com/shieldra/domain/ProtectionStateEngine.kt:66-88`)
keeps a motion observation `SIGNAL_ONLY` unless `SensorQuality == VALID`, and confirms on
`duration >= motionStrongDuration` (3 s) or `occurrences >= 2` inside a `<= 10 s` window. The
observation type (`GuardContracts.kt:31-44`) and `ApprovedGuardRules` (`:80-83`) exist too.
**What does not exist is the thing that turns sensor samples into that observation** — the
magnitude window, the quality judgement and the noise rejection. That is Batch G's real work,
and it is pure, testable JVM code.

## Calibration is NOT a decision I may invent

G1-02 fixes the **time** thresholds (3 s / 10 s / 2 occurrences) but says nothing about the
**magnitude** threshold ("strong/unusual") or the noise floor. The device rows above give no
numbers either — a threshold must come from measured samples, not from a guess. So:

- the pure evaluator takes an injected `MotionCalibration` (threshold, hysteresis, minimum valid
  sample rate) and every JVM test asserts against injected values;
- the **shipping defaults** are asked of the owner as **Q-G1**, after the on-device burst has
  produced a measured resting-noise floor and a measured movement magnitude range. The resting
  half is now measured (≈0.52 m/s² worst case, twice); the movement half is not, because no
  automated test may carry the phone — that needs one deliberate carry by the owner;
- until then no screen may show a Motion guard as `Active`.

## UNVERIFIED — requirements to test on the owner's device, not results

The first pass of the harness (previous section) settled three of these rows and left the rest
open. What remains is listed here; nothing below is a result.

| # | Requirement | How it will be tested | Why it is not assumed |
|---|---|---|---|
| 1 | Delivery with the **screen off**, and while the app is genuinely background rather than merely activity-less | instrumented window with the screen off, plus a measured battery delta | **measured so far:** 159 events in 1500 ms with no resumed activity — an **upper bound**, because the instrumentation itself holds the process alive. Android 9+ suppresses continuous delivery to background apps and the exemption set must be proven on this OEM build (`docs/PHASE4_ANDROID_CAPABILITY_MATRIX.md:10` marks Motion CONDITIONAL with exactly this evidence required) |
| 2 | Does the tripwire actually fire on a real carry or pickup of this phone? | arm `requestTriggerSensor` and have the owner move the phone with the dump visible | **measured so far:** the platform accepts the registration and it fired 0 times untouched — which is expected on a still device and says nothing about a carry; vendor HAL quality varies |
| 3 | Movement magnitude on this device | samples from a real carry, then **Q-G1** | only the **resting** floor is measured (≈0.52 m/s² worst case); the strong level has no measurement behind it yet |
| 4 | Whether a foreground service is needed at all, and of which type | only after 1–3 are known | **A-8: no `<service>`, no `FOREGROUND_SERVICE*` until a real monitoring service is in use** |
| 5 | Reboot survival | deferred | **A-10: no `RECEIVE_BOOT_COMPLETED` in this phase** |

## The reporting rules that follow (already binding)

- While no detector runs, the dashboard keeps saying **"No check yet"** and `ATTENTION` —
  `LockGuardDecision.lastCheckSeconds()` returns `null` and `DashboardMode.claimsMonitoring`
  is `false`. Batch F put those there on purpose.
- `MonitoringClaim.ActiveGuardMonitoring` (the only value with `claimsProtection == true`) may be
  produced only when `BackgroundExecutionController.monitoringClaim(detectorRunning = true)`
  is fed a detector that is really running. Nothing before that point may render `Protected`.
- If a foreground service is ever added (A-8), the ongoing notification says
  **"SHIELDRA protection is active"** (A-9) and never "24/7" and never "guaranteed".
- Battery restriction is presented as the owner's **optional recommendation** wording (A-11):
  *"For more reliable background protection on some devices, SHIELDRA recommends allowing
  unrestricted battery usage. Your device manufacturer may still apply additional background
  restrictions."* — not a requirement, and not a claim that the system would kill the service
  without it.

## Explicitly not created by this document

No `SensorManager` call, no listener, no service, no permission and no manifest change **in
`app/src/main`** — the g4b gate fails any of those there. The Batch G sensor harness that produced
the readings above lives in `app/src/androidTest`, registers listeners only while a test runs, and
arms only a one-shot trigger; none of it is reachable from the app. The gates that currently fail a
`<service>` or a second `<uses-permission>` stay exactly as they are; when Batch G earns an FGS,
the gate change and the amendment must land in the same visible commit.
