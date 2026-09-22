# Phase 4 EventPipeline Design Review

**Date:** 2026-09-22
**Status:** DESIGN REVIEW / NO IMPLEMENTATION
**Depends on:** Batch 2 pure `ProtectionStateEngine` (`0e02f55`)

## Purpose

Define the decisions required before implementing an EventPipeline coordinator. This document intentionally does not select persistence, evidence, encryption, background execution, delivery, or provider behavior.

## Current facts

The domain contains an injectable clock and identity provider, a pure protection engine, typed Guard evaluations, SecurityEvent states, evidence/delivery boundaries, and empty repository/pipeline interfaces. The existing event states are `INITIATED`, `COLLECTING`, `READY`, `DELIVERING`, `DELIVERED`, `DEFERRED`, and `FAILED_FINAL`. No pipeline implementation exists.

## Decisions required before implementation

| Area | Options | Current status | Required evidence |
|---|---|---|---|
| Submission identity | Event ID only; event ID plus attempt key; Guard sequence key plus event ID | **PROPOSED — NOT APPROVED** | Replay and duplicate-processing analysis |
| Stage ownership | Coordinator owns transitions; repository transaction owns transitions; split ownership with explicit result types | **PROPOSED — NOT APPROVED** | Failure/process-death and atomicity analysis |
| Evidence stage | Validate before persistence; persist event then attach evidence; event-specific staged record | **PROPOSED — NOT APPROVED** | Evidence policy and storage decision |
| Persistence boundary | One transaction per stage; one transaction for event metadata; durable outbox-like record | **PROPOSED — NOT APPROVED** | Room schema/migration and recovery decision |
| Deferred/resume | Resume from last durable stage; restart from validation; manual retry only | **PROPOSED — NOT APPROVED** | Process-death, expiry, and user-control policy |
| Failure semantics | Retryable vs terminal typed error; all failures become `FAILED_FINAL`; explicit deferred state | **PROPOSED — NOT APPROVED** | Error taxonomy and operational policy |
| Expiry | No expiry; fixed expiry; event-type-specific expiry | **UNKNOWN** | Product/security decision; do not infer from UI |
| Concurrency | Serialize per event; serialize per Guard; optimistic version; reject duplicate submission | **PROPOSED — NOT APPROVED** | Duplicate and concurrent signal analysis |
| Delivery | Local-only initial result; delivery adapter boundary; no delivery stage initially | **DEFERRED / OPEN** | G5 provider decision and notification policy |

## Safe contract work still possible

Before these decisions are closed, the repository may add framework-free result/error types and test fixtures that do not encode a selected lifecycle, schema, retry count, expiry, or persistence strategy. It must not implement a coordinator whose behavior implies one of the unresolved choices.

## Explicit non-actions

This review does not add Room, DataStore, Keystore, evidence capture, camera, location, sensors, WorkManager, foreground services, notifications, network calls, provider SDKs, billing, accounts, or external delivery. It does not change the approved Guard rules or claim production monitoring.

## Entry criteria for the next implementation batch

1. Approve event identity and idempotency semantics.
2. Approve authoritative state/lifecycle and expiry, including any `EXPIRED` decision.
3. Approve stage ownership and transaction boundaries.
4. Approve retry/deferred/resume/cancellation/failure semantics.
5. Approve whether the first coordinator is local-only and persistence-free or requires the approved storage adapter.
6. Add deterministic contract tests for every approved decision.

Until these criteria are met, EventPipeline remains **READY FOR DESIGN** rather than **READY FOR IMPLEMENTATION**.
