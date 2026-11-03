# Remote screen lock — `/lock` and `<force-lock />` (amendment A-42)

Date: 2026-11-03 · Branch: `fix/audit-round-1` · Status: implemented, CI verification pending

## 1. The owner decision

Audit round 1 deliberately left `/lock` and `<force-lock />` out, because the Lock guard's device
administrator had been kept to one policy (`watch-login`). On 2026-11-03 the owner asked for both to
be added immediately if doing so does not violate Google Play policy. It does not (section 2), so
both are in this branch, recorded as amendment A-42 in
`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`.

## 2. Why this is allowed

Google Play has no clause that forbids consumer device administration. The clauses that actually
bind this feature are:

- **Deceptive behaviour / user consent (5.1, 5.2.1):** the role is granted only from the system's
  own add-administrator prompt, opened from the Protection screen, with an explanation shown in the
  same prompt, and it is easily reversible from system settings.
- **Ransomware (5.3):** an administrator that prevents removal is the prohibited shape. This one
  does not block removal or uninstall; `onDisableRequested` only warns and (while armed) captures
  one tamper event before the right is surrendered.
- **Elevated privilege abuse (5.4):** the app must not use administrator power to prevent being
  stopped or uninstalled. It does not.
- **Misleading claims (5.5):** the app must not claim a lock it did not perform. Every reply and
  every shipped string is written to the measured outcome instead.

Android documents both halves as the normal consumer path: `<force-lock />` in `uses-policies` and
`DevicePolicyManager.lockNow()` ("Immediately lock the device") in the device-administration guide.
Neither is among the deprecated enterprise policies.

## 3. What the administrator can and cannot do

The declared policy set is exactly:

```xml
<uses-policies>
    <watch-login />
    <force-lock />
</uses-policies>
```

It cannot wipe the device, change or expire the password, limit password rules, disable the camera,
disable keyguard features or reset the password. The owner can switch the role off, clear app
storage or uninstall the app at any time. `lockNow()` is called from exactly one file,
`DeviceScreenLock.kt`, and only after `isAdminActive` returns true.

## 4. The command contract

`RemoteCommand.parse` accepts `/lock`, `/LOCK`, `/lock@BotName` and `/lock <argument>` (an argument
is ignored). The poller acts on it only while protection is armed, "Control from Telegram" is on,
Telegram delivery is configured, and the message arrives from the owner's own chat id — the same
gate every other command already passes.

Replies are the measured outcome, never a guess:

| Outcome | Reply |
|---|---|
| `LOCKED` | `SHIELDRA: screen locked` |
| `ADMIN_OFF` | `SHIELDRA: device admin is off — turn it on in Protection, then send /lock again` |
| `NEEDS_REGRANT` | `SHIELDRA: the lock power is not active yet — switch device admin off and on once in Protection, then send /lock again` |
| `FAILED` | `SHIELDRA: the screen could not be locked` |

The lock needs a network at the moment of the command; commands are not queued, so an offline phone
never locks later on its own.

## 5. Existing administrators

A device administrator granted before this policy was added keeps the policy set it was granted
with. The first `/lock` on such a phone is refused by the platform (`SecurityException`), which is
reported as `NEEDS_REGRANT` with the exact one-time fix: switch the role off and on in Protection.
The phone checklist covers this state explicitly.

## 6. Not claimed

- The phone is not wiped, its password is not changed and it is never locked on its own.
- A phone with no secure screen lock still shows a keyguard that can be swiped open; `/lock` turns
  the screen off, it does not create a credential.
- This is not a replacement for Google's Find My Device, which works even when Shieldra is not
  running. The panic screen keeps recommending it first and now states honestly that Shieldra can
  also lock the screen when its remote commands are reachable.
- Device verification is **NOT RUN** until the owner schedules it. CI is the only build verifier.

## 7. Verification

The local static gates are run before the push (g4b, Group C, phase 3). CI is dispatched on
`fix/audit-round-1`; the run id and test count are recorded in `docs/ROADMAP.md`, not here.
