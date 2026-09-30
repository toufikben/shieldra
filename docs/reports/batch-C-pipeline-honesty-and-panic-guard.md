# Batch C — Pipeline honesty and Panic Guard end-to-end

## BATCH
`C-pipeline-honesty-and-panic-guard` (protocol **Batch C**) — the owner's directive to remove
the fabricated-success path from `InMemoryEventPipeline` and to implement a Panic Guard that
persists exactly one confirmed event per explicit activation, binds every repeat press to it
while non-terminal, and records nothing that did not really happen.

## SCOPE
- **§15 honest pipeline.** A stage call alone no longer buys progress: an event can only reach
  READY when an `EvidenceReference` row is verifiably in the repository, and DELIVERED when a
  SUCCESS `DeliveryInformation` row exists. An unverified claim defers the event instead of
  advancing it. `RoomEventRepository` now exposes `recordEvidence()` and `recordDeliveryAttempt()`,
  and refuses arbitrary metadata keys it cannot persist rather than silently dropping them.
- **Panic Guard.** `PanicIncidentCoordinator` mints one real `EventId`, confirms the event via
  `ProtectionStateEngine`, persists it before any response work, and keeps an in-session
  `activationCount` / `lastActivatedAt`. Repeats bind to the same non-terminal event; a terminal
  event starts a new incident. No attempt counter, confirmation delay, cooldown, or time window.
- **Honest projection.** The dashboard history labels each state with what it actually did
  (`status_recorded`, `status_collecting`, …) so INITIATED never reads as "Sending" and COLLECTING
  never reads as "Ready".
- **Static enforcement** of the artifact-verification rule, the panic wiring, and the metadata
  fail-closed boundary in `.github/scripts/g4b-review-check.sh`.

Out of scope, deliberately: a runtime storage install gate flip (§15 — Batch D), a UI affordance
for manual rotation, and a durable persistence layer for the activation count (Q-C9).

## FILES CHANGED
- app/src/main/kotlin/com/shieldra/domain/EventPipelineContracts.kt (+2 methods)
- app/src/main/kotlin/com/shieldra/domain/InMemoryEventPipeline.kt (artifact checks + defer)
- app/src/main/kotlin/com/shieldra/domain/EventState.kt (`isTerminal()`)
- app/src/main/kotlin/com/shieldra/domain/PanicIncidentCoordinator.kt (new — 104 lines)
- app/src/main/kotlin/com/shieldra/storage/room/RoomEventRepository.kt (recordEvidence, recordDeliveryAttempt, metadata rejection)
- app/src/main/kotlin/com/shieldra/app/presentation/guard/PersistedEventReader.kt (`eventStateLabelRes`)
- app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt (wired coordinator + honest labels)
- app/src/main/res/values/strings.xml + values-ar/strings.xml (8 state-label strings)
- .github/scripts/g4b-review-check.sh (+8 new gates)
- app/src/test/kotlin/com/shieldra/domain/InMemoryEventPipelineTest.kt (+5 honesty tests)
- app/src/test/kotlin/com/shieldra/domain/PanicIncidentCoordinatorTest.kt (new — 10 tests)
- app/src/test/kotlin/com/shieldra/app/presentation/guard/PersistedEventReaderTest.kt (+3 honesty tests)
- app/src/androidTest/kotlin/com/shieldra/storage/room/RoomEventRepositoryInstrumentedTest.kt (+3 device tests)
- app/src/androidTest/kotlin/com/shieldra/storage/room/RoomPanicPersistenceInstrumentedTest.kt (new — 3 device tests)

## DECISIONS ENFORCED
§6 (fail closed — unpersistable metadata is rejected, not dropped), §15 (no silent policy change —
the storage gate stays off), §16 (real notifications require persisted delivery rows), and the
owner's Batch C directive itself: no phantom success, no invented counter/delay/cooldown/time
window, persist-first Panic, lifecycle-bound incident identity.

## TESTS
CI `clean test lint assembleDebug assembleDebugAndroidTest` on run **36655321989**:
**212 tests, 212 passed, 0 failures, 0 errors, 0 skipped**. This batch contributes **18 new JVM
tests** and **6 new instrumented tests**.

- `InMemoryEventPipelineTest` (+5): evidence_stage_that_records_no_artifact_cannot_advance_the_event_to_ready,
  delivery_stage_that_records_no_receipt_cannot_reach_delivered, reaching_ready_requires_a_persisted_evidence_reference,
  reaching_delivered_requires_a_persisted_successful_attempt, failed_delivery_attempt_keeps_the_event_out_of_delivered.
- `PanicIncidentCoordinatorTest` (10 new): first_panic_creates_exactly_one_real_event_id,
  repeated_panic_while_the_event_is_not_terminal_reuses_the_same_event_id,
  repeated_panic_does_not_start_duplicate_delivery_or_evidence_work,
  activation_count_and_last_activation_stay_consistent_with_the_presses,
  a_new_panic_after_the_previous_event_is_terminal_creates_a_new_event_id,
  concurrent_panic_presses_cannot_create_two_active_events (8 threads),
  a_bound_repeat_is_not_recorded_as_a_separate_incident,
  a_duplicate_event_id_is_bound_instead_of_becoming_a_second_confirmed_event,
  a_panic_that_cannot_be_persisted_is_rejected_instead_of_claimed,
  the_recorded_event_is_the_persisted_event.
- `PersistedEventReaderTest` (+3): a_panic_that_collected_nothing_is_never_projected_as_evidence,
  a_panic_that_was_never_sent_projects_no_delivery_receipt,
  states_that_performed_no_delivery_are_not_labelled_as_sending_or_delivered.

## ANDROID TESTS
RUN on the owner device RMX3910 (`IR7X5DJ7KZGEHMS8`, Android 16 / API 36, **no StrongBox
on this device**) with the app and androidTest APKs from the **same** CI run **36655321989**
(commit `8cce2d7`), after uninstalling both packages:

- `RoomPanicPersistenceInstrumentedTest` — **OK (3 tests)**:
  - a_panic_sequence_persists_one_event_on_the_device_filesystem (two presses = one event,
    no evidence, no delivery);
  - a_panic_after_a_terminal_event_persists_a_second_event (two distinct ids);
  - device_history_claims_no_evidence_and_no_receipt_for_a_recorded_panic (honest projection).
- Full instrumented suite — **OK (46 tests)** (was 40; +6 new). No regressions.

Honest note: the device screen was locked during this session, so the on-screen Panic button
could not be pressed and photographed. The instrumented suite proves the persistence path on
the real phone; the visible behavior would be the deferred snackbar because `StorageRuntimeApproval.approved`
remains false (Batch D flips it).

## BUILD
CI `clean test lint assembleDebug assembleDebugAndroidTest` — **SUCCESS** (run **36655321989**),
including `assembleDebugAndroidTest`, which is the only task that compiles the instrumented
source set.

## STATIC AUDIT
`g4b-review-check.sh` extends the G4-B gate with, all **PASS** locally and on CI:
- pipeline has no default success-producing stages;
- stage results are verified against persisted artifacts;
- evidence and delivery artifacts are recordable and persisted;
- unpersistable metadata fails closed instead of disappearing;
- panic confirms, persists first, and bounds the incident by lifecycle only;
- the panic button is wired to real persistence off the main thread;
- event state labels claim only what the state really did;
- the owner's named panic tests and the on-device persistence test are present.

GitHub runners have no `rg`, so every gate uses `grep`.

## SECURITY AUDIT
Self-review of the new surface (not a substitute for the independent review G4 still needs):
- `PanicIncidentCoordinator` lives entirely in the domain package — no Android APIs, no
  platform leakage, no UI coupling. It calls the pure `ProtectionStateEngine` and the
  `EventRepository` contract.
- Activation counts are kept in a `ConcurrentHashMap<EventId, ActivationRecord>` inside the
  coordinator instance. They are **not written to disk** because schema v2 has no metadata column;
  Room rejects such writes. That is the fail-closed choice: the number is correct for the running
  process and gone on restart, but it is never presented as durable. Q-C9 asks whether a schema
  v3 metadata table should be added later.
- The coordinator's `activate()` is `@Synchronized` and scans at most 100 recent events, so
  concurrent presses cannot mint two active events. Tested with 8 threads.
- No new permission, no new network path, no new export surface.

## SELF-ADVERSARIAL FINDINGS
1. **A typo almost broke the whole batch.** The pushed GREEN commit had `ActivationRecord(`
   closed with `}` instead of `)` — a single-character error that caused 12 compile errors in
   `compileDebugKotlin`. Fixed in `8cce2d7`. The RED/GREEN pair was genuine (RED run
   `36651570215` failed on unresolved symbols as intended), but the repair needed its own CI
   round (`36655321989`).
2. **JUnit4's returning `assertNotNull` was unproven in androidTest.** The repo had never
   assigned the return value of the two-arg form in an instrumented source set. Switched to
   Kotlin's `checkNotNull` to avoid a version-dependent inference risk.
3. **An indirect `run { }` construct hid behind unresolved types.** In
   `a_bound_repeat_is_not_recorded_as_a_separate_incident`, `assertIs<PanicActivation.Recorded>(run { ... })`
   produced a cascade of "Cannot infer type" errors in the RED run. Rewritten to separate
   statements so a real failure cannot hide behind inference.
4. **The device could not be unlocked for a live UI check.** The phone was PIN-locked, so the
   Panic button press was not photographed. The instrumented suite covers the persistence path
   on the real phone; the UI path is deferred until Batch D flips the §15 gate.
5. **Not finished in this batch:** the activation count is session-only (Q-C9), the storage
   install gate is still off (Batch D), and the approved successor chain stops at v2 (no key
   destruction in the rotation path — Q-C8).

## COMMIT
`c3c3a43` RED (pipeline honesty + panic tests) → `d39945f` GREEN (production code) →
`8cce2d7` fix(panic) — the typo repair.

## REMOTE
`toufikben/shieldra`, branch `main`. HEAD `8cce2d7`.

## CI
- `36651570215` (`c3c3a43`) — RED #1, **failure**: unresolved `recordEvidence`,
  `recordDeliveryAttempt`, `eventStateLabelRes`, `PanicActivation`.
- `36652982348` (`d39945f`) — GREEN #1, **failure**: `ActivationRecord` parameter list closed
  with `}` instead of `)`.
- `36655321989` (`8cce2d7`) — repair, **success**: 212 JVM tests, 212 passed; lint,
  `assembleDebug`, `assembleDebugAndroidTest`, Group C boundary scan, G4-B review and
  Phase 3 review all green; `apks-36655321989` used for the device run.

## ROADMAP
`docs/ROADMAP.md`: add row `4e-pipeline-honesty-and-panic-guard` under the dependency-aware
execution plan, marking protocol Batch C as CLOSED (CI + DEVICE). Update the Phase 5 section
to say the engine maps PANIC → CRITICAL and the platform path now exists behind the §15 gate.
Add Q-C9 to `docs/OWNER_QUESTIONS.md`.

## STATUS
**IMPLEMENTED, TESTED, CI VERIFIED, DEVICE VERIFIED.**
Not SECURITY REVIEWED. Not PRODUCTION APPROVED. `G4` is not accepted by this batch.

## BLOCKERS
None for this batch. Held owner decisions: **Q-C8** (when may a retired key be destroyed),
**Q-C9** (may the activation count be made durable), and the unresolved **§30 vs. store-launch**
monetization conflict. The §15 install gate remains off (Batch D).

## NEXT BATCH
Protocol Batch D — flip `StorageRuntimeApproval.approved` to true (the production install gate),
and decide Q-C9 (schema v3 metadata table vs. session-only tally).
