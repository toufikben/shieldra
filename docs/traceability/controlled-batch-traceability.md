# Controlled Command Batch Traceability

**Command source:** `docs/authority/controlled-implementation-command.md`
**Execution mode:** one batch at a time with stop-and-recheck gates
**Current gate:** documentation preflight before Batch 0

| Batch | Objective | Files inspected | Files changed | Problems found | Problems fixed | Validation | Result | Remaining problems | Next batch |
|---|---|---|---|---|---|---|---|---|---|
| Preflight | Publish complete roadmap, handoff, command source, and batch ledger before execution | Repository history, current plans, Phase 3 audit, authority index | Roadmap, handoff, command copy, ledger, preflight report | Documentation was distributed across several files; no single next-agent entry point existed | Centralized navigation and preserved exact command | Git diff check and document existence check after commit | IN PROGRESS | Final preflight commit and remote verification pending | Batch 0 |
| 0 | Build/Foundation | Pending controlled reread | Pending | Existing Phase 3 build passed; prior report records old handoff defects already addressed | Pending second controlled audit | Pending clean build, resource scan, dependency scan | PENDING | Must recheck against current files | Batch 1 |
| 1 | Architecture/Models/Data Flow | Pending | Pending | First audit found Compose-coupled models and preview/runtime coupling; delivered shell addressed them | Pending second audit | Pending model/import/usage scan and build/tests | PENDING | Must recheck all usages | Batch 2 |
| 2 | Navigation/Screen Connectivity | Pending | Pending | Full route/state propagation audit not yet recorded | Pending | Pending route matrix and build/tests | PENDING | Runtime navigation remains untested without device | Batch 3 |
| 3 | Reusable Components | Pending | Pending | Component-by-component second audit not yet recorded | Pending | Pending component matrix, accessibility scan, build | PENDING | Must not batch unrelated components | Batch 4 |
| 4 | Localization/RTL | Pending | Pending | 59 lint warnings and known unmigrated sample/component copy remain | Pending | Pending complete production hardcoded-string scan and resource checks | PENDING | Arabic runtime layout unavailable without device | Batch 5 |
| 5 | Accessibility | Pending | Pending | No emulator/device; runtime large-text and semantics behavior unknown | Pending | Pending static target/semantics scan and, if available, runtime test | PENDING | Runtime visual/accessibility remains UNKNOWN | Batch 6 |
| 6 | Design System | Pending | Pending | Design-token consistency audit not yet recorded | Pending | Pending raw-value/token scan and build | PENDING | No redesign permitted | Batch 7 |
| 7 | Every Screen | Pending | Pending | State matrix has not been independently recorded for every screen | Pending | Pending screen-state matrix and available tests | PENDING | Device-dependent cases remain UNKNOWN | Batch 8 |
| 8 | Deferred Functionality Honesty | Pending | Pending | Must recheck every deferred callback after other batches | Pending | Pending forbidden-implementation scan and callback audit | PENDING | No fake success/backend allowed | Batch 9 |
| 9 | Final Integration | Pending | Pending | Pending | Pending | Clean build, tests, static/resource/navigation scans, full reinspection, CI | PENDING | Final report required | Stop before Phase 4 |

## Required batch report fields

Each batch report must state the batch number, objective, inspected files, changed files, findings, fixes, validation commands and outputs, result, remaining problems, and next batch. A result may be `PASS`, `FAIL`, `UNKNOWN`, `BLOCKED`, or `IN PROGRESS`; `PASS` requires evidence for the relevant claim.
