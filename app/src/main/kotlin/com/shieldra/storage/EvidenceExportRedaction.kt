package com.shieldra.storage

/**
 * The §5.5 redaction policy for an evidence export: what may leave the encrypted store and what is
 * stripped. Encryption keys and the owner's delivery secrets (bot token, app password) are NEVER
 * exportable — exporting them would defeat the whole at-rest encryption. Evidence (photo, audio,
 * location) and event metadata may be exported; the owner may additionally exclude location.
 *
 * Pure: the available items and the owner's location choice in, the redacted set out. Unit-tested.
 */
object EvidenceExportRedaction {

    enum class Item {
        PHOTO,
        AUDIO,
        LOCATION,
        EVENT_METADATA,
        ENCRYPTION_KEY,
        DELIVERY_SECRET,
    }

    /** Items that may never be exported under any option. */
    val NEVER_EXPORTABLE: Set<Item> = setOf(Item.ENCRYPTION_KEY, Item.DELIVERY_SECRET)

    fun included(available: Set<Item>, includeLocation: Boolean): Set<Item> =
        available
            .minus(NEVER_EXPORTABLE)
            .let { if (includeLocation) it else it.minus(Item.LOCATION) }
}
