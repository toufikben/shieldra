# Shieldra Master Roadmap

**Repository:** `toufikben/shieldra`
**Branch:** `main`
**Current HEAD:** `6dc83964851ff3afce125abe65b340edd6095ae3`
**Last observed remote CI:** `35778220471` (`in_progress` at the last audit)
**Project mode:** controlled sequential implementation; Phase 3 source/build/static scope complete; Phase 4 production capabilities are not yet implemented.
**Decision authority for this cycle:** [`../SHIELDRA_DECISIONS.md`](../SHIELDRA_DECISIONS.md)

## Operating rule

Every meaningful batch follows: **READ → TRACE → IMPLEMENT → BUILD/TEST → RE-AUDIT → COMMIT → PUSH → VERIFY**. Dependent security areas are sequential. Only independent documentation, localization, UI, design-system, and test work may be parallelized when file ownership cannot conflict.

## Current state summary

| Area | Status | Evidence |
|---|---|---|
| Repository and CI foundation | DONE | Group A diagnostics and GitHub workflow |
| Group B project structure | DONE | Android app module and boundaries |
| Group C pure contracts | DONE / READY FOR CLOSURE | `app/src/main/kotlin/com/shieldra/domain` |
| Phase 3 presentation shell | DONE for source/build/static scope | Batches 0–9 and remediation reports |
| Batch 1 foundation fixes | DONE | Commit `bf5e77c` |
| Batch 2 screen states and PreviewData isolation | DONE | Commit `ef66c94` |
| Runtime device visual/RTL/accessibility verification | UNKNOWN | No emulator or connected device available |
| Phase 4 authority register | UPDATED | `docs/PHASE4_DECISION_REGISTER.md` |
| Phase 4 production behavior | BLOCKED / CONTROLLED | No Android security capability implemented |

## Gate status

| Gate | Status | What is authorized now | What remains |
|---|---|---|---|
| G0 — Authority | APPROVED FOR INTERNAL DEVELOPMENT | Continue without historical Product Freeze dependency; internal register is authoritative for this cycle | Later product/release authority may supersede it |
| G1 — Protection rules | APPROVED FOR PURE CONTRACT CLOSURE | Encode approved Guard semantics in framework-free contracts/tests | Android feasibility, response, evidence, and API limitations |
| G2 — Evidence/privacy | APPROVED PRINCIPLE / DETAILS BLOCKED | Maintain minimum post-confirmation evidence contracts and matrices | Capture, consent, retention, deletion, export, redaction, crypto |
| G3 — Android monitoring | APPROVED PRINCIPLE / DETAILS BLOCKED | Research capability limits and prepare adapters without runtime behavior | Service, permissions, device matrix, Doze/OEM/recovery |
| G4 — Storage/security | APPROVED DIRECTION / DETAILS BLOCKED | Define pure repository contracts and storage proposal | Schema, migrations, crypto, Keystore, backup, auth |
| G5 — External services | DEFERRED | Keep provider interfaces only where already justified | Cloud, delivery, billing, ads, analytics, backend |
| G6 — Security review | APPROVED PROCESS / FINAL AUDIT PENDING | Continuous AI/owner review and evidence collection | Dedicated security/privacy/release audit |
| G7 — Implementation | READY FOR CONTROLLED PURE BATCHES | Batch 1 pure Guard contract closure is active | Sequential engine, pipeline, then isolated platform capabilities |

## Phases and batches

### Phase 0 — Repository/Foundation Audit

**Status:** DONE. The repository, branch, build, CI, source boundaries, resources, tests, and existing reports have been rechecked at `ef66c94`.

### Phase 1 — Product/Handoff Verification

**Status:** DONE for the available supplied scopes; historical Product Freeze v5 and complete Phase 1 files are not required for this cycle under approved G0-01. The internal decision record is now authoritative and preserves unresolved items explicitly.

### Phase 3 — UI/Foundation Completion

**Status:** DONE for source/build/static scope. The presentation shell, localization, accessibility source checks, screen-state previews, deferred-action honesty, and PreviewData removal are complete. Runtime device claims remain UNKNOWN.

### Phase 4 — Protection Architecture

**Status:** IN PROGRESS — documentation and contract preparation only.

**Completed:** approved Guard decisions, evidence principle, device-first direction, storage direction, external-service deferral, sequential execution rule, capability/evidence/storage matrices.

**Remaining:** pure contract closure, event identity/idempotency representation, result/error semantics, lifecycle/expiry decision closure, and technical Android capability reports.

**Dependencies:** Batch 0 documentation complete; Batch 1 must remain framework-free and deterministic.

**Approval requirements:** do not implement Android security behavior, capture, monitoring, persistence engine, encryption, or providers until their open decisions are approved.

### Phase 5 — Guard Implementation

**Status:** BLOCKED after pure engine contracts until Android feasibility and capability-specific approval exist.

**Order:** Lock, Motion, SIM, Battery only after each rule/API review; Panic is a separate direct-action capability. Battery security anomaly remains undefined and must not be invented.

### Phase 6 — Evidence & Privacy

**Status:** BLOCKED. The minimum-evidence principle is approved, but camera/location policy, permission, retention, deletion, export, redaction, encryption, and backup are unresolved.

### Phase 7 — Continuous Monitoring

**Status:** BLOCKED. The principle is maximum technically feasible legitimate monitoring without guarantees; service type, lifecycle, restart, Doze, OEM, Force Stop, notification, and device matrix are unresolved.

### Phase 8 — Local Alerts/Response

**Status:** BLOCKED. Panic severity is approved; local response, notifications, lock-screen disclosure, deduplication, and failure semantics remain open.

### Phase 9 — External Services

**Status:** DEFERRED. Device-first scope excludes cloud, email, SMS, messaging providers, billing, ads, analytics, and backend services until a new approval changes G5.

### Phase 10 — Security/Privacy Audit

**Status:** REQUIRED before production/security claims. It must cover threat model, abuse cases, privacy, permissions, retention, evidence exposure, dependencies, background execution, device/OEM evidence, and recovery.

### Phase 11 — Release Hardening

**Status:** NOT STARTED. Requires all implementation batches, device/provider/migration/recovery evidence, rollback plan, and final release authority.

## Dependency-aware execution plan

| Batch | Scope | Dependencies | Status |
|---|---|---|---|
| 0 | Reconciled decisions, gate register, Guard rules, Android/evidence/storage matrices, roadmap | Unified prompts | DONE — `d50e626` |
| 1 | Pure Guard contract closure: typed observations, approved rule values, confirmation results, deterministic tests | Batch 0; no Android APIs or persistence engine | DONE — `6dc8396` |
| 2 | Pure `ProtectionStateEngine`: Lock/Motion/SIM/Panic approved semantics; Battery remains non-event until anomaly approval | Batch 1; pure deterministic implementation only | IN PROGRESS |
| 3 | Pure `EventPipeline` coordinator: stage ownership, idempotency, deferred/resume/failure semantics over abstractions | Batch 2 plus unresolved stage decisions | BLOCKED / NEEDS DECISION CLOSURE |
| 4a | Android capability research and device matrix | G3 review; no production behavior | READY AS INVESTIGATION |
| 4b | Storage adapter | G4 schema/crypto/migration approval | BLOCKED |
| 4c+ | One platform capability per batch: one Guard, evidence, auth, or monitoring | Capability-specific approval and device evidence | BLOCKED |
| 5 | Local response and notifications | G2/G3 policy | BLOCKED |
| 6 | External providers | G5 change from deferred | DEFERRED |
| 7 | End-to-end security/release review | All prior batches | BLOCKED |

## Required next batch

**Batch 2 — Pure `ProtectionStateEngine`** is the active safe implementation batch. It may implement only the approved pure Guard confirmation semantics over the Batch 1 contracts with injected Clock/IdentityProvider. It must not add Room, DataStore, Keystore, sensors, camera, location, notifications, workers, services, network, billing, or provider SDKs. Battery remains non-event until an anomaly is separately approved.
