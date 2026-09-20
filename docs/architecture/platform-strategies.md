# Group C Platform Strategies

## Background execution

A future implementation may evaluate a Foreground Service for user-visible continuous protection and WorkManager for deferrable, retryable work. Boot and recovery behavior must account for Android boot restrictions. Doze and OEM battery policies can interrupt background work, and no background behavior is guaranteed by this contract-only phase. The current project contains only platform boundary names and no service, receiver, worker, or scheduling implementation.

## Permission strategy

Group C adds no Android permissions. Camera, location, SMS, contacts, and microphone permissions are not required to compile the contracts and remain deferred until a later approved feature phase demonstrates a real need.

## Android boundary

Domain models contain only Kotlin/JVM and `java.time` types. Android `Context`, `Activity`, `Service`, `BroadcastReceiver`, camera APIs, and Android location classes are excluded from the core contract layer.
