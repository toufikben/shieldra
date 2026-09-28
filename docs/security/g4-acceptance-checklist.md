# G4 Security Storage Acceptance Checklist

**Current decision:** G4 = APPROVED DIRECTION / DETAILS BLOCKED
**Target decision:** APPROVED FOR PRODUCTION STORAGE

G4 may change to the target decision only when every required row is approved and evidenced. Documentation alone does not pass a row that requires implementation or device verification.

| ID | Acceptance requirement | Evidence required | Status |
|---|---|---|---|
| G4-A-01 | Data inventory and minimization | Approved allowlist and prohibited-data list | PROPOSED — REQUIRES APPROVAL |
| G4-A-02 | Room schema and entity mapping | Approved schema, entities, constraints, indexes, and EventPipeline consistency | PROPOSED — REQUIRES APPROVAL |
| G4-A-03 | Non-destructive migrations | Explicit migrations, tests, interrupted-startup behavior, downgrade decision | PARTIALLY IMPLEMENTED — schema v2, explicit `Migration(1, 2)`, and an instrumented migration test exist; interrupted-startup and downgrade decisions still open; REQUIRES APPROVAL |
| G4-A-04 | Data lifecycle | Retention, deletion, reset, export, uninstall, cleanup, orphan policy | PROPOSED — REQUIRES APPROVAL |
| G4-A-05 | Backup/restore | Manifest verification and metadata/file/key mismatch behavior | PROPOSED + DEVICE/AVD VERIFICATION REQUIRED |
| G4-A-06 | Cryptography profile | Approved AES-GCM profile, AAD, payload, provider, failure behavior | PROPOSED — REQUIRES APPROVAL |
| G4-A-07 | Key lifecycle | Alias scope, creation, rotation, re-encryption, retirement, loss | ROTATION IMPLEMENTED LOCALLY — versioned aliases, per-evidence `key_version`/`aad_version`, resumable re-encryption, and decision §3.5 tests exist; Keystore runtime re-encryption and key-loss behavior require device evidence; REQUIRES APPROVAL |
| G4-A-08 | Authentication binding | Credential/biometric/StrongBox policy and invalidation behavior | STRONGBOX DETECTION CORRECTED — runtime `FEATURE_STRONGBOX_KEYSTORE` query replaces SDK-level inference; the owner RMX3910 measures without StrongBox; invalidation behavior unverified; DEVICE VERIFICATION REQUIRED |
| G4-A-09 | Corruption and partial writes | Fault injection, fail-closed behavior, quarantine/recovery | IMPLEMENTATION REQUIRED |
| G4-A-10 | EventPipeline atomicity | Persistent adapter tests for all eight required interruption cases | IMPLEMENTATION REQUIRED |
| G4-A-11 | Android runtime verification | Keystore, migration, process death, corruption, restore evidence | DEVICE/AVD VERIFICATION REQUIRED |
| G4-A-12 | Threat model | Threat → asset → attack surface → mitigation → residual risk → verification | PROPOSED — REQUIRES REVIEW |
| G4-A-13 | Privacy/security review | Named reviewer, unresolved-risk disposition, explicit acceptance | REQUIRED |
| G4-A-14 | High-risk blockers | No unresolved HIGH security blocker | REQUIRED |

## Explicit non-claims

Until all rows pass, SHIELDRA must not claim production-ready security, guaranteed evidence recovery, immunity to a rooted/fully compromised device, continuous operation, successful backup restoration, or hardware-backed protection on every device.

## Current decision

The checklist is **NOT ACCEPTED**. Batch 4c remains a locally verified candidate.
Since 2026-09-27 an owner device (Realme RMX3910) is attached and has executed
the Room and real AES-GCM/AAD instrumented suites, so the earlier "no device
available" blocker no longer applies; the outstanding device work is the
rotation, StrongBox, and migration instrumented runs. G4 remains closed until
implementation evidence, CI results, device runs, and named security review
are all present.
