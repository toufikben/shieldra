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
- **Device:** **pending** — the phone was occupied when the CI run finished. The round will use the
  build from `test-41782160333` and record results below. The physical snatch is planned as NOT RUN
  on the device: locking would leave the shared phone at the lock screen and a real still-then-lift
  movement cannot be produced safely from here; that path is covered by the JVM plan tests and the
  g4b pin.

