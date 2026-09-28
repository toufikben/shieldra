# Shieldra Master Roadmap

**Repository:** `toufikben/shieldra`
**Branch:** `main`
**Current HEAD:** `origin/main` (the exact commit is the repository's current remote tip; this self-referential document intentionally avoids recording its own changing hash)
**Remote CI evidence:** runs `35886198586` (`b9d56fa`), `35886895480` (`2760107`), and `35886951932` (`1519701`) completed `success`. The corrected workflow now compiles `assembleDebugAndroidTest`; no run executes connected device tests. Domain batch `191b049` has run `35888768082`; CI hardening batch `abed3c4` has run `35888896037`; documentation batch `c423a47` has run `35888837174`; storage batch `1335a8b` has run `35889049875`. All four were `in_progress` at the last check; no success is claimed for them yet. AAD binding batch (`ea6bc7f`+`d317cb2`+`b5d7e9e`+`8f6a3af`+`6a13d59`) pushed 2026-09-26; CI run triggered.
**Project mode:** controlled sequential implementation; Phase 3 source/build/static scope complete; G4-B local storage implementation is in progress under the approved initial conservative policy. Android runtime verification and production approval remain pending.
**Decision authority for this cycle:** [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](authority/FINAL_SECURITY_STORAGE_DECISIONS.md) (binding, owner-supplied 2026-09-24) and [`../SHIELDRA_DECISIONS.md`](../SHIELDRA_DECISIONS.md)

**Latest local implementation batch (4d-aad-binding):** Implements AAD-v1 canonical binary serialization (`EvidenceAadBuilder`), mandatory AAD enforcement (`KeystoreEvidenceCipherWithAad`), versioned per-app key alias policy (`EvidenceKeyPolicy`), StrongBox detection (`AndroidKeystoreEncryptor`), all 10 unit tests required by decision §1.4, canonical vector tests, and device instrumentation test (`RealAesGcmAadTest`). `AndroidKeystoreEncryptor` is `open` for JVM test doubles. No production wiring.

**Build-fix lineage after `6a13d59`:** `3b07583` removes a duplicate `KeystoreEvidenceCipherWithAad` declaration; `e1e66fd` makes `RealAesGcmAadTest` JUnit4-compatible; `cc0a603` converts unit tests to `kotlin.test` (matching `testImplementation(kotlin("test-junit5"))`) and renames androidTest methods to `snake_case` (D8 rejects spaces pre-DEX-040); `31d53e2` lets Android Keystore generate the GCM IV on encrypt (real devices reject caller-provided IVs). Local JDK 17 validation: `testDebugUnitTest` 83/83 pass, `lintDebug` pass, `assembleDebug` + `assembleDebugAndroidTest` pass.

**Device evidence (owner device RMX3910, Android 16/API 36, 2026-09-27):** `connectedDebugAndroidTest` — 7/7 `RoomEventRepositoryInstrumentedTest` pass, 11/11 `RealAesGcmAadTest` pass (real AES-GCM + Android Keystore authentication, including wrong-AAD rejection). APK installed on device via `adb install`. This closes the prior UNKNOWN for Keystore/Room runtime behavior on at least one physical device; OEM matrix and background behavior remain open.

**Storage hardening:** commit `1335a8b` prevents symlink payload reads and filters symlinks from reconciliation, and checks every evidence/delivery child insert inside the Room transaction. It does not claim atomic multi-process ownership, AAD identity binding, migration recovery, or Keystore runtime behavior.

**Acceptance evidence:** [`docs/reports/g4-acceptance-matrix.md`](reports/g4-acceptance-matrix.md) now records Code/Test/CI/Device evidence separately. Android runtime work, AAD/Keystore lifecycle, migration/recovery policy, evidence retention/deletion, and production wiring are explicitly deferred rather than marked complete.

**4d-7 audit correction:** the first remote CI attempt exposed a real Kotlin compilation defect in the two repository overrides. It was fixed in `b9d56fa`. The audit also closed a backup-boundary gap by rejecting caller-provided evidence paths outside `noBackupFilesDir`. Implementation and instrumented-test compilation runs passed; device execution remains unconfirmed.

**CI coverage correction:** the successful `b9d56fa` run did not compile AndroidTest sources because the workflow invoked `assembleDebug` only. Commit `2760107` adds `assembleDebugAndroidTest`, and run `35886895480` passed. Instrumented compilation is now verified; device execution remains blocked.

**Pure-domain correction:** commit `191b049` fixes four verified logic gaps found by adversarial review: unbounded Motion repetition counts, out-of-order Lock timestamps, unsynchronized pure-engine state, and unsafe Pipeline resume/expiry/failure persistence behavior. Android adapters and runtime behavior remain deliberately deferred.

**G4-B CI enforcement:** commit `abed3c4` makes the G4-B review script fail on independently missing cloud-backup/device-transfer exclusions and missing Room catalog entries, and runs it in GitHub Actions. Negative fixture tests passed locally; runtime/device claims remain blocked.

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
| Final Security Storage Decisions | FILED AS SOURCE OF TRUTH | `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` |
| AAD-v1 binding implementation | CLOSED | `EvidenceAadBuilder`, `KeystoreEvidenceCipherWithAad`, `EvidenceKeyPolicy`, `AndroidKeystoreEncryptor` (StrongBox), 10 unit tests, canonical vector tests, `RealAesGcmAadTest`; commit `6a13d59` |
| Phase 4 production behavior | CONTROLLED / G4-B IN PROGRESS | Approved initial local storage policy; Room/file adapter and persistence tests are authorized; Android runtime verification and production security approval remain disabled |

## Gate status

| Gate | Status | What is authorized now | What remains |
|---|---|---|---|
| G0 — Authority | APPROVED FOR INTERNAL DEVELOPMENT | Continue without historical Product Freeze dependency; internal register is authoritative for this cycle | Later product/release authority may supersede it |
| G1 — Protection rules | APPROVED FOR PURE CONTRACT CLOSURE | Encode approved Guard semantics in framework-free contracts/tests | Android feasibility, response, evidence, and API limitations |
| G2 — Evidence/privacy | APPROVED PRINCIPLE / DETAILS BLOCKED | Maintain minimum post-confirmation evidence contracts and matrices | Capture, consent, retention, deletion, export, redaction, crypto |
| G3 — Android monitoring | APPROVED PRINCIPLE / DETAILS BLOCKED | Batch 4a research and device matrix only | Service, permissions, device evidence, Doze/OEM/recovery |
| G4 — Storage/security | APPROVED DIRECTION / AAD IMPLEMENTED / DETAILS OPEN | AAD-v1 serialization, mandatory AAD enforcement, key alias policy implemented and tested | Keystore runtime, key rotation, migration/recovery, retention, deletion, export, device verification, threat model, sign-off |
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

**Completed:** approved Guard decisions, evidence principle, device-first direction, storage direction, external-service deferral, sequential execution rule, capability/evidence/storage matrices. AAD-v1 canonical serialization and mandatory enforcement implemented (decisions §1.1–§1.4, §2.1–§2.3). **4d-aad-binding CLOSED.**

**Remaining:** Keystore runtime verification on device, key rotation algorithm (§3), Room migration/recovery (§4), evidence lifecycle (§5), retention policy (§5.4), export disabled enforcement (§5.5), security failure semantics enforcement (§6). Final production gate (§15) remains entirely open.

**Dependencies:** Batch 0 documentation complete; Batch 1 must remain framework-free and deterministic.

**Approval requirements:** implement only the locally authorized G4-B storage policy; do not claim Android runtime behavior, production readiness, or security certification.

### Phase 5 — Guard Implementation

**Status:** BLOCKED after pure engine contracts until Android feasibility and capability-specific approval exist.

### Phase 6 — Evidence & Privacy

**Status:** BLOCKED. The minimum-evidence principle is approved, but camera/location policy, permission, retention, deletion, export, redaction, encryption, and backup are unresolved.

### Phase 7 — Continuous Monitoring

**Status:** BLOCKED. The principle is maximum technically feasible legitimate monitoring without guarantees; service type, lifecycle, restart, Doze, OEM, Force Stop, notification, and device matrix are unresolved.

### Phase 8 — Local Alerts/Response

**Status:** BLOCKED. Panic severity is approved; local response, notifications, lock-screen disclosure, deduplication, and failure semantics remain open.

### Phase 9 — External Services

**Status:** DEFERRED. Device-first scope excludes cloud, email, SMS, messaging providers, billing, ads, analytics, and backend services until a new approval changes G5.

### Phase 10 — Security/Privacy Audit

**Status:** REQUIRED before production/security claims.

### Phase 11 — Release Hardening

**Status:** NOT STARTED.

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
| 4d+-expiration-lock-ui | Domain/Room expiration consistency, fixed Lock Guard presentation, removal of unapproved demo policy claims, focused tests | Existing 4d storage boundary and approved Lock decisions | COMPLETE LOCALLY — implementation `ae52dd9`; G4 remains open |
| 4d-7-correction | Fix CI-discovered override compilation error and enforce `noBackupFilesDir` evidence boundary | 4d-7 audit; no runtime approval | IMPLEMENTED — `b9d56fa`; CI `35886198586` success; G4 remains open |
| 4d-7-ci-coverage | Compile instrumented AndroidTest sources in the reproducible CI workflow | 4d-7 audit evidence | IMPLEMENTED — `2760107`; CI `35886895480` success; device execution remains blocked |
| 4d-domain-hardening | Enforce approved Motion/Lock semantics and fail-closed EventPipeline stage persistence | Pure domain authorization; no Android runtime | IMPLEMENTED — `191b049`; CI `35888768082` queued; local SDK unavailable; Android/runtime work deferred |
| 4d-ci-hardening | Enforce G4-B review script in CI and remove weak/no-op assertions | Existing G4-B local review scope | IMPLEMENTED — `abed3c4`; CI `35888896037` queued; device/runtime work deferred |
| 4d-storage-hardening | Reject symlink evidence payloads and fail closed on Room child-row collisions | Existing local storage implementation; no new security policy | IMPLEMENTED — `1335a8b`; CI `35889049875` in progress; runtime/device work deferred |
| 4d-acceptance-matrix | Separate implementation evidence from CI/device/security acceptance and record deferred Android work | All preceding local batches | DOCUMENTED — `bf4ccca` lineage; runtime/security acceptance remains blocked |
| 4d-aad-binding | AAD-v1 canonical serialization, mandatory AAD cipher, key alias policy, StrongBox detection, 10 unit tests, canonical vector tests, device instrumentation test (decisions §1.1–§1.4, §2.1–§2.3) | Final Security Storage Decisions (owner-supplied 2026-09-24) | **CLOSED** — commit `6a13d59`; all implementation + tests + static review complete; CI triggered |
| 4d-key-rotation | Key rotation algorithm, resumable rotation, rotation tests (decisions §3.1–§3.5) | 4d-aad-binding; CI pass | IMPLEMENTED AND DEVICE-EXECUTED — CI GREEN (run `36433212886`: 112 JVM tests, lint, both APKs, all review scripts) and `OK (2 tests)` on the RMX3910 |
| 4d-keystore-rotation-runtime | Instrumented rotation against two distinct real Keystore keys, proving re-encryption rather than metadata relabel (decision §3.1–§3.4) | 4d-key-rotation | IMPLEMENTED — CI GREEN (runs `36432257961` failed compile → fixed `d725cd8`) and `OK (2 tests)` on the RMX3910; enforced by a `g4b` gate |
| 4d-strongbox-correction | StrongBox detection queries `FEATURE_STRONGBOX_KEYSTORE` at runtime instead of inferring from SDK level; placeholder device assertion replaced (decision §2.3) | 4d-aad-binding audit | IMPLEMENTED — CI GREEN + **executed on RMX3910** (`RealAesGcmAadTest` passed in the `OK (22 tests)` run of 2026-09-28); device fact confirmed: this phone reports no StrongBox and the code agrees |
| 4d-migration-recovery | Room migration enforcement, unsupported version/downgrade/failure RecoveryRequired, tests (decisions §4.1–§4.6) | 4d-key-rotation or parallel if no conflict | IMPLEMENTED — CI GREEN (run `36428989303`: 112 JVM tests) **and device `OK (22 tests)` on RMX3910**; the device run corrected the migration-failure markers to measured Room/SQLite wording (see `docs/reports/device-evidence-2026-09-28.md`) |
| 4d-evidence-lifecycle | UNAVAILABLE/QUARANTINE/tombstone states, retention policy, export disabled, deletion semantics, factory reset (decisions §5.1–§5.7) | 4d-migration-recovery | BLOCKED |
| 4d-failure-semantics | Security failure semantics table enforcement (decision §6) | 4d-evidence-lifecycle | BLOCKED |
| 4d+ | One platform capability per batch: one Guard, evidence, auth, or monitoring | Capability-specific approval and device evidence | BLOCKED |
| 5 | Local response and notifications | G2/G3 policy | BLOCKED |
| 6 | External providers | G5 change from deferred | DEFERRED |
| 7 | End-to-end security/release review | All prior batches | BLOCKED |

## Required next batch

**`4d-evidence-lifecycle` (decisions §5.1–§5.7) — BLOCKED on an owner
decision.** The §3 runtime half is now closed: `EvidenceKeyRotationInstrumentedTest`
(2 tests) runs on the owner RMX3910 and passed (`OK (2 tests)`, run
`36433212886`), alongside the full instrumented suite `OK (24 tests)`. The last
unproven claim of decision §3 — re-encryption with **real** Android Keystore
keys (`shieldra_evidence_v1` → `shieldra_evidence_v2`) — is measured and
recorded in `docs/reports/device-evidence-2026-09-28.md`.

Nothing on §3 or §4 remains open. The next unblocked work is §5, but it cannot
start without the owner's answer on one point: **the retention policy** — how
long tombstoned and quarantined evidence is kept, and what "factory reset"
deletes. §5.4–§5.7 depend on it. Until that is answered this batch stays BLOCKED
rather than being implemented on an invented policy (decision §14 forbids
silent policy changes).

When it does start, device runs need the app and androidTest APKs from the
**same** CI run, because each runner regenerates the debug keystore.

**Correction recorded against 4d-aad-binding:** that batch closed without a
§16 batch report and its StrongBox instrumented assertion was a placeholder
(`assertTrue(true)`), which masked an SDK-level-only capability check. Both
were fixed in this batch and documented in
`docs/reports/batch-4d-key-rotation.md`.
