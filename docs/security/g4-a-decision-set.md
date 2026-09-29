# SHIELDRA G4-A Conservative Decision Set

**Status:** SUPERSEDED ON STATUS, STILL VALID AS SPECIFICATION — see the reconciliation below
**Gate:** G4 = SUBSTANTIALLY DEVICE-VERIFIED / NOT PRODUCTION-APPROVED
**Date:** 2026-09-23 · **Reconciled:** 2026-09-29 against `fcfcd96`

This document converts the remaining G4 gaps into a conservative implementation decision set. It
does not close G4 and does not authorize production storage until the acceptance checklist is
approved.

> ## Reconciliation — why every row below still says "PROPOSED"
>
> Written on 2026-09-23, this file marked every item `PROPOSED — REQUIRES APPROVAL`. The owner
> then approved most of them **in a different document** —
> [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](../authority/FINAL_SECURITY_STORAGE_DECISIONS.md)
> and [`SHIELDRA_DECISIONS.md`](../../SHIELDRA_DECISIONS.md) — and the code plus CI plus the
> RMX3910 device then verified them. The inline labels were never updated, so reading this file
> alone now understates the repository's real state. Per the owner protocol §4 ("Do NOT simply
> append another paragraph" — reconcile) and §5 (the seven status levels are not
> interchangeable), here is the mapping. **Where this table says APPROVED, the inline row below
> is approved; where it still says PROPOSED, it genuinely is.**
>
> | § | Proposal | Owner decision | Evidence today |
> |---|---|---|---|
> | 1 | Room schema, entities, constraints, idempotent `EventId`, `EXPIRED ≠ FAILED_FINAL` | **APPROVED** | IMPLEMENTED, CI VERIFIED, DEVICE VERIFIED (`RoomEventRepositoryInstrumentedTest`, `PersistedEventReadingInstrumentedTest`) |
> | 2 | Non-destructive migrations, `fallbackToDestructiveMigration` prohibited, fail closed | **APPROVED** (authority §4.2 "لا destructive fallback" and §4.3, plus the owner's standing instruction against destructive migration) | IMPLEMENTED, DEVICE VERIFIED for the 1→2 upgrade path. **Downgrade is not implemented and not tested** — unsupported by policy (§4.4), which is the fail-closed behaviour this § asks for |
> | 3 | Lifecycle: shortest retention, orphan reconciliation, export off by default, audited reset | **PARTLY APPROVED** — retention is now fixed at evidence 30 d / event metadata 90 d / quarantine 30 d (§5.4, amendment A-1); export DISABLED by default (§5.5); factory reset deletes the evidence key irreversibly (§5.7, A-2/A-3) | IMPLEMENTED, CI VERIFIED. Cleanup **cadence** on a real device is still NOT RUN |
> | 4 | Backup/restore exclusion, "unavailable unless key restored" | **APPROVED as direction** | Manifest/XML + enforced CI review gate: STATIC + CI VERIFIED. **Real-device backup/restore/reinstall: NOT RUN** (Q-D17) |
> | 5 | Crypto profile: AES-256-GCM, Keystore key, 12-byte IV, 128-bit tag, versioned envelope, AAD canonical, no plaintext fallback | **APPROVED** — AAD-v1 is authoritative; §2.5 forbids software-key fallback | IMPLEMENTED, DEVICE VERIFIED including every negative AAD case (`RealAesGcmAadTest`) |
> | 6 | Key alias ownership, lazy creation, never silently replaced, rotation, retirement | **ROTATION APPROVED** (90 days or manual, §9 of the protocol); **RETIREMENT APPROVED as a state machine**; **destruction of a retired version is NOT APPROVED** — open owner decision Q-C8 | Rotation algorithm IMPLEMENTED + DEVICE VERIFIED. **Trigger NOT IMPLEMENTED** (`rotationDue()` has zero production callers). **Retirement NOT IMPLEMENTED** (no code) |
> | 7+ | Authentication binding, corruption classification, acceptance checklist rows | See `g4-acceptance-checklist.md` | Corruption/recovery semantics are implemented and device-verified per §6 failure semantics; independent **security review (G6) has not happened**, so nothing here is SECURITY REVIEWED |
>
> **The single most important correction:** this file's own conclusion — "does not authorize
> production storage" — is now enforced by code rather than by prose. `StorageRuntimeApproval`
> is `false` and the wiring gate refuses to open the database or create a Keystore alias, which
> `StorageWiringGateOffInstrumentedTest` proves on the device. Storage stays closed until the
> first real detector is verified in the same batch (protocol §11, §14).
>
> **One staleness found in the authority document itself, reported rather than edited:**
> [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](../authority/FINAL_SECURITY_STORAGE_DECISIONS.md)
> §4.1 still says "Room version = **1** currently" and §4.5 says "no migration is required now
> from v1 → v2". Both were true when written; the database is now at version 2 with an explicit
> `Migration(1, 2)` that is registered and device-tested. That is a factual drift, not a policy
> conflict — §4.5 says exactly what to do "عند أول تغيير حقيقي في schema" and that is what was
> done. I am not editing a binding authority file on my own initiative; the owner can amend the
> two sentences, and until then read them as history.

## 1. Room schema proposal

Room should store structured metadata and pipeline state only. Sensitive evidence remains in an encrypted-file layer and is referenced by an allowlisted identifier.

| Entity | Required fields | Constraints and indexes |
|---|---|---|
| `SecurityEventEntity` | `eventId`, `eventType`, `state`, nullable `severity`, `createdAtEpochMs`, `updatedAtEpochMs`, nullable `resumeStage`, nullable `expiresAtEpochMs` | Primary key `eventId`; unique canonical identity; indexes on `state` and `expiresAtEpochMs`; `updatedAt >= createdAt`; bounded enums only |
| `EvidenceReferenceEntity` | `evidenceId`, `eventId`, `kind`, `contentReference`, nullable `capturedAtEpochMs` | Primary key `evidenceId`; index on `eventId`; foreign key to event; no raw evidence blob; reference must be allowlisted and non-sensitive in naming |
| `DeliveryAttemptEntity` | `attemptId`, `eventId`, `channel`, `status`, nullable `occurredAtEpochMs`, nullable `failureReason` | Primary key `attemptId`; index on `eventId`; foreign key to event; bounded channel/status; no credentials or arbitrary endpoint |

The schema must preserve Batch 3 decisions. `EventId` remains canonical. Duplicate insert or retry for the same ID is idempotent. `EXPIRED` remains distinct from `FAILED_FINAL`. The repository owns atomic persistence, and the coordinator owns stage selection. `resumeStage` is persisted together with the state that it describes. No arbitrary `metadata` map is promoted to a free-form database column.

**Decision status:** Proposed. Room dependency, entity mapping, enum encoding, relationship ownership, and schema export still require approval.

## 2. Non-destructive migration policy

Production security data must use explicit, forward Room migrations. `fallbackToDestructiveMigration` is prohibited. Each version change must include exported schema review, a migration test from the previous version, an interrupted-migration test, and a post-migration invariant check.

If a migration cannot complete, startup must fail closed for security-sensitive operations. The app may expose a recovery state and preserve the original database for diagnostic or owner-approved recovery; it must not silently create an empty database and present it as a valid history. Downgrade is unsupported by default and must produce an explicit incompatible-version state rather than destructive rollback. Rollback means restoring an authenticated, separately approved backup or shipping a forward migration; it does not mean deleting the database.

**Decision status:** Proposed. Room version, migration tooling, and recovery UX require approval.

## 3. Data lifecycle policy

The proposed default is the shortest retention that supports active processing, approved history, and recovery. No indefinite retention is permitted by default. Exact durations require owner/privacy approval and must be per data class or Guard where necessary.

Active events remain until a terminal state is durably persisted. Completed, failed, and expired events enter an approved history lifecycle. `EXPIRED` is never silently rewritten as `FAILED_FINAL`. Evidence cannot outlive the approved parent-event evidence retention. Temporary files are deleted after successful atomic rename or quarantined after a failed validation. Orphaned references and orphaned encrypted files are detected by a reconciliation job and are quarantined or deleted according to the approved policy; neither is silently treated as valid evidence.

Manual reset must require the approved authentication/authorization path and must report what was removed. Export is prohibited by default until a separate redaction, authorization, audit, and destination policy is approved. Uninstall removes local application data according to Android behavior; the product must not claim recovery after uninstall unless Android actually restored protected data and the key remains available.

**Decision status:** Proposed. Retention durations, reset authority, export, and cleanup cadence require approval.

## 4. Backup and restore policy

The conservative default is to exclude security events, encrypted evidence, database files, and key material from cloud backup and device transfer. The current Android rules already exclude database and file domains; this must be verified in the final manifest and runtime test package.

On reinstall or device transfer, treat protected local data as unavailable unless Android demonstrably restores both metadata and the matching key. If metadata is restored without the encrypted file, mark the reference unavailable and do not claim evidence recovery. If the encrypted file is restored without its key, fail closed and report permanent key unavailability; never decrypt through a substitute key. If database and file sets are inconsistent, run reconciliation and quarantine/delete according to the approved lifecycle policy.

**Decision status:** Proposed. Backup exclusion, transfer behavior, and user-facing reset/recovery behavior require approval and device verification.

## 5. Cryptography profile

The proposed profile uses established Android/JVM primitives only:

| Parameter | Proposed value | Status |
|---|---|---|
| Algorithm | AES-256-GCM | PROPOSED — REQUIRES APPROVAL |
| Key material | Android Keystore AES key | PROPOSED — REQUIRES DEVICE VERIFICATION |
| IV/nonce | 12 bytes from `SecureRandom` for every encryption | PROPOSED — REQUIRES APPROVAL |
| Authentication tag | 128 bits | PROPOSED — REQUIRES APPROVAL |
| AAD | Version, logical object class, and stable object identifier in a canonical length-prefixed encoding; never secret content | PROPOSED — REQUIRES APPROVAL |
| Payload | Versioned envelope containing version, IV length/IV, ciphertext length/ciphertext; no plaintext metadata | PROPOSED — REQUIRES APPROVAL |
| Provider | Android Keystore for key operations; platform/JCA `Cipher` for AES-GCM | PROPOSED — REQUIRES DEVICE VERIFICATION |
| Failure | Authentication failure, invalid version, truncation, or missing key is an unavailable/corrupt result; never plaintext fallback | PROPOSED — REQUIRES APPROVAL |

No custom cryptographic protocol, software-key fallback, or unauthenticated encryption is permitted.

**Decision status:** Proposed. The existing Batch 4c candidate is evidence of feasibility, not approval.

## 6. Key lifecycle policy

The key alias belongs to the application installation and protected storage profile. It must be namespaced, non-user-content-derived, and never written into the database or evidence file. Key creation is lazy and explicit. Existing key material is never silently replaced.

Rotation creates a new key, encrypts and durably verifies each payload under the new key, records a resumable rotation marker, and retires the old key only after all eligible payloads are verified. A crash or interruption resumes from the marker. Old-key deletion before verification is prohibited. A missing or invalid key makes affected evidence unavailable; it does not trigger a substitute key or plaintext recovery. Permanent key loss is reported as non-recoverable local evidence loss under the approved privacy policy.

**Decision status:** Proposed. Alias ownership, rotation cadence, re-encryption protocol, hardware-backed requirement, and permanent-loss UX require approval.

## 7. Biometric and device credential binding

The proposed policy prefers hardware-backed Keystore protection when available and remains correct on devices without StrongBox by reporting capability status rather than bypassing authentication. No software fallback may weaken the authentication boundary.

The owner must select whether the evidence key requires device credential authentication, whether a validity window is allowed, and whether biometric enrollment invalidates the key. Device credential change, lock-screen removal, biometric enrollment change, and Keystore invalidation must each produce an explicit unavailable/recovery state. Runtime behavior differs by API level and device; all such outcomes are **IMPLEMENTATION + DEVICE VERIFICATION REQUIRED**.

**Decision status:** Proposed. Authentication binding, StrongBox preference, invalidation behavior, and re-enrollment policy require approval.

## 8. Corruption and partial-write policy

Security-sensitive evidence fails closed. A Room corruption signal, invalid state, invalid foreign key, truncated payload, invalid authentication tag, unsupported payload version, unexpected metadata, missing file, crash before rename, or crash after rename without database commit must never be accepted as valid evidence.

File writes use a temporary file, complete authenticated write, close/sync where supported, and atomic rename. The database reference is committed only after the file is durably available. Reconciliation handles the two mismatch directions: a reference without a file becomes unavailable/quarantined, and a file without a reference becomes quarantined or deleted according to the approved retention policy. Recovery must preserve the event’s idempotency and must not silently restart a persisted pipeline stage.

**Decision status:** Proposed. Atomic file adapter, fault injection, Room corruption handling, and quarantine rules require implementation and testing.

## 9. EventPipeline atomicity

The persistent repository must expose an atomic operation for state, resume stage, update timestamp, and bounded recovery metadata. The coordinator must not write storage fields directly. The adapter must test:

1. successful state update;
2. failed state update with no partial persisted transition;
3. process death before persistence;
4. process death after persistence;
5. duplicate `EventId` insertion;
6. concurrent processing of one `EventId`;
7. resume from the last persisted stage;
8. expiration during processing.

No crash-consistency claim is permitted until these tests pass on the actual adapter and are complemented by Android runtime verification.

**Decision status:** Proposed. Room/file adapter and fault-injection tests are required.

## Gate status

This decision set is **PROPOSED — REQUIRES APPROVAL**. It makes the next implementation steps concrete but does not close G4. Any item marked implementation or device verification required remains open until its evidence exists.
