# ADR-002

**Title:** Keep Group C event transitions pure and inject identity/time sources

**Decision:** Represent event state changes with a pure `transition` function and keep event identity and time behind `IdentityProvider` and `Clock` abstractions.

**Context:** Group C requires deterministic, testable contracts without implementing the production state engine, retries, Smart Queue, authentication, or Android behavior.

**Options Considered:**

1. Pure transition function with injected identity/time abstractions.
2. Mutable event methods that call UUID and system clock APIs directly.
3. A state-machine framework dependency.

**Advantages:**

The chosen approach is deterministic in unit tests, has no production dependency, makes terminal and deferred rules explicit, and keeps platform APIs outside the Domain contract layer.

**Disadvantages:**

A later composition root must provide identity and time implementations. The current single module cannot enforce every package boundary through Gradle visibility.

**Chosen Option:** Option 1.

**Reason:** It provides the smallest testable contract surface that satisfies Group C without creating feature behavior or adding framework coupling.

**Consequences:**

`SecurityEvent` construction is internal to the module and owned architecturally by `ProtectionStateEngine`. The current repository contains no implementation of that engine.

**Evidence:** `EventState.kt`, `SecurityEventContracts.kt`, `IdentityAndTime.kt`, `ContractTest.kt`, and the Group C CI run.

**Review Status:** PENDING EXTERNAL REVIEW
