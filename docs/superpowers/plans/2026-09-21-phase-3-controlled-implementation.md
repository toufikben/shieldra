# Phase 3 Controlled Implementation Plan

## Objective

Implement the supplied Shieldra Phase 3 presentation handoff in the existing Group C repository while correcting the handoff defects identified by the forensic audit. The implementation must preserve Group C contracts and must not add deferred production behavior.

## Batch 1 — Build and design foundation

**Files:** root/app Gradle files, manifest, resources, design tokens, theme, graphics, activity/app entry points.
**Validation:** reread all changed files; verify resource references; run Gradle configuration and the smallest available compile task.

## Batch 2 — Models and reusable components

**Files:** pure presentation models, preview data, foundation components, domain components.
**Validation:** scan model packages for Compose UI types; scan components for hardcoded strings, missing semantics, undersized click targets, and forbidden implementations; run unit/compile checks.

## Batch 3 — Screens and navigation

**Files:** onboarding, dashboard, history, event detail, premium, settings, routes, NavHost.
**Validation:** trace every route and callback; verify preview/demo data is not hidden as production state; verify all deferred actions are visibly honest; scan all user-facing strings and RTL-sensitive layout.

## Batch 4 — Tests and CI evidence

**Files:** tests, boundary/localization/resource scan scripts, workflow, report.
**Validation:** run static checks; run Gradle tests/build in CI-capable environment; inspect the remote workflow result and summary.

## Stop rules

Do not add production security, location, camera, delivery, billing, ads, authentication, encryption, persistence, backend, or cloud behavior. Do not commit or push a batch until its changed files have been independently reread and its relevant validation has passed. Do not claim runtime visual verification while no emulator or device is available.
