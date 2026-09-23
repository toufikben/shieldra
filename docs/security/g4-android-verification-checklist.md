# G4 Android Runtime Verification Checklist

**Status:** DEVICE/AVD VERIFICATION REQUIRED
**Gate:** G4 remains APPROVED DIRECTION / DETAILS BLOCKED

No result in this checklist is passed until an AVD or physical device produces an evidence record with API level, build, model, permissions, settings, exact commands, timestamps, observed output, and recovery result.

| ID | Test | Required profiles | Pass evidence |
|---|---|---|---|
| A-01 | Keystore key creation and algorithm/size inspection | API 26, 31, 34, 35; physical OEM | Key created in Android Keystore; no software fallback; policy matches approved profile |
| A-02 | Encrypt/decrypt and authenticated payload round trip | Same | Plaintext round trip succeeds; fresh IV per operation; wrong AAD fails |
| A-03 | Invalid tag, truncation, unsupported version | Same | Operation fails closed; no plaintext or valid-evidence result |
| A-04 | Device credential required/validity behavior | API 26, 31, 34, 35; physical OEM | Actual prompt, denial, timeout, and unavailable behavior recorded |
| A-05 | Biometric enrollment change | API 28+; physical device | Key invalidation or non-invalidation matches approved policy and is recorded |
| A-06 | Device credential change/removal | API 26+; physical device | Key behavior and recovery state recorded; no bypass |
| A-07 | StrongBox/hardware-backed availability | API 28+; supported physical device | Availability is reported; no unsupported claim generalized to all devices |
| A-08 | Process death before/after storage commit | API 26–35; AVD/physical | Persistent state/resume behavior matches atomicity policy |
| A-09 | Force-stop and restart | API 26–35; AVD/physical | No claim of recovery beyond Android behavior; state is reconciled on restart |
| A-10 | Room migration from every supported prior version | API 26–35; AVD | Explicit migration passes; no destructive fallback; interrupted migration is safe |
| A-11 | Database corruption behavior | API 26–35; AVD/physical | Fail-closed recovery state; no silent empty-history replacement |
| A-12 | Encrypted-file crash/rename/tamper | API 26–35; AVD/physical | Temp/rename/reconciliation behavior matches policy |
| A-13 | Missing key / mismatched DB and file | API 26–35; AVD/physical | Evidence marked unavailable; no substitute key or plaintext recovery |
| A-14 | Backup/restore and device transfer | API 26–35; physical where available | Actual Android result documented; excluded data is not assumed recoverable |

## Required evidence record

Each test record must include: stable test ID; manufacturer/model; API and build fingerprint; app commit and target SDK; key policy; permission and lock-screen state; biometric state; battery/Doze state; exact action/ADB sequence; timestamps; exception/result; persisted state; recovery result; classification; and reviewer.

## Current state

All rows are **DEVICE/AVD VERIFICATION REQUIRED**. The current environment has no confirmed ADB, emulator, AVD, or physical Android device. No Keystore or device behavior is claimed as verified.
