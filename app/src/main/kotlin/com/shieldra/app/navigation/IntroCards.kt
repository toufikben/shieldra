package com.shieldra.app.navigation

/**
 * P4-22 (owner order 2026-11-26): the short tour shown once, after setup, that says what this app
 * actually does.
 *
 * The list is data on purpose, not a hand-written row of screens. Two reasons:
 *
 * - **The order is the owner's.** He named these features in this order, and the tour follows it.
 * - **Every key is a join.** A card's title and body are resources named `intro_<key>_title` and
 *   `intro_<key>_body`, so a key and its strings can be checked against each other by a test instead of
 *   drifting apart silently. The screen reads the same keys.
 *
 * Nothing here claims anything. The numbers a card states (the two-minute siren cap, the ten-minute
 * wipe window, the thirty-day retention) are asserted against the code that enforces them, because a
 * promise printed on the first screen the owner opens is the kind of sentence this project has had to
 * correct before.
 */
object IntroCards {

    /** One card: the key its two resources are named after. */
    data class Card(val key: String)

    /**
     * Every card, in the order the owner named the features. The list is the whole tour: adding a card
     * means adding its two strings in both locales, and the truth test fails until they exist.
     */
    val cards: List<Card> = listOf(
        Card("lock"),
        Card("motion"),
        Card("sim"),
        Card("battery"),
        Card("snatch"),
        Card("safe_zones"),
        Card("evidence"),
        Card("delivery"),
        Card("remote"),
        Card("siren"),
        Card("tracking"),
        Card("wipe"),
    )

    /** How many cards the tour has; the screen shows "n of this". */
    val count: Int get() = cards.size
}
