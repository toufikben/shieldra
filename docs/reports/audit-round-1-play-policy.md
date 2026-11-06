# Audit round 1 — the exact-alarm decision (F-PLAY-1)

Date: 2026-11-03 · Branch: `fix/audit-round-1` · Status: implemented, CI verified

## 1. The policy fact

Google Play ties `USE_EXACT_ALARM` to a purpose: it may only be published by apps whose core
function is an alarm clock or a calendar. The Android 14 behaviour page states it verbatim:

> "Apps will not be able to publish a version of their app with this permission in the
> manifest unless they qualify based on the policy language."
> — https://developer.android.com/about/versions/14/changes/schedule-exact-alarms

SHIELDRA is an anti-theft app. It does not qualify. The permission was declared at
`app/src/main/AndroidManifest.xml` (old line 56) and would have put the whole listing at risk
for a precision the product can request at runtime instead.

`SCHEDULE_EXACT_ALARM` is a different permission: it is user-grantable on Android 12+, denied by
default on Android 14+ for non-exempt apps, and the app must handle both answers. It stays
declared, because the countdown and the watchdog genuinely want exact timing when the owner
grants it.

## 2. What changed

| Change | File |
|---|---|
| `USE_EXACT_ALARM` removed from the manifest; the comment now says why | `app/src/main/AndroidManifest.xml` |
| Gate `EXPECTED` permission list updated to the honest set | `.github/scripts/g4b-review-check.sh:431` |
| `ExactAlarmAccess` (state + system prompt) added to the protection package | `app/src/main/kotlin/com/shieldra/protection/ExactAlarmAccess.kt` |
| "Alarms and reminders" row on the protection screen | `ProtectionSetupScreen.kt`, `ShieldraNavHost.kt` |
| New string in both locales (592 keys each, parallel) | `values/strings.xml`, `values-ar/strings.xml` |

## 3. Behaviour without the grant

Every scheduling call site already checks `AlarmManager.canScheduleExactAlarms()` and falls back
to `setAndAllowWhileIdle`:

- `CountdownReceiver.kt:114`
- `GuardWatchdogReceiver.kt:118` (the dead-man patrol)
- `PendingCaptureReceiver.kt:45`

So a refused grant costs second-level precision, never the guard itself. The protection screen now
names that state as a row the owner can fix in one tap, instead of leaving it invisible.

## 4. What was not adopted

`systemExempted` foreground-service type and the `ROLE_EMERGENCY` safety-app role were re-examined
and stay out, for the reasons already recorded in `batch-G5-foreground-service-type-review.md`:
SHIELDRA matches none of the qualifying roles, and buying a type with a permission the product
does not use violates A-8's "never add permission-only code". No Console disclosure change is
needed for this round.

## 5. Verification

The three local static gates pass (`g4b-review-check.sh` with its full PASS list, `group-c`,
`phase3`). The CI run attached to the implementing commit prints the JVM test count; the exact
run id and count are recorded in the round report, not here.
