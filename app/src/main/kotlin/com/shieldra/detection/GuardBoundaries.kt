package com.shieldra.detection

/*
 * Design B (2026-11-06): the Lock boundary was removed with its guard. Its only sensor was the
 * device-admin `onPasswordFailed` callback — an authority the app may not hold — so there is no
 * `LockGuard` interface, no `FailedPinAttempt` observation, and no `NOT_DEVICE_ADMIN` state left
 * to paper over.
 *
 * What remains is the boundary rule the remaining guards still obey: a guard is a declaration,
 * never a detector. Detection lives in its own class (`SimGuard`, the Motion pipeline, the battery
 * receiver, the Panic action), so this file stays free of platform code.
 */

interface MotionGuard

interface SimGuard

interface BatteryGuard

interface PanicGuard
