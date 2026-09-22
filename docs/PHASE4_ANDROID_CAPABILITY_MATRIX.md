# SHIELDRA Phase 4 Android Capability Matrix

**Status:** Technical investigation matrix. It does not authorize platform implementation.
**Last reconciled:** 2026-09-22

| Capability | Current status | Permission/API question | Background limitation | OEM/device limitation | Safe next action |
|---|---|---|---|---|---|
| PIN/lock attempt detection | **UNKNOWN** | Determine whether supported apps can observe the required attempts; do not assume universal access | Process availability and Android security boundaries may prevent observation | OEM/API variation expected | Research and document feasibility; no implementation |
| Motion sensors | **PROPOSED CAPABILITY / DETAILS UNKNOWN** | Determine sensor availability, quality, sampling, and lifecycle | Doze, process death, battery policy, and sensor restrictions apply | Sensor quality and aggressive task killing vary | Capability spike and device matrix; no confirmed event producer yet |
| SIM/subscription state | **UNKNOWN** | Determine API, permission, multi-SIM/eSIM, and baseline semantics | Background access and callback reliability require testing | Carrier/OEM/API variation | Technical feasibility report before adapter |
| Battery/power state | **AVAILABLE AS METADATA, SECURITY ANOMALY UNKNOWN** | Battery/charging metadata may be observable | Process death and shutdown races apply | OEM power policy varies | Keep metadata contract; do not invent anomaly |
| Boot/restart | **UNKNOWN** | Determine legal receiver/restart behavior | Android restrictions and user Force Stop apply | OEM boot behavior varies | Document recovery policy before implementation |
| Foreground service | **PROPOSED — NOT APPROVED** | Service type, permission, notification, and user disclosure require approval | Start restrictions, Doze, notification permission, and Force Stop apply | OEM task killers vary | Capability and policy review only |
| WorkManager | **PROPOSED — NOT APPROVED** | Suitability depends on work type and timing guarantees | Quotas and deferred execution prevent continuous guarantees | OEM battery policy varies | Use only after background strategy approval |
| Notifications | **UNKNOWN** | Runtime permission, channel, lock-screen redaction, and accessibility copy | Delivery is not proof of user viewing | User/channel/OEM settings vary | Define local notification contract and failure semantics |
| Location | **UNKNOWN** | Foreground/background permission, approximate/precise, source and freshness | Background access is restricted and battery-sensitive | Accuracy and provider availability vary | Evidence policy and device matrix first |
| Camera | **UNKNOWN** | Permission, lifecycle, foreground/background restrictions | Background capture is restricted; no hidden capture | Camera hardware and OEM behavior vary | Contract/policy only; no capture |
| Room/DataStore | **APPROVED DIRECTION; DETAILS UNKNOWN** | Dependency, schema, migration, backup, corruption recovery | Process death and migration interruption apply | Device storage behavior varies | Pure repository contracts first; implementation after G4 details |
| Encrypted evidence files | **APPROVED DIRECTION; CRYPTO UNKNOWN** | Keystore, algorithm, key invalidation, backup exclusion | Key loss and device lock state affect recovery | Hardware-backed behavior varies | Threat-model and crypto review first |
| Process recovery | **UNKNOWN** | Need durable stage/idempotency semantics | Process death, reboot, Force Stop may interrupt work | OEM restrictions vary | Define recovery contract before workers |

## Global limitation

The product may aim for maximum technically feasible continuous protection, but Android/OEM restrictions, user Force Stop, Doze, process death, permissions, and unavailable sensors can prevent guarantees. The UI and documentation must state only tested behavior.
