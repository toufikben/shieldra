# Group C Boundary Map

```text
UI
 │  Domain contracts only
 ▼
Domain
 │  Signal, SecurityEvent, EventState, errors, identity/time abstractions,
 │  ProtectionStateEngine, EventPipeline, AuthenticationGate, repositories
 ├──────────────► Detection
 │                 Guards produce Signals only
 ├──────────────► Evidence
 │                 Capture/validation/vault contracts only
 └──────────────► Delivery
                   Orchestrator/channel/receipt contracts only

Platform
 │  Android-specific infrastructure boundaries only
 └──────────────► Future implementations outside Group C
```

## Rules

UI cannot construct a `SecurityEvent`, mutate event state, perform delivery, access repository implementations, or bypass `AuthenticationGate`. The current UI package remains an empty presentation boundary.

Detection guards can expose signal-producing boundaries but cannot reference `SecurityEvent`, delivery receipts, evidence implementations, or platform implementations. The current guard file contains declarations only.

Evidence and Delivery own their respective contract models. Neither package calls Android APIs or network providers in Group C.

Platform boundaries remain isolated from Domain models. No `Context`, `Activity`, `Service`, `BroadcastReceiver`, camera class, or Android location class appears in the core contracts.

`ProtectionStateEngine` is the only architectural boundary whose contract can create a confirmed event. No implementation is provided in Group C.
