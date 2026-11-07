# Lock Guard — Restored (A-33, owner order 2026-11-07)

**Status:** ACTIVE. The Lock guard is back in this build with one platform power and no others.

## Why it is back

The removal note claimed Android never delivers a failed-unlock report to a home-installed app.
That is false. AOSP `DevicePolicyManagerService.reportFailedPasswordAttempt` broadcasts
`ACTION_PASSWORD_FAILED` to every active device administrator whose policy XML declares
`USES_POLICY_WATCH_LOGIN` (`<watch-login />`). The API 26 three-argument callback
`onPasswordFailed(Context, Intent, UserHandle)` delegates to the two-argument override, so one
implementation serves every supported release. The owner also confirmed the guard worked on his
own phone before it was removed.

The owner's order (2026-11-07): restore it immediately, with sound behaviour — a thief who takes
the phone home and guesses the PIN must produce the agreed signal.

## What it does

`FailedUnlockCounter` holds the rule, free of Android and unit-tested on the JVM:

- the owner's threshold is 1..5 wrong attempts, default **2**;
- below the threshold nothing is captured (the attempt is only counted);
- at the threshold and **every later attempt in the same session** the guard captures and alerts,
  so a thief who keeps guessing is photographed each time and the alert carries the attempt number;
- a correct unlock, or a quiet gap longer than **10 minutes**, ends the session and resets the
  count — two morning mistypes are not a night burglary.

`LockGuardReceiver` is the device-admin receiver. It acts only while protection is armed and the
Lock toggle is on. `onDisableRequested` captures once before the role is given up: stripping
protection is itself a signal. The signal includes:

- a front-camera photo and a location fix through `ProtectionService` when they can be taken;
- an alert of kind `LOCK` titled "Wrong unlock attempts" with a "Failed attempts: N" line;
- a persisted `LOCK_CONFIRMED_EVENT` row that History files under its own **Lock** filter;
- if the camera cannot run, the attempt count still reaches the owner through the queued text
  alert — the count is the agreed signal.

## The one power it asks for

`res/xml/device_admin.xml` declares `<watch-login />` and nothing else. The app cannot lock the
screen, reset the password, wipe the phone, change the keyguard, or read what was typed. The owner
grants the role from the setup screen through `ACTION_ADD_DEVICE_ADMIN`; the Health screen and the
Protection setup checklist report whether the role is actually active. The dashboard renders the
Lock guard **Active** only when protection is armed, the toggle is on, **and** the role is granted;
otherwise it says the role is needed rather than pretending the guard hears anything. The
protection score awards its 19 Lock points only to that same pair.

`.github/scripts/g4b-review-check.sh` section 5d enforces the policy: `<watch-login />` present;
force-lock, wipe, reset-password, password-quality and set-password forbidden; receiver registered
with `BIND_DEVICE_ADMIN`; no `lockNow(`, `resetPassword(`, `wipeData(`, no `/lock` command, no
`setMaximumFailedPasswordsForWipe`.

## Persistence and compatibility

- Queued alert lines: field 6 is the attempt count again. A line written by the removal build has
  it empty and decodes to "no count"; a line written by the older Lock Guard build decodes to the
  count it carried.
- Queued capture requests: slot 2 is the attempt count again. The removal build deliberately kept
  the slot empty, so old queues decode in place and no request is lost.
- Persisted `LOCK_CONFIRMED_EVENT` rows project to `EventType.FailedUnlock` and the Lock title. Rows
  saved during the removal build were written as generic confirmed events and cannot be recovered
  as Lock rows; that is a historical fact, not a fabricated upgrade.

## Limits, stated honestly

- A wrong-PIN attempt is never read, only the fact that one failed.
- `adb shell am force-stop` keeps manifest receivers from being delivered until the app is launched
  again. The guard cannot hear through a force-stop; the watchdog and restart alert cover the gap.
- A thief who disables device admin triggers one capture and the neutral warning, then the role is
  gone. Android gives no way to block that without the device-owner role, which this app does not
  request.
- Automated wrong-PIN testing is forbidden on the owner's shared phone. The JVM rule is tested in
  `FailedUnlockCounterTest`; the end-to-end signal is verified by the owner on his own device.

## Related records

- `docs/reports/batch-J-lock-feasibility.md` and `docs/reports/batch-4d-lock-guard-adapter.md`
  remain historical feasibility notes.
- The removal commit's premise is superseded by the AOSP behaviour above and by the owner's order.
