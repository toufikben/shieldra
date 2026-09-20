# Group C Core Contract Inventory

| Contract | Responsibility | Owner layer | Inputs | Outputs | Forbidden responsibilities | Future phase |
|---|---|---|---|---|---|---|
| `Signal` | Carry typed guard observations | Domain | Type, strength, timestamp, context | Immutable data | Creating SecurityEvent or deciding security state | Group D detection adapters |
| `SecurityEvent` | Represent a confirmed event identity and references | Domain | Event ID, type, timestamp, state, references | Immutable event model | Detection, capture, delivery, authentication, persistence | Later orchestration phase |
| `EventState` / `transition` | Constrain event lifecycle transitions | Domain | Current and requested state | Allowed or rejected result | Smart Queue, retries, network, UI behavior | Later event engine |
| `ProtectionStateEngine` | Sole architectural owner for confirmed-event creation | Domain | Signal, Clock, IdentityProvider | SecurityEvent contract | Implementing detection rules or security decisions now | Later state engine |
| `EventPipeline` | Boundary separating signal/event, evidence, and delivery | Domain | Contract-level orchestration inputs | Interface only | UI, camera, network, channel delivery | Later orchestration |
| `Evidence` | Represent photo, location, or combined evidence | Evidence | Typed references and location metadata | Immutable evidence model | Camera, location collection, persistence, encryption implementation | Later evidence phase |
| `EvidenceValidator` / `EncryptedVault` | Validate and store evidence behind boundaries | Evidence | Evidence contract | Interface/result contract | Production capture or encryption implementation | Later evidence phase |
| `DeliveryReceipt` | Represent channel outcome without claiming sending | Delivery | Channel, status, timestamps, failure reason | Immutable receipt | Network calls, Smart Queue, provider integration | Later delivery phase |
| `AuthenticationGate` | Boundary for sensitive operations | Domain | SensitiveOperation | AuthenticationDecision | UI implementation or production authentication | Later security phase |
| Repository interfaces | Abstract persistence ownership | Domain | Future domain models | Interface only | Fake persistence, schema, mock data | Later persistence phase |
| `ContractError` | Categorize cross-layer failures | Domain | Error type and optional message | Immutable error model | Throwing policy, recovery behavior, logging | Later implementation phases |
| `Clock` / `IdentityProvider` | Inject time and identity for deterministic contracts | Domain | Implemented provider | Instant/EventId | Direct platform calls in core contracts | Later composition root |
