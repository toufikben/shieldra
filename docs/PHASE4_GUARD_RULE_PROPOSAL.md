# SHIELDRA Phase 4 Guard Rule Proposal

**Status:** Approved rules are separated from unresolved implementation details.
**Last reconciled:** 2026-09-22

## Decision table

| Guard | Signal | Validation | Approved threshold/confirmation | Reset/cancellation | Cooldown | Severity | Response/evidence |
|---|---|---|---|---|---|---|---|
| Lock | Wrong PIN attempt | Must be an actual failed PIN attempt exposed by an approved Android capability | Two consecutive failures within approximately two minutes confirm an event | Counter resets after ten minutes without another failure; cancellation and lifecycle handling remain to define | Five minutes for the same attack sequence | HIGH | Response and evidence remain Guard-specific and unapproved; no evidence on a Signal alone |
| Motion | Meaningful unusual movement | Sensor availability and quality validation; isolated/noisy readings ignored | Strong/unusual movement lasting approximately three seconds, OR two suspicious movements within ten seconds | Calibration, window representation, and cancellation remain technical details to verify | Same-event deduplication and cooldown representation remain to define | HIGH when clearly abnormal | Motion-relevant minimum evidence only after confirmation |
| SIM | Confirmed SIM/subscription state change | Verify actual API/device support and baseline comparison | Immediate confirmed event; no repetition requirement | Baseline/recovery semantics remain to verify | Same-event deduplication remains to define | HIGH | SIM-relevant minimum metadata only after confirmation |
| Battery | Power/battery observation | Battery percentage alone is insufficient | No confirmed security event until an abnormal power/charging state is separately defined and approved | No anomaly reset/cancellation policy exists yet | Not applicable until anomaly is approved | LOW/MEDIUM only if later approved | Battery/power metadata normally; no camera by default |
| Panic | Explicit user activation | User action reaches the approved local trigger | Immediate confirmed event; no confirmation delay or attempt count | Accidental-activation policy is not changed without approval | Same-event handling must avoid duplicate spam | CRITICAL/HIGHEST | Response/evidence remain separately defined |

## Guard-specific rationale and risks

### Lock

The rule distinguishes a single weak signal from a confirmed sequence. False positives can arise from a legitimate user forgetting a credential; false negatives can arise when the platform does not expose the attempt or the process is unavailable. Android observability and OEM behavior are **UNKNOWN** until a capability investigation is completed.

### Motion

Motion requires quality validation and persistence rather than PIN-style counting. False positives include transport vibration and ordinary handling; false negatives include sensor unavailability, calibration drift, Doze, process death, and OEM restrictions. Sensor API, sampling, and battery cost are **UNKNOWN**.

### SIM

The approved product rule treats a confirmed subscription/SIM state change as direct. The meaning of “change,” baseline storage, multi-SIM/eSIM behavior, permissions, and API availability are **UNKNOWN** and must be resolved before implementation.

### Battery

A percentage is not a security incident. The anomaly definition is intentionally **UNKNOWN**; no security implementation may invent one. Battery metadata can be considered evidence only when an approved confirmed event exists.

### Panic

An explicit user activation is direct and immediate. A delay that defeats the action is not permitted by the approved rule. Recipient, local response, cancellation, authentication override, and evidence are **UNKNOWN** or **DEFERRED** until separately approved.

## Cross-cutting rule

```text
Signal → validation/quality → Guard-specific confirmation → Confirmed Event → severity → approved response → minimum relevant evidence
```

A Signal is not a confirmed event. The engine must prevent duplicate alerts from one continuous incident and distinguish a genuinely new or escalated event.

## Not approved

The following values are not silently selected: exact Android APIs, sensor sampling frequency, calibration constants, battery anomaly definition, camera trigger, location trigger, evidence retention, notification escalation, external delivery, or cryptographic policy. Any future option must be labeled **PROPOSED — NOT APPROVED** until accepted.
