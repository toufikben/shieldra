# Batch F2 — onboarding copy says what the onboarding screens actually do

**Date:** 2026-10-01 · **Slices:** `F2a-onboarding-permission-claim`, `F2b-protection-step-duplicate-sentence`
**Status:** **CLOSED** — commit `1babdbd`, CI `36871617120` **success**, and both fixes re-read on the
RMX3910 with that run's own APK pair (`OK (68 tests)` ×2 on the install). Verdicts at the bottom.
**Authority:** protocol §16 (a screen may claim only what really happened) and §23 (no new permission
behaviour in a copy fix); Batch F's precedent that shipped copy is a truthfulness surface.
**Amendment needed?** No. Nothing here changes a decision, a permission, or a mechanism — it removes two
claims the running app did not honour.

## HOW THIS WAS FOUND

Not by reading the code. By installing the CI pair of the *previous* commit (`adf68cc`, run
`36868131863`) on the owner's RMX3910 and walking the first-run flow with the screen on:

| Step | What was run | Result |
|---|---|---|
| Instrumented suite, pass 1 (fresh install) | 14 classes, the G4-A measurement vehicle excluded | **`OK (68 tests)`** in 23.476 s |
| Instrumented suite, pass 2 (same install) | same 14 classes | **`OK (68 tests)`** in 21.391 s |
| First-run flow | `Get started` → notification step → protection step → dashboard | two copy defects visible, screenshots below |
| Dashboard guards | after onboarding | banner reads *"No continuous monitoring is running yet"*, `Last check · —`. **This row under-reported at the time:** it transcribed four guards (Lock / Motion / SIM / Panic), but the factory had already emitted **five** — Battery was missing from my note, found on the `1babdbd` re-walk below |

The G4-A vehicle's own 4 tests are deliberately not in the 68: they are exercised by the campaign
driver, which controls the screen, not by a suite pass.

## FINDING 1 — the onboarding permission step claimed a request it never makes

The step rendered `permission_notification_reason`:

> *"… SHIELDRA asks for it **only here**, and your security events are recorded either way."*

That sentence belongs to the A-6 **Settings** screen, which really does hold the only
`POST_NOTIFICATIONS` launcher (`ShieldraNavHost.kt:271`). The onboarding step's `onAllow` is
`defer()` (`ShieldraNavHost.kt:164`), so tapping **Continue** shows
*"This action is intentionally deferred in Phase 3."* and asks for nothing. A first-run screen told the
user where the permission is requested, and it was the wrong screen — caught by tapping it, not by
reading it.

**Fix (copy only, no behaviour change):** a new resource `onboarding_notification_reason` in both
locales, saying the permission is asked for **in Settings**; the onboarding step uses it and the A-6
screen keeps its accurate sentence. The deferred **Continue** button itself is left exactly as it was —
that is **Q-F1 item 3**, which the owner has not answered, so removing or rewiring the step is his call.
What this slice removed is only the sentence that pointed the user at the wrong screen.

## FINDING 2 — the protection step printed one sentence twice

`lock_guard_window` already ends with *"The first failed attempt is recorded as a signal, but does not
create a confirmed security event."* and the step then rendered `attempt_recording_note`, which is that
same sentence. On the phone the paragraph and the line under it read identically.

**Fix:** the duplicate `Text` removed from `ProtectionSetupScreen.kt` and the now-unused
`attempt_recording_note` deleted from both locales. The fuller string (`lock_guard_window`, which also
carries the 2-minute window) is what stays on screen.

## THE GATE THAT NOW HOLDS BOTH

`.github/scripts/g4b-review-check.sh` section 17, written **before** the fix:

- **RED** — `FAIL: values has no onboarding_notification_reason for the deferred onboarding step`
  (the only new failure; every earlier section still passed).
- **GREEN** — the section now requires: the key to exist in `values` **and** `values-ar`; its text to
  contain neither `only here` nor `هنا فقط`; `ShieldraNavHost.kt` to pass
  `R.string.onboarding_notification_reason` to the onboarding step; and `ProtectionSetupScreen.kt` to
  stop rendering `lock_guard_window` and `attempt_recording_note` together.

Local gates after the fix: `G4-B LOCAL REVIEW PASS`, `PHASE 3 STATIC REVIEW PASS`,
`Group C boundary scan passed`.

## WHAT THIS SLICE DELIBERATELY DID NOT TOUCH

`Q-F1` carries four items now — three from Batch F plus the `Battery` row that this slice's own re-walk
surfaced. Only the copy half of item 3 is fixed here, because it was a statement the
app contradicted on screen. Item **1** (the Premium screen's `Email · WhatsApp · Telegram`,
"Advanced delivery receipts", and the free-plan summary), item **2** (the empty `DELIVERY` card on
the event Detail screen) and item **4** (the unscheduled fifth guard row) are **unchanged** — item 1 is
pitch copy whose correctness depends on the unanswered Q-D5/Q-D7 ads-and-billing conflict, and §14
forbids resolving an open decision by deleting the text it might still need.

## VERDICTS AFTER WRITING THE ABOVE

**CI for this commit — `36871617120`, conclusion `success`.** The workflow ran the full command
(`clean test lint assembleDebug assembleDebugAndroidTest`) plus all three contract scripts, so
section 17 passed in CI and not only locally.

**Device re-check — done, on the APK pair from that same run** (`1babdbd`), installed over plain
`adb install -r` after uninstalling both packages, with `runtime permissions:` confirmed empty so the
permission flow was not pre-granted:

| Step | What was looked at | What was actually seen |
|---|---|---|
| Instrumented suite, pass 1 | the same 14 classes, vehicle excluded | **`OK (68 tests)`** in 19.365 s |
| Instrumented suite, pass 2 | same install, same classes | **`OK (68 tests)`** in 19.112 s |
| Onboarding permission step | the fix from Finding 1 | renders *"… SHIELDRA asks for it **in Settings**, and your security events are recorded either way."* — the false "only here" sentence is gone |
| Onboarding protection step | the fix from Finding 2 | the sentence appears **once**: *"The attempts must occur within 2 minutes. The first failed attempt is recorded as a signal, but does not create a confirmed security event."* |
| Dashboard after onboarding | regressions | the guard list still says **"Not monitoring yet"** for every row, `Last check · —`, `Network · Offline` — five rows, not four (see below) |

Screenshots: `f2-onboarding-permission-step.png`, `f2-protection-step.png`, `f2-dashboard.png`
(written outside the repo, under the session evidence directory).

One thing the re-walk surfaced that is **not** a regression from this slice and was **not** changed
here: the dashboard has a **fifth** guard row, `Battery / Not monitoring yet`. `LockGuardDashboardFactory.kt:27`
maps over `GuardKind.entries`, and `GuardKind` (PresentationModels.kt:11) has `Battery` next to Lock,
Motion, Sim and Panic. The row is honest — disabled, "Not monitoring yet" — so it breaks no §31 rule
by itself, but it advertises a guard that has no detector and is in no batch, which is the same
product-surface question as Q-F1 item 1. Raised as **Q-F1 item 4** rather than deleted silently.

## WHAT IS NOT CLAIMED

- Nothing about Motion, storage, notification delivery, permissions or the manifest changed. No
  permission was added, requested, or revoked; `POST_NOTIFICATIONS` still has exactly one request
  site (`ShieldraNavHost.kt:271`), and `onAllow` on the onboarding step is still `defer()` —
  the deferred button is Q-F1 item 3 and remains the owner's decision.
- The Q-F1 items **1** and **2** findings are still open on screen: the Premium tab still shows
  "Email · WhatsApp · Telegram", "Advanced delivery receipts" and the free-plan summary naming
  Motion/SIM/Lock guards, and the event Detail screen still renders an empty `DELIVERY` card. This
  slice fixed only the two onboarding-sentence defects.
- The 68-test figures in the "HOW THIS WAS FOUND" table are from the **previous** commit's pair
  (`adf68cc`, run `36868131863`); the pair directly below is what verified this commit.
- Neither 68-test run was ever executed in CI: androidTest is compiled there, never instrumented
  (see the ROADMAP gate notes). The only device evidence is this RMX3910 pair.
