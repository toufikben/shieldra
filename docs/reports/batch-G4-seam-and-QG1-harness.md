# Batch G / slices G4-seam + Q-G1 harness — the Motion judgement now refuses a stream it cannot believe, and the calibration measurement exists

**Both halves measured and validated something; neither shipped a policy.** No `<service>`, no
`FOREGROUND_SERVICE`, no `specialUse`, no notification change, no runtime monitoring activation, no
Security Event, no severity, no evidence write, no camera, no location was added by any commit in
this batch (`git log --name-only` over `bc9af7d`→`1e4bf6c`). Motion Guard still reads
**"Motion Guard — Not monitoring yet"**, and `MonitoringClaim` is still the only thing that can say
otherwise.

## BATCH

Two slices of the owner's post-G4-A directive, in the order the directive's FINAL RULE sets out
(*A-15 decision → pure G4 evaluator → Q-G1 measurement → owner threshold approval → real FGS
implementation*):

| Slice | What it is | Where it stands |
|---|---|---|
| **A-15** | The owner's decision record: a real Foreground Service architecture is adopted for continuous Motion monitoring. Recorded at `bc9af7d`, CI `36879204285` success. A decision record, **not** permission to write the service. | CLOSED (decision recorded) |
| **G4-seam** | Directive §3/§8: the missing link in `Signal → Validation → Confirmation → Severity → Confirmed Security Event`. The signal layer's stream verdict is now consumed by the Motion judgement instead of being discarded. Pure JVM, injection-calibrated, deterministic. | **CI-green + device-green** |
| **Q-G1 harness** | Directive §5/§6: a test-only device harness that measures one stationary baseline and one deliberately carried window, through the adapter G3 already verified, and reports raw and derived data. | **rest half measured; carry half NOT RUN — it needs the owner's hands** |

## WHAT THE G4 SEAM ACTUALLY CHANGED

`MotionGuardObservation.of(reading, streamQuality, window)` now *requires* the
`MotionSignalQuality` the normalizer computed for the stream it is judging. Before this, the bridge
accepted a `MotionReading` and threw away the verdict that produced it, so a stalled or non-finite
stream could still arrive at the domain engine as `SensorQuality.VALID` and confirm an attack on
evidence the signal layer had already disbelieved.

Two properties the directive asked for, and where they live:

- **Refused before counted.** The quality is checked at the top of `of(...)`; a stream measured as
  `EMPTY` is reported `SensorQuality.UNAVAILABLE`, `INVALID`/`IRREGULAR` as `NOISY`, and in every
  refusal case `duration` is `Duration.ZERO` and `suspiciousOccurrences` is `0`. Nothing about an
  untrusted stream is allowed to leak into the owner's repetition rule on its way to being discarded.
- **Never upgraded.** A `USABLE` stream is the only path that reaches `MotionWindowEvaluator`, and
  the domain layer's existing guard (`Motion sensor quality is not valid`) still has to agree.

The owner's timing rule is untouched: ~3 s of strong motion, or two suspicious episodes inside 10 s,
confirms; isolated noise does not. `MotionCalibration` still has **no defaults**, so there is still
no production strong-motion threshold anywhere in `app/src/main`.

Two vocabularies are now bridged explicitly rather than collapsed: `MotionSignalQuality
{EMPTY, INVALID, IRREGULAR, USABLE}` (what the sampling did) → `SensorQuality
{VALID, UNAVAILABLE, NOISY}` (what the domain may claim).

### What was tested

`app/src/test/kotlin/com/shieldra/detection/MotionStreamQualityTest.kt` — 14 new JVM tests, covering
the directive's §8 list: an irregular/invalid/empty stream is never reported with valid sensor
quality; an untrusted stream reports no movement at all; a repeated movement on an untrusted stream
never reaches the repetition rule; three seconds of strong movement on an untrusted stream stays
signal-only; the identical movement on a trusted stream *does* still confirm (so the seam cannot be
satisfied by always refusing); the refusal holds for every injected calibration; and changing the
injected calibration changes the decision deterministically. Plus the five §7-D boundary cases —
below the noise floor, just under it, between floor and strong level, just under the strong level,
above it — deliberately placed off the exact boundary values so a float comparison cannot make the
evidence flaky.

All 11 owner-named G1 tests and `ApprovedGuardRules` are unchanged. The 6 pre-existing
`MotionWindowEvaluatorTest` call sites now pass `MotionSignalQuality.USABLE`, because those tests ask
what the evaluator decided about its own samples; the refusals belong to the new file.

## EVIDENCE

### CI (the only builder — this workstation cannot assemble an APK)

| Commit | What it was | Run | Verdict |
|---|---|---|---|
| `927f20d` | RED: the seam tests, no seam | `36880439938` | **failure** — `compileDebugUnitTestKotlin`, unresolved `MotionSignalQuality` parameter. Recorded as the red half. |
| `7e3eb0e` | GREEN: seam + gate §18 + harness call sites | `36881558063` | **failure** — three `assertTrue` written in JUnit argument order inside a kotlin.test file. A harness defect, not a red test. |
| `9adde73` | the order fix | `36882705433` | **success** — `CI TEST SUMMARY: 284 tests, 284 passed, 0 failures, 0 errors, 0 skipped`, plus Group C boundary scan, G4-B review and Phase 3 review all PASS. |
| `cc5ca9f` | Q-G1 harness + gate §19 | `36884109178` | **success** — 284 JVM tests, `assembleDebugAndroidTest` compiled the harness into the test APK. |
| `1e4bf6c` | the harness race fix | `36885649601` | **success** — same 284, same three static gates. |

### Device (RMX3910 `IR7X5DJ7KZGEHMS8`, Android 16 / SDK 36)

APK pair from run `36885649601` — both from that one run, because each runner regenerates the debug
keystore. Screen held lit for the whole session with `svc power stayon true` and restored to `false`
afterwards; the condition is recorded in every `ENVIRONMENT` line as
`screen_interactive_at_end=true`.

| Run | Result |
|---|---|
| Full instrumented suite, pass 1 (15 classes; the 4-test G4-A vehicle excluded) | **`OK (70 tests)`** |
| Full instrumented suite, pass 2, same install, logcat streamed | **`OK (70 tests)`** |
| `MotionCalibrationInstrumentedTest` alone | **`OK (2 tests)`** |

The 68 → 70 change is exactly the two new harness methods. **The count did not come free:** the
first device pass, on the `cc5ca9f` pair, reported `Tests run: 70, Failures: 1` —
`java.util.ConcurrentModificationException` at `measure()`. The harness collected samples on the
sensor's handler thread into a `Collections.synchronizedList` and then copied it unsynchronized;
`synchronizedList` guards each call, not an iteration. Fixed by unregistering first and copying under
the collector's lock (`1e4bf6c`). That is a defect in the *measuring instrument*; nothing in
`app/src/main` was involved, and the failure is reported here rather than edited out.

## §7-A — THE REST BASELINE (supplemented, not replaced)

G2's untouched-phone figures stay on the record: **min 0.2853 / median 0.3948 / max 0.5237** and
**min 0.2943 / median 0.3858 / max 0.5050** m/s² of gravity deviation, at ≈106 Hz delivered for a
20 ms request.

The new harness re-measures the same quantity on the *shipping* path (`MotionSensorAdapter` →
`MotionSignalNormalizer(3.0)`), which G2 did not use. Four independent windows, all unattended:

| Window | n | min | median | p90 | p99 | max | median interval | longest gap | clock drops | quality |
|---|---|---|---|---|---|---|---|---|---|---|
| carry-sequence rest, pass 2 (8 s) | 845 | 0.2607 | 0.3964 | 0.4560 | 0.5145 | 0.5462 | 9.0 ms | 10 ms | 0 | USABLE |
| rest-only, pass 2 (12 s) | 1270 | 0.2532 | 0.3901 | 0.4450 | 0.4819 | 0.5197 | 9.0 ms | 14 ms | 0 | USABLE |
| carry-sequence rest, focused (8 s) | 846 | 0.2410 | 0.3816 | 0.4411 | 0.4765 | 0.5112 | 9.0 ms | 10 ms | 0 | USABLE |
| rest-only, focused (12 s) | 1267 | 0.2550 | 0.3865 | 0.4452 | 0.5053 | 0.5456 | 9.0 ms | 19 ms | 2 | USABLE |

m/s². Reading: a still RMX3910 sits at **median ≈ 0.38–0.40**, **p99 ≈ 0.48–0.51** and a
**maximum ≈ 0.51–0.55** — G2's numbers, reproduced within a few hundredths by a different code path,
at **≈111 Hz** delivered against a 20 000 µs request (a request, never a claim; A-12).

**The one thing this batch adds that G2 did not show.** In the focused run, a *carry window that
nobody touched* produced a single isolated reading of **4.5576 m/s²** — eight times the rest median,
and above every probe level the harness swept up to 4.0 — while the window's median stayed at 0.4016
and its p99 at 0.5109. The harness reported it honestly and still said
`deliberate_movement_identified=false`, because the run above the probe lasted **0 ms**: one sample
is not three seconds.

That is a measurement, in the owner's own units, of why §4's rule is shaped the way it is. A
threshold keyed to the *maximum* deviation would have fired on a phone lying still on a table. A
rule keyed to *duration above a level* did not.

## §7-B — DELIBERATE-MOTION OBSERVED RANGES: **NOT RUN**

The directive's §5 is explicit that *the device owner performs the single deliberate
carry/movement manually*, and §11 that *no automated test may physically move the phone*. Both carry
windows in this batch were unattended, so both reported `deliberate_movement_identified=false`, which
is the harness working as designed and not a measurement of a carry.

**No range of deliberate-motion deviation exists yet.** Per §11, the correct response to missing or
contaminated movement evidence is to repeat the measurement, never to infer a number — so nothing was
inferred here.

## §7-C — PROPOSED STRONG-MOTION THRESHOLD: **WITHHELD, deliberately**

The directive's §7-C asks for a proposed level labelled `PROPOSED — NOT OWNER APPROVED`. A proposal
cannot honestly be made from this batch's data, and §7-C cannot be satisfied without inventing the
missing input: a strong-motion level has to sit *above what a real carry produces*, and no carry has
been measured. Rest data alone bounds the **noise floor** (≈0.5 m/s² p99, with isolated transients to
4.6), not the separation between a carry and an attack.

What can be stated without inventing anything is the **shape** the answer must take, since the seam
already consumes it: a gravity-deviation level in m/s², sustained for the owner's ~3 s, or reached by
two episodes inside 10 s — injected as `MotionCalibration`, with no default in production code. The
number itself waits for §7-B.

## §7-D — JVM BOUNDARY CASES: LANDED

The five boundary tests listed above are in `MotionStreamQualityTest` and are among the 284 JVM tests
green on `36885649601`: below the noise floor starts nothing, just under it starts nothing, between
floor and strong level starts an episode but never a confirmation on its own, just under the strong
level starts an episode, and above it an episode begins — all of them with the injected fixture
calibration (`strong = 4.0`, `floor = 1.2`, `minEpisodeMs = 400`, `maxSampleGapMs = 500` m/s²·ms).
They are fixture numbers used by a test to prove the mechanism, **not** candidate product levels, and
they appear nowhere in `app/src/main`.

## WHAT THE GATES NOW FORBID (added to, never relaxed)

- **§18** keeps A-15 a decision record: `<service`, `startForeground`, `FOREGROUND_SERVICE`,
  `foregroundServiceType`, `SPECIAL_USE`, `specialUse` are banned in `app/src/main`; the seam's
  `streamQuality` parameter must have no default; all four `MotionSignalQuality` names must be
  consumed; `MotionGuardObservation` may not be called from `app/src/main` yet; the 14 new test names,
  `guard_subtitle_inactive` in both locales and `enum class MonitoringClaim` are pinned.
- **§19** keeps the harness a measurement: it must sample through `MotionSensorAdapter(` and never
  call `registerListener`/`getDefaultSensor` itself; it must not create Security Events, channels or
  storage writes; it must not drive the device (`executeShellCommand`, `injectEvents`,
  `getUiAutomation`) — the owner's §5 forbids that; the §5 phase durations (8 s / 20 s / 6 s), the
  `RAW`/`DERIVED` split, the probe vocabulary and the §6 field list are pinned; and
  `MotionCalibrationInstrumented` may never appear in `app/src/main`.

Neither gate was loosened to allow this batch's code, and §10's instruction not to weaken existing
guards was followed by not touching them.

## END STATE, CHECKED AGAINST §10

No FGS service · no FGS permission · no `specialUse` · no reboot permission · no wake lock · no
battery exemption · no camera or location permission · no WorkManager · no new production sensor
wiring · `MotionCalibration` has no defaults · the UI still says "Not monitoring yet".

## WHAT IS STILL OPEN

1. **Q-G1 — the owner approves the measured strong-motion calibration.** It cannot be answered from
   this batch: §7-B is NOT RUN. The single thing needed is one manual pass of the harness sequence on
   the RMX3910 (leave it still 8 s → pick it up deliberately and carry it naturally 20 s → put it back
   and leave it still 6 s) while the suite runs.
2. **The real Foreground Service implementation** — authorized in architecture by A-15, still forbidden
   by §2 until Q-G1 is answered. **Stopped here, as §12 instructs.**
3. Unrelated and still waiting on the owner: Q-F1 items 1–2, the deferred onboarding button, Q-F1
   item 4, the Q-D5/Q-D7 ads-and-billing conflict with §30, and Q-C8.
