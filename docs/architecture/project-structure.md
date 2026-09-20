# Group B Project Structure

## Module map

The project uses one Android `app` module. A multi-module layout is not justified before behavior exists and would add build complexity without improving the present boundary contract.

```text
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   └── kotlin/com/shieldra/
    │       ├── ui/PresentationBoundary.kt
    │       ├── domain/
    │       │   ├── DomainBoundaries.kt
    │       │   └── Signal.kt
    │       ├── detection/GuardBoundaries.kt
    │       ├── evidence/EvidenceBoundaries.kt
    │       ├── delivery/DeliveryBoundaries.kt
    │       └── platform/PlatformBoundaries.kt
    └── test/kotlin/com/shieldra/domain/SignalTest.kt
```

## Dependency direction

The allowed conceptual direction is **UI → Domain → Contracts/abstractions → Platform**. The Group B source contains declarations only, so no runtime dependency is introduced between the packages. Detection, Evidence, and Delivery do not depend on UI. Platform boundaries do not own business rules.

| Package | Responsibility | Allowed dependencies | Forbidden dependencies |
|---|---|---|---|
| `ui` | Presentation boundary only | Domain contracts | Security rules, state decisions, detection, delivery, Android feature behavior |
| `domain` | Future orchestration boundaries and the Signal contract | Standard Kotlin/JVM types | Android UI, platform implementations, delivery providers, detection implementations |
| `detection` | Future guard boundaries | Domain Signal type only when a future contract requires it | UI, delivery, evidence capture, confirmed security events |
| `evidence` | Future capture, validation, and vault boundaries | Domain abstractions only when later specified | Camera, location, production encryption, security-state decisions |
| `delivery` | Future queue and channel boundaries | Domain abstractions only when later specified | SMTP, WhatsApp, Telegram, security-state decisions |
| `platform` | Future Android integration boundaries | Android SDK in later phases | Business rules, security decisions, feature implementations |

## Explicit non-goals

This structure does not implement a state engine, event lifecycle, guard detection, evidence capture, encryption, authentication, database schema, delivery, notifications, safe zones, geofencing, continuous GPS, billing, advertising, or final UI.
