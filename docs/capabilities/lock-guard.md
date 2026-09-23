# G4-B / 4d+ — Lock Guard Capability Preparation

**Status:** PREPARED — PLATFORM ADAPTER NOT IMPLEMENTED  
**Gate:** G4 remains **APPROVED DIRECTION / IMPLEMENTATION IN PROGRESS**  
**Rule authority:** G1-01 in `SHIELDRA_DECISIONS.md`

## Approved domain behavior

A failed device-PIN attempt is a **Signal**, not an event by itself. Two consecutive failed attempts observed within two minutes produce one confirmed Lock Security Event with **HIGH** severity. The sequence resets after ten minutes without another failed attempt. A five-minute cooldown suppresses repeated confirmations from the same attack sequence. The domain engine remains framework-free and deterministic.

The engine must preserve these distinctions:

- one failed attempt: `SIGNAL_ONLY`;
- two qualifying attempts: `CONFIRMED`, severity `HIGH`, one `SecurityEvent`;
- a qualifying attempt inside the five-minute cooldown: `SIGNAL_ONLY`;
- an attempt after the ten-minute reset window starts a new sequence;
- malformed Lock signals are `NOT_A_SECURITY_EVENT`;
- no camera, location, network, delivery, or evidence capture is triggered by a Signal alone.

## Android feasibility boundary

Android exposes `DeviceAdminReceiver.ACTION_PASSWORD_FAILED` to a device administrator that requested `DeviceAdminInfo.USES_POLICY_WATCH_LOGIN`. The callback can query `DevicePolicyManager.getCurrentFailedPasswordAttempts()`. This is not equivalent to universal third-party observability: the app must be provisioned as an eligible device administrator, the user or management authority must grant that role, and behavior must be verified across API/OEM profiles.

The candidate adapter must therefore report an explicit capability state such as `AVAILABLE`, `NOT_DEVICE_ADMIN`, `UNSUPPORTED`, `PERMISSION_OR_PROVISIONING_REQUIRED`, or `RUNTIME_ERROR`. It must never synthesize a failed-PIN Signal from app lifecycle events, screen on/off events, accessibility observations, or guessed counters.

The official Android reference describes `ACTION_PASSWORD_FAILED` as a broadcast to a device administrator and states that the admin must request `USES_POLICY_WATCH_LOGIN` to receive it: [DeviceAdminReceiver reference][1].

## Candidate adapter boundary — not yet implemented

A future Android adapter may:

1. declare and provision a `DeviceAdminReceiver` with the reviewed `USES_POLICY_WATCH_LOGIN` policy;
2. receive the failed-password callback;
3. record a timestamped `GuardSignal(LOCK, FailedPinAttempt)` only after the callback is genuinely delivered;
4. pass the Signal to `DefaultProtectionStateEngine`;
5. persist only the confirmed event through the approved local repository;
6. expose unsupported/provisioning/runtime failure states without claiming missed attempts were detected.

It must not:

- request unrelated permissions;
- add accessibility, camera, location, network, cloud, or analytics behavior;
- treat `getCurrentFailedPasswordAttempts()` as a durable event log without testing reset semantics;
- claim guaranteed monitoring across process death, force-stop, Doze, reboot, profiles, or OEM policy;
- collect evidence before a confirmed event and a separately approved evidence policy.

## Device test plan

| Test ID | Profile | Action | Expected result | Classification |
|---|---|---|---|---|
| LOCK-01 | API 26+ AOSP | Inspect admin provisioning and policy declaration | Capability state is explicit; no false availability | Platform/device observation |
| LOCK-02 | API 26+ AOSP | Provision admin, submit one wrong device credential | One `SIGNAL_ONLY`; no event | Runtime evidence |
| LOCK-03 | API 26+ AOSP | Submit second wrong credential within 2 minutes | One HIGH confirmed event | Runtime evidence |
| LOCK-04 | API 26+ AOSP | Repeat wrong credential inside 5-minute cooldown | No second confirmation | Runtime evidence |
| LOCK-05 | API 26+ AOSP | Wait at least 10 minutes after a single failed attempt, then fail once | New sequence starts; signal only | Runtime evidence |
| LOCK-06 | API 26+ AOSP | Receive callback, kill/restart process before persistence | No duplicate event; recovery result explicit | Runtime evidence |
| LOCK-07 | API 26+ AOSP | Reboot and inspect admin/callback behavior | Provisioning and recovery outcome recorded | Runtime evidence |
| LOCK-08 | API 26+ AOSP/OEM | Remove or disable admin role | Capability becomes unavailable; no silent fallback | Runtime evidence |
| LOCK-09 | Work/profile device | Compare parent/profile challenge behavior | User/profile scope recorded; no cross-profile assumption | Runtime evidence |
| LOCK-10 | OEM physical device | Repeat LOCK-02 through LOCK-08 | OEM differences and unsupported outcomes recorded | Physical-device evidence |

Every test record must include device identity, API/build, app commit SHA, admin/provisioning state, exact action sequence, timestamps, observed callback/counter, persisted event result, and reviewer classification.

## Pure deterministic coverage

The existing `ProtectionStateEngineTest` covers the core two-attempt confirmation and reset/cooldown behavior. Additional boundary tests are required before implementing the Android adapter:

- exact two-minute boundary;
- exact five-minute cooldown boundary;
- exact ten-minute reset boundary;
- malformed Lock signal shape;
- duplicate callback timestamp and duplicate event identity handling;
- callback counter unavailable or inconsistent with the event callback;
- no event creation on `SIGNAL_ONLY` or unsupported capability state.

## Exit criteria for implementation

Lock Guard may move from preparation to a platform implementation batch only after:

1. the owner approves the device-admin/provisioning product choice;
2. API/OEM runtime evidence covers the relevant profiles;
3. the adapter emits only verified callbacks;
4. the pure boundary tests pass;
5. persistence and duplicate handling are proven;
6. unsupported and provisioning failure states are explicit;
7. the roadmap and capability evidence are updated and pushed.

[1]: https://developer.android.com/reference/android/app/admin/DeviceAdminReceiver "Android DeviceAdminReceiver reference"
