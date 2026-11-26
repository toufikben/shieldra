package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P3-1: the README describes the build that exists.
 *
 * It used to end with "Not implemented yet: Lock, SIM and Battery guards; camera or location evidence;
 * any delivery off the phone", while every one of those now ships: the SIM and battery guards run
 * inside the protection service, captures take a photo and a fix, and alerts leave through the owner's
 * own Telegram and email. A README that under-reports is the same defect as one that over-reports — a
 * reader cannot tell which sentences are still true, so none of them are trusted.
 *
 * The file is read from disk rather than through a resource because the claim is about the repository's
 * own front page, which no androidTest or JVM context can reach.
 */
class ReadmeTruthTest {

    private val readme: String by lazy {
        listOf(File("../README.md"), File("README.md"))
            .firstOrNull(File::isFile)
            ?.readText()
            ?: error("no README.md above ${File(".").absolutePath}")
    }

    @Test
    fun p3_1_the_readme_stops_calling_shipped_guards_unimplemented() {
        val stale = listOf(
            "Lock, SIM and Battery guards",
            "camera or location evidence",
            "any delivery off the phone",
        )
        for (phrase in stale) {
            assertTrue(
                !readme.contains(phrase),
                "README still says \"$phrase\" while the build ships it",
            )
        }
    }

    @Test
    fun p3_1_the_readme_names_what_actually_ships() {
        val shipped = mapOf(
            "the protection service" to "protection",
            "the SIM guard" to "SIM",
            "the battery guard" to "battery",
            "photo and location evidence" to "evidence",
            "the owner's own Telegram" to "Telegram",
            "the failed-unlock guard" to "failed unlock",
            "the snatch lock" to "snatch lock",
            "the safe zones" to "safe zone",
            "Express the honest limits" to "cannot",
        )
        for ((what, token) in shipped) {
            assertTrue(
                readme.contains(token, ignoreCase = true),
                "README never mentions $what (expected token \"$token\")",
            )
        }
    }

    @Test
    fun p3_1_the_readme_states_the_limits_it_cannot_cross() {
        // The facts a reader must not have to discover after buying: the lock is self-triggered only,
        // the device-admin role carries no destructive power, and a SIM swap is never proven.
        for (token in listOf(
            "never on demand",
            "no destructive device administrator",
            "never proven",
        )) {
            assertTrue(
                readme.contains(token, ignoreCase = true),
                "README is silent about \"$token\"",
            )
        }
    }

    @Test
    fun p3_1_the_readme_does_not_repeat_the_retired_design_b_limits() {
        // Design B (2026-11-06) removed the lock; A-46 (owner decision 2026-11-10) restored it. A
        // front page that still denies the lock power describes a build that no longer ships.
        for (token in listOf(
            "cannot lock the screen",
            "no device administrator",
            "holds no device administrator",
            "left the app without any lock power",
        )) {
            assertFalse(
                readme.contains(token, ignoreCase = true),
                "README still carries the retired claim \"$token\"",
            )
        }
    }
}
