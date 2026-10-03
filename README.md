# Shieldra

Shieldra is a device-first Android anti-theft and personal-safety app (Kotlin, Jetpack Compose, minSdk 26, target API 36). Everything it records stays on the phone.

## What the current build does

- **Motion monitoring:** a foreground service samples the accelerometer, judges movement with the owner-approved calibration (`ApprovedMotionCalibration`), records a confirmed Motion incident in local storage, and posts a local notification. Stopping monitoring asks for the device's own biometric or screen-lock credential.
- **Silent Panic:** press-and-hold on the dashboard records a Panic incident. Its notification goes to a silent channel: no sound, no vibration, nothing on the lock screen.
- **Local storage:** Room for event metadata (explicit migrations, no destructive fallback). Evidence files are AES-GCM encrypted with an Android Keystore key and canonical AAD, with retention windows (30 days for evidence, 90 days for events), key rotation, and a confirmed factory reset.
- **Notifications:** `POST_NOTIFICATIONS` is requested only from the notification setup screen (amendment A-6).
- **English and Arabic** resources, with RTL support.

## Not implemented yet

Lock, SIM and Battery guards; camera or location evidence; any delivery off the phone (trusted contacts, messaging, cloud); app lock; export; billing. Screens show these as unavailable rather than pretending they work.

## Authority

The internal decision record is [`SHIELDRA_DECISIONS.md`](SHIELDRA_DECISIONS.md). The binding security and storage amendments are in [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md), and the owner question register is in [`docs/OWNER_QUESTIONS.md`](docs/OWNER_QUESTIONS.md).

## Validation

CI (`.github/workflows/group-a-diagnostics.yml`) builds and validates every push to `main`:

```text
./gradlew clean test lint assembleDebug assembleDebugAndroidTest
bash .github/scripts/group-c-contract-check.sh
bash .github/scripts/g4b-review-check.sh
bash .github/scripts/phase3-review-check.sh
```

Instrumented tests run on a physical device using the APK pair that CI publishes to the `device-apks` draft release.
