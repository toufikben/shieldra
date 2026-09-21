# Shieldra Controlled Implementation Command

**Source:** user-supplied attachment `pasted_content.txt` received on 2026-09-21.
**Status:** authoritative process instruction for the controlled audit/implementation sequence; it does not replace unavailable Product Freeze v5 or Phase 1 product authority.

```text
SHIELDRA — CONTROLLED IMPLEMENTATION COMMAND

You have completed the initial forensic audit.

NOW STOP.

Do NOT begin a massive implementation.

First convert your audit findings into a concrete execution plan.

Group the findings into independent batches.

Recommended order:

BATCH 0 — Build/Foundation

Fix only:

- Gradle/build configuration
- missing files
- broken imports
- invalid resources
- launcher icon
- compilation blockers
- dependency inconsistencies

Then:
READ → FIX → REVIEW → BUILD → RECHECK.

Do not touch visual polish yet.

---

BATCH 1 — Architecture / Models / Data Flow

Fix:

- presentation/domain boundary violations
- Compose types inside inappropriate pure models
- state ownership
- model inconsistencies
- callback contracts
- navigation data flow
- PreviewData accidentally used as runtime data

Then:
READ → FIX → REVIEW ALL USAGES → BUILD/TEST → SECOND AUDIT.

---

BATCH 2 — Navigation + Screen Connectivity

Audit and fix:

- every route
- every screen entry
- every screen exit
- parameters
- back navigation
- callbacks
- state propagation
- dead routes
- unreachable screens

Then perform a complete navigation re-audit.

---

BATCH 3 — Reusable Components

Fix components individually.

For each component:

1. inspect implementation
2. inspect every usage
3. fix API/state
4. inspect accessibility
5. inspect RTL
6. inspect touch target
7. inspect theme/token usage
8. compile
9. inspect again

Do NOT modify unrelated components in the same batch.

---

BATCH 4 — Localization + RTL

Perform a COMPLETE production-source scan.

Do not rely on the previous audit.

Find every user-visible hardcoded string.

Move them into Android resources.

Verify:

- English
- Arabic
- placeholders
- pluralization where necessary
- RTL
- mixed Arabic/English/numbers
- phone numbers
- IDs
- timestamps
- navigation labels
- accessibility labels

After fixing, search the ENTIRE production source again.

There must be no overlooked user-facing hardcoded strings.

---

BATCH 5 — Accessibility

Audit every interactive element.

Verify approximately 48dp minimum interactive targets where applicable.

Inspect:

- buttons
- chips
- dismiss controls
- icon buttons
- cards
- navigation
- switches
- sliders
- text fields

Check:

- semantics
- role
- contentDescription
- state description
- keyboard/accessibility behavior
- contrast
- large text

Then perform a SECOND complete accessibility scan.

---

BATCH 6 — Design System

Verify:

- colors
- typography
- spacing
- shapes
- elevation
- icons
- motion
- tokens

Remove inappropriate raw values where they violate the design system.

Do NOT redesign SHIELDRA.

Correct implementation inconsistencies only.

---

BATCH 7 — Every Screen

Review each screen independently.

For each screen test:

- normal state
- loading
- empty
- error
- disabled
- long text
- Arabic
- narrow width
- large text

Do not consider a screen complete until its states have been reviewed.

---

BATCH 8 — Deferred Functionality Honesty

Verify that Phase 3 placeholders remain honest.

Especially:

- premium
- restore purchase
- panic
- permissions
- maps
- delete
- export

No fake success.

No fake backend.

No accidental production functionality.

---

BATCH 9 — Final Integration

Run:

- clean build
- relevant tests
- static checks
- resource verification
- navigation verification

Then inspect the entire project again.

---

CRITICAL BATCH RULE

After EACH batch:

1. stop
2. reread the relevant handoff section
3. reread every modified file
4. inspect every usage of modified APIs
5. run validation
6. search again for the same defect class
7. check for regressions
8. only then move to the next batch

If the batch introduces a regression:

STOP.

Fix it before continuing.

Never carry known failures into the next batch.

---

DO NOT REPORT COMPLETION PREMATURELY

Do not say:

"Done"

"Fixed"

"Complete"

until the corresponding validation actually proves it.

A code change without validation is NOT a completed fix.

A successful build alone is NOT proof that the UI, navigation, accessibility, localization, architecture, or design are correct.

---

FINAL REQUIREMENT

At the end of every batch report:

- Batch number
- Objective
- Files inspected
- Files changed
- Problems found
- Problems fixed
- Validation performed
- Validation result
- Remaining problems
- Next batch

Then continue automatically if the current batch is clean.

Do not wait for me between ordinary batches unless you encounter a genuinely blocking ambiguity that cannot be resolved from the repository, handoff, Android documentation, or available skills.
```
