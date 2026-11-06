# Lock Guard — Retired (Design B, 2026-11-06)

**Status:** REMOVED FROM THIS BUILD. The Lock guard no longer exists in the app.

## What was here

This page used to prepare a Lock guard built on `DeviceAdminReceiver.ACTION_PASSWORD_FAILED`.
That callback is delivered only to an app holding device-administrator powers, and a
home-installed anti-theft app is never granted that role on the platforms this product targets.
The owner's decision therefore retired the guard instead of asking the user to hand over
administrator privileges:

- the app must **not** hold device-admin powers (and previously granted ones are released);
- a guard whose only sensor rests on a refused privilege is deleted, not faked;
- a guard whose sensor is an ordinary, grantable permission stays, but with no lock power.

## What replaced it

Nothing on the Lock surface. `GuardKind` is now `MOTION`, `SIM`, `BATTERY`, `PANIC`; the
detection interfaces live in `app/src/main/kotlin/com/shieldra/detection/GuardBoundaries.kt`,
and the design-B absence rules are enforced by `.github/scripts/g4b-review-check.sh` section 5d
(no `device_admin.xml`, no `BIND_DEVICE_ADMIN`, no `android.app.admin` import, no `lockNow(`, no
`/lock` command, no Lock settings keys).

The historical feasibility work is preserved in `docs/reports/batch-J-lock-feasibility.md` and
`docs/reports/batch-4d-lock-guard-adapter.md`. They describe a direction the owner has since
rejected; they are records, not current capability.

## Persistence note

Alerts and capture requests queued by a build that still had the Lock guard keep their line
format: the retired failure-count slot is written empty and ignored on read, so an update does
not drop work already queued. A persisted event row naming `LOCK_CONFIRMED_EVENT` is read as an
unnamed confirmed event (History shows it under "Other") instead of failing the whole read.
