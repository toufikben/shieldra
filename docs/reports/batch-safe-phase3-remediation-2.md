# Safe Phase 3 Remediation Batch 2

**Date:** 2026-09-21  
**Scope:** deferred-functionality honesty, demo disclosure, finite demo lookup, and empty-state clarity.  
**Result:** PASS for the bounded source/build scope; runtime device behavior remains UNKNOWN.

## Objective

Remove misleading presentation behavior without implementing any Phase 4 capability. This batch does not request permissions, create events, persist settings, access sensors/camera/location, send notifications, call a provider, or add a production data source.

## Changes

The Dashboard now displays the existing Phase 3 disclosure stating that the screen is a presentation demo with no production data or integrations.

The onboarding notification screen now uses `Continue` rather than `Allow`. Its English and Arabic explanation explicitly says that the screen shows where notification setup would appear and that no Android permission is requested. The runtime callback remains deferred rather than advancing as if permission had been granted.

The welcome and empty-state copy now describes the presentation demo rather than claiming that Shieldra detects, collects, or delivers production security events.

History now distinguishes a genuinely empty supplied event list from a non-empty list whose active filters produce zero results.

Runtime event details now use a finite demo-only lookup for the two known demo event IDs. Unknown IDs render a neutral localized unavailable state rather than showing the details of an unrelated static event.

The obsolete English and Arabic `action_allow` resources were removed after the presentation-only action was renamed.

## Validation

The final post-cleanup validation passed:

```text
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/group-c-contract-check.sh
git diff --check
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug --no-daemon
BUILD SUCCESSFUL
```

The static review confirmed the following boundaries remain intact:

- Presentation models are framework-free.
- Runtime navigation does not import PreviewData.
- No permissions or broken launcher reference were added.
- No camera, location, network, persistence, billing, or background implementation was added.
- No event creation was added outside the existing domain boundary.
- No fake-data, placeholder, TODO, secret, or unsafe logging pattern was introduced in app source.
- English and Arabic resources remain present and compile successfully.

## Self-review

The finite event lookup is intentionally limited to the existing demo IDs and returns `null` for anything else. It is not a repository, event pipeline, or production lookup. The unknown state uses localized copy and the existing reusable error component. The permission callback is deliberately deferred so the UI cannot imply that Android permission state changed.

Actual rendering, Arabic bidi behavior, TalkBack, focus order, large-text reflow, and runtime navigation remain UNKNOWN without an emulator or connected device. Lint completes successfully but the project retains the previously recorded warning count and those warnings remain a separate cleanup topic.

## Next gate

Before another source batch, verify the second batch after remote CI. Phase 4 implementation remains blocked by the decision register: authority, security rules, evidence policy, Android platform matrix, storage, external services, and security review are not approved.

## References

[1]: ../reports/phase-3-completion-matrix.md "Phase 3 completion matrix"
[2]: ../architecture/phase-4-decision-register.md "Phase 4 decision register"
[3]: ../ROADMAP.md "Shieldra master roadmap"
