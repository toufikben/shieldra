# Group C Product Strategies

## Localization

The Android manifest already declares RTL support. A later UI phase should use Android per-app locale APIs and keep language selection separate from domain contracts. Both RTL and LTR layouts remain requirements for the future UI phase; Group C adds no UI strings or final screens.

## Billing and advertising

The future billing boundary should represent a lifetime Premium purchase. The future advertising boundary should represent Free-only eligibility. Group C adds no Billing SDK, Ads SDK, purchase flow, advertising behavior, or payment data.

## Compatibility matrix

| Area | Group C baseline | Evidence | Status |
|---|---|---|---|
| JDK | 17 | Group A CI and Group C CI build | NEEDS REVIEW |
| Gradle | 9.7.1 | Wrapper and CI build | NEEDS REVIEW |
| Android compile SDK | 35 | Gradle configuration and CI SDK installation | NEEDS REVIEW |
| Minimum Android SDK | 26 | `app/build.gradle.kts` | NEEDS REVIEW |
| Android-specific runtime behavior | None in Group C | Source scan | NOT APPLICABLE |

## Dependency discipline

No production dependency was added. The existing build and test tooling is unchanged. Room, WorkManager, Keystore helpers, networking, delivery providers, billing, ads, camera, and location libraries remain deferred.
