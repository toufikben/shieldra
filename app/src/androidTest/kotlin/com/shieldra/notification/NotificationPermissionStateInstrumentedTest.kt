package com.shieldra.notification

import android.content.Context
import android.app.NotificationManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Android half of amendment A-6, on the real device.
 *
 * A runtime permission the app has never asked for reads as DENIED to
 * `checkSelfPermission` — the platform cannot tell "refused" from "silent". The
 * screen must not turn that silence into a refusal the user never made, because
 * A-6 requires the three states (not requested / allowed / refused) to stay
 * distinct and honest.
 *
 * The pure mapping (API level, grant, result) is covered by the JVM suite; this
 * suite proves the parts that only the platform can answer.
 */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionStateInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun a_permission_that_was_never_asked_for_is_not_reported_as_refused() {
        val notifier = LocalEventNotifier(context, FakePermissionRequestRecord(requested = false))
        val expected = if (Build.VERSION.SDK_INT < NotificationPermissionState.RUNTIME_PERMISSION_FROM_SDK_INT) {
            NotificationPermissionState.NotRequired
        } else {
            NotificationPermissionState.NotRequested
        }
        assertEquals(
            "before the dialog has been shown the platform's silence is not a refusal (A-6)",
            expected,
            notifier.permissionState(),
        )
    }

    @Test
    fun once_the_app_has_asked_the_state_is_never_shown_as_not_requested() {
        val notifier = LocalEventNotifier(context, FakePermissionRequestRecord(requested = true))
        val state = notifier.permissionState()
        assertTrue(
            "after a request the state must be a real answer, but was $state",
            state == NotificationPermissionState.Granted ||
                state == NotificationPermissionState.Denied ||
                state == NotificationPermissionState.NotRequired,
        )
    }

    @Test
    fun posting_reports_only_what_the_platform_actually_did() {
        val notifier = LocalEventNotifier(context, FakePermissionRequestRecord(requested = true))
        val result = notifier.postSecurityEvent(
            eventId = "device-notification-post",
            statusLabel = "device",
        )
        val granted = notifier.permissionState() == NotificationPermissionState.Granted ||
            notifier.permissionState() == NotificationPermissionState.NotRequired
        assertEquals(
            "a post must succeed exactly when the permission allows it, and never claim more " +
                "(state=${notifier.permissionState()}, result=$result)",
            granted,
            result is LocalPostResult.PostedLocally,
        )
    }

    @Test
    fun the_security_event_channel_exists_on_the_device_after_ensure_channel() {
        val notifier = LocalEventNotifier(context, FakePermissionRequestRecord(requested = false))
        assertTrue(
            "the channel must be created without throwing on this device",
            notifier.ensureChannel(),
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(
            "a notification posted to a missing channel is dropped silently, so the channel must exist",
            manager.getNotificationChannel(LocalEventNotifier.DEFAULT_CHANNEL_ID),
        )
    }

    /** Test-only record: the device suite must not depend on what earlier presses stored. */
    private class FakePermissionRequestRecord(var requested: Boolean) : PermissionRequestRecord {
        override fun hasBeenRequested(): Boolean = requested

        override fun markRequested() {
            requested = true
        }
    }
}
