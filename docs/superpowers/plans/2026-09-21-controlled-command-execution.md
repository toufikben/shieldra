# Controlled Command Execution Plan

> **For agentic workers:** Execute one batch at a time. Each batch ends with a report, validation, and remote verification before the next batch begins.

**Goal:** Re-audit and correct the delivered Phase 3 presentation shell according to the supplied controlled command without adding Phase 4 or production functionality.

**Architecture:** Preserve the Group C contracts and the current single Android app module. Correct only implementation inconsistencies in the presentation layer, build foundation, navigation, components, localization, accessibility, design tokens, screen states, and deferred-action honesty. Keep demo data explicit and keep production integrations absent.

**Tech Stack:** Kotlin, Android Gradle Plugin 9.0.0, Gradle 9.7.1, Kotlin Compose compiler 2.2.10, Jetpack Compose BOM 2025.08.00, Android SDK 35, JDK 17, Android resources in English and Arabic.

**Spec:** `docs/authority/controlled-implementation-command.md`; current product boundary is documented in `docs/ROADMAP.md` and `docs/reports/phase-3-audit.md`.

## Global Constraints

- Do not start Phase 4.
- Do not add production security, location, camera, evidence, delivery, billing, ads, authentication, encryption, persistence, backend, cloud, Safe Zones, geofencing, or continuous GPS behavior.
- Do not redesign Shieldra; correct implementation inconsistencies only.
- Do not treat a successful build as proof of UI, accessibility, localization, navigation, or design correctness.
- After every batch: reread the relevant command section, reread modified files, inspect all modified API usages, validate, search again, write the report, and verify Git/remote state.
- If a regression appears, stop and fix it before the next batch.
- Use `DemoData` only as explicit presentation demo data; never import `presentation.preview` from runtime navigation.

## Review Focus

1. Build/resource failures, dependency drift, and missing launcher assets must be proven by clean resource compilation and Gradle validation.
2. Compose types must not leak into pure presentation models; prove this with import/type scans and all-usage review.
3. Navigation callbacks must not silently imply production success; prove this with a route/callback matrix and deferred-action scan.
4. Every production hardcoded user-facing string must be found by a complete second scan and either resource-backed or explicitly non-user-facing.
5. Accessibility and RTL behavior cannot be fully proven without a device; separate static PASS from runtime UNKNOWN in every report.

## Batch 0 — Build/Foundation

**Files:** `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/**`, `.github/workflows/group-a-diagnostics.yml`.

- [ ] Re-read the Batch 0 command section and all named files.
- [ ] Compare plugin/dependency versions against the existing verified CI evidence.
- [ ] Scan resource references, launcher icon declarations, XML escapes, and generated resource names.
- [ ] Run `./gradlew clean :app:processDebugResources :app:compileDebugKotlin`.
- [ ] Run `git diff --check`, YAML/shell syntax checks, and a dependency consistency scan.
- [ ] Write `docs/reports/batch-0-foundation.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 1 — Architecture/Models/Data Flow

**Files:** `app/src/main/kotlin/com/shieldra/app/presentation/model/**`, `presentation/demo/**`, `presentation/preview/**`, callback contracts, `navigation/**`, and all usages found by `rg`.

- [ ] Re-read every model and every usage before editing.
- [ ] Scan pure models for Compose/UI framework types.
- [ ] Verify runtime code never imports preview data.
- [ ] Verify callback contracts preserve state ownership and do not implement domain behavior.
- [ ] Run unit tests, compile, and a second import/usage scan.
- [ ] Write `docs/reports/batch-1-architecture.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 2 — Navigation + Screen Connectivity

**Files:** `navigation/ShieldraRoutes.kt`, `navigation/ShieldraNavHost.kt`, every screen entry point, callbacks, and `DemoData` usages.

- [x] Build a route matrix listing every route, entry, exit, argument, back behavior, and bottom-tab behavior.
- [x] Detect dead routes and unreachable screens statically.
- [x] Verify every callback has either navigation or an honest deferred outcome.
- [x] Run unit/build/static checks and re-read every changed navigation usage.
- [x] Write `docs/reports/batch-2-navigation.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 3 — Reusable Components

**Files:** one component family per sub-batch under `design/components/foundation/**` and `design/components/domain/**`.

- [x] Inspect every component implementation and all call sites.
- [x] For each family check API/state, semantics, RTL, target size, token usage, theme access, and Compose API correctness.
- [x] Compile after each component family; do not mix unrelated fixes.
- [x] Write `docs/reports/batch-3-components.md` with a component matrix.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 4 — Localization + RTL

**Files:** all production Kotlin under `app/src/main/kotlin/com/shieldra/app/**`, `app/src/main/res/values/**`, `values-ar/**`, and locale-sensitive tests.

- [ ] Run a complete hardcoded user-visible string scan, excluding comments, debug labels, test data, and non-user-facing animation labels.
- [ ] Add resource IDs with English and Arabic values, preserving placeholders and mixed numbers/IDs.
- [ ] Re-run the complete scan and resource-reference check.
- [ ] Inspect RTL-sensitive rows, navigation, icons, timestamps, and phone/ID text.
- [ ] Write `docs/reports/batch-4-localization.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 5 — Accessibility

**Files:** all interactive components and screen files, plus accessibility tests or static scan scripts.

- [ ] Enumerate buttons, chips, dismiss controls, icon buttons, cards, nav items, switches, sliders, and fields.
- [ ] Verify approximately 48dp targets, semantics roles, descriptions, state descriptions, and keyboard behavior by static inspection.
- [ ] Verify contrast from tokens and document large-text/device limitations.
- [ ] Run a second complete scan after fixes.
- [ ] Write `docs/reports/batch-5-accessibility.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 6 — Design System

**Files:** `design/tokens/**`, `design/theme/**`, `design/graphics/**`, icon mapping, and component usages.

- [ ] Inventory colors, typography, spacing, shapes, elevation, icons, motion, and raw values.
- [ ] Correct only inconsistencies that violate the existing handoff tokens.
- [ ] Run raw-value/token scans and compile.
- [ ] Write `docs/reports/batch-6-design-system.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 7 — Every Screen

**Files:** each onboarding, dashboard, history, event detail, premium, and settings screen plus their models/components.

- [ ] Create a screen-state matrix for normal, loading, empty, error, disabled, long text, Arabic, narrow width, and large text.
- [ ] Review each screen independently and record static versus runtime evidence.
- [ ] Add only presentation-state corrections; do not add domain behavior.
- [ ] Write `docs/reports/batch-7-screen-state.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 8 — Deferred Functionality Honesty

**Files:** navigation callbacks, premium, onboarding permissions, dashboard panic/guards, event detail maps/delete/export, settings callbacks.

- [ ] Enumerate every deferred action and its visible result.
- [ ] Confirm no callback performs fake billing, permissions, maps, deletion, export, panic, backend, or auth behavior.
- [ ] Run forbidden-pattern scans and unit/build checks.
- [ ] Write `docs/reports/batch-8-deferred-honesty.md`.
- [ ] Commit/push only if clean; verify remote CI.

## Batch 9 — Final Integration

**Files:** entire repository, final report and roadmap.

- [ ] Run clean build, all relevant tests, static checks, resource verification, and navigation verification.
- [ ] Reinspect the entire project and Git history.
- [ ] Verify final remote SHA, workflow, Job Summary, and artifact behavior.
- [ ] Write `docs/reports/batch-9-final-integration.md` and update `docs/ROADMAP.md`, `docs/AI-HANDOFF.md`, and the traceability ledger.
- [ ] Stop before Phase 4.

## Commit policy

Use one commit per clean batch or independently reviewable sub-batch. Commit messages must name the batch. Do not rewrite history. Do not push known failures.

## References

[1]: https://github.com/toufikben/shieldra/actions/runs/35575159609 "Shieldra final successful CI run"
