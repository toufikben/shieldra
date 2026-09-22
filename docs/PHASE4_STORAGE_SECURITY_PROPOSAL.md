# SHIELDRA Phase 4 Storage and Security Proposal

**Status:** Direction approved; implementation details are not approved.
**Last reconciled:** 2026-09-22

## Approved direction

- Use **Room** for structured persistent data such as security events, event metadata, Guard state, monitoring state, and suitable configuration metadata.
- Use **DataStore** for simple preferences and feature configuration.
- Store large evidence blobs in encrypted evidence files where appropriate; keep references and metadata in Room.
- Keep the architecture device-first and do not add cloud synchronization or external providers initially.

## Proposed logical model — not approved schema

| Store | Candidate contents | Status |
|---|---|---|
| Room | Event identity, Guard type, severity, lifecycle state, timestamps, evidence references, delivery/local-response metadata | **PROPOSED — NOT APPROVED** |
| Room | Guard baseline and monitoring state | **PROPOSED — NOT APPROVED** |
| DataStore | User preferences, feature enablement, display/configuration preferences | **PROPOSED — NOT APPROVED** |
| Encrypted files | Photo/audio/file evidence blobs, where a future approved policy permits them | **PROPOSED — NOT APPROVED** |

## Required security decisions before implementation

1. Database schema, indexes, constraints, and migration versions.
2. Retention, deletion, export, reset, uninstall, and backup semantics.
3. Evidence file naming, metadata minimization, integrity protection, and cleanup.
4. Cryptographic algorithm/library, nonce/IV handling, key generation, rotation, invalidation, recovery, and hardware-backed requirements.
5. Keystore authentication binding and behavior when device credentials change.
6. Corruption recovery and partial-write behavior.
7. Authentication requirements for sensitive operations.
8. Process-death and crash recovery for pipeline stages.

No cryptographic algorithm, key size, migration, backup policy, or authentication behavior is silently selected by this document. A future implementation batch must include a threat-model and technical review before adding dependencies or production storage.
