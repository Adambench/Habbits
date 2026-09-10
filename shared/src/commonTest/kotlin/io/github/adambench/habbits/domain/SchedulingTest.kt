package io.github.adambench.habbits.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fixtures are real habits from the vault, so the ported rules are checked
 * against schedules that actually exist rather than invented ones.
 */
class SchedulingTest {

    // 2026-09-07 is a Monday; the week runs Mon 7th … Sun 13th.
    private val monday = LocalDate(2026, 9, 7)
    private val thursday = LocalDate(2026, 9, 10)
    private val friday = LocalDate(2026, 9, 11)
    private val sunday = LocalDate(2026, 9, 13)

    private fun habit(
        id: String = "h",
        frequencyType: FrequencyType = FrequencyType.Daily,
        recurringDays: Weekdays = Weekdays.All,
        intervalDays: Int? = null,
        intervalStart: LocalDate? = null,
        status: HabitStatus = HabitStatus.Active,
    ) = Habit(
        id = id,
        label = id,
        frequencyType = frequencyType,
        recurringDays = recurringDays,
        intervalDays = intervalDays,
        intervalStart = intervalStart,
        status = status,
    )

    @Test
    fun daily_habit_is_due_every_day() {
        val quranMorning = habit("quranMorning")
        listOf(monday, thursday, friday, sunday).forEach {
            assertTrue(quranMorning.isScheduledOn(it), "expected due on $it")
        }
    }

    @Test
    fun weekly_habit_is_due_only_on_its_days() {
        // "Read Kahf" — Fridays only.
        val readKahf = habit(
            id = "readKahf",
            frequencyType = FrequencyType.Weekly,
            recurringDays = Weekdays.of(listOf(5)),
        )
        assertTrue(readKahf.isScheduledOn(friday))
        assertFalse(readKahf.isScheduledOn(monday))
        assertFalse(readKahf.isScheduledOn(thursday))
        assertFalse(readKahf.isScheduledOn(sunday))
    }

    @Test
    fun weekly_habit_supports_multiple_days() {
        // "Sawm" — Mondays and Thursdays.
        val sawm = habit(
            id = "sawm",
            frequencyType = FrequencyType.Weekly,
            recurringDays = Weekdays.of(listOf(1, 4)),
        )
        assertTrue(sawm.isScheduledOn(monday))
        assertTrue(sawm.isScheduledOn(thursday))
        assertFalse(sawm.isScheduledOn(friday))
    }

    @Test
    fun weekly_habit_with_no_days_selected_stays_visible() {
        // Matches the legacy fallback: an empty selection meant every day, so a
        // half-configured habit does not silently disappear.
        val unset = habit(frequencyType = FrequencyType.Weekly, recurringDays = Weekdays.None)
        assertTrue(unset.isScheduledOn(monday))
        assertTrue(unset.isScheduledOn(sunday))
    }

    @Test
    fun interval_habit_is_due_on_multiples_of_the_interval() {
        val everyThreeDays = habit(
            frequencyType = FrequencyType.Interval,
            intervalDays = 3,
            intervalStart = monday,
        )
        assertTrue(everyThreeDays.isScheduledOn(monday))
        assertFalse(everyThreeDays.isScheduledOn(LocalDate(2026, 9, 8)))
        assertFalse(everyThreeDays.isScheduledOn(LocalDate(2026, 9, 9)))
        assertTrue(everyThreeDays.isScheduledOn(LocalDate(2026, 9, 10)))
        assertTrue(everyThreeDays.isScheduledOn(LocalDate(2026, 9, 13)))
    }

    @Test
    fun interval_habit_is_not_due_before_its_start() {
        val fromFriday = habit(
            frequencyType = FrequencyType.Interval,
            intervalDays = 2,
            intervalStart = friday,
        )
        assertFalse(fromFriday.isScheduledOn(monday))
        assertFalse(fromFriday.isScheduledOn(thursday))
        assertTrue(fromFriday.isScheduledOn(friday))
    }

    @Test
    fun interval_habit_without_a_start_date_stays_visible() {
        val noStart = habit(frequencyType = FrequencyType.Interval, intervalDays = 5)
        assertTrue(noStart.isScheduledOn(monday))
    }

    @Test
    fun interval_of_zero_does_not_divide_by_zero() {
        // Legacy coerced a falsy interval to 1, meaning "every day".
        val zero = habit(
            frequencyType = FrequencyType.Interval,
            intervalDays = 0,
            intervalStart = monday,
        )
        assertTrue(zero.isScheduledOn(monday))
        assertTrue(zero.isScheduledOn(thursday))
    }

    @Test
    fun sleeping_and_archived_habits_are_hidden_from_the_day_view() {
        assertFalse(habit(status = HabitStatus.Sleeping).isVisibleOn(monday))
        assertFalse(habit(status = HabitStatus.Archived).isVisibleOn(monday))
        assertTrue(habit(status = HabitStatus.Active).isVisibleOn(monday))
    }

    @Test
    fun sleeping_habit_still_reports_its_schedule() {
        // The editor needs the schedule even while the day view hides the habit.
        val sleeping = habit(
            status = HabitStatus.Sleeping,
            frequencyType = FrequencyType.Weekly,
            recurringDays = Weekdays.of(listOf(5)),
        )
        assertTrue(sleeping.isScheduledOn(friday))
        assertFalse(sleeping.isVisibleOn(friday))
    }

    @Test
    fun category_order_matches_the_run_of_the_day() {
        assertEquals(
            listOf("Anytime", "Before Fajr", "Fajr", "Shuruq", "Dhuhr", "Asr", "Maghrib", "Isha"),
            Category.entries.map { it.storageId },
        )
    }

    @Test
    fun unknown_stored_values_fall_back_instead_of_throwing() {
        assertEquals(Category.Anytime, Category.fromStorageId("Tahajjud Time"))
        assertEquals(HabitType.Other, HabitType.fromStorageId(null))
        assertEquals(HabitStatus.Active, HabitStatus.fromStorageId("unknown"))
        assertEquals(FrequencyType.Daily, FrequencyType.fromStorageId(""))
    }
}
