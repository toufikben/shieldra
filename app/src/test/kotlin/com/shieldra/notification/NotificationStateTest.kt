package com.shieldra.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Amendment A-6: the permission state and the local posting result are two
 * separate facts, and neither is "delivered".
 *
 * `NotificationPermissionState` answers "may this OS version show a
 * notification, and did the user allow it?". `LocalPostResult` answers "what
 * happened when we actually handed a notification to Android on this device?".
 * Collapsing them would let an app claim an alert reached a human — which §31
 * and the owner's A-6 forbid — or would let a refused permission look like a
 * failed send (it is neither: no attempt is made).
 *
 * These are pure mappings so the API-level and permission branches are provable
 * without a device; the androidTest suite proves the real Android side.
 */
class NotificationStateTest {

    @Test
    fun below_api_33_the_runtime_permission_is_not_required() {
        assertEquals(
            NotificationPermissionState.NotRequired,
            NotificationPermissionState.forSdkInt(30),
            "Android 11 has no runtime POST_NOTIFICATIONS permission",
        )
        assertEquals(
            NotificationPermissionState.NotRequired,
            NotificationPermissionState.forSdkInt(26),
            "the minimum supported version needs no permission either",
        )
    }

    @Test
    fun at_or_above_api_33_a_missing_grant_is_not_requested() {
        assertEquals(
            NotificationPermissionState.NotRequested,
            NotificationPermissionState.forSdkInt(33, granted = null),
            "no answer yet means the feature has not been asked for",
        )
        assertEquals(
            NotificationPermissionState.NotRequested,
            NotificationPermissionState.forSdkInt(36, granted = null),
        )
    }

    @Test
    fun an_explicit_grant_and_an_explicit_refusal_are_distinguished() {
        assertEquals(
            NotificationPermissionState.Granted,
            NotificationPermissionState.forSdkInt(36, granted = true),
        )
        assertEquals(
            NotificationPermissionState.Denied,
            NotificationPermissionState.forSdkInt(36, granted = false),
            "an explicit refusal must never read as 'not asked yet'",
        )
    }

    @Test
    fun a_grant_on_an_old_version_is_not_required_rather_than_granted() {
        assertEquals(
            NotificationPermissionState.NotRequired,
            NotificationPermissionState.forSdkInt(30, granted = true),
            "a platform without the permission cannot be 'granted' it",
        )
    }

    @Test
    fun an_unavailable_permission_never_yields_a_posting_result() {
        val result = NotificationPermissionState.Denied.postIfPossible(
            sdkInt = 36,
            actuallyGranted = false,
            post = { LocalPostResult.PostedLocally },
        )
        assertEquals(
            LocalPostResult.NotAttempted,
            result,
            "a denied permission must produce no attempt at all, not a failure",
        )
    }

    @Test
    fun an_old_platform_posts_without_any_permission_question() {
        val result = NotificationPermissionState.NotRequired.postIfPossible(
            sdkInt = 30,
            actuallyGranted = false,
            post = { LocalPostResult.PostedLocally },
        )
        assertEquals(LocalPostResult.PostedLocally, result)
    }

    @Test
    fun a_granted_permission_reports_the_real_outcome_of_the_post() {
        val posted = NotificationPermissionState.Granted.postIfPossible(
            sdkInt = 36,
            actuallyGranted = true,
            post = { LocalPostResult.PostedLocally },
        )
        assertEquals(LocalPostResult.PostedLocally, posted)

        val failed = NotificationPermissionState.Granted.postIfPossible(
            sdkInt = 36,
            actuallyGranted = true,
            post = { LocalPostResult.FailedToPost("channel missing") },
        )
        assertEquals(LocalPostResult.FailedToPost("channel missing"), failed)
    }

    @Test
    fun the_grant_is_rechecked_at_post_time_not_trusted_from_earlier() {
        // The user can revoke in Settings while the process is alive; the stored
        // state says Granted but the live check says otherwise.
        val result = NotificationPermissionState.Granted.postIfPossible(
            sdkInt = 36,
            actuallyGranted = false,
            post = { LocalPostResult.PostedLocally },
        )
        assertEquals(
            LocalPostResult.NotAttempted,
            result,
            "a revoked permission must stop the post even if state still says Granted",
        )
    }

    @Test
    fun a_thrown_post_is_reported_as_failed_not_as_a_success() {
        val result = NotificationPermissionState.Granted.postIfPossible(
            sdkInt = 36,
            actuallyGranted = true,
            post = { throw IllegalStateException("no channel") },
        )
        val failed = result as LocalPostResult.FailedToPost
        assertTrue(
            failed.reason.contains("no channel"),
            "the real cause must be preserved instead of swallowed",
        )
    }

    @Test
    fun no_local_result_ever_reads_as_delivered() {
        val names = LocalPostResult.entries.map { it.name }
        assertTrue(
            names.none { it.contains("DELIVERED", ignoreCase = true) },
            "'PostedLocally' is the strongest local claim; DELIVERED belongs to remote delivery: $names",
        )
        assertTrue(
            names.none { it.contains("SENT", ignoreCase = true) },
            "a post call is not proof a human saw anything: $names",
        )
    }
}
