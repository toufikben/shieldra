# Shieldra

Shieldra is a clean-slate Android project repository. Group A established the reproducible environment, Group B created the Android project structure, and Group C established typed core contracts and architectural boundaries. Phase 3 now adds the presentation-only Compose shell described by the original handoff.

The current app includes onboarding, dashboard, history, event detail, premium, settings, reusable design-system components, a localized English/Arabic resource foundation, an explicit vector launcher icon, and a Compose navigation graph. The runnable shell uses clearly named `DemoData`; it is not production data and does not claim that deferred actions succeeded.

## Explicit Phase 3 non-goals

This repository does not implement detection, camera capture, location collection, evidence capture, network delivery, WhatsApp or Telegram integrations, authentication, persistence, encryption, notifications, billing, advertising, Safe Zones, geofencing, continuous GPS, cloud synchronization, or a backend. Panic, maps, delete, export, billing, restore, settings enforcement, and guard actions surface an honest deferred message.

## Authority and source documents

The original handoff was audited read-only from `toufikben/Ai_super_cleaner` using its complete `Phase1` and `README.md` documents. The forensic baseline and discrepancy record are preserved in [`docs/reports/phase-3-audit.md`](docs/reports/phase-3-audit.md), and the controlled batch plan is preserved in [`docs/superpowers/plans/2026-09-21-phase-3-controlled-implementation.md`](docs/superpowers/plans/2026-09-21-phase-3-controlled-implementation.md). Product Freeze v5 and the complete Phase 1 authority documents were not supplied as repository files; that limitation remains explicit rather than being invented away.

## Validation

The local validation suite uses JDK 17 and a temporary Android SDK 35 installation:

```text
./gradlew clean testDebugUnitTest lintDebug assembleDebug
bash .github/scripts/phase3-review-check.sh
```

The local JDK 17 validation baseline is successful: debug build, unit tests, lint, and static review pass. Runtime visual verification on an emulator/device remains UNKNOWN because no emulator or connected device is available. Remote GitHub Actions validation for the current commit is successful.
