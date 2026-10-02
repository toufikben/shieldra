# Batch K — the five owner answers, applied

**Date:** 2026-10-02
**Owner input:** the five-answer decision message of 2026-10-02 (Q-K-1 … Q-K-5), recorded as amendments
**A-20 … A-24** in `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` §17.
**Verification target:** `main` at `8da7686`.
**What this report is:** the execution record for those five answers — what landed, which CI run proves
each part, what was measured on the phone today, and what is still owed. It gives **no security sign-off.**

---

## 1. Where each answer stands

| Answer | Landed as | CI proof | Really proved | Still owed |
|---|---|---|---|---|
| **Q-K-1** remove the Premium tab and route | `e8bb16e` (RED) → `d0573bf` (GREEN) | `36991054611` (RED) · `36994802842` (GREEN) | the tab and route are gone and a test fails if any declared route names a premium screen again | the on-device "Premium state after Q-K-1" observation — needs an APK from a run after `d0573bf`, and §6 says none exists |
| **Q-K-2** retention executes | `e8bb16e` (RED) → `d34ed86` (GREEN) | `36993925242` · `36994802842` | the 30/30/90 windows decide real deletions in the pure planner, and the three authorized execution points are wired | `EvidenceRetentionSweepInstrumentedTest` — **NOT RUN** (blocked on §6) |
| **Q-K-3** API 36 | `d06cfc8` (RED gate) → `8da7686` (GREEN) | `36994944518` (RED) · `36995812606` (GREEN) | the release compiles and targets 36, every gate passes, 341/341 JVM | Android 16 runtime behaviour on `RMX3910` from that run's APK pair — **NOT RUN** (blocked on §6) |
| **Q-K-4** listing vocabulary | amendment **A-23** | docs-only | the allowed and forbidden sentences are written down | the Play Console text itself — a Batch L submission-time task |
| **Q-K-5** CI artifacts | `e78694e` | `36989921551` | a missing required APK now fails the run visibly, and future APKs expire in 14 days | the quota is **still exhausted**; freeing it needs a deletion decision — §6.4 |

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
every run above — but has never been executed, because no APK pair has been downloadable since §6.

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
`continue-on-error`. That is visible again in `36995812606`.

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

## 8. What this report does not claim

- No **G6 / Batch K sign-off**. Protocol §38 is honoured by not signing; the string "G6 PASS" does not
  appear in this file.
- API 36 is verified as **compile + gates + JVM**, not as device compatibility. No Android 16 behaviour
  from §3 has been measured on the RMX3910.
- The retention sweep is verified as **pure-plan behaviour + wiring that compiles**, not as a deletion
  observed on a phone: `EvidenceRetentionSweepInstrumentedTest` is **NOT RUN**.
- Q-K-1's removal is verified as **route/tab absent + 341/341 + gates green**, not as a screen observed
  after the change.
- Nothing in §7 was measured on a build containing this batch's code, because no such APK exists to
  install. Every §7 row is the `18cc745` build, labelled as such.
- Batch K cannot be called CLOSED: §6.4 is open, and H-1…H-12, Q-I-1…Q-I-6, Q-J-1…Q-J-6 remain
  unanswered, which is what decides what the audit is allowed to claim about camera/location, SIM and Lock.
