# Motion as a guard — lock on a confirmed lift or snatch (amendment A-43)

Date: 2026-11-04 · Branch: `fix/audit-round-1` · Status: code complete, CI and device
verification recorded below (signed release deliberately deferred by the owner)

## 1. The owner decision

The Motion guard used to be a separate opt-in: a dashboard toggle, plus a settings flag that let
arming start it. On 2026-11-04 the owner asked for it to be a guard like the lock and SIM ones —
always on — and for its active state to lock the phone when it is snatched, if the change is
appropriate and logical. It is, so it is implemented and recorded as amendment A-43 in
`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`.

Two facts make the design safe to turn on by default:

- The guard leaves the response, the grace window and the switch in the owner's hands, exactly as
  the lock and SIM guards do. Turning it off while protection is armed weakens protection, so it
  asks for the owner's screen lock (`ProtectionChange`).
- The screen lock power is not new. It is the same credentialed administrator A-42 introduced
  (`watch-login` + `force-lock`, one guarded `lockNow()` caller). Motion only adds a second reason
  to use an already-approved, easily revocable power. No permission is added and the Play policy
  argument in `docs/reports/audit-round-1-force-lock.md` is unchanged.

## 2. What the owner controls

| Setting | Default | Meaning |
|---|---|---|
| Motion guard (`motionEnabled`) | **on** | On by default like the lock and SIM guards. Arming starts it; disarming stops it; a reboot with protection armed resumes it. |
| Response: Silent capture | selected | Capture a photo and a fix quietly; lock the screen at once. |
| Response: Loud siren | | The same, plus the siren; lock the screen at once. |
| Response: Grace then siren | | Wait the grace window. The owner's own unlock during it cancels the siren **and** the lock; if nobody unlocks, the siren sounds and the screen locks. |

The grace response is the answer to the obvious false positive — picking your own phone up from a
table. The immediate responses are for an owner who would rather a mistake lock the screen than let
a snatch through. Nothing here locks on its own without a confirmed Motion incident.

The retired `motion_with_arming` key is not read. Its old default was false and every settings save
persisted it, so it cannot be told apart from a deliberate off; carrying it forward would leave
existing installs with the guard off, the opposite of this decision. `motion_enabled` starts at true
and is the only switch.

## 3. Lifecycle

- **Arming** (`ProtectionRuntime.arm`) records the flag, starts `ProtectionService`, schedules the
  countdown and the patrol, and starts Motion monitoring when the guard is on. The platform facts
  still gate the start: the A-6 notification permission and installed storage. A refusal (or an
  unanswered permission) is logged and leaves arming intact; the dashboard keeps saying the truth.
- **Disarming** stops `ProtectionService`, cancels the countdown and patrol, stops Motion monitoring,
  and cancels a grace response that is still waiting — a response queued before the disarm must not
  siren or lock a phone the owner just chose to stop watching.
- **Reboot** (`BOOT_COMPLETED`, after the first unlock) re-arms protection and resumes Motion with
  it, only while armed and enabled. The locked-boot half still only records the interruption; it
  starts nothing.
- **Saving the settings while armed** starts the monitor when the guard was switched on and stops it
  when it was switched off, so the saved flag cannot disagree with what is running.

## 4. The lock contract

`TableModeResponder` turns the response into a pure `MotionResponsePlan`:

| Response | Siren now | Wait for the owner | Siren after grace | Lock at once | Lock after grace |
|---|---|---|---|---|---|
| SILENT | no | no | no | yes | no |
| SIREN | yes | no | no | yes | no |
| GRACE_THEN_SIREN | no | yes | yes | no | yes |

`fire()` always requests the capture (through the live protection service, or the guard's own
text-only alert when protection is not armed) **before** it locks, because the camera and the fix
need the moment the confirmation bought them. The lock itself goes through
`DeviceScreenLock.now(context)` — the single `lockNow()` caller — and only while the stored `armed`
flag is true. The four honest outcomes are unchanged: `LOCKED`, `ADMIN_OFF`, `NEEDS_REGRANT`,
`FAILED`. A non-locking outcome is logged, never retried silently and never reported as a lock.

Two intentional limits:

- With protection disarmed, Motion can still be started by hand from the dashboard, but its lock is
  refused. The screen-lock power belongs to armed protection; disarming is the owner saying stop
  watching.
- A phone whose administrator was granted before A-42, or which has no administrator at all, records
  and reports the incident but cannot lock until the role is granted (and re-granted once, for a
  pre-A-42 grant).

## 5. Tests and gates

JVM tests:

- `MotionResponsePlanTest` — silent and siren lock at once; grace waits for the owner's unlock and
  only then sirens and locks.
- `MotionGuardDefaultsTest` — the guard is on by default like the lock and SIM guards.
- `ProtectionChangeTest` — switching the guard off weakens protection and asks for the screen lock.

`g4b-review-check.sh` pins A-43: the default and the retired key, the pure plan's lock outcomes, the
lock through `DeviceScreenLock`, the armed gate around it, arming/boot starting Motion, disarming
stopping it, the settings-save sync, `ProtectionChange`, and the named tests. The single
`lockNow()` caller pin is unchanged.

## 5b. The defect the phone found (amendment A-44)

Measured on build 391 (upgrade over 389, old `motion_with_arming=false` preserved):

1. Protection was armed from the Protection screen; `ProtectionService` (action ARM) and
   `MotionMonitoringService` started at 07:41:12.
2. The screen showed “Armed — guarding in the background”, but `shieldra_protection.xml` held
   `armed=false`, and `dumpsys activity services` showed only `MotionMonitoringService`.
3. At 07:42:12 the service logged `protection is no longer armed: this instance is standing down` —
   one heartbeat after arming. The stored flag, not the screen, decided.

The cause is older than A-43: the screen is composed with a settings snapshot whose `armed` was
false; pressing Save writes the whole snapshot through `ProtectionSettingsStore.save`, so changing
any guard (the Motion response included) silently disarmed protection while the UI kept saying
“Armed”. A-43's save-path Motion sync also used that stale flag, so the monitor kept running.

A-44 fixes it at the source: `save()` no longer writes `KEY_ARMED`; `setArmed` is the flag's only
writer. `ProtectionRuntime.applySettings` reads the live flag back for its scheduling decisions, and
the NavHost replaces the draft's `armed` with `protectionArmed` and follows the live flow. `g4b`
pins all three. Device re-verification after the fix is recorded below.

## 6. Verification

The three local static gates were run before the push: g4b (`PASS: A-43 …`), Group C boundary scan,
Phase 3 static review. CI and device results are appended here and in `docs/ROADMAP.md` after they
run; nothing in this section is claimed before it is measured.

- **CI:** run `41782160333` on `9c30867` — **success**. `CI TEST SUMMARY: 578 tests, 578 passed,
  0 failures, 0 errors, 0 skipped` (the four new JVM tests included); `BUILD SUCCESSFUL in 9m 41s`;
  `Group C boundary scan passed`; `PASS: A-43: Motion is on by default, arming and boot start it,
  and a confirmed lift locks through DeviceScreenLock`; `G4-B LOCAL REVIEW PASS`;
  `PHASE 3 STATIC REVIEW PASS`. The branch APK pair and the phone APK were published to the draft
  release `test-41782160333`; the signed release build succeeded but was not published, per the
  owner's instruction to defer the signed version until the app is fully verified.
- **Device on 391:** partial round, stopped by the A-44 defect above. Measured: the upgrade kept
  the old `motion_with_arming=false` and the Motion guard still rendered **on** with the new
  description; the administrator still held exactly `watch-login` + `force-lock`; the permission
  list was unchanged. The arming started both services once, and the Save-while-armed test is what
  exposed A-44.
- **CI (A-44):** run `41788801231` on `a710c40` — **success**. `CI TEST SUMMARY: 578 tests, 578
  passed, 0 failures, 0 errors, 0 skipped`; `Group C boundary scan passed`; `PASS: A-43 …`;
  `PASS: A-44: the armed flag has one writer; a settings Save can no longer disarm a running
  protection`; `G4-B LOCAL REVIEW PASS`; `PHASE 3 STATIC REVIEW PASS`. The debug pair and phone APK
  were published to `test-41788801231`; the signed release built but was not published.
### Device round on build 392

Build 392 was installed over 391 in place (versionCode 392, `lastUpdateTime` 2026-11-04 08:09:40),
with the same preferences: `motion_with_arming=false` was still present and `motion_enabled=true`.
Measured on the phone:

- **Default-on and copy:** the Protection screen showed the Motion guard **on**, with the full
  description in English and Arabic; Help showed the four guards on by default in both languages.
- **Arming:** pressing **Arm** on the Protection screen started `ProtectionService` (intent action
  `com.shieldra.protection.ARM`) and `MotionMonitoringService` together, and the ongoing
  notification read “SHIELDRA protection is active”. The dashboard claimed `PROTECTED`, Guards
  `4/5` (Panic not monitoring), and the Motion card read “Motion monitoring is active” — the claim
  comes from the running service, not from the button that started it. The settings screen and the
  dashboard agree on the guard's state; the dashboard's stop row says the owner's screen lock is
  required.
- **A-44 regression path:** the Protection screen was opened while protection was disarmed, armed
  from that same screen, and **Save** was pressed. The disarmed starting point was produced without
  the owner's screen lock by stopping the app and clearing only the stored `armed` flag; UI Disarm
  was not usable because it demands the owner's screen lock. `armed` stayed `true`, both services stayed
  alive, the screen still read “Armed — guarding in the background”, and a check after one
  heartbeat found the same two ServiceRecords and no “standing down” line. The old code wrote the
  stale `armed=false` snapshot here; the fix holds.
- **Policy and permissions:** the administrator still holds exactly `watch-login` + `force-lock`
  (no wipe); the requested-permission list is identical to build 391's.
- **Instrumentation:** `run finished: 78 tests, 0 failed, 0 ignored`. The runner's line was read
  live from `logcat` at 09:33:07; the adb `-w` summary was lost when the command was moved to the
  background, and another process later cleared the log buffer, so the events buffer's
  `am_kill … due to finished inst` at 09:33:08 is kept beside it as the dispatch record.

**NOT RUN on the device, deliberately:** **Disarm** and switching the Motion guard off (both ask
for the owner's screen lock, which the owner's protocol forbids entering), the dashboard's stop
direction (same owner check), and the physical snatch/lock (it would leave the shared phone at the
lock screen and a real still-then-lift cannot be produced from here). These paths are covered by
the `ProtectionChange` tests, `MotionResponsePlanTest` and the g4b pins. Two environment facts are
recorded for honesty: the phone was rebooted at 08:35 by someone else (the app's own restart alert
in the owner's channel is the timestamp), and another app repeatedly held the foreground, including
one Telegram session that took it between a UI dump and a tap. The stored response read `SILENT` at
08:40, `SIREN` at 08:56 and `SILENT` again after the instrumented suite; this round tapped no
response chip before the `SIREN` reading, so that change came from outside it (the phone was in
other hands between steps, and the instrumented settings tests write their own fixtures). It does
not affect the armed-flag assertion, which is read from storage and from the service records.


