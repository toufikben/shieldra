# Safe Phase 3 Remediation Batch 3

**Date:** 2026-09-21  
**Scope:** design-token consistency, Premium presentation semantics, and stale Phase 3 comments.  
**Result:** PASS for the bounded source/build scope; runtime device behavior remains UNKNOWN.

## Findings addressed

A final production-wide scan found three remaining default card shapes recreated directly in Dashboard, Event Detail, and Settings instead of using the canonical `ShieldraCardShape` token. It also found a text check mark embedded in the Premium feature title presentation and a stale comment describing the ad slot as a placeholder.

## Changes

Dashboard panic action, Event Detail details block, and Settings groups now use `ShieldraCardShape`. The token itself remains the single owner of the 14dp default card shape.

Premium feature rows now render a decorative Material check icon beside the localized feature title rather than embedding a Unicode check mark in a text string. The icon has no spoken label because the adjacent title carries the meaning.

The ad slot comment now describes the component as a Phase 3 demo with no SDK, matching its intentional boundary.

The permission boundary comment in navigation now explicitly states that Android permission acquisition is not implemented in Phase 3.

## Validation

The following checks passed after the changes:

```text
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug --no-daemon
BUILD SUCCESSFUL
```

A second scan found no production `RoundedCornerShape(14.dp)` usage outside the token definition. No Premium check-mark literal, stale ad placeholder comment, or TODO/FIXME marker was introduced by the batch.

## Boundary review

No navigation route, data model, permission, manifest entry, dependency, persistence path, security behavior, network call, sensor access, camera access, location access, billing flow, or background task changed. The batch is limited to presentation structure and comments.

Actual rendering, font-scale behavior, Arabic RTL layout, TalkBack output, and device hit rectangles remain UNKNOWN without an emulator or connected device.

## Next gate

Push this batch and wait for remote CI. After remote verification, the remaining work should be limited to evidence-driven Phase 3 cleanup or the approved Phase 4 decision gates; production security implementation remains blocked by the Phase 4 decision register.

## References

[1]: ../reports/phase-3-completion-matrix.md "Phase 3 completion matrix"
[2]: ../architecture/phase-4-decision-register.md "Phase 4 decision register"
[3]: ../ROADMAP.md "Shieldra master roadmap"
