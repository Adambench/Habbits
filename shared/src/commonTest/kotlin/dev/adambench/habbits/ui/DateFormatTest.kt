package dev.adambench.habbits.ui

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DateFormatTest {

    @Test
    fun weekday_names_line_up_with_iso_numbering() {
        // 2026-09-07 is a Monday.
        assertEquals("Mon", LocalDate(2026, 9, 7).weekdayShort())
        assertEquals("Sun", LocalDate(2026, 9, 13).weekdayShort())
    }

    @Test
    fun month_names_are_not_off_by_one() {
        assertEquals("Jan", LocalDate(2026, 1, 1).monthShort())
        assertEquals("Sep", LocalDate(2026, 9, 10).monthShort())
        assertEquals("Dec", LocalDate(2026, 12, 31).monthShort())
    }

    @Test
    fun header_reads_as_a_date() {
        assertEquals("Thu, Sep 10", LocalDate(2026, 9, 10).headerLabel())
    }

    @Test
    fun weekday_initials_are_single_characters() {
        val week = (7..13).map { LocalDate(2026, 9, it).weekdayInitial() }
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), week)
    }
}
