# Shieldra Master Roadmap

**Repository:** `toufikben/shieldra`
**Branch:** `main`
**Current HEAD:** `3c7b39d0677cff5ade5791285b83982e29792a42` (`docs: correct roadmap head and CI status`, 2026-09-23)
**Remote CI evidence:** the preceding implementation/documentation run `35883932062` for `69b4d8f` was observed `in_progress`; no result is claimed here for the newer `3c7b39d` documentation commit. The latest completed baseline run was `35826925039` (`success`).
**Project mode:** controlled sequential implementation; Phase 3 source/build/static scope complete; G4-B local storage implementation is in progress under the approved initial conservative policy. Android runtime verification and production approval remain pending.
**Decision authority for this cycle:** [`../SHIELDRA_DECISIONS.md`](../SHIELDRA_DECISIONS.md)

**Latest local implementation batch:** `ae52dd9` preserves event expiration across domain, InMemory, Room, and database reopen paths while aligning the Lock Guard demo with approved semantics. Gradle verification remains **BLOCKED BY MISSING ANDROID SDK** in the current environment; no Android runtime or device claim is made.

**Current audit batch:** expiration is now part of the domain `SecurityEvent` and is preserved by InMemory and Room state persistence, including database reopen coverage. The Phase 3 Lock Guard screen now displays the fixed approved value of two failed attempts within two minutes; unapproved Battery Emergency and seven-day retention demo claims were removed. This batch does not add Android adapters, production evidence capture, retention policy, or G4 approval.

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
| Phase 4 production behavior | CONTROLLED / G4-B IN PROGRESS | Approved initial local storage policy; Room/file adapter and persistence tests are authorized; Android runtime verification and production security approval remain disabled |

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

**Status:** IN PROGRESS — G4-B local storage implementation authorized under the initial conservative policy; platform/runtime and production behavior remain gated.

**Completed:** approved Guard decisions, evidence principle, device-first direction, storage direction, external-service deferral, sequential execution rule, capability/evidence/storage matrices.

**Remaining:** Room/file implementation evidence, device execution evidence, capability-specific approvals, evidence/privacy details, and final security sign-off. Batch 4b preflight is intentionally deferred to owner download/device testing; Android runtime behavior remains unverified. The G4 gap review identifies 18 closure requirements; production storage approval remains prohibited.

**Dependencies:** Batch 0 documentation complete; Batch 1 must remain framework-free and deterministic.

**Approval requirements:** implement only the locally authorized G4-B storage policy; do not claim Android runtime behavior, production readiness, or security certification. Capture, monitoring, providers, and external services remain gated.

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
| 4d-1 | Room foundation: version catalog, entities, DAOs, exported schema, database shell | Owner G4-B authorization; Batch 4c schema candidate | COMPLETE — BUILD/TEST/LINT/STATIC PASS; NOT WIRED TO APP |
| 4d-2 | Local Room repository boundary, encrypted evidence-file adapter, atomic file handling, deterministic file recovery tests | 4d-1; approved initial local policy | COMPLETE — BUILD/TEST/LINT/STATIC PASS; ANDROID KEYSTORE UNVERIFIED |
| 4d-3 | Room instrumented integration tests: reopen persistence, duplicate EventId, atomic expected-state updates, persisted delivery resume, foreign-key enforcement | 4d-2; Android test database | COMPLETE — `assembleDebugAndroidTest` PASS; DEVICE EXECUTION PENDING |
| 4d-4 | Cleanup/orphan reconciliation, quarantine foundations, migration-failure guards, and Room runtime execution | 4d-3; owner device/AVD | LOCAL PORTION COMPLETE — DEVICE EXECUTION PENDING |
| 4d-5 | Explicit local storage factory, no-backup evidence directory, non-destructive Room configuration, lifecycle reconciliation entry point | 4d-4; approved local policy | LOCAL COMPLETE — BUILD/TEST PASS; RUNTIME VERIFICATION PENDING |
| 4d-6 | Owner-device execution of Room/Keystore tests and runtime evidence package | 4d-5; owner device/AVD | PREPARED — BLOCKED HERE; `adb`/AVD REQUIRED |
| 4d-7 | Offline G4-B local closure audit: backup boundaries, schema evidence, wiring scope, no-fallback/no-destructive checks, reproducible review script | 4d-6; local implementation complete | IN PROGRESS — RUNTIME/SECURITY ACCEPTANCE OPEN |
| 4d+-lock-prep | Lock Guard domain boundary tests, Android feasibility notes, and device test plan | G1-01; 4d-6 runtime evidence pending | PURE BOUNDARY COMPLETE LOCALLY — ADAPTER NOT IMPLEMENTED; DEVICE EVIDENCE PENDING |
| 4d+-lock-pure-integration | Verified callback boundary to pure ProtectionStateEngine integration tests | 4d+-lock-prep; no Android APIs | COMPLETE LOCALLY — DEVICE/ADAPTER EVIDENCE PENDING |
| 4d+-local-hardening | Evidence-file deletion/quarantine failure handling, terminal pipeline idempotency, focused JVM tests | Existing local storage and Batch 3 contracts | COMPLETE LOCALLY — commit `309ac37`; Gradle re-run BLOCKED BY MISSING ANDROID SDK |
| 4d+-expiration-lock-ui | Domain/Room expiration consistency, fixed Lock Guard presentation, removal of unapproved demo policy claims, focused tests | Existing 4d storage boundary and approved Lock decisions | IMPLEMENTED LOCALLY — build verification BLOCKED BY MISSING ANDROID SDK; G4 remains open |
| 4d+ | One platform capability per batch: one Guard, evidence, auth, or monitoring | Capability-specific approval and device evidence | BLOCKED |
| 5 | Local response and notifications | G2/G3 policy | BLOCKED |
| 6 | External providers | G5 change from deferred | DEFERRED |
| 7 | End-to-end security/release review | All prior batches | BLOCKED |

## Required next batch

**G4-B — Local Storage Implementation** is authorized by the owner’s initial conservative implementation authorization. Sub-batch 4d-7 performs an offline closure audit of backup boundaries, exported schema evidence, factory wiring scope, and no-fallback/destructive-migration controls through a reproducible review script and report. The pure `4d+-lock-prep` boundary is now implemented and tested locally; the platform adapter remains unimplemented until runtime evidence and capability-specific approval exist. The active environment still has no `adb`, emulator, AVD, or attached device. Keep G4 as **APPROVED DIRECTION / IMPLEMENTATION IN PROGRESS**; do not claim Android runtime verification or production storage approval.
