package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P4-19: the setup sliders bound themselves with literals written into the screen.
 *
 * Four sliders on the Protection screen take their range from a number typed in
 * `ProtectionSetupScreen.kt` rather than from the object that decides what the value may be:
 *
 *  - the wrong-unlock threshold uses `1f..5f` while `FailedUnlockCounter` publishes
 *    `MIN_THRESHOLD`/`MAX_THRESHOLD`, and `save` clamps to those;
 *  - the battery level uses `5f..50f` while `BatteryBreathLogic` publishes `MIN_LEVEL = 3`
 *    and `MAX_LEVEL = 50`, so the slider silently refuses the lowest two levels the guard
 *    really accepts;
 *  - the audio clip uses `5f..30f` while `AudioCapture.record` clamps to `3..60`, so the owner
 *    cannot ask for the 45 seconds the recorder would honour, and the slider's own range is
 *    a promise the recorder never made;
 *  - the Motion grace window uses `3f..30f` while `TableModeResponder` accepts any
 *    non-negative count, so the screen is the only bound and nothing publishes it.
 *
 * The pattern the repository already fixed three times (P4-11 escalation, P4-12 retention,
 * P4-13 tracking) is: the policy object publishes `MIN`/`MAX`, the screen draws the slider
 * inside them, and `save` clamps to the same pair. These four were left behind.
 *
 * The test reads the screen's source rather than its behaviour because the defect is exactly
 * a disagreement between two files, and only the source shows both sides of it.
 */
class SliderBoundsTruthTest {

    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "app/src/main").isDirectory }
            ?: error("no app module above ${File("").absolutePath}")
    }

    private fun main(relative: String): String = File(root, "app/src/main/$relative").readText()

    private val screen: String by lazy {
        main("kotlin/com/shieldra/app/presentation/screen/settings/ProtectionSetupScreen.kt")
    }

    /**
     * Every slider in the file takes its range from a published bound or from a named
     * constant, never from a pair of literals.
     */
    @Test
    fun p4_19_no_slider_bounds_itself_with_literals() {
        val literalRanges = Regex("""range\s*=\s*-?\d+(\.\d+)?f?\s*\.\.\s*-?\d+(\.\d+)?f?""")
            .findAll(screen)
            .map { it.value }
            .toList()
        assertTrue(
            literalRanges.isEmpty(),
            "the Protection screen still bounds a slider with a literal: " +
                "$literalRanges. The range belongs to the object that decides the value.",
        )
    }

    /** The audio clip's own bounds are published, so the slider can reach what the recorder honours. */
    @Test
    fun p4_19_the_audio_bounds_are_published_by_the_recorder() {
        val audio = main("kotlin/com/shieldra/protection/AudioCapture.kt")
        for (bound in listOf("MIN_SECONDS", "MAX_SECONDS")) {
            assertTrue(
                audio.contains(bound),
                "AudioCapture publishes no $bound, so the setup slider has nothing to take its range from",
            )
        }
        assertTrue(
            screen.contains("AudioCapture.MIN_SECONDS") && screen.contains("AudioCapture.MAX_SECONDS"),
            "the audio slider does not take its range from AudioCapture's own bounds",
        )
        assertTrue(
            audio.contains("coerceIn(MIN_SECONDS, MAX_SECONDS)"),
            "the recorder no longer clamps to the bounds the slider shows, so the two can drift apart",
        )
    }

    /** The Motion grace window's bounds are published, so the screen is not the only bound. */
    @Test
    fun p4_19_the_grace_bounds_are_published() {
        val plan = main("kotlin/com/shieldra/protection/MotionResponsePlan.kt")
        for (bound in listOf("MIN_GRACE_SECONDS", "MAX_GRACE_SECONDS")) {
            assertTrue(
                plan.contains(bound),
                "MotionResponsePlan publishes no $bound, so the grace slider has nothing to take its range from",
            )
        }
        assertTrue(
            screen.contains("MotionResponsePlan.MIN_GRACE_SECONDS"),
            "the grace slider does not take its range from MotionResponsePlan's own bounds",
        )
    }

    /** The store clamps the two values the screen now bounds, so both sides agree. */
    @Test
    fun p4_19_the_store_clamps_to_the_same_bounds() {
        val store = main("kotlin/com/shieldra/protection/ProtectionSettings.kt")
        assertTrue(
            store.contains(
                "KEY_AUDIO_SECONDS, settings.audioSeconds.coerceIn(" +
                    "AudioCapture.MIN_SECONDS, AudioCapture.MAX_SECONDS)",
            ),
            "save() writes the audio length unclamped, so the stored clip can be longer than the " +
                "slider can show",
        )
        assertTrue(
            store.contains(
                "KEY_MOTION_GRACE, settings.motionGraceSeconds.coerceIn(" +
                    "MotionResponsePlan.MIN_GRACE_SECONDS, MotionResponsePlan.MAX_GRACE_SECONDS)",
            ),
            "save() writes the grace window unclamped, so a stored wait can fall outside the bounds " +
                "the screen draws",
        )
    }

    /** The two counters' sliders take their range from the classes that clamp them. */
    @Test
    fun p4_19_the_counter_sliders_take_their_published_bounds() {
        for ((what, bound) in listOf(
            "wrong-unlock threshold" to "FailedUnlockCounter.MIN_THRESHOLD",
            "battery level" to "BatteryBreathLogic.MIN_LEVEL",
        )) {
            assertTrue(
                screen.contains(bound),
                "the $what slider does not take its range from $bound, so it can offer a value the " +
                    "store then silently clamps",
            )
        }
    }
}
