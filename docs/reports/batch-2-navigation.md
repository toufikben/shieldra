# Batch 2 Report — Navigation and Screen Connectivity

**Batch:** 2
**Objective:** Re-audit every route, screen entry and exit, argument, back-navigation path, callback, state-propagation path, dead route, and unreachable screen without adding Phase 4 functionality.

## Files inspected

The audit inspected `navigation/ShieldraRoutes.kt`, `navigation/ShieldraNavHost.kt`, all screen entry points under `presentation/screen/`, all screen callback contracts, `presentation/demo/DemoData.kt`, `presentation/model/PresentationModels.kt`, and every usage of `ShieldraRoutes`, `BottomTab`, navigation calls, and callback contracts.

## Files changed

- `app/src/main/kotlin/com/shieldra/app/navigation/ShieldraNavHost.kt`
- `docs/reports/batch-2-navigation.md`
- `docs/traceability/controlled-batch-traceability.md`
- `docs/ROADMAP.md`
- `docs/AI-HANDOFF.md`
- `docs/superpowers/plans/2026-09-21-controlled-command-execution.md`

## Route matrix

| Route | Entry | Exit/actions | Arguments | Back behavior | Bottom bar |
|---|---|---|---|---|---|
| `onboarding/welcome` | App start; `startDestination` default | Get started → permissions; Skip → dashboard | None | System back exits the app-level graph | Hidden |
| `onboarding/permissions` | Welcome Get started | Allow or Not now → protection setup | None | Navigation back returns to welcome | Hidden |
| `onboarding/protection` | Permissions outcome | Continue → dashboard; welcome is removed from the back stack | None | Navigation back returns to permissions | Hidden |
| `main/dashboard` | Onboarding completion or skip; bottom tab | Event → event detail; View all → history; guard/panic → honest deferred snackbar | None | System back follows the navigation stack | Dashboard selected |
| `main/history` | Dashboard View all or bottom tab | Event → event detail; type/status filters update screen state | None | System back follows the navigation stack | History selected |
| `main/premium` | Bottom tab | Buy/restore → honest deferred snackbar | None | System back follows the navigation stack | Premium selected |
| `main/settings` | Bottom tab | Settings row → honest deferred snackbar | None | System back follows the navigation stack | Settings selected |
| `main/event/{eventId}` | Dashboard/history event click | Back → previous screen; maps/delete/export → honest deferred snackbar | Required string `eventId` | Explicit `popBackStack()` | Hidden |

## Problems found

The route graph contained all declared routes and each route had a `composable` entry. No dead route or unreachable screen was found. Event-detail arguments were declared as required strings and every event entry point supplied an ID. All deferred callbacks had an explicit Phase 3 snackbar outcome.

One confirmed state-propagation defect was found in `HistoryScreen` wiring: `HistoryFilterState()` was recreated as a constant input on every composition and `onFilterChange` only showed the deferred snackbar. Selecting a type or status filter therefore could not update the selected chip state.

Runtime navigation, system-back behavior, and Arabic/device layout remain unverified because no emulator or connected device is available in the sandbox.

## Problems fixed

`ShieldraNavHost` now owns a remembered `HistoryFilterState` for the history destination. The `HistoryCallbacks.onFilterChange` callback applies the returned type and status values, and the updated state is passed back into `HistoryScreen`. This preserves the existing presentation-only boundary and does not add a data repository or production behavior.

## Validation performed

The following checks passed:

```text
./gradlew clean testDebugUnitTest lintDebug assembleDebug --no-daemon
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
```

The Gradle build completed successfully. Unit tests passed: 11 total (2 presentation-model, 7 contract, 2 signal), with zero failures or errors. Lint completed with zero errors; existing warnings remain, including two deprecated `DirectionsRun` icon references and non-blocking unused-resource warnings. The Phase 3 static review and Group C boundary scan both passed. The second route/callback scan found no dead routes, missing entries, missing callback assignments, or runtime preview-data imports.

## Validation result

**PASS for Batch 2 static navigation and screen-connectivity scope.** The confirmed history-filter propagation defect was fixed and validated. Runtime navigation behavior remains **UNKNOWN**, not claimed as passed.

## Remaining problems

Batch 3 must review reusable components individually, including the deprecated directional icon references and interactive behavior. Batch 4 must perform the complete production localization and RTL scan. Runtime visual, device navigation, Arabic layout, large-text, and accessibility behavior remain unverified without an emulator or connected device. No known Batch 2 source failure is being carried forward.

## Next batch

Batch 3 — Reusable Components, one component family at a time.
