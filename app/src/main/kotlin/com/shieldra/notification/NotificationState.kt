package com.shieldra.notification

/**
 * What the operating system says about notification permission (amendment A-6).
 *
 * This is a fact about the *device*, never about a delivery. It is kept apart
 * from [LocalPostResult] so a refusal can never be mistaken for a failed send,
 * and a successful post can never be mistaken for a permission being granted.
 */
enum class NotificationPermissionState {
    /** This OS version has no runtime notification permission to ask for. */
    NotRequired,

    /** The permission exists and nobody has answered yet. */
    NotRequested,

    /** The permission exists and the user allowed it. */
    Granted,

    /** The permission exists and the user refused it. */
    Denied;

    companion object {
        /** The first API level with a runtime POST_NOTIFICATIONS permission. */
        const val RUNTIME_PERMISSION_FROM_SDK_INT: Int = 33

        /**
         * Maps the real platform answer onto the state.
         *
         * `granted` is null when the platform cannot answer yet (before the
         * request, or on a version with no such permission). On a version
         * without the permission the answer is [NotRequired] whatever the flag
         * says — a platform cannot be granted a permission it does not have.
         */
        fun forSdkInt(sdkInt: Int, granted: Boolean?): NotificationPermissionState = when {
            sdkInt < RUNTIME_PERMISSION_FROM_SDK_INT -> NotRequired
            granted == null -> NotRequested
            granted -> Granted
            else -> Denied
        }
    }

    /**
     * Posts only when this state allows it, and only after the *live* grant has
     * been re-checked. The caller passes `actuallyGranted` because a user can
     * revoke the permission in Settings while the process is alive; a cached
     * `Granted` must not be trusted.
     *
     * A refusal produces [LocalPostResult.NotAttempted]: no notification was
     * built, so there is nothing to report as succeeded or failed.
     */
    fun postIfPossible(
        sdkInt: Int,
        actuallyGranted: Boolean,
        post: () -> LocalPostResult,
    ): LocalPostResult {
        val live = forSdkInt(sdkInt, if (sdkInt >= RUNTIME_PERMISSION_FROM_SDK_INT) actuallyGranted else null)
        if (live != Granted && live != NotRequired) return LocalPostResult.NotAttempted
        return try {
            post()
        } catch (error: Exception) {
            LocalPostResult.FailedToPost(error.message ?: error::class.java.simpleName)
        }
    }
}

/**
 * What happened when SHIELDRA handed a notification to Android on this device
 * (amendment A-6).
 *
 * [PostedLocally] is the strongest claim SHIELDRA may make about a
 * notification, and it means exactly one thing: the platform accepted the post.
 * It is not proof that a human saw it, opened it, or that anything reached
 * another device. `DELIVERED` remains reserved for real remote delivery, which
 * this batch does not implement (§30).
 */
sealed interface LocalPostResult {
    /** Nothing was posted: the permission did not allow it. */
    data object NotAttempted : LocalPostResult

    /** Android accepted the local post. Nothing more is claimed. */
    data object PostedLocally : LocalPostResult

    /** A post was attempted and failed; [reason] carries the real cause. */
    data class FailedToPost(val reason: String) : LocalPostResult
}
