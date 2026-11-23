# Shieldra

Shieldra is a device-first Android anti-theft and personal-safety app (Kotlin, Jetpack Compose, minSdk 26, target API 36). Everything it records stays on the phone until the owner's own channels carry it off.

## What the current build does

- **Protection service:** arming starts one foreground service that runs the SIM guard, the battery last-breath guard, the tamper watch and the heartbeat, and re-posts an ongoing notice whose lock-screen visibility is `VISIBILITY_SECRET` — nothing is shown to whoever is holding the phone.
- **SIM guard:** the owner's armed subscription set is the baseline. A subscription that disappears, one that was not there at arming, or a radio that goes dark is recorded as an event with its own history type and alert.
- **Battery last-breath guard:** at the owner's chosen level the phone writes a final event and sends what it still can before it dies.
- **Tamper watch:** USB debugging or the developer options switched on while protection is armed is recorded once per act, so a thief who unlocks the bootloader is not silent.
- **Motion monitoring:** the same calibration the owner approved (`ApprovedMotionCalibration`) judges movement. A confirmed Motion incident is stored, photographed and announced. Stopping monitoring asks for the device's own biometric or screen-lock credential.
- **Silent Panic:** press-and-hold on the dashboard records a Panic incident on a silent channel: no sound, no vibration, nothing on the lock screen.
- **Evidence:** a confirmed event takes a photo from the cameras the owner allowed and a location fix, and keeps a short audio clip when audio is on. Evidence files are AES-GCM encrypted with an Android Keystore key and canonical AAD; reading decrypts with the key version the row was written with, so rotation never strands an old file.
- **Delivery off the phone:** alerts leave through the owner's own channels — Telegram (with a `/locate`, `/photo`, `/track`, `/alarm`, `/status` command loop answered only on the owner's chat id) and SMTP email. Delivery is a real state machine: a receipt that could not be stored leaves the event `DEFERRED` and in the queue, never `DELIVERED`.
- **Remote commands:** the owner's Telegram chat can ask a stolen phone to report its position, take a photo, sound the siren, or say what its battery and network are. One command per update: a redelivered message cannot start the siren twice.
- **Lost notice:** `/lost` and `/found` answer whoever holds the phone with the owner's contact and message.
- **History and detail:** every recorded event is listed with its delivery state and opened in a detail screen showing its battery, location and evidence; deleting an event is a real delete behind the owner's screen lock, and it removes that event's evidence files.
- **Local storage:** Room for event metadata (explicit migrations, no destructive fallback), with retention windows (evidence 30 days, events 90 days by default, both following the owner's setting), key rotation, an export with redaction and an audit log, and a confirmed factory reset.
- **Guard patrol:** a watchdog outside the service tells the owner when the guards have gone quiet — the failure no phone reports by itself.
- **Notifications:** `POST_NOTIFICATIONS` is requested only from the notification setup screen (amendment A-6), and monitoring refuses to start when its ongoing notice would not reach the owner.
- **English and Arabic** resources, with RTL support and a parity gate that checks keys *and* format arguments.

## What this build deliberately cannot do

These are platform and product limits, not gaps waiting to be filled. They are stated here so the list above is read correctly.

- **It cannot lock the screen.** Design B (2026-11-06) left the app without any lock power. A guard detects and reports; it never freezes the phone.
- **It holds no device administrator**, so there is no remote wipe and no factory-reset protection. The honest substitute that ships: a real local delete of events and their evidence, plus the lost notice.
- **A SIM swap is never proven.** The card's serial (ICCID) sits behind `READ_PRIVILEGED_PHONE_STATE`, which no store-distributed app may hold, so the guard reports *that* the subscription set changed, not *which* card replaced which. An eSIM profile switches on and off exactly like a card.
- **A force-stopped app observes nothing.** Android excludes a stopped package from the SIM broadcast, and a vendor battery manager can starve the process. The patrol is what tells the owner the guards went quiet.
- **Only when the owner turned it on.** Every guard runs inside the service the owner armed; nothing is silent, nothing starts itself, and nothing restarts on boot beyond what the owner's armed flag restores.

## Not implemented yet

Export to a share sheet, and a real billing wiring (the plan switch is present and refuses itself on Play installs rather than pretending).

## Authority

The internal decision record is [`SHIELDRA_DECISIONS.md`](SHIELDRA_DECISIONS.md). The binding security and storage amendments are in [`docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md`](docs/authority/FINAL_SECURITY_STORAGE_DECISIONS.md), and the owner question register is in [`docs/OWNER_QUESTIONS.md`](docs/OWNER_QUESTIONS.md). The measured platform limits quoted above come from `docs/reports/batch-I-sim-feasibility.md` and `docs/reports/batch-G4A-background-sensor-measurement.md`.

## Validation

CI (`.github/workflows/group-a-diagnostics.yml`) builds and validates every push:

```text
./gradlew clean test lint assembleDebug assembleDebugAndroidTest
bash .github/scripts/group-c-contract-check.sh
bash .github/scripts/g4b-review-check.sh
bash .github/scripts/phase3-review-check.sh
bash .github/scripts/locale-parity-check.sh
bash .github/scripts/merged-manifest-check.sh
```

Instrumented tests run on a physical device using the APK pair that CI publishes to the `device-apks` draft release.
