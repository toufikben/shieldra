# G4 Acceptance Matrix

**Repository:** `toufikben/shieldra`  
**Scope:** Local Room/evidence foundation and G4-B closure evidence  
**Current conclusion:** **PARTIAL — static and compile evidence exists; device/runtime and security-policy acceptance remain blocked.**

This matrix deliberately separates implementation from verification. A `YES` in the Code column does not imply runtime security verification.

| Requirement | Code | Test | CI | Device | Status | Evidence or blocker |
|---|---:|---:|---:|---:|---|---|
| Room entities and exported schema exist | YES | PARTIAL | YES | NOT REQUIRED | VERIFIED STATIC | Exported schema and Room build checks pass; runtime database creation is not device-verified. |
| Foreign keys and indexes are declared | YES | PARTIAL | YES | REQUIRED | PARTIAL | Source/schema evidence exists; instrumented runtime execution is blocked without an AVD/device. |
| Event state persistence and expected-state update | YES | YES STATIC | YES | REQUIRED | PARTIAL | InMemory and Room paths are covered by source/tests; Room reopen execution is not runtime-verified. |
| Event ID duplicate protection | YES | YES STATIC | YES | REQUIRED | PARTIAL | Domain and repository paths exist; device database execution remains unverified. |
| Evidence encrypted-file write/read | YES | YES JVM | YES | REQUIRED | PARTIAL | Deterministic JVM cipher tests pass in CI; Android Keystore behavior is not established. |
| Temporary-file cleanup and atomic replacement intent | YES | YES JVM | YES | REQUIRED | PARTIAL | Single-writer deterministic tests exist; concurrent writer, crash, and filesystem fallback behavior are not verified. |
| Symlink evidence payload rejection | YES | YES JVM | PENDING | REQUIRED | PARTIAL | Added in `1335a8b`; CI run `35889049875` was in progress at the last recorded check. |
| Room child-row collision is fail-closed | YES | PARTIAL | PENDING | REQUIRED | PARTIAL | Added in `1335a8b`; instrumented collision/fault execution is not yet available. |
| Backup and device-transfer exclusions | YES | YES STATIC | YES | REQUIRED | PARTIAL | Manifest/XML and enforced G4-B script pass; Android backup/restore behavior is not device-verified. |
| `noBackupFilesDir` evidence boundary | YES | YES SOURCE/STATIC | YES | REQUIRED | PARTIAL | Factory boundary and static review pass; actual Android filesystem behavior is unverified. |
| G4-B review script is enforced by CI | YES | YES FIXTURE | PENDING | NOT REQUIRED | PARTIAL | Added in `abed3c4`; latest subsequent runs were still active at the last check. |
| Android Keystore key generation and AES-GCM policy | CANDIDATE ONLY | NO DEVICE TEST | YES COMPILE | REQUIRED | BLOCKED | Requires Android runtime, hardware capability evidence, and approved policy. |
| AAD binding to evidence identity | NO | NO | YES COMPILE | REQUIRED | BLOCKED | G4-A decision is not approved; do not implement by assumption. |
| Key ownership, invalidation, rotation, and loss recovery | NO | NO | YES COMPILE | REQUIRED | BLOCKED | Requires explicit security/lifecycle decision and device matrix. |
| Room migration and downgrade recovery | NO PRODUCTION MIGRATIONS | POLICY TEST ONLY | YES COMPILE | REQUIRED | BLOCKED | `StorageMigrations.all` is empty; migration history and recovery policy require approval. |
| Corruption classification and recovery state | PARTIAL | PARTIAL STATIC | YES COMPILE | REQUIRED | BLOCKED | Current mapping can throw on invalid enum/UUID data; recovery semantics require a defined policy. |
| Orphan/reference deletion lifecycle | PARTIAL | PARTIAL JVM | YES COMPILE | REQUIRED | BLOCKED | Missing-reference and parent-delete policy is unresolved; no destructive cleanup is invented. |
| Connected Android instrumented tests | YES SOURCES | NOT EXECUTED | COMPILE ONLY | REQUIRED | BLOCKED | CI compiles `assembleDebugAndroidTest`; no authorized AVD, emulator, or device is available. |
| Production wiring into `ShieldraApp` | NO | NOT APPLICABLE | STATIC | REQUIRED | DEFERRED | Explicitly gated until runtime and G4 approval; no automatic wiring is added. |

## Deferred application/runtime work

The following items are intentionally **deferred for a later Android programmer or owner decision** and are not represented as completed implementation:

1. Running `connectedDebugAndroidTest` and the supplied device-test script on an authorized AVD or physical device.
2. Verifying Room reopen, process death, backup/restore, Keystore invalidation, and hardware-backed behavior on Android.
3. Choosing and implementing AAD identity binding for encrypted evidence.
4. Defining key alias ownership, authentication binding, rotation, invalidation, and permanent-loss recovery.
5. Defining and implementing Room migration history, downgrade handling, corruption recovery, and rollback evidence.
6. Defining the lifecycle for missing evidence references, orphan files, deletion, export, and retention.
7. Wiring local storage into the production application graph.
8. Implementing Android Lock, Motion, SIM, Battery, or Panic adapters; the current work only hardens pure domain contracts.

## Gate conclusion

G4-B must remain **APPROVED DIRECTION / IMPLEMENTATION IN PROGRESS**. The repository has useful static and compile evidence, but it does not yet have the device, lifecycle, cryptographic-policy, or independent security/privacy evidence required for a production or security-complete claim.
