# Group B Architecture & Project Structure Implementation Plan

> **For agentic workers:** This plan implements only the supplied Group B scope and does not authorize Group C or production feature behavior.

**Goal:** Establish a minimal Android project and typed architectural boundaries from scratch without implementing security, detection, evidence, delivery, persistence, or final UI behavior.

**Architecture:** Use one `app` module with package boundaries for UI, domain, detection, evidence, delivery, and platform. The domain layer owns future orchestration boundaries; all other boundaries are declarations only in this phase.

**Tech Stack:** Android Gradle Plugin, Kotlin/JVM, Gradle 9.7.1-compatible toolchain, compile SDK 35, JDK 17, no production runtime dependencies.

**Spec:** `docs/authority/phase-1-group-b-supplied-scope.md`; Product Freeze v5 and complete Phase 1 Sections 1–43 were not supplied and remain a limitation.

## Global Constraints

- Group B only: Architecture & Project Structure.
- Project is created from scratch inside `toufikben/shieldra`.
- No legacy repository, code, database, UI, services, state machine, or dependencies are inspected or reused.
- No security behavior, detection logic, evidence capture, delivery, authentication, production encryption, persistence schema, safe-zone architecture, or final UI.
- Android compile platform is 35 and JDK is 17, matching the Group A evidence.
- Unresolved requirements are recorded as `NEEDS REVIEW` or `BLOCKED`; they are not invented.

## Review Focus

1. Dependency direction must remain UI → Domain → Contracts/abstractions → Platform; the test is a repository scan plus successful compilation.
2. Boundary declarations must not contain production behavior; the test is a source scan for method bodies, TODO placeholders, and feature verbs.
3. Removed Safe Zone and continuous GPS architecture must remain absent; the test is a forbidden-symbol scan.
4. The project must build with the Group A toolchain; the test is `gradle --offline assembleDebug` after dependencies are available.
5. The dependency inventory must match Gradle declarations exactly; the test is a manual diff between `build.gradle.kts` files and `docs/architecture/dependencies.md`.

## Tasks

### Task 1: Authority and architecture documentation

Create the supplied-scope authority record, authority index, project structure, dependency inventory, traceability matrix, and implementation report skeleton. Record unavailable higher-authority documents as `BLOCKED` rather than fabricating them.

### Task 2: Minimal Android project

Create `settings.gradle.kts`, root and app Gradle files, `gradle.properties`, and a minimal manifest. Use one app module and no production dependencies beyond the Android/Kotlin build plugins.

### Task 3: Typed Group B boundaries

Create package-local boundary interfaces and the explicit `Signal` data contract. Do not implement any interface or feature behavior.

### Task 4: Verification

Run Bash/YAML/static checks, forbidden-feature scans, Gradle lint/compile/test/build, inspect the diff, update the report and traceability, then perform an independent self-audit.

### Task 5: Git delivery

Commit only Group B files, push to `main`, verify the remote SHA and workflow, and stop for external review. Group C is not started.
