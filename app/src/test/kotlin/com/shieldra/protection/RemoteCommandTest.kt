package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The raw Telegram shapes the poller must read the same way every time. This is the guard around
 * `/lock`: the command that can blank the screen is recognized from a bare message, a bot-tagged
 * message and any letter case — and an unknown verb is still left for the poller to ignore.
 */
class RemoteCommandTest {

    @Test
    fun a_bare_lock_command_has_no_argument() {
        assertEquals(RemoteCommand("/lock", ""), RemoteCommand.parse("/lock"))
    }

    @Test
    fun a_bot_tag_is_stripped_from_the_command() {
        assertEquals(RemoteCommand("/lock", ""), RemoteCommand.parse("/lock@ShieldraBot"))
    }

    @Test
    fun the_command_is_case_insensitive() {
        assertEquals(RemoteCommand("/lock", ""), RemoteCommand.parse("/LOCK"))
        assertEquals(RemoteCommand("/track", "off"), RemoteCommand.parse("/TRACK OFF"))
    }

    @Test
    fun an_argument_is_trimmed_and_lowercased() {
        assertEquals(RemoteCommand("/track", "off"), RemoteCommand.parse("/track   OFF  "))
        assertEquals(RemoteCommand("/lock", "now"), RemoteCommand.parse("/lock now"))
    }

    @Test
    fun a_bot_tag_and_an_argument_can_arrive_together() {
        assertEquals(RemoteCommand("/track", "off"), RemoteCommand.parse("/track@ShieldraBot off"))
    }

    @Test
    fun other_commands_keep_their_names() {
        assertEquals(RemoteCommand("/photo", ""), RemoteCommand.parse("/photo@MyBot"))
        assertEquals(RemoteCommand("/locate", ""), RemoteCommand.parse("/locate"))
        assertEquals(RemoteCommand("/status", ""), RemoteCommand.parse("/status"))
    }

    @Test
    fun an_unknown_command_is_preserved_for_the_poller_to_ignore() {
        assertEquals(RemoteCommand("/wipe", ""), RemoteCommand.parse("/wipe"))
    }

    @Test
    fun an_empty_message_is_an_empty_command() {
        assertEquals(RemoteCommand("", ""), RemoteCommand.parse(""))
    }
}
