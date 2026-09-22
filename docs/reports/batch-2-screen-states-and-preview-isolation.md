# Batch 2 — Screen States and Preview Isolation

**Date:** 2026-09-22
**Status:** VERIFIED for source, build, tests, and static review; device runtime remains UNKNOWN.

## Objective

Improve Phase 3 screen-state previews and remove the constraints caused by a second, non-localized `PreviewData` source. Runtime behavior must continue to use localized `DemoData`; no Phase 4 behavior may be introduced.

## Findings

1. `PreviewData` was no longer used by runtime navigation, but five screen preview functions still depended on it.
2. `PreviewData` duplicated demo content, contained non-localized literals, and prevented previews from exercising the localized English/Arabic demo factories.
3. Dashboard had only protected dark/light previews even though its implementation already supported empty recent events, suspicious protection state, and disabled guards.
4. History had an empty-data preview but no filtered-no-match preview.
5. Event detail had no preview for `LAST_KNOWN` location freshness.

## Changes

- Added composable localized factories to `DemoData`:
  - `dashboardEmpty()`
  - `dashboardSuspicious()`
  - `dashboardDisabled()`
  - `eventDetailLastKnown()`
- Replaced all screen preview imports/usages of `PreviewData` with `DemoData`:
  - Dashboard
  - History
  - Event detail
  - Premium
  - Settings
- Added Dashboard previews for empty, suspicious, and disabled states.
- Added a History preview for events that do not match the selected filter.
- Added an Event detail preview for `LAST_KNOWN` location data.
- Deleted the now-unused `app/src/main/kotlin/com/shieldra/app/presentation/preview/PreviewData.kt` and its empty package.
- Added a static review guard that fails if `PreviewData` or `presentation.preview` returns to application source.

## Files inspected

- `app/src/main/kotlin/com/shieldra/app/presentation/demo/DemoData.kt`
- All screen files previously importing `PreviewData`.
- Dashboard, History, EmptyState, LoadingState, and ErrorState state paths.
- Navigation and source-level runtime data references.

## Validation

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/tmp/shieldra-sdk
export ANDROID_SDK_ROOT=/tmp/shieldra-sdk
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
test -z "$(rg -n 'PreviewData|presentation\\.preview' app/src/main/kotlin || true)"
```

Results:

- `BUILD SUCCESSFUL`.
- Unit tests passed.
- Lint passed.
- Debug assembly passed.
- Phase 3 static review passed, including the new DemoData-only source check.
- Group C boundary scan passed.
- `git diff --check` passed.
- PreviewData isolation check passed.

## Remaining limitations

- Preview declarations are source-level verification only; visual RTL, accessibility announcements, large text, narrow widths, and interaction behavior remain **UNKNOWN** without an emulator or connected device.
- Loading and error components have standalone previews, but the current Phase 3 screen APIs do not define production loading/error state models. Adding such runtime state ownership would be a separate architectural decision and is not invented here.
- Phase 4 remains **BLOCKED**. This batch added no camera, location collection, persistence, encryption, authentication, delivery, billing, backend, or background behavior.

## Result

**VERIFIED** for the controlled Phase 3 source/build/static scope. `PreviewData` has been removed from application source and all screen previews now use localized `DemoData` factories.

