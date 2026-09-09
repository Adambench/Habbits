package dev.adambench.habbits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeekdaysTest {

    @Test
    fun iso_day_numbers_round_trip_through_the_mask() {
        val days = listOf(1, 4, 7)
        assertEquals(days, Weekdays.of(days).toIsoDayNumbers())
    }

    @Test
    fun monday_is_bit_zero_and_sunday_is_bit_six() {
        assertEquals(0b000_0001, Weekdays.of(listOf(1)).mask)
        assertEquals(0b100_0000, Weekdays.of(listOf(7)).mask)
        assertEquals(0b111_1111, Weekdays.All.mask)
    }

    @Test
    fun out_of_range_days_are_ignored_rather_than_corrupting_the_mask() {
        assertEquals(Weekdays.None, Weekdays.of(listOf(0, 8, -3)))
        assertFalse(0 in Weekdays.All)
        assertFalse(8 in Weekdays.All)
    }

    @Test
    fun adding_and_removing_days_is_symmetric() {
        val friday = Weekdays.None.plus(5)
        assertTrue(5 in friday)
        assertFalse(5 in friday.minus(5))
        assertEquals(Weekdays.None, friday.minus(5))
    }

    @Test
    fun empty_is_reported_for_no_days_selected() {
        assertTrue(Weekdays.None.isEmpty)
        assertFalse(Weekdays.All.isEmpty)
    }
}
