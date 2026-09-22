# Phase 4 Contract Readiness Preflight

**Date:** 2026-09-22  
**Result:** PREPARED for internal pure-contract work / CONTROLLED for production implementation.
**Scope:** Safe Phase 4 preparation only; no production runtime behavior added.

## Executive decision

The current repository contains pure boundaries and marker contracts for protection state, event pipeline, guards, evidence, delivery, repositories, platform execution, and authentication. The unified internal decision register now authorizes framework-free contract closure for the approved Guard rules. It does not authorize Android capabilities, persistence engines, cryptography, evidence capture, background workers, notifications, providers, or final security claims.

No camera, location, sensor, persistence, background worker, encryption, authentication, notification, delivery, billing, account, or network implementation was added in this preflight.

## Proposed execution order

| Order | Work package | Entry requirement | Safe output |
|---|---|---|---|
| 0 | Authority and decision closure | Product Freeze v5, complete Phase 1, official Phase 4 specification, review authorization | Approved decision register and versioned requirements |
| 1 | Pure contract closure | Approved event states, signals, errors, idempotency, evidence and delivery result types | Framework-free interfaces and deterministic contract tests |
| 2 | Pure ProtectionStateEngine | Approved trigger, corroboration, lifecycle, expiry, duplicate, and concurrency rules | Injected-clock deterministic state engine and tests |
| 3 | Pure EventPipeline coordinator | Approved stage ownership, transaction boundaries, cancellation, retry, resume, and failure semantics | Adapter-only coordinator and deterministic orchestration tests |
| 4 | Security/platform readiness | Threat model, data classification, privacy, Keystore, storage, Android matrix, permissions, background policy | Approved adapters and device test plan |
| 5 | Isolated platform capabilities | One approved capability plus permissions, failure cases, and device evidence | One adapter at a time: storage, guard, evidence, auth, or delivery |
| 6 | End-to-end release review | Security/privacy sign-off and device/provider/migration/recovery evidence | Release decision; no security claim before sign-off |

## Contract findings

`ProtectionStateEngine` currently exposes `createConfirmedEvent` but does not define confirmation rules, invalid transitions, duplicate handling, expiry, concurrency, or error results. `EventPipeline` is currently a marker interface and therefore cannot authorize stage behavior.

`Signal` defines signal type, strength, timestamp, and a string context, but the repository does not define how signals are corroborated, deduplicated, authenticated, or converted into confirmed events. The existing event states and delivery contracts do not resolve the required lifecycle, retry, receipt-proof, and persistence semantics.

Repository, platform, evidence, delivery, and authentication boundaries are intentionally abstract. Selecting Room, DataStore, WorkManager, a foreground service, CameraX, a location provider, Keystore policy, a biometric model, or an external provider would be a material product/security decision and is not safe to infer from an interface name.

## Required blockers before Order 1

The following remain required inputs, not implementation tasks:

1. The internal decision register is authoritative for current pure-contract work; historical Product Freeze v5, complete Phase 1 Sections 1–43, and any later product authority remain release/review inputs where required.
2. Per-guard triggers, thresholds, corroboration, debounce, cooldown, cancellation, lifecycle, and failure UX.
3. Authoritative event state list, terminal states, expiry, idempotency, replay protection, and concurrency rules.
4. Evidence consent, precision, retention, deletion, export, redaction, encryption, and backup policy.
5. Authentication, storage, background execution, notification, delivery, retry, billing, ads, account, and provider decisions.
6. Threat model, privacy review, device/OEM matrix, offline behavior, compromised-device assumptions, rollback, and release sign-off.

## Validation

The Phase 3 final matrix remains green: clean unit tests, lint, Debug and Release assembly, Phase 3 static review, Group C boundary scan, and `git diff --check`. The existing static boundary checks continue to reject production camera/location/network/persistence/billing patterns.

## Next action

Keep Phase 4 platform/security production implementation controlled. Begin Order 1 with pure contracts and deterministic tests only, using the approved internal Guard rules and preserving every unresolved item as open.

## References

[1]: ../architecture/phase-4-decision-register.md "Phase 4 decision register"
[2]: ../reports/phase-4-readiness-and-gated-plan.md "Phase 4 readiness and gated plan"
[3]: ../ROADMAP.md "Shieldra roadmap"
