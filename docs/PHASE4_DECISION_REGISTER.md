# SHIELDRA Phase 4 Decision Register

**Last reconciled:** 2026-09-22
**Authority:** [`../SHIELDRA_DECISIONS.md`](../SHIELDRA_DECISIONS.md) and the approved guard update supplied for this cycle.
**Implementation status:** Phase 4 production implementation remains controlled and sequential; platform/security details not marked approved remain blocked.

## Gate status

| Gate | Current status | Meaning |
|---|---|---|
| G0 — Project authority | **APPROVED FOR INTERNAL DEVELOPMENT** | The internal register authorizes continued work without waiting for historical Product Freeze files. |
| G1 — Protection rules | **APPROVED FOR PURE CONTRACT CLOSURE** | Guard confirmation rules are approved below; Android feasibility, response, and evidence details remain open. |
| G2 — Evidence/privacy | **APPROVED PRINCIPLE / DETAILS BLOCKED** | Minimum, post-confirmation, Guard-specific evidence only; capture, retention, deletion, export, and encryption details remain unresolved. |
| G3 — Android monitoring | **APPROVED PRINCIPLE / DETAILS BLOCKED** | Maximum feasible legitimate monitoring; no guarantee against Android/OEM/Force Stop limits. |
| G4 — Storage/security | **APPROVED DIRECTION / DETAILS BLOCKED** | Room + DataStore + encrypted evidence-file direction is approved; schema, migrations, algorithms, keys, and backup remain open. |
| G5 — External services | **DEFERRED** | Device-first; external providers, cloud, billing, ads, analytics, and remote delivery are not part of the initial implementation. |
| G6 — Security review | **APPROVED PROCESS / FINAL AUDIT PENDING** | Owner + AI review continues; dedicated security/privacy review is required before production security claims. |
| G7 — Implementation | **READY FOR CONTROLLED PURE BATCHES** | Sequential implementation only, beginning with contracts and deterministic tests. |

## Classification rules

Every item is classified as **FACT**, **ANDROID CONSTRAINT**, **APPROVED DECISION**, **PROPOSED DECISION**, **ASSUMPTION**, **UNKNOWN**, or **DEFERRED**. A proposed or unknown item cannot be treated as approved by implementation convenience.

## Approved G1 guard rules

| Guard | Signal | Confirmation | Reset/cooldown | Severity | Evidence boundary |
|---|---|---|---|---|---|
| Lock | Wrong PIN attempt | Two consecutive wrong attempts within two minutes | Reset after ten minutes without another failure; five-minute cooldown for the same attack sequence | HIGH | No evidence from a Signal alone; post-confirmation minimum relevant evidence only |
| Motion | Meaningful, unusual movement after quality validation | Strong/unusual movement lasting approximately three seconds, or two suspicious movements within ten seconds; isolated noise ignored | Exact engine representation, calibration, and recovery remain implementation details to verify | HIGH when clearly abnormal | Motion-relevant minimum evidence only after confirmation |
| SIM | Confirmed subscription/SIM state change | Immediate confirmed event; no repetition requirement | API/device feasibility and baseline semantics remain to verify | HIGH | SIM-relevant minimum metadata only after confirmation |
| Battery | Battery/power observation | Battery percentage alone never confirms an event; abnormal power/charging anomaly is not yet defined | No anomaly implementation until separately approved | LOW/MEDIUM only if a future anomaly is approved | Normally battery/power metadata, not camera |
| Panic | Explicit user activation | Immediate confirmed event; no delay or attempt count | Same-event handling still requires engine idempotency/cooldown semantics | CRITICAL/HIGHEST | Only approved Panic-relevant minimum evidence |

## Approved architecture direction

The first implementation path is device-first and framework-free where possible. External services are deferred. Room, DataStore, and encrypted evidence files are a direction, not authorization to add dependencies or finalize cryptography. Android capabilities must be introduced one adapter at a time after capability and permission review.

## Open decisions before platform production code

1. Android API and OEM feasibility for PIN, sensors, SIM/subscription state, battery anomalies, boot, and monitoring.
2. Event identity, deduplication, replay protection, concurrency, expiry, and same-event cooldown representation.
3. Camera/location trigger, permission, precision/freshness, retention, deletion, export, redaction, and backup policy.
4. Storage schema, migration, corruption recovery, backup exclusion, encryption algorithm, Keystore lifecycle, and authentication binding.
5. Notification policy, local response, supported-device matrix, Doze/OEM policy, process death, reboot, and Force Stop behavior.
6. Final security/privacy audit scope and release claims.

## Safe execution order

| Batch | Scope | Dependency | Status |
|---|---|---|---|
| 0 | Decision register, guard proposal, capability/evidence/storage matrices, roadmap | Unified approved prompts | **IN PROGRESS** |
| 1 | Pure contract closure and deterministic result/error tests | Batch 0; no Android APIs | **READY AFTER BATCH 0** |
| 2 | Pure ProtectionStateEngine with injected clock/identity | Batch 1 + approved G1 rules | **BLOCKED UNTIL BATCH 1** |
| 3 | Pure EventPipeline coordinator over approved abstractions | Batch 2 + stage/idempotency decisions | **BLOCKED / OPEN DETAILS** |
| 4 | One isolated platform capability at a time | G2/G3/G4 details and device matrix | **BLOCKED** |
| 5 | Local response/evidence/monitoring in separate batches | Capability-specific approval and evidence | **BLOCKED** |
| 6 | External providers | G5 decision change | **DEFERRED** |
| 7 | End-to-end security/release review | All implementation batches | **BLOCKED** |

## Batch 3 EventPipeline approval amendment — 2026-09-22

The following seven decisions are now **APPROVED** for the local/in-memory Batch 3 implementation:

| Decision | Approved rule |
|---|---|
| Event identity/idempotency | `EventId` is canonical; same-ID processing is idempotent; retries/resume continue the existing event. |
| Lifecycle | Add explicit `EXPIRED`; it is distinct from `FAILED_FINAL`; valid transitions are documented in `EventState.kt`. |
| Stage ownership | Coordinator orchestrates Confirmed → Evidence → Delivery → Completed; repository owns persistence and atomic updates. |
| Resume | Resume from the last successfully persisted stage/state; do not restart automatically from the beginning. |
| Expiration | Per-event/per-Guard policy; exact durations remain configurable and are not invented here. |
| Concurrency | Sequential processing per `EventId`; duplicate concurrent processing is prevented; independent events may proceed independently. |
| Initial implementation | Local/in-memory repository and coordinator first; Room remains a future adapter with no coordinator redesign. DataStore remains for lightweight preferences and encrypted files remain the evidence direction. |

This amendment opens only the pure/local EventPipeline architecture. Android APIs, Room, evidence capture, encryption, network delivery, external providers, billing, ads, and production monitoring remain gated.

## Batch 4a research completion — 2026-09-22

Batch 4a research is **COMPLETE AS DOCUMENTATION ONLY**. Official Android sources were reviewed for motion sensors, SIM/subscription APIs, background execution, Doze, WorkManager, foreground services, notifications, camera, and location. The results are recorded in [`PHASE4_ANDROID_CAPABILITY_MATRIX.md`](PHASE4_ANDROID_CAPABILITY_MATRIX.md) and [`reports/batch-4a-android-capability-research.md`](reports/batch-4a-android-capability-research.md).

The device/API test plan is recorded in [`PHASE4_DEVICE_TEST_MATRIX.md`](PHASE4_DEVICE_TEST_MATRIX.md). This completion does not authorize Android permissions, services, sensors, telephony, notifications, camera, location, Room, or monitoring code. Device execution evidence and capability-specific approval remain required before any platform adapter.

## Batch 4b device-execution preflight — 2026-09-22

Batch 4b is **BLOCKED at preflight**. The active environment has no `adb`, no Android Emulator binary, no enumerated AVD, and no attached physical Android device. Therefore no device capability result is marked as passed. The exact checks, observations, and unblock requirements are recorded in [`reports/batch-4b-device-execution-preflight.md`](reports/batch-4b-device-execution-preflight.md).

The storage adapter remains a separate Batch 4c and is still blocked by G4 schema, migration, crypto, Keystore, and backup decisions. No renumbering of this gate authorizes storage or platform implementation.

## Batch 4c storage and Keystore candidate — 2026-09-23

Batch 4c is **CANDIDATE IMPLEMENTATION COMPLETE / G4 STILL OPEN**. The repository now contains a versioned schema contract, explicit migration boundary, explicit encryption policy, and an Android Keystore-backed AES-GCM candidate with no software-key fallback. Deterministic schema/policy tests and the full local build, unit tests, lint, and debug assembly passed.

The candidate is not wired into Room, DataStore, EventPipeline, evidence capture, or the application graph. No retention, deletion, export, key rotation, recovery, biometric policy, authentication UX, or backup restoration policy was invented. Production storage integration remains blocked until G4 approves the open schema, migration, corruption recovery, crypto, Keystore lifecycle, authentication binding, and backup decisions. See [`reports/batch-4c-storage-keystore-schema.md`](reports/batch-4c-storage-keystore-schema.md).

## G4 security acceptance gap review — 2026-09-23

The G4 acceptance review is **COMPLETE AS GAP ANALYSIS / NOT ACCEPTED FOR CLOSURE**. The review records 18 closure requirements. Batch 4c provides a locally verified schema and Keystore candidate, but production integration remains blocked by unresolved data lifecycle, migration, corruption recovery, key lifecycle, authentication binding, hardware-backed, runtime-device, threat-model, and security/privacy sign-off requirements. The full checklist is in [`reports/g4-security-acceptance-gap-review.md`](reports/g4-security-acceptance-gap-review.md).
