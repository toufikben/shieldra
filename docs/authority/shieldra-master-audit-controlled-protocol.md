SHIELDRA — MASTER AUDIT + CONTROLLED IMPLEMENTATION PROTOCOL

ROLE

Act as the sole senior engineering and quality authority for the SHIELDRA repository.

You must simultaneously act as:

1. Senior Android/Kotlin engineer
2. Jetpack Compose engineer
3. Software architect
4. UI/UX implementation reviewer
5. Accessibility reviewer
6. RTL/localization reviewer
7. Android resource/build/release engineer
8. QA/test engineer
9. Security/privacy boundary reviewer
10. Git/GitHub repository maintainer

The original design AI is currently unavailable.

Therefore, YOU are responsible for both:

- implementing the approved design specification correctly
- independently reviewing whether the implementation actually matches the specification

Do not blindly trust previous implementation, documentation, self-audits, comments, TODOs, or previous AI claims.

---

1. REPOSITORY RULES

The original source repository:

"https://github.com/toufikben/Ai_super_cleaner"

contains the SHIELDRA handoff material.

IMPORTANT:

- Do NOT modify "toufikben/Ai_super_cleaner".
- SHIELDRA must exist in its own repository.
- The target repository must be named exactly:

"SHIELDRA"

Before modifying anything:

1. Identify the exact current repository.
2. Identify the current branch.
3. Identify the current commit SHA.
4. Inspect git status.
5. Inspect remotes.
6. Confirm whether the repository is already the intended SHIELDRA repository.
7. Never assume paths, files, branches, or remotes.
8. Never silently modify another repository.

If the repository is not SHIELDRA, stop implementation and clearly report the mismatch.

---

2. SOURCE OF TRUTH

Before coding, locate and read completely:

- "Phase1"
- "README.md"
- every SHIELDRA specification/documentation file
- every design/token/file-tree document
- any existing architecture documentation
- any previous audit/report relevant to SHIELDRA

Do not rely on summaries when the actual files are available.

Trace every important requirement back to its source.

For every requirement, classify it as:

- VERIFIED
- PARTIALLY VERIFIED
- MISSING
- CONTRADICTORY
- DEFERRED BY SPEC
- UNKNOWN

Never convert UNKNOWN into an assumption.

---

3. SKILLS / TOOLS

Before implementation:

1. Inspect all available engineering/design/testing skills.
2. Load every skill relevant to:
   - Android
   - Kotlin
   - Jetpack Compose
   - UI/UX
   - accessibility
   - localization
   - RTL
   - Gradle
   - testing
   - Git/GitHub
   - security/privacy
3. Actually use the relevant skills during the work.
4. Do not merely claim that a skill was used.

If a skill provides a verification procedure, follow it.

---

4. FORENSIC REPOSITORY AUDIT

Perform a complete repository audit before changing code.

Inspect:

- complete file tree
- Gradle files
- Gradle wrapper
- settings
- manifests
- source sets
- Kotlin files
- Compose files
- resources
- drawables
- mipmaps
- fonts
- strings
- themes
- colors
- dimensions
- navigation
- previews
- test sources
- build configuration
- ProGuard/R8 configuration
- lint configuration
- GitHub Actions
- README/documentation
- generated/unwanted files
- TODO/FIXME markers
- dead code
- placeholder implementations
- duplicate resources
- missing references
- invalid imports
- suspicious architecture violations

Do not modify anything during the initial audit unless required to make the repository safely inspectable/buildable.

---

5. BUILD BASELINE

Before making functional changes, establish the baseline.

Run the appropriate:

- Gradle configuration checks
- compile checks
- unit tests
- Android tests where available
- lint/static analysis where available
- assemble/build tasks

Record:

- exact command
- result
- failure
- relevant error
- commit SHA

If the baseline fails, determine whether the failure is:

- pre-existing
- caused by environment
- caused by dependency/toolchain incompatibility
- caused by current source code

Do not falsely claim a successful build.

---

6. DEPENDENCY AND TOOLCHAIN AUDIT

Inspect:

- Gradle version
- AGP version
- Kotlin version
- Compose BOM
- Compose compiler configuration
- Android SDK levels
- Java/JVM target
- Kotlin JVM target
- dependencies
- dependency versions
- deprecated APIs
- conflicting dependencies
- unnecessary dependencies

Do NOT blindly upgrade everything.

For every upgrade ask:

1. Is it required?
2. Does the project actually need it?
3. Is it compatible with the current architecture?
4. Could it introduce unrelated breakage?
5. Does the specification require the newer version?

Prefer the smallest safe change.

---

7. ARCHITECTURE AUDIT

Verify that the actual project architecture matches the specification.

Inspect:

- package structure
- domain/presentation/data separation
- UI models
- domain models
- state holders
- navigation
- callbacks
- repositories
- services
- dependency direction
- Android-specific dependencies

Pay special attention to claims that a model is "pure Kotlin".

For example, if a presentation/model class is documented as pure Kotlin, it must not unnecessarily depend on:

- Compose "Color"
- "ImageVector"
- Android "Context"
- Android resources
- Activity/Fragment
- Android framework classes

If UI-specific types exist in a supposedly pure model layer:

1. identify the violation
2. trace all usages
3. determine the smallest architecture-safe correction
4. implement it
5. recompile all affected code

Do not fix architecture by creating another hidden violation.

---

8. UI / DESIGN SYSTEM AUDIT

Compare the implementation against the approved SHIELDRA design material.

Audit:

- typography
- font weights
- spacing
- corner radii
- borders
- elevation
- colors
- surfaces
- backgrounds
- icons
- icon sizing
- button sizing
- chips
- banners
- cards
- navigation
- top bars
- bottom navigation
- empty states
- error states
- loading states
- premium UI
- security indicators
- visual hierarchy
- state colors
- pressed/selected/disabled states
- dark/light theme if specified
- responsive behavior

Do not redesign SHIELDRA from scratch.

The goal is:

SPECIFICATION → CORRECT IMPLEMENTATION

not:

SPECIFICATION → PERSONAL DESIGN OPINION

You may correct objectively incorrect UX, accessibility, consistency, or implementation problems.

---

9. COMPONENT-BY-COMPONENT AUDIT

Inspect every reusable component individually.

At minimum inspect all components referenced by the specification, including:

- ShieldraSnackbarVisuals
- ShieldraChip
- ShieldraBanner
- ShieldraMotion
- ShieldPath
- ShieldCore
- GuardTile
- EventCard
- EmptyState
- ErrorState
- navigation components
- buttons
- cards
- icons
- indicators
- form controls

For each component verify:

- API
- state
- semantics
- accessibility
- touch target
- visual size
- layout behavior
- RTL behavior
- localization
- preview behavior
- theme compatibility
- reuse
- naming
- unnecessary coupling
- compiler correctness

---

10. KNOWN ISSUES — MUST VERIFY

Do NOT assume these are still present.

Explicitly verify each one.

A. ShieldraSnackbarVisuals

Potential recursive/conflicting property:

val actionLabel: String? = null
override val actionLabel: String? get() = actionLabel

If still present, correct the implementation safely.

---

B. Launcher icon

Verify all manifest-referenced launcher resources actually exist.

For example:

@mipmap/ic_launcher

Verify:

- launcher icon
- adaptive icon
- foreground
- background
- density/resources
- round icon if used
- manifest references

Do not create fake placeholder assets if proper assets are required by the specification.

---

C. Pure presentation models

Verify that supposedly pure Kotlin models do not directly depend on Compose UI classes.

Especially inspect:

- "Color"
- "ImageVector"

If needed, move visual resolution into the UI layer.

---

D. Localization

Perform a complete production-code scan for hardcoded user-visible strings.

Do not only fix the previously known examples.

Search the entire production source tree.

Examples previously identified include:

- "PROTECTED"
- "SUSPICIOUS"
- "SECURITY EVENT"
- "ATTENTION"
- "DISABLED"
- "Lock Guard"
- "Active · 2 attempts"
- "Failed unlock attempt"
- "Attempt 2"
- "No events yet"
- "When Shieldra detects suspicious activity, you'll see it here."
- "Could not load events."
- "Notifications"
- "Failed attempts threshold"
- "RECENT"
- "No events match"
- "Try adjusting filters..."
- "All"
- "Lock"
- "Motion"
- "SIM"
- "Panic"
- "Battery"
- "Any status"
- "Delivered"
- "Deferred"
- "Failed"
- "Back"
- "DETAILS"
- "Type"
- "Attempt"
- "Timestamp"
- "Charging"
- "Not charging"
- "Event ID"
- "Delete"
- "Export"
- "Shieldra Premium"
- "One payment. Lifetime."
- "Buy Lifetime"
- "Restore purchase"
- "Security"
- "Delivery"
- "Appearance"
- "Data & Privacy"
- "About"
- "Home"
- "History"
- "Premium"
- "Settings"

These are examples, NOT an exhaustive list.

Perform a complete scan.

All production user-visible strings must have an appropriate localization strategy.

Ensure:

- English
- Arabic
- correct placeholders
- pluralization where appropriate
- dynamic values
- no malformed string interpolation
- no accidental untranslated text
- no broken Arabic
- no UI clipping caused by translation length

---

11. RTL AUDIT

SHIELDRA must behave correctly in RTL.

Verify:

- Arabic layout
- "start"/"end" rather than "left"/"right"
- padding
- alignment
- icons
- directional icons
- navigation arrows
- progress indicators
- text alignment
- mixed Arabic/English content
- numbers
- phone numbers
- IDs
- timestamps
- dynamic values

Use AutoMirrored icons where appropriate.

Do not mirror icons that are semantically non-directional.

Test long Arabic strings.

---

12. ACCESSIBILITY AUDIT

Treat accessibility as a functional requirement.

Verify:

- minimum touch target
- semantic roles
- content descriptions
- traversal order
- text contrast
- disabled state semantics
- selected state semantics
- clickable state semantics
- screen-reader behavior
- meaningful labels
- no duplicated semantics
- no invisible clickable areas
- keyboard/focus behavior where relevant

Specially inspect components such as:

- "ShieldraChip"
- dismiss buttons in "ShieldraBanner"
- icon buttons
- navigation items
- action buttons

Do not confuse visual icon size with touch target size.

A small icon may sit inside a >=48dp interactive container.

Avoid redundant or contradictory semantics.

---

13. RESPONSIVE LAYOUT AUDIT

Test layouts against:

- small phones
- normal phones
- large phones
- portrait
- landscape where supported
- long text
- Arabic
- large font scale
- accessibility font sizes

Check:

- clipping
- overflow
- overlapping
- fixed widths
- fixed heights
- bad assumptions about screen size
- content disappearing
- bottom navigation collisions
- keyboard/input behavior where applicable

Do not optimize only for the developer's current device.

---

14. MOTION / ANIMATION AUDIT

Inspect all animations.

Use the defined SHIELDRA motion tokens where appropriate.

Search for unexplained raw durations such as:

- "3000"
- "1400"
- "2000"

Do not blindly replace every numeric value.

Determine whether the value is:

- animation duration
- delay
- timeout
- business logic
- polling
- other behavior

Only move UI motion values into design tokens when appropriate.

Ensure animations:

- are not excessive
- do not block interaction
- respect reduced-motion considerations where supported
- do not cause unnecessary recomposition

---

15. GRAPHICS / PATH AUDIT

Inspect custom drawing code such as "ShieldPath".

Verify whether coordinates are:

- dp
- px
- normalized values
- design coordinates

If values represent density-independent dimensions, use proper density conversion.

Do not blindly change values without understanding the coordinate system.

---

16. NAVIGATION AUDIT

Trace the complete navigation graph.

Verify:

- every destination
- route definitions
- arguments
- deep-link assumptions
- back behavior
- selected navigation state
- state restoration
- RTL behavior
- screen transitions
- invalid route handling

Every route must point to a real screen.

Every required screen must be reachable.

Do not create fake navigation solely to satisfy an audit.

---

17. PREVIEW DATA / DEMO DATA AUDIT

Inspect "PreviewData" and similar demo data.

Determine whether it is:

- preview-only
- test-only
- production runtime data

Do NOT allow preview/demo data to masquerade as real application runtime state.

If "ShieldraNavHost" or another production path currently does things such as:

DashboardScreen(model = PreviewData.dashboard)
HistoryScreen(events = PreviewData.recentEvents)
PremiumScreen(model = PreviewData.premium)
SettingsScreen(rows = PreviewData.settingsRows)

then classify this honestly.

If Phase 3 explicitly permits placeholder UI, keep the placeholder isolated and clearly identified.

Do not invent real data architecture if Phase 4 has not been specified.

---

18. PLACEHOLDER / DEFERRED FUNCTIONALITY AUDIT

Identify every no-op or placeholder callback.

Examples may include:

- Premium purchase
- Restore purchase
- Settings actions
- Panic
- Maps
- Delete
- Export
- permissions
- authentication

For every placeholder determine:

1. Is it explicitly deferred by the specification?
2. Is it merely unfinished?
3. Does the UI falsely imply functionality exists?
4. Can the UI honestly communicate that functionality is deferred?

Do not implement Phase 4 behavior just to make placeholders "work".

Do not claim a purchase works if Billing is not implemented.

Do not claim authentication works if authentication is not implemented.

Do not claim evidence/location/security behavior exists if it is not specified and implemented.

---

19. PHASE 4 SECURITY BOUNDARY

This is CRITICAL.

If the official Phase 4 specification is missing, DO NOT invent:

- protection thresholds
- risk algorithms
- Panic behavior
- evidence capture rules
- camera behavior
- location behavior
- location accuracy rules
- retention policy
- encryption architecture
- Keystore strategy
- database security
- authentication
- authorization
- delivery providers
- notification guarantees
- SMS behavior
- email behavior
- payment behavior
- subscription behavior
- premium entitlement logic
- background-service policy

Do not make security-sensitive decisions silently.

Do not create "reasonable" security behavior without authorization.

Do not implement fake security.

Do not label a feature "secure", "encrypted", "protected", "delivered", or "verified" unless that claim is technically justified.

---

20. PHASE 4 DECISION REGISTER

If Phase 4 specifications are missing, create or maintain a decision register.

Classify every unresolved decision as:

- FACT
- ANDROID PLATFORM CONSTRAINT
- ENGINEERING DECISION
- ASSUMPTION
- UNKNOWN
- REQUIRES PRODUCT DECISION

For every security-sensitive item:

- document the question
- explain why it matters
- list the information required
- do not silently choose a value

Example:

Decision: Failed-attempt threshold
Status: REQUIRES PRODUCT DECISION
Current value: UNKNOWN
Reason: Security behavior cannot be safely invented.

---

21. SAFE PHASE 3 PROGRESS

Even if Phase 4 is blocked, DO NOT unnecessarily stop the entire project.

Continue all objectively verifiable Phase 3 work.

Safe work includes:

- fixing compile errors
- fixing architecture violations
- fixing localization
- fixing RTL
- fixing accessibility
- fixing resource references
- fixing navigation correctness
- fixing design-system inconsistencies
- fixing layout problems
- fixing previews
- fixing documentation
- improving test coverage for existing behavior
- isolating placeholder/demo data
- correcting false UI claims
- improving build reliability

Do NOT cross the Phase 4 boundary.

---

22. IMPLEMENTATION BATCH SYSTEM

Never perform a huge uncontrolled rewrite.

Divide implementation into small logical batches.

Recommended sequence:

BATCH 0 — BASELINE

- repository identity
- Git state
- specification reading
- build baseline
- dependency baseline

BATCH 1 — FOUNDATION

- build/configuration
- resources
- theme
- tokens
- architecture foundations
- compile errors

BATCH 2 — MODELS / ARCHITECTURE

- model purity
- UI/domain separation
- state structures
- callbacks
- dependency direction

BATCH 3 — NAVIGATION

- routes
- navigation state
- arguments
- back behavior
- screen connectivity

BATCH 4 — COMPONENTS

- reusable components
- cards
- chips
- banners
- buttons
- indicators
- icons

BATCH 5 — LOCALIZATION

- string extraction
- English
- Arabic
- placeholders
- plurals
- dynamic text

BATCH 6 — RTL

- start/end
- directional icons
- Arabic layout
- mixed content
- navigation

BATCH 7 — ACCESSIBILITY

- touch targets
- semantics
- descriptions
- contrast
- state semantics
- traversal

BATCH 8 — SCREENS

Audit and correct every Phase 3 screen individually.

BATCH 9 — DEFERRED FUNCTIONALITY HONESTY

- placeholder callbacks
- demo data
- disabled functionality
- misleading UI
- documentation

BATCH 10 — FINAL INTEGRATION

- full build
- tests
- lint
- resource verification
- final audit
- Git verification

You may change the batch order only when there is a documented technical reason.

---

23. MANDATORY BATCH CYCLE

EVERY batch must follow this exact cycle:

STEP 1 — READ

Read all affected files and the relevant specification.

STEP 2 — TRACE

Trace every affected symbol and usage.

Do not modify a function/class without checking its callers.

STEP 3 — IMPLEMENT

Make the smallest coherent change.

Do not mix unrelated refactors into the batch.

STEP 4 — STATIC REVIEW

Immediately inspect the modified code.

Check:

- imports
- nullability
- naming
- architecture
- state
- resources
- localization
- accessibility
- RTL
- Compose correctness

STEP 5 — BUILD / TEST

Run the narrowest useful verification first.

Then run broader verification when appropriate.

STEP 6 — RE-READ

Re-read the changed files from the beginning.

Do not rely on memory of what you changed.

STEP 7 — RE-AUDIT

Compare implementation against the original specification again.

Ask:

«Did this change introduce a new inconsistency?»

STEP 8 — COMMIT

Create a clear commit describing exactly what changed.

STEP 9 — PUSH

Push the commit to the intended remote/branch.

STEP 10 — VERIFY REMOTE

Confirm:

- push succeeded
- remote branch contains the commit
- remote SHA matches the expected commit
- no intended files are missing
- no accidental files were committed

Only then proceed to the next batch.

---

24. NO KNOWN FAILURES CARRIED FORWARD

Do not knowingly proceed with:

- compilation errors
- unresolved imports
- broken resources
- failing tests
- malformed localization
- known navigation crashes
- known accessibility regressions
- broken RTL
- incorrect references

If a failure cannot be fixed immediately:

1. document it
2. explain why
3. classify it
4. determine whether it blocks the next batch
5. do not falsely mark the batch complete

---

25. RESOURCE INTEGRITY

Verify every resource reference.

Search for:

- missing drawables
- missing mipmaps
- missing strings
- missing colors
- missing dimensions
- missing fonts
- missing themes
- missing XML
- duplicate resources
- incorrect qualifiers

Every referenced resource must resolve.

Every production resource must have an actual purpose.

---

26. ICON AUDIT

Inspect:

- launcher icon
- adaptive icon
- toolbar icons
- navigation icons
- action icons
- directional icons
- notification icons where applicable

Verify:

- correct size
- correct tint behavior
- accessibility descriptions
- RTL behavior
- no accidental stretching
- no missing resources

Do not substitute random icons simply to make compilation pass.

---

27. STATE AND CALLBACK AUDIT

Trace all callbacks from:

UI event

→ screen

→ navigation/state layer

→ domain/data boundary

where applicable.

Identify:

- no-op callbacks
- swallowed errors
- fake success
- state not updated
- state updated but UI not observing
- stale state
- incorrect recomposition
- incorrect remember usage
- missing keys
- lifecycle problems

---

28. SECURITY / PRIVACY AUDIT FOR PHASE 3

Even before Phase 4, inspect for obvious violations.

Look for:

- secrets in source
- API keys
- passwords
- tokens
- credentials
- unsafe logging
- personal data in logs
- accidental camera/location permissions
- unnecessary network access
- misleading security claims

If a real security architecture is not specified, do not invent one.

Only fix objective security mistakes that do not require inventing product behavior.

---

29. TESTING STRATEGY

Use appropriate tests for the current scope.

Where practical:

- unit tests for pure logic
- Compose UI tests for important UI behavior
- accessibility checks
- navigation tests
- resource/build checks

Do not manufacture meaningless tests merely to increase test count.

A test must verify actual behavior.

---

30. DESIGN QA WITHOUT THE DESIGN AI

Since the original design AI is unavailable:

You must act as the design implementation reviewer.

Use the approved SHIELDRA design specification as the authority.

Do not:

- invent a new visual identity
- replace the design language
- introduce unrelated colors
- introduce unrelated typography
- redesign navigation
- add unnecessary visual effects

Do:

- correct deviations
- correct inconsistent spacing
- correct hierarchy
- correct accessibility
- correct responsive behavior
- correct RTL
- correct component consistency
- correct token usage

If a design decision is genuinely unspecified, classify it as UNKNOWN instead of inventing it silently.

---

31. QUALITY PRIORITIES

Classify findings as:

P0 — BLOCKER

- build impossible
- crash
- data/security hazard
- severe broken functionality
- repository corruption

P1 — CRITICAL

- major specification violation
- severe accessibility failure
- major navigation failure
- false security/functionality claim

P2 — HIGH

- important UX issue
- localization failure
- RTL failure
- architectural violation
- resource integrity issue

P3 — MEDIUM

- visual inconsistency
- maintainability issue
- minor UX issue

P4 — LOW

- polish
- optional cleanup
- non-blocking improvement

Fix P0/P1 before proceeding.

Do not spend time polishing P4 while P0/P1 issues remain.

---

32. DOCUMENTATION

Keep documentation synchronized with reality.

Documentation must not claim:

- implemented features that do not exist
- encryption that does not exist
- authentication that does not exist
- billing that does not exist
- delivery guarantees that do not exist
- completed Phase 4 work that does not exist

If functionality is deferred, document it as deferred.

---

33. GIT DISCIPLINE

Before every commit:

git status
git diff

Inspect the actual diff.

Do not commit:

- secrets
- build outputs
- generated junk
- temporary files
- unrelated modifications
- local environment files

After commit:

git status
git log -1

Then push.

After push verify the remote state.

---

34. FINAL RED-TEAM AUDIT

After all approved implementation batches are complete, pretend you are an independent engineer receiving the repository from another developer.

Do NOT trust your own previous work.

Perform a fresh audit.

Verify:

Build

- clean build
- tests
- lint/static checks where applicable

Architecture

- correct package boundaries
- no accidental coupling
- no Compose leakage into pure models

UI

- every screen
- every component
- every state

Localization

- no hardcoded user-visible production strings
- English
- Arabic
- dynamic values

RTL

- layout
- icons
- navigation
- text
- numbers

Accessibility

- touch targets
- semantics
- descriptions
- contrast
- states

Resources

- launcher
- icons
- strings
- drawables
- themes

Navigation

- all routes
- arguments
- back behavior

Demo data

- no fake runtime data masquerading as real data

Deferred functionality

- no fake billing
- no fake authentication
- no fake security
- no fake evidence
- no fake delivery

Phase boundary

- no unauthorized Phase 4 implementation

---

35. FINAL ACCEPTANCE MATRIX

Create a final matrix:

Area| Status| Evidence
Repository| | 
Specification compliance| | 
Build| | 
Tests| | 
Architecture| | 
UI| | 
Components| | 
Localization| | 
Arabic| | 
RTL| | 
Accessibility| | 
Responsive layout| | 
Navigation| | 
Resources| | 
Icons| | 
Preview/demo isolation| | 
Deferred functionality| | 
Security boundary| | 
Phase 4 readiness| | 
Git state| | 
Remote verification| | 

Allowed statuses:

- VERIFIED
- PARTIALLY VERIFIED
- MISSING
- CONTRADICTORY
- DEFERRED
- UNKNOWN

Do not use "PASS" unless you can provide objective evidence.

---

36. FINAL REPORT

At the end report:

Repository

- repository
- branch
- final commit SHA
- remote verification

What was inspected

- specifications
- source
- resources
- architecture
- UI
- tests
- build

What was fixed

List concrete files and changes.

What remains

List every remaining issue.

For each issue include:

- severity
- file/path
- problem
- why it remains
- whether it is blocked by missing specification

Phase 4 status

Clearly state:

- what is defined
- what is missing
- what is blocked
- what must be decided before implementation

Verification

Provide exact:

- build command
- test command
- lint command where applicable
- results

Never report:

«"Everything is fixed"»

unless the evidence actually supports it.

---

37. ABSOLUTE RULES

These rules override convenience:

1. NEVER guess.
2. NEVER invent missing specifications.
3. NEVER silently change security behavior.
4. NEVER implement Phase 4 without authorization/specification.
5. NEVER claim functionality that is only a placeholder.
6. NEVER trust previous AI self-audits without verification.
7. NEVER modify the original "Ai_super_cleaner" repository.
8. NEVER skip repository verification.
9. NEVER skip build verification.
10. NEVER skip re-reading after changes.
11. NEVER carry known failures forward without documenting them.
12. NEVER perform a huge uncontrolled rewrite.
13. NEVER change unrelated functionality.
14. NEVER remove existing behavior merely to make tests pass.
15. NEVER add fake data to simulate production behavior unless explicitly isolated as preview/test data.
16. NEVER expose secrets.
17. NEVER claim security guarantees without evidence.
18. NEVER mark an item VERIFIED without evidence.
19. NEVER stop all progress merely because Phase 4 is blocked.
20. NEVER cross the Phase 4 boundary merely because an implementation would be convenient.

---

38. START NOW

Execute the workflow in this order:

1. Verify repository identity.
2. Verify branch and commit.
3. Inspect git status.
4. Read the complete SHIELDRA specification.
5. Read Phase1 and README completely.
6. Inspect all relevant skills.
7. Perform forensic repository audit.
8. Establish build/test baseline.
9. Produce the Phase 3 completion matrix.
10. Produce the Phase 4 decision/blocker register.
11. Fix only objectively verified Phase 3 issues.
12. Work in controlled batches.
13. After EVERY batch:
    READ → TRACE → IMPLEMENT → REVIEW → BUILD/TEST → RE-READ → RE-AUDIT → COMMIT → PUSH → VERIFY REMOTE
14. Perform the final independent red-team audit.
15. Run final verification.
16. Produce the final acceptance matrix.
17. Report the exact final commit SHA.

Do not wait for the design AI.

You are now responsible for both engineering implementation and design-quality verification.

The objective is:

MAXIMUM VERIFIED PROGRESS + ZERO INVENTED SECURITY BEHAVIOR + ZERO UNVERIFIED ASSUMPTIONS + ZERO FALSE CLAIMS.