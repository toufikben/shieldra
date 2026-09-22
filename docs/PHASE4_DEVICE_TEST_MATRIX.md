# SHIELDRA Phase 4 Device and API Test Matrix

**Batch:** 4a
**Status:** Test plan prepared; execution requires physical devices/emulators and must remain separate from production adapters.
**Target project SDK:** compile/target SDK 35; minimum SDK 26.

## Required platform coverage

| Profile | API / OS | Environment | Primary purpose | Required evidence |
|---|---:|---|---|---|
| A | 26 / Android 8 | AOSP emulator | Background-service and background-location baseline; notification-channel boundary | Service limit, channel behavior, background location cadence |
| B | 29 / Android 10 | AOSP emulator | Runtime permissions, foreground-service location type, `ACTIVITY_RECOGNITION`, background location declaration | Permission and service-type outcomes |
| C | 30 / Android 11 | AOSP emulator | Background-location Settings flow, telephony baseline, foreground-service behavior | Approximate/background location and API result matrix |
| D | 31 / Android 12 | AOSP emulator | Sensor rate limits, foreground-service background-start restriction, camera while-in-use boundary | Exceptions, rate limits, visible-start behavior |
| E | 33 / Android 13 | AOSP emulator | `POST_NOTIFICATIONS`, notification pre-grant/denial, channels | Permission states and notification surfaces |
| F | 34 / Android 14 | AOSP emulator | Foreground-service type and while-in-use enforcement | Required manifest/type/permission failure matrix |
| G | 35 / Android 15 | AOSP emulator | Force-stop/stopped package behavior, boot restrictions, current target baseline | PendingIntent/widget/stopped-state and boot recovery |
| H | Supported OEM 1 | Physical phone | Sensor, camera, location, battery manager, lock-screen, process behavior | OEM-specific observations under default battery policy |
| I | Supported OEM 2 | Physical phone | Independent OEM comparison | Differences from OEM 1 and AOSP |
| J | Telephony device | Physical single-SIM | SIM removal/insert and subscription change | SIM state and active-subscription snapshots |
| K | Dual-SIM/eSIM/MEP device | Physical phone | Logical/physical slot and profile behavior | Slot, subscription, card, embedded, and visibility records |

Profiles H–K are not interchangeable. At least one physical OEM device must support cellular telephony, and at least one test device must represent the SIM/eSIM behavior required by the product scope.

## Capability test matrix

| Test family | A26 | A29 | A30 | A31 | A33 | A34 | A35 | OEM/physical |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Sensor inventory and missing-sensor handling | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Sensor foreground/background delivery | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Sensor rate-limit and privacy-toggle behavior | — | — | — | ✓ | ✓ | ✓ | ✓ | ✓ |
| SIM feature and permission matrix | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Physical SIM transition | — | — | — | — | — | — | — | ✓ |
| Dual-SIM/eSIM/MEP mapping | — | — | — | — | — | — | — | ✓ |
| Background service restrictions | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Foreground-service background start | — | — | — | ✓ | ✓ | ✓ | ✓ | ✓ |
| Foreground-service type prerequisites | — | — | — | — | — | ✓ | ✓ | ✓ |
| WorkManager persistence/reboot | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Doze/App Standby recovery | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Force-stop/stopped-state behavior | — | — | — | — | — | — | ✓ | ✓ |
| Direct Boot / locked reboot | — | — | — | — | — | — | ✓ | ✓ |
| Notification permission | — | — | — | — | ✓ | ✓ | ✓ | ✓ |
| Notification channels and user overrides | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Lock-screen visibility | — | — | — | — | ✓ | ✓ | ✓ | ✓ |
| Camera permission/device toggle | — | — | — | ✓ | ✓ | ✓ | ✓ | ✓ |
| Location approximate/precise | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Background location cadence | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

## Evidence record template

Each executed test must record the following fields in a durable report or CI/device artifact:

| Field | Required value |
|---|---|
| Test ID | Stable identifier, for example `4A-SENSOR-01` |
| Capability | Motion, SIM, background, notification, camera, or location |
| Device profile | A–K from this matrix |
| Manufacturer/model | Exact value from device properties |
| Android build | API level, release, build fingerprint |
| App build | Commit SHA and target SDK |
| Permissions | Granted, denied, revoked, approximate, or not applicable |
| User/system settings | Battery mode, Doze/Standby, notification/channel, lock-screen, camera/location toggle |
| Action sequence | Exact user and ADB commands, in order |
| Observed output | Timestamps, callback counts, values, exceptions, notifications, and state |
| Recovery result | Process kill, reboot, foregrounding, unlock, or permission restoration result |
| Classification | Platform fact, device observation, unsupported, denied, deferred, or unknown |
| Reviewer | Owner/AI review status |

## Stop conditions

Stop the capability investigation and record **BLOCKED** if the test device cannot expose the required feature, if a permission or user setting prevents the test, if behavior differs across target devices without an approved support policy, or if the result would require claiming continuous execution or exact timing. Do not compensate for a missing physical device by treating an emulator result as equivalent evidence.

## Minimum completion gate for Batch 4a

Batch 4a research documentation is complete when the capability report, this matrix, official source links, per-capability test cases, and unresolved decisions are committed. Batch 4a is not a claim that any runtime capability has passed. A capability-specific implementation batch requires fresh device evidence for that capability.
