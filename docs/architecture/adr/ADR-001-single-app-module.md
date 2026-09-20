# ADR-001

**Title:** Use one Android app module for the Group B architecture foundation

**Decision:** Create one `app` module and express architecture boundaries as packages and typed interfaces.

**Context:** Group B requires architecture and project structure, but explicitly forbids production feature behavior. Multiple modules would add build and dependency complexity before there are implementations to isolate.

**Options Considered:**

1. One `app` module with explicit package boundaries.
2. Separate Gradle modules for UI, domain, detection, evidence, delivery, and platform.

**Advantages:**

The single module is minimal, easy to build on the verified runner, and makes the conceptual dependency direction visible without introducing premature Gradle coupling.

**Disadvantages:**

Package boundaries are not yet enforced by Gradle module visibility. That enforcement can be reconsidered when behavior and module-level dependencies are authorized.

**Chosen Option:** Option 1.

**Reason:** It is the smallest structure that satisfies the supplied Group B architecture requirement without implementing later-phase features.

**Consequences:**

The repository has a compileable Android foundation and explicit boundaries. A future reviewer may require module separation once the authority documents specify implementation scope.

**Evidence:** `docs/architecture/project-structure.md`, the Gradle build, and the Group B CI run after push.

**Review Status:** PENDING EXTERNAL REVIEW
