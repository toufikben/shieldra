package com.shieldra.protection

/**
 * Assembles the theft notice the holder of a locked phone reads (design D3).
 *
 * The app cannot know who picks up the phone, so the notice carries all three languages it ships:
 * Arabic, English and French. The owner's own sentence is appended when he wrote one, and his contact
 * is written once, at the end, so it is the last line either way.
 *
 * Pure: strings in, one string out.
 */
object TheftNoticeText {

    fun compose(
        arabic: String,
        english: String,
        french: String,
        ownerMessage: String?,
        contactLine: String?,
    ): String = buildString {
        val lines = mutableListOf(arabic.trim(), english.trim(), french.trim())
        ownerMessage?.trim()?.takeIf { it.isNotEmpty() }?.let(lines::add)
        contactLine?.trim()?.takeIf { it.isNotEmpty() }?.let(lines::add)
        append(lines.filter(String::isNotEmpty).joinToString("\n\n"))
    }
}
