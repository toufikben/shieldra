# Batch K — the five owner answers, applied

**Date:** 2026-10-02
**Owner input:** the five-answer decision message of 2026-10-02 (Q-K-1 … Q-K-5), recorded as amendments
**A-20 … A-24** in `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` §17.
**Verification target:** `main` at `c8343fe` (CI run `37037762042`: **341 tests, 341 passed, 0 failures, 0
errors, 0 skipped**, and its release step printed
`PASS: the device APK pair is published on draft release device-apks for c8343feec3390d43646e8d8a76083727c664373e`).
`c8343fe` is docs-only, so the production code it names is `902f15c`'s, whose own run `37034116024` is green
with the same 341/341 and the same PASS line.
**What this report is:** the execution record for those five answers — what landed, which CI run proves
each part, what was measured on the phone today, and what is still owed. It gives **no security sign-off.**
It also raises one new owner question, **Q-K-6** (§7.3), and one wording finding, **F-K7** (§7.2).

---

## 1. Where each answer stands

| Answer | Landed as | CI proof | Really proved | Still owed |
|---|---|---|---|---|
| **Q-K-1** remove the Premium tab and route | `e8bb16e` (RED) → `d0573bf` (GREEN) | `36991054611` (RED) · `36994802842` (GREEN) · re-verified on `902f15c` by `37034116024` | the tab and route are gone and a test fails if any declared route names a premium screen again, **and the phone now shows a three-item tab bar (Home · History · Settings) on a build carrying the removal** — §7.2 | nothing for this answer; the F-K1 screen-side half is closed |
| **Q-K-2** retention executes | `e8bb16e` (RED) → `d34ed86` (GREEN) | `36993925242` · `36994802842` · `37034116024` | the 30/30/90 windows decide real deletions in the pure planner, the three authorized execution points are wired, **and two device tests removed real ciphertext from `no_backup/` on the RMX3910** — §7.3 | HEAD's own version of the third test to run green on a phone, which is Q-K-6, and the Settings "Policy pending" wording (F-K6) |
| **Q-K-3** API 36 | `d06cfc8` (RED gate) → `8da7686` (GREEN) | `36994944518` (RED) · `36995812606` (GREEN) · `37034116024` · `37037762042` | the release compiles and targets 36, every gate passes, 341/341 JVM, **a `targetSdk=36` build installs and runs on this Android 16 / API 36 phone with every screen rendering and notifications posting** (§7.2), **and §3's item list was then probed one by one** — edge-to-edge by bounds on four screens, back by two measured pops, the FGS type read as `specialUse` with its ongoing notice, 3 declared / 1 runtime permission, `sw360dp` under the 600 dp threshold (§7.4) | landscape, which this phone refused to rotate into (**NOT RUN**, §7.4), and the 16 KB page-size item, which a 4096-byte-page phone cannot test either way |
| **Q-K-4** listing vocabulary | amendment **A-23** | docs-only | the allowed and forbidden sentences are written down | the Play Console text itself — a Batch L submission-time task, **plus F-K7, the dashboard's unmeasured "Offline" (§7.2)** |
| **Q-K-5** CI artifacts | `e78694e` + `589f4a5`/`a147bef` (§6.5) | `36989921551` · `37003070708` | a missing required APK now fails the run visibly, and future APKs expire in 14 days | closed for delivery: the pair now publishes to a required release channel, so the quota no longer blocks a download — **nothing was deleted**, §6.5. The pair for `902f15c` was downloaded and hash-verified in §7.3 |

---

## 2. Q-K-1 — the Premium tab is gone

The owner's ruling: *"Remove the Premium tab and its route from the current release. Do not implement
Billing. Do not leave a purchasable-looking Premium screen with fake price/features."*

**RED first** (`e8bb16e`, run `36991054611`): `ReleaseNavigationSurfaceTest` was added before any
deletion. On CI it failed exactly where intended —

```
ReleaseNavigationSurfaceTest > qk1_no_declared_route_names_a_premium_screen() FAILED
ReleaseNavigationSurfaceTest > qk1_released_tab_bar_has_exactly_dashboard_history_settings() FAILED
341 tests completed, 2 failed
```

**GREEN** (`d0573bf`, run `36994802842`): 8 files changed, **−195 lines, 0 added**. Removed: the
`ShieldraRoutes.PREMIUM` route, `BottomTab.Premium`, its `composable {}` block, its icon and label,
`PremiumScreen.kt` and `PremiumCallbacks.kt` (directory deleted), `DemoData.premium()`,
`PremiumUiModel`/`PremiumFeatureUiModel`, and 14 string keys in each of the two locales. No billing SDK
was added, no replacement monetization screen was invented. `guard_premium_feature` and
`isPremiumLocked` stayed — they belong to the guard tiles, not to a store.

Run `36994802842`: `CI TEST SUMMARY: 341 tests, 341 passed, 0 failures, 0 errors, 0 skipped`,
`BUILD SUCCESSFUL in 7m 30s`, Group C boundary scan, `G4-B LOCAL REVIEW PASS` and
`PHASE 3 STATIC REVIEW PASS` all green. Its only red step was the APK upload (§6).

---

## 3. Q-K-3 — `compileSdk`/`targetSdk` 36

The owner's ruling: *"Raise compileSdk and targetSdk to 36 now. Do not request a Play Console extension
unless an actual technical blocker appears… Do not assume that compile success means device compatibility."*

**RED** (`d06cfc8`, run `36994944518`): the static gate was written first and failed on CI with
`FAIL: compileSdk is not 36 (Q-K-3)` while the build itself stayed green — the gate, not the build, was
the thing under test.

**GREEN** (`8da7686`): `app/build.gradle.kts:9` `compileSdk = 36`, `:14` `targetSdk = 36`,
`.github/workflows/group-a-diagnostics.yml:37` `platforms;android-36`,
`.github/scripts/group-a-diagnostics.sh:56` `build-tools;36.0.0`. The gate now pins all three, so a
silent revert to 35 fails CI.

**Exact-SHA verdict — run `36995812606`, headSha `8da7686f74a711abc281fdee06399f74e332f507`:**

| Step | Conclusion |
|---|---|
| Install required SDK packages (android-36) | success |
| Run Group A diagnostics | success |
| Run Group B and Group C tests and Android build | **success** — `BUILD SUCCESSFUL in 7m 10s`, `CI TEST SUMMARY: 341 tests, 341 passed, 0 failures, 0 errors, 0 skipped` |
| Run Group C boundary scan | success |
| Run G4-B local storage review | success (now asserts API 36) |
| Run Phase 3 static review | success |
| Upload built APKs for owner-device execution | **failure** — `Failed to CreateArtifact: Artifact storage quota has been hit` |
| Overall run conclusion | **failure**, caused only by that upload |

So API 36 is proven to **compile, lint, pass every gate and pass every JVM test**. It is **not** proven to
run correctly on the phone, and that gap is deliberate: the run produced no APK to install (§6).

### Android 16 verification items, from the official pages

Per §25 these come from the Android Developers "Behavior changes" pages for API 36, fetched 2026-10-02,
not from a summary or community reading. The list below is what the device pass has to look at once an
APK exists; nothing here has been measured yet.

| Official change (apps targeting 36) | Does it touch this app? | What the device pass must check |
|---|---|---|
| Edge-to-edge opt-out is disabled — `windowOptOutEdgeToEdgeEnforcement` no longer works | **Yes, UI-wide** | every screen draws under the status bar without clipped text; the dashboard header and the guard tiles keep their spacing |
| Predictive back gesture is on by default; `onBackPressed` is not called and `KEYCODE_BACK` is not sent | **Yes** — History → Event Detail → Back | the detail screen returns to History with the tab bar intact; no screen exits the app |
| Adaptive layouts: on displays ≥ 600dp min-width, `screenOrientation` / resizability / aspect-ratio restrictions are ignored | **Phone is < sw600dp** — expected no effect | confirm the RMX3910 pass still locks to portrait, and note the finding if it does not |
| Fixed-rate scheduling optimization: at most one missed `scheduleAtFixedRate` run is executed | **No** — no such scheduler is used (G5 forbids polling/timers) | nothing |
| Health & fitness permissions replace `BODY_SENSORS` for `FOREGROUND_SERVICE_TYPE_HEALTH` | **No** — Motion uses `specialUse`, not a health FGS type | confirm the FGS type and its permission are unchanged in `dumpsys activity services` |
| `MediaStore#getVersion()` per-app string; Safer Intents (opt-in); GPU syscall filtering | **No** — no MediaStore, no exported receiver/provider/deep link (A-10), no custom GL | nothing |
| Local-network runtime permission (opt-in flag today) | **No** — no `INTERNET` permission at all | re-run the permission probe: still 3 declared / 1 prompted |
| All apps on 16: Job-Scheduler quota by standby bucket, 16 KB page-size compatibility dialog | **No WorkManager/JobScheduler**; 4 KB alignment with no native libs | confirm no page-size compatibility dialog appears on launch |

The two real items are therefore **edge-to-edge** and **predictive back** — both UI, both reachable in the
nine-screen pass, and neither one provable by a compile.

---

## 4. Q-K-2 — the retention windows now decide real deletions

The owner's ruling: *"Automatic expiry is in release scope. Make the approved retention policy execute in
production… Do NOT introduce WorkManager, AlarmManager, polling, or a new background scheduler solely for
retention. Do NOT change the retention numbers. Do NOT claim wall-clock deletion at the exact expiry
instant when the process is not executing."*

**The numbers were not touched:** 30 days evidence, 30 days quarantine, 90 days event metadata
(`EvidenceRetentionPolicy` constants unchanged; a test asserts all three).

**One planner, one clock source.** `EvidenceRetentionPolicy.planExpired(...)` is the only place a window
is read; it takes stored ages and a clock and returns the ids whose window has closed. Boundary is
inclusive, matching `isExpired`. An artifact with no known age is never deletable, and a clock that moved
backwards deletes nothing — both pinned by tests.

Evidence age is taken from the **owning event's immutable `created_at_epoch_ms`** through a Room JOIN, not
from the file's `lastModified()`. That choice is load-bearing: key rotation rewrites the payload, so file
mtime moves forward on every rotation and a 30-day window judged on mtime would renew itself forever.
Quarantine keeps using mtime, because a rename preserves it and a quarantined file has no Room row.

**The three authorized execution points, and only those:**

1. **Application/process startup** — `ShieldraApp.checkEvidenceRotation` sweeps before rotation on the
   same thread, each in its own `try`/`catch`.
2. **`MotionMonitoringService` startup** — `storage.scheduleRetentionSweep()` after the storage check.
3. **After persisting a Security Event / evidence record** — `RoomEventRepository` takes an
   `onRecordPersisted` callback wired to `retention::schedule`; it fires from `save()` and
   `recordEvidence()`, and deliberately not from `recordDeliveryAttempt()`.

No scheduler, no timer, no polling, no new permission. Cleanup therefore happens **when the app is
already running** — which is exactly the wording the owner required, and the reason the Settings row must
not promise an instant.

**RED** (`e8bb16e`, run `36991054611`): the seven `qk2_*` tests could not even compile —
`Unresolved reference 'planExpired'` / `'RetainedArtifact'` — which is the correct failure for behaviour
that did not exist yet. **GREEN** (`d34ed86`, run `36993925242` then `36994802842`): all seven pass, and
`36994802842` shows the whole suite at 341/341 with every static gate green.

**NOT RUN:** `EvidenceRetentionSweepInstrumentedTest` (three on-device cases: an expired event and its
payload go while a young pair survives; a quarantined file past 30 days goes and a young one stays;
nothing expired leaves everything in place). It compiles in CI — `assembleDebugAndroidTest` is green on
every run above — but has never been executed. It could not be, while no APK pair was downloadable (§6);
the pair is downloadable now (§6.5), so what stands between this test and a result is an unlocked phone.

What a waiting review *could* still prove, without a device: the three cases match the production constants
they lean on — the test's `.quarantine` directory and `.shieldra` suffix are `EncryptedEvidenceFileStore.kt:266,268`,
the ids it asserts come from `name.removeSuffix(FILE_SUFFIX)` at `:158`, and the sweeper maps a payload's age
through the owning event (`EvidenceRetentionSweeper.kt:91-98`), which is the exact thing case 1 exists to
catch. Each case also opens a uniquely-named database and evidence directory, so the suite can run twice on
one install without a prior case's rows leaking into the next. What remains device-only is the deletion
itself and whether `setLastModified` is honoured on this OEM's filesystem.

---

## 5. Q-K-4 — release-facing vocabulary

Recorded as **A-23**. Allowed: *"SHIELDRA uses only the Android permissions required by the features you
enable"* and, for Motion, *"Motion monitoring runs as a user-started foreground service and shows an
ongoing notification while it is active"*. Forbidden: "Zero Android permissions", "24/7 protection",
"Always protected", "Guaranteed monitoring", and any claim that Lock/SIM/Camera/Location are active.

No product sentence unrelated to those rules was rewritten — the owner's instruction was explicit:
*"Do not rewrite unrelated product claims unless they are contradicted by actual implementation."* The
Console text itself is a Batch L task, because a package id and its listing are a one-way door and this
build has not been submitted.

---

## 6. Q-K-5 — the CI artifact quota, and the one thing it now blocks

### 6.1 What the workflow change proved

`e78694e` removed `continue-on-error` from the **required** APK upload and set `retention-days: 14` on it.
The two diagnostic uploads keep `continue-on-error` (`.github/workflows/group-a-diagnostics.yml:83,92`) —
they are not device evidence and must not be able to fail a build.

Run `36989921551` is the proof the owner asked for: `BUILD SUCCESSFUL in 7m 28s`, `332 tests, 332 passed`,
every gate green, and the run's **only** failed step is `Upload built APKs for owner-device execution` —
so the run's overall conclusion is **failure**. A missing APK is now visible, not hidden.

The same run also shows why the old green runs lied: its two diagnostic upload steps printed the same
`Failed to CreateArtifact` error and were still reported **success**, purely because of
`continue-on-error`. That is visible again in `36995812606`, in this report's own commit
(`a8ad383` → run `36998736737`: `341 tests, 341 passed`, `BUILD SUCCESSFUL in 8m 23s`, Group C + G4-B +
Phase 3 all green, **only** the APK upload red), and in the F-K3 gate commit that follows it.

### 6.2 Current inventory (measured 2026-10-02, `gh api …/actions/artifacts`, 3 pages)

| | earlier today | now |
|---|---|---|
| artifacts | 301 | **257** |
| total size | ≈ 1,215 MiB | **669.4 MiB** |
| `apks-*` | 69 | **38** (667.7 MiB — 99.7% of everything) |
| artifacts already past `expires_at` | — | **0** |
| earliest `expires_at` remaining | — | **2026-12-22** |

Two conclusions follow directly. First, ~546 MiB of already-expired artifacts has been reaped without any
action here, yet every upload still fails — GitHub's usage figure is recalculated on its own schedule, so
the error can outlive the bytes. Second, and more important: **nothing left on the list expires before
2026-12-22**, so the quota will not heal by itself. Those 38 APK pairs are the whole problem, and each is
~17.5 MiB.

### 6.3 Which APK pairs are evidence

Every one of the 38 run ids is cited somewhere in `docs/`, so "unreferenced" is the empty set. The owner's
rule is narrower — keep what a **device-verification** report relies on — so each id was re-checked against
the sentences that cite it. That check corrected my own first pass: five pairs I had flagged as obsolete
are in fact cited as installed builds and are **kept** —
`36884109178` ("first device pass, on the `cc5ca9f` pair"), `36842813807` ("APK pair used on the phone"),
`36791998789` ("APKs downloaded and run"), `36779189112` ("the same pair was run against `1502726`"),
`36770817105` ("verified on the device"). The current install, `36939853591`, is kept.

Six pairs are cited **only** as "commit X → CI success" and belong to **docs-only commits**, so their
bytes were never installable evidence of anything: `36875623738`, `36874698595`, `36874321721`,
`36854114484`, `36598839725`, `36559760906` — ≈ **105 MiB**. Deleting all six still leaves ≈ 564 MiB.

### 6.4 The decision I am not making

GitHub's plan-quota table gives **500 MB** for GitHub Free and 1 GB for Pro (fetched 2026-10-02; note the
fetch was served through a proxy URL rather than the canonical docs host, so the figure is marked
*re-confirm in Batch L* rather than assumed). If 500 MB applies to this account, then:

- the six provably-non-evidence APK pairs free ~105 MiB → ~564 MiB, **still over**, and
- a new pair costs ≈ 17.6 MiB (667.7 MiB ÷ 38), so fitting the next upload needs **≥ ~187 MiB gone — about
  eleven pairs**, not six, and
- getting under the limit requires deleting **superseded code-build APKs** whose runs are cited in
  reports as build verdicts — bytes that no longer have a documented device role but that would let
  someone re-install an older build.

Deleting them is irreversible and the owner's constraint was explicit ("Do not delete evidence artifacts
blindly"), so this is isolated as the one open dependency rather than resolved by guessing. The three
ways forward, in the order I would rank them:

1. **Delete the six docs-only pairs now** (~105 MiB, zero evidence cost) **and additionally** the
   superseded pre-Batch-G code pairs, keeping every pair a report cites as an installed build. Needs one
   owner line naming the cut-off (a date, or "keep only the last pair of each batch").
2. **Raise the plan** (Pro = 1 GB) — no deletion, no code change.
3. **Move device APKs out of Actions artifacts** (e.g. release assets) — a new delivery path, and it
   changes how "the APK pair from the same CI run" is defined, so it needs its own approval.

Until one of them lands, `d0573bf`/`d34ed86`/`8da7686` are verified **by CI only**: Q-K-1's screen, Q-K-2's
instrumented sweep and Q-K-3's Android 16 behaviour all stay open, and Batch K cannot close.

### 6.5 Q-K-5b — the owner picked way 3, and it is now the authority

Asked to choose, the owner picked **"قناة توزيع بديلة"** — an alternate delivery path — over naming a
deletion cut-off, raising the plan, or waiting. So way 3 shipped and **no artifact was deleted**. The
inventory above is unchanged in this segment: re-measured at 2026-10-02 after the last run, still **38
`apks-*` pairs / 667.7 MiB, 0 expired**.

`589f4a5` added `.github/scripts/publish-device-apks.sh`, run by a new **required** step in
`.github/workflows/group-a-diagnostics.yml`, and `a147bef` fixed the first thing it caught: inside a `run:`
step the GitHub CLI does not read the job token implicitly, so the step died with
`gh: To use GitHub CLI in a GitHub Actions workflow, set the GH_TOKEN environment variable` and
`Process completed with exit code 4` (run `37002080419`). That failure is the gate working as designed — a
publish that cannot prove the bytes exist fails the run instead of leaving a green lie.

Verified with the exact-SHA check Q-K-5 asked for:

| commit | run | verdict |
|---|---|---|
| `6ab41f2` (F-K3 static gate) | `36999879499` | failure — `341 tests, 341 passed`, Group C + G4-B + Phase 3 green, **only** the APK artifact upload red |
| `589f4a5` (release channel) | `37002080419` | failure at the new publish step (missing `GH_TOKEN`) |
| `a147bef` (token fix) | `37003070708` | **success, no non-success step** |

`gh release view device-apks` after that run returns `draft=true`, `tagName=device-apks`,
`targetCommitish=a147bef1a8bc73b60768c3288a0d854e49e765da`, and exactly two assets — `app-debug.apk`
(18,505,786 B) and `app-debug-androidTest.apk` (626,877 B), both stamped `2026-10-02T11:57:42Z`, i.e. the
same run. This is the first downloadable Q-K-era APK pair since the five answers landed.

Two properties matter more than the download itself.

**The rule the owner wrote still holds.** Q-K-5 said a missing APK must be *visible as a CI failure*, not
hidden by `continue-on-error`. The publish step is not tolerant and re-checks its own output
(`gh release view … --json assets` and a `fail` if either name is absent), so a build that produces no
downloadable pair now fails the run through a required step. The artifact copy stayed in the workflow,
still `continue-on-error: true`, demoted to a convenience copy — and it is still failing: `apks-37003070708`
does not appear in the artifacts listing at all, on a run whose overall conclusion is success. That is
exactly the quota trap `docs/reports/` has recorded before, which is why the release, not the run colour,
is the evidence here.

**What is not yet proven.** Release-asset storage has its own account limit; no official figure was fetched
in this segment, so it is a Batch L item rather than an assumption. The safeguard is the same required step:
if release storage ever refuses an upload, `gh release upload` fails, the asset re-check never runs, and the
run goes red — so this channel cannot rot silently the way the artifact channel did.

Q-K-1, Q-K-2 and Q-K-3 therefore move from *blocked on delivery* to *blocked on the phone being unlocked*.

---

## 7. Device half — what was actually observed today

The RMX3910 (`IR7X5DJ7KZGEHMS8`) came back unlocked at ~11:43 local (`dumpsys window` →
`mInputRestricted=false`), which unblocks the screenshot pass that Batch K recorded as NOT RUN. It was run
on **the build that is on the phone**, and that has to be stated before any observation:

```
$ adb shell dumpsys package com.shieldra.app
versionCode=1 minSdk=26 targetSdk=35
versionName=0.1.0
lastUpdateTime=2026-10-02 07:38:31
```

That is the `apks-36939853591` pair (commit `18cc745`, the G5 build) — it predates Q-K-1, Q-K-2 and Q-K-3.
So this pass documents **the state before the three answers shipped**, plus the Motion lifecycle that G5
delivered. Screenshots are under `%TEMP%\kshot3\` (720×1604 PNGs, not committed); every string below was
read from `uiautomator dump`, i.e. from the view hierarchy, not from a guess about the picture.

| # | Screen | Exactly what was seen |
|---|---|---|
| 1 | First run | Shieldra mark, "Security events are recorded on this phone.", "What you trigger is recorded and stored here, on this device. You can start Motion monitoring from the dashboard.", `Get started` / `Skip for now` |
| 2 | Onboarding "Notifications" | "Step 1 of 1" + "SHIELDRA records security events on this phone. To also show you a notification when one happens, Android needs your permission. **SHIELDRA asks for it in Settings**, and your security events are recorded either way." `Continue` shows the snackbar "This action is intentionally deferred in Phase 3." — the copy no longer promises a request here, which is the A-6 fix holding on screen |
| 3 | Onboarding "Lock Guard" | "Lock Guard uses the approved protection rule." / "**2 failed attempts confirm a security event**" / "The attempts must occur within 2 minutes. The first failed attempt is recorded as a signal, but does not create a confirmed security event." **Observation, not a verdict:** this is present tense about a guard that has no detector shipped (Batch J), and the dashboard's own Lock tile on the same screen says "Not monitoring yet" — the two readings sit one tap apart. Raised as **F-K6** below |
| 4 | Dashboard, monitoring stopped | Header "ATTENTION" / "No check yet"; body "Security events are recorded and stored on this phone. **No continuous monitoring is running yet.**"; status row "No check yet / Last check", "— / Battery", "Offline / Network"; `Silent Panic — Trigger an emergency event`; GUARDS tiles Lock "Not monitoring yet", Motion "Not monitoring yet", SIM "Not monitoring yet", Panic "Not monitoring yet"; button "Start Motion monitoring". Tab bar: **Home · History · Premium · Settings** |
| 5 | Dashboard, monitoring active | Body sentence changed to "Security events are recorded and stored on this phone. **Motion monitoring is running.**"; Motion tile → "Motion Guard. Motion monitoring is active. Status: Active." with the green indicator; Lock/SIM/Panic unchanged; header still "ATTENTION / No check yet" — a running Motion monitor does not invent a check. Button now "Stop Motion monitoring" |
| 6 | The ongoing notice, while active | `dumpsys activity services`: `ServiceRecord{… MotionMonitoringService} isForeground=true foregroundId=20002 types=0x40000000`. `dumpsys notification --noredact`: `NotificationRecord(… id=20002 … importance=2 … flags=ONGOING_EVENT|NO_CLEAR|FOREGROUND_SERVICE)`, `channel=shieldra_motion_monitoring`, `android.title=SHIELDRA protection is active` — A-9's exact sentence, silent, non-dismissible, no "24/7" and no "guaranteed" anywhere in it |
| 7 | After Stop | service records `0`, `NotificationRecord.*com.shieldra` `0` — the notice is cancelled with the service, and the phone was left with monitoring **off** |
| 8 | History | filter chips All · Lock · Motion · SIM · Panic and Any status · Delivered · Deferred · Failed; two real rows, both "Security event", "7 hours ago" and "9 hours ago", each `Delivery: Skipped` — no fabricated delivery state |
| 9 | Event Detail | "No photo available" · "LOCATION / Location unavailable" · "DETAILS Type: Security event / Timestamp: 7 hours ago / Event ID 65fac75e-4937-444c-90ba-da5750263b37" · a DELIVERY section — nothing claims evidence that was not captured |
| 10 | Settings | SECURITY: "Protection — Lock Guard · Guards"; NOTIFICATIONS: "Notifications — Allowed"; APPEARANCE: Theme "Match system", Language; DATA & PRIVACY: "Storage — Local storage is in use on this phone", "**Retention policy — Policy pending**"; SECURITY (APP): "App lock — Require authentication" |
| 11 | Premium (still live on this build) | "Shieldra Premium" / "One payment. Lifetime." / four checkmarked claims — "Multiple delivery channels · Email · WhatsApp · Telegram", "Extended history · 90 days · 500 events", "Advanced delivery receipts", "No ads" — / "**Buy Lifetime — 9.99**" / "Restore purchase" / "Free includes: Lock Guard · Motion Guard · SIM Guard · Panic · Email delivery." This is F-K1 photographed: a price, a restore-purchase affordance, and a free-tier sentence naming three guards whose own tiles read "Not monitoring yet" one tap away. Note also that the price renders with **no currency symbol** |

**Two of the nine requested screens are not reachable on this build**, and the reason is §6:
"Premium state after Q-K-1" (the tab still exists here) and a real **Motion event** row (a Motion-triggered
event needs the Q-K-2/Q-K-3 build to be installable, and none is downloadable).

### F-K6 — new, from row 3 and row 10

Two on-screen statements are now behind the answers rather than ahead of them:

- The onboarding Lock Guard page asserts the rule in the present tense while no Lock detector ships
  (Batch J, Q-J-1…Q-J-6 unanswered). Under §31 this is the same class as the findings Batch F2 and G5
  fixed, but the wording is a **product claim about a guard whose scope the owner has not yet ruled on**,
  so it is recorded and left alone — Q-K-4 says not to rewrite claims that are not contradicted by
  implementation, and Batch J has not decided what Lock Guard will be.
- "Retention policy — Policy pending" was written when the policy executed nothing. After Q-K-2 it
  under-states the build: the windows are approved and cleanup now runs at three execution points. The
  truthful replacement is a wording choice (windows + "cleanup runs when the app starts"), and the owner's
  Q-K-2 answer covered re-wording only for the *negative* branch, so the positive wording is theirs.

Neither is a false "protection" claim today, and neither blocks a gate.

---

## 7.2 The same pass, on the build that carries the answers — MEASURED 16:30–17:02

The phone came back unlocked at ~14:18 and the pair published by the new `device-apks` release was installed
then, so the pass §7 could only run against `18cc745` was run a second time against a build that contains
Q-K-1, Q-K-2 and Q-K-3. Screenshots are under `%TEMP%\kshot4\` (38 PNGs + their `uiautomator` dumps, not
committed); capture window `16:30` → `17:02` on the phone's clock.

**What is on the phone, read back after the pass (17:34 local):**

```
$ adb shell dumpsys package com.shieldra.app
versionCode=1 minSdk=26 targetSdk=36
versionName=0.1.0
firstInstallTime=2026-10-02 14:18:08   lastUpdateTime=same   installerPackageName=null

$ adb shell getprop ro.build.version.release ro.build.version.sdk ro.product.model
16
36
RMX3910        (build RMX391023703_16.0.5.1201(EX01))
```

`firstInstallTime == lastUpdateTime` means the 14:18 install replaced the §7 install **with its data wiped**,
so every reading below belongs to this build and this data directory, not to the earlier one.

**Which commit.** The pair downloadable at 13:18Z was the one published by run `37004923649`, i.e. `253ce07`;
that is an inference from publish times, not a read from the device. The hard check is what separates that
commit from HEAD: `git diff --name-only 253ce07..902f15c` returns **one** file,
`app/src/androidTest/…/EvidenceRetentionSweepInstrumentedTest.kt`, and **0** files under `app/src/main`. So the
production code on the phone is identical to HEAD's, and this pass is a valid screen-side check of Q-K-1,
Q-K-2 and Q-K-3.

**Android 16, measured.** The RMX3910 runs Android 16 / API 36, so this install is the first time a
`targetSdk=36` build has been *run* rather than compiled: it installed, every screen rendered, Motion
monitoring started and stopped, notifications posted, and the Room/Keystore paths were exercised by the
instrumented run in §7.3. That is the device half of §3's "Android 16 verification items" — it is **not** a
behavioural audit of every item on that list.

| # | Screen | Exactly what was seen |
|---|---|---|
| 1 | First run (`01`, 16:30) | Shieldra mark, "Security events are recorded on this phone.", "What you trigger is recorded and stored here, on this device. You can start Motion monitoring from the dashboard.", `Get started` / `Skip for now` — unchanged from §7 row 1, so the A-6 onboarding wording holds on the new build |
| 2 | Dashboard, stopped (`05`, 16:32) | "ATTENTION / No check yet"; "Security events are recorded and stored on this phone. **No continuous monitoring is running yet.**"; status row "No check yet · Last check", "— · Battery", "**Offline** · Network"; `Silent Panic`; GUARDS Lock/Motion "Not monitoring yet"; button "Start Motion monitoring". **Tab bar: Home · History · Settings** |
| 3 | Dashboard, active (`21`, 16:50) | Body sentence "Security events are recorded and stored on this phone. **Motion monitoring is running.**"; Motion tile "Motion Guard / Motion monitoring is active" with the green indicator; Lock tile still "Not monitoring yet"; header still "ATTENTION / No check yet"; same three-item tab bar |
| 4 | Guards grid (`38`, 17:02) | Lock "Not monitoring yet", **Motion "Motion monitoring is active"**, SIM "Not monitoring yet", Panic "Not monitoring yet", Battery "Not monitoring yet"; button "Stop Motion monitoring". A Panic event had already been recorded at 16:51 and the Panic tile still says "Not monitoring yet" — the tile reports monitoring, not history, and it does not inflate itself |
| 5 | Panic → History (`22`, `23`, 16:50–16:51) | After the Panic press, History shows one row "Security event / 0 seconds ago / `Skipped`" **without restarting the app** |
| 6 | History, Panic filter (`33`, 16:57) | The same row is still listed with **Panic** selected (chip green) → the persisted row's own type is Panic on a build where `persistedEventTypeOf` maps `PANIC_ACTIVATION → EventType.Panic`. This is §7 row 8's claim re-measured on the shipped code, and it is the screen-side proof Q-K-1's sibling fix (A) needed |
| 7 | Event Detail (`24`, `26`, 16:51–16:53) | "Security event" · "No photo available" · "LOCATION / Location unavailable" · "DETAILS Type: Security event / Timestamp: 58 seconds ago / **Event ID 8c75b077-94de-439b-a6e2-fc9fa91741df**" · an empty DELIVERY section · `Delete` / `Export`; Export shows the snackbar "This action is intentionally deferred in Phase 3." — F-K3's no-op, seen on screen |
| 8 | Settings (`34`, `35`, `36`, `37`, 16:58–17:00) | "Protection — Lock Guard · Guards"; "Notifications — **Allowed**"; Theme "Match system" (and the theme + language pickers, `35`–`37`); "Storage — Local storage is in use on this phone"; "**Retention policy — Policy pending**"; "App lock — Require authentication" |
| 9 | **Premium** | **There is no Premium screen to photograph.** The route, the tab and the strings are gone from the build, and the tab bar measured in rows 2, 3, 5, 6 and 8 has exactly three items. §7 row 11 was F-K1 photographed on `18cc745`; this is F-K1 photographed as closed |

**One §7 reading did NOT reproduce, and the reason matters.** The earlier install showed
`POST_NOTIFICATIONS: granted=true, flags=[ USER_SET\|… ]`, which §7 used as the proof that the runtime prompt
really reached a human. This install shows `granted=true, flags=[ USER_SENSITIVE_WHEN_GRANTED\|USER_SENSITIVE_WHEN_DENIED ]`
— **no `USER_SET`**. What *is* proven here is that the app's own request path ran on this install:
`shared_prefs/shieldra_notification.xml` holds `permission_requested=true` with an mtime of **16:41**, inside
this pass. What is *not* provable from this install is that a user tapped Allow, because the flag the earlier
install carried is absent. So Q-K-4's "1 prompted" sentence rests on the `18cc745` install's `USER_SET` flag,
and this install may only be quoted as "the request path executed". Nobody re-asked the owner to answer a
permission dialog to recover the stronger reading.

**The rest of §7 reproduced on this install, unchanged:** `install permissions` = `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE` + the AGP-injected signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
(3 declared, 1 runtime, same as §5); `head -c 16 databases/shieldra.db` = `SQLite format 3\0` — still
plaintext at rest; `dumpsys activity services` → **0** `ServiceRecord`;
`dumpsys deviceidle whitelist | grep -ci shieldra` → **0**.

**New, and stronger than §7's notification row:** `dumpsys notification --noredact` now lists **3**
`NotificationRecord`s for the package, all on channel `shieldra_security_events` at `importance=4`, and one of
them is tagged `8c75b077-94de-439b-a6e2-fc9fa91741df` — the exact Event ID read off the Event Detail screen in
row 7. So the device shows the whole A-6 chain at once: an event was persisted, its detail screen names that
ID, and a notification tagged with the same ID was posted on the audible channel. The Motion notice's own
channel (`shieldra_motion_monitoring`) appears only in the channel settings, never as a record — monitoring is
stopped, so §7's "no ongoing notice left behind" holds even with three real event notifications in the shade.

### F-K7 — new, from row 2 and row 3: the Network tile asserts a state nothing measured

The dashboard's third status tile reads **"Offline"** with a Wi-Fi-off icon in *both* the stopped and the
active screenshot, while the phone's own status bar in the same pictures shows 4G and Wi-Fi. Nothing in the
build performs a connectivity check, and the projection layer knows that:
`LockGuardDashboardFactory.kt:51-52` passes `networkLabel = stringResource(R.string.status_network_unknown)`
("Network unavailable") together with `networkOk = false`. The strip then throws the honest label away —
`LiveStatusStrip.kt:87`: `primary = if (networkOk) networkLabel else stringResource(R.string.status_network_offline)`
— and `:86` swaps the icon to `Icons.Filled.WifiOff`. The "unknown" string is referenced exactly once, by the
factory that is never allowed to say it.

Why it belongs in a security audit: protocol `:753` forbids labelling a thing on evidence that does not exist,
and "Offline" is a *definite negative* about the device's network — the same class as the claims Batch F2 and
G5 fixed, reached on every dashboard view. It is also the kind of sentence a Play reviewer can photograph.
The fix is display-only (render the unknown state as unknown, and keep "Offline" for a measured negative), it
needs no new permission and no connectivity API, and it is scheduled under **Q-K-4**, whose rule is to correct
claims that are contradicted by the implementation. Recorded, not silently patched, because the wording is a
release-facing claim.

### 7.3 The retention sweep, run on the device — 17:44 and 17:47

§8 of this file said `EvidenceRetentionSweepInstrumentedTest` was **NOT RUN**. It has now run, twice, on the
Android 16 install above, without needing the screen unlocked (it touches Room and files, not UI):

```
$ adb shell am instrument -w -e class com.shieldra.storage.EvidenceRetentionSweepInstrumentedTest \
    com.shieldra.app.test/androidx.test.runner.AndroidJUnitRunner
Tests run: 3,  Failures: 1        (identical at 17:44 and at 17:47)
```

**Two of the three passed on real files and a real database**, and those two are the device half of Q-K-2:
`expired_event_and_its_payload_are_removed_while_a_young_pair_survives` (a 91-day row and its ciphertext
disappear, a 1-day row and its ciphertext stay, the reference row goes with them) and
`quarantined_file_past_its_thirty_day_window_is_removed_and_a_young_one_is_kept`. Deletion is no longer a
plan computed in a JVM test — it removed real bytes from `no_backup/` on the owner's phone.

**The one failure is the stale expectation, not the sweeper** — and its message is the best measurement in the
section:

```
java.lang.AssertionError: expected:<RetentionSweepReport(deletedEventRows=0, deletedEvidenceFileIds=[], …)>
                 but was:<RetentionSweepReport(deletedEventRows=0, deletedEvidenceFileIds=[evidence-young], …)>
  at …EvidenceRetentionSweepInstrumentedTest.nothing_expired_leaves_every_row_and_payload_in_place(…kt:123)
```

The installed test APK is the `253ce07` pair, so its copy of that test still saves an owning row **89 days**
old and then asserts nothing expired. With the approved 30/30/90 windows, an 89-day owner *does* expire its
payload, and the sweeper said exactly that: `deletedEventRows=0` (the row is inside its 90-day window) while
`deletedEvidenceFileIds=[evidence-young]` (the payload is outside the 30-day one). That is the 30/30
asymmetry observed on a device, produced by a test that was wrong about it — which is why HEAD `902f15c`
changed that line to `now - day` and added a fourth test that asserts this behaviour instead of denying it.
The failure is therefore **expected on this install and already fixed at HEAD**; nothing in
`EvidenceRetentionSweeper` or `EvidenceRetentionPolicy` is implicated.

**Why HEAD's version could not be run here, and why that is an owner call.** The HEAD pair is published and
was downloaded and verified — `sha256 072e571f3f1cc97c8f5038c50dff5ffccc08461f7dcb6464fd2ad1cfbbb59707`
(`app-debug-androidTest.apk`, 627,305 B) and
`sha256 ece1a76cdcf4c54882ab57e79d44092a6b088f93bdd9005abd70265434e98163` (`app-debug.apk`, 18,505,786 B),
matching the release digests, with `target_commitish = 902f15c0f968f9a52e5f12aa2dddf39af41f1f6e`. Installing
just the test APK is refused:

```
$ adb install -r -t app-debug-androidTest.apk
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.shieldra.app.test signatures do not
match newer version; ignoring!]
```

Each CI runner signs with its own debug keystore, so a test APK from a different run cannot join the app
already on the phone, and the app cannot be replaced either without uninstalling it. Uninstalling would
destroy the install this batch's device evidence rests on: the `POST_NOTIFICATIONS` grant, `shieldra.db` with
the 16:51 Panic row, the two notification channels, the Keystore aliases and the lifecycle record. That is a
one-way trade of evidence for a test count, so it is raised as **Q-K-6** in `docs/OWNER_QUESTIONS.md`
(and in the audit report's §8) rather than resolved with a quiet `adb uninstall`.

---

## 7.4 §3's Android 16 item list, probed one by one — MEASURED 18:04–18:20

§7.2 proved the build installs and runs. §3 asked for something narrower: each behaviour that changes for
apps targeting 36, checked rather than assumed. That pass ran 18:04→18:20 on the **same 14:18 install** — no
reinstall was attempted, because §7.3 shows HEAD's pair cannot be installed over it (Q-K-6) — so the APK on
the phone is still the pair run `37004923649` published for `253ce07`. The attribution to HEAD is the
diff-based one, re-run against the current tip: `git diff --name-only 902f15c..c8343fe` lists four files, all
under `docs/`, and `git diff --name-only 253ce07..c8343fe -- app/src/main app/build.gradle.kts .github`
returns **nothing**. So the production code on the phone is the code at HEAD.

| §3 item | Reading | Verdict |
|---|---|---|
| Edge-to-edge (opt-out gone at 36) | `uiautomator` bounds on four screens: window and root nodes `[0,0][720,1604]`, app content `[0,64][720,1356]`, tab bar `[0,1356][720,1516]`, system-reminder strip `[0,1516][720,1604]`. First real content row at y=96 (History filter chips) / y=112 (dashboard shield). `MainActivity.kt:13` calls `enableEdgeToEdge()` and `themes.xml:4-5` already makes both bars transparent | **No regression, and none was possible**: the app was already edge-to-edge at target 35, so removing the opt-out changes nothing it relied on. Nothing is drawn under the 64 px status inset on any of the four screens |
| Predictive back (`onBackPressed` no longer called) | `grep` over `app/src/main` for `onBackPressed`, `BackHandler`, `OnBackInvoked`, `enableOnBackInvokedCallback` → **0 hits**; the manifest declares no `enableOnBackInvokedCallback`. Measured navigation: History → Event Detail → `KEYCODE_BACK` → History with the three-item tab bar intact and the same row; Settings → back → Home | **Behaviour correct, and the risk was empty.** The app has no back-override code path for the change to disable, so back is handled entirely by the `NavHost` dispatcher. Note honestly that `KEYCODE_BACK` from adb is the *key* path, not the touch gesture — the animation itself was not observed |
| Adaptive layouts above 600 dp min-width | `dumpsys window` override config reads `sw360dp w360dp h802dp 320dpi … port`; display is 720×1604 px at 320 dpi | **Does not apply** — 360 dp is well under the 600 dp threshold |
| Fixed-rate scheduling optimisation | No scheduler exists: `grep` for `scheduleAtFixedRate`, `java.util.Timer`, `WorkManager`, `AlarmManager` over `app/src/main` returns only `PlatformBoundaries.kt:5`, which is an empty marker `interface WorkManagerBoundary` | **Not applicable** |
| Health/fitness permissions replacing a health FGS type | `dumpsys activity services com.shieldra.app` while monitoring: `ServiceRecord{… MotionMonitoringService}`, `isForeground=true foregroundId=20002 types=0x40000000` — that is `SPECIAL_USE` (1<<30), unchanged | **Confirmed unchanged.** The notice is `NotificationRecord(… id=20002 … importance=2 … channel=shieldra_motion_monitoring flags=ONGOING_EVENT\|NO_CLEAR\|FOREGROUND_SERVICE)` with `android.title=SHIELDRA protection is active` |
| Local-network runtime permission | `dumpsys package com.shieldra.app` → `requested permissions:` `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` + the AGP-injected signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; `runtime permissions:` `android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SENSITIVE_WHEN_GRANTED\|USER_SENSITIVE_WHEN_DENIED ]` | **Still 3 declared / 1 prompted**, and the same `USER_SET`-absent reading as §7.2 — which is what narrows Q-K-4's permission sentence |
| 16 KB page size / Job-Scheduler quota | `adb shell getconf PAGE_SIZE` → **4096** on this phone; no native libraries in the build; no compatibility dialog appeared across four launches | **Not exercised by this device** — it runs 4 KB pages, so this pass cannot prove or disprove 16 KB behaviour |

**Start and stop, re-measured on this build.** Tapping `Start Motion monitoring` produced the `ServiceRecord`
and the ongoing notice above, flipped the tile to "Motion monitoring is active" and the button to
"Stop Motion monitoring"; tapping stop left **0** `ServiceRecord`, **0** `NotificationRecord` for the package
and **0** `dumpsys deviceidle whitelist` matches. So A-18/A-19's visible contract holds on a `targetSdk=36`
build, not only on the `18cc745` one.

**One premise in §3 was wrong, and the correction matters more than the row.** §3 asked the device pass to
"confirm the RMX3910 pass still locks to portrait". There is no lock to confirm: the manifest declares no
`android:screenOrientation` for `MainActivity`, and `configChanges` includes `orientation|screenSize|
screenLayout`, so the app neither locks portrait nor recreates on rotation. Setting
`system/user_rotation=1` (with and without `accelerometer_rotation=1`) did not rotate this ColorOS build at
all — `dumpsys display` kept `rotation 0` and `dumpsys window` kept `mRotation=ROTATION_0` — so **landscape
behaviour on this phone is NOT RUN**, by the phone's refusal rather than the app's design. The settings were
restored to `0`/`0` afterwards. Landscape is therefore a real untested surface for a release that ships an
orientation-agnostic activity, and it is listed in §8 rather than being called a pass.

**A measurement hazard worth recording, because it produced a false alarm.** At 18:07 a screenshot came back
showing the launcher and `uiautomator` returned `ERROR: null root node returned by UiTestAutomationBridge`.
The reading is not a Shieldra crash: `pidof com.shieldra.app` returned **7515** (alive), the `crash` log
buffer holds **no** Shieldra entry, and the `events` buffer's only Shieldra kills are the three
`stop com.shieldra.app due to finished inst` lines at 17:25/17:43/17:46 that belong to §7.3's instrumented
runs. What took the screen was a third app, `com.animai.rafiq`, repeatedly coming to the foreground during
this window (`mCurrentFocus` alternated between it and Shieldra). Every reading above was therefore taken
with `mCurrentFocus` checked first, and the walk was re-driven from `am start` when focus was lost.

### F-K8 — new, from the cold-start probe: onboarding is re-shown on every cold start, forever

The last probe of this window was meant to check whether the onboarding screen seen at 18:04 was an artefact
of how the app was being started. It is not, and the answer came out the other way.

```
$ adb shell am force-stop com.shieldra.app        # a process death of the kind G4-A measured as routine
$ adb shell monkey -p com.shieldra.app -c android.intent.category.LAUNCHER 1
…
[244,660][476,735]    Shieldra
[64,751][656,835]     Security events are recorded on this phone.
[64,867][656,1001]    What you trigger is recorded and stored here, on this device. You can start Motion
                      monitoring from the dashboard.
[289,1101][431,1134]  Get started
[279,1221][441,1254]  Skip for now
```

After a force-stop and a launcher tap, the first screen is the **welcome page again** — on a phone that has a
persisted Security Event, has already been through the flow, and whose monitoring state the user last set
hours earlier. The code says why, and says it is unconditional:

- `ShieldraNavHost.kt:81` — `startDestination: String = ShieldraRoutes.ONBOARDING_WELCOME`. The start
  destination is a constant, not a decision.
- `grep` over `app/src/main` for a persisted completion flag returns nothing: the only
  `getSharedPreferences` call in the whole production tree is `LocalEventNotifier.kt:35`, which stores the
  notification-permission state. There is no "onboarding seen" value to consult, and no Room table holds it
  either.
- The three `popUpTo(ONBOARDING_WELCOME) { inclusive = true }` calls clear the onboarding from the *back
  stack*; they do not record that it happened.

The contrast that makes this a finding rather than a nitpick is the resume path, measured minutes earlier in
the same window: `HOME`, then a launcher tap, restored the **scrolled dashboard** with the guard tiles exactly
where they were. So the app is not "always starts at onboarding" — it is "every time the process dies, the
user is a new user again". On this phone the process dies on its own (G4-A: a dark screen starves it of CPU
entirely), which means the cold-start path is the normal path, not the exceptional one.

**What is not claimed:** that this is a lie to the user. The welcome page's copy is true on the day it is
seen again — events really are recorded on this phone, and monitoring really is not running. The cost is that
the first screen after any process death is a setup screen instead of the status screen, which is exactly the
screen a person looks for when they are unsure. And the onboarding flow's own next page is the notification
permission request, so a returning user is walked toward a permission prompt they already answered.

**What was not done:** nothing was changed. Persisting "onboarding completed" is new behaviour that no
amendment covers, and deciding whether the release should carry it — or should instead start on the dashboard
whenever a database already exists — is the owner's call. It is raised as **Q-K-7**.

---

## 8. What this report does not claim

- No **G6 / Batch K sign-off**. Protocol §38 is honoured by not signing; the string "G6 PASS" does not
  appear in this file.
- API 36 is verified as **compile + gates + JVM + "the build installs and runs on an Android 16 phone and
  every screen renders"** (§7.2) **+ §3's item list probed one by one** (§7.4): edge-to-edge measured by
  `uiautomator` bounds on four screens, back navigation measured Detail→History and Settings→Home, the FGS
  type read as `0x40000000`/`specialUse` with its ongoing notice, the permission set re-read as 3 declared /
  1 runtime, and `sw360dp` read as under the 600 dp adaptive-layout threshold.
  **Two items stay open.** *Landscape* is **NOT RUN**: this ColorOS build ignored
  `system/user_rotation=1`, and the app declares no `screenOrientation` lock, so nothing was ever seen in a
  rotated frame. *16 KB page size* is **not exercised** — the phone reports `getconf PAGE_SIZE` = 4096, so
  this device cannot prove or disprove it either way.
- The retention sweep is verified as **two device-passing deletion tests + the third's measured output**
  (§7.3), not as HEAD's version of that class running green on a phone: the installed test APK predates
  `902f15c`, and installing HEAD's pair is refused on signature grounds (Q-K-6).
- Q-K-1's removal is verified as **route/tab absent + 341/341 + gates green + a three-item tab bar
  photographed on the shipped build** (§7.2 rows 2, 3, 5, 6, 8). What is *not* claimed is that any user
  ever saw the Premium screen on this build — it does not exist here.
- §7's rows are the `18cc745` build and §7.2's rows are the `253ce07`-code build; the two installs are not
  mixed in either table, and the one reading that did not reproduce between them (`USER_SET` on
  `POST_NOTIFICATIONS`) is called out rather than averaged away.
- Batch K cannot be called CLOSED: §6.4 is open, **Q-K-6** (§7.3) and **Q-K-7** (F-K8, §7.4) are open, F-K7
  (§7.2) is a release-facing wording fix still to be made under Q-K-4, and H-1…H-12, Q-I-1…Q-I-6,
  Q-J-1…Q-J-6 remain unanswered, which is what decides what the audit is allowed to claim about
  camera/location, SIM and Lock.
