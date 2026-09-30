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
   practice, so `HIGH_SAMPLING_RATE_SENSORS` is not needed either.
3. **A raw continuous accelerometer watch is the wrong shape on this device.** The accelerometer
   is **non-wakeUp**: it cannot bring the device out of suspend, so an "always sampling" guard
   would have to hold the CPU awake — a battery cost the owner has not approved and §31 forbids
   implying is free.
4. **`SIGNIFICANT_MOTION` is the Android-sanctioned tripwire**: one-shot, **wakeUp**, no
   permission, and it is the platform's own "the device was moved" signal. The shape Batch G
   should therefore take is: `SIGNIFICANT_MOTION` fires → a short accelerometer burst samples
   the movement → the approved G1-02 rule decides → a Security Event is persisted through the
   existing pipeline.

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
  produced a measured resting-noise floor and a measured movement magnitude range;
- until then no screen may show a Motion guard as `Active`.

## UNVERIFIED — requirements to test on the owner's device, not results

| # | Requirement | How it will be tested | Why it is not assumed |
|---|---|---|---|
| 1 | Do sensor callbacks keep arriving when the app is **not** the foreground activity? | instrumented test that registers a listener and reads events after the activity stops | Android 9+ suppresses continuous/on-change/one-shot delivery to background apps; the documented exemption set must be **proven on this OEM build**, not trusted from docs (`docs/PHASE4_ANDROID_CAPABILITY_MATRIX.md:10` marks Motion CONDITIONAL with exactly this evidence required) |
| 2 | Does the burst sample with the **screen off**, and what does it cost? | instrumented test around screen-off plus a measured battery delta | non-wakeUp sensor; Doze; `low_power` state |
| 3 | Does `SIGNIFICANT_MOTION` actually fire on a real carry/pickup of this phone? | manual device check with the dump visible | vendor HAL quality varies |
| 4 | Resting noise floor and movement magnitude on this device | measured samples, then **Q-G1** | thresholds are policy, not physics |
| 5 | Whether a foreground service is needed at all, and of which type | only after 1–3 are known | **A-8: no `<service>`, no `FOREGROUND_SERVICE*` until a real monitoring service is in use** |
| 6 | Reboot survival | deferred | **A-10: no `RECEIVE_BOOT_COMPLETED` in this phase** |

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

No `SensorManager` call, no listener, no service, no permission, no manifest change. The gates
that currently fail a `<service>` or a second `<uses-permission>` stay exactly as they are; when
Batch G earns an FGS, the gate change and the amendment must land in the same visible commit.
