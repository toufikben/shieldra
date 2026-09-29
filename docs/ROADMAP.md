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
`4d-persisted-event-reader` — real persisted events now reach the UI through
`PersistedEventReader` on `Dispatchers.IO`, with `listRecent` on the repository contract
and a newest-first Room query. It is CLOSED (CI + DEVICE). Nothing after it has started.
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
| G3 — Android monitoring | **ANSWERED / UNBLOCKED in order** | §16 real notifications after a real event exists; §17 a foreground service is acceptable for an active guard; **WorkManager is prohibited as a real-time detector**; no 24/7 guarantee may be claimed; Doze/OEM/Force Stop/process death/reboot limitations must be documented | Background execution is Batch F, after the Panic loop is device-verified |
| G4 — Storage/security | **SUBSTANTIALLY DEVICE-VERIFIED / NOT PRODUCTION-APPROVED** | AAD-v1, mandatory AAD, alias policy, StrongBox runtime detection, AES-GCM on real Keystore, key rotation algorithm, migration/recovery, evidence lifecycle, failure semantics, storage wiring gate, persisted read path — all IMPLEMENTED, CI VERIFIED, and DEVICE VERIFIED | **Two genuine gaps found by trace (§9/§10): no rotation trigger and no key retirement.** Plus threat model, OEM matrix beyond one device, and sign-off. See "Real remaining G4 gaps" |
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
see their batch rows. What actually remains in G4 is narrower than it used to be and is
enumerated in "Real remaining G4 gaps": the absent rotation trigger, the absent old-key
retirement lifecycle, the threat-model/privacy sign-off, and an OEM device matrix beyond
the single RMX3910. Overall G4 is **not** production-approved.

**Dependencies:** Batch 0 documentation complete; Batch 1 must remain framework-free and deterministic.

**Approval requirements:** implement only the locally authorized G4-B storage policy; do not claim Android runtime behavior, production readiness, or security certification.

### Phase 5 — Guard Implementation

**Status:** **UNBLOCKED IN SEQUENCE.** The pure engine already maps
`GuardKind.PANIC` → immediate `confirmed(..., GuardSeverity.CRITICAL, ...)` in
`ProtectionStateEngine.kt:32`, and `GuardObservation.PanicActivated` exists, so G1-05 is
expressed in the domain. What is missing is the platform path: nothing connects a UI
action to that engine and persists the result. Protocol Batch C implements exactly that.
Motion is Batch G, SIM Batch I (feasibility first), Lock Batch J (feasibility **and** the
current official Play policy, cited, first). Battery stays prohibited (§20, G1-04).

### Phase 6 — Evidence & Privacy

**Status:** **PARTIALLY RESOLVED / Batch H.** Retention (§5.4/A-1 30 days), deletion
(§5.6), export (§5.5 disabled), factory reset (§5.7/A-2/A-3), and encryption (§1–§3) are
settled and device-verified. Protocol §21–§23 now also settle the shape: **no camera and
no location in the Panic batch**, no audio ever approved, and permissions requested only
incrementally for the feature that needs them. Unresolved and deferred to Batch H: camera
trigger and duration, location precision/freshness, redaction, and the consent/privacy copy.

### Phase 7 — Continuous Monitoring

**Status:** **RESOLVED IN PRINCIPLE / Batch F.** §17 authorizes a foreground service for
an active guard and **prohibits WorkManager as a real-time detector**. The Android
limitations (Doze, OEM battery management, Force Stop, process death, reboot, user
restrictions) must be documented, and **no 24/7 guarantee may be claimed** — the app must
never present a guarantee it cannot prove.

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
| 4d-7 | Offline G4-B local closure audit: backup boundaries, schema evidence, wiring scope, no-fallback/no-destructive checks, reproducible review script | 4d-6; local implementation complete | **CLOSED (CI + DEVICE)** by `4d-7-correction`, `4d-7-ci-coverage`, `4d-ci-hardening`, `0901c5c` and the later §5/§6 batches. G4 **overall** remains open — see "Real remaining G4 gaps" |
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
| 4d+ | One platform capability per batch: one Guard, evidence, auth, or monitoring | Capability-specific approval and device evidence | **UNBLOCKED IN ORDER** — the owner protocol §38 authorizes the sequence Panic (C) → notifications (E) → background (F) → Motion (G) → evidence/permissions (H) → SIM (I) → Lock (J). Not one batch of everything |
| 5 | Local response and notifications | G2/G3 policy | **UNBLOCKED — sequenced after Panic.** §16 real notifications; §17 foreground service acceptable; WorkManager prohibited as a real-time detector; no 24/7 guarantee may be claimed |
| 6 | External providers | G5 change from deferred | **DEFERRED — REAFFIRMED.** §30 explicitly bars Firebase, cloud, accounts, analytics, **ads**, WhatsApp, Telegram, SMS and email during the core batches |
| 7 | End-to-end security/release review | All prior batches | BLOCKED on batches B–I; protocol Batch K/L |

## Real remaining G4 gaps (found by trace, 2026-09-29)

Two lifecycle requirements in the owner protocol are **genuinely unmet**. Both were
found by tracing call sites, not assumed.

| Gap | Evidence from trace | Status |
|---|---|---|
| **No rotation trigger exists** | `EvidenceKeyPolicy.rotationDue()` (`EvidenceKeyPolicy.kt:52`) is defined and tested, but `grep -rn "rotationDue" app/src/main` returns **only its own definition** — no production caller. `EvidenceKeyRotator` is referenced only from `EvidenceKeyRotator.kt` itself plus its JVM and instrumented tests. Nothing in `ShieldraApp`, the storage factory, or any service invokes rotation. | **IMPLEMENTED + TESTED + CI VERIFIED + DEVICE VERIFIED — but NOT TRIGGERED.** Per protocol §9, `rotationDue()` alone is not evidence that automatic rotation exists, and no claim of automatic rotation may be made |
| **No old-key retirement lifecycle** | `grep -rni "retir" app/src/main` returns **zero matches**. There is no `RETIRED` state, no "zero remaining references to the old key version" check, and no destruction path distinct from the §5.7 factory reset. Rotation deliberately leaves old keys in place. | **NOT IMPLEMENTED.** Per protocol §10, implement the retirement **state** without inventing a destruction policy; the final destruction decision stays an explicit owner question |

Everything else the protocol listed as possible G4 residue was checked and is already
closed: AAD-v1 (§8 — canonical format intact, mandatory on the evidence path, real AES-GCM
authentication failure proven on device), StrongBox runtime detection (§7 —
`FEATURE_STRONGBOX_KEYSTORE`, not SDK inference), the rotation algorithm's step order and
resumability, failure semantics (§27), factory reset (§28), and the retention values
(`Evidence`/`Quarantine` 30 days per A-1, event metadata 90 days).

## Required next batch

**Batch B — `4d-rotation-orchestration-and-retirement`** (protocol §9/§10). Close the two
gaps above: a real production rotation trigger, and an old-key retirement state machine
that never destroys a key while evidence still references it. TDD, CI green, device
verified.

This is followed, in the owner-mandated order, by:

| Batch | Work | Protocol |
|---|---|---|
| A | Reconcile roadmap / questions / decisions against the actual repository | §3–§5 — **this batch** |
| B | Rotation trigger + old-key retirement | §9–§10 |
| C | **Panic Guard end-to-end** — UI action → signal → immediate confirmation → `EventId` → `SecurityEvent` → pipeline → Room → reload → History → Detail, idempotent on repeat | §12–§15 |
| D | Flip `StorageRuntimeApproval.approved = true` in the **same** batch, and only after C's device verification | §11, §14 |
| E | Real notifications; `POST_NOTIFICATIONS` requested only when needed; granted/denied/disabled handled honestly — never "alert sent" without permission | §16 |
| F | Background/active-guard execution; foreground service acceptable; **WorkManager prohibited as a real-time detector**; no 24/7 claim | §17 |
| G | Motion Guard | §18 |
| H | Evidence / privacy / permission implementation | §21–§23 |
| I | SIM Guard — feasibility first, then implementation | §19 |
| J | Lock Guard — feasibility + **current** official Play policy, cited exactly, before any code | §24–§25 |
| K | Full security/privacy/device audit | §33–§34 |
| L | Release hardening | §38 |

**Standing prohibitions carried forward from the protocol:** no battery-percentage security
event (§20); no continuous camera or location (§21–§22); no audio recording is approved;
no external providers, **including ads and analytics**, during these core batches (§30);
no UI may show `Protected`/`Active`/`Evidence captured`/`Alert sent` unless real state
proves it (§31); every dead button must work, be honestly disabled, or be removed (§32).

**Batch D is not a standalone step.** `StorageRuntimeApproval.approved` stays `false`
until the Panic producer is device-verified, so no intermediate build ever opens storage
and shows an empty app.

When device runs are needed, use the app and androidTest APKs from the **same** CI run,
because each runner regenerates the debug keystore.

**Correction recorded against 4d-aad-binding:** that batch closed without a
§16 batch report and its StrongBox instrumented assertion was a placeholder
(`assertTrue(true)`), which masked an SDK-level-only capability check. Both
were fixed in this batch and documented in
`docs/reports/batch-4d-key-rotation.md`.
