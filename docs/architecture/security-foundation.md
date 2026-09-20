# Group C Security Foundation

## Boundaries

The Android Keystore boundary remains in `platform/PlatformBoundaries.kt`. Sensitive data must cross a future storage boundary rather than being placed in UI state or arbitrary domain metadata. `EncryptedVault` is a contract in the evidence layer, not an encryption implementation. `AuthenticationGate` is independent of UI and enumerates sensitive operations including disabling protection, configuring delivery, deleting sensitive evidence, and changing sensitive settings.

## Deferred decisions

The encryption algorithm, key lifecycle, authentication mechanism, secure storage schema, and production evidence-retention policy are **DEFERRED**. Group C does not claim production security and does not add a cryptographic dependency or implementation.

## Confirmed-event authority

Only `ProtectionStateEngine` exposes the contract operation that can create `SecurityEvent`. Guards produce `Signal` values only. No guard, UI, service, or delivery adapter can create a confirmed event through the current source boundaries.
