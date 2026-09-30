# Batch F — background execution: what SHIELDRA may honestly do and say

**Status:** design record for the Batch F contract. No service, no second
permission, and no detector was added in this batch (owner directives F3 and F6).
**Written:** 2026-09-30, against commit `0e767b2` and the Batch E report findings 4/5.
**Binding sources:** `SHIELDRA_DECISIONS.md` (G3-01: maximum technically feasible
continuous protection, never a guaranteed 24/7 claim), the owner protocol §16/§17/§23/§30/§31,
and [`FINAL_SECURITY_STORAGE_DECISIONS.md`](../authority/FINAL_SECURITY_STORAGE_DECISIONS.md).

## 1. The separation this batch exists to enforce

| Layer | What it can honestly say | What it must never imply |
|---|---|---|
| Background execution (`com.shieldra.runtime`) | "a place to run exists / is up" | that anything is being observed |
| Guard detection (Motion / SIM / Lock — later batches) | "this sensor or callback is delivering readings" | — |
| Event confirmation (`ProtectionStateEngine`, `PanicIncidentCoordinator`) | "the approved rule confirmed an event" | — |
| Persistence (Room) | "the event is stored on this phone" | that storing means delivering |
| Notification (`LocalEventNotifier`, amendment A-6) | "Android accepted a local post" | that a human saw it or another device received it |

`BackgroundExecutionController` holds no repository, builds no event, and touches no
notifier — the static gate (`g4b-review-check.sh` §11) fails the build if it ever does.
The single projection the UI may use is `monitoringClaim(detectorRunning)`, and
`claimsProtection` is true only for `ActiveGuardMonitoring`, which needs **both** a
running execution layer **and** a detector that is genuinely observing. Today no build
can produce it, so no screen may say `Protected` or `Monitoring Active`.

## 2. Lifecycle semantics actually implemented

| Event | Behaviour | Result reported |
|---|---|---|
| `start()` with prerequisites present | platform layer brought up once | `Started`, phase `Running` |
| `start()` again | nothing is brought up a second time | `AlreadyRunning` |
| `stop()` | graceful teardown once, phase back to `Stopped` | `Stopped` |
| `stop()` again | nothing is torn down a second time | `AlreadyStopped` |
| `start()` with a prerequisite absent | refused before touching the platform | `PrerequisitesMissing(missing)` |
| platform throws during start | phase stays `Stopped`, no retry, no invented cause | `Failed(reason)` |
| process recreated | a fresh controller is `Stopped`; nothing restarts itself | `NoExecution` claim |

`Prerequisite` names the three things a real detector needs — `DetectorAvailable`,
`PermissionGranted`, `StorageInstalled`. `DetectorAvailable` has no producer in any
batch up to F, which is the honest reason nothing can be started yet.

## 3. Android constraints the future service must satisfy (documented, not yet verified)

These are the platform rules a Batch G+ implementation has to design around. They are
listed as **requirements to verify on the owner's device**, not as results:

1. **Background start limits (Android 8+).** A background app cannot start an ordinary
   service; continuous observation needs a foreground service or an equivalent
   legitimate mechanism.
2. **Foreground service cannot be started from the background (Android 12 / API 31+).**
   Start must come from a visible component, a user action, or a documented exemption.
3. **Ongoing notification is subject to `POST_NOTIFICATIONS` (Android 13 / API 33+).**
   If the user refused notifications, the foreground-service notification may not be
   displayed. A refused notification permission must still never block recording an
   event (amendment A-6), so a future guard's execution must not be gated on it.
4. **The service type and its permission must be declared and match real use
   (Android 14 / API 34+).** This is why no type is chosen in Batch F: the type belongs
   to the detector, not to the batch (§17, directive F3).
5. **Doze and App Standby** defer background work while the device is idle.
6. **OEM battery management** (this test phone is a Realme RMX3910, Android 16 / API 36)
   can restrict or kill background processes beyond AOSP behaviour. Observed on this
   device already: `pm clear` and `pm revoke` are both refused by the OEM build, which is
   a concrete sign its background policies are stricter than stock.
7. **Force Stop** kills the process and its service; nothing runs again until the user
   launches the app. A reboot likewise requires an explicit, permitted restart path.

**Therefore:** SHIELDRA may say it uses legitimate Android mechanisms to keep watching as
long as the platform allows. It may never say monitoring is continuous, guaranteed, or
24/7 (G3-01, §17).

## 4. What each screen may report

* Dashboard: real persisted events plus the disclosure line produced by
  `SettingsContent.dashboardMode(storageInstalled)` — "events are recorded on this phone,
  no continuous monitoring is running yet", or the no-storage variant.
* Settings: the notification row shows the live A-6 permission state
  (`Allowed` / `Not allowed` / `Not requested` / `Not needed on this Android version`);
  the storage row shows a state word, never a figure (`storageRowShowsFigures = false`).
* Nothing anywhere may show an "active guard", "monitoring", or "protected" state until a
  detector reports it from its own real readings.

## 5. Deferred deliberately (and why)

| Item | Deferred to | Reason |
|---|---|---|
| `<service>` declaration, `FOREGROUND_SERVICE*`, service type | the batch that ships the first real detector | choosing a type now would be inventing an Android requirement (F3) |
| WorkManager use | never for detection | §17 prohibits it as a real-time detector (F4) |
| Motion / SIM / Lock acquisition code | Batches G / I / J | F6 keeps detectors out of F; Lock additionally needs the §24–§25 policy citation |
| Camera, location, audio, cloud, Firebase, SMS, email, WhatsApp, Telegram, analytics | not approved | §30 |

## 6. Exit criteria for the next batch that uses this contract

A batch may bring the execution layer up only when it can answer, with device evidence:
which detector is bound, which permission it requested and from which screen, which
foreground-service type it declares and why that type matches its use, what happens after
process death and after Force Stop on the owner's phone, and which screen text changes as
a result. Without those answers the honest state remains `NoExecution`.
