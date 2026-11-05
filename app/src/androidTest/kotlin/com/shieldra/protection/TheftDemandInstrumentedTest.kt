package com.shieldra.protection

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A-45 D3 on the device: the demand notice, posted and read back exactly as the holder of a locked
 * phone would see it.
 *
 * The JVM copy tests ([com.shieldra.protection.TheftDemandCopyTest]) prove the *files* carry three
 * languages. They cannot prove that `TheftDemand.show` pulls all three at once on a real handset, that
 * the contact survives the round trip into the notification, or that the countdown base is actually in
 * the future — and those are the parts the owner's complaint was about («الجملة القصيرة يجب ان تكون
 * بكل اللغات حتى يفهم أمر آخر»). This test posts the notice, reads it back out of
 * [NotificationManager.getActiveNotifications], and takes it down again.
 *
 * It never locks the screen and never touches the keyguard: the phone this runs on is shared with
 * another inspector, and a lock would leave it at a screen only its owner can open. The lock half of
 * the ladder is recorded as NOT RUN in the round-up, deliberately.
 *
 * The expected sentences are read from the app's own resources per locale rather than written out
 * here, so the test cannot drift away from the copy it is checking.
 */
@RunWith(AndroidJUnit4::class)
class TheftDemandInstrumentedTest {

    private val app: Context = ApplicationProvider.getApplicationContext()

    /** The same three copies `TheftDemand` composes, read straight from the shipped resources. */
    private fun bodyIn(tag: String): String {
        val configuration = Configuration(app.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return app.createConfigurationContext(configuration)
            .getString(com.shieldra.app.R.string.theft_demand_body)
    }

    private fun posted(): Notification? =
        app.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.firstOrNull { it.id == TheftDemand.NOTIFICATION_ID }
            ?.notification

    /**
     * The holder's whole message, whichever of the two text slots the system filled. A big-style notice
     * normally carries both, and reading them in this order keeps the test from failing on a handset that
     * only ever exposes one.
     */
    private fun holderText(notice: Notification): String =
        notice.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: notice.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: error("the notice carries no text at all")

    /**
     * Android 13+ can refuse the post entirely, and a refused post is not a broken notice. The check is
     * on the context rather than through a compat helper so this test adds no dependency.
     */
    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @Test
    fun the_holder_sees_all_three_languages_and_the_contact_once() {
        assumeTrue(
            "notifications are not allowed to this build on this API level",
            notificationsAllowed(),
        )
        val contact = "+213 555 12 34 56"
        try {
            TheftDemand.show(app, contact, "Call me after 8 pm")

            val notice = requireNotNull(posted()) {
                "the demand was never posted — a locked phone would say nothing"
            }

            val text = holderText(notice)

            for (tag in listOf("ar", "en", "fr")) {
                assertTrue(
                    "the notice the holder reads is missing the $tag copy: $text",
                    text.contains(bodyIn(tag)),
                )
            }

            // The owner's own sentence rides along, and the contact lands once — normalized, because a
            // stranger should be able to tap it rather than retype it.
            assertTrue("the owner's sentence is missing: $text", text.contains("Call me after 8 pm"))
            assertEquals(
                "the contact must be written exactly once",
                1,
                text.split(OwnerContact.normalized(contact)).size - 1,
            )

            // What makes it a demand rather than a toast: public on a locked screen, sticky, counting
            // down the window the ladder waits for, and one action that dials.
            assertEquals(
                "the notice must be visible on the lock screen",
                Notification.VISIBILITY_PUBLIC,
                notice.visibility,
            )
            assertTrue("the demand must stay until it is answered", notice.isOngoing)
            val remaining = notice.`when` - System.currentTimeMillis()
            assertTrue(
                "the countdown must sit inside the approved ${SnatchLadder.DEMAND_MS} ms window, got $remaining",
                remaining in 1..SnatchLadder.DEMAND_MS,
            )
            assertEquals("a phone number earns exactly one action", 1, notice.actions?.size ?: 0)

            // The round-up quotes this line: it is the notice as the handset actually rendered it, with
            // the newlines kept visible, so a reader can see the three copies without holding the phone.
            Log.i(TAG, "SHIELDRA_DEMAND_PHONE ${notice.`when`} ACTIONS ${notice.actions?.size}")
            Log.i(TAG, "SHIELDRA_DEMAND_TEXT ${text.replace("\n", "\\n")}")
        } finally {
            TheftDemand.hide(app)
        }
        assertNull("the notice outlived the hide, so an owner unlock would not clear it", posted())
    }

    @Test
    fun an_unusable_contact_is_shown_as_text_and_never_becomes_a_button() {
        assumeTrue(
            "notifications are not allowed to this build on this API level",
            notificationsAllowed(),
        )
        try {
            TheftDemand.show(app, "find me at the coffee shop", "")
            val notice = requireNotNull(posted()) {
                "even without a usable contact the holder must be told"
            }

            assertEquals("a line that is not a number gets no dial action", 0, notice.actions?.size ?: 0)
            val text = holderText(notice)
            assertTrue(
                "the owner's text is still shown, unreadable as a number it may be",
                text.contains("find me at the coffee shop"),
            )
            assertTrue(
                "the three copies are composed regardless of the contact",
                listOf("ar", "en", "fr").all { text.contains(bodyIn(it)) },
            )
            Log.i(TAG, "SHIELDRA_DEMAND_PLAINTEXT ${text.replace("\n", "\\n")}")
        } finally {
            TheftDemand.hide(app)
        }
    }

    private companion object {
        const val TAG = "ShieldraDemand"
    }
}
