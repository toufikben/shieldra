# Group C Core Contracts & Boundaries Implementation Plan

> **For agentic workers:** This plan implements only the supplied Group C scope. It does not authorize feature behavior, production integrations, or Group D.

**Goal:** Establish testable domain contracts and architectural boundaries for events, states, evidence, delivery, authentication, persistence, errors, identity, and time without implementing production behavior.

**Architecture:** Keep Android-specific types out of domain models. Use enums, value/data contracts, interfaces, and a pure state-transition function. Use restricted constructors and source-level boundary validation where a single Gradle module cannot enforce package visibility.

**Tech Stack:** Kotlin 2.2.10 resolved by AGP 9 built-in Kotlin, Gradle 9.7.1, JDK 17, Android SDK Platform 35, Kotlin test JUnit 5.

**Spec:** `docs/authority/phase-1-group-c-supplied-scope.md`.

## Global Constraints

- Group C only: Core Contracts & Boundaries.
- No failed-unlock, motion, SIM, battery, camera, location collection, evidence capture, SMTP, WhatsApp, Telegram, queue, Safe Zone, geofencing, continuous GPS, notifications, billing, ads, or final UI behavior.
- Guards produce Signals only; no Guard creates a confirmed security event.
- Only the ProtectionStateEngine boundary may issue a confirmed security event contract.
- `DEFERRED` is non-terminal; `FAILED_FINAL` is terminal.
- No Android `Context`, `Activity`, `Service`, `BroadcastReceiver`, location, or camera types in core domain contracts.
- No new production dependency.
- Status values are limited to PASS, FAIL, PARTIAL, BLOCKED, and NEEDS REVIEW; never VERIFIED.

## Review Focus

1. Event identity must be independent for each event; test two identities are unequal.
2. Event transitions must reject terminal-state exits and permit DEFERRED reprocessing; test the full allowed matrix.
3. Location freshness must make CURRENT and LAST_KNOWN explicit; test both models and age/accuracy/source fields.
4. Delivery receipts must represent SUCCESS, FAILED, DEFERRED, and SKIPPED without network behavior; test each enum.
5. UI and Guards must not reference SecurityEvent or create delivery/evidence/platform implementations; test with source scans.

## Tasks

### Task 1: Core contracts

Modify `Signal.kt` only as needed and create `SecurityEventContracts.kt`, `EventState.kt`, `IdentityAndTime.kt`, and `ErrorContracts.kt`. Use `internal` construction for `SecurityEvent` and pure transition logic.

### Task 2: Boundary contracts

Create `EvidenceContracts.kt`, `DeliveryContracts.kt`, `AuthenticationContracts.kt`, and `RepositoryContracts.kt`. Keep implementations absent and use only domain-safe Kotlin/JVM types.

### Task 3: Tests

Create `ContractTest.kt` covering Signal values, event identity uniqueness, state transitions, location freshness, delivery statuses, and contract-level authentication operations.

### Task 4: Documentation

Preserve the exact Group C scope, then create core contract inventory, boundary map, state transition documentation, security/authentication/background/permission/localization/billing/ads/compatibility strategy notes, and update the traceability matrix and Group C report.

### Task 5: Verification and delivery

Run the full Gradle build, tests, lint, ShellCheck, YAML lint, forbidden-feature scans, secret scans, and Git integrity checks. Perform a second self-audit, commit, push, verify remote CI, and stop.
