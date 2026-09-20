# Shieldra Project Structure — Group C

## Module map

The project uses one Android `app` module. Package boundaries are explicit in source and documentation; separate Gradle modules remain deferred until implementation behavior justifies stronger module-level visibility.

```text
app/src/main/kotlin/com/shieldra/
├── ui/PresentationBoundary.kt
├── domain/
│   ├── AuthenticationContracts.kt
│   ├── DomainBoundaries.kt
│   ├── ErrorContracts.kt
│   ├── EventState.kt
│   ├── IdentityAndTime.kt
│   ├── RepositoryContracts.kt
│   ├── SecurityEventContracts.kt
│   └── Signal.kt
├── detection/GuardBoundaries.kt
├── evidence/
│   ├── EvidenceContracts.kt
│   └── platform boundary remains in PlatformBoundaries.kt
├── delivery/DeliveryContracts.kt
└── platform/PlatformBoundaries.kt

app/src/test/kotlin/com/shieldra/domain/
├── ContractTest.kt
└── SignalTest.kt
```

## Dependency direction

The intended direction is **UI → Domain → Detection/Evidence/Delivery → Platform**. Domain models use Kotlin/JVM and `java.time` only. Detection guards expose signal-producing boundaries. Evidence and Delivery expose contract models without implementation. Platform names remain boundary declarations only.

`ProtectionStateEngine` is the only boundary that exposes the contract operation for creating a confirmed `SecurityEvent`. No implementation exists in Group C.

## Explicit non-goals

This structure does not implement failed-unlock detection, motion detection, SIM detection, battery emergency behavior, camera, location collection, evidence capture, SMTP, WhatsApp, Telegram, Smart Queue, Safe Zones, geofencing, continuous GPS, production notifications, production billing, Ads SDK, final UI, production encryption, or production authentication.
