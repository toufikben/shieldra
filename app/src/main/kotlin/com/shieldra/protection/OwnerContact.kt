package com.shieldra.protection

/** What the owner typed into the contact field actually is. */
enum class ContactKind { PHONE, EMAIL, UNKNOWN }

/**
 * The single reading of the owner's contact (design D3). The value is shown to a stranger and turned
 * into a call action, so the app must know whether it can honestly offer to dial it: a phone number,
 * an address, or neither. A contact the app cannot classify is displayed but never made actionable.
 */
object OwnerContact {

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[A-Za-z]{2,}$")

    /** Digits with an optional leading `+`, after separators are dropped, between 6 and 15 long. */
    private val PHONE = Regex("^\\+?[0-9]{6,15}$")

    fun kind(raw: String): ContactKind {
        val trimmed = raw.trim()
        return when {
            EMAIL.matches(trimmed) -> ContactKind.EMAIL
            PHONE.matches(trimmed.digitsOnly()) -> ContactKind.PHONE
            else -> ContactKind.UNKNOWN
        }
    }

    /** The form that is displayed and dialled: separators out, address lowercased. */
    fun normalized(raw: String): String = when (kind(raw)) {
        ContactKind.EMAIL -> raw.trim().lowercase()
        ContactKind.PHONE -> raw.digitsOnly()
        ContactKind.UNKNOWN -> raw.trim()
    }

    /** The intent data for the notice's call action, or null when there is nothing honest to dial. */
    fun dialUri(raw: String): String? = when (kind(raw)) {
        ContactKind.PHONE -> "tel:${normalized(raw)}"
        ContactKind.EMAIL -> "mailto:${normalized(raw)}"
        ContactKind.UNKNOWN -> null
    }

    /** Spaces, dashes and brackets are formatting, not part of a number. */
    private fun String.digitsOnly(): String = filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' }
}
