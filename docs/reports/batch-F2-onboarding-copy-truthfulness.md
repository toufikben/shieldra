# Batch F2 — onboarding copy says what the onboarding screens actually do

**Date:** 2026-10-01 · **Slices:** `F2a-onboarding-permission-claim`, `F2b-protection-step-duplicate-sentence`
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
| Dashboard guards | after onboarding | Lock / Motion / SIM / Panic all **"Not monitoring yet"**, banner reads *"No continuous monitoring is running yet"*, `Last check · —` |

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

`Q-F1` lists three surfaces. Only the copy half of item 3 is fixed here, because it was a statement the
app contradicted on screen. Item **1** (the Premium screen's `Email · WhatsApp · Telegram`,
"Advanced delivery receipts", and the free-plan summary) and item **2** (the empty `DELIVERY` card on
the event Detail screen) are **unchanged** — item 1 is pitch copy whose correctness depends on the
unanswered Q-D5/Q-D7 ads-and-billing conflict, and §14 forbids resolving an open decision by deleting
the text it might still need.

## WHAT IS NOT CLAIMED

- **CI: NOT RUN at the time of writing** for this commit — the verdict is appended below once the run
  for it concludes.
- **Device re-check: NOT RUN** — the two fixes have not yet been looked at on the phone. They are copy
  changes, and copy changes have been wrong on this project before, so the flow is re-walked and
  re-photographed with the APK pair from this commit's own run before this slice is called done.
- Nothing about Motion, storage, notifications delivery, permissions or the manifest changed. No
  permission was added, requested, or revoked; `POST_NOTIFICATIONS` still has exactly one request site.
- The 68-test result is a re-verification of the **previous** commit's APK pair, not of this one.
