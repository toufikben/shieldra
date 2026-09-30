# Batch G, slice G1 — Motion judgement: samples become the approved observation

**No sensor was registered, no foreground service exists, no permission was added, and no screen
changed in this slice. Motion is still not `Active` anywhere in the app.**

## BATCH
`G-motion-judgement` (first slice of protocol **Batch G — Motion Guard**, §18). It implements the
half the repository was missing between "the owner approved a rule" and "the rule can run": the
approved time rule (G1-02: ≈3 s strong movement, or two suspicious movements inside 10 s, only
after sensor-quality validation, isolated noise ignored) already lived in
`DefaultProtectionStateEngine.evaluateMotion`, but nothing could tell it how long a movement
lasted or whether the samples deserved belief.

## SCOPE
- **`MotionSample`** — one raw triaxial reading with its elapsed time. No Android type.
- **`MotionCalibration`** — the four numbers a Motion judgement needs: strong level, noise floor,
  minimum episode length, maximum tolerated sample gap. **It has no defaults and no shipping
  values**: the magnitude thresholds are **Q-G1**, which the owner must set from measured samples.
- **`MotionWindowEvaluator.read(samples)` → `MotionReading`** — orientation-free magnitude
  (`|√(x²+y²+z²) − 9.80665|`), an episode that opens at the strong level and closes only after the
  deviation falls back through the noise floor (hysteresis), episodes shorter than
  `minEpisodeMs` dropped as isolated noise, and `Unreliable` — never a "still device" — for a
  non-finite sample, non-increasing timestamps, or a hole wider than `maxSampleGapMs`. Fewer than
  two samples is `Insufficient`, which is a third fact and not a quiet phone.
- **`MotionGuardObservation.of(reading, window)`** — the only bridge into the approved engine. It
  maps `Insufficient → UNAVAILABLE`, `Unreliable → NOISY`, `Movements → VALID`, counts only the
  episodes inside the trailing repetition window, and reports an occurrence span **only when two
  episodes really exist**.

Deliberately out of scope (each blocked on evidence, not on effort): `SensorManager` and any
listener (A-8 + the capability matrix's required background-delivery evidence), the foreground
service and its permission and type (A-8: last, and only if the device shows monitoring must
continue outside the foreground), `RECEIVE_BOOT_COMPLETED` (A-10, rejected for this phase), the
shipping magnitude thresholds (Q-G1), and any UI change (§31: a guard with no detector may not
render `Active`).

## FILES CHANGED
- `app/src/main/kotlin/com/shieldra/detection/MotionDetection.kt` (new — 205 lines, pure JVM)
- `app/src/test/kotlin/com/shieldra/detection/MotionWindowEvaluatorTest.kt` (new — 11 JVM tests)
- `.github/scripts/g4b-review-check.sh` (§14 Motion gates, +37 lines)
- `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` (amendments **A-8…A-11** recorded **before**
  any Motion code — commit `7914731`)
- `docs/capabilities/motion-detection-requirements.md` (new — the measured RMX3910 sensor
  inventory and the six requirements still unverified)
- `docs/ROADMAP.md`, `docs/OWNER_QUESTIONS.md` (Q-G2 answered as A-8…A-11; Q-G1 opened)

## DECISIONS ENFORCED
**§18 / G1-02** — the time rule is consumed, not rewritten: `ApprovedGuardRules.motionStrongDuration`
(3 s), `motionRepetitionWindow` (10 s) and `motionOccurrencesToConfirm` (2) are read from the
approved object, and no magnitude threshold ships anywhere. **A-8** — the judgement file contains no
`android.*` import, `app/src/main` registers no listener, and the manifest still has no `<service>`
and exactly one `<uses-permission>`. **A-10** — no `BOOT_COMPLETED` anywhere; the gate now fails if
one appears. **A-11** — no battery-optimisation wording was added, so nothing can overstate it yet.
**§31** — the dashboard still reports `No check yet` / `ATTENTION`; Motion renders `—`.
**§14** — the four owner rulings were committed as visible amendments in `7914731`, one commit
ahead of the code that relies on them. **§4.3 / §2.5** untouched.

## TESTS
CI workflow "Group A diagnostics" on `toufikben/shieldra` `main`:

- **`36788390266` (`2fcc8f0`) — RED, failure as intended.** Conclusion `failure`; the run log
  contains **57 unresolved-reference errors and every one of them is in
  `MotionWindowEvaluatorTest.kt`**, naming exactly the symbols that did not exist yet
  (`MotionSample`, `MotionCalibration`, `MotionEpisode`, `MotionReading`,
  `MotionWindowEvaluator`, `MotionGuardObservation`, plus their members). The only other files in
  the log are pre-existing `w:` warnings (`SettingsContent.kt:29` annotation target,
  `AndroidKeystoreEncryptor.kt:117/136` deprecated API, `StorageRecovery.kt:90` always-true
  condition).
- **`36789435527` (`6f96967`, HEAD) — success.** From that run's own `unit-test-results-36789435527`
  artifact (32 result classes): **259 tests, 0 failures, 0 errors, 0 skipped** — Batch F closed at
  248, so the delta is exactly the 11 new Motion tests. `MotionWindowEvaluatorTest` reports
  `tests="11" skipped="0" failures="0" errors="0"`. Lint, `assembleDebug` and
  `assembleDebugAndroidTest` green; `BUILD SUCCESSFUL in 4m 52s`.

The named scenarios, all passing: `a_strong_movement_that_last_longer_than_the_approved_three_seconds_confirms_a_security_event`,
`a_burst_shorter_than_the_minimum_episode_is_isolated_noise_and_never_an_occurrence`,
`two_strong_bursts_inside_the_repetition_window_confirm_and_a_single_one_does_not`,
`two_bursts_eleven_seconds_apart_are_outside_the_approved_window_and_stay_signal_only`,
`a_silence_longer_than_the_sampling_limit_is_unreliable_rather_than_a_quiet_device`,
`a_non_finite_sample_is_unreliable_and_can_never_confirm`,
`no_samples_are_insufficient_and_are_never_reported_as_a_still_device`,
`the_same_movement_on_a_phone_turned_on_its_side_measures_the_same`,
`values_between_the_noise_floor_and_the_strong_level_start_no_episode`,
`a_movement_that_never_settles_reports_the_full_measured_duration_not_zero`,
`the_peak_recorded_for_an_episode_is_the_largest_deviation_it_saw`.

Three of these assert against the **real approved rule**, not a copy of it: the confirmation
travels through `DefaultProtectionStateEngine.evaluate` with a `Clock` and `IdentityProvider`, so a
change to G1-02 would fail here rather than silently diverge.

## ANDROID TESTS
RUN on the owner device **RMX3910** (`IR7X5DJ7KZGEHMS8`, Android 16 / API 36, no StrongBox) with
`app-debug.apk` and `app-debug-androidTest.apk` taken from the **same** green run `36789435527`:

- Full instrumented suite, **first run on a fresh install: `OK (55 tests)`** (`Time: 5.244`).
- Full instrumented suite, **second run on the same install: `OK (55 tests)`** (`Time: 3.583`).
  Both captured to `.dev-artifacts/device-suite-36789435527.txt` and `-second.txt`.
- Installing over the previously installed package failed with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.shieldra.app signatures do not match`
  (each CI runner regenerates the debug keystore), so **both packages were uninstalled** before
  installing this pair. Consequence stated plainly: the Panic events recorded during the Batch D/E
  verification were cleared from History with them. They were verification data, not incidents.

**NOT RUN / not claimed:** no on-device Motion sample of any kind. This slice has no runtime
surface to exercise — no listener, no service, no screen — so there is no instrumented Motion test
yet and none is implied. The 55-test runs above are a **regression** check that the new pure code
in the APK broke nothing, which is all they prove. The measured numbers Q-G1 needs (resting noise
floor, real movement magnitude range, background delivery, screen-off cost) come from the next
slice, and until they exist Motion may not be shown as `Active`.

## BUILD
`assembleDebug` and `assembleDebugAndroidTest` succeeded in CI `36789435527`; the APKs installed on
the phone came from that run's `apks-36789435527` artifact. No local build was attempted (owner
directive: CI is the verifier on this machine).

## STATIC AUDIT
Run locally on HEAD:
- `.github/scripts/g4b-review-check.sh` — **PASS (41 checks)**, including the new §14 Motion gates:
  the judgement file must exist and contain **no `android.*` import**; `SensorManager` /
  `registerListener` / `DefaultSensor` may not appear anywhere in `app/src/main/kotlin`; no
  `MotionCalibration` parameter may carry a default value; `MotionReading.Unreliable` must remain
  reachable; no `RECEIVE_BOOT_COMPLETED`/`BOOT_COMPLETED` in `app/src/main`; and all eleven named
  Motion tests must still exist under their exact names.
- `.github/scripts/group-c-contract-check.sh` — **PASS** (Domain still has no Android dependency).
- `.github/scripts/phase3-review-check.sh` — **PASS** (still exactly one `<uses-permission>`).

## SECURITY AUDIT
- Attack surface added: **none measurable at runtime.** No new permission, component, file,
  exported surface, network path, or key material. The new code is `com.shieldra.detection`,
  unreachable from the app's UI and from Android callbacks.
- Fail-closed by construction: an untrusted sample set produces `Unreliable`, which the bridge
  maps to `SensorQuality.NOISY`, and the approved engine returns `SIGNAL_ONLY` for any quality
  other than `VALID`. Silence produces `Insufficient` → `UNAVAILABLE` → `SIGNAL_ONLY`. There is no
  path from "no data" to "confirmed".
- No invented security constant: the only numeric literals in the production file are
  `STANDARD_GRAVITY_MS2 = 9.80665` (the physics reference the deviation is taken against, not a
  threshold) and zero-identity values. A gate fails the build if a calibration parameter ever gains
  a default.
- Calibration is a value object passed in by the caller, so a future adapter cannot smuggle a
  threshold into the domain layer.

## SELF-ADVERSARIAL FINDINGS
1. **My first fixture would have made every threshold test fail for the wrong reason.** The helper
   I deleted (`samples()`) applied the deviation on the **x axis**, so `|√(x²+y²+z²) − g|` came out
   near zero and the "strong movement" cases were not strong at all. Caught by working the
   arithmetic through before pushing; the surviving helper puts the delta on the gravity axis.
2. **The eleven-second test originally tested the wrong rule.** It separated the two bursts with a
   single quiet sample, i.e. a 10,000 ms hole, which the evaluator correctly reads as
   `Unreliable` — the test would have re-proved the sampling-gap rule and never reached the
   repetition window. Replaced with 510 real quiet samples at 20 ms, and it now asserts two
   episodes exist before asserting that only one was counted.
3. **Episode end time changed while writing.** Ending at the first *below-floor* sample would
   credit up to one sample interval of movement the sensor never observed. Episodes now end at the
   last above-floor sample; the never-settling case ends at the last sample, pinned exactly
   (`160` samples at `20 ms` → `3180 ms`, not `3200` and not `0`).
4. **`suspiciousOccurrencesWithin` is `null` for one episode.** Reporting a span for a single
   movement would invent a window the samples never contained, and the engine's repetition branch
   requires ≥2 occurrences regardless — so a non-null span there could only ever mislead.
5. **The orientation test is a floating-point test, not just a semantics test.** Nested `hypot`
   could have produced last-bit differences between the flat and side readings and broken exact
   `data class` equality. Summing the squares first keeps it exact: adding `0.0 * 0.0` is exact and
   both axis orders produce the identical sum before one `sqrt`.
6. **One gate I added nearly blocked the next legitimate slice.** The `SensorManager` ban is
   deliberately scoped to `app/src/main`: the *evidence* that justifies an adapter has to come from
   an instrumented test first, which is exactly A-8's ordering. The gate text says so instead of
   leaving a future reader to guess.
7. **I claimed a second device run before I had captured its output.** The re-run was not redirected
   to a file, so the result could not be quoted honestly. I ran the full suite again and captured
   it (`OK (55 tests)`, `Time: 3.583`); the number in this report is from the file, not from memory.
8. **A `-q` commit is not proof of what it contained.** After two quiet commits I checked each with
   `git show --stat` and confirmed the file lists (4 docs files, then the test file alone, then the
   implementation plus the gate) instead of trusting the commit message.

## COMMIT
`7914731` (amendments A-8…A-11 + measured requirements + Q-G1) → `2fcc8f0` (RED, test only) →
**`6f96967`** (GREEN: `MotionDetection.kt` + g4b §14 gates) — HEAD.

## REMOTE
`toufikben/shieldra`, branch `main`. Verified with `gh api repos/toufikben/shieldra/commits/6f96967`
and by `gh run list` showing run `36789435527` against that SHA.

## CI
Green run **`36789435527`** (HEAD `6f96967`) — 259/259 JVM, lint clean, both APKs, g4b 41 checks,
Group C and Phase 3 scans green. Preceding RED run `36788390266` failed as designed.

## ROADMAP
`docs/ROADMAP.md`: Batch G's first slice recorded as **LANDED (CI + DEVICE regression)** with the
honest limits — judgement only, no detector, Q-G1 unanswered, Motion not `Active`.

## STATUS
**Slice G1 CLOSED (CI verified; device verified as a no-regression run).** The repository can now
turn a burst of accelerometer samples into exactly the observation G1-02 was written for, and it
refuses to produce one when the samples do not deserve it. Nothing in the shipping app monitors
anything yet.

## BLOCKERS
- **Q-G1** (open, owner): the strong-movement level and noise floor. This slice deliberately did not
  pick them; the next one produces the measured numbers to answer it.
- **A-8 ordering**: an FGS, its permission and its type stay unadded until the adapter and its
  delivery evidence exist.
- Unrelated and still awaiting the owner: Q-D5/Q-D7 (ads vs §30), Q-C8 (retired-key destruction),
  Q-F1 (Premium/Detail/onboarding delivery claims).

## NEXT BATCH
**Slice G2 — the adapter's evidence, on the owner's phone, before any adapter.** An instrumented
suite that measures on RMX3910 whether a registered accelerometer listener still delivers once the
activity is no longer resumed, whether `SIGNIFICANT_MOTION` fires at all on this vendor HAL, and
what the resting noise floor and a carried-phone magnitude actually look like — reporting measured
values, asserting only facts it can prove. Its output is the input to Q-G1, and only after it can
A-8's "a real monitoring service" condition be judged.
