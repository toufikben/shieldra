# Safe Phase 3 Remediation Batch 1

**Date:** 2026-09-21  
**Scope:** presentation-only corrections derived from the fresh Phase 3 audit.  
**Result:** PASS for the bounded source/build scope; runtime device behavior remains UNKNOWN.

## Objective

Correct only objectively verifiable Phase 3 defects without adding security behavior, permissions, persistence, capture, delivery, authentication, billing, network integrations, or background guarantees.

## Findings addressed

The independent audit identified repeated direct card-shape values, an unbounded event metadata row, unused motion tokens, and visible runtime DemoData values that were not resource-backed.

## Files changed

- `app/src/main/kotlin/com/shieldra/app/design/components/domain/EventCard.kt`
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/EvidenceCard.kt`
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/DeliveryReceipt.kt`
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/ErrorState.kt`
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/GuardTile.kt`
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/LiveStatusStrip.kt`
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/LoadingState.kt`
- `app/src/main/kotlin/com/shieldra/app/design/components/domain/ShieldCore.kt`
- `app/src/main/kotlin/com/shieldra/app/presentation/demo/DemoData.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`

## Changes

All seven audited domain components now use the existing `ShieldraCardShape` token for the default card corner. No visual value was invented or changed.

`EventCard` now uses `FlowRow` for metadata pills, so the presentation can wrap labels rather than forcing a single horizontal row at narrow widths or large font scales.

`ShieldCore` now consumes the existing `ShieldraMotion` timing and easing locals for protected, suspicious, and attention states. No new motion policy was introduced.

Visible runtime fixture values for Wi-Fi, event times, receipt times, and the premium demo price now come from matching English and Arabic resources. This changes presentation localization only; it does not create a production data source.

## Validation

The following validations passed after implementation:

```text
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug --no-daemon
BUILD SUCCESSFUL
```

The current test source contains 12 `@Test` methods. Lint completed with 28 warnings and no reported build error in the verified run.

The following static checks passed:

```text
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
```

A second audit found no remaining `RoundedCornerShape(14.dp)` card recreations in the audited domain component directory and no remaining target DemoData literals for Wi-Fi, price, or sample times. Runtime navigation still uses DemoData and PreviewData remains preview-only. No Phase 4 integration pattern was introduced.

## Self-review

The changed APIs are limited to Compose layout/token usage and resource lookup. No domain contract, navigation route, permission, manifest entry, dependency, network call, persistence path, or security state changed. The FlowRow change is presentation-only and compilation verified it. The resource additions were added in both English and Arabic to preserve parity.

Runtime RTL rendering, TalkBack behavior, actual touch rectangles, large-text reflow, animation behavior, and narrow-device rendering remain UNKNOWN because no emulator or connected device is available.

## Next batch

The next safe batch is deferred-functionality and state-disclosure cleanup: make permission onboarding explicitly presentation-only, use the existing Phase 3 demo disclosure in the shell, qualify misleading demo claims without adding runtime behavior, and improve static empty/unknown-detail presentation states. That batch must be reviewed separately and must not add permission acquisition or a production data source.

## References

[1]: ../reports/phase-3-completion-matrix.md "Phase 3 completion matrix"
[2]: ../architecture/phase-4-decision-register.md "Phase 4 decision register"
[3]: ../ROADMAP.md "Shieldra master roadmap"
