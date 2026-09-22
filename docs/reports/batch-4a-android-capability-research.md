# Batch 4a — Android Capability Research

**Date:** 2026-09-22
**Status:** Research and test planning complete; no Android production code added.
**Scope:** Motion sensors, SIM/subscription state, background execution, notifications, camera, and location.

## Executive conclusion

Android provides APIs for each capability required by Shieldra, but none of the researched areas provides a universal guarantee of continuous operation, exact timing, durable callbacks, process survival, or identical OEM behavior. The safe implementation boundary is therefore **conditional capability support**: discover the runtime capability, verify permissions and user settings before each operation, persist state, re-query authoritative state after recovery, and expose unsupported or deferred outcomes honestly.

Batch 4a does not open a production Android adapter. It closes the research stage and defines the device evidence required before opening one capability-specific implementation batch.

## Findings by capability

| Capability | Research result | Current Shieldra decision |
|---|---|---|
| Motion sensors | `SensorManager` supports runtime discovery and event listeners. Sensor inventory and properties vary by device. Android 9+ suppresses continuous/on-change/one-shot callbacks for background apps. Android 12+ applies rate limits to relevant sensors. Doze, processor suspension, process death, and OEM policy prevent continuity guarantees. | **CONDITIONAL**. Feature-gate at runtime; no universal continuous-motion claim. |
| SIM/subscription | `TelephonyManager` and `SubscriptionManager` expose SIM/subscription state subject to feature checks, permissions, carrier privileges, caller-visible records, API level, eSIM/MEP behavior, and process lifetime. Listener callbacks are not durable event logs. | **CONDITIONAL / UNKNOWN** for real-time detection. Snapshot and reconciliation are required. |
| Background execution | WorkManager provides durable, constraint-aware, eventual scheduling. Foreground services are user-visible and restricted by launch, type, permission, Doze, process, force-stop, boot, and OEM rules. | **CONDITIONAL**. No always-on or exact-deadline guarantee. |
| Notifications | API 33+ requires `POST_NOTIFICATIONS` for ordinary notifications. API 26+ target apps require channels. Users and system policy control channel behavior, alerting, lock-screen exposure, and interruption. | **CONDITIONAL**. A notification is not proof of delivery, viewing, or app-process survival. |
| Camera | `CAMERA` is runtime-controlled. Camera foreground services require visible-start and service-type rules; Android 12+ and 14+ add background-start and while-in-use restrictions. Device-wide camera disablement can produce a blank feed. | **BLOCKED** until consent, trigger, redaction, retention, encryption, and service policy are approved. |
| Location | Foreground/background and approximate/precise access are separate. Android 26+ limits ordinary background updates to only a few times per hour. Foreground services can retain foreground location under documented prerequisites, but not against process, Doze, force-stop, or OEM restrictions. | **BLOCKED** until precision, freshness, consent, retention, and background policy are approved. |

## Cross-cutting constraints

Android controls process lifetime. A cached process can be killed, and `onDestroy()` is not guaranteed. Listeners, callbacks, in-memory state, and background threads therefore cannot be treated as durable records. The implementation must persist the last known state and perform a fresh snapshot after process recreation, reboot, foregrounding, and permission changes.

Doze and App Standby defer or restrict network access, jobs, syncs, standard alarms, and WorkManager execution. A wake lock, battery-optimization exemption, notification, or foreground service addresses only specific parts of the platform policy. None converts the application into an always-running process.

Permissions are not permanent capability grants. The user can deny or revoke a runtime permission, select approximate location, disable camera access at device level, disable notifications, change channel importance, or restrict background activity. Each protected operation must re-check its prerequisites and return an explicit unavailable, denied, deferred, or unsupported result.

OEM restrictions are a separate evidence dimension. The Android documentation defines platform behavior but does not establish uniform battery managers, lock-screen presentation, radio behavior, sensor inventory, or Settings UI across manufacturers. OEM observations must remain device-specific and must not be promoted to platform guarantees.

## Required evidence before platform adapters

Each capability must complete its own implementation gate with a permission matrix, failure tests, process-death tests, Doze/Standby tests where applicable, and evidence from the supported device matrix. Camera and location must not be combined into one implementation batch. SIM and Motion must remain separate adapters. Notification behavior must be tested separately from the monitoring source that triggers it.

The minimum evidence package for each adapter is: API level and build, device model and OEM, target SDK, permission state, user setting state, battery/Standby state, exact action sequence, timestamps, observed result, failure/exception, recovery behavior, and a conclusion labeled either **platform fact**, **device observation**, **unsupported**, or **unknown**.

## Batch 4a outcome

Batch 4a is complete as a research and planning batch. The next safe action is a capability-specific review and selection of one adapter. No Android permission, service, sensor, telephony, notification, camera, or location code should be added until that adapter's decision package is approved.

## References

[1]: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview "Android sensor framework and sensor availability"
[2]: https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion "Android motion sensors"
[3]: https://developer.android.com/reference/android/hardware/SensorManager "SensorManager API reference"
[4]: https://developer.android.com/reference/android/telephony/SubscriptionManager "SubscriptionManager API reference"
[5]: https://developer.android.com/reference/android/telephony/TelephonyManager "TelephonyManager API reference"
[6]: https://developer.android.com/develop/connectivity/telecom/dialer-app/telephony-ids "Android telephony and subscription identifiers"
[7]: https://developer.android.com/reference/android/Manifest.permission#READ_PHONE_STATE "READ_PHONE_STATE permission"
[8]: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start "Foreground-service background-start restrictions"
[9]: https://developer.android.com/develop/background-work/background-tasks/persistent "Persistent background work with WorkManager"
[10]: https://developer.android.com/training/monitoring-device-state/doze-standby "Doze and App Standby"
[11]: https://developer.android.com/guide/components/activities/process-lifecycle "Android process lifecycle"
[12]: https://developer.android.com/develop/ui/compose/notifications/notification-permission "Notification runtime permission"
[13]: https://developer.android.com/develop/ui/compose/notifications/channels "Notification channels"
[14]: https://developer.android.com/develop/ui/compose/notifications/create-notification "Creating notifications and lock-screen visibility"
[15]: https://developer.android.com/develop/sensors-and-location/location/permissions "Location permissions"
[16]: https://developer.android.com/develop/sensors-and-location/location/permissions/background "Background location permission"
[17]: https://developer.android.com/about/versions/oreo/background-location-limits "Background location limits"
[18]: https://developer.android.com/develop/background-work/services/fgs/service-types "Foreground-service types"
[19]: https://developer.android.com/about/versions/14/changes/fgs-types-required "Android 14 foreground-service type requirements"
[20]: https://developer.android.com/training/permissions/requesting "Android runtime permission guidance"
[21]: https://developer.android.com/about/versions/15/behavior-changes-all "Android 15 behavior changes and stopped packages"
[22]: https://developer.android.com/training/articles/direct-boot "Android Direct Boot"
[23]: https://developer.android.com/topic/performance/background-optimization "Android background optimization and device restrictions"
