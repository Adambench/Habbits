package io.github.adambench.habbits.domain.stats

import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.FrequencyType
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.Weekdays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsCalculatorTest {

    // 2026-09-07 Mon … 2026-09-13 Sun. "Today" is Sunday the 13th.
    private val today = LocalDate(2026, 9, 13)
    private fun day(d: Int) = LocalDate(2026, 9, d)

    private val daily = Habit(id = "daily", label = "Adkar", category = Category.Fajr)
    private val fridays = Habit(
        id = "kahf", label = "Read Kahf", category = Category.Anytime,
        frequencyType = FrequencyType.Weekly, recurringDays = Weekdays.of(listOf(5)),
    )
    private val measured = Habit(
        id = "quran", label = "Quran", category = Category.Anytime,
        unit = "verses", defaultValue = 100, step = 50,
    )

    private fun compute(
        habits: List<Habit>,
        completions: List<Completion>,
        range: StatsRange = StatsRange.Month,
    ) = StatsCalculator.compute(habits, completions, range, today, earliest = day(7))

    @Test
    fun a_weekly_habit_is_only_due_on_its_own_days() {
        val s = compute(listOf(fridays), listOf(Completion(day(11), "kahf", null)))
        val stat = s.habits.single()
        assertEquals(1, stat.due, "one Friday in Mon 7th to Sun 13th")
        assertEquals(1, stat.done)
        assertEquals(1f, stat.rate, "a Friday-only habit must not be marked missed on Tuesday")
    }

    @Test
    fun rate_divides_by_days_due_not_by_calendar_days() {
        val s = compute(listOf(daily, fridays), listOf(Completion(day(11), "kahf", null)))
        val kahf = s.habits.first { it.habit.id == "kahf" }
        val adkar = s.habits.first { it.habit.id == "daily" }
        assertEquals(1, kahf.due)
        assertEquals(7, adkar.due, "a daily habit is due every day in the window")
        assertEquals(0, adkar.done)
    }

    @Test
    fun current_streak_counts_back_over_due_days() {
        val done = listOf(11, 12, 13).map { Completion(day(it), "daily", null) }
        val s = compute(listOf(daily), done)
        assertEquals(3, s.habits.single().currentStreak)
    }

    @Test
    fun an_unfinished_today_does_not_break_the_streak() {
        // Done through yesterday; today is still open.
        val done = listOf(10, 11, 12).map { Completion(day(it), "daily", null) }
        val s = compute(listOf(daily), done)
        assertEquals(3, s.habits.single().currentStreak, "the day is not over yet")
    }

    @Test
    fun a_missed_day_does_break_the_streak() {
        val done = listOf(9, 10, 12).map { Completion(day(it), "daily", null) }
        val s = compute(listOf(daily), done)
        val stat = s.habits.single()
        assertEquals(1, stat.currentStreak, "the 11th was missed")
        assertEquals(2, stat.longestStreak, "the 9th and 10th")
    }

    @Test
    fun longest_streak_survives_a_later_break() {
        val done = listOf(7, 8, 9, 10, 12).map { Completion(day(it), "daily", null) }
        assertEquals(4, compute(listOf(daily), done).habits.single().longestStreak)
    }

    @Test
    fun measured_habits_total_their_amounts() {
        val done = listOf(
            Completion(day(11), "quran", 100),
            Completion(day(12), "quran", 250),
        )
        val stat = compute(listOf(measured), done).habits.single()
        assertEquals(350, stat.total)
    }

    @Test
    fun plain_checks_have_no_total() {
        val s = compute(listOf(daily), listOf(Completion(day(11), "daily", null)))
        assertNull(s.habits.single().total, "a plain check has no amount to sum")
    }

    @Test
    fun sleeping_and_archived_habits_are_excluded_but_reported() {
        val retired = daily.copy(id = "old", label = "Retired", status = HabitStatus.Archived)
        val s = compute(
            listOf(daily, retired),
            listOf(Completion(day(11), "old", null), Completion(day(11), "daily", null)),
        )
        assertEquals(1, s.habits.size, "a retired habit is not counted as missed every day")
        assertEquals(1, s.excludedHabits)
        assertEquals(1, s.excludedLogged, "its history is still reported, not hidden")
        assertEquals(2, s.totalLogged)
    }

    @Test
    fun perfect_days_need_every_due_habit_done() {
        val s = compute(
            listOf(daily, fridays),
            listOf(Completion(day(11), "daily", null), Completion(day(11), "kahf", null)),
        )
        assertEquals(1, s.perfectDays, "only the 11th had both due habits done")
        val friday = s.days.first { it.date == day(11) }
        assertTrue(friday.isPerfect)
        assertEquals(2, friday.due)
    }

    @Test
    fun a_day_with_nothing_due_is_not_perfect() {
        val s = compute(listOf(fridays), listOf(Completion(day(11), "kahf", null)))
        val tuesday = s.days.first { it.date == day(8) }
        assertEquals(0, tuesday.due)
        assertTrue(!tuesday.isPerfect, "zero due is not a perfect day")
        assertEquals(1, s.perfectDays)
    }

    @Test
    fun weekday_breakdown_lands_on_the_right_days() {
        val s = compute(listOf(fridays), listOf(Completion(day(11), "kahf", null)))
        val friday = s.weekdays.first { it.isoDayNumber == 5 }
        val monday = s.weekdays.first { it.isoDayNumber == 1 }
        assertEquals(1, friday.due)
        assertEquals(1, friday.done)
        assertEquals(0, monday.due)
    }

    @Test
    fun categories_aggregate_their_members() {
        val s = compute(
            listOf(daily, fridays, measured),
            listOf(Completion(day(11), "daily", null)),
        )
        val fajr = s.categories.first { it.category == Category.Fajr }
        assertEquals(7, fajr.due)
        assertEquals(1, fajr.done)
    }

    @Test
    fun weeks_are_anchored_to_monday() {
        val s = compute(listOf(daily), emptyList())
        assertEquals(listOf(day(7)), s.weeks.map { it.start })
        assertEquals(7, s.weeks.single().due)
    }

    @Test
    fun overall_rate_and_streaks_hold_together() {
        val done = listOf(9, 10, 11, 12, 13).map { Completion(day(it), "daily", null) }
        val s = compute(listOf(daily), done)
        assertEquals(7, s.due)
        assertEquals(5, s.done)
        assertEquals(5, s.currentStreak)
        assertEquals(5, s.activeDays)
    }

    @Test
    fun an_empty_database_produces_zeroes_rather_than_dividing_by_zero() {
        val s = compute(emptyList(), emptyList())
        assertEquals(0f, s.rate)
        assertEquals(0, s.currentStreak)
        assertEquals(0, s.perfectDays)
        assertTrue(s.habits.isEmpty())
    }
}

/** Gap and recovery metrics: what separates "intermittent" from "abandoned". */
class GapMetricsTest {

    private val today = LocalDate(2026, 1, 20)
    private fun day(d: Int) = LocalDate(2026, 1, d)
    private val daily = Habit(id = "d", label = "Daily")

    private fun compute(done: List<Int>) = StatsCalculator.compute(
        habits = listOf(daily),
        completions = done.map { Completion(day(it), "d", null) },
        range = StatsRange.All,
        today = today,
        earliest = day(1),
    ).habits.single()

    @Test
    fun longest_gap_finds_the_worst_run_of_misses() {
        // done 1,2 … missed 3-9 (7) … done 10 … missed 11-14 (4) … done 15-20
        val stat = compute(listOf(1, 2, 10, 15, 16, 17, 18, 19, 20))
        assertEquals(7, stat.longestGap)
    }

    @Test
    fun current_gap_counts_misses_since_the_last_completion() {
        val stat = compute(listOf(1, 2, 3))
        assertEquals(16, stat.currentGap, "the 4th to the 19th; today is not counted")
    }

    @Test
    fun current_gap_is_zero_while_up_to_date() {
        assertEquals(0, compute((1..20).toList()).currentGap)
    }

    @Test
    fun an_unfinished_today_does_not_open_a_gap() {
        val stat = compute((1..19).toList())
        assertEquals(0, stat.currentGap, "the day is not over")
    }

    @Test
    fun two_habits_with_the_same_rate_are_told_apart_by_their_gaps() {
        // Both 50%: one alternates, the other ran then stopped.
        val alternating = compute((1..20).filter { it % 2 == 1 })
        val abandoned = compute((1..10).toList())
        assertEquals(alternating.done, abandoned.done, "same number of completions")
        assertEquals(1, alternating.longestGap)
        assertEquals(9, abandoned.longestGap, "the same rate, a completely different story")
        assertEquals(9, abandoned.currentGap)
    }

    @Test
    fun recovery_rate_measures_bouncing_back_the_next_due_day() {
        // Missed the 2nd, 4th and 5th. Came back on the 3rd and the 6th, but
        // the 4th was followed by another miss.
        val stat = compute(listOf(1, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20))
        assertEquals(2, stat.longestGap)
        assertEquals(2f / 3f, stat.recoveryRate)
    }

    @Test
    fun a_long_absence_reads_as_abandoned() {
        val dropped = compute(listOf(1, 2, 3))
        assertEquals(16, dropped.currentGap)
        assertTrue(dropped.isAbandoned)
        assertTrue(!compute((1..20).filter { it % 2 == 1 }).isAbandoned, "alternating is not abandoned")
    }

    @Test
    fun a_habit_never_missed_has_no_recovery_rate() {
        assertNull(compute((1..20).toList()).recoveryRate, "nothing to recover from")
    }

    @Test
    fun per_habit_days_cover_the_whole_window_for_the_heatmap() {
        val stat = compute(listOf(5))
        assertEquals(20, stat.days.size)
        assertTrue(stat.days.all { it.due }, "a daily habit is due every day")
        assertEquals(1, stat.days.count { it.done })
    }
}
