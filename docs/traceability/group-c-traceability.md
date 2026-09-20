# Group C Requirement Traceability

The complete Product Freeze v5 and Phase 1 Sections 1–43 remain unavailable. IDs below are derived only from the supplied Group C scope.

| ID | Requirement | Source | Action / Decision | Evidence | Status | Notes |
|---|---|---|---|---|---|---|
| GC-SCOPE-01 | Define Signal and closed strength values | `phase-1-group-c-supplied-scope.md:90-109` | Retained typed Signal and tested STRONG/MEDIUM/WEAK | `Signal.kt`, `ContractTest.kt`, CI test step | NEEDS REVIEW | No event creation from Signal |
| GC-SCOPE-02 | Create independent SecurityEvent UUID and event fields | `phase-1-group-c-supplied-scope.md:113-130` | Added internal construction with injected EventId | `SecurityEventContracts.kt`, ContractTest | NEEDS REVIEW | No production engine |
| GC-SCOPE-03 | Define states and valid transitions | `phase-1-group-c-supplied-scope.md:133-153` | Added enum and pure transition function | `EventState.kt`, `state-transitions.md`, ContractTest | NEEDS REVIEW | Deferred is non-terminal; terminal exits rejected |
| GC-SCOPE-04 | Guards produce Signals only | `phase-1-group-c-supplied-scope.md:157-171` | Kept guard declarations empty and source-scanned them | `GuardBoundaries.kt`, self-audit command | NEEDS REVIEW | No detection logic |
| GC-SCOPE-05 | ProtectionStateEngine owns confirmed event creation | `phase-1-group-c-supplied-scope.md:175-187` | Added contract-only authority method; no implementation | `DomainBoundaries.kt`, boundary-map.md | NEEDS REVIEW | Package enforcement is source-audited |
| GC-SCOPE-06 | Evidence and location contracts distinguish freshness | `phase-1-group-c-supplied-scope.md:218-265` | Added Evidence variants and CURRENT/LAST_KNOWN model | `EvidenceContracts.kt`, ContractTest | NEEDS REVIEW | No capture or location collection |
| GC-SCOPE-07 | Delivery and receipt statuses | `phase-1-group-c-supplied-scope.md:269-309` | Added typed channels, outcomes, and receipt | `DeliveryContracts.kt`, ContractTest | NEEDS REVIEW | No network or send claim |
| GC-SCOPE-08 | Authentication boundary | `phase-1-group-c-supplied-scope.md:313-328` | Added sensitive operations and decision enum | `AuthenticationContracts.kt`, ContractTest | NEEDS REVIEW | No authentication implementation |
| GC-SCOPE-09 | Repository and error boundaries | `phase-1-group-c-supplied-scope.md:332-364` | Added empty repository interfaces and typed error categories | `RepositoryContracts.kt`, `ErrorContracts.kt` | NEEDS REVIEW | No fake persistence |
| GC-SCOPE-10 | Deterministic time and identity | `phase-1-group-c-supplied-scope.md:368-374` | Added injectable `Clock` and `IdentityProvider` | `IdentityAndTime.kt`, ADR-002 | NEEDS REVIEW | No system API implementation in Domain |
| GC-SCOPE-11 | Documentation and traceability | `phase-1-group-c-supplied-scope.md:447-497` | Added inventory, maps, state docs, strategies, and this matrix | Repository files and Git diff | NEEDS REVIEW | External review required |
| GC-SCOPE-12 | No Safe Zone or Geofence contract | `phase-1-group-c-supplied-scope.md:411-424` | Added no such model and ran forbidden-symbol scan | Self-audit output, source tree | PASS | Evidence from fresh scan required at delivery |
| AUTHORITY-01 | Product Freeze v5 | Task authority order | Not supplied; retained as blocked | `authority-index.md` | BLOCKED | Not substituted by Group C attachment |
| AUTHORITY-02 | Complete Phase 1 Specification | Task authority order | Not supplied; retained as blocked | `authority-index.md` | BLOCKED | Group C attachment is scope-specific |
