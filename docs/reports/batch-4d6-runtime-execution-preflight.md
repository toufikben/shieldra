# Batch 4d-6 — Runtime Execution Preflight

**Date:** 2026-09-23  
**Repository:** `toufikben/shieldra`  
**Scope:** Room integration and Keystore runtime evidence  
**Status:** **PREPARED — BLOCKED UNTIL OWNER DEVICE/AVD IS AVAILABLE**

## Preflight result

The 4d-6 execution entry point is prepared, but the active environment has no Android Debug Bridge, emulator binary, AVD, or attached device.

| Check | Result |
|---|---|
| `adb` executable | NOT AVAILABLE |
| `adb devices -l` | NOT RUNNABLE |
| `emulator` executable | NOT AVAILABLE |
| `emulator -list-avds` | NOT RUNNABLE |
| Authorized device | NOT AVAILABLE |
| Room/Keystore runtime evidence | NOT COLLECTED |

No runtime pass is claimed. In particular, this report does not claim Room persistence, foreign-key enforcement, Keystore key creation, invalidation, backup behavior, or evidence recovery on Android.

## Prepared execution entry point

The repository now contains [`scripts/run-4d6-device-tests.sh`](../../scripts/run-4d6-device-tests.sh). After connecting one authorized device or starting one AVD, run:

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SERIAL=<optional-device-id>
bash scripts/run-4d6-device-tests.sh
```

The script refuses to continue when `adb` is unavailable, no device is authorized, or multiple devices are connected without `ANDROID_SERIAL`. It builds the debug and Android test APKs, runs only `RoomEventRepositoryInstrumentedTest`, and records basic device identity values.

## Required runtime evidence

The owner should preserve the command output and record:

- manufacturer, model, API level, Android build fingerprint;
- app commit SHA and APK build result;
- all Room instrumented test results;
- Keystore creation/use/invalidation outcomes when the applicable test is available;
- backup and device-transfer behavior as observed on the test profile;
- failures, unsupported features, permissions, and device settings;
- classification as platform fact, device observation, unsupported, denied, deferred, or unknown.

A virtual device may validate Room, process/reopen behavior, selected migration behavior, and API-level Keystore behavior. It does not replace physical-device evidence for OEM battery management, SIM/eSIM, radio, or hardware-specific claims.

## Gate decision

**4d-6 remains blocked for runtime execution in this environment.** The local implementation and test APK compilation are complete; the next valid transition requires an authorized device or AVD and execution of the prepared script. G4 remains **APPROVED DIRECTION / IMPLEMENTATION IN PROGRESS**.
