# Batch 2 — Pure ProtectionStateEngine

**Date:** 2026-09-22
**Status:** VERIFIED locally; ready for commit and remote verification.

## Objective

Implement the approved Guard confirmation semantics in a deterministic, framework-free `ProtectionStateEngine`. This batch deliberately stops before Android capabilities, evidence, storage, monitoring, notifications, or external services.

## Implemented behavior

- Lock: one failed PIN is `SIGNAL_ONLY`; two attempts within the approved two-minute window confirm a HIGH event. The approved ten-minute reset and five-minute same-sequence cooldown are represented.
- Motion: invalid/noisy sensor quality remains `SIGNAL_ONLY`; valid movement confirms when the approved approximately three-second persistence or two suspicious occurrences condition is met, with HIGH severity.
- SIM: the typed confirmed subscription/SIM-change observation immediately produces a HIGH event.
- Panic: explicit activation immediately produces a CRITICAL event without a confirmation delay.
- Battery: even an `abnormal` placeholder observation does not confirm an event because the abnormal power condition has not been defined and approved.
- Confirmed events receive the result severity and an injected identity; timestamps come from the signal observation while the injected clock controls engine time/cooldown evaluation.

## Boundary decisions preserved

No Android API, sensor listener, SIM provider, battery API, camera, location, Room, DataStore, Keystore, worker, service, notification, network, provider, or external dependency was added. No evidence or response policy was invented. Motion quality and typed observation data are contracts supplied by a future platform adapter; they do not claim that Android can provide them universally.

## Validation

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
```

Result: `BUILD SUCCESSFUL`; unit tests, lint, Debug assembly, Phase 3 static review, Group C boundary scan, and diff check passed. The changed domain package contains no Android or external implementation imports.

## Remaining decisions

Event idempotency across process death, cross-Guard deduplication, event lifecycle/expiry, EventPipeline stage ownership, persistence transaction boundaries, evidence policy, Android feasibility, and platform recovery remain outside this batch. They must be closed before implementing a pipeline or platform adapter.
