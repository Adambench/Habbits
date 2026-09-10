package io.github.adambench.habbits.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrayerClockTest {

    private val montreal = TimeZone.of("America/Toronto")
    private val settings = PrayerSettings()

    private fun times(date: LocalDate, s: PrayerSettings = settings) =
        PrayerClock.timesFor(date, s, montreal)

    @Test
    fun computes_all_six_times_for_montreal() {
        val t = times(LocalDate(2026, 9, 10))
        listOf(
            Category.Fajr, Category.Shuruq, Category.Dhuhr,
            Category.Asr, Category.Maghrib, Category.Isha,
        ).forEach { assertTrue(t[it] != null, "missing $it") }
        println("Montreal 2026-09-10: " + t.times.entries.joinToString { "${it.key.displayName} ${it.value}" })
    }

    @Test
    fun times_run_in_order_through_the_day() {
        val t = times(LocalDate(2026, 9, 10))
        val ordered = listOf(
            Category.Fajr, Category.Shuruq, Category.Dhuhr,
            Category.Asr, Category.Maghrib, Category.Isha,
        ).map { t[it]!! }
        assertEquals(ordered.sorted(), ordered, "prayer times must increase through the day")
    }

    @Test
    fun midsummer_still_resolves_at_montreals_latitude() {
        // The short nights near the solstice are exactly where a missing
        // high-latitude rule produces nonsense.
        val t = times(LocalDate(2026, 6, 21))
        val fajr = t[Category.Fajr]!!
        val isha = t[Category.Isha]!!
        assertTrue(fajr < t[Category.Shuruq]!!, "Fajr must precede sunrise: $fajr")
        assertTrue(isha > t[Category.Maghrib]!!, "Isha must follow Maghrib: $isha")
        println("Montreal 2026-06-21: Fajr $fajr, Isha $isha")
    }

    @Test
    fun sunrise_in_september_is_in_the_morning() {
        val sunrise = times(LocalDate(2026, 9, 10))[Category.Shuruq]!!
        assertTrue(sunrise.hour in 5..8, "sunrise looked wrong: $sunrise")
    }

    @Test
    fun hanafi_asr_is_later_than_standard() {
        val date = LocalDate(2026, 9, 10)
        val standard = times(date)[Category.Asr]!!
        val hanafi = times(date, settings.copy(madhab = AsrMadhab.Hanafi))[Category.Asr]!!
        assertTrue(hanafi > standard, "Hanafi Asr $hanafi should follow standard $standard")
    }

    @Test
    fun a_different_method_moves_fajr() {
        val date = LocalDate(2026, 9, 10)
        val isna = times(date)[Category.Fajr]!!
        val mwl = times(date, settings.copy(method = PrayerMethod.MuslimWorldLeague))[Category.Fajr]!!
        assertTrue(mwl < isna, "MWL uses a wider angle so Fajr comes earlier: $mwl vs $isna")
    }

    @Test
    fun the_live_window_walks_through_the_day() {
        val t = times(LocalDate(2026, 9, 10))
        assertEquals(Category.BeforeFajr, t.windowAt(LocalTime(2, 0)))
        assertEquals(Category.Fajr, t.windowAt(t[Category.Fajr]!!))
        assertEquals(Category.Shuruq, t.windowAt(t[Category.Shuruq]!!))
        assertEquals(Category.Dhuhr, t.windowAt(t[Category.Dhuhr]!!))
        assertEquals(Category.Asr, t.windowAt(t[Category.Asr]!!))
        assertEquals(Category.Maghrib, t.windowAt(t[Category.Maghrib]!!))
        assertEquals(Category.Isha, t.windowAt(LocalTime(23, 59)))
    }

    @Test
    fun the_moment_before_fajr_is_still_before_fajr() {
        val t = times(LocalDate(2026, 9, 10))
        val fajr = t[Category.Fajr]!!
        val justBefore = LocalTime(fajr.hour, fajr.minute - 1)
        assertEquals(Category.BeforeFajr, t.windowAt(justBefore))
    }

    @Test
    fun an_empty_timetable_reports_before_fajr_rather_than_throwing() {
        assertEquals(Category.BeforeFajr, DayPrayerTimes.Empty.windowAt(LocalTime(12, 0)))
    }
}
