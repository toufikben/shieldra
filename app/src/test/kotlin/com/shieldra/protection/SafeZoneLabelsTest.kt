package com.shieldra.protection

import kotlin.test.Test
import kotlin.test.assertEquals

/** G-11: a default zone label must never repeat the way `Zone ${size + 1}` did after a deletion. */
class SafeZoneLabelsTest {

    private fun zone(name: String) = SafeZone(name, GeofenceLogic.Zone(0.0, 0.0, 200.0))

    @Test
    fun the_first_default_name_is_number_one() {
        assertEquals("Zone 1", SafeZoneLabels.nextDefaultName(emptyList()))
    }

    @Test
    fun a_deleted_place_is_reused_instead_of_colliding_with_the_survivors() {
        // Zones 1 and 2, then 1 was deleted: the old rule named the next one "Zone 2" again.
        assertEquals("Zone 2", SafeZoneLabels.nextDefaultName(listOf(zone("Zone 1"), zone("Zone 3"))))
    }

    @Test
    fun names_without_a_number_do_not_block_numbering() {
        assertEquals("Zone 1", SafeZoneLabels.nextDefaultName(listOf(zone("Home"), zone("Work"))))
    }

    @Test
    fun the_number_is_not_capped_at_a_small_count() {
        val ten = (1..10).map { zone("Zone $it") }

        assertEquals("Zone 11", SafeZoneLabels.nextDefaultName(ten))
    }
}
