# Batch 1 — Pure Guard Contract Closure

**Date:** 2026-09-22
**Status:** VERIFIED locally; ready for commit and remote verification.

## Objective

Close the smallest safe part of the Phase 4 contract gap using only the approved Guard decisions. This batch adds typed, framework-free observations and confirmation result shapes. It does not implement a protection engine, Android adapter, sensor listener, persistence, evidence capture, monitoring, notification, network, or provider.

## Read and trace

The batch re-read and traced `DomainBoundaries.kt`, `Signal.kt`, `SecurityEventContracts.kt`, `EventState.kt`, `ErrorContracts.kt`, existing evidence/delivery/authentication contracts, domain tests, Gradle configuration, and static boundary scripts. The existing `ProtectionStateEngine` is only a boundary and `EventPipeline` remains a marker interface. No platform implementation was present.

## Implemented

- Added `GuardKind` and `GuardSeverity`.
- Added typed `GuardObservation` variants for failed PIN, motion quality/duration/repetition, SIM change, battery power state, and explicit Panic activation.
- Added `GuardSignal` and shape validation so an observation cannot silently masquerade as another Guard type.
- Added `GuardConfirmationStatus` with explicit `SIGNAL_ONLY`, `CONFIRMED`, and `NOT_A_SECURITY_EVENT` states.
- Added `GuardConfirmation` invariant requiring severity for confirmed results.
- Added only the approved numeric rule values: Lock two-minute confirmation window, ten-minute reset, five-minute cooldown, two Lock attempts, Motion approximately three seconds, ten-second repetition window, and two Motion occurrences.
- Explicitly kept Battery percentage/non-anomaly behavior outside confirmed security-event logic.
- Added deterministic unit tests for values, typed shape validation, invalid motion values, confirmation invariants, and battery non-event representation.

## Security-sensitive decisions

**Approved:** the values and Guard semantics supplied in the approved Guard update.

**Proposed:** none implemented.

**Deferred/unknown:** Android observability, sensor sampling/calibration, SIM baseline/API feasibility, Battery anomaly definition, response/evidence policy, event identity/idempotency implementation, lifecycle expiry, and all platform/storage/cryptographic details.

## Validation

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
```

Result: `BUILD SUCCESSFUL`; unit tests, lint, Debug assembly, Phase 3 static review, Group C boundary scan, and diff check passed. The changed domain files contain no Android, Compose, storage, network, camera, location, worker, or provider imports.

## Scope boundary

This is contract closure, not engine implementation. The next dependent batch may implement a pure `ProtectionStateEngine` only after tracing these contracts and preserving unresolved platform/evidence policies. EventPipeline stage ownership and idempotency remain open and are not implemented here.
