package com.shieldra.protection

/**
 * Turns a raw Telegram message into the command the poller acts on. Pure, so JVM tests can pin the
 * shapes a real chat produces — `/alarm`, `/ALARM`, `/alarm@ShieldraBot`, `/track off` — without a
 * device or a bot. Only the first word is a command; everything after it is one trimmed argument.
 * The poller ignores a verb it does not know, exactly as before.
 */
data class RemoteCommand(val verb: String, val argument: String) {
    companion object {
        fun parse(raw: String): RemoteCommand = RemoteCommand(
            verb = raw.substringBefore(' ').substringBefore('@').lowercase(),
            argument = raw.substringAfter(' ', "").trim().lowercase(),
        )
    }
}
