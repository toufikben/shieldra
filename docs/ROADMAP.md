# Shieldra Master Roadmap

**Repository:** `toufikben/shieldra`
**Branch:** `main`
**Current HEAD:** `d58f3cf78ed2085f1840c8033c4d17714640fa6f`
**Last observed remote CI:** `35816089777` (`in_progress` at the last audit)
**Project mode:** controlled sequential implementation; Phase 3 source/build/static scope complete; Phase 4 production capabilities remain gated while Batch 4b device execution and G4 approvals are pending. G4 gap analysis and the G4-A conservative decision set are now recorded.
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
| Phase 4 production behavior | BLOCKED / CONTROLLED | Pure local EventPipeline and Batch 4c storage/Keystore candidate are present; no production storage integration or Android security capability is enabled |

## Gate status

| Gate | Status | What is authorized now | What remains |
|---|---|---|---|
| G0 — Authority | APPROVED FOR INTERNAL DEVELOPMENT | Continue without historical Product Freeze dependency; internal register is authoritative for this cycle | Later product/release authority may supersede it |
| G1 — Protection rules | APPROVED FOR PURE CONTRACT CLOSURE | Encode approved Guard semantics in framework-free contracts/tests | Android feasibility, response, evidence, and API limitations |
| G2 — Evidence/privacy | APPROVED PRINCIPLE / DETAILS BLOCKED | Maintain minimum post-confirmation evidence contracts and matrices | Capture, consent, retention, deletion, export, redaction, crypto |
| G3 — Android monitoring | APPROVED PRINCIPLE / DETAILS BLOCKED | Batch 4a research and device matrix only | Service, permissions, device evidence, Doze/OEM/recovery |
| G4 — Storage/security | APPROVED DIRECTION / DETAILS BLOCKED | Reviewable schema/Keystore candidate, 18-point gap review, and G4-A proposals | Owner/security approval, implementation evidence, data/key lifecycle, recovery, runtime evidence, threat model, sign-off |
| G5 — External services | DEFERRED | Keep provider interfaces only where already justified | Cloud, delivery, billing, ads, analytics, backend |
| G6 — Security review | APPROVED PROCESS / FINAL AUDIT PENDING | Continuous AI/owner review and evidence collection | Dedicated security/privacy/release audit |
| G7 — Implementation | READY FOR CONTROLLED PURE BATCHES | Batch 3 local EventPipeline is authorized and implemented | Sequential platform capabilities remain gated |

## Phases and batches

### Phase 0 — Repository/Foundation Audit

**Status:** DONE. The repository, branch, build, CI, source boundaries, resources, tests, and existing reports have been rechecked at `ef66c94`.

### Phase 1 — Product/Handoff Verification

**Status:** DONE for the available supplied scopes; historical Product Freeze v5 and complete Phase 1 files are not required for this cycle under approved G0-01. The internal decision record is now authoritative and preserves unresolved items explicitly.

### Phase 3 — UI/Foundation Completion

**Status:** DONE for source/build/static scope. The presentation shell, localization, accessibility source checks, screen-state previews, deferred-action honesty, and PreviewData removal are complete. Runtime device claims remain UNKNOWN.

### Phase 4 — Protection Architecture

**Status:** IN PROGRESS — pure/local architecture and Batch 4c storage/Keystore candidate implemented; platform and production storage behavior remain gated.

**Completed:** approved Guard decisions, evidence principle, device-first direction, storage direction, external-service deferral, sequential execution rule, capability/evidence/storage matrices.

**Remaining:** device execution evidence, G4 approval details, capability-specific approvals, evidence/privacy details, and platform adapters. Batch 4b preflight is blocked because no ADB, emulator, AVD, or physical device is available in the active environment. The G4 gap review identifies 18 closure requirements; production storage remains prohibited.

**Dependencies:** Batch 0 documentation complete; Batch 1 must remain framework-free and deterministic.

**Approval requirements:** do not implement Android security behavior, capture, monitoring, Room persistence, encryption, or providers until their open decisions are approved. Batch 3 is limited to the verified local/in-memory architecture.

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
| 2 | Pure `ProtectionStateEngine`: Lock/Motion/SIM/Panic approved semantics; Battery remains non-event until anomaly approval | Batch 1; pure deterministic implementation only | DONE — `0e02f55` |
| 3 | Local/in-memory `EventPipeline`: canonical EventId idempotency, EXPIRED lifecycle, coordinator/repository split, persisted-stage resume, per-event expiry, sequential per-event processing | Batch 2; approved Batch 3 decisions | DONE — `f6f637b` |
| 4a | Android capability research and device matrix | G3 review; no production behavior | RESEARCH COMPLETE — DEVICE EXECUTION PENDING |
| 4b | Execute device/API matrix and collect runtime evidence | Batch 4a; ADB/AVD or physical devices | BLOCKED — NO DEVICE OR AVD AVAILABLE |
| 4c | Versioned schema and Keystore candidate; no production wiring | G4 review; no silent crypto or retention decisions | CANDIDATE COMPLETE — BUILD/TEST PASS; G4 STILL OPEN |
| 4d+ | One platform capability per batch: one Guard, evidence, auth, or monitoring | Capability-specific approval and device evidence | BLOCKED |
| 5 | Local response and notifications | G2/G3 policy | BLOCKED |
| 6 | External providers | G5 change from deferred | DEFERRED |
| 7 | End-to-end security/release review | All prior batches | BLOCKED |

## Required next batch

**G4-A — Conservative decision set** is now documented under [`docs/security/`](security/). It converts the remaining gaps into proposed, reviewable policies without opening G4. Batch 4c remains locally verified but is not production storage; Batch 4b device execution, owner/security approval, and implementation evidence remain blocked.
