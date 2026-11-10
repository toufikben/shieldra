package com.shieldra.protection

/**
 * The lock-screen message a confirmed snatch leaves for whoever is holding the phone (A-46).
 *
 * It is written in the three languages the owner asked for, one paragraph each, because the person
 * holding a stolen phone is not necessarily the person the app's language is set to. The owner's own
 * note and contact line come last, and only when they exist: an empty line is a blank paragraph on a
 * lock screen, which reads as a broken notice rather than a short one.
 *
 * Pure, so the joining rule is stated without Android.
 */
object SnatchText {

    fun compose(
        arabic: String,
        english: String,
        french: String,
        ownerMessage: String?,
        contactLine: String?,
    ): String {
        val languages = listOf(arabic, english, french).map { it.trim() }.filter { it.isNotEmpty() }
        val extras = listOf(ownerMessage.orEmpty().trim(), contactLine.orEmpty().trim())
            .filter { it.isNotEmpty() }
        return (languages + extras).joinToString("\n\n")
    }
}
