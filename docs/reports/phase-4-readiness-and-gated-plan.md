# Shieldra Phase 4 — Readiness and Gated Execution Plan

**Date:** 2026-09-21  
**Decision:** **Internal development is authorized; production platform/security implementation remains controlled and gated.**

## Executive decision

The repository explicitly separates internal development authority from historical external documents. The unified internal register now authorizes continued work without waiting for Product Freeze v5 or complete Phase 1 files. It does not authorize unresolved platform, evidence, cryptographic, provider, or release behavior. The current interfaces for detection, evidence, delivery, persistence, authentication, and platform behavior remain boundaries or marker contracts until their specific decisions are closed.

Therefore, implementing all Phase 4 features immediately would require inventing security thresholds, event semantics, persistence schemas, privacy rules, Android permission behavior, provider choices, retry policy, authentication, billing, and account behavior. That would risk false protection, false delivery, privacy violations, and unsafe security claims. No production Phase 4 code was added in this review.

## Parallel review coverage

Six independent workstream reviews plus a synthesis review covered:

| Workstream | Current decision |
|---|---|
| Protection state engine and event pipeline | Blocked until confirmation, lifecycle, idempotency, stage ownership, and error semantics are approved |
| Local persistence and settings | Blocked until typed settings, sensitivity, storage technology, backup, migration, and authentication policy are approved |
| Lock, Motion, SIM, Battery, and Panic guards | Blocked until per-guard triggers, thresholds, platform feasibility, permissions, lifecycle, and safety behavior are approved |
| Camera, location, evidence, encryption, and permissions | Blocked until threat model, consent, retention, crypto/key lifecycle, and Android platform decisions are approved |
| Notifications, delivery queue, providers, and retries | Blocked until channel/provider, payload, consent, receipt, queue, retry, credential, and background-execution decisions are approved |
| Authentication, billing, Premium, ads, and accounts | Blocked until identity, security, SKU/entitlement, ad/consent, account, backend, and release decisions are approved |

## Safe execution sequence

### Gate 0 — Complete the already-authorized Phase 3 command

Complete controlled Batches 5–9: accessibility, design-system review, every-screen state review, deferred-functionality honesty, and final integration. Maintain DemoData-only runtime behavior and honest deferred messages. Do not add Phase 4 permissions, runtime integrations, provider SDKs, persistence, background workers, or fake implementations.

### Gate 1 — Approved internal authority and product decision package

Use the committed internal decision package as the authority for this development cycle. Historical Product Freeze v5, complete Phase 1 Sections 1–43, and external review remain useful future inputs but are not a blocker to pure contract work. They remain required before final production/security claims if they are needed by the owner or release authority.

The decision package must resolve guard triggers and thresholds, signal corroboration/debounce/cooldown, panic semantics, event identity/idempotency, lifecycle including the conflict between the Group B `EXPIRED` mention and the Group C seven-state model, EventPipeline operations, evidence/privacy policy, authentication, Android permissions/background execution, persistence, providers, billing, ads, accounts, and release ownership.

### Batch 1 — Contract closure only

After Gate 1, update only pure domain contracts and deterministic tests. Define approved result/error types, event lifecycle, expiry, idempotency, pipeline stage ownership, guard signal outputs, evidence validation outcomes, repository boundaries, and authentication decisions. No Android APIs, network calls, persistence engine, provider SDK, camera, location, billing, or fake success.

### Batch 2 — Pure ProtectionStateEngine

Implement only the approved deterministic state/confirmation core with injected Clock and IdentityProvider. Add comprehensive tests for valid/invalid transitions, duplicate signals, concurrency decisions, expiry, and error semantics. Guards remain signal producers and cannot create confirmed events directly.

### Batch 3 — Pure EventPipeline coordinator

Implement only approved stage orchestration over approved abstractions. Define evidence validation, persistence transaction boundaries, deferred/resume/failure handling, cancellation, and idempotency without implementing capture, encryption, queue workers, network calls, or providers.

### Gate 2 — Security/platform readiness

Approve threat model, data classification, privacy/retention/deletion/export, AuthenticationGate enforcement, Keystore/key lifecycle, Room/DataStore choice, Android permission and background-execution policy, notification policy, provider contracts, credential ownership, and physical-device/emulator test matrix.

### Batch 4 — Isolated platform capabilities

Implement one approved platform capability at a time behind adapters: persistence, one guard, evidence capture, authentication, or delivery. Each capability requires its own permissions, failure tests, security review, device evidence, and stop-and-recheck gate. Camera/location, messaging, billing, and authentication must not be combined into one batch.

### Gate 3 — End-to-end release review

Require unit, integration, instrumentation, device/OEM, provider-callback, migration/recovery, permission-revocation, security, privacy, and release validation. Require explicit sign-off before claiming production protection, capture, authentication, notification, delivery, purchase, or entitlement behavior.

## Required decisions before production code

1. Product Freeze v5 and complete Phase 1 authority.
2. Per-guard requirements for Lock, Motion, SIM, Battery, and Panic.
3. Rules for converting signals into confirmed events.
4. Event identity, deduplication, replay protection, concurrency, expiry, and lifecycle.
5. EventPipeline stages, ownership, persistence boundaries, retries, resume, cancel, and failure results.
6. Camera/location capture triggers, precision/freshness rules, consent, and offline behavior.
7. Privacy, retention, deletion, export, backup, redaction, and audit-log rules.
8. Encryption algorithm/library, Keystore lifecycle, key invalidation, rotation, recovery, and authentication binding.
9. Android permissions, notification permission, foreground/background execution, Doze/OEM support, and supported device matrix.
10. Settings schema, defaults, sensitivity, authentication requirements, reset/clear semantics, migration, and DataStore versus Room.
11. Authentication provider, session, recovery, lockout, and SensitiveOperation policy.
12. Delivery providers, recipients, payload/evidence policy, credentials, receipts, retry taxonomy, queue behavior, and operational ownership.
13. Billing provider, lifetime SKU, price/currency, verification, restore, refunds, revocation, and entitlement source of truth.
14. Advertising provider, consent, regional policy, personalization, placements, and entitlement-based suppression.
15. Account/backend/cloud behavior, deletion/export, multi-device behavior, and data portability.
16. External security/privacy/product review and final release authority.

## Current status

- **Production Phase 4 implementation:** BLOCKED.
- **Safe work now:** complete Phase 3 Batches 5–9; document authority gaps; prepare threat-model questions; validate existing pure contracts; maintain static boundary checks; preserve honest deferred UI behavior.
- **Runtime claims:** UNKNOWN without an emulator or connected device.
- **No Phase 4 production files changed by this readiness review.**
