# Batch 1 — Phase 3 Foundation Fixes

**Date:** 2026-09-22
**Status:** VERIFIED for source, build, tests, resources, and static review; device runtime remains UNKNOWN.

## Objective

Apply only objectively verifiable Phase 3 corrections in localization/resources, RTL-safe UI structure, accessibility semantics, and existing pure-model tests. No Phase 4 runtime behavior was added.

## Audit findings

1. `EventCard` constructed its merged accessibility description with hard-coded English punctuation and concatenation rather than a localized resource.
2. `GuardTile`, `EventCard`, and `PermissionsScreen` previews contained user-visible literal strings instead of using the localized demo resources already provided by the project.
3. `ErrorState` preview contained a hard-coded English error message.
4. History filter tests covered only a lock/delivered case and a negative panic case; the declared type and status filter vocabulary was not fully exercised.
5. English and Arabic string keys were already equal before the batch; the new accessibility key was added to both locales and parity remained intact.
6. Existing directional layout usage was already based on start/end-aware APIs; no unsafe left/right layout correction was justified by the audit.

## Files changed

- `app/src/main/kotlin/com/shieldra/app/design/components/domain/EventCard.kt`
  - Added localized `event_accessibility` formatting.
  - Replaced manual English accessibility concatenation.
  - Reused localized `DemoData.recentEvents()` in the preview.
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/GuardTile.kt`
  - Reused localized `DemoData.guards()` in the preview.
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/ErrorState.kt`
  - Reused `R.string.could_not_load_events` in the preview.
- `app/src/main/kotlin/com/shieldra/app/presentation/screen/onboarding/PermissionsScreen.kt`
  - Reused localized notification title and reason resources in the preview.
- `app/src/main/res/values/strings.xml`
  - Added English `event_accessibility` resource.
- `app/src/main/res/values-ar/strings.xml`
  - Added Arabic `event_accessibility` resource.
- `app/src/test/kotlin/com/shieldra/app/presentation/PresentationModelTest.kt`
  - Added coverage for every declared history type and delivered/deferred/failed status filter.

## Validation

The following command completed successfully:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/tmp/shieldra-sdk
export ANDROID_SDK_ROOT=/tmp/shieldra-sdk
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
```

Results:

- `BUILD SUCCESSFUL`.
- Unit tests passed.
- Lint passed.
- Debug assembly passed.
- Phase 3 static review passed.
- Group C boundary scan passed.
- `git diff --check` passed.
- English/Arabic resource parity passed with 194 keys.
- The post-fix production-source candidate scan no longer found hard-coded strings in the corrected components and previews. `PreviewData.kt` remains intentionally preview-only data and is not imported by runtime navigation; it is not a production data source.

## Remaining issues

- Emulator/device runtime verification remains **UNKNOWN**: Arabic rendering, TalkBack announcements, physical touch rectangles, large text, narrow widths, and real back-stack behavior still require a device.
- `PreviewData.kt` retains static preview literals by design. It is used only from `@Preview` functions, while runtime navigation uses localized `DemoData`; converting it would be a separate preview-data cleanup and is not required to claim runtime localization.
- Phase 4 remains **BLOCKED** by missing approved product/security authority. This batch added no camera, location, storage, encryption, authentication, delivery, billing, backend, or background behavior.

## Next batch

After commit and remote verification, the next safe scope is a separately reviewed Phase 3 batch for screen-state and accessibility refinements that can be proven without a device. Do not start Phase 4 implementation.

