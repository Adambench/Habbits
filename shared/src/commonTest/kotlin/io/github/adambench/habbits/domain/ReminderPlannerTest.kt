package io.github.adambench.habbits.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderPlannerTest {

    private val today = LocalDate(2026, 9, 10)
    private val tomorrow = LocalDate(2026, 9, 11)

    /** Tomorrow runs a few minutes later, as real prayer times do. */
    private fun times(date: LocalDate) = DayPrayerTimes(
        date = date,
        times = mapOf(
            Category.Fajr to LocalTime(5, 2).plusMinutes(if (date == tomorrow) 2 else 0),
            Category.Shuruq to LocalTime(6, 27),
            Category.Dhuhr to LocalTime(12, 52),
            Category.Asr to LocalTime(16, 24),
            Category.Maghrib to LocalTime(19, 15).plusMinutes(if (date == tomorrow) -2 else 0),
            Category.Isha to LocalTime(20, 39),
        ),
    )

    private fun LocalTime.plusMinutes(n: Int): LocalTime {
        val total = hour * 60 + minute + n
        return LocalTime(total / 60, total % 60)
    }

    private fun settings(vararg windows: WindowReminder) =
        ReminderSettings(enabled = true, windows = windows.toList())

    @Test
    fun a_reminder_fires_at_its_offset_from_the_window() {
        val s = settings(WindowReminder(Category.Maghrib, enabled = true, offsetMinutes = 20))
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(12, 0)), ::times)!!
        assertEquals(LocalDateTime(today, LocalTime(19, 35)), next.at, "Maghrib 19:15 plus 20")
    }

    @Test
    fun a_negative_offset_fires_before_the_window() {
        val s = settings(WindowReminder(Category.Fajr, enabled = true, offsetMinutes = -15))
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(2, 0)), ::times)!!
        assertEquals(LocalTime(4, 47), next.at.time, "Fajr 05:02 minus 15")
    }

    @Test
    fun a_window_that_has_passed_rolls_to_tomorrows_time_not_todays() {
        val s = settings(WindowReminder(Category.Maghrib, enabled = true, offsetMinutes = 0))
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(21, 0)), ::times)!!
        assertEquals(tomorrow, next.at.date)
        assertEquals(
            LocalTime(19, 13),
            next.at.time,
            "tomorrow's Maghrib, not today's — they differ every day",
        )
    }

    @Test
    fun windows_with_no_prayer_time_use_their_fallback_clock_time() {
        val s = settings(
            WindowReminder(Category.Anytime, enabled = true, fallbackHour = 9, fallbackMinute = 30),
        )
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(6, 0)), ::times)!!
        assertEquals(LocalTime(9, 30), next.at.time)
    }

    @Test
    fun before_fajr_also_falls_back_rather_than_being_dropped() {
        val s = settings(
            WindowReminder(Category.BeforeFajr, enabled = true, fallbackHour = 4, fallbackMinute = 0),
        )
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(1, 0)), ::times)!!
        assertEquals(LocalTime(4, 0), next.at.time)
    }

    @Test
    fun the_soonest_reminder_is_the_one_returned() {
        val s = settings(
            WindowReminder(Category.Isha, enabled = true, offsetMinutes = 0),
            WindowReminder(Category.Dhuhr, enabled = true, offsetMinutes = 0),
            WindowReminder(Category.Maghrib, enabled = true, offsetMinutes = 0),
        )
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(6, 0)), ::times)!!
        assertEquals(Category.Dhuhr, next.category)
    }

    @Test
    fun disabled_windows_and_the_master_switch_are_both_respected() {
        val onlyDisabled = settings(WindowReminder(Category.Fajr, enabled = false))
        assertNull(ReminderPlanner.next(onlyDisabled, LocalDateTime(today, LocalTime(1, 0)), ::times))

        val masterOff = ReminderSettings(
            enabled = false,
            windows = listOf(WindowReminder(Category.Fajr, enabled = true)),
        )
        assertNull(ReminderPlanner.next(masterOff, LocalDateTime(today, LocalTime(1, 0)), ::times))
    }

    @Test
    fun an_offset_is_clamped_inside_the_day_rather_than_wrapping() {
        // Isha 20:39 plus eight hours would land at 04:39 the next morning,
        // which belongs to a different window entirely.
        val s = settings(WindowReminder(Category.Isha, enabled = true, offsetMinutes = 8 * 60))
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(6, 0)), ::times)!!
        assertEquals(today, next.at.date)
        assertEquals(LocalTime(23, 59), next.at.time)
    }

    @Test
    fun the_full_plan_is_ordered_and_covers_every_enabled_window() {
        val s = settings(
            WindowReminder(Category.Fajr, enabled = true, offsetMinutes = 0),
            WindowReminder(Category.Dhuhr, enabled = true, offsetMinutes = 0),
            WindowReminder(Category.Isha, enabled = true, offsetMinutes = 0),
        )
        val plan = ReminderPlanner.plan(s, LocalDateTime(today, LocalTime(6, 0)), ::times)
        assertEquals(3, plan.size)
        assertEquals(plan.sortedBy { it.at }, plan, "the plan must be in firing order")
        assertTrue(plan.first().category == Category.Dhuhr, "Fajr has already passed at 06:00")
    }

    @Test
    fun a_reminder_exactly_now_rolls_forward_instead_of_firing_late() {
        val s = settings(WindowReminder(Category.Dhuhr, enabled = true, offsetMinutes = 0))
        val next = ReminderPlanner.next(s, LocalDateTime(today, LocalTime(12, 52)), ::times)!!
        assertEquals(tomorrow, next.at.date)
    }
}
