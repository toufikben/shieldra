package com.shieldra.detection

/*
 * There is no Lock boundary, and A-46 did not add one. A wrong unlock is not a guard boundary: it is a
 * device-admin `onPasswordFailed` callback that `LockGuardReceiver` counts and turns into a
 * `LOCK_CONFIRMED_EVENT`, so there is no `LockGuard` interface and no `FailedPinAttempt` observation to
 * place here. Design B (2026-11-06) removed the boundary with the guard; `bb0455f` restored the guard
 * as a receiver, not as an interface.
 *
 * What remains is the boundary rule the guards still obey: a guard is a declaration,
 * never a detector. Detection lives in its own class (`SimGuard`, the Motion pipeline, the battery
 * receiver, the Panic action), so this file stays free of platform code.
 */

interface MotionGuard

interface SimGuard

interface BatteryGuard

interface PanicGuard
