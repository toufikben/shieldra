# SHIELDRA Internal Decisions

**Status:** Authoritative internal development record for this cycle.
**Repository:** `toufikben/shieldra`
**Last reconciled:** 2026-09-30

This document records decisions approved in the unified SHIELDRA engineering protocol. It does not invent unresolved security, privacy, Android, cryptographic, provider, or release policy. Items not marked **APPROVED** remain proposed, unknown, deferred, or blocked.

## Approved decisions

| ID | Area | Decision | Status | Boundary |
|---|---|---|---|---|
| G0-01 | Project authority | Continue development without waiting for historical Product Freeze v5 or Phase 1 files; this internal register is authoritative for the current cycle. | **APPROVED** | It does not replace a later product or security review. |
| G0-02 | Architecture | Device-first architecture. | **APPROVED** | Cloud and external providers are deferred initially. |
| G0-03 | Monitoring | Maximum technically feasible continuous protection using legitimate Android mechanisms; never claim guaranteed 24/7 operation. | **APPROVED PRINCIPLE** | API, permission, OEM, Doze, and recovery details remain to be decided. |
| G0-04 | Storage direction | Room for structured data, DataStore for preferences/configuration, and encrypted evidence files with metadata references. | **APPROVED DIRECTION** | Cryptographic algorithms, key lifecycle, schema, migrations, and backup policy require technical review. |
| G0-05 | External services | External cloud, delivery, billing, ads, analytics, and backend services are deferred initially. | **APPROVED / DEFERRED** | Interfaces may exist; production providers must not be added without approval. |
| G0-06 | Review | Continuous owner + AI review during development; final dedicated security/privacy audit before production security claims. | **APPROVED PROCESS** | The final audit is still required and is not claimed complete. |
| G0-07 | Execution | Small sequential batches. Every batch follows READ → TRACE → IMPLEMENT → BUILD/TEST → RE-AUDIT → COMMIT → PUSH → VERIFY. | **APPROVED PROCESS** | Dependent security areas are never parallelized. |
| G1-01 | Lock Guard | One wrong PIN is a Signal. Two consecutive wrong PIN attempts within two minutes produce a confirmed Lock Security Event. Reset after ten minutes without another failed attempt. Five-minute same-sequence cooldown. Severity HIGH. | **APPROVED RULE** | Actual Android observability and API/OEM feasibility remain to be verified. |
| G1-02 | Motion Guard | Motion is not counted like PIN attempts. Confirm after strong/unusual movement lasting approximately three seconds, or two suspicious movements within ten seconds, after sensor-quality validation. Ignore isolated noise. | **APPROVED RULE** | Sensor API support, calibration, sampling, and battery behavior remain to be verified. |
| G1-03 | SIM Guard | A confirmed SIM/subscription state change is an immediate confirmed event without repetition. Severity HIGH. | **APPROVED RULE** | Android API/device limitations must be documented before implementation. |
| G1-04 | Battery Guard | Battery percentage alone is not a security event. No battery security event is implemented until an abnormal power/charging condition is separately defined and approved. | **APPROVED LIMITATION** | Battery metadata may be evidence; the security anomaly remains unknown. |
| G1-05 | Panic Guard | Explicit user Panic activation is an immediate confirmed event without confirmation delay or attempt counting. Severity CRITICAL/HIGHEST. | **APPROVED RULE** | Response, recipients, and evidence remain separately undecided. |
| G2-01 | Evidence | No continuous camera or location capture. A Signal alone does not collect evidence. Collection begins only after a confirmed event and only for approved Guard-relevant minimum evidence. | **APPROVED PRINCIPLE** | Camera/location permissions, retention, deletion, export, and crypto details remain unresolved. |
| G3-01 | Monitoring honesty | Use legitimate Android mechanisms and recovery strategies; document Doze, OEM restrictions, process death, reboot, and Force Stop limitations. | **APPROVED PRINCIPLE** | Exact service, permission, and supported-device policy remains unresolved. |

## Explicitly not approved yet

The following must not be silently selected or implemented: exact camera trigger, location precision/freshness thresholds, evidence retention duration, deletion/export authority, cryptographic algorithm and Keystore lifecycle, authentication provider and session policy, Android foreground/background mechanism, notification payload policy, storage schema and migration details, external provider, delivery retry policy, billing, ads, accounts/cloud, or final release claims.

> **Superseded where the owner has since ruled.** This list is the 2026-09-22 state and is
> kept as written. Each item the owner has since decided is recorded as a numbered amendment
> in the §17 log of
> [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md),
> which is the binding authority: **A-1** quarantine window, **A-2/A-3** factory-reset key
> deletion and its failure record, **A-4** missing-key reads surface as recovery-required,
> **A-5** the §15 production storage gate opened, **A-6** real local notifications with
> `POST_NOTIFICATIONS` requested only from the notification setup screen, and **A-7** the
> durable Panic activation count (Room v2→v3). So "notification payload policy" and the
> Room schema/migration details are no longer unapproved — they are owner-approved within
> the boundaries those amendments state. Items with no amendment listed above remain
> unapproved, including the Android foreground/background mechanism, external providers,
> billing, and ads.

## Status vocabulary

- **APPROVED:** authorized product/process decision.
- **APPROVED PRINCIPLE/DIRECTION:** the direction is authorized, but implementation parameters remain open.
- **PROPOSED — NOT APPROVED:** a concrete option for review only.
- **UNKNOWN:** requires technical or product information.
- **DEFERRED:** intentionally not part of the current device-first scope.
- **BLOCKED:** cannot safely proceed until an input or approval exists.

## Operating rule

Engineer approved behavior, propose unresolved behavior without implementing it, verify actual Android limits, and keep the roadmap synchronized after every meaningful batch.

## Batch 3 approved EventPipeline decisions — 2026-09-22

The following seven decisions are **APPROVED** for the first local/in-memory EventPipeline implementation. They supersede the earlier Batch 3 design-review entries that marked these points as proposals.

1. **Event identity and idempotency:** `EventId` is the canonical identity. No second sequence identity is introduced unless the code genuinely requires it. Reprocessing the same `EventId` must not create a duplicate event or duplicate final delivery; retries and resume continue the existing event.
2. **Lifecycle:** Add explicit `EXPIRED`. Expiration is not `FAILED_FINAL`. Valid transitions remain documented in the domain state contract.
3. **Stage ownership:** The coordinator owns `Confirmed → Evidence → Delivery → Completed` orchestration and chooses the next stage. The repository owns persistence and atomic state updates. The coordinator must not depend on storage implementation details.
4. **Interruption/resume:** Resume from the last successfully persisted stage/state. Do not restart from the beginning unless the persisted state requires it.
5. **Expiration policy:** Support a per-event/per-Guard expiration policy. Exact durations remain configurable and policy-driven; no new product/security threshold is invented.
6. **Concurrency:** Process stages sequentially per `EventId` and prevent duplicate concurrent processing of the same event. Independent events may proceed independently where safe.
7. **Initial implementation:** Implement the first pipeline locally/in-memory behind clean repository interfaces. Room remains the planned future repository for events, history, pipeline state, metadata, and idempotency tracking; DataStore remains for lightweight preferences; sensitive evidence remains intended for encrypted files rather than raw Room blobs.

These approvals authorize the pure/local Batch 3 architecture only. They do not authorize Room, Android services, evidence capture, encryption implementation, network delivery, providers, billing, ads, or production monitoring claims.

## G4-A conservative decision set — 2026-09-23

The supplied G4 Security Storage Decision Freeze is accepted as the current process authority for converting gaps into explicit proposals. The following documents are now available for review:

- [`docs/security/g4-data-inventory.md`](docs/security/g4-data-inventory.md)
- [`docs/security/g4-a-decision-set.md`](docs/security/g4-a-decision-set.md)
- [`docs/security/g4-android-verification-checklist.md`](docs/security/g4-android-verification-checklist.md)
- [`docs/security/g4-threat-model.md`](docs/security/g4-threat-model.md)
- [`docs/security/g4-acceptance-checklist.md`](docs/security/g4-acceptance-checklist.md)

These documents are **PROPOSED — REQUIRES APPROVAL**. They do not approve Room wiring, production storage, retention durations, export, key lifecycle, authentication binding, or Android runtime behavior. Any item requiring implementation or device verification remains explicitly open. G4 remains **APPROVED DIRECTION / DETAILS BLOCKED**.

## Decisions delegated by the owner — 2026-10-03

The owner asked for these to be decided after research and recorded here. Each one can be reversed by a later entry.

| ID | Decision | Status | Reason |
|---|---|---|---|
| D-2026-10-03-1 | **Motion reuse window = 10 minutes**, measured from the incident's persisted start (`MotionIncidentBinder.MOTION_REUSE_WINDOW_MINUTES`). | **APPROVED** | Nothing in the shipping app terminates a Motion incident, so without a window the guard recorded one event forever. 10 minutes is the value A-28 already approved for Panic. It groups one theft attempt into one incident, while a new attempt minutes later still raises a new alert. |
| D-2026-10-03-2 | **New dependencies**: `androidx.biometric:biometric:1.1.0`, `androidx.fragment:fragment:1.8.5`, `androidx.lifecycle:lifecycle-runtime-compose:2.9.2`. | **APPROVED** | Stopping monitoring must require the owner's biometric or screen lock (`SensitiveOperation.DISABLE_PROTECTION`); otherwise whoever holds the phone stops protection with one tap. The biometric library merges `USE_BIOMETRIC` into the final manifest. No credential is stored by SHIELDRA. |
| D-2026-10-03-3 | **Silent Panic uses a silent notification channel** (`IMPORTANCE_MIN`, no sound or vibration, `VISIBILITY_SECRET`) and fires on press-and-hold. | **APPROVED** | A panic that rings or shows on the lock screen tells the attacker it was pressed; a single tap recorded false emergencies. |

Verification: CI run `37128967055` (366/366 JVM tests, all static reviews PASS) and an on-device run on the RMX3910 (Android 16) of the CI-built APK pair: `OK (72 tests)`, excluding the two manual measurement campaigns (`MotionBackgroundMeasurementInstrumentedTest`, `MotionCalibrationInstrumentedTest`). A manual pass covered onboarding, permission, Panic (tap vs hold, silent channel), History, Event Detail, Motion start (specialUse FGS), the owner check on Stop (cancel keeps monitoring), and showed no crash.
