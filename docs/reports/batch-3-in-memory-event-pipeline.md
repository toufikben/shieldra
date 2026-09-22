# Batch 3 — In-Memory EventPipeline

**Date:** 2026-09-22
**Status:** Locally verified; ready for commit and remote verification.

## Approved input

The supplied Batch 3 update approved seven decisions: canonical `EventId` idempotency, explicit `EXPIRED`, coordinator/repository ownership split, resume from persisted stage, per-event expiration policy, sequential processing per event with duplicate prevention, and a local/in-memory first implementation that can later be replaced by Room.

## Read and trace

The batch re-read `DomainBoundaries.kt`, `EventState.kt`, `SecurityEventContracts.kt`, `RepositoryContracts.kt`, the existing ProtectionStateEngine, the prior EventPipeline design review, and all relevant usages. Before this batch, EventPipeline and EventRepository were empty marker interfaces and `EXPIRED` did not exist.

## Implemented architecture

- Added explicit `EXPIRED` lifecycle state and valid transition rules.
- Added `EventRepository` with atomic state-update and conflict results.
- Added `InMemoryEventRepository` with synchronized updates and duplicate-save protection.
- Added `EventPipeline` process contract, typed stage/failure/result models, and per-event expiration policy.
- Added `InMemoryEventPipeline` where the coordinator owns Confirmed → Evidence → Delivery → Completed orchestration and interacts only with repository/stage abstractions.
- Added injected evidence and delivery stages with completed/deferred/failed outcomes.
- Added persisted deferred-stage metadata so recovery resumes Evidence or Delivery rather than restarting blindly.
- Added per-EventId locks to prevent duplicate concurrent processing while allowing independent event keys to use independent locks.
- Kept Room, DataStore, Android services, camera, location, encryption, network, providers, billing, ads, and real evidence/delivery outside the batch.

## Tests

Added deterministic coverage for same-ID idempotency, duplicate save protection, explicit expiry, deferred delivery resume, evidence terminal failure, and independent event state. Existing full unit tests, lint, assembly, Phase 3 static review, Group C boundary scan, and diff check were also run.

## Validation result

`BUILD SUCCESSFUL`; all requested local checks passed after fixing one compile-time type mismatch in the internal state-update helper. The domain package contains no Android or external implementation dependencies.

## Remaining blockers

The implementation is local and non-persistent by design. Room schema/migrations, crash durability beyond the in-memory process, evidence validation/capture, encryption/Keystore, Android execution limits, notifications, real delivery, provider contracts, and production security/release review remain gated.
