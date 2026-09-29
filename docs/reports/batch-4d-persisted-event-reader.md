# Batch report — 4d-persisted-event-reader (real events reach the UI)

## BATCH
4d-persisted-event-reader — replace the always-empty event list with a real
read of persisted events. This closes the *read* path only; it does **not**
close §15 and does **not** close G4.

## SCOPE
The previous batch made the dashboard honest but permanently empty
(`recentEvents = emptyList()`, `lockGuardEventDetail` always `null`). Events
were being persisted by the domain but nothing ever read them back, so the
storage slice had no visible purpose.

This batch adds a read path: `listRecent(limit)` on the repository contract, a
newest-first Room query, a pure projection reader, and an off-main-thread read
in the navigation host. No detection, no services, no permissions, and no
storage-gate change are involved.

Deliberately **out of scope**:
- **Detection / phase 5 local alerts** — owner-policy items behind G2/G3.
- **A ViewModel** — `produceState`/`LaunchedEffect` proves the read works; a
  ViewModel is a lifecycle concern that should be decided with the real
  refresh/live-update policy, not smuggled in here.
- **The event detail route's evidence** — a persisted row carries no photo or
  location, so the detail honestly shows neither rather than inventing them.

## FILES CHANGED
- `app/src/main/kotlin/com/shieldra/domain/EventPipelineContracts.kt` —
  `listRecent(limit: Int): List<SecurityEvent>` on the repository contract,
  documented as off-UI-thread.
- `app/src/main/kotlin/com/shieldra/domain/InMemoryEventPipeline.kt` — the
  in-memory implementation, newest-first, `require(limit > 0)`.
- `app/src/main/kotlin/com/shieldra/storage/room/StorageDaos.kt` —
  `findRecent(limit)`, `ORDER BY created_at_epoch_ms DESC`.
- `app/src/main/kotlin/com/shieldra/storage/room/RoomEventRepository.kt` —
  implements `listRecent`.
- `app/src/main/kotlin/com/shieldra/app/presentation/guard/PersistedEventReader.kt`
  (NEW) — the pure projection core. Stays inert when storage is not installed;
  never invents a photo, a location, a battery level, or a delivery receipt;
  all display text arrives already localized via `PersistedEventLabels`.
- `app/src/main/kotlin/com/shieldra/app/presentation/guard/StorageEventReader.kt`
  (NEW) — binds the reader to the installed `LocalShieldraStorage`. There is no
  speculative open and no second storage instance.
- `app/src/main/kotlin/com/shieldra/app/presentation/guard/LockGuardDashboardFactory.kt`
  — `lockGuardDashboard(state, recentEvents)`; the always-`null`
  `lockGuardEventDetail` is deleted.
- `app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt` — reads
  real events for dashboard and history, resolves the detail route, and
  dispatches every Room read through `Dispatchers.IO`.
- `app/src/main/res/values/strings.xml`, `values-ar/strings.xml` — event title
  and the four localized age strings.
- `app/src/test/kotlin/com/shieldra/app/presentation/guard/PersistedEventReaderTest.kt`
  (NEW) — 12 JVM tests.
- `app/src/androidTest/kotlin/com/shieldra/app/presentation/guard/PersistedEventReadingInstrumentedTest.kt`
  (NEW) — 5 device tests.
- `.github/scripts/g4b-review-check.sh` — new gate (see STATIC AUDIT).

## DECISIONS ENFORCED
- **No fabricated evidence:** the projection sets `hasPhoto = false` and
  `hasLocation = false` because a persisted row contains neither. It never
  invents them, and a test asserts both.
- **No main-thread Room access:** every read is dispatched to
  `Dispatchers.IO`, because Room throws when queried on the main thread. A CI
  gate fails if the dispatch disappears.
- **Inert without installed storage:** a build whose §15 gate is off reads
  nothing at all — no speculative open, no fallback source.
- §14: no policy change; `StorageRuntimeApproval.approved` remains `false`.

## TESTS
171 JVM tests green in CI (was 154; +17 across the reader and its projection).
CI summary: `171 tests, 171 passed, 0 failures, 0 errors, 0 skipped`.

The reader tests cover: storage-not-installed reads nothing; a failed read
degrades to no events rather than crashing; load order is preserved; a skewed
clock never yields a negative age; an injected localized formatter is used
verbatim; the caller's `limit` reaches the query; an unknown id produces no
detail.

## ANDROID TESTS
On the owner RMX3910 with app + androidTest APKs from the same green run
(`36552909429`): `PersistedEventReadingInstrumentedTest` **`OK (5 tests)`**,
and the full instrumented suite **`OK (34 tests)`** (was 29; +5 new) — no
regressions.

These tests prove on a real device that a row saved through
`RoomEventRepository.save` comes back through the same reader the UI uses,
that `listRecent` returns newest-first and honours its limit, that a delivery
state survives the round trip into the projection, that a stored event
resolves by its own id, and that a reader with no installed storage reads
nothing.

## BUILD
`assembleDebug` and `assembleDebugAndroidTest` both built in CI
(run `36552909429`, `BUILD SUCCESSFUL in 4m 48s`).

## STATIC AUDIT
`g4b-review-check.sh` PASS in CI, including the new gate
`PASS: persisted events are read off the main thread and only when storage is
installed`. The gate fails if the reader is missing, if it does not gate on
`storageInstalled`, if the `Dispatchers.IO` dispatch disappears, or if the
NavHost stops using `readRecent`/`readOne`.

## SECURITY AUDIT
Read-only addition: no new write path, no new permission, no network, no key
material, no migration, no egress. The previously green raw-create,
§4.3 destructive-migration, and §5.5 no-egress gates remain green.

## SELF-ADVERSARIAL FINDINGS
1. **RED was proven by symbol absence.** Commit `3bb1507` pushed the test
   only; run `36549780429` failed to compile with
   `Unresolved reference 'PersistedEventReader'`. The test cannot pass before
   the code lands.
2. **A `null` was doing two jobs.** The first detail-route version used
   `produceState<EventDetailUiModel?>(null)`, so "still loading" and "no such
   event" were indistinguishable — the screen would have shown "unknown
   event" before the database was ever read. CI's smart-cast error exposed
   this, and it is fixed with an explicit `DetailLookup` that separates
   `Loading` from `Resolved(null)`.
3. **`produceState` inferred the sealed subtype, not the sealed type.**
   `produceState(initialValue = DetailLookup.Loading, ...)` typed the state as
   `DetailLookup.Loading`, so assigning a `Resolved` was a type mismatch
   (run `36551915935`). Replaced with `LaunchedEffect` + an explicitly typed
   `mutableStateOf<DetailLookup>`, which also removes the overload ambiguity
   that made the fix non-obvious.
4. **The first localization attempt was wrong and was caught before CI.**
   Calling `stringResource(id, 0)` and then `String.format` on the result
   would not substitute anything, because the `%1$d` was already consumed. The
   templates are now captured raw and formatted once.
5. **A `gh run watch` exit code lied once more.** The watch for `36552909429`
   exited non-zero because the connection to the API dropped mid-poll, not
   because the workflow failed; re-querying the run showed
   `conclusion: success` with every step `success`. The conclusion was only
   accepted after re-reading the run itself, not the watcher's exit code.
6. **Age formatting was simplified deliberately.** The reader reports a coarse
   age (`1h ago`) and the caller supplies the localized wording, rather than
   composing `1h 30m ago` in the domain layer. Composing it would have put
   English grammar into a framework-free class and made Arabic impossible.

## COMMIT
`3bb1507` (RED test) → `34bcb0a` (reader + wiring, CI found 3 compile errors) →
`8fbb9f5` (compile fixes) → `0444ee5` (`LaunchedEffect` for the detail lookup),
lineage on `main`.

## REMOTE
`toufikben/shieldra` @ `main`, pushed to `0444ee5`.

## CI
"Group A diagnostics": `36549780429` FAILED (expected RED compile) →
`36551174645` FAILED (3 compile errors) → `36551915935` FAILED (type
inference) → `36552909429` **SUCCESS** (171 tests, lint, both APKs, all review
scripts including the new gate).

## ROADMAP
`4d-persisted-event-reader` CLOSED (CI + DEVICE) for the read path. §15
production gate and overall G4 sign-off remain **OPEN**.

## STATUS
CLOSED for this slice. Persisted events now reach the UI honestly.

## BLOCKERS
§15/G4 still require: the detection path and phase 5 local alerts (G2/G3
owner-policy answers), phase 6 external providers, phase 7 end-to-end
security/release review, the storage/crypto/guard/evidence/alert/permission/
privacy audit set, the final self-adversarial audit, and the owner's visible
flip of `StorageRuntimeApproval.approved` (§14). Nothing in this batch may be
read as evidence for any of those.

## NEXT BATCH
Present the next genuine code-unblocking phase for owner approval. The next
real gap is that nothing ever *creates* an event at runtime, so the honest read
path will always show an empty list in a shipped build. Producing events
requires the detection path, which is gated on G2/G3 owner-policy answers.
