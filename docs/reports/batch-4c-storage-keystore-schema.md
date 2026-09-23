# Batch 4c — Storage Schema and Keystore Candidate

**Date:** 2026-09-23
**Status:** Implementation candidate under G4 review; production storage is not wired.

## Scope implemented

This batch adds a versioned SQL schema contract for the future Room adapter. Version 1 models security events, evidence references, and delivery attempts. It includes primary keys, event foreign keys with cascade semantics, non-empty checks, timestamp ordering, expiry/state indexes, and an explicit migration boundary with no destructive default migration.

This batch also adds an Android Keystore-backed encryption candidate. It requires the caller to provide an explicit `EncryptionPolicy` and key alias. The candidate uses AES-GCM with a 256-bit AES key, a fresh 12-byte initialization vector for each encryption operation, a 128-bit authentication tag, optional caller-supplied associated data, and a versioned binary payload envelope. It never falls back to a software key when Android Keystore is unavailable.

## Security boundaries preserved

The implementation is not connected to the application graph, EventPipeline, evidence capture, Room, DataStore, notifications, or any production response path. No retention, deletion, export, reset, uninstall, backup restoration, key rotation, authentication UX, or biometric policy was invented. The existing backup rules continue to exclude databases and files.

The policy object deliberately has no default instance. This prevents an adapter from silently selecting cryptographic or authentication behavior by convenience. Existing-key loading does not silently replace, rotate, or delete key material. A future security review must define key lifecycle, recovery after credential changes, and the approved product policy before integration.

## Validation

The pure schema and envelope policy tests cover versioning, migration absence, explicit AES-256 policy validation, authentication-window validation, and payload envelope round-tripping. Android Keystore behavior requires an actual Android runtime and is not claimed as device-verified in this batch.

## Open G4 decisions

The following remain open and block production integration: Room dependency/version, entity mapping, migration authorization, corruption recovery, retention and deletion semantics, backup/reset behavior, key alias ownership, key rotation and invalidation, hardware-backed requirement, authentication binding, user credential changes, evidence-file naming, metadata minimization, and final security/privacy sign-off.

## Gate decision

Batch 4c may be considered **candidate implementation complete** only after the build and deterministic tests pass. It does not close G4. The next step is a focused storage/security review followed by an approved Room/file adapter batch; no platform evidence or production security claim should depend on this candidate before that review.

## Verification result

The first verification attempt failed because Kotlin `const val` cannot hold multiline SQL strings. The declarations were corrected to runtime `val` constants. The second verification then completed successfully with Java 17 and the temporary Android SDK:

```text
./gradlew clean testDebugUnitTest lintDebug assembleDebug
BUILD SUCCESSFUL
52 actionable tasks: 52 executed
```

Lint reported only the platform deprecation warning for `setUserAuthenticationValidityDurationSeconds`; there were no lint failures. Keystore execution itself remains unverified on a device because Batch 4b is still blocked.
