# Shieldra AI Handoff

This file is written for a new AI agent that must continue Shieldra without repeating discovery, asking for already answered facts, or re-running broad audits unnecessarily.

## Immediate facts

The repository is `toufikben/shieldra`, branch `main`, and the current commit at handoff preparation is `ddc001ebf71ae595cc905e5450814679522714d8`. The working tree must be checked before editing. The repository is a private, independent clean-slate project. The original handoff repository was `toufikben/Ai_super_cleaner`; it was inspected read-only and is not the destination.

The latest completed remote workflow is [run 35575159609](https://github.com/toufikben/shieldra/actions/runs/35575159609), which succeeded on the report-evidence commit. The workflow's actual log recorded Ubuntu 24.04.5, x86_64/amd64, Temurin JDK 17.0.20.1, Gradle 9.7.1, and Android SDK paths. The run passed Group A diagnostics, Group B/Group C build, Group C boundary scan, Phase 3 static review, Job Summary publication, and artifact upload. The Checks API returned 403 for the available token; this is a permission limitation, not a workflow failure.

## What is already delivered

Group A delivered reproducible environment diagnostics. Group B delivered the single Android app module and architecture documentation. Group C delivered typed contracts and boundaries. Phase 3 delivered a Compose presentation shell with onboarding, dashboard, history, event details, premium, settings, design components, design tokens, navigation, English/Arabic resource foundations, a vector launcher icon, and named `DemoData`.

Phase 3 is presentation-only. It does not implement a security engine, camera, location collection, evidence capture, delivery, WhatsApp, Telegram, billing, ads, authentication, encryption, persistence, backend, cloud services, Safe Zones, geofencing, or continuous GPS. Deferred actions must remain honest and must not display fake success.

## Authority status

Product Freeze v5 and the complete Phase 1 specification were not supplied as repository files. Do not invent their missing requirements. The available supplied Group B and Group C scopes are under `docs/authority/`. The original Phase 3 handoff was read and audited; its defects and corrections are documented in `docs/reports/phase-3-audit.md`.

## Required first commands

```bash
cd /home/ubuntu/shieldra
git status --short
git rev-parse HEAD
gh api repos/toufikben/shieldra/commits/main --jq '.sha'
find docs -type f -print | sort
```

If the local SHA and remote SHA differ unexpectedly, stop and report. Never discard work from another agent without an explicit rollback instruction.

## Validation commands

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/tmp/shieldra-sdk
export ANDROID_SDK_ROOT=/tmp/shieldra-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew clean testDebugUnitTest lintDebug assembleDebug
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
```

A local successful build is necessary but not sufficient for visual, navigation, accessibility, localization, or design claims. No emulator or connected device was available in the sandbox at the last audit. Runtime visual checks remain unknown until a device is available.

## Required documentation reading order

Read `docs/ROADMAP.md`, this file, `docs/authority/controlled-implementation-command.md`, `docs/traceability/controlled-batch-traceability.md`, the latest report under `docs/reports/`, and the active plan under `docs/superpowers/plans/`. Then read only the source files listed for the active batch and all usages of their modified APIs.

## Batch execution contract

The attached controlled command is divided into Batches 0–9. Each batch is independent and must follow this exact gate: read the relevant handoff section; inspect the named files; change only the batch scope; validate; search again for the same defect class; write the batch report; verify Git; commit and push; verify remote CI; then update the ledger. Do not carry known failures forward.

Batch 0 covers build and foundation. Batch 1 covers architecture, models, and data flow. Batch 2 covers navigation and screen connectivity. Batch 3 covers reusable components one component family at a time. Batch 4 covers all production localization and RTL. Batch 5 covers accessibility. Batch 6 covers the design system. Batch 7 independently reviews every screen state. Batch 8 verifies deferred-functionality honesty. Batch 9 performs final integration and full-project reinspection.

## Known risks to investigate, not assume fixed

The first Phase 3 pass left 59 non-fatal lint warnings, mostly unused handoff resource keys. The first localization pass did not migrate every user-visible sample/model value or every reusable component label. The original handoff had known issues involving a duplicate Snackbar property, a missing launcher resource, Compose-coupled presentation models, preview/runtime coupling, and undersized dismiss controls; these were addressed in the delivered shell, but the controlled command requires each defect class to be searched again.

## Prohibited shortcuts

Do not create a large implementation without batch boundaries. Do not redesign Shieldra. Do not add production integrations to make a screen appear functional. Do not use preview-only data in runtime. Do not declare visual, accessibility, localization, RTL, or navigation completion from a successful compile alone. Do not start Phase 4.

## Required report format

Every batch report must include: batch number, objective, files inspected, files changed, problems found, problems fixed, validation performed, validation result, remaining problems, and next batch. Use factual status words such as `PASS`, `FAIL`, `UNKNOWN`, or `BLOCKED`; never convert an untested runtime condition into `PASS`.

## Current controlled-command status

Batches 0, 1, 2, 3, and 4 have passed their static controlled audits. An independent final red-team audit is recorded in `docs/reports/final-red-team-audit.md`; it found and fixed two compact touch targets, removed the remaining production non-null assertions, hardened long/mixed-direction event-detail rows, and found no unresolved critical or high correctness issue. Batch 5 remains the next controlled batch if further audit execution continues. Runtime navigation, Arabic layout, accessibility behavior, and device-level visual checks remain UNKNOWN because no emulator or connected device is available.

## References

[1]: https://github.com/toufikben/shieldra/actions/runs/35575159609 "Shieldra final successful CI run"
[2]: https://github.com/toufikben/shieldra "Shieldra repository"
