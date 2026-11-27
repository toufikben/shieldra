package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-26: the retention window is the one bounded field that `save` writes without clipping it.
 *
 * `EvidenceRetentionPolicy` publishes the range the owner's window may take
 * (`MIN_OWNER_DAYS = 7`, `MAX_OWNER_DAYS = 365`) and explains why: below the floor the sweep deletes
 * evidence the owner had no chance to read, above the ceiling a promise of deletion stops meaning
 * anything. The settings screen draws its slider inside exactly that pair, and `fromOwnerDays` is
 * what turns the setting into the windows the sweeper honours.
 *
 * `ProtectionSettingsStore.save` clips every other bounded field to the bounds its own object
 * publishes — lock threshold, battery level, grace, tracking interval and duration, audio seconds,
 * escalation threshold, geofence interval, snatch interval — and writes `retentionDays` straight
 * through. So a value written by an older build, by a restored backup, or by any code path that is
 * not the slider is stored outside the range the screen offers and the policy documents, and the
 * screen then draws a slider whose handle sits past the end of its own track. `fromOwnerDays` does
 * not catch it either: it only refuses zero and negative values, so `3` days or `5000` days are
 * honoured as written.
 *
 * This is the same defect P4-19 fixed for the four sliders, in the one field that was missed: a
 * bound is published on the object that decides the value, so `save` has to clip to that same pair
 * or the publication means nothing.
 */
class RetentionBoundsTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private val store: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/protection/ProtectionSettings.kt").readText()
    }

    private val policy: String by lazy {
        File(root, "app/src/main/kotlin/com/shieldra/storage/EvidenceRetentionPolicy.kt").readText()
    }

    /** The `save` body, where each stored field is written. */
    private fun saveBody(): String {
        val start = store.indexOf("fun save(settings: ProtectionSettings)")
        assertTrue(start > 0, "the settings writer is gone, so this finding must be re-measured")
        val end = store.indexOf("\n    }", start)
        return store.substring(start, if (end > start) end else store.length)
    }

    /** The bounds are still published, so the finding is about the writer and not the policy. */
    @Test
    fun p4_26_the_policy_still_publishes_the_owner_range() {
        assertTrue(
            policy.contains("const val MIN_OWNER_DAYS: Int = 7"),
            "the owner floor is gone, so this finding must be re-measured",
        )
        assertTrue(
            policy.contains("const val MAX_OWNER_DAYS: Int = 365"),
            "the owner ceiling is gone, so this finding must be re-measured",
        )
    }

    /** Every other bounded field is clipped, so the shape this finding compares against is real. */
    @Test
    fun p4_26_the_writer_still_clips_the_fields_it_does_clip() {
        val body = saveBody()
        assertTrue(
            body.contains("KEY_LOCK_THRESHOLD, settings.lockThreshold.coerceIn("),
            "the writer stopped clipping the lock threshold, so this finding must be re-measured",
        )
        assertTrue(
            body.contains("KEY_AUDIO_SECONDS, settings.audioSeconds.coerceIn("),
            "the writer stopped clipping the audio length, so this finding must be re-measured",
        )
    }

    /** And the retention window is clipped to the same pair the slider draws inside. */
    @Test
    fun p4_26_the_writer_clips_the_retention_window() {
        val body = saveBody()
        assertTrue(
            body.contains("KEY_RETENTION, settings.retentionDays.coerceIn("),
            "the retention window is stored as written, so a value from an older build or a backup " +
                "sits outside the range the screen offers and the policy documents, and the slider " +
                "draws a handle past the end of its own track",
        )
    }
}
