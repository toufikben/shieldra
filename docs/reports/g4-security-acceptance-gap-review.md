# G4 Security Acceptance Gap Review

**Date:** 2026-09-23
**Repository:** `toufikben/shieldra`
**Reviewed implementation:** Batch 4c candidate, commit `7a53fbe`
**Gate status:** **NOT READY FOR FINAL CLOSURE**

## Executive decision

Batch 4c passes the local engineering gate but does not pass the G4 security-acceptance gate. The repository contains a reviewable versioned schema contract and an Android Keystore encryption candidate. It does not yet contain an approved production storage policy, a Room/DataStore adapter, a finalized key lifecycle, a recovery model, or device evidence for Keystore behavior.

The missing work is primarily **decision and evidence work**, not another generic implementation pass. G4 must not be marked closed merely because `clean testDebugUnitTest lintDebug assembleDebug` succeeds.

## Evidence reviewed

The review compared the authoritative internal decisions, the Phase 4 decision register, the storage/security proposal, the Batch 4c report, the current roadmap, the Batch 4c source/tests, and the Android backup rules.

| Evidence | Current finding |
|---|---|
| `StorageSchema` | Version 1 SQL contract exists; migration registry is intentionally empty. |
| `EncryptionPolicy` | Explicit policy object exists; no silent default policy exists. |
| `AndroidKeystoreEncryptor` | AES-GCM candidate exists; no software-key fallback; not wired to the app graph. |
| Batch 4c tests | Schema, policy validation, and envelope round-trip tests exist. |
| Local verification | Build, unit tests, lint, and debug assembly pass. |
| Device verification | Not available; Batch 4b has no ADB, AVD, emulator, or physical device. |
| Backup rules | Database and file domains are excluded, but the product-level reset/restore decision is not documented. |
| Authority records | G4 remains `APPROVED DIRECTION / DETAILS BLOCKED`. |

## Closure checklist

| ID | G4 requirement | Current status | Why it prevents closure | Required closure evidence |
|---|---|---|---|---|
| G4-01 | Approved data classification and minimization | **MISSING** | The schema has event/evidence/delivery fields, but there is no approved field-by-field classification for sensitive, operational, derived, or user-visible data. | Data inventory with owner, purpose, sensitivity, minimization rationale, and prohibited fields. |
| G4-02 | Final schema and entity mapping | **PARTIAL** | SQL candidate exists, but Room entity/DAO mapping, nullability policy, enum encoding, metadata limits, and relationship ownership are not approved. | Approved schema document, Room mapping, constraints, indexes, and review sign-off. |
| G4-03 | Migration and downgrade policy | **MISSING** | Version 1 has no forward migration and no policy for failed, interrupted, incompatible, or downgraded migrations. | Migration matrix, non-destructive policy, interrupted-migration test, downgrade decision, and rollback plan. |
| G4-04 | Retention and deletion | **MISSING** | No duration, event-specific expiry behavior, evidence cleanup, user reset, uninstall, or secure deletion semantics are defined. | Approved retention table, deletion state machine, cleanup job behavior, and verification tests. |
| G4-05 | Export and user authority | **MISSING** | Export authority, redaction, audit trail, and whether sensitive records can leave the device are unresolved. | Explicit export policy or explicit prohibition, with redaction and audit requirements. |
| G4-06 | Backup, restore, and device transfer | **PARTIAL** | Current rules exclude databases/files, but the product decision for reset, reinstall, device transfer, and orphaned key/data behavior is absent. | Approved backup matrix, restore behavior, excluded-domain verification, and orphan cleanup policy. |
| G4-07 | Cryptographic algorithm and parameters | **PARTIAL** | The candidate uses AES-GCM/AES-256/128-bit tag/12-byte IV, but the authoritative decision record still says the algorithm and key details are not approved. | Security approval of algorithm, parameters, library/provider, AAD format, payload version, and failure semantics. |
| G4-08 | Key ownership and alias scope | **MISSING** | Alias format is validated, but ownership, namespace, per-installation/per-user scope, collision handling, and multi-profile behavior are not decided. | Key alias registry, ownership model, collision test, and profile/user-boundary decision. |
| G4-09 | Key lifecycle and rotation | **MISSING** | No rotation, re-encryption, old-key retirement, crash recovery, or partially rotated dataset policy exists. | Key lifecycle state machine, rotation protocol, atomicity rules, and recovery tests. |
| G4-10 | Credential and biometric changes | **MISSING** | `invalidateOnBiometricEnrollment` and authentication validity are caller inputs, not an approved product policy. Device credential changes and invalidated keys have no recovery behavior. | Approved authentication-binding policy and tests for biometric enrollment, lock-screen removal/change, invalidation, and re-enrollment. |
| G4-11 | Hardware-backed requirement | **MISSING** | The candidate requests Android Keystore but does not establish whether hardware-backed key material is mandatory, preferred, or acceptable to fall back to unavailable status. | Threat-model decision and runtime attestation/availability policy; explicitly no software fallback if required. |
| G4-12 | Evidence-file security | **MISSING** | No file adapter exists for names, paths, permissions, atomic writes, metadata minimization, integrity envelope, cleanup, or orphan handling. | File format/path policy, atomic-write protocol, tamper test, cleanup test, and permission review. |
| G4-13 | Corruption and partial-write recovery | **MISSING** | SQL and payload validation exist, but database corruption, truncated envelopes, torn writes, and invalid foreign-key states have no recovery policy. | Recovery state machine, quarantine/delete rules, user-visible outcome, and fault-injection tests. |
| G4-14 | Pipeline/storage atomicity | **PARTIAL** | Batch 3 defines repository ownership and resume semantics, but no persistent adapter proves atomic stage/state updates across process death. | Adapter transaction tests for duplicate IDs, crash after write, crash before write, resume, expiry, and concurrent access. |
| G4-15 | Authentication for sensitive operations | **MISSING** | The domain has authentication contracts, but storage access policy, unlock/session duration, reauthentication, and failure UX are not bound to storage. | Sensitive-operation matrix and implementation-level authentication policy. |
| G4-16 | Android runtime evidence | **MISSING** | No ADB, emulator, AVD, or physical device is available. Keystore generation, invalidation, lock-screen behavior, and API/OEM differences are unverified. | Device evidence package across the supported API/OEM matrix, starting with Keystore and storage recovery. |
| G4-17 | Threat model and abuse cases | **MISSING** | No G4-specific threat model connects assets, attackers, compromise assumptions, key theft, local extraction, rollback, or malicious app access to controls. | Reviewed threat model with assets, trust boundaries, abuse cases, mitigations, residual risk, and owner acceptance. |
| G4-18 | Security/privacy sign-off | **MISSING** | G6 requires a dedicated final security/privacy review, and no G4 acceptance record is signed. | Named owner/security reviewer, checklist result, unresolved-risk disposition, and explicit G4 acceptance decision. |

## Critical blockers

The following blockers must be closed before G4 can be marked **APPROVED FOR PRODUCTION STORAGE**:

1. **G4-04, G4-06, G4-09, and G4-10:** data and key lifecycle decisions. A secure cipher without deletion, rotation, restore, and invalidation behavior is not a complete storage security design.
2. **G4-07, G4-08, and G4-11:** cryptographic and Keystore authority. The current candidate is an implementation option, not an approved security policy.
3. **G4-12, G4-13, and G4-14:** failure and recovery behavior. Storage must remain safe across corruption, partial writes, process death, and resumed pipeline stages.
4. **G4-16:** runtime evidence. Keystore behavior cannot be accepted from JVM tests alone.
5. **G4-17 and G4-18:** threat-model and independent security/privacy sign-off.

## Recommended closure sequence

| Order | Action | Exit condition |
|---:|---|---|
| 1 | Approve data inventory, minimization, retention, deletion, export, backup, and reset policy | G4-01, G4-04, G4-05, and G4-06 closed |
| 2 | Approve schema/entity/migration and corruption model | G4-02, G4-03, and G4-13 closed |
| 3 | Approve cryptographic and Keystore policy | G4-07 through G4-11 closed |
| 4 | Implement the Room/file adapter behind existing repository boundaries | No coordinator redesign; adapter tests pass |
| 5 | Add fault-injection, process-death, rotation, invalidation, and tamper tests | G4-09, G4-12, G4-13, and G4-14 evidence complete |
| 6 | Execute Android device evidence | G4-16 evidence recorded; unsupported outcomes remain explicit |
| 7 | Conduct threat-model and security/privacy review | G4-17 and G4-18 signed |

## Decision required from the owner

The owner does **not** need to approve the existence of the current candidate code. The material decision is whether to approve the policy values and product behavior listed in the critical blockers. Until those decisions are supplied, the correct repository status is:

> **G4: APPROVED DIRECTION / DETAILS BLOCKED; Batch 4c candidate implementation verified locally; production storage integration prohibited.**

## Final assessment

**G4 closure score: 3 of 18 checks are substantially evidenced, 4 are partial, and 11 are missing.** The exact count is a review aid rather than a security metric. The gate remains correctly closed because the missing items include key lifecycle, data lifecycle, recovery, runtime evidence, threat model, and sign-off.

## Repository references

- [`PHASE4_DECISION_REGISTER.md`](../PHASE4_DECISION_REGISTER.md)
- [`PHASE4_STORAGE_SECURITY_PROPOSAL.md`](../PHASE4_STORAGE_SECURITY_PROPOSAL.md)
- [`batch-4c-storage-keystore-schema.md`](batch-4c-storage-keystore-schema.md)
- [`PHASE4_DEVICE_TEST_MATRIX.md`](../PHASE4_DEVICE_TEST_MATRIX.md)
- [`ROADMAP.md`](../ROADMAP.md)
- [`SHIELDRA_DECISIONS.md`](../../SHIELDRA_DECISIONS.md)

## G4-A response

The supplied G4 Security Storage Decision Freeze is now represented by a conservative decision package under [`../security/`](../security/). The package includes the data inventory, consolidated schema/migration/lifecycle/backup/crypto/key/authentication/corruption proposals, Android verification checklist, focused threat model, and final acceptance checklist. The package converts the gaps into reviewable decisions but intentionally leaves all concrete policies **PROPOSED — REQUIRES APPROVAL**. G4 remains closed until implementation, device evidence, and owner/security/privacy sign-off are complete.
