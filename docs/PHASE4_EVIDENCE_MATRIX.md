# SHIELDRA Phase 4 Evidence and Privacy Matrix

**Status:** Approved minimum-evidence principle; implementation details remain open.
**Last reconciled:** 2026-09-22

| Guard | Confirmed event | Minimum relevant evidence direction | Permission | Retention | Deletion/export | Status |
|---|---|---|---|---|---|---|
| Lock | Two approved consecutive wrong-PIN signals within the approved window | Timestamp, Guard identity, approved attempt metadata; battery/location/camera only if separately approved and relevant | **UNKNOWN** | **PROPOSED — NOT APPROVED** | **PROPOSED — NOT APPROVED** | Principle approved; details blocked |
| Motion | Approved movement confirmation | Timestamp, motion characteristics/quality metadata; location/camera only if separately approved | **UNKNOWN** | **PROPOSED — NOT APPROVED** | **PROPOSED — NOT APPROVED** | Principle approved; details blocked |
| SIM | Confirmed subscription/SIM state change | Timestamp and minimum state-change metadata | **UNKNOWN** | **PROPOSED — NOT APPROVED** | **PROPOSED — NOT APPROVED** | API and privacy review required |
| Battery | No event from percentage alone; future anomaly only if approved | Battery level/charging/power metadata for an approved event | **UNKNOWN** | **PROPOSED — NOT APPROVED** | **PROPOSED — NOT APPROVED** | Security anomaly not defined |
| Panic | Explicit user activation | Timestamp, Guard identity, minimum user-action metadata; additional evidence only if approved | **UNKNOWN** | **PROPOSED — NOT APPROVED** | **PROPOSED — NOT APPROVED** | Response policy remains open |

## Approved privacy boundaries

- Do not continuously capture camera or location.
- A Signal alone does not start evidence collection.
- Evidence begins only after a Confirmed Event and only for Guard-relevant minimum data.
- Battery events normally use power metadata rather than camera evidence.
- Do not claim `CURRENT` location when only `LAST_KNOWN` data exists.
- Do not finalize retention, deletion, export, redaction, backup, or cryptographic policy without review.

## Open policy decisions

The following remain **UNKNOWN** or **PROPOSED — NOT APPROVED**: camera trigger and consent, location precision/freshness, retention duration, deletion semantics, export authorization, redaction, uninstall/reset behavior, backup exclusion, encryption algorithm, Keystore lifecycle, and authentication binding.
