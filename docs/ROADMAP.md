# Shieldra Master Roadmap

**Repository:** `toufikben/shieldra`
**Branch:** `main`
**Current HEAD:** `origin/main` (the exact commit is the repository's current remote tip; this self-referential document intentionally avoids recording its own changing hash)

**Remote CI evidence (reconciled 2026-09-29):** the latest verified green run is
`36559760906` (`fcfcd96`): `BUILD SUCCESSFUL`, `CI TEST SUMMARY: 171 tests, 171 passed,
0 failures, 0 errors, 0 skipped`, lint, `assembleDebug` + `assembleDebugAndroidTest`, both
APKs uploaded as `apks-<run-id>`, and every static review gate reporting `PASS`. CI never
executes instrumented tests; those are run on the owner's physical device (see the Device
verification rule below). Historical runs `35886198586`, `35886895480`, `36552909429`,
`36537846580`, `36521881590`, `36494249786`, `36433212886` are cited in individual batch
rows below.

**Device verification rule:** connected tests ARE executed, on the owner's physical
RMX3910 (Android 16 / API 36) using the app and androidTest APKs from the **same** CI run
id — each GitHub runner regenerates its debug keystore, so cross-run APKs cannot both
install. The full instrumented suite currently stands at **`OK (34 tests)`**. The earlier
"No device or AVD available" statements in this file were stale and are corrected below.

**Project mode:** controlled sequential implementation. Phase 3 presentation shell is
closed for source/build/static scope and honest on device. The G4 local storage/security
slice is implemented and device-verified, but **the app has no detection at all**: no code
path creates a `SecurityEvent`, and `StorageRuntimeApproval.approved` is `false`. Production
readiness is not claimed anywhere in this roadmap.

**Decision authority for this cycle:**
1. [`../SHIELDRA_DECISIONS.md`](../SHIELDRA_DECISIONS.md)
2. [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](authority/FINAL_SECURITY_STORAGE_DECISIONS.md) (binding, owner-supplied 2026-09-24, amendments A-1…A-4 in §17)
3. **`docs/OWNER_QUESTIONS.md`** — the question register; owner answers recorded there are binding for the question they answer
4. **The owner Final Master Execution Protocol (received 2026-09-29)** — supersedes the
   registers on scope and sequencing. Its material rulings are recorded in
   [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](authority/FINAL_SECURITY_STORAGE_DECISIONS.md)
   and are what the batch table below now reflects. Where it conflicts with an older
   roadmap sentence, **the protocol wins**; older sentences are being corrected, not
   silently obeyed.

## Status model

Per the owner protocol, these seven levels are **not interchangeable**. Every batch row
below uses exactly these words.

| Level | Means | Never claimed from |
|---|---|---|
| `IMPLEMENTED` | the code exists | — |
| `TESTED` | a JVM or instrumented test exercises it | compile success |
| `CI VERIFIED` | a specific green run id proves build + tests + gates | local run, `gh run watch` exit code |
| `DEVICE VERIFIED` | executed on the owner RMX3910 with named test counts | CI |
| `SECURITY REVIEWED` | a threat-model/privacy review signed it off | device tests |
| `CLOSED` | all applicable levels above, plus a §16 batch report | any single level |
| `PRODUCTION APPROVED` | §15 satisfied end to end | everything short of it |

`Implemented ≠ Tested ≠ CI Verified ≠ Device Verified ≠ Security Approved ≠ Production Released.`

**Current tip (reconciled 2026-09-29):** the newest closed batch is
`4d-rotation-orchestration-and-retirement` (protocol **Batch B**) — the §9 rotation trigger
and the §10 old-key retirement state now exist, are CI green at `194 tests, 194 passed`,
and are device-verified on the owner RMX3910 (`OK (5 tests)`, full suite `OK (40 tests)`).
The batch before it, `4d-persisted-event-reader`, remains CLOSED (CI + DEVICE). Nothing
after Batch B has started; Batch C (Panic Guard) is next.
The previously-named "latest" batch (`4d-aad-binding`) and every storage batch before it
are history, not the frontier; the batch table below is the authority on ordering.

**What the app genuinely does today:** it can store and read encrypted evidence and
persisted events, and it can project them honestly — but **no code path creates a
`SecurityEvent`**. `ShieldraNavHost` wires nine user actions to a "deferred" snackbar
(`onPanic`, `onGuardClick`, `onDelete`, `onExport`, `onOpenInMaps`, `onBuy`, `onRestore`,
`onAllow`, settings rows), and `StorageRuntimeApproval.approved` is `false`, so a default
build never opens the database. The dashboard renders `DISABLED`. This is the honest state,
and it is what Batch C of the owner protocol exists to change.

**Durable toolchain constraints (learned the hard way; do not re-learn them):**
- Real Android Keystore rejects a caller-provided GCM IV — the cipher must let the Keystore
  generate it (`31d53e2`).
- D8 rejects spaces in androidTest method names (`snake_case` there), while `app/src/test`
  uses `kotlin.test`.
- `app/src/test` (JVM, kotlin.test) takes the message **last**; `app/src/androidTest`
  (JUnit4 `org.junit.Assert`) takes the message **first**. Swapping them compiles but
  asserts the wrong thing.
- CI runs `test` then `assembleDebugAndroidTest`; only the latter compiles instrumented
  sources. A green `test` says nothing about androidTest compilation.
- GitHub runners have no `rg`. A review gate written with `rg … && fail` silently
  evaluates false under `set -e`; gates must use `grep` (`0901c5c` fixed three such
  no-op gates).
- Room throws if queried on the main thread.
- A fresh install's Keystore alias does not exist until the first encrypt — warm a key up
  before asserting "wrong key rejects", or the test hits missing-key → recovery-required.

**Device evidence (owner RMX3910, Android 16/API 36):** instrumented execution is real and
repeated, not blocked. Current full suite **`OK (34 tests)`** covering
`RoomEventRepositoryInstrumentedTest`, `ShieldraDatabaseMigrationInstrumentedTest`,
`RealAesGcmAadTest` (AES-GCM + Keystore authentication incl. wrong-AAD rejection),
`EvidenceKeyRotationInstrumentedTest`, `EvidenceKeyResetInstrumentedTest`,
`StorageRecoveryInstrumentedTest`, `StorageWiringGateOffInstrumentedTest`, and
`PersistedEventReadingInstrumentedTest`. The device reports **no StrongBox**, which is why
no code or doc may claim hardware backing. OEM matrix, background execution, reboot, and
process-death behavior beyond these tests remain **NOT RUN**.

**Storage hardening:** commit `1335a8b` prevents symlink payload reads and filters symlinks
from reconciliation, and checks every evidence/delivery child insert inside the Room
transaction.

**Acceptance evidence:** [`docs/reports/g4-acceptance-matrix.md`](reports/g4-acceptance-matrix.md)
was reconciled in Batch A (2026-09-29). It had claimed Keystore/AAD/migration/instrumented
execution were `BLOCKED` "for lack of a device"; those rows now carry the measured result, each
pointing at the named instrumented test, and the file keeps the several rows that really are
still `NOT RUN` (backup/restore, concurrency/crash, symlink, downgrade, StrongBox).

**Pure-domain correction:** commit `191b049` fixes four verified logic gaps found by adversarial review: unbounded Motion repetition counts, out-of-order Lock timestamps, unsynchronized pure-engine state, and unsafe Pipeline resume/expiry/failure persistence behavior. Android adapters and runtime behavior remain deliberately deferred.

**G4-B CI enforcement:** commit `abed3c4` makes the G4-B review script fail on independently missing cloud-backup/device-transfer exclusions and missing Room catalog entries, and runs it in GitHub Actions. Superseded on `rg`→`grep` enforcement by `0901c5c`.

## Operating rule

Every meaningful batch follows the owner protocol's full sequence: **READ → TRACE →
TEST DESIGN → IMPLEMENT → STATIC REVIEW → JVM TEST → LINT → `assembleDebug` →
`assembleDebugAndroidTest` → DEVICE TEST → RE-READ → ADVERSARIAL REVIEW → REGRESSION →
DIFF REVIEW → COMMIT → PUSH → VERIFY REMOTE → VERIFY CI → UPDATE ROADMAP → FINAL
REPORT.** Dependent security areas are sequential. Only independent documentation,
localization, UI, design-system, and test work may be parallelized when file ownership
cannot conflict. A green workflow in which a required security gate is skipped is **not**
a PASS.

## Current state summary

| Area | Status | Evidence |
|---|---|---|
| Repository and CI foundation | DONE | Group A diagnostics and GitHub workflow |
| Group B project structure | DONE | Android app module and boundaries |
| Group C pure contracts | DONE / READY FOR CLOSURE | `app/src/main/kotlin/com/shieldra/domain` |
| Phase 3 presentation shell | DONE for source/build/static scope | Batches 0–9 and remediation reports |
| Batch 1 foundation fixes | DONE | Commit `bf5e77c` |
| Batch 2 screen states and PreviewData isolation | DONE | Commit `ef66c94` |
| Runtime device visual/RTL/accessibility verification | **NOT RUN** | Instrumented tests DO execute on the owner RMX3910 (`OK (34 tests)`), but no visual/RTL/accessibility pass has been performed on device. The old "no device available" claim was false. |
| Phase 4 authority register | UPDATED | `docs/PHASE4_DECISION_REGISTER.md` |
| Final Security Storage Decisions | FILED AS SOURCE OF TRUTH | `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` |
| AAD-v1 binding implementation | CLOSED | `EvidenceAadBuilder`, `KeystoreEvidenceCipherWithAad`, `EvidenceKeyPolicy`, `AndroidKeystoreEncryptor` (StrongBox), 10 unit tests, canonical vector tests, `RealAesGcmAadTest`; commit `6a13d59` |
| Phase 4 production behavior | CONTROLLED / G4-B IN PROGRESS | Approved initial local storage policy; Room/file adapter and persistence tests are authorized; Android runtime verification and production security approval remain disabled |

## Gate status

| Gate | Status | What is authorized now | What remains |
|---|---|---|---|
| G0 — Authority | APPROVED FOR INTERNAL DEVELOPMENT | Continue without historical Product Freeze dependency; internal register is authoritative for this cycle | Later product/release authority may supersede it |
| G1 — Protection rules | APPROVED FOR PURE CONTRACT CLOSURE | Encode approved Guard semantics in framework-free contracts/tests | Android feasibility, response, evidence, and API limitations |
| G2 — Evidence/privacy | **ANSWERED / UNBLOCKED for the Panic batch** | Minimum post-confirmation evidence only. Owner protocol §21–§23: **no camera in the Panic batch**, no audio ever approved, no continuous location, no location in the Panic batch unless actually required; permissions requested **incrementally and only for the feature that needs them** | Camera policy, location precision/freshness, redaction, and the consent/privacy text remain for Batch H |
| G3 — Android monitoring | **MOTION DETECTOR LANDED AND DEVICE-VERIFIED (G5 CLOSED)** | §16 real notifications after a real event exists (Batch E); §17 a foreground service for an active guard — **now real**: `MotionMonitoringService` runs as `specialUse`, user-started and user-stopped, with one ongoing notification reading exactly "SHIELDRA protection is active"; **WorkManager is prohibited as a real-time detector**; no 24/7 guarantee may be claimed; Batch F's `BackgroundExecutionController` contract and `MonitoringClaim` still hold, so no screen may claim monitoring the service does not really have | The remaining guards (Lock, SIM) are still capability-gated and read "Not monitoring yet / Disabled" honestly. Doze/OEM/Force Stop/reboot/process-death behaviour stays **measured evidence, not a claimed result**: the RMX3910 campaign shows a dark screen starves the process (0–22 CPU ticks, an undrained 4500-entry queue), which is exactly why no OEM-survivability or 24/7 claim is made anywhere |
| G4 — Storage/security | **SUBSTANTIALLY DEVICE-VERIFIED / NOT PRODUCTION-APPROVED** | AAD-v1, mandatory AAD, alias policy, StrongBox runtime detection, AES-GCM on real Keystore, key rotation algorithm, **rotation trigger and old-key retirement (§9/§10 — Batch B, CI `36612265938` + device `OK (40 tests)`)**, migration/recovery, evidence lifecycle, failure semantics, storage wiring gate, persisted read path — all IMPLEMENTED, CI VERIFIED, and DEVICE VERIFIED | Retirement **destroys nothing**: Q-C8 is still an open owner decision, the §15 install gate is open (amendment **A-5**, Batch D) so rotation is reachable at runtime, and the approved successor chain stops at v2. What remains for G4: independent security review, threat model, OEM matrix beyond one device, and sign-off |
| G5 — External services | **DEFERRED — REAFFIRMED 2026-09-29** | Keep provider interfaces only where already justified | §30 of the owner protocol explicitly prohibits adding **Firebase, cloud backend, accounts, analytics, ads, WhatsApp, Telegram, SMS, email** during these core batches. This overrides the earlier note that AdMob/IAP were "authorized" — see the correction in `docs/OWNER_QUESTIONS.md` |
| G6 — Security review | APPROVED PROCESS / FINAL AUDIT PENDING | Continuous AI/owner review and evidence collection | Dedicated security/privacy/release audit (protocol Batch K) |
| G7 — Implementation | **READY FOR PLATFORM BATCHES** | The pure-engine and storage foundations are closed; Panic Guard end-to-end is authorized as the next product batch (protocol §12–§15) | Each subsequent guard remains capability-gated in protocol order |

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

**Remaining:** the §5 storage slice and §6 failure semantics are **CLOSED (CI + DEVICE)** —
see their batch rows, as is the §9/§10 rotation trigger and retirement lifecycle (Batch B,
CLOSED). What actually remains in G4 is narrower still: the threat-model/privacy sign-off,
an OEM device matrix beyond the single RMX3910, and the independent security review. Overall
G4 is **not** production-approved.

**Dependencies:** Batch 0 documentation complete; Batch 1 must remain framework-free and deterministic.

**Approval requirements:** implement only the locally authorized G4-B storage policy; do not claim Android runtime behavior, production readiness, or security certification.

### Phase 5 — Guard Implementation

**Status:** **CLOSED (Batch C) for Panic; UNBLOCKED IN SEQUENCE for the rest.** The pure engine already maps
`GuardKind.PANIC` → immediate `confirmed(..., GuardSeverity.CRITICAL, ...)` in
`ProtectionStateEngine.kt:32`, and `GuardObservation.PanicActivated` exists. Protocol Batch C connects a UI
action to that engine and persists the result via `PanicIncidentCoordinator`: one explicit activation mints
exactly one confirmed event, binds every repeat press to it while non-terminal, and records nothing that did
not really happen. Motion is Batch G, SIM Batch I (feasibility first), Lock Batch J (feasibility **and** the
current official Play policy, cited, first). Battery stays prohibited (§20, G1-04).

### Phase 6 — Evidence & Privacy

**Status:** **PARTIALLY RESOLVED / Batch H.** Retention (§5.4/A-1 30 days), deletion
(§5.6), export (§5.5 disabled), factory reset (§5.7/A-2/A-3), and encryption (§1–§3) are
settled and device-verified. Protocol §21–§23 now also settle the shape: **no camera and
no location in the Panic batch**, no audio ever approved, and permissions requested only
incrementally for the feature that needs them. Unresolved and deferred to Batch H: camera
trigger and duration, location precision/freshness, redaction, and the consent/privacy copy.

### Phase 7 — Continuous Monitoring

**Status:** **CONTRACT LANDED (Batch F) / EXECUTION SURFACE DELIBERATELY ABSENT.** §17 authorizes
a foreground service for an active guard and **prohibits WorkManager as a real-time detector**.
Batch F shipped only the platform-neutral execution contract
(`com.shieldra.runtime.BackgroundExecutionController`: idempotent start/stop, honest
prerequisite refusal, failure keeps the layer `Stopped`, a recreated process reports `Stopped`,
and `MonitoringClaim` whose only protection-claiming value requires a running detector). **No
service, no foreground-service type, no second permission and no WorkManager detector were
created**, because the detector that would need them does not exist yet — the type "must be
selected from the actual detector's Android requirements rather than preselected now". The
Android limitations (Doze, OEM battery management, Force Stop, process death, reboot, user
restrictions) are recorded as requirements in
`docs/capabilities/background-execution-limits.md`, **not** as verified results, and **no 24/7
guarantee may be claimed** — the app must never present a guarantee it cannot prove.

### Phase 8 — Local Alerts/Response

**Status:** **UNBLOCKED / Batch E, after Batch C is device-verified.** §16 requires real
notifications with `POST_NOTIFICATIONS` requested only when the feature genuinely needs it,
and granted/denied/disabled-in-Settings handled honestly: **the app must never display
"alert sent" when notification permission is unavailable** (§31).

### Phase 9 — External Services

**Status:** **DEFERRED — REAFFIRMED BY THE OWNER 2026-09-29 (§30).** Device-first scope
excludes cloud, email, SMS, messaging providers, billing, **ads**, analytics, Firebase,
accounts, and backend services during these core batches. This explicitly supersedes the
earlier note that AdMob and IAP had been authorized for the launch; see the correction in
`docs/OWNER_QUESTIONS.md` Q-D5/Q-D7.

### Phase 10 — Security/Privacy Audit

**Status:** REQUIRED before production/security claims. Protocol Batch K.

### Phase 11 — Release Hardening

**Status:** NOT STARTED. Protocol Batch L. Requires a real release keystore — never a CI
debug keystore — before any distribution.

## Dependency-aware execution plan

| Batch | Scope | Dependencies | Status |
|---|---|---|---|
| 0 | Reconciled decisions, gate register, Guard rules, Android/evidence/storage matrices, roadmap | Unified prompts | DONE — `d50e626` |
| 1 | Pure Guard contract closure: typed observations, approved rule values, confirmation results, deterministic tests | Batch 0; no Android APIs or persistence engine | DONE — `6dc8396` |
| 2 | Pure `ProtectionStateEngine`: Lock/Motion/SIM/Panic approved semantics; Battery remains non-event until anomaly approval | Batch 1; pure deterministic implementation only | DONE — `0e02f55` |
| 3 | Local/in-memory `EventPipeline`: canonical EventId idempotency, EXPIRED lifecycle, coordinator/repository split, persisted-stage resume, per-event expiry, sequential per-event processing | Batch 2; approved Batch 3 decisions | DONE — `f6f637b` |
| 4a | Android capability research and device matrix | G3 review; no production behavior | RESEARCH COMPLETE — **and the matrix is now executed on the owner RMX3910** for storage/crypto; OEM breadth still NOT RUN |
| 4b | Execute device/API matrix and collect runtime evidence | Batch 4a; ADB/AVD or physical devices | **PARTIALLY DEVICE VERIFIED — the old "BLOCKED, no device" status was false.** `adb` is available and `connectedDebugAndroidTest` runs on the RMX3910 (`OK (34 tests)`). Sensors, notifications, background execution, reboot and process death remain NOT RUN because the features do not exist yet |
| 4c | Versioned schema and Keystore candidate; no production wiring | G4 review; no silent crypto or retention decisions | CANDIDATE COMPLETE — BUILD/TEST PASS; G4 STILL OPEN |
| 4d-1 | Room foundation: version catalog, entities, DAOs, exported schema, database shell | Owner G4-B authorization; Batch 4c schema candidate | COMPLETE — BUILD/TEST/LINT/STATIC PASS; NOT WIRED TO APP |
| 4d-2 | Local Room repository boundary, encrypted evidence-file adapter, atomic file handling, deterministic file recovery tests | 4d-1; approved initial local policy | COMPLETE — BUILD/TEST/LINT/STATIC PASS; ANDROID KEYSTORE UNVERIFIED |
| 4d-3 | Room instrumented integration tests: reopen persistence, duplicate EventId, atomic expected-state updates, persisted delivery resume, foreign-key enforcement | 4d-2; Android test database | COMPLETE — `assembleDebugAndroidTest` PASS; DEVICE EXECUTION PENDING |
| 4d-4 | Cleanup/orphan reconciliation, quarantine foundations, migration-failure guards, and Room runtime execution | 4d-3; owner device/AVD | LOCAL PORTION COMPLETE — DEVICE EXECUTION PENDING |
| 4d-5 | Explicit local storage factory, no-backup evidence directory, non-destructive Room configuration, lifecycle reconciliation entry point | 4d-4; approved local policy | LOCAL COMPLETE — BUILD/TEST PASS; RUNTIME VERIFICATION PENDING |
| 4d-6 | Owner-device execution of Room/Keystore tests and runtime evidence package | 4d-5; owner device/AVD | **DEVICE VERIFIED** — executed repeatedly on the RMX3910; current full suite `OK (34 tests)`. The "BLOCKED HERE; adb/AVD REQUIRED" status was stale |
| 4d-7 | Offline G4-B local closure audit: backup boundaries, schema evidence, wiring scope, no-fallback/no-destructive checks, reproducible review script | 4d-6; local implementation complete | **CLOSED (CI + DEVICE)** by `4d-7-correction`, `4d-7-ci-coverage`, `4d-ci-hardening`, `0901c5c` and the later §5/§6 batches. G4 **overall** remains open — see "G4 gaps found by trace", closed by Batch B |
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
| 4d-evidence-lifecycle | UNAVAILABLE/QUARANTINE/tombstone states, retention policy, export disabled, deletion semantics, factory reset (decisions §5.1–§5.7) | 4d-migration-recovery | CLOSED (CI + DEVICE) — §5.4/§5.5/§5.7 storage slice under amendments A-1/A-2/A-3. CI GREEN (run `36494249786`: 134 JVM tests) and `EvidenceKeyResetInstrumentedTest` **`OK (2 tests)` on RMX3910** (run `36495834536`), full suite `OK (26 tests)` — proves the on-device Keystore evidence key is irreversibly deleted and prior evidence becomes permanently unreadable. §5.1/§5.2/§5.6 states and §5.3 event deletion already covered by `EncryptedEvidenceFileStore`/Room repo. See `docs/reports/batch-4d-evidence-lifecycle.md` |
| 4d-failure-semantics | Security failure semantics table enforcement (decision §6) | 4d-evidence-lifecycle | CLOSED (CI + DEVICE) — §6 classifier (`SecurityFailureSemantics`) + §6/A-4 read-path enforcement. A missing Keystore key on read surfaces as `EvidenceKeyRecoveryRequiredException` (never laundered into `EvidenceUnavailableException`); corruption/AAD-mismatch stays UNAVAILABLE; a confirmed factory reset keeps post-reset reads UNAVAILABLE because it removes the files too. CI GREEN (runs `36519322364` → `36521881590`: 151 JVM tests, lint, both APKs, all review scripts incl. the new §6/A-4 gate) and full instrumented suite **`OK (26 tests)`** on the owner RMX3910. Recorded as amendment A-4 in decisions §17. See `docs/reports/batch-4d-failure-semantics.md` |
| 4d-storage-wiring-gate | Install local storage behind the §15 runtime approval gate (`StorageRuntimeApproval.approved`, off by default) via a pure `decideStorageInstall`; wire `ShieldraApp` through `openOrRecovery`; g4b wiring gate + fix `rg`→`grep` no-op enforcement checks | 4d-failure-semantics | CLOSED (CI + DEVICE) — **gated slice only; §15/G4 stay OPEN.** `ShieldraApp` attaches storage only when the owner flips the flag (§14); default build never opens storage. CI GREEN (`36525682066` RED-compile → `36525983600` → `36537846580`: 154 JVM tests, both APKs, grep-based §15/§4.3/§5.5 gates now genuinely PASS) and on RMX3910 `StorageWiringGateOffInstrumentedTest` `OK (3 tests)` + full suite `OK (29 tests)` (no regressions; default-off creates no `shieldra.db` and no Keystore key). See `docs/reports/batch-4d-storage-wiring-gate.md` |
| 4d+-lock-guard-adapter | Replace the fabricated runtime demo dashboard with an honest Lock Guard projection (`LockGuardDecision` → `lockGuardDashboard`); drop `DemoData` from the runtime protection path; g4b honest-projection gate | 4d-storage-wiring-gate; no detection policy needed | CLOSED (CI + DEVICE) — **projection slice only; §15/G4 stay OPEN.** Invariant: with no detection the app must never render "Protected" or an "Active" guard. CI GREEN (`bbdffd6` RED-compile → `36542577529` SUCCESS: `BUILD SUCCESSFUL`, both APKs, new gate `PASS: runtime guard projection is honest (no fabricated protection)`); on RMX3910 the same APK renders `DISABLED`, `Not monitoring yet. Status: Disabled.`, battery `—`, network `Offline`, and `StorageWiringGateOffInstrumentedTest` `OK (3 tests)`. Real persisted-event read, detection, services, and alerts are DEFERRED. See `docs/reports/batch-4d-lock-guard-adapter.md` |
| 4d-persisted-event-reader | Real read path: `listRecent` on the repository contract, newest-first Room query, pure `PersistedEventReader` projection, off-main-thread read in the NavHost; g4b read gate | 4d+-lock-guard-adapter; no detection policy needed | CLOSED (CI + DEVICE) — **read path only; §15/G4 stay OPEN.** Invariants: the projection never invents a photo/location/battery/receipt, and a build without installed storage reads nothing. CI GREEN (`3bb1507` RED-compile → `36551174645`/`36551915935` compile fixes → `36552909429` SUCCESS: `171 tests, 171 passed`, both APKs, new gate `PASS: persisted events are read off the main thread and only when storage is installed`); on RMX3910 `PersistedEventReadingInstrumentedTest` `OK (5 tests)` and the full suite `OK (34 tests)` (no regressions). Producing events at runtime was DEFERRED at that time and is now authorized as protocol Batch C (Panic Guard). See `docs/reports/batch-4d-persisted-event-reader.md` |
| 4d-rotation-orchestration-and-retirement | **Protocol Batch B** — production rotation trigger (§9) and the old-key retirement state machine (§10): `EvidenceRotationOrchestrator`, durable `FileBackedKeyLifecycle`, whole-set eligibility scan, `rotateEvidenceIfNeeded` composition, interval check in `ShieldraApp`; g4b §9/§10 gates incl. a line-order gate | 4d-persisted-event-reader; decisions §3.1–§3.4; Q-C8 stays unanswered | CLOSED (CI + DEVICE) — **trigger + retirement state only; §15/G4 stay OPEN, and no key is destroyed.** Ordering invariant, enforced mechanically: identify every eligible entry → migrate all → re-scan for surviving references → mark RETIRED; destruction is never implied. CI chain `f88bdb6` RED → `f318752` GREEN (harness-clock compile failure `36601603916`) → `2aefc06` `36603299441` SUCCESS (183) → `b71877e` RED → `663f414` (androidTest compile failure `36605868791`) → `22603d2` **`36612265938` SUCCESS: `194 tests, 194 passed, 0 failures, 0 errors, 0 skipped`**, lint, both APKs, Group C boundary scan, G4-B and Phase 3 review gates green. On RMX3910 (APKs from the same run): `EvidenceKeyLifecycleInstrumentedTest` **`OK (5 tests)`** — including a manual run through production wiring that re-encrypts real Keystore evidence v1→v2, verifies the bytes under v2, proves v1 now rejects them, and marks v1 RETIRED in the durable record; full instrumented suite **`OK (40 tests)`** (was 35, no regressions). Honest limits: the trigger is behind `StorageRuntimeApproval.approved = false` (Batch D), the manual path has no UI affordance, and the approved successor chain stops at v2. See `docs/reports/batch-4d-rotation-orchestration-and-retirement.md` |
| 4e-pipeline-honesty-and-panic-guard | **Protocol Batch C** — artifact-verified pipeline stages (no phantom success) and Panic Guard end-to-end: `PanicIncidentCoordinator` persists-first with lifecycle-bound incident identity, `EventRepository.recordEvidence/recordDeliveryAttempt`, honest state labels, metadata fail-closed; g4b pipeline honesty + panic gates | 4d-rotation-orchestration-and-retirement; Q-C9 raised | CLOSED (CI + DEVICE) — **pipeline honesty + Panic persistence only; §15/G4 stay OPEN.** Invariant: a stage call alone cannot advance an event — READY requires a persisted `EvidenceReference`, DELIVERED requires a persisted SUCCESS attempt, and unpersistable metadata is rejected rather than dropped. Panic mints one real EventId, binds repeats to it while non-terminal, and records nothing that did not happen. CI chain `c3c3a43` RED (unresolved symbols) → `d39945f` GREEN (typo compile failure) → `8cce2d7` repair **`36655321989` SUCCESS: `212 tests, 212 passed, 0 failures, 0 errors, 0 skipped`**, lint, both APKs, Group C boundary scan, G4-B and Phase 3 review gates green. On RMX3910 (APKs from the same run): `RoomPanicPersistenceInstrumentedTest` **`OK (3 tests)`** and full suite **`OK (46 tests)`** (was 40, no regressions). Honest limits: activation count is session-only (Q-C9), the storage install gate remains off (Batch D flips it), and the device screen was locked so the UI press was not photographed. See `docs/reports/batch-C-pipeline-honesty-and-panic-guard.md` |
| 4d+ | One platform capability per batch: one Guard, evidence, auth, or monitoring | Capability-specific approval and device evidence | **UNBLOCKED IN ORDER** — the owner protocol §38 authorizes the sequence Panic (C) → notifications (E) → background (F) → Motion (G) → evidence/permissions (H) → SIM (I) → Lock (J). Not one batch of everything |
| 5 | Local response and notifications | G2/G3 policy | **UNBLOCKED — sequenced after Panic.** §16 real notifications; §17 foreground service acceptable; WorkManager prohibited as a real-time detector; no 24/7 guarantee may be claimed |
| 6 | External providers | G5 change from deferred | **DEFERRED — REAFFIRMED.** §30 explicitly bars Firebase, cloud, accounts, analytics, **ads**, WhatsApp, Telegram, SMS and email during the core batches |
| 7 | End-to-end security/release review | All prior batches | BLOCKED on batches B–I; protocol Batch K/L |

## G4 gaps found by trace (2026-09-29) — closed by Batch B

Two lifecycle requirements in the owner protocol were **genuinely unmet** at the time of
the trace. Both were found by tracing call sites, not assumed. **Batch B closed both on
2026-09-29** (`docs/reports/batch-4d-rotation-orchestration-and-retirement.md`); the rows
below keep the original evidence so the correction is auditable.

| Gap | Evidence from trace | Status |
|---|---|---|
| **No rotation trigger existed** | `EvidenceKeyPolicy.rotationDue()` (`EvidenceKeyPolicy.kt:52`) was defined and tested, but `grep -rn "rotationDue" app/src/main` returned **only its own definition** — no production caller, and nothing in `ShieldraApp` or the storage factory invoked rotation. | **CLOSED — CI VERIFIED + DEVICE VERIFIED.** `EvidenceRotationOrchestrator` (`EvidenceKeyLifecycle.kt`) decides the due state from the approved interval or an explicit manual request and drives `EvidenceKeyRotator` over the whole eligible set; `LocalShieldraStorage.rotateEvidenceIfNeeded()` is the production entry point and `ShieldraApp.onCreate` runs the interval check behind the §15 gate. CI `36612265938` (194/194 JVM) and device `OK (5 tests)` / full suite `OK (40 tests)`. **Honest limits:** the trigger is unreachable while `StorageRuntimeApproval.approved` is `false` (Batch D flips it), the manual path has no UI affordance yet, and no approved successor exists past v2, so a second rotation reports `NO_APPROVED_SUCCESSOR` instead of inventing one. |
| **No old-key retirement lifecycle** | `grep -rni "retir" app/src/main` returned **zero matches** — no `RETIRED` state, no "zero remaining references" check, and no path distinct from the §5.7 factory reset. | **CLOSED — CI VERIFIED + DEVICE VERIFIED.** `FileBackedKeyLifecycle` is the durable RETIRED state (active generation, retired set, rotation clock) in `noBackupFilesDir`, schema-checked and fail-closed when damaged; the orchestrator marks a generation retired **only** after a whole-set re-scan finds zero surviving references, enforced mechanically by a line-order gate in `g4b-review-check.sh`. **Destruction of a retired key remains an open owner decision (Q-C8) and is not implemented.** |

Everything else the protocol listed as possible G4 residue was checked and is already
closed: AAD-v1 (§8 — canonical format intact, mandatory on the evidence path, real AES-GCM
authentication failure proven on device), StrongBox runtime detection (§7 —
`FEATURE_STRONGBOX_KEYSTORE`, not SDK inference), the rotation algorithm's step order and
resumability, failure semantics (§27), factory reset (§28), and the retention values
(`Evidence`/`Quarantine` 30 days per A-1, event metadata 90 days).

## Required next batch

**Batch G — Motion Guard** (§18) is the current batch, and its first deliverable — *the detector's
real Android requirements* — is now **MEASURED, not assumed**:
`docs/capabilities/motion-detection-requirements.md` records the RMX3910 sensor inventory
(accelerometer `perm: n/a`, continuous, **non-wakeUp**, 10–200 Hz; `SIGNIFICANT_MOTION`
one-shot **wakeUp** `perm: n/a`) and the shape it forces: a wake-capable tripwire, then a short
accelerometer burst judged by the approved G1-02 rule.

*Naming:* "slice G1/G2/G3" below is Batch G's own build order. The `G1 — Protection rules`,
`G2 — Evidence/privacy`, `G3 — Android monitoring` rows in the §18 table earlier in this file are
the older decision-log numbering and mean something else entirely.

**The owner settled the four open decisions on 2026-09-30** (recorded as amendments **A-8 … A-11**
in `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md` §17 — read them before writing any Motion
code):

| | Ruling |
|---|---|
| **A-8** | A foreground service is approved **as an architectural decision, not as permission-only code**. No `<service>`, no `FOREGROUND_SERVICE`, no `FOREGROUND_SERVICE_SPECIAL_USE` until a real monitoring service the user starts and can stop exists. `specialUse` is chosen **only** when nothing more specific fits, with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property and a Play justification. An empty service written to defeat the CI gate is forbidden. |
| **A-9** | While an FGS really runs it must show a **continuous, accurate** notification: *"SHIELDRA protection is active"*. Never "24/7", never "guaranteed". |
| **A-10** | **Rejected for now**: no `RECEIVE_BOOT_COMPLETED`, no `BOOT_COMPLETED`. First prove *app opened → user enables → monitoring starts*, then test OEM/reboot behaviour. |
| **A-11** | Battery restriction is an **optional recommendation**, with the owner's exact wording; it is never presented as a requirement, and the app never claims the system would kill the service without it. |
| **A-12** | Slice **G3 is signal data only**: the adapter registers the continuous accelerometer (availability lookup for `SIGNIFICANT_MOTION`, never arming it from this layer), the sample rate is **computed from delivered timestamps** because "106 Hz is a test result, not a contract", no measured number may become a constant, and **Signal ≠ Confirmed Security Event**. Its closing sentence is fixed wording, not a claim of accuracy. |

Consequences for the batch order: the **pure** Motion judgement (samples → quality → G1-02
window) and the honest projection came first (G1), the device evidence second (G2), and the Android
sensor adapter plus its signal-quality layer third (G3, landed under A-12). What still has to be
measured before any monitoring claim is the **on-device background-delivery** behaviour; **the
foreground service and its permission come after that, and only if it shows monitoring must continue
outside the foreground.** Motion's magnitude thresholds are **Q-G1** (an owner decision) and stay
injected until measured.

**Slice `G-motion-judgement` is LANDED (CI verified + device no-regression verified, 2026-10-01).**
`MotionDetection.kt` turns raw samples into the exact `GuardObservation.Motion` the approved engine
already consumes: orientation-free magnitude, hysteresis between the strong level and the noise
floor, isolated bursts dropped as noise, and `Unreliable` / `Insufficient` instead of a "still
device" claim when the samples stop, lie, or never arrive. RED `36788390266` failed with 57
unresolved-reference errors confined to the new test file; GREEN `36789435527` is **259 JVM tests,
0 failures, 0 errors, 0 skipped** (Batch F's 248 + the 11 new Motion tests, read from that run's
own `unit-test-results` artifact). On RMX3910 with APKs from the same run: full instrumented suite
**`OK (55 tests)` twice on one install**. **What this is not:** no listener is registered, no
service, permission or screen changed, no magnitude threshold ships (Q-G1 open), and Motion still
renders `—` — nothing monitors anything yet. See
`docs/reports/batch-G1-motion-judgement.md`.

**Slice `G-motion-sensor-evidence` is LANDED (CI verified + measured on the owner's phone,
2026-10-01).** `MotionSensorEvidenceInstrumentedTest` (6 tests) registered listeners and armed a
trigger on RMX3910, and the answers were not the ones the documents predicted:

- **the tripwire is a *trigger*, not a listener** — `registerListener(SIGNIFICANT_MOTION, …)` is
  refused with the platform's own `E/SensorManager: Trigger Sensors should use the
  requestTriggerSensor.`, while `requestTriggerSensor` **is accepted** from this ordinary unsigned
  debug app. A-8's wake-up shape therefore has a real entry point; nothing before this slice knew
  that;
- **~100 Hz whether asked or not** — a 20 000 µs (50 Hz) request delivered 159 events per 1500 ms
  (≈106 Hz, `SensorService` actuating the HAL at 10 ms), so any sampling or battery reasoning must
  use the delivered rate;
- **delivery continued with no resumed activity** (159 events / 1500 ms) — recorded as the
  **upper bound** it is: the instrumentation holds the process alive and the screen stayed on, so
  this number does **not** answer A-8's foreground-service question;
- **the resting noise floor is a number now** — max |‖a‖ − g| of **0.524** and **0.505 m/s²** over
  two independent ~2 s rests (210 and 213 samples), which is the measured half of Q-G1;
- **millisecond timestamps lose real samples** — the platform delivered two samples inside one
  millisecond and the pure rule correctly rejected that whole burst as `Unreliable`, so the
  adapter must carry sub-millisecond time.

CI chain: `390569e` **RED** `36791176374` (three `Unresolved reference`s in
`compileDebugAndroidTestKotlin` — the harness was never compiled on this PC) → `7082987` **success**
`36791998789` (259/259 JVM) → device run of *that* build returned `Tests run: 6, Failures: 1`,
which is how the trigger refusal surfaced → `12bf081` **success** `36793076936` (259/259 JVM, lint
run to completion, g4b + Phase 3 `PASS`, both APKs). On the phone with that run's APKs: Motion class
**`OK (6 tests)`**, then the full instrumented suite **`OK (61 tests)` twice on one install**.
**What this is not:** no adapter, no `SensorManager` reference in `app/src/main`, no service, no
permission, no manifest or screen change; the strong-magnitude half of Q-G1 is still unmeasured
because no test may carry the phone. See `docs/reports/batch-G2-motion-sensor-evidence.md`.

**Slice `G-motion-sensor-adapter` is LANDED (CI verified + device verified, 2026-10-01), inside the
boundary amendment A-12 draws.** `MotionSensorAdapter` registers the continuous accelerometer (and
only it), stamps samples with the sensor's clock, and unregisters on command;
`MotionSignalNormalizer` describes that stream — magnitude, the **median interval measured from the
timestamps that arrived**, the rate derived from it, and `EMPTY` / `INVALID` / `IRREGULAR` / `USABLE`
with reasons that quote measured numbers. Nothing decides anything: no Security Event, no severity,
no notification, no Room write, no service, no permission, and `MotionSensorAdapter(` is unreachable
from the app because the gate fails if it is referenced anywhere else in `app/src/main`.

- **measured through the new path on RMX3910** (logged, never asserted — A-12 rule 2): a 20 000 µs
  request delivered a **9.0 ms median interval (111.1 Hz)**, 159 samples per 1.5 s window,
  `clockResolutionDrops=0`, `significant_motion_available=true`;
- **G2's "the adapter must carry sub-millisecond time" was answered a different way**: the adapter
  keeps millisecond stamps and *drops plus counts* a reading whose millisecond collides, so the
  stream that reaches the pure rule is always strictly increasing instead of being rejected whole.
  Nothing was dropped on this device, but the sample type still cannot express two readings inside
  one millisecond — if a shipping calibration ever needs every sample, `MotionSample` has to move to
  microseconds;
- **CI chain**: `d664783` **RED** `36812820209` (72 errors, confined to the new JVM test) →
  `2cb1275` `36813397127` ✗ (`abs`/`sqrt` left in `MotionDetection.kt`) → `0b28f6b` `36813935519` ✗
  (kotlin.test message order at `MotionSignalNormalizerTest.kt:76`) → `c22d9a4` `36814029302` ✗
  (**same** line-76 error: that commit fixed a reason string, not the argument order) → `742a117`
  **success** `36814463442` (**270/270 JVM**, lint to completion, Group C + G4-B + Phase 3 `PASS`,
  both APKs);
- **on the phone with that run's APKs**: `MotionSensorAdapterInstrumentedTest` **`OK (7 tests)`** ×2
  and the full instrumented suite **`OK (68 tests)`** ×2 on one install; the dashboard still shows
  **Motion Guard — "Not monitoring yet"**.

**Slice `G4-A` — the background-sensor measurement — is RUN AND CLOSED AS EVIDENCE, not as a
delivery rate (2026-10-01).** 20 device runs on RMX3910 across four campaigns, in the two buckets
amendment A-14 requires. **Valid evidence is only the lit-screen baseline**: five `CONTINUING` runs
(6335/6336/6334/6328 window samples at a **9.0 ms median → 111.1 Hz**, plus one later control run at
**19.0 ms → 52.6 Hz** with the same requested 20 000 µs — the spread is reported, no cause claimed,
neither number is a spec). **Every dark window is in the other bucket**: 10 genuinely dark runs (60 s,
300 s, 900 s) ended `TIMEOUT_NO_DATA` with a 0-byte vehicle evidence file, and the independent
witnesses agree on why — the measured process advanced **0–22 CPU ticks over spans of 229–1128 s**
(thread state never `R`) while SensorService kept the subscription enabled and logged
`Dropping 1 cached events (4500/4500) ... app:com.shieldra.app` up to 521 times per run, i.e. an
**undrained queue with a live registration and no running consumer**. The lit control shows the same
process accumulating ~2 ticks/s, so the vehicle is not broken. campaign-2's two `CONTAMINATED` B runs
were a display that self-wakes 2.0 s after `keyevent 223` and stayed lit under the 30-minute timeout;
A-14's mandated clean re-run (campaign-3, only `screen_off_timeout` lowered to 15 000 ms and restored
to 1 800 000 afterwards, both read back) produced **three non-contaminated dark runs that still never
returned** — a 60-second dark window already starves the process. A-1/A-2 (adb/USB transport loss) were
not re-run, as A-14 orders. **What this does not say**: it does not establish that the sensor stopped
producing (the 2.003 s eviction cadence is ambiguous and both readings are kept), it does not describe
a production service under Doze/OEM restrictions on battery (A-13), and **"an FGS is required" is not
a result of this campaign** — that is A-8's decision, now raised as **Q-G3** for the owner. See
`docs/reports/batch-G4A-background-sensor-measurement.md`.

**Q-G3 was ANSWERED on 2026-10-01 — Option 1 — and the answer is amendment A-15:** continuous Motion
monitoring gets a **real Foreground Service** that the user starts, can stop, and sees an accurate
ongoing notification for while it genuinely runs; no 24/7 claim, no boot start (A-10 intact), battery
guidance optional (A-11 intact), and `specialUse` chosen only if a repository/API review shows no more
specific FGS type fits. **A-15 is a decision record, not implementation permission.** The owner fixed the
order: **A-15 → pure G4 evaluator with no default threshold → one manual Q-G1 measurement → owner
approves the strong-motion calibration → only then the FGS code.** So `<service>`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_SPECIAL_USE`, monitoring-notification changes, runtime monitoring activation,
Security Event creation from Motion, severity, evidence, Panic, camera and location all remain forbidden
in this slice, and **the static gates are not relaxed to make room for future code** — a later relaxation
must itself be a numbered amendment.

**G4 — the layer above signal data** (`Signal → Validation → Confirmation → Severity → Confirmed
Security Event`, the flow A-12 names) is **the current slice, pure code only**. The missing piece is
narrow and real: `MotionGuardObservation.of` decides quality from the *evaluator's* reading alone and
reports `SensorQuality.VALID` for any episode list, so a stream the G3 normalizer measured as
`IRREGULAR` or `INVALID` could still reach the engine as valid sensor data and confirm. The slice closes
that seam — quality carried, never upgraded — with the owner's 3 s / two-in-10 s rule untouched and every
magnitude value still injected. Until then Motion may not be shown as `Active` anywhere, and
per A-14 Batch H arrives only as a **decisions package** (Q-C5's capture duration, photo count,
location precision/retention, whether camera is allowed at all) with no H code and no gate relaxation
before an explicit amendment.

**Slice `G4-seam` — the quality-aware Motion judgement — is CLOSED (CI verified, 2026-10-01).** The
gap described above is now closed: `MotionGuardObservation.of(reading, streamQuality, window)`
*requires* the `MotionSignalQuality` the G3 normalizer computed and refuses on it **before anything is
counted** — `EMPTY` → `SensorQuality.UNAVAILABLE`, `INVALID`/`IRREGULAR` → `NOISY`, each refusal
carrying `Duration.ZERO` and zero occurrences, so an untrusted stream cannot leak a duration or a
repetition window into the owner's rule on its way to being discarded. The two vocabularies are bridged
rather than collapsed (`MotionSignalQuality` = what the sampling did, `SensorQuality` = what the domain
may claim), the 3 s / two-in-10 s rule is untouched, and `MotionCalibration` still has **no defaults**.
14 new JVM tests (`MotionStreamQualityTest`) cover §8 — including "a trusted stream still confirms", so
the seam cannot pass by always refusing — plus the five §7-D boundary cases. CI chain `927f20d` **RED**
(`36880439938` failure, unresolved seam symbol) → `7e3eb0e` GREEN attempt (`36881558063` failure: three
`assertTrue` in JUnit order inside a kotlin.test file — a harness defect, kept in the record) →
`9adde73` **success `36882705433`: 284 tests, 284 passed**, all three static gates green. Gate section
18 now forbids any A-15 code token in `app/src/main` and pins the seam's no-default parameter. Report:
`docs/reports/batch-G4-seam-and-QG1-harness.md`.

**Slice `QG1-harness` — the one manual calibration measurement — is BUILT, DEVICE-VERIFIED, and only
half RUN (2026-10-01).** `MotionCalibrationInstrumentedTest` samples through the already-verified
`MotionSensorAdapter` and describes readings with `MotionSignalNormalizer`, so the numbers it logs are
the shipping path's own. It reports raw and derived data separately, sweeps `PROBE` levels that locate
*which* stretch was the carry without becoming product levels, and ends every window with
`OWNERSHIP q_g1=UNANSWERED shipped_strong_threshold=false`. CI `36885649601` **success** compiled it
into the test APK; on RMX3910 with that run's pair the full suite is **`OK (70 tests)` twice on one
install** (68 → 70 is exactly the two new methods) and the class alone **`OK (2 tests)`**. Getting
there cost one honest device failure: the first pass reported `Tests run: 70, Failures: 1`,
`ConcurrentModificationException`, because the collector copied a `synchronizedList` while the sensor
thread was appending to it — `synchronizedList` guards calls, not iterations. Fixed in `1e4bf6c` by
unregistering before the copy; the instrument was wrong, not the product.

Measured and on the record (**§7-A, supplementing G2 rather than replacing it**): a still RMX3910 over
four unattended windows sits at **median 0.3816–0.3964 m/s²**, **p99 0.4765–0.5145**, **max
0.5112–0.5516** gravity deviation at ≈111 Hz delivered for a 20 000 µs request, `clock_resolution_drops`
0–2, quality `USABLE`. One window also caught a **single isolated 4.5576 m/s² transient on an untouched
phone** while its median stayed at 0.4016 — and the harness still correctly answered
`deliberate_movement_identified=false`, because the run above the probe lasted 0 ms. That is the
owner's duration-over-magnitude rule confirmed by measurement: a threshold keyed to a *maximum* would
have fired on a phone lying still.

**NOT RUN, and the reason it cannot be: §7-B, the deliberate carry.** §5 requires the device owner to
perform the pickup and carry by hand and §11 forbids any automated test from moving the phone, so both
carry windows in this batch were unattended and both reported no movement. Consequently **§7-C is
deliberately withheld**: no strong-motion level is proposed, because a level must sit above what a real
carry produces and no carry has been measured — inventing one from rest data would turn measurement
into policy, which the directive's FINAL RULE forbids. The next owner action is one manual pass of the
printed sequence (still 8 s → pick up and carry naturally 20 s → put down and leave still 6 s) while the
harness runs; **the Foreground Service stays unimplemented until Q-G1 is answered.**

**Slice `F2-onboarding-copy-truthfulness` is CLOSED — CI and device verified (2026-10-01).** Commit
`1babdbd`, CI `36871617120` **success** (full command + all three contract scripts), and its own APK
pair re-walked on RMX3910 after a clean install with no permission pre-granted: the permission step now
reads "… asks for it **in Settings** …", the protection step prints its sentence once, and the
instrumented suite ran **`OK (68 tests)`** again on that install (19.365 s, 19.112 s).
It came
out of walking the first-run flow on RMX3910 with the APK pair of `adf68cc` instead of reading the code:
the instrumented suite passed **`OK (68 tests)` twice on one install** (the four G4-A vehicle tests are
excluded from a suite pass because the driver, not the suite, controls the screen), and the walk found
two claims the running app contradicted. The onboarding permission step reused the A-6 Settings sentence
*"SHIELDRA asks for it only here"* while its **Continue** is `defer()` and asks for nothing; the
protection step printed the same sentence twice, because `lock_guard_window` already contains
`attempt_recording_note`. Fixed as copy only — a new `onboarding_notification_reason` in both locales
pointing at Settings, the duplicate `Text` and the now-unused string removed — and pinned by
`g4b-review-check.sh` section 17, which was **RED first** (`FAIL: values has no
onboarding_notification_reason for the deferred onboarding step`). **What this slice did not do:** the
deferred button itself is still the owner's decision inside **Q-F1 item 3**, and Q-F1 items 1 (Premium
pitch copy) and 2 (empty `DELIVERY` card) are untouched, because §14 forbids resolving an open decision
by deleting the text it might still need. See
`docs/reports/batch-F2-onboarding-copy-truthfulness.md`.

Motion, SIM and Lock detection were explicitly out of Batch F's scope and remain unimplemented.

**Batch F — `F-background-execution-contract`** is **CLOSED** (2026-09-30). **No continuous Guard
detector was implemented in Batch F**, and none is claimed. It closed Batch E's three truthfulness
findings (the `Email · WhatsApp · Telegram` delivery row, the stale "no production data" dashboard
banner, the demo storage numbers) plus one more found while re-reading the device dump: the
dashboard printed `Last check · 0s ago`, evidence of a check nothing ever performed. The background
execution contract landed with the five layers separated and enforced by gates. CI chain `0e767b2`
RED (`Unresolved reference 'SettingsContent'/'SettingsRowTarget'/'MonitoringClaim'`) → `1502726`
**success, 247/247 JVM** → `cea844b` RED (`Unresolved reference 'lastCheckSeconds'`) → `d861922`
**success, 248/248 JVM, 0 failures, 0 errors, 0 skipped**, lint clean, both APKs, g4b 40 checks and
Phase 3 9 checks green. On RMX3910 (APKs from the same run `36782719559`): full instrumented suite
**`OK (55 tests)` twice on one install**, onboarding reads "Continuous monitoring is not part of
this build yet", the dashboard reads **"Last check · No check yet"** with **ATTENTION** and no
`Protected`, Settings has a **Notifications** row with the live permission state, no delivery row
anywhere, and a Storage row that states a real state instead of a count; a Panic press still posted
a real notification (`tag=a9d5b402-9a6b-47ef-a4a2-1cdeb99be65d`) and reached History. No FGS exists,
so no service device test exists or is claimed. See
`docs/reports/batch-F-background-execution-contract.md`.

**Batch E — `E-local-notifications-and-durable-activation`** is **CLOSED** (2026-09-30): real
local notifications (amendment **A-6**) and the durable Panic activation count with Room v2→v3
(amendment **A-7**). CI chain `f16e635` RED → `a8d4bee`→`bdfb8ba`→`8084026`→`aa3517c`→`c3f367b`
repairs → `ffd6745` narrow RED (a never-requested permission must not read as a refusal) →
`4ec751b`→`53f0f43`, final run **`36771561585` success: 231 JVM tests, 0 failures, 0 errors, 0
skipped**, lint, both APKs, g4b/Group C/Phase 3 gates green. On RMX3910 (APKs from the same run):
`NotificationPermissionStateInstrumentedTest` **`OK (4 tests)`**,
`PanicActivationSchemaMigrationInstrumentedTest` **`OK (3 tests)`**,
`RoomPanicPersistenceInstrumentedTest` **`OK (3 tests)`**,
`StorageRuntimeInstallInstrumentedTest` **`OK (6 tests)`**, and the full suite **`OK (55 tests)`
twice on one install**. Observed on screen: fresh install reads **"Not requested"**; answering
"Don't allow" changes it to **"Not allowed"** and nothing else; with the permission granted a
Panic press posts a real notification record whose tag is the event id shown in History/Detail;
three further presses leave one notification record and one event, including across a cold
restart; turning the permission off in system settings makes the next press post **0**
notifications while the event stays recorded. G4 remains **unaccepted** and §15 remains open.
See `docs/reports/batch-E-local-notifications-and-durable-activation.md`.

**Batch D — `D-storage-runtime-approval`** is **CLOSED** (2026-09-30): the flip is recorded as amendment
**A-5** in `docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`, CI `36745562314` is green (213/213 JVM),
and on RMX3910 the running app opens storage, writes `shieldra.db` and the §9 rotation record
(`shieldra-key-lifecycle.bin`, `active=v1`), and a real Panic press reaches History with its own
event id while the dashboard stays honest (`ATTENTION`, every guard `—`). See
`docs/reports/batch-D-storage-runtime-approval.md`.

**Batch B — `4d-rotation-orchestration-and-retirement`** (protocol §9/§10) is **CLOSED**:
the production rotation trigger and the old-key retirement state machine landed, with the
retirement ordering enforced (migrate all → re-scan → zero remaining references → mark
RETIRED) and no key-destruction surface anywhere. See
`docs/reports/batch-4d-rotation-orchestration-and-retirement.md`.

Batch E is **closed** (2026-09-30, amendments A-6/A-7 — see the section above). Q-C9 was decided
with it and landed as durable per-event metadata, not a delivery receipt.

This is followed, in the owner-mandated order, by:

| Batch | Work | Protocol |
|---|---|---|
| A | Reconcile roadmap / questions / decisions against the actual repository | **CLOSED** — `ba1a224`, CI `36598839725` success, report `docs/reports/batch-A-roadmap-reconciliation.md` |
| B | Rotation trigger + old-key retirement (§9–§10) | **CLOSED** — `f88bdb6`→`22603d2`, CI `36612265938` success (194/194 JVM), device `OK (5 tests)` + full suite `OK (40 tests)` on RMX3910, report `docs/reports/batch-4d-rotation-orchestration-and-retirement.md` |
| C | **Panic Guard end-to-end** — UI action → signal → immediate confirmation → `EventId` → `SecurityEvent` → pipeline → Room → reload → History → Detail, idempotent on repeat | §12–§15 — **CLOSED** `c3c3a43`→`d39945f`→`8cce2d7`, CI `36655321989` success (212/212 JVM), device `OK (3)` + full suite `OK (46 tests)`, report `docs/reports/batch-C-pipeline-honesty-and-panic-guard.md` |
| D | Flip `StorageRuntimeApproval.approved = true` in the **same** batch, and only after C's device verification | §11, §14 — **CLOSED** `986cb00`→`36e04c1`→`28d708c`, CI `36745562314` success (213/213 JVM), device `OK (6 tests)` + full suite `OK (48 tests)` **twice on one install** on RMX3910, report `docs/reports/batch-D-storage-runtime-approval.md`. The flip is recorded as amendment **A-5**; g4b now enforces the flipped value so an unrecorded revert fails CI |
| E | Real local notifications (A-6) + durable Panic activation count with Room v2→v3 (A-7); `POST_NOTIFICATIONS` requested only from the notification setup screen; granted / refused / never-asked kept distinct; a refusal never blocks recording; no local post ever worded as delivered | §16, §23, §31 — **CLOSED** `f16e635`→`…`→`53f0f43`, CI `36771561585` success (231/231 JVM, 0 failures), device `OK (4)` + `OK (3 migration)` + `OK (3 persistence)` + `OK (6 install)`, full suite **`OK (55 tests)` twice on one install** on RMX3910, report `docs/reports/batch-E-local-notifications-and-durable-activation.md`. Exactly one permission is declared; g4b fails a second one, a remote provider in the notification package, any `DELIVERED`/`SENT` name in the local result vocabulary, and a notifier that cannot tell silence from refusal |
| F | Background-execution **contract** only — no detector, no service, no new permission; five layers separated; `MonitoringClaim` makes a protection claim unrepresentable without a running detector; Batch E's three truthful-copy findings closed | §17 — **CLOSED** `0e767b2`→`1502726`→`cea844b`→`d861922`, CI `36782719559` success (**248/248 JVM**, 0 failures), device full suite **`OK (55 tests)` twice on one install** on RMX3910 + on-screen dumps (Settings/dashboard/onboarding honest, Panic→notification→History intact), report `docs/reports/batch-F-background-execution-contract.md`. **No continuous Guard detector was implemented.** g4b now fails: a second permission, a `<service>` or WorkManager detector before a real guard exists, a delivery row or demo storage figure in Settings, a `Protected`/`Monitoring` claim from the execution layer alone, a missing named Batch F test, and any screen asserting a check that never ran. **F2 follow-up (2026-10-01):** Batch F's own on-device dump called onboarding honest and was wrong — a first-run walk on RMX3910 with the `adf68cc` APK pair found the permission step claiming the request happens "only here" while its Continue is `defer()`, and the protection step printing one sentence twice; both fixed as copy only, pinned by g4b section 17, suite re-verified **`OK (68 tests)` ×2** on that install. Report: `docs/reports/batch-F2-onboarding-copy-truthfulness.md`. **Re-walk on `1babdbd`'s own pair** (CI `36871617120`) confirmed both fixes on screen, re-ran **`OK (68 tests)` ×2**, and surfaced a fifth dashboard row — `Battery / Not monitoring yet`, honest but unscheduled — raised as **Q-F1 item 4** rather than deleted |
| G | Motion Guard — **slices G1 + G2 + G3 LANDED**: pure judgement (`MotionWindowEvaluator` + `MotionGuardObservation`, CI `36789435527` 259/259 JVM, suite `OK (55 tests)` ×2); on-device sensor evidence (`MotionSensorEvidenceInstrumentedTest`, CI `36793076936` **259/259 JVM**, Motion class **`OK (6 tests)`** and full suite **`OK (61 tests)` ×2** on RMX3910); and the **adapter + signal-quality layer inside amendment A-12** (`MotionSensorAdapter` + `MotionSignalNormalizer`, CI `36814463442` **270/270 JVM**, adapter class **`OK (7 tests)`** ×2 and full suite **`OK (68 tests)`** ×2 on one install). Measured through the shipped path: `registerListener` for the accelerometer returns `true`, 159 samples per 1500 ms, median interval **9.0 ms** → **111.1 Hz** computed from delivered timestamps (never asserted as a constant), `clockResolutionDrops=0`, `significant_motion_available=true` with the trigger **never armed**. G3 emits **signal data only** — no Security Event, severity, notification, Room write, service or permission; `MotionSensorAdapter(` is referenced nowhere else in `app/src/main` (g4b section 15). Thresholds stay injected (at that slice the rest half was measured and the strong half still needed one deliberate carry — that carry has since happened and is answered below), and the dashboard shows **Motion Guard — "Not monitoring yet"**. **G4-A (the screen-off measurement) is RUN AND CLOSED AS EVIDENCE** on RMX3910 — 20 runs in A-14's two buckets: valid evidence exists only for lit windows (9.0 ms median → 111.1 Hz ×4, plus one 19.0 ms → 52.6 Hz control on the same requested period), while all 10 genuinely dark windows ended `TIMEOUT_NO_DATA` with the platform keeping the registration and an undrained 4500-entry queue (`Dropping 1 cached events (4500/4500) … app:com.shieldra.app`, up to 521 lines/run) and the measured process advancing 0–22 CPU ticks over 229–1128 s; A-14's clean re-run of B (only `screen_off_timeout` changed, restored after) produced three non-contaminated dark runs that still never returned. **No delivery rate exists for the screen-off case on this device**, and "FGS required" is explicitly not a campaign result. Report: `docs/reports/batch-G4A-background-sensor-measurement.md`. **Next: the owner's A-8/FGS decision (Q-G3), then G4** — **both now done in part:** **Q-G3 was answered
as A-15**, the **G4 quality seam LANDED** (`9adde73`, CI `36882705433` **284/284 JVM**, gate §18 holding
A-15 as decision-only) and the **Q-G1 harness LANDED and device-verified** (`1e4bf6c`, CI `36885649601`,
RMX3910 **`OK (70 tests)` ×2**, class alone `OK (2 tests)`). Q-G1's **strong half is ANSWERED 2026-10-01: `4.6 m/s²`, recorded as amendment A-16**, after the one
manual carry the question required (pass3 on RMX3910, `OK (1 test)`, rest `max 0.5192` / carry
`p99 3.4878`, `max 5.1399`); **G1-02 was left untouched** by your ruling, and the harness's
`deliberate_movement_identified=false` was explicitly not treated as a reason to lower the 3 s.
What remained open at that point inside Q-G1: `noiseFloorDeltaMs2`, `minEpisodeMs`, `maxSampleGapMs` and
`requestedReportPeriodUs` — so no `MotionCalibration(...)` literal shipped and Motion stayed un-armable.
The **FGS API/type review** A-8/A-15(7) demanded is written and eliminates all fourteen documented
types to `specialUse` (`docs/reports/batch-G5-foreground-service-type-review.md`). **Q-G1 IS NOW CLOSED
IN FULL — amendment A-17:** the shipping calibration is `4.6 / 0.8 / 500 / 100 / 20_000` (the `250 ms`
proposal rejected for `100 ms`, ≈10× the worst measured gap), `specialUse` is **confirmed** with the
owner's `health` correction applied (it also opens via `HIGH_SAMPLING_RATE_SENSORS`; excluded on
documented *purpose*, not permission shape), and `minEpisodeMs` is ruled **episode definition**, distinct
from G1-02's **confirmation policy**, which is untouched.
**THE G5 DESIGN IS APPROVED — amendment A-18 (2026-10-01):** the three decisions it still needed are
answered (`stallToleranceFactor = 11.0`, derived from the measured path so it decides at ≈99 ms against
the approved 100 ms ceiling, which stays the governor if a device's median rises; monitoring **fails
closed** when `POST_NOTIFICATIONS` is denied on API 33+ because an invisible ongoing notification would
break A-15(3), while Android itself would keep the service running; and confirmed Motion reuses the
active non-terminal **MOTION** event's `EventId` and **never** a Lock/SIM/Battery/Panic one, with no
cooldown invented), and the design took three corrections (one adapter owner — the `Service`, with
`MotionRuntime` holding no second instance; the truth read from real service state + `adapter.isReporting`
and never from a Start press; and start-latency/CPU/battery/screen-off measured as a separate campaign,
not as a precondition for G5). **The calibration is now six numbers and complete.**
**G5 IS CLOSED (2026-10-02).** The Foreground Service is no longer unimplemented: the real
`MotionMonitoringService` (`specialUse`, user-started, user-stopped, one ongoing notification reading
exactly "SHIELDRA protection is active") landed as `17bc8a4` with amendment **A-19** moved in that same
commit (CI `36932332864` success), and the four truthfulness fixes the owner then ordered landed as
`93a885f`→`3fd8abc`→`2bc6143`→**`18cc745`** (CI **`36939853591` success — 332/332 JVM**, Group C boundary
scan, G4-B static and Phase 3 static all pass), with **no authority amendment needed or written** and the
whole do-not-touch list byte-for-byte unchanged. All ten RMX3910 points were verified, including the one
that matters most: a shaken Motion event appeared in the open History screen **inside the same live
process** (pid 26771, started 01:36:34) typed **Motion**, while the Panic row stayed **Panic**. Three
stale-wording findings (two "demo events" empty states and a detail row labelled "Type" that prints the
title) were recorded and deliberately **not** fixed, because they are outside the four authorized fixes.
**G5 claims no 24/7 reliability and no OEM survivability**, and the CPU/battery/screen-off campaign stays
evidence, not a gate. Report: `docs/reports/batch-G5-closure.md`. Next: **Batch H — the decisions package
only**, with no camera or location code and no invented owner policy. See
`docs/reports/batch-G4-seam-and-QG1-harness.md` and `docs/reports/batch-G5-foreground-service-type-review.md` | §18 — **CLOSED** (Motion monitors, persists, dedupes, notifies, and the UI now says only what is true) |
| H | Evidence / privacy / permission implementation | §21–§23 — **DECISIONS PACKAGE DELIVERED, IMPLEMENTATION BLOCKED ON THE OWNER** (`docs/reports/batch-H-decisions-package.md`): the encrypted evidence substrate is already shipped and device-verified (`EncryptedEvidenceFileStore`, `EvidenceReference` + its Room table, 30/90/30 retention, export disabled), so what is missing is policy, not plumbing. **12 decisions (H-1…H-12) are presented and none is answered** — starting with whether camera evidence is allowed at all and which guards may capture. **No camera or location code, no permission, no manifest line and no gate relaxation was written**, because A-14 bars inventing H decisions and bars relaxing `group-c-contract-check.sh:24-25`, `g4b-review-check.sh:330-334`/`420-422` and `phase3-review-check.sh:35/45-52` implicitly to admit code. Batch I and J do not depend on these answers |
| I | SIM Guard — feasibility first, then implementation | §19 |
| J | Lock Guard — feasibility + **current** official Play policy, cited exactly, before any code | §24–§25 |
| K | Full security/privacy/device audit | §33–§34 |
| L | Release hardening | §38 |

**Standing prohibitions carried forward from the protocol:** no battery-percentage security
event (§20); no continuous camera or location (§21–§22); no audio recording is approved;
no external providers, **including ads and analytics**, during these core batches (§30);
no UI may show `Protected`/`Active`/`Evidence captured`/`Alert sent` unless real state
proves it (§31); every dead button must work, be honestly disabled, or be removed (§32).

**Batch D was never a standalone step, and the ordering held.** `StorageRuntimeApproval.approved`
stayed `false` until the Panic producer was device-verified (Batch C, `OK (46 tests)` on
RMX3910), and the flip then landed as a visible commit with a recorded amendment (A-5). No
intermediate build ever opened storage and showed an empty app: g4b enforced `false` for
every commit up to `986cb00`, and enforces `true` only from `36e04c1` onward.

When device runs are needed, use the app and androidTest APKs from the **same** CI run,
because each runner regenerates the debug keystore.

**Correction recorded against 4d-aad-binding:** that batch closed without a
§16 batch report and its StrongBox instrumented assertion was a placeholder
(`assertTrue(true)`), which masked an SDK-level-only capability check. Both
were fixed in this batch and documented in
`docs/reports/batch-4d-key-rotation.md`.
