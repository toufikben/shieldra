# Group B Dependency Inventory

The Group B foundation adds no production runtime library dependency. The Android Gradle Plugin supplies built-in Kotlin support for this project; a separate Kotlin Android Gradle plugin is intentionally not declared because AGP 9 rejects it. The test-only Kotlin JUnit 5 adapter supports the real Signal contract tests.

| Name | Version | Purpose | License | Maintenance | Security | Required/Optional |
|---|---|---|---|---|---|---|
| Android Gradle Plugin | 9.0.0 | Android project build integration and built-in Kotlin support | Apache-2.0 | Google-maintained | Evaluated through Gradle resolution and CI build | Required build tool |
| Kotlin compiler support via AGP | 2.2.10 resolved | Kotlin compilation supplied by AGP 9 | Apache-2.0 | JetBrains/Google-maintained | Evaluated through compile and test tasks | Required build tool |
| Kotlin test JUnit 5 adapter | 2.2.10 resolved through `kotlin("test-junit5")` | Unit test annotations and JUnit 5 adapter | Apache-2.0 | JetBrains-maintained | Test-only; no production runtime exposure | Required test-only |

No Room, WorkManager, AndroidX, Firebase, network, delivery, analytics, billing, advertising, or encryption dependency is added in Group B because no production behavior is authorized in this scope.

The project uses Gradle 9.7.1 and JDK 17, matching Group A evidence. These versions and the AGP choice remain `NEEDS REVIEW` until external review compares them with the complete authority documents.
