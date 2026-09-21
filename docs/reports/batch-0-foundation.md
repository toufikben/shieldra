# Batch 0 Report — Build/Foundation

**Batch:** 0
**Objective:** Re-read and validate Gradle/build configuration, missing files, imports, resources, launcher icon, compilation blockers, and dependency consistency without touching visual polish.

## Files inspected

The batch inspected `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, `app/src/main/AndroidManifest.xml`, all files under `app/src/main/res/`, `.github/workflows/group-a-diagnostics.yml`, and all shell scripts under `.github/scripts/`.

## Files changed

No application or CI source files were changed in Batch 0. The batch added this report and updated the batch ledger only.

## Problems found

The foundation scan found no missing manifest resource, invalid XML backslash escape, obsolete Android SDK `tools` package, or dependency declaration outside the version catalog. The manifest references `@drawable/ic_launcher`, `@string/app_name`, and the two XML backup resources; each exists. The workflow installs `platform-tools`, `platforms;android-35`, and `build-tools;35.0.0` and does not request the obsolete `tools` package.

The first dependency command was deliberately corrected after it targeted the root project configuration `debugRuntimeClasspath`, which does not exist at the root. This was a command-targeting error in the audit, not a repository defect. The corrected command targeted `:app:dependencies --configuration debugRuntimeClasspath` and passed.

The clean Kotlin compile emitted two deprecation warnings for `Icons.Filled.DirectionsRun`; this is outside Batch 0 because the command explicitly prohibits visual polish and reusable-component changes in this batch. It is carried to Batch 3/6 for controlled review.

## Problems fixed

No source defect required a Batch 0 fix. The dependency audit command was corrected and rerun successfully.

## Validation performed

The following checks passed:

```text
./gradlew clean :app:processDebugResources :app:compileDebugKotlin --stacktrace
bash -n .github/scripts/*.sh
YAML parse of .github/workflows/group-a-diagnostics.yml
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

The foundation build reported `BUILD SUCCESSFUL`. The dependency graph resolved Activity Compose 1.10.1, Navigation Compose 2.8.9, Compose BOM 2025.08.00, and Material 3 1.3.2. Resource scans found no invalid backslash escapes. The working tree remained clean except for the new documentation/report files.

## Validation result

**PASS for Batch 0 foundation scope.** No known Batch 0 failure is being carried forward. The command-targeting error was corrected before declaring the batch result.

## Remaining problems

Two deprecated `DirectionsRun` icon references remain for Batch 3/6 review. The previous Phase 3 report also records non-fatal unused-resource lint warnings and incomplete migration of some user-visible sample/component strings; those belong to Batch 4. Runtime visual verification remains unavailable without an emulator or device.

## Next batch

Batch 1 — Architecture, models, and data-flow re-audit.
