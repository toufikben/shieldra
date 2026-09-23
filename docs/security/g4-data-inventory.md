# SHIELDRA G4-A Data Classification and Minimization Decision

**Decision ID:** G4-A-01  
**Subject:** Data classification and minimization  
**Status:** **PROPOSED — REQUIRES APPROVAL**  
**Gate status:** **G4 = APPROVED DIRECTION / DETAILS BLOCKED**  
**Prepared:** 2026-09-23  
**Scope:** Device-first local storage and the data passed through the Batch 3 EventPipeline

> This document is a security decision proposal, not a production-approval record. It does not close G4, establish production readiness, or provide Android/device verification. All policy values and behaviors marked **PROPOSED — REQUIRES APPROVAL** remain blocked until the owner and security/privacy reviewer approve them.

## 1. Purpose

The purpose of this decision is to define the minimum SHIELDRA data set needed to create and process confirmed Security Events, preserve approved event history, resume interrupted processing, and support recovery. The proposed default is:

> **Store the minimum information required to operate SHIELDRA, process Security Events, maintain approved history, and recover interrupted processing.**

SHIELDRA must not collect or retain continuous camera, audio, location, contact, message, or unrelated device data. A Signal is not evidence. Evidence collection begins only after a confirmed event and only when a separate Guard-specific policy authorizes the minimum relevant evidence. This proposal preserves the approved device-first direction: Room is intended for structured metadata and state, DataStore for lightweight preferences, and sensitive evidence for an encrypted-file layer with references from Room.

## 2. Scope and decision boundaries

This inventory covers the following logical stores and transient data:

- the future Room event, evidence-reference, and delivery-attempt records;
- bounded Guard and monitoring configuration that is necessary to operate the approved rules;
- DataStore preferences and feature configuration;
- encrypted evidence files and their minimal references;
- temporary files, cleanup/quarantine records, and exported data;
- Android Keystore key material and key identifiers as protected platform resources; and
- the state required to preserve Batch 3 identity, idempotency, expiration, sequential processing, and resume behavior.

This proposal does not approve Room wiring, a finalized schema, retention durations, export authority, cryptographic parameters, key lifecycle, authentication binding, backup/restore behavior, or Android platform behavior. The existing `StorageSchema`, `EncryptionPolicy`, and `AndroidKeystoreEncryptor` are candidate implementation evidence only.

## 3. Classification vocabulary and ownership

The proposed classification is intentionally simple so that implementation can enforce it without inferring sensitivity from arbitrary user content.

| Classification | Meaning | Handling expectation |
|---|---|---|
| **Operational metadata** | Identifiers, state, timestamps, bounded type values, and references required to run the pipeline. | Persist only the allowlisted fields. Protect database access and integrity. |
| **Security-sensitive metadata** | Event severity, expiration, recovery state, delivery outcome, key identifiers, and evidence relationships that reveal a security event or affect recovery. | Persist only when required. Protect at rest and fail closed on inconsistency. |
| **Sensitive evidence** | Photo, audio, file, or other Guard-specific content captured after confirmation. | Do not place in unrestricted Room blobs. Store only in an approved encrypted-file layer, with a minimal reference in Room. |
| **Configuration** | User-selected feature and display settings, plus approved Guard policy parameters. | Store only settings needed to operate SHIELDRA. Do not store secrets or raw telemetry in DataStore. |
| **Transient data** | In-memory values, temporary write buffers, and temporary paths used during one operation. | Do not persist. Clear or delete after success or failure according to the approved recovery policy. |

The **coordinator owns workflow and stage selection**. The **repository owns persistence and atomic state updates**. The data model must not force the coordinator to know whether persistence is in memory, Room, or another approved adapter. A future Room adapter must preserve the same repository boundary.

## 4. Proposed data inventory

The following is the complete proposed inventory for the current device-first scope. Each row states whether persistence is required for the product behavior. Where the answer is conditional, the condition is an approval boundary rather than permission to store by convenience.

### 4.1 Structured event and recovery data

| Field or document | Purpose | Sensitivity | Source | Persistence required? | Proposed retention requirement | Proposed deletion behavior | Proposed backup behavior | Proposed encryption requirement |
|---|---|---|---|---|---|---|---|---|
| `event_id` / `EventId` | Canonical identity, idempotency, duplicate suppression, concurrency key, and resume lookup. | Security-sensitive metadata | ProtectionStateEngine / EventPipeline | **Yes.** It is the primary key and must not be regenerated during retry or resume. | Retain only while needed for active processing and approved history. Exact terminal-history duration is **PROPOSED — REQUIRES APPROVAL**. | Delete with the event when its approved retention ends. Never delete merely because one processing attempt completed if approved history still requires the record. | **PROPOSED — REQUIRES APPROVAL:** exclude from Android backup and device transfer unless a later policy explicitly approves an authenticated, integrity-preserving export. | Database protection under the approved storage profile; integrity constraints and unique primary-key enforcement are required. |
| `event_type` | Identifies the event record type. Current domain value is `CONFIRMED_SECURITY_EVENT`. | Operational metadata; may reveal that a security event exists | ProtectionStateEngine / domain contract | **Yes**, if the record is persisted. Use a constrained enum, not arbitrary text. | Same as `event_id`. | Delete with the event. | Exclude by default pending backup approval. | Same as structured event metadata. |
| `guard_type` (if required by the approved policy) | Identifies the Guard responsible for a confirmed event and permits a per-Guard expiration policy. | Security-sensitive metadata | Guard confirmation engine | **Conditionally.** Persist only if the approved policy or audit requirement needs it. Use a constrained enum. The current domain contract does not yet expose this field. | Same as event history; no separate longer retention. | Delete with the event. | Exclude by default pending backup approval. | Same as structured event metadata. |
| `severity` | Carries the approved Guard severity used for handling and presentation. | Security-sensitive metadata | Guard confirmation engine | **Yes** for a confirmed event when severity is present; null remains allowed because the current domain model allows it. | Same as event history. | Delete with the event. | Exclude by default pending backup approval. | Same as structured event metadata. |
| `state` | Stores lifecycle state and prevents invalid transitions. Allowed values include `INITIATED`, `COLLECTING`, `READY`, `DELIVERING`, `DELIVERED`, `DEFERRED`, `FAILED_FINAL`, and `EXPIRED`. | Security-sensitive metadata | EventPipeline / repository | **Yes.** It is required for idempotency, terminal-state handling, and resume. | Active records remain until terminal handling. Terminal retention is **PROPOSED — REQUIRES APPROVAL**. | `EXPIRED` is a real terminal state and must not be rewritten as `FAILED_FINAL`. Delete only through the approved lifecycle cleanup. | Exclude by default; do not restore state without the matching event and integrity-checked evidence references. | Database protection and atomic state updates are required. |
| `created_at_epoch_ms` | Establishes event creation time and supports ordering/history. | Security-sensitive metadata | Event creation clock | **Yes.** | Same as event history; do not retain beyond the event. | Delete with the event. | Exclude by default pending approval. | Same as structured event metadata. |
| `updated_at_epoch_ms` | Records the latest persisted state change and supports recovery diagnostics. | Security-sensitive metadata | Repository transaction clock | **Yes.** | Same as event history. | Delete with the event. | Exclude by default pending approval. | Same as structured event metadata. |
| `resume_stage` | Records the last successfully persisted pipeline stage needed to resume after interruption. It must represent the coordinator’s stage boundary rather than an arbitrary implementation detail. | Security-sensitive metadata | EventPipeline / repository | **Yes** for persistent processing. The current candidate schema has `resume_stage`; the in-memory domain currently encodes deferred stage as bounded metadata. | Only while the event can be resumed or while approved history requires the state. | Clear with the event. A missing or invalid value causes safe recovery handling, not restart from the beginning by assumption. | Exclude by default; never restore it without the corresponding event state. | Database protection and atomic update with the state are required. |
| `expires_at_epoch_ms` | Stores the deadline for the event’s per-event/per-Guard expiration policy. A null value means no deadline has been assigned by an approved policy. | Security-sensitive metadata | EventExpirationPolicy / policy engine | **Yes** when a deadline exists; nullability is required because the current candidate permits it. | Keep only while the event is active or until the approved terminal cleanup. | On or after the deadline, the coordinator must persist `EXPIRED` through the repository. Do not silently delete an active record at expiry. | Exclude by default pending approval. | Database protection and atomic comparison/update are required. |
| bounded resume metadata | Carries only the approved resume discriminator needed to distinguish evidence and delivery resumption. It must not be a free-form map in persistent storage. | Security-sensitive metadata | EventPipeline | **Conditionally.** Persist only as normalized columns or an allowlisted representation. The current `metadata` map is an in-memory contract and is not authorization to persist arbitrary keys. | Same as `resume_stage`. | Delete with the event. | Exclude by default. | Same as structured event metadata. |

### 4.2 Evidence references and encrypted evidence files

| Field or document | Purpose | Sensitivity | Source | Persistence required? | Proposed retention requirement | Proposed deletion behavior | Proposed backup behavior | Proposed encryption requirement |
|---|---|---|---|---|---|---|---|---|
| `evidence_id` | Stable identity for one evidence item and the join key between metadata and the encrypted file. | Security-sensitive metadata | Evidence stage | **Yes** when evidence is approved and captured. | No longer than the parent event’s approved evidence retention. Exact duration is **PROPOSED — REQUIRES APPROVAL**. | Delete the encrypted file and reference as one lifecycle operation where possible. If one side is missing, quarantine or delete according to the approved orphan policy and surface the mismatch. | **PROPOSED — REQUIRES APPROVAL:** exclude encrypted evidence and references from Android backup and device transfer. | Reference is protected as database data. The evidence content requires an approved authenticated-encryption profile; unrestricted raw blobs are prohibited. |
| `event_id` on the evidence reference | Associates evidence with the canonical event. | Security-sensitive metadata | Evidence stage | **Yes** for a persisted reference. Foreign-key relationship and cascade policy require approval. | Same as parent event/evidence. | Delete the reference with the parent event and clean the file; do not leave silent orphan records. | Exclude by default. | Database protection and relationship integrity are required. |
| `kind` | Identifies the approved evidence category, such as a future Guard-specific minimum type. | Sensitive metadata | Evidence policy / evidence stage | **Yes** for an approved evidence item. Use a constrained enum. Do not use a raw MIME/type string as a proxy for permission. | Same as the evidence item. | Delete with the evidence item. | Exclude by default. | Database protection; content is separately encrypted. |
| `content_reference` | Points to the encrypted file without embedding the file content in Room. | Security-sensitive metadata; may reveal file layout | Evidence-file adapter | **Yes** only while the encrypted file exists or the record is in approved quarantine handling. | Same as evidence retention; never retain a dangling reference after cleanup completes. | If the file is absent, mark the record for recovery/quarantine and do not claim the evidence is available. | Exclude by default. | Protect the reference in the database; do not expose predictable paths or sensitive names. |
| `captured_at_epoch_ms` | Records when approved evidence was captured. | Sensitive metadata | Evidence stage / device clock | **Conditionally.** Persist only if needed for event history, expiry, or user-visible audit. | Same as evidence retention. | Delete with the evidence item. | Exclude by default. | Database protection; no additional location metadata is implied. |
| encrypted evidence file | Holds the minimum Guard-relevant evidence after confirmation. | **Sensitive evidence** | Approved evidence adapter and Android capability | **Conditionally.** No file is created unless a separate Guard policy authorizes the item. | The shortest approved duration that satisfies the product purpose; exact duration requires owner/privacy approval. No indefinite retention default. | Use the approved file lifecycle: temporary file, close/sync where supported, atomic rename, authenticated read, then deletion or quarantine. Invalid authentication, truncation, unsupported version, or unexpected metadata must fail closed. | Exclude from Android backup and device transfer by default. Do not assume reinstall or restore can recover it. | **PROPOSED — REQUIRES APPROVAL:** authenticated encryption using the reviewed candidate profile only after crypto and Keystore approval. No raw Room blob and no plaintext fallback. |

### 4.3 Delivery and local-response records

| Field or document | Purpose | Sensitivity | Source | Persistence required? | Proposed retention requirement | Proposed deletion behavior | Proposed backup behavior | Proposed encryption requirement |
|---|---|---|---|---|---|---|---|---|
| `attempt_id` | Uniquely identifies a delivery attempt for diagnostics and idempotent adapter behavior. | Security-sensitive metadata | Delivery stage / repository | **Conditionally.** Persist only if a delivery channel is enabled and audit/history requires attempt records. | No longer than the parent event’s approved history. | Delete with the event; do not retain failed delivery details indefinitely. | Exclude by default. | Database protection and uniqueness are required. |
| `event_id` on delivery attempt | Associates an attempt with the canonical event. | Security-sensitive metadata | Delivery stage | **Conditionally**, with the attempt record. | Same as parent event. | Delete with the attempt and parent event according to lifecycle order. | Exclude by default. | Database protection and foreign-key integrity are required. |
| `channel` | Identifies the approved local or future delivery channel. External providers are deferred. | Security-sensitive metadata | Delivery policy | **Conditionally.** Use a constrained enum; do not persist provider credentials or arbitrary endpoints. | Same as delivery history. | Delete with the attempt. | Exclude by default. | Database protection. |
| `status` | Records `SUCCESS`, `FAILED`, `DEFERRED`, or `SKIPPED` delivery outcome. | Security-sensitive metadata | Delivery stage | **Conditionally.** | Same as delivery history. | Delete with the attempt. | Exclude by default. | Database protection and atomic association with the event. |
| `occurred_at_epoch_ms` | Records when the delivery attempt occurred. | Security-sensitive metadata | Delivery stage clock | **Conditionally.** | Same as delivery history. | Delete with the attempt. | Exclude by default. | Database protection. |
| `failure_reason` | Carries a bounded, non-secret diagnostic category. It must not contain message bodies, credentials, tokens, stack traces with personal data, or arbitrary provider responses. | Security-sensitive metadata | Delivery adapter | **Conditionally.** Store only an allowlisted reason code or tightly bounded diagnostic. | Same as delivery history; shorter retention is preferred. | Delete with the attempt. | Exclude by default. | Database protection; never store secrets in this field. |

### 4.4 Configuration, preferences, and monitoring state

| Field or document | Purpose | Sensitivity | Source | Persistence required? | Proposed retention requirement | Proposed deletion behavior | Proposed backup behavior | Proposed encryption requirement |
|---|---|---|---|---|---|---|---|---|
| feature enablement | Enables or disables an approved Guard or feature. | Configuration; may reveal enabled protections | User settings | **Yes** only for settings that must survive process restart. | While the setting is active or until user reset. | Remove on manual reset; defaults must not enable unapproved capture. | **PROPOSED — REQUIRES APPROVAL:** preferences may be backed up only if they contain no security-sensitive state and restore cannot silently enable risky behavior. | Use DataStore as the intended store; protect through platform storage. No secret values. |
| display/configuration preferences | Stores user-visible presentation choices needed to operate the app. | Low-to-moderate configuration sensitivity | User settings | **Yes** only when needed across restarts. | While configured; no separate long-term history. | Remove on manual reset or uninstall. | Backup may be considered only for non-sensitive preferences after approval. | DataStore platform protection; no evidence or keys. |
| approved Guard policy parameters | Stores explicitly approved configuration such as a policy selection or enabled Guard. | Security-sensitive configuration | Product policy / user settings | **Conditionally.** Persist only the approved, bounded values needed to reproduce behavior. | While the policy is active. | Remove or reset on manual reset; do not silently preserve a policy after an incompatible migration. | Exclude by default if restoration could change security behavior. | DataStore platform protection; integrity and validation required. |
| Guard baseline / monitoring state | Stores only the minimum state needed to operate an approved Guard, such as a bounded baseline or last-known operational marker. | Security-sensitive metadata; may reveal behavior patterns | Guard adapter | **Conditionally.** No continuous raw sensor stream is stored. | Retain only while needed by the active Guard; reset when the Guard is disabled or on manual reset. | Delete/reset with the Guard. | Exclude by default; device transfer is not assumed safe. | DataStore or approved structured storage; no raw camera, audio, location, or sensor stream. |
| credentials, PINs, tokens, or provider secrets | Attempted configuration or authentication material. | Highly sensitive secret | User/provider/authentication layer | **No** in this inventory. Use Android-approved credential/key mechanisms where separately approved. | None. | Never persist in Room, DataStore, evidence files, logs, or delivery diagnostics. | Never back up through SHIELDRA. | Not applicable; absence is the control. |

### 4.5 Keys, temporary data, exports, and backups

| Field or document | Purpose | Sensitivity | Source | Persistence required? | Proposed retention requirement | Proposed deletion behavior | Proposed backup behavior | Proposed encryption requirement |
|---|---|---|---|---|---|---|---|---|
| Android Keystore key material | Encrypts/decrypts approved evidence or other approved protected payloads. | **Critical secret material** | Android Keystore | **Yes only in the Keystore**, subject to an approved alias/lifecycle policy. Key bytes must never be copied into application storage. | Until approved rotation, retirement, invalidation, or uninstall behavior applies. Exact lifecycle requires approval. | Never delete an old key before successful re-encryption and durable verification. If permanently unavailable, fail closed and do not claim recovery. | Do not export or back up key bytes. Do not assume a restored database has a usable key. | **PROPOSED — REQUIRES APPROVAL:** Android Keystore protection with no software-key fallback. Hardware-backed preference and device behavior remain unverified. |
| key alias | Selects the Keystore key without revealing key material. | Security-sensitive metadata | Storage adapter / approved key registry | **Conditionally.** Persist only as a bounded, namespaced identifier required to locate the key. | While encrypted payloads depend on it and during approved rotation recovery. | Remove only after all dependent payloads and the approved retirement process permit it. | Exclude by default; never restore an alias as proof that the key exists. | Protect as security-sensitive metadata; the alias is not a substitute for the key. |
| temporary plaintext buffer | Allows an approved operation to construct or process a payload before encryption. | Sensitive transient data | Evidence/encryption adapter | **No durable persistence.** In-memory use only where technically required. | Operation lifetime only. | Clear references and delete any temporary file on success, failure, cancellation, or process interruption where supported. | Never back up. | Keep out of logs; encrypted file creation must use a temporary-file and atomic-rename design where supported. |
| temporary encrypted file | Prevents a partially written final evidence file from being treated as complete. | Sensitive evidence | Evidence-file adapter | **Transient only.** | Until successful close/sync and atomic rename, or until cleanup/quarantine handles failure. | Delete or quarantine after a failed write; never promote an incomplete file. | Never back up. | Use the approved authenticated envelope; do not treat a filename or extension as integrity evidence. |
| orphan/quarantine record | Allows safe detection and handling of a database reference without a file, or a file without a reference. | Security-sensitive metadata | Storage integrity scanner | **Conditionally**, only for the shortest cleanup/review period approved for recovery. | Exact review/cleanup interval requires approval; no indefinite quarantine. | Delete after verified cleanup or approved manual disposition. | Exclude by default. | Protect as structured metadata; quarantined content remains encrypted and inaccessible to normal processing. |
| exported data package | Provides an explicit user- or administrator-authorized export if export is later approved. | Potentially sensitive or highly sensitive | Export operation | **No by default.** Export is prohibited until authority, redaction, audit, and destination policy are approved. | If later approved, use the shortest purpose-bound duration and require explicit deletion. | Delete after transfer or cancellation; no automatic export cache. | Never include in Android backup by default. | Require a separately approved export protection and redaction model; do not export keys. |
| Android backup/restore contents | A platform-controlled copy that may contain app data if configuration permits. | Potentially security-sensitive | Android backup subsystem | **No SHIELDRA authorization by default.** | No SHIELDRA retention claim; platform behavior requires verification. | Configure exclusion of security database and evidence-file domains as the current candidate does; verify on supported devices. | **PROPOSED — REQUIRES APPROVAL:** exclude Room database, encrypted evidence, key aliases that imply recoverability, and temporary files. Non-sensitive preferences require a separate decision. | Do not infer recoverability from backup presence. Database metadata and evidence/key material must be treated as unavailable unless restored together and verified. |

## 5. Data that must not be stored

The following data **MUST NOT** be stored in Room, DataStore, evidence files, delivery diagnostics, logs, exports, or backups unless a new, explicit security/privacy decision supersedes this proposal:

1. Continuous camera, microphone, audio, or location capture, including raw streams and background snapshots.
2. Location histories, precise coordinates, address books, contacts, messages, call logs, unrelated application data, or web-browsing data.
3. Raw sensor streams, continuous battery telemetry, or SIM/subscription history. A Guard may retain only the minimum derived fact needed for an approved confirmed event or bounded operational state.
4. Evidence collected from a Signal before event confirmation, or evidence unrelated to the confirmed Guard.
5. Authentication secrets, PINs, passwords, access tokens, provider credentials, private keys, Keystore key bytes, or recovery secrets.
6. Arbitrary event metadata maps, unbounded user text, message bodies, raw provider responses, stack traces containing personal data, or unrestricted exception payloads.
7. Plaintext sensitive evidence, raw evidence blobs in Room, predictable sensitive filenames, or path names containing EventId plus personal/location content.
8. Data retained solely for analytics, advertising, profiling, cloud synchronization, billing, or external-provider purposes. Those services are deferred from the initial device-first scope.
9. A duplicate record for the same `EventId`, a second sequence identity used only to bypass idempotency, or a replayed delivery that appears to be a new event.
10. A backup or export copy that implies encrypted evidence is recoverable when the matching Keystore key is unavailable.

## 6. Invariants and processing alignment

The future persistent adapter must preserve these invariants. The first seven are approved Batch 3 architectural decisions; the storage-specific values in this section remain proposed until G4 approval.

1. **Canonical identity:** `EventId` is the only canonical event identity. It is the primary key. A retry, duplicate input, or resumed operation must address the existing row.
2. **Idempotency:** Inserting an existing `EventId` must not create a second event. A repeated processing request must not create duplicate evidence references or duplicate final delivery solely because it was retried.
3. **Explicit expiration:** `EXPIRED` is a real lifecycle state and is distinct from `FAILED_FINAL`. When `expires_at_epoch_ms` is reached, the coordinator asks the repository to atomically transition the current state to `EXPIRED`. A conflicting update fails closed.
4. **Per-event/per-Guard expiry:** Expiration is derived from the approved `EventExpirationPolicy`. Exact durations are not invented here. A persisted deadline must be tied to the event and, where required by policy, to a constrained Guard type.
5. **Ownership:** The coordinator selects `Confirmed → Evidence → Delivery → Completed` stages and decides what to do next. The repository performs atomic persistence and reports conflicts. The coordinator does not depend on Room details.
6. **Resume:** Processing resumes from the last successfully persisted state/stage. Missing, invalid, or conflicting resume data must not cause an automatic restart from the beginning or duplicate delivery.
7. **Sequential processing:** Processing is serialized per `EventId`. Concurrent attempts for different events may proceed independently where safe. Persistent locking or transaction behavior must preserve the same rule after process death.
8. **Bounded state:** Enum/state values, channels, evidence kinds, and diagnostic reasons are constrained. Arbitrary strings are not accepted as a minimization substitute.
9. **Reference integrity:** An evidence reference is not evidence. Normal processing may use only a reference whose encrypted file exists, parses, and authenticates under the approved key. A missing or unauthenticated file is not silently ignored.
10. **No sensitive Room blobs:** Room stores structured state and minimal references. Sensitive evidence remains in the encrypted-file layer unless a later approval explicitly changes this boundary.
11. **No false recovery:** A restored database without its encrypted files, or encrypted files without their database metadata and usable Keystore key, is an incomplete set. The application must report unavailable data and quarantine or clean up according to the approved policy.
12. **Terminal states:** `DELIVERED`, `FAILED_FINAL`, and `EXPIRED` do not receive ordinary new processing. Retention cleanup is separate from pipeline transition and must not mutate terminal meaning.

## 7. Failure behavior

The proposed failure behavior is conservative and requires approval before production implementation.

- **Duplicate `EventId`:** Return an idempotent existing-event result or a repository conflict. Do not create a second row, evidence reference, or final delivery.
- **Concurrent processing of one `EventId`:** Serialize per event. If a persistent transaction cannot acquire the required ownership, return a conflict and leave the last durable state unchanged.
- **State update failure:** Do not report stage completion. Preserve the previous durable state when possible and return a failure requiring retry or recovery.
- **Process death before persistence:** On restart, process from the last durable state. Work that was only in memory is not treated as completed.
- **Process death after persistence:** Re-read the durable state and treat the persisted transition as authoritative. Do not replay a completed stage merely because the caller did not receive its response.
- **Expiration during processing:** Re-evaluate the per-event deadline at the coordinator/repository boundary. If expiration wins the atomic update, persist `EXPIRED`; do not report delivery or completion for a state that was not durably committed.
- **Missing evidence file:** Mark the reference as unavailable for recovery handling, do not pass it to delivery as valid evidence, and quarantine/delete according to the approved orphan policy.
- **Truncated file, invalid envelope, unsupported version, or invalid authentication tag:** Fail closed. Do not return plaintext, silently repair, or accept the file as evidence. Quarantine or delete only under the approved lifecycle policy.
- **Database corruption or invalid relationship:** Refuse normal processing for affected records, preserve recoverable data where possible, and enter an explicit recovery outcome. Do not use `fallbackToDestructiveMigration` or silently discard security history.
- **Missing or invalid Keystore key:** Return an unavailable-key failure. Do not generate an unrelated replacement key and claim that old evidence was recovered. Any replacement and re-encryption path requires the separate key-lifecycle decision.
- **Interrupted cleanup:** Cleanup must be repeatable and idempotent. A later scan must identify references without files, files without references, and temporary files without promoting them into valid evidence.
- **Unapproved export request:** Refuse export. No cached package or temporary plaintext export may remain.

## 8. Implementation evidence

The repository contains candidate evidence, not approval evidence:

| Evidence | Finding and boundary |
|---|---|
| `app/src/main/kotlin/com/shieldra/storage/StorageSchema.kt` | Defines a version-1 SQL contract for `security_events`, `event_evidence_refs`, and `event_delivery_attempts`. It includes `event_id` as the event primary key, state and expiry indexes, `resume_stage`, nullable `expires_at_epoch_ms`, and foreign keys from child records. It has an empty explicit migration registry. It is not a Room adapter and does not establish approved retention, deletion, backup, or corruption behavior. |
| `app/src/main/kotlin/com/shieldra/storage/EncryptionPolicy.kt` | Requires callers to supply policy values and has no default instance. It names the AES-GCM candidate parameters, but the authoritative security decision remains open. |
| `app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt` | Provides an Android Keystore-backed AES-GCM candidate with a random 12-byte IV, versioned envelope, optional associated data, and no software-key fallback. It is not wired into the application graph. Keystore and device behavior are unverified. |
| `app/src/main/kotlin/com/shieldra/domain/SecurityEventContracts.kt` | Defines canonical `EventId`, event type, nullable severity, evidence references, delivery information, and an in-memory metadata map. The map is not authorization to persist arbitrary metadata. |
| `app/src/main/kotlin/com/shieldra/domain/InMemoryEventPipeline.kt` | Demonstrates per-`EventId` in-memory locking, idempotent repository insertion, explicit `EXPIRED`, coordinator stage selection, repository state updates, and deferred-stage resume. It does not prove persistent crash consistency. |
| `app/src/main/kotlin/com/shieldra/domain/EventState.kt` | Defines the lifecycle states and allowed transitions, including `EXPIRED` as a distinct terminal state. |
| Local Batch 4c verification | Schema/policy/envelope tests, build, unit tests, lint, and debug assembly passed according to the gap review. These are deterministic local results, not Android runtime evidence and not G4 acceptance. |

The candidate schema does not yet map all domain values into approved Room entities and DAOs. In particular, the future adapter must resolve how the in-memory `metadata["deferredStage"]` becomes the candidate `resume_stage`, whether `guard_type` is required for expiration policy, and how delivery/evidence child records are atomically updated with the parent event. No coordinator redesign is authorized by this proposal.

## 9. Device evidence and verification requirements

**IMPLEMENTATION + DEVICE VERIFICATION REQUIRED.** The current environment has no confirmed ADB, emulator/AVD, or physical Android device. Therefore this document records **no passed device result** and makes no claim about Keystore, backup, file, or runtime behavior.

Before any platform security claim, an Android verification package must execute and record the supported API levels and device/OEM matrix. At minimum it must cover:

- Keystore AES-GCM key creation, lookup, encryption, decryption, alias validation, and missing-key behavior;
- hardware-backed and StrongBox availability reporting, without inventing a software fallback;
- authentication-required key behavior, biometric enrollment changes, device-credential changes, key invalidation, and re-enrollment;
- process death, force-stop, restart, interrupted stage persistence, duplicate `EventId`, concurrent same-event requests, resume, and expiration races;
- Room creation and migration, failed/interrupted migration, startup behavior, unsupported version, downgrade behavior, and database corruption;
- temporary-file write, close/sync, atomic rename, crash interruption, truncated envelope, invalid tag, unsupported payload version, missing file, and orphan cleanup;
- Android backup configuration, reinstall, device transfer, restore with database but no evidence, evidence but no database, and restored metadata with unavailable Keystore key; and
- manual reset, uninstall, and any approved export behavior.

Until these tests are executed, all platform conclusions remain **DEVICE/AVD VERIFICATION REQUIRED**. JVM tests cannot establish Android Keystore or backup behavior.

## 10. Unresolved approvals

The following approvals remain open. They must not be silently resolved by implementation convenience:

1. **Inventory and minimization:** approval of the field-by-field inventory, prohibited-data list, bounded metadata allowlist, and whether `guard_type` is required.
2. **Retention and deletion:** exact active, terminal, evidence, delivery, quarantine, temporary-file, and history durations; automatic cleanup schedule; manual reset; uninstall; and deletion verification.
3. **Export and user authority:** whether export is prohibited or permitted, who may authorize it, required redaction, audit trail, destination protection, and deletion.
4. **Backup, restore, and transfer:** confirmation that Room/database and evidence-file domains remain excluded; behavior on reinstall, device transfer, partial restore, missing key, and orphan cleanup; and whether any non-sensitive preferences may restore.
5. **Room schema and migration:** entity/DAO mapping, `guard_type`, metadata normalization, nullability, enum encoding, limits, relationship ownership, forward migration tests, downgrade behavior, startup failure, and rollback strategy.
6. **Cryptography and key lifecycle:** approval of AES-GCM parameters, associated-data format, payload encoding, provider, alias ownership/scope, authentication binding, rotation, re-encryption, retirement, invalidation, and permanent key loss behavior.
7. **Evidence-file policy:** paths, permissions, atomic-write protocol, file metadata minimization, quarantine, orphan detection, cleanup, and tamper handling.
8. **Pipeline/storage atomicity:** persistent transaction tests for duplicate IDs, state-update failures, process death before/after persistence, concurrent same-event processing, resume, and expiration.
9. **Platform and privacy review:** supported API/OEM matrix, device evidence, threat model, and independent security/privacy sign-off.

## 11. Gate status and acceptance criteria

**Current gate status: NOT ACCEPTED FOR CLOSURE.** This document closes no G4 requirement by itself. It supplies the missing G4-01 decision candidate and identifies dependencies for G4-02, G4-04, G4-05, G4-06, G4-12, G4-13, and G4-14.

G4 must remain:

> **G4 = APPROVED DIRECTION / DETAILS BLOCKED.**

G4 may not change to **APPROVED FOR PRODUCTION STORAGE** until this inventory and all dependent lifecycle, schema, migration, crypto, key, recovery, pipeline, runtime, threat-model, and security/privacy decisions are approved and evidenced. No statement in this document should be read as a production-readiness claim, a crash-consistency claim, a device-verification claim, or a claim that SHIELDRA is secure against a rooted or fully compromised device.

## References

[1]: ../../SHIELDRA_DECISIONS.md "SHIELDRA Internal Decisions"
[2]: ../PHASE4_DECISION_REGISTER.md "SHIELDRA Phase 4 Decision Register"
[3]: ../PHASE4_STORAGE_SECURITY_PROPOSAL.md "SHIELDRA Phase 4 Storage and Security Proposal"
[4]: ../reports/g4-security-acceptance-gap-review.md "G4 Security Acceptance Gap Review"
[5]: ../../app/src/main/kotlin/com/shieldra/storage/StorageSchema.kt "Storage schema candidate"
[6]: ../../app/src/main/kotlin/com/shieldra/storage/EncryptionPolicy.kt "Encryption policy candidate"
[7]: ../../app/src/main/kotlin/com/shieldra/storage/AndroidKeystoreEncryptor.kt "Android Keystore encryption candidate"
[8]: ../../app/src/main/kotlin/com/shieldra/domain/InMemoryEventPipeline.kt "Batch 3 in-memory EventPipeline"
[9]: ../../app/src/main/kotlin/com/shieldra/domain/EventState.kt "Event lifecycle state contract"
[10]: ../../app/src/main/kotlin/com/shieldra/domain/SecurityEventContracts.kt "Security event domain contract"

Document references: [1] [2] [3] [4] [5] [6] [7] [8] [9] [10]
