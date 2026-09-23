# Batch 4d-7 — G4-B Local Closure Audit

**Date:** 2026-09-23  
**Commit under review:** `b9d56fa` (`fix: enforce storage boundary and compile overrides`)  
**Status:** **IMPLEMENTED — STATIC/COMPILE CI VERIFIED; DEVICE RUNTIME/SECURITY ACCEPTANCE STILL OPEN**

## Scope

This batch performs an offline closure audit of the locally implementable G4-B boundaries after 4d-6 was blocked by the absence of `adb`, an emulator, an AVD, and an attached Android device. It does not replace Room/Keystore runtime evidence, threat-model review, or security/privacy sign-off.

## Automated checks

The repository now includes `.github/scripts/g4b-review-check.sh`, which verifies:

- application-level backup is disabled;
- database and file domains are excluded from cloud backup and device transfer;
- evidence is rooted under `Context.noBackupFilesDir` through the explicit factory;
- the factory is not automatically wired into the application graph before runtime approval;
- the exported Room schema exists and records foreign-key evidence;
- the Keystore boundary documents no software fallback;
- no destructive migration fallback appears in production source;
- the working tree diff has no whitespace errors.

GitHub Actions now runs this review script directly, and the corrected workflow also compiles `assembleDebugAndroidTest`. The successful runs are `35886895480` for instrumented-test compilation and `35886951932` for the documentation update; neither runs connected device tests.

The check ends with `G4-B LOCAL REVIEW PASS` only when all local assertions pass. It deliberately does not assert Android behavior that requires a device.

## Current local findings

| Area | Finding | Classification |
|---|---|---|
| Room schema | Version 1 schema exported with event, evidence-reference, delivery-attempt tables and foreign-key relationships | Local implementation evidence |
| Persistence boundary | `RoomEventRepository` remains behind `EventRepository`; coordinator redesign was not introduced | Local implementation evidence |
| Evidence files | Atomic temporary write and replacement, fail-closed read, orphan quarantine, temporary cleanup | Local deterministic evidence |
| Backup | Manifest disables backup; extraction rules exclude database/file/shared-preference domains | Configuration evidence; runtime verification still required |
| Key boundary | Android Keystore candidate has no software fallback | Source evidence; device verification still required |
| Application wiring | `LocalShieldraStorage` is explicit and not auto-installed from `Application` | Scope-control evidence |
| Backup boundary | Factory now rejects evidence directories outside `Context.noBackupFilesDir` | Local source/test evidence; runtime verification still required |
| CI correction | Remote CI exposed default parameters on two `override` methods; removed in `b9d56fa` | Code correction; run `35886198586` succeeded |
| CI coverage | Workflow now compiles `assembleDebugAndroidTest` and invokes the G4-B review script | Static/compile CI evidence; connected device execution remains blocked |
| Device runtime | No `adb`, emulator, AVD, or device is available | BLOCKED |

## Non-claims

This audit does not claim successful Android Room execution, Keystore key generation, biometric or credential invalidation behavior, backup/restore behavior, process-death recovery on a device, hardware-backed protection, production readiness, or G4 acceptance.

## Next safe action

Run `scripts/run-4d6-device-tests.sh` with one authorized device or AVD. Preserve its output and add the device identity, test results, failures, settings, and classification to the runtime evidence package. Keep G4 as **APPROVED DIRECTION / IMPLEMENTATION IN PROGRESS** until runtime evidence and independent security/privacy review are complete.
