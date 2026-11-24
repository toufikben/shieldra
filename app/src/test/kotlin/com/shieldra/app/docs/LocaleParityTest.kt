package com.shieldra.app.docs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P3-4: Arabic and English stay equal, checked by the test suite and not only by a shell script.
 *
 * `.github/scripts/locale-parity-check.sh` already compares the two files, but it is a step in a workflow
 * file: a step removed, a `continue-on-error` added, or a script deleted takes the whole guarantee with
 * it and nothing in the build notices. The audit rounds keep finding Arabic-only and English-only lines
 * (F-K11, Q-K-4), so the claim is pinned twice — once as the gate CI runs, and once here, where Gradle
 * runs it on every `test` and a red test cannot be scrolled past.
 *
 * Two things are compared, and the second one is the one that has actually broken:
 *
 * 1. **Keys.** A key that exists in one locale only is a line one phone never shows.
 * 2. **Format arguments, as a set per key.** A translation that drops `%1$s` prints a gap where the
 *    value belonged, or throws at `String.format` time on the owner's phone. A set rather than a
 *    sequence, because an Arabic sentence legitimately reorders its arguments.
 *
 * Arabic also has to *be* Arabic. A key present in `values-ar` but carrying the English sentence passes
 * a key comparison and still hands an Arabic owner English text, which is exactly the defect these
 * rounds keep turning up. The few exceptions are names, not sentences, and they are listed below.
 */
class LocaleParityTest {

    private val res: File by lazy {
        listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull(File::isDirectory)
            ?: error("no res directory above ${File(".").absolutePath}")
    }

    /** Elements that carry a name and a body: plain strings, and plurals folded into their own name. */
    private fun entries(locale: String, file: String): Map<String, String> {
        val text = File(res, "$locale/$file").readText()
        val out = linkedMapOf<String, String>()
        Regex("<string\\s+name=\"([A-Za-z0-9_]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .forEach { out[it.groupValues[1]] = it.groupValues[2] }
        Regex("<plurals\\s+name=\"([A-Za-z0-9_]+)\"[^>]*>(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .forEach { out[it.groupValues[1]] = it.groupValues[2] }
        return out
    }

    private fun args(body: String): Set<String> =
        Regex("%[0-9]+\\$[a-zA-Z]|%%|%[a-zA-Z]")
            .findAll(body)
            .map { it.value }
            .filter { it != "%%" }
            .toSet()

    @Test
    fun p3_4_the_two_locales_define_the_same_keys() {
        for (file in listOf("strings.xml", "plurals.xml")) {
            val en = entries("values", file).keys
            val ar = entries("values-ar", file).keys
            assertEquals(
                en - ar,
                emptySet(),
                "$file: keys English has and Arabic does not",
            )
            assertEquals(
                ar - en,
                emptySet(),
                "$file: keys Arabic has and English does not",
            )
        }
    }

    @Test
    fun p3_4_every_line_carries_the_same_format_arguments() {
        for (file in listOf("strings.xml", "plurals.xml")) {
            val en = entries("values", file)
            val ar = entries("values-ar", file)
            for (key in en.keys intersect ar.keys) {
                assertEquals(
                    args(en.getValue(key)),
                    args(ar.getValue(key)),
                    "$file: $key passes different format arguments in Arabic",
                )
            }
        }
    }

    @Test
    fun p3_4_an_arabic_line_is_arabic() {
        // Names and acronyms, not sentences: a phone shows SIM, GPS and SSL/TLS as they are, and the
        // language row names English in English so a lost owner can find their way back to it.
        val allowedWithoutArabic = setOf(
            "filter_sim",
            "location_source_gps",
            "alert_email_ssl",
            "alert_email_starttls",
            "privacy_billing_title",
            "language_english",
        )
        for (file in listOf("strings.xml", "plurals.xml")) {
            val ar = entries("values-ar", file)
            for ((key, body) in ar) {
                if (key in allowedWithoutArabic) continue
                // A format-only line ("%1$s") has no words to translate.
                val words = body.replace(Regex("%[0-9]+\\$[a-zA-Z]|%[a-zA-Z]"), "").replace(Regex("[\\s\\p{Punct}،؛؟«»]"), "")
                if (words.isEmpty()) continue
                assertTrue(
                    body.any { it in '\u0600'..'\u06FF' },
                    "$file: $key is in values-ar but carries no Arabic: $body",
                )
            }
        }
    }
}
