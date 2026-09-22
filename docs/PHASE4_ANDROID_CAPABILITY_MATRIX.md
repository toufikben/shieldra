# SHIELDRA Phase 4 Android Capability Matrix

**Batch:** 4a
**Status:** Research complete; platform implementation remains gated.
**Last researched:** 2026-09-22
**Primary sources:** Android Developers documentation, cited inline and in the research report.

| Capability | Officially supported surface | Confirmed constraints | Shieldra status | Required evidence before adapter |
|---|---|---|---|---|
| Motion sensors | `SensorManager`, `Sensor`, `SensorEvent`, `SensorEventListener`; runtime inventory and listener registration [1] [2] | Inventory and properties vary by device. Android 9+ suppresses continuous/on-change/one-shot callbacks for background apps. Android 12+ applies rate limits to affected motion/position sensors. Processor suspension, Doze, process death, and OEM policy prevent continuity guarantees. | **CONDITIONAL** | Runtime inventory, foreground/background comparison, lawful foreground-service test, rate-limit test, screen-off/wake-up test, Doze and process recovery |
| SIM/subscription | `TelephonyManager`, `SubscriptionManager`, `SubscriptionInfo`, subscription listeners, and selected `TelephonyCallback` interfaces [4] [5] | Feature and permission checks are required. Records and fields are caller-visible and may be redacted. Logical slots, physical slots, subscription IDs, card IDs, eSIM, and MEP differ. Callbacks are process-bound and not durable logs. | **CONDITIONAL / UNKNOWN** | No-telephony, permission, single-SIM, dual-SIM/eSIM/MEP, callback recovery, Doze reconciliation |
| Background execution | WorkManager for persistent eventual work; foreground services for user-visible ongoing work [8] [9] [10] | Background services, foreground-service starts, service types, while-in-use permissions, Doze, Standby, hibernation, force-stop, reboot, process death, and OEM restrictions apply. No exact-time or always-alive guarantee. | **CONDITIONAL** | WorkManager reboot/kill, Doze/Standby, FGS start matrix, type prerequisites, force-stop, Direct Boot, OEM comparison |
| Notifications | `POST_NOTIFICATIONS`, notification channels, visibility and `NotificationManager` [12] [13] [14] | API 33+ runtime permission; API 26+ channels for target-26+ apps. Users and system policy control channel behavior, alerting, lock-screen exposure, DND, and ranking. | **CONDITIONAL** | Fresh install grant/deny, upgrade pre-grant, channel override, lock-screen matrix, Doze/process recovery, OEM presentation |
| Camera | `CAMERA` runtime permission and typed camera foreground service [18] [19] | Device-wide camera toggle, while-in-use permission, visible-start rule, service type and API 34+ prerequisites apply. Background capture is not generally available by merely declaring permission. | **BLOCKED** | Consent/trigger/privacy policy, permission revocation, device toggle, typed FGS visible-start and negative background-start tests |
| Location | Foreground/background permissions, approximate/precise choice, typed location foreground service [15] [16] [17] | API 26+ ordinary background updates are limited to only a few times per hour. API 29+ needs location FGS type; API 30+ background permission flow differs; API 34+ adds type prerequisites. | **BLOCKED** | Precision/freshness policy, consent, background grant, service prerequisites, cadence, Doze, process and OEM recovery |
| Room/DataStore | Planned storage direction only | Schema, migrations, backup, corruption recovery, crypto, and key lifecycle are not approved. | **APPROVED DIRECTION / DETAILS UNKNOWN** | G4 storage/security decision package |
| Encrypted evidence files | Planned evidence direction only | Algorithm, Keystore lifecycle, invalidation, retention, deletion, export, and backup are unresolved. | **APPROVED DIRECTION / DETAILS UNKNOWN** | Threat model and crypto/privacy review |

## Cross-cutting rule

A capability is supported only when its runtime feature, permission, user setting, process state, and device policy satisfy the documented prerequisites. Missing hardware, denied permission, unavailable telephony, deferred scheduling, process death, force-stop, Doze, or OEM restriction must produce an explicit unsupported/deferred/unknown result. None may be represented as guaranteed continuous protection.

## Required device coverage

Use [`PHASE4_DEVICE_TEST_MATRIX.md`](PHASE4_DEVICE_TEST_MATRIX.md). The minimum matrix spans API 26, 29, 30, 31, 33, 34, and 35 AOSP images, plus representative physical OEM, telephony, dual-SIM/eSIM/MEP devices. Emulator results do not replace physical evidence for radio, SIM, sensor inventory, camera hardware, battery management, or OEM behavior.

## References

[1]: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview "Android sensor framework and sensor availability"
[2]: https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion "Android motion sensors"
[3]: https://developer.android.com/reference/android/hardware/SensorManager "SensorManager API reference"
[4]: https://developer.android.com/reference/android/telephony/SubscriptionManager "SubscriptionManager API reference"
[5]: https://developer.android.com/reference/android/telephony/TelephonyManager "TelephonyManager API reference"
[6]: https://developer.android.com/develop/connectivity/telecom/dialer-app/telephony-ids "Android telephony identifiers"
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
