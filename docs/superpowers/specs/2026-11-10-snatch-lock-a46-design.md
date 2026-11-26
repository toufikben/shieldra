# A-46 — the real snatch lock, a ten-minute proof window, and an honest ladder (design)

Date: 2026-11-10 · Branch: `fix/general-app-gaps` · Owner approval: given explicitly in the same
session, after the proposal was read in full. The owner's own words restated: lock the screen the
moment a snatch is confirmed, take **no** photo or fix at that second, demand proof for ten minutes
with continuous sound, light and vibration, reset everything on proof, and otherwise escalate on its
own every five or ten minutes — pausing inside a safe zone and slowing to the longest step at the 10%
battery floor, because that floor is the existing "as agreed" battery rule (A-33).

This supersedes the anti-lock recommendation of design B (2026-11-06). Design B had removed the lock
power because Android's theft features already lock a phone; the owner rejected that reasoning twice:
the app's own lock is the property they paid for, and Google's lock is not ours to rely on.

## 1. Decisions

**D1 — one real lock, one caller.** `<force-lock />` returns beside `<watch-login />` in
`res/xml/device_admin.xml`. `DeviceScreenLock` is the only object allowed to call
`lockNow()`, and `g4b-review-check.sh` fails if a second caller appears. The app still cannot reset
the password, wipe the phone or change the keyguard.

**D2 — the re-grant trap is surfaced, not hidden.** An administrator granted before A-46 does not
silently gain force-lock; the platform keeps the policies it was granted with. `DeviceScreenLock.now`
distinguishes `ADMIN_OFF` from `NEEDS_REGRANT`, and the Protection screen shows the exact fix (switch
the role off and on once) until `isGranted` is true.

**D3 — no capture at t=0.** The evidence starts only when the ten-minute proof window expires. A
thief running with the phone is not a subject worth photographing; the owner's location at that
minute is noise. The alert at t=0 is text only, and it says so.

**D4 — proof has two paths.** Unlocking with the phone's own credential (read from the keyguard after
a 15 s grace, only when the incident really locked the screen) or pressing the stop button in the
app. Both clear the whole incident: store, demand notice, alarm, vibration, ladder alarm, and the
periodic countdown returns to its chosen interval. A restart that reaches `BOOT_COMPLETED` is proof
too, because that broadcast exists only after the credential was entered.

**D5 — escalation is automatic.** No proof by the tenth minute means theft. The ladder moves to full
evidence (photo + fix onto the Motion event) at the owner's cadence, five or ten minutes, and repeats
until proof, a safe zone, disarm, or the six-hour ceiling. A wrong PIN does not wait for the next rung;
it accelerates the same ladder. No proof is needed for escalation to start — that was the owner's
correction to the earlier proposal.

**D6 — safe zone pauses, it does not kill.** A tick inside a fresh safe zone keeps the incident,
stops the vibration, re-posts the demand with a waiting status, and sets the next tick. Leaving the
zone resumes escalation with no proof lost. A wrong PIN inside a zone stays on the old evidence path.

**D7 — the 10% floor slows the ladder.** At or below `BatteryBreathLogic.DEFAULT_LEVEL` (10%),
unplugged, the interval is the longest offered (ten minutes). Charging or above the floor, the owner's
choice stands. The rule is in pure code, `SnatchPolicy.intervalMs`.

**D8 — the stranger-facing demand is trilingual by resource.** The notice is built from
`snatch_demand_*` strings in `values` (en), `values-ar`, and a `values-fr` that holds **exactly** those
nine keys. The owner's private note and contact appear only when set, and the dial action appears only
when the contact is dialable. The channel is new (`shieldra_snatch_v1`) because Android freezes a
channel's sound and visibility at creation.

**D9 — it survives process death and reboot.** The incident lives in device-protected storage
(`SnatchStore`, `shieldra_snatch`), the tick is an exact alarm received by a direct-boot-aware,
non-exported `SnatchReceiver`, and `LOCKED_BOOT_COMPLETED` re-posts the demand as far as a locked
phone allows while `BOOT_COMPLETED` proves ownership. A tick that cannot read the credential-protected
settings keeps the incident alive at a ten-minute pace and sends nothing.

**D10 — off means off.** Disarming, switching the snatch guard off, and "delete all data" all end or
erase the incident; `eraseAll` now wipes `shieldra_snatch` beside `shieldra_boot`, and the B-3 gate
compares that list with every declared preferences store.

## 2. What is deliberately not here

- No lock on demand (`/lock` stays forbidden), no wipe, no password reset, no keyguard change.
- No capture before proof, no continuous tracking during the proof window.
- No new permission beyond `VIBRATE` (install-time) — A-6/A-19 permissions are unchanged.
- No WorkManager, no polling service, no always-on foreground service for the ladder; the alarm is
  the clock.

## 3. Verification

Local gates: `g4b-review-check.sh` (5c permission set, 5d one lock caller, 5g retired layer, 5i the
A-46 pins), `phase3-review-check.sh` (permission allowlist incl. VIBRATE), `locale-parity-check.sh`
(values ↔ values-ar; French limited to the demand). CI is the only build verifier; the run id and test
count are recorded in `docs/ROADMAP.md`. The physical lock, vibration, notice and escalation cadence
are on the owner's phone checklist — they cannot be proven by a build.
