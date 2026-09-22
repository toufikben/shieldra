# Batch 0 — Unified Decision Reconciliation

**Date:** 2026-09-22
**Status:** Documentation and planning batch; verified locally before commit.

## Objective

Reconcile the two supplied SHIELDRA prompts into one operating specification, treat the approved Guard update as authoritative, preserve unresolved security/platform choices as open, and create a dependency-aware sequential execution plan.

## Sources read

- `/home/ubuntu/upload/pasted_content.txt` — Master Engineering & Product Execution Protocol, read through line 2244.
- `/home/ubuntu/upload/pasted_content_2.txt` — Approved Guard Decisions Update, read through line 89.
- Current repository roadmap, authority index, Phase 4 registers/readiness reports, domain contracts, build configuration, tests, CI, and recent commits.

## Reconciled decisions

The latest Guard update overrides earlier example or proposal values for Lock, Motion, SIM, Battery, Panic, evidence collection, cooldown principles, and monitoring. The master protocol remains authoritative for the batch cycle, no-invention rule, device-first direction, Room/DataStore/encrypted-evidence direction, external-service deferral, review process, and required documentation.

Approved rules are: two consecutive wrong PIN attempts within approximately two minutes confirm a Lock event, with a ten-minute counter reset and five-minute same-sequence cooldown; Motion confirms after the approved persistence/repetition rule with sensor-quality validation; confirmed SIM/subscription change is immediate; battery percentage alone is never a security event; explicit Panic activation is immediate. No continuous camera/location capture is allowed, and evidence begins only after a confirmed event for minimum relevant data.

## Files created or updated

- `SHIELDRA_DECISIONS.md`
- `docs/PHASE4_DECISION_REGISTER.md`
- `docs/PHASE4_GUARD_RULE_PROPOSAL.md`
- `docs/PHASE4_ANDROID_CAPABILITY_MATRIX.md`
- `docs/PHASE4_EVIDENCE_MATRIX.md`
- `docs/PHASE4_STORAGE_SECURITY_PROPOSAL.md`
- `docs/ROADMAP.md`
- `docs/authority/authority-index.md`
- `docs/architecture/phase-4-decision-register.md`
- `docs/reports/phase-4-readiness-and-gated-plan.md`
- `docs/reports/phase-4-contract-readiness-preflight.md`
- `README.md`

## Current repository findings

The repository is clean at the starting commit `ef66c9458da6e4db69894333607b22074fef3472`, with the same remote SHA. Phase 3 is complete for source/build/static scope, while runtime device verification remains unknown. Domain contracts exist but `ProtectionStateEngine` is only a boundary and `EventPipeline` is still a marker interface. No Room, DataStore, Keystore, camera, location, sensor, worker, notification, network, provider, or billing implementation is present.

## Dependency-aware plan

1. **Batch 0:** this documentation and authority reconciliation.
2. **Batch 1:** pure contract closure and deterministic result/error/lifecycle/idempotency tests; no Android or persistence APIs.
3. **Batch 2:** pure `ProtectionStateEngine` using injected clock and identity, implementing only approved Guard semantics; Battery remains non-event until an anomaly is approved.
4. **Batch 3:** pure `EventPipeline` coordinator over abstractions, after stage ownership and failure/idempotency semantics are closed.
5. **Capability investigations:** Android API/device matrix before any platform adapter.
6. **Isolated platform batches:** one storage, Guard, evidence, authentication, monitoring, or notification capability at a time, each with its own review and device evidence.
7. **Final security/release review:** only after implementation evidence exists.

Dependent security areas are intentionally sequential. Documentation and independent UI work may be parallelized later only when file ownership and dependency risk are clear.

## Validation

- Required decision and matrix files exist and are non-empty.
- Current roadmap points to the latest commit and next Batch 1.
- `git diff --check` passed after removing one documentation trailing-space defect.
- No production security behavior was added in this batch.

## Remaining issues

Open decisions include Android observability and OEM support, exact evidence permissions/retention/deletion/export, storage schema/migrations, cryptographic algorithm and Keystore lifecycle, background strategy, notification policy, authentication details, and final audit/release claims. These remain clearly labeled `UNKNOWN`, `PROPOSED — NOT APPROVED`, `DEFERRED`, or `BLOCKED`.
