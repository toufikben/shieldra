# Batch G, slice G4-A — what actually happened to the accelerometer path while the display was dark

**This slice measured something and decided nothing.** No `app/src/main` file was created or changed
by any G4-A commit (`git log --name-only` over `5c27666`→`7111b82` shows only
`app/src/androidTest/...`, `scripts/run-g4a-device-campaign.sh`, `.github/scripts/g4b-review-check.sh`
and `docs/...`). No foreground service, no permission, no wake lock, no manifest change, no screen
change. Motion Guard still reads "Not monitoring yet" in the shipped UI — that is carried over from
G3's verified screenshot and was **not re-opened on the phone in this step (NOT RUN this step)**.

The report answers exactly one question, because amendment **A-14** allows only one: *«ماذا حدث فعليًا
للشبكة الحسّية بينما الشاشة مطفأة؟»* — what happened to the sensor path while the display was dark.
The sentence "a foreground service is required" does not appear in this report as a result, because
A-8's question is the owner's architectural decision, not a measurement outcome.

## BATCH
`G4-A` — the measurement stage in front of slice G4 (`Signal → Validation → Confirmation → Severity →
Confirmed Security Event`). It exists because G3 landed the adapter and the owner ruled, in **A-13**,
that before any of that decision code is written, one number has to be measured on a real phone:
does an accelerometer subscription that was opened while the app was in the foreground keep
delivering samples after the display goes dark?

## WHAT THIS SLICE IS ALLOWED TO SAY
- **A-13** (`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` §17): G4-A is a measurement campaign
  only. Its numbers describe *an instrumented test process on a charging, adb-connected phone*, not a
  production service under Doze, App Standby or OEM restrictions. 111.1 Hz and 52.6 Hz are device
  readings, never specs.
- **A-14** (recorded at `7111b82`, CI `36854114484` success): the report must carry **two buckets** —
  valid evidence, and infrastructure failure that does not enter the conclusion (USB/ADB loss,
  `NO_EVIDENCE`, `TIMEOUT_NO_DATA`, any `CONTAMINATED`). A run that failed for infrastructure reasons
  is not re-run merely to fill a column; a contaminated run is excluded from the conclusion and
  re-run once in clean conditions. Sequence after this report: evidence review → the owner's A-8/FGS
  decision → Batch H **as a decisions package only**, and no H code before an amendment changes the
  authority and the gates themselves.

## METHOD
| Piece | What it is |
|---|---|
| Vehicle | `app/src/androidTest/kotlin/com/shieldra/detection/MotionBackgroundMeasurementInstrumentedTest.kt` — registers `MotionSensorAdapter` **before** the screen goes dark, waits for `PowerManager.isInteractive` to say false (settle deadline 120 s), closes the window on `elapsedRealtime` (the clock the samples carry), counts interactive polls inside the window, then publishes one `G4A_JSON=` evidence line **from the main thread**. Classification is A-13's: `NO_DATA` / `CONTINUING` / `INTERRUPTED`, plus a contamination flag whenever the display was interactive during any poll. |
| Driver | `scripts/run-g4a-device-campaign.sh` at `3320c1e` — installs the CI pair, runs one method at a time, sends `keyevent 224` then `keyevent 223` after 8 s for the dark phases, keeps `logcat` for the vehicle tag **and** `SensorService:W`, carries its own wall-clock deadline (`window_ms/1000 + 240 s`), and on breach force-stops both packages and writes `driver-timeout.json` (schema `shieldra-g4a-driver-timeout-v1`) while leaving `evidence.json` empty. Crossing that deadline is labelled `TIMEOUT_NO_DATA`, never folded into `INTERRUPTED` — the owner's explicit instruction. |
| Phases | A = 60 s with the display lit (reference rate). B = 60 s dark. C = 300 s dark. D = 900 s dark. Three runs each = the 12 runs the owner ordered. |
| Campaigns | **20 device runs in total**: campaign-1 (4 runs, before the driver deadline and the main-thread evidence fix), campaign-2 (the ordered 12), campaign-3 (3 runs, A-14's clean re-run of B), campaign-4 (1 lit control). Every row of all four appears in one of the two buckets below. |
| Independent witnesses | `logcat -b events -s screen_toggled` (survives the driver's per-run `logcat -c`), `post-power.txt` (`mWakefulness` / `mIsInteractive` / `mHalInteractiveModeEnabled` / last interactive hint), `SensorService` cached-event evictions attributed to `app:com.shieldra.app`, and a passive `/proc` observer (`shieldra-g4a-evidence/observe-process-scheduling.sh`) that samples the measured process's kernel counters every 15 s and writes nothing to the device. |

The observer exists because of a hole in the vehicle's own design: a process that is never scheduled
cannot publish its own evidence, so `TIMEOUT_NO_DATA` alone cannot separate "the sensor stopped" from
"the process was never given CPU". It answers the second half from outside the vehicle.

## CONDITIONS (all of them disclosed)
- Device: realme **RMX3910** (`IR7X5DJ7KZGEHMS8`), Android 16 / SDK 36, `Build.BOARD=RM6769`
  (adb reports `mt6768`), build `RMX391023703_16.0.5.1201(EX01)`, ColorOS/OPLUS services running.
- **USB-connected and charging for every run** (`usb_state=rndis,adb`, `USB powered: true`). Charging
  is a Doze blocker; A-13 requires saying so rather than hiding it. Battery 72 %.
- APKs: `app-debug.apk` (18 489 506 B) and `app-debug-androidTest.apk` (610 945 B) downloaded from CI
  run **`36842813807`** (commit `b27f67e`, conclusion *success*) — the same run for both packages,
  because each runner regenerates the debug keystore and a cross-run pair cannot both install.
- `screen_off_timeout` was **1 800 000 ms** for campaign-1/2. For the A-14 clean re-run of phase B
  (campaign-3) it was lowered to **15 000 ms** — the single condition changed, so that a display that
  self-wakes goes dark again on its own instead of staying lit for half an hour. It was restored to
  **1 800 000** after the re-run and verified by reading the value back. `stay_on_while_plugged_in`
  was left at **0** for all campaigns and restored to **15** afterwards, also read back.
- No wake lock, no foreground service, no battery-optimisation exemption, no new permission, no
  `app/src/main` change, no change to the vehicle, and the contamination rule stayed exactly as A-13
  wrote it.
- Raw evidence is **not committed**: it lives outside the repository under
  `Documents/Qoder/2026-09-28/7acdfe05/shieldra-g4a-evidence/{campaign-1,campaign-2,campaign-3,campaign-4}`
  (`summary.tsv`, per-run `evidence.json` / `driver-timeout.json` / `logcat.txt` /
  `sensor-service.log` / `post-power.txt`, `process-scheduling.tsv`, `screen-toggled-events.txt`).
  This report is the durable part; the directories are the primary records it quotes.

## BUCKET 1 — VALID EVIDENCE (runs the vehicle returned from, with the conditions it accepted)

| Source | Phase | Result | Window samples | Median interval | Derived rate | Longest gap | `clockResolutionDrops` | SensorService drops | Started (UTC) |
|---|---|---|---|---|---|---|---|---|---|
| campaign-1 | A 1 | `CONTINUING` | 6335 | 9.0 ms | 111.1 Hz | 14 ms | 0 | 0 | 09:42:56 |
| campaign-1 | A 2 | `CONTINUING` | 6336 | 9.0 ms | 111.1 Hz | 18 ms | 2 | 0 | 09:44:05 |
| campaign-1 | A 3 | `CONTINUING` | 6334 | 9.0 ms | 111.1 Hz | 18 ms | 6 | 0 | 09:45:15 |
| campaign-2 | A 3 | `CONTINUING` | 6328 | 9.0 ms | 111.1 Hz | 10 ms | 0 | 0 | 10:57:34 |
| campaign-4 | A 1 | `CONTINUING` | 3172 | 19.0 ms | 52.6 Hz | 19 ms | 0 | 0 | 13:01:42 |

Every valid run is a **lit-screen** run. That is the whole shape of bucket 1: the measurement produced
usable numbers only while the display was on, and bucket 1 is what the reference rate rests on.

campaign-4 is a one-run control added after the re-run, to prove the vehicle was not broken by the
dark windows: same install, same APK pair, 60 s lit window → it returned normally (`registration_result
"Started"`, `window_closed_within_guard true`, `observer_completed true`). It also disagrees with the
earlier baseline: the same requested 20 000 µs period measured **19.0 ms (52.6 Hz)** there against
**9.0 ms (111.1 Hz)** in the four runs before it. No cause is claimed — the reading sits with the two
numbers, and A-12's rule against hardcoding either one is exactly what this spread is for.

## BUCKET 2 — NO USABLE VEHICLE MEASUREMENT (excluded from the conclusion, cause named per row)

| Source | Phase | Label | Cause, from the witnesses | SensorService drops for this package |
|---|---|---|---|---|
| campaign-1 | B 1 | `NO_EVIDENCE` | Vehicle published from a background thread and the evidence was lost — the defect fixed at `b27f67e`. 623 evictions. | 623 |
| campaign-2 | A 1 | `TIMEOUT_NO_DATA` | **Transport loss, not a measurement.** `post-power.txt` at the deadline says `mWakefulness=Awake`, `mIsInteractive: true` — a lit window, so nothing about the dark path can be read from it; the next run could not see the device at all. | 103 |
| campaign-2 | A 2 | `NO_EVIDENCE` | Same transport loss: `adb.exe: device 'IR7X5DJ7KZGEHMS8' not found` ×3, instrument exit 255. The device returned to adb ~37 min later; `/proc/uptime` showed 2 days 5:38, so it did not reboot. | 0 |
| campaign-2 | B 1 | `CONTAMINATED` (`INTERRUPTED`) | `screen_was_interactive_in_1_of_3_polls`. The window stretched to 87 s and the display came back at 12:00:32 local — during the tail, not the dark. 2012 samples at 19.0 ms. | 0 |
| campaign-2 | B 2 | `CONTAMINATED` (`INTERRUPTED`) | `screen_was_interactive_in_12_of_12_polls`: the whole window was lit. `screen_toggled` shows the display going off at 12:00:52.649 and self-waking at 12:00:54.675 with no `input_interaction`, and with a 30-minute timeout it stayed lit. 6186 samples at 9.0 ms — a lit-window reading, so unusable as dark-window evidence. | 0 |
| campaign-2 | B 3 | `TIMEOUT_NO_DATA` | Genuinely dark; vehicle never returned. 96 evictions. No observer coverage (started 12:25 local). | 96 |
| campaign-2 | C 1 | `TIMEOUT_NO_DATA` | Genuinely dark; never returned. 220 evictions. No observer coverage. | 220 |
| campaign-2 | C 2 | `TIMEOUT_NO_DATA` | Genuinely dark; never returned. 221 evictions. Only one observer row (the observer started as it ended). | 221 |
| campaign-2 | C 3 | `TIMEOUT_NO_DATA` | Genuinely dark; never returned. 221 evictions. Observer: 532 s, `+2/+1` ticks. | 221 |
| campaign-2 | D 1 | `TIMEOUT_NO_DATA` | Genuinely dark; never returned. 520 evictions. Observer: 1128 s, `+2/+0` ticks. | 520 |
| campaign-2 | D 2 | `TIMEOUT_NO_DATA` | Genuinely dark; never returned. 521 evictions. Observer: 1127 s, `+17/+5` ticks. | 521 |
| campaign-2 | D 3 | `TIMEOUT_NO_DATA` | Genuinely dark; never returned. 520 evictions. Observer: 1127 s, `+0/+0` ticks. | 520 |
| campaign-3 | B 1 | `TIMEOUT_NO_DATA` | Clean re-run, genuinely dark. 100 evictions. Observer: 229 s, `+0/+0` ticks. | 100 |
| campaign-3 | B 2 | `TIMEOUT_NO_DATA` | Clean re-run, genuinely dark. 100 evictions. Observer: 295 s, `+13/+3` ticks. | 100 |
| campaign-3 | B 3 | `TIMEOUT_NO_DATA` | Clean re-run, genuinely dark. 97 evictions. Observer: 286 s, `+16/+6` ticks. | 97 |

`A 1` and `A 2` were **not** re-run, as A-14 orders: their cause is the cable/adb transport, the lit
baseline is already carried by four valid A runs, and the rows stay in the table with their cause.

## THE CLEAN RE-RUN OF B THAT A-14 REQUIRED

campaign-3 ran phase B three times with the display timeout shortened (15 000 ms) and nothing else
changed. Before the campaign, a 44-second probe (`keyevent 224` → `223` → poll every 5 s) showed the
screen going dark at 13:38:40.216 and **not** self-waking — the campaign-2 wake source did not
reproduce.

Result: **none of the three runs was contaminated**, which is the point of the re-run — the
contamination in campaign-2 B 1/B 2 was a lit display, and it is now excluded. All three ended as
`TIMEOUT_NO_DATA` with the reap witnesses agreeing:

- `post-power.txt` at the moment the driver stopped the run: `mWakefulness=Asleep`,
  `mIsInteractive: false`, `mHalInteractiveModeEnabled=false`, and `mLastInteractivePowerHintTime`
  **296 651 ms earlier** in run 1 (296 434 ms in run 2) — the display had been dark for the whole
  300-second deadline, not merely at the moment of reading.
- `evidence.json` 0 bytes in all three, `driver-timeout.json` present with
  `"vehicle_emitted_evidence":"false"`, `"deadline_s":"300"`, `"screen_off_requested":"1"`.
- 100 / 100 / 97 SensorService evictions attributed to `app:com.shieldra.app`.

So the honest outcome of the mandated re-run is not a clean number: it is that **a 60-second dark
window already behaves like the 300- and 900-second ones.** Duration is not the variable that decides
whether the vehicle gets to report.

## WHAT THE INDEPENDENT WITNESSES MEASURED

**1. The process under measurement was not executing.** From the passive `/proc` observer — 297 rows,
sampled every 15 s, three campaigns:

| Run | pid | Rows | Observed span | `utime` delta | `stime` delta | Thread states seen | `mWakefulness` reads |
|---|---|---|---|---|---|---|---|
| campaign-2 C 3 (300 s dark) | 11918 | 33 | 532 s | +2 | +1 | all `S` | Asleep 8/8 |
| campaign-2 D 1 (900 s dark) | 12851 | 70 | 1128 s | +2 | +0 | all `S` | Asleep 18/18 |
| campaign-2 D 2 | 17002 | 70 | 1127 s | +17 | +5 | all `S` | Asleep 17/17 |
| campaign-2 D 3 | 19059 | 69 | 1127 s | +0 | +0 | all `S` | Asleep 18/18 |
| campaign-3 B 1 (60 s dark) | 23572 | 15 | 229 s | +0 | +0 | all `S` | Asleep 4/4 |
| campaign-3 B 2 | 24319 | 19 | 295 s | +13 | +3 | all `S` | Asleep 5/5 |
| campaign-3 B 3 | 25584 | 18 | 286 s | +16 | +6 | all `S` | Asleep 4/4 |
| **campaign-4 A 1 (60 s lit control)** | 28495 | 2 | 16 s | **+23** | **+9** | all `S` | **Awake 1/1** |

In the lit control the counters advance at ~2 ticks/s; in every dark run they advance 0–22 ticks over
spans of 229–1128 s, i.e. 0.00–0.08 ticks/s — 25 to 300 times less, and two runs (campaign-2 D 3 and
campaign-3 B 1) advanced by exactly nothing. No sample was ever `R`. `runnable_other` (the kernel's 1-minute load average) stayed at
21–25 throughout, so the SoC as a whole kept executing other work while this process accumulated
essentially no CPU. *(Ticks are `/proc/<pid>/stat` fields 14/15; converting them to seconds assumes
USER_HZ = 100, the usual Linux/Android value, and was **not verified** on this kernel — so the tick
counts are the primary numbers and the seconds are indicative.)*

**2. The platform kept the subscription and kept a full queue.** Every run's `sensor-service.log`
starts with `SensorEventConnection sensor handle=1, package = com.shieldra.detection.MotionSensorAdapter
enable` — the registration survived the screen going dark. Then evictions, all of them reading
`Dropping 1 cached events (4500/4500) to save 1/1 new events ... app:com.shieldra.app type=1`: the
cache stayed **full at 4500/4500 for the entire window**, from 100 evictions in a 60 s-class dark run
to 521 in a 15-minute one. In campaign-3 run B 1 they arrived on a strict ~2.003 s cadence
(13:41:55.414, 13:41:57.417, 13:41:59.421, …) with the cumulative counter going 0 → 105 on the second
line and then staying at 105.

That cadence is ambiguous and both readings are kept: (i) only ~0.5 events per second reached the
connection once the display was dark, or (ii) the eviction log's own rhythm is a platform timer and
says nothing about the sensor's rate. What neither reading can dispute: **the connection was never
drained**, and the platform was still handling events for this package while the app was not running.

**3. `screen_toggled` explains the contaminated runs.** The events buffer (`logcat -b events`, which
the driver's per-run `logcat -c` does not clear) shows campaign-2's B 2 window: off 12:00:52.649 → on
12:00:54.675, a 2.0-second self-wake with no `input_interaction`, then lit for the rest of the window
because the display timeout was 30 minutes. B 1's single interactive poll corresponds to the
12:00:32 wake. After 12:16:28 the display stayed dark for the remaining 75 minutes of campaign-2,
which is why C and D were never contaminated. The self-wake did not reproduce in campaign-3.

## THE ANSWER TO THE ONE QUESTION

For the display-dark case on this RMX3910, across 10 genuinely dark windows (60 s, 300 s, 900 s;
including the three clean re-runs A-14 required):

1. The accelerometer subscription that was opened while the app was in the foreground **is accepted by
   the platform and stays registered** through the dark window.
2. **Not one sample reached the app's own sink** during any dark window: the vehicle never reached its
   publish point in 10/10 runs, and its per-run `evidence.json` is 0 bytes in every one of them.
3. The reason the vehicle gave nothing is visible from outside it: the measured process **consumed
   essentially no CPU** (0–22 ticks over 229–1128 s, thread state never `R`) while the platform side
   kept an undrained, full 4500-entry queue for this package and logged evictions against it for the
   whole window.
4. Consequently **there is no measured delivery rate with the screen off on this device.** Bucket 1
   contains only lit windows; every dark window is in bucket 2. The delivery question stays open
   *as a measurement*, and the numbers that do exist (111.1 Hz, 52.6 Hz) describe lit-screen rates only.

That is the entire claim this slice makes. It is a statement about a frozen/scheduled-out test process
on a charging phone, which is what A-13 says it is.

## WHAT THIS DOES NOT ESTABLISH
- **Not** whether a foreground service is required, would help, or would not help. A-8 stays the
  owner's decision; A-14 explicitly says "FGS مطلوب" is not a campaign result.
- **Not** whether a *production* service (not an instrumented test process, not tied to an
  instrumentation daemon, subject to Doze/App Standby/OEM restrictions, on battery) would receive
  samples with the screen off. A-13's limits apply verbatim and none of them was relaxed here.
- **Not** whether the hardware kept sampling at the configured rate. The observer sees the framework
  and the process, not the IIO/HAL queue, and the eviction cadence is ambiguous by design.
- **Not** whether Motion detection is accurate, whether any magnitude means an attack, or anything
  about a Security Event. Q-G1's strong-magnitude half still needs one deliberate carry of the phone,
  which no automated test may perform.
- **Not** anything about camera, location, or Batch H. Per A-14 those parameters (capture duration,
  photo count, location precision, retention, whether camera is allowed at all) stay the owner's and
  are not proposed here; `group-c-contract-check.sh` ("No camera, location collection, or network
  implementation") and `g4b-review-check.sh` (exactly one `<uses-permission>`, `<service>` = failure)
  still forbid any H code, and this report does not ask to relax them.

## SELF-ADVERSARIAL FINDINGS
1. **The campaign's headline number is an absence, and absences are easy to over-read.** "No sample
   reached the app" is solid (0-byte evidence files plus kernel counters). "The sensor stopped
   delivering" is *not* supported, and the 2.003 s eviction cadence cannot settle it either way.
2. **`mWakefulness` was only sampled every 60 s by the observer** (1 in 4 rows), so a very short
   lit period inside a dark window would not appear in the tsv. `post-power.txt` closes this only at
   the end of a run; for the reaped runs the strongest available statement is the 296 s
   "last interactive hint" age, which covers the whole deadline.
3. **`ap_suspend_ms` is not a trustworthy field.** It reported `-509` in campaign-3's lit control and
   in campaign-2's B 2 — a negative suspension time is meaningless. It comes from mixing wall-clock
   and `elapsedRealtime` and is recorded here as a defect of the vehicle, not used as evidence.
4. **The lit baseline itself is not stable** — 111.1 Hz four times, then 52.6 Hz once, same requested
   period, same device, same build. Two of A-12's "never hardcode a measurement" cases are now visible
   in one table. No cause is claimed.
5. **The observer ran only from 12:25 local in campaign-2**, so C 1 and C 2 have no `/proc` rows (C 2
   has one) and their starvation claim rests on the eviction log alone. campaign-1's runs have no
   observer at all.
6. **Thread state `S` alone would be uninteresting** — a process blocked in `Thread.sleep` is also `S`.
   The load of this finding is in the *frozen utime/stime deltas over 229–1128 s* compared with the
   lit control's 2 ticks/s, not in `S` itself. Stating `S` without the deltas would have been the
   overstatement.
7. **`USER_HZ = 100` was assumed, not verified**, so the seconds in the observer table are indicative.
8. **A single 16-second lit control is a thin control.** Two rows were captured because the run
   finished before the observer could take more; the control proves the vehicle returns, not the exact
   scheduling rate.
9. **The contamination rule was never touched, and that is why the clean re-run produced no numbers.**
   A rule that flags any interactive poll is strict and honest; combined with a platform that starves
   dark processes, it means a clean dark run cannot both be clean and report a rate. Fixing that would
   require changing the vehicle, which the owner forbade in this step.
10. **Nothing here was verified visually on the app's UI in this step** — no screen was opened, so
    "Motion still reads Not monitoring yet" is inherited from G3 plus the fact that no `app/src/main`
    file changed, not re-observed today. Marked NOT RUN rather than PASS.

## FILES CHANGED
| File | What happened |
|---|---|
| `docs/reports/batch-G4A-background-sensor-measurement.md` | this report |
| `docs/ROADMAP.md` | Batch G row and the "Required next batch" narrative now carry G4-A's measured outcome |
| `docs/OWNER_QUESTIONS.md` | G4-A result note + the A-8 question re-opened as **Q-G3** with options only, and Batch H recorded as decisions-only per A-14 |

No code file was changed by this slice's closing step; the code that made the measurement possible
landed earlier (`d922140`, `f952b23`, `b27f67e`, `aa1af0d`, `3320c1e`).

## COMMIT / REMOTE / CI
- `5c27666` — docs(authority): record A-13 before any G4-A code exists
- `d922140` — test: the measurement vehicle + gate section for it
- `f952b23`, `b27f67e` — fix: close the window on the samples' own clock; publish evidence from the main thread
- `aa1af0d`, `3320c1e` — fix: the driver stops discarding a measured run; enforces its own wall-clock deadline
- `7111b82` — docs: amendment **A-14** (two buckets, the B re-run, the H gate) — CI `36854114484` **success**
- APK pair used on the phone: CI `36842813807` (commit `b27f67e`) — **success**
- Remote: `toufikben/shieldra`, branch `main`, workflow "Group A diagnostics".

## STATUS
**MEASUREMENT COMPLETE — INCONCLUSIVE AS A DELIVERY RATE, CONCLUSIVE AS A STARVATION OBSERVATION.**
G4-A produced valid numbers only for lit windows and produced, for dark windows, ten reaped runs with
independent witnesses that agree the app process did not run while the platform kept an undrained
sensor queue for it. Per A-13, this does not establish motion-detection accuracy, security-event
correctness, background survivability of a production build, or whether a foreground service is
required.

## BLOCKERS
Nothing blocks the *report*. What blocks G4 (and is not a code problem):
1. The owner's **A-8/FGS decision** on the evidence in this report — now raised as **Q-G3** with
   options, no recommendation from me.
2. **Q-G1's strong-magnitude half** — one deliberate carry of the phone by the owner.
3. Whether any further measurement is wanted at all (e.g. the same campaign on battery instead of
   charging), which only the owner can price.

## NEXT
Per A-14, in order: this report → the owner reviews the evidence → the owner answers A-8/FGS →
Batch H arrives **as a decisions package only** (Q-C5's capture duration, photo count, location
precision/retention, and whether camera is allowed at all), with no H code and no gate relaxation
until an amendment (next free number **A-15**) changes the authority and the gates themselves.
