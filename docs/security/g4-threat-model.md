# SHIELDRA G4 Focused Threat Model

**Status:** PROPOSED — REQUIRES SECURITY/PRIVACY REVIEW
**Gate:** G4 = APPROVED DIRECTION / DETAILS BLOCKED

This model covers protection at rest and storage integrity. It does not claim that encryption protects a rooted or fully compromised runtime.

| Threat | Asset | Attack surface | Existing mitigation | Residual risk | Required verification |
|---|---|---|---|---|---|
| Local attacker with filesystem access | Event metadata and encrypted evidence | App-private database/files | Android app-private storage; proposed Keystore encryption; backup exclusion candidate | Device compromise may expose metadata or enable deletion | File/database permission inspection and tamper tests |
| Rooted or fully compromised device | Keys, plaintext during authorized use, process memory | OS, runtime, debugger, memory, Keystore access | Keystore boundary; no software fallback; minimized evidence | **High residual risk**; SHIELDRA cannot claim immunity | Explicit product-risk acceptance; no false security claim |
| Database extraction | Event history, state, references | Database file and backups | Structured metadata minimization; backup exclusion candidate; integrity constraints | Metadata may reveal event occurrence even without evidence | Extraction test and metadata classification review |
| Encrypted-file extraction | Evidence content | Evidence files and temporary files | Proposed AES-GCM envelope; temp/close/atomic rename; no plaintext fallback | Key availability and device compromise remain risks | Wrong-key, wrong-AAD, truncation, and tag-failure tests |
| Key extraction attempt | Keystore key material | Android Keystore and aliases | Non-exportable Keystore key; hardware-backed preference; no key bytes in app storage | API/OEM/StrongBox variation; rooted device risk | Device/AVD Keystore inspection and invalidation tests |
| Backup extraction | Database, evidence, key identifiers | Cloud/device transfer | Current database/file exclusions; no key export | Platform behavior varies and needs verification | Backup/restore/device-transfer evidence |
| Rollback | Older event state or old ciphertext | Database restore, old files, migration | Versioned schema/payload; explicit migration; no destructive fallback | Replay/rollback policy is not implemented | Version rollback and stale-payload tests |
| Corrupted or tampered evidence | Evidence authenticity and event history | File bytes, metadata, foreign keys | Authenticated encryption candidate; fail-closed proposal; bounded schema | No production file adapter yet | Fault injection and reconciliation tests |
| Duplicate/replayed Event | Idempotency and delivery correctness | Event input, repository retry | Canonical EventId; unique key; sequential per-event processing | Persistent adapter is not implemented | Duplicate/concurrent/resume tests |
| Interrupted processing | Pipeline state and evidence references | Process death, crash, force-stop | Batch 3 resume contract; candidate state fields | No persistent crash-consistency proof | Process-death and transaction tests on Android |
| Stolen unlocked device | Local history and active keys | App UI, files, process | Authentication policy remains open; evidence minimization | User-visible data may be exposed | Authentication UX and lock-screen tests |

## Security boundary

The proposed controls protect selected data at rest and detect selected integrity failures. They do not make SHIELDRA secure against a fully compromised runtime, a rooted device, malicious system software, or a user who has authorized access to the device and application.

## Required sign-off

The owner and security/privacy reviewer must accept residual risks, especially rooted-device risk, metadata exposure, key invalidation, backup exclusion, permanent key loss, and unavailable recovery. Every implementation claim must be supported by code evidence, deterministic tests, Android runtime evidence, or an explicit documented limitation.
