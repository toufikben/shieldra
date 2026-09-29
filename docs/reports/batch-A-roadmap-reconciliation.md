# Batch A — Reconcile roadmap / questions / decisions against the actual repository

**Date:** 2026-09-29
**Protocol reference:** owner *Final Master Execution Protocol* §3–§5, §38 (Batch A)
**Commit:** `ba1a224` (`docs(reconcile): Batch A — roadmap, question register, and G4
evidence against actual repository state`), lineage on `main` after `fcfcd96`.
**Type:** documentation reconciliation only — **no production code changed in this batch.**

## BATCH

Re-read the four authority documents, the current `main` source tree, the CI history, and
the two evidence files that had drifted (`docs/ROADMAP.md`,
`docs/reports/g4-acceptance-matrix.md`), then rewrote them to state what is actually true —
including writing down the mistakes.

## SCOPE

Protocol §4 required correcting stale roadmap text rather than appending to it, and §5
required installing the seven-level status model. Protocol §3 also required tracing rather
than trusting reports, so every claim below was re-verified against source in this session.

## TRACE — what was actually measured, not assumed

| Check | Command / file | Result |
|---|---|---|
| Does a production rotation trigger exist? | `grep -rn "rotationDue" app/src` | Defined at `EvidenceKeyPolicy.kt:52`, referenced **only** by `EvidenceKeyPolicyTest.kt`. Zero production callers → **automatic rotation does not exist** |
| Does `EvidenceKeyRotator` run from anywhere in the app? | `grep -rn "EvidenceKeyRotator" app/src` | Only its own file + JVM test + instrumented test → **algorithm verified, never invoked in production** |
| Does any retirement lifecycle exist? | `grep -rni "retir" app/src` | **Zero matches** — no `RETIRED` state, no reference-count check, no destruction path distinct from §5.7 factory reset |
| Does any code path create a security event? | `grep -rn "createConfirmedEvent" app/src/main` | **Zero callers.** `DefaultProtectionStateEngine` itself has no caller in `app/src/main`; `GuardKind.PANIC → confirmed(CRITICAL)` exists at `app/src/main/kotlin/com/shieldra/domain/ProtectionStateEngine.kt:32` as pure domain only |
| Retention values | `EvidenceRetentionPolicy.kt:50-52` | `QUARANTINE 30`, `EVIDENCE 30`, `EVENT_METADATA 90` — matches §29 exactly, unchanged |
| Room schema version | `ShieldraDatabase.kt:12` | `version = 2`, `MIGRATION_1_2` registered (`ShieldraDatabaseMigrations.kt:24`) — contradicts authority §4.1/§4.5 "Room version = 1 currently" |
| Instrumented suite size | `grep -c "@Test" app/src/androidTest` | **34 tests across 8 classes** — contradicts the acceptance matrix's "no device available" |
| Storage gate | `StorageRuntimeApproval.kt:12` | `approved = false` — correct for a producer-less shell (§11) |
| Dead buttons | `grep -n "defer" ShieldraNavHost.kt` | 9 deferred actions at lines 130, 154, 155, 184, 185, 192, 221, 222, 223 |
| Threat model sign-off | `docs/security/g4-threat-model.md:3` | still `PROPOSED — REQUIRES SECURITY/PRIVACY REVIEW` → **no SECURITY REVIEWED row may be claimed anywhere** |

## FILES CHANGED

- `docs/ROADMAP.md`
- `docs/OWNER_QUESTIONS.md`
- `docs/reports/g4-acceptance-matrix.md`
- `docs/security/g4-a-decision-set.md`
- `docs/reports/batch-A-roadmap-reconciliation.md` (this file)

## WHAT WAS CORRECTED

**`docs/ROADMAP.md`**
- Deleted the "Latest local implementation batch (4d-aad-binding)" block that still described
  AAD as the frontier, and the whole stale "Required next batch" narration that re-listed
  already-closed work.
- Replaced the "in progress at the last check" CI paragraph with the current verified tip:
  run `36559760906` → `BUILD SUCCESSFUL`, `171 tests, 171 passed, 0 failures, 0 errors`.
- Added the seven-level status model with a "never claimed from" column, and the explicit
  inequality line from §40.
- Corrected four rows that asserted **no device exists**: "Runtime device
  visual/RTL/accessibility verification | UNKNOWN | No emulator or connected device
  available" → `NOT RUN` with the true reason; batch rows `4b`, `4d-6`, `4d-7` → the measured
  results; and the new *Device verification rule* paragraph (same-run-id APKs, because each
  runner regenerates its debug keystore).
- Added "Project mode" stating plainly that the app has **no detection at all**.
- Rewrote the decision-authority ladder to rank the owner protocol above the registers.
- Rewrote the gate table: G2 answered/unblocked for Panic, G3 answered in order, G4
  substantially device-verified but **not** production-approved, G5 deferred and reaffirmed,
  G7 ready for platform batches.
- Added **Real remaining G4 gaps (found by trace)** — the two gaps above, each with its grep
  evidence — plus the list of things that were checked and are genuinely already closed.
- Replaced "Required next batch" with Batch B and the protocol's authoritative A→L order, with
  the standing prohibitions carried forward.
- Rewrote Phases 5–11 from the protocol (§16–§25) instead of the old "BLOCKED" text.
- Added the durable toolchain constraints (Keystore-generated IV, D8 `snake_case`,
  kotlin.test-vs-JUnit4 message order, `assembleDebugAndroidTest` being the only step that
  compiles instrumented sources, `rg` absent on runners, Room main-thread throw, Keystore
  alias warm-up).

**`docs/OWNER_QUESTIONS.md`**
- Transcribed the protocol's answer into each of Q-C1…Q-C7, **quoting the section** that
  answers it so an answer can be checked rather than trusted.
- Replaced the speculative "what happens after you answer" list with the authoritative batch
  table.
- Recorded the one genuine contradiction the protocol did not resolve: §30 bars ads and
  billing while the 2026-09-29 store-launch decision authorized both. Written as a
  **CONFLICT** box with both sources side by side, what I am doing meanwhile (nothing is
  blocked), and a one-line reply format.
- Added **Q-C8**, a question the protocol itself raised and did not answer: §10 forbids
  inventing a destruction policy for a *retired* key version, so that stays an owner decision.
- Corrected the "zero permissions" framing: it is pre-launch copy that Batch E legitimately
  invalidates, not a permanent constraint.
- Filled the corrections table with the four errors this reconciliation found.

**`docs/reports/g4-acceptance-matrix.md`** — rewritten. Six rows said `BLOCKED` for lack of a
device, for an unapproved AAD decision, and for missing migrations; all three premises are now
false and each corrected row names the instrumented test that proves it. The rows that really
are still unverified stay unverified: backup/restore, concurrency/crash, symlink on device,
downgrade, StrongBox, and the independent security review.

**`docs/security/g4-a-decision-set.md`** — added a per-section reconciliation of the
`PROPOSED — REQUIRES APPROVAL` labels against the authority file and current evidence, so the
file no longer understates what the owner has since decided.

## DECISIONS ENFORCED (not created)

§2.5 no software-key fallback · §4.2–§4.4 no destructive migration · §5.4/A-1 retention
unchanged · §5.5/A-2/A-3 export off and audited reset · A-4 missing key → `RECOVERY_REQUIRED`
· §7 StrongBox preferred with runtime capability detection, never SDK inference · §14 no silent
policy change · §15 gate stays closed. **Nothing in this batch altered any of them.**

## SELF-ADVERSARIAL FINDINGS ON THIS BATCH ITSELF

1. **Temptation to over-claim from green CI.** The old matrix did exactly that in reverse (it
   under-claimed after the device runs happened). Fixed by tying every corrected row to a named
   test rather than to a workflow status.
2. **Almost wrote "rotation is device verified" without qualification.** The *algorithm* is;
   the *trigger* is not, and §9 explicitly forbids conflating them. Both sentences now appear
   separately in three documents.
3. **Almost recorded the ad conflict silently as "§30 wins".** Newer instruction ≠ owner
   decision when the older one was itself explicit and dated; §37 puts data-sharing and
   distribution inside the stop boundary, so it is now a visible question with a recommendation,
   not a quiet override.
4. **Found a staleness inside a binding authority file** (Room version 1 vs the real 2).
   Reported it in `g4-a-decision-set.md` instead of editing the authority document, because
   amending that file is the owner's act under §14.
5. **Number discipline.** Re-counted the instrumented tests (34), the JVM tests (171), the
   deferred actions (9) and the AAD negative cases (11 in `RealAesGcmAadTest`) from source
   rather than from the previous draft of these documents.

## TESTS / ANDROID TESTS / BUILD / LINT

`NOT RUN` — this batch changed no code, and the repository's tests were not executed locally
(the owner's standing rule is that CI is the verifier, because this machine cannot build
Android reliably). The CI run below is the evidence that the documentation change broke
nothing.

## STATIC AUDIT

`docs/ROADMAP.md`, `docs/reports/g4-acceptance-matrix.md` and
`docs/security/g4-a-decision-set.md` were re-read in full after editing, and each factual
claim in them was checked against the `TRACE` table above.

## SECURITY AUDIT

No security surface changed. The batch **adds** two documented requirements for Batch B (a
real rotation trigger; a retirement state machine that cannot delete a key while a reference
survives) and records the destruction decision as explicitly unapproved.

## REMOTE

`toufikben/shieldra` @ `main`, `fcfcd96..ba1a224` pushed. Verified by comparing the local and
remote SHAs after the push succeeded (the first attempt failed on a `github.com:443` timeout
and was retried).

## CI

```text
Run ID:    36598839725
Workflow:  Group A diagnostics
Head SHA:  ba1a2245585543d99220a6a9869eb54a9f706708
Jobs:      diagnostics (test → lint → assembleDebug → assembleDebugAndroidTest → review gates → artifacts)
Conclusion: success
```

Read from the run object (`gh run view --json conclusion`), not from a watch exit code. No
required gate was skipped: the same workflow runs the G4-B review gates that were green at
`fcfcd96`, and a documentation-only diff cannot turn a skipped gate into a pass.

## ROADMAP

Batch A is now marked as the in-progress/reconciled state; `Required next batch` reads
**Batch B — `4d-rotation-orchestration-and-retirement`**, followed by C (Panic), D (flip the
storage gate in the same verified batch), E (notifications), F (background), G (Motion),
H (evidence/privacy/permissions), I (SIM feasibility), J (Lock + current Play policy cited),
K (full audit), L (release hardening).

## STATUS

**CLOSED for its own scope** (documentation reconciliation, CI verified, §16 report present).
It closes nothing else: G4 remains **OPEN — NOT PRODUCTION APPROVED**, §15 remains **OPEN**,
and the app still has **no detection**.

## BLOCKERS

No technical blocker. Two owner decisions are recorded and held, neither of which stops
Batch B or C:
1. **Q-D5/Q-D7** — ads/billing: §30 versus the store-launch decision.
2. **Q-C8** — destruction of a *retired* key version: §10 forbids inventing it, so Batch B
   stops at `RETIRED`.

## NEXT BATCH

**Batch B — `4d-rotation-orchestration-and-retirement`.** TDD from
`EvidenceRotationOrchestratorTest`: a production trigger implementing "90 days OR manual",
the §10 retirement ordering (identify → migrate all → verify all → zero references →
`RETIRED`), durable lifecycle state in Room behind an explicit `Migration(2, 3)`, the real
caller in `ShieldraApp` behind the existing §15 gate, a CI gate proving no delete surface
exists on this path, and instrumented execution on the RMX3910.
