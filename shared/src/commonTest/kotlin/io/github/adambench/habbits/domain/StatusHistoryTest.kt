package io.github.adambench.habbits.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusHistoryTest {

    private val today = LocalDate(2026, 9, 28)
    private fun day(m: Int, d: Int) = LocalDate(2026, m, d)
    private fun change(m: Int, d: Int, status: HabitStatus) = StatusChange(day(m, d), status)

    @Test
    fun a_change_is_appended_from_its_date() {
        val history = StatusHistory.withChange(
            listOf(change(1, 5, HabitStatus.Active)), day(4, 1), HabitStatus.Sleeping,
        )
        assertEquals(listOf(change(1, 5, HabitStatus.Active), change(4, 1, HabitStatus.Sleeping)), history)
    }

    @Test
    fun sleeping_and_waking_on_the_same_day_leaves_no_trace() {
        val start = listOf(change(1, 5, HabitStatus.Active))
        val slept = StatusHistory.withChange(start, today, HabitStatus.Sleeping)
        val woken = StatusHistory.withChange(slept, today, HabitStatus.Active)
        assertEquals(start, woken, "a same-day round trip is not a change")
    }

    @Test
    fun a_change_to_the_current_status_is_not_recorded() {
        val start = listOf(change(1, 5, HabitStatus.Active))
        assertEquals(start, StatusHistory.withChange(start, today, HabitStatus.Active))
    }

    @Test
    fun an_active_habit_is_inferred_to_exist_from_its_first_completion() {
        val history = StatusHistory.infer(HabitStatus.Active, day(3, 25), day(8, 28), created = day(9, 10), today)
        assertEquals(listOf(change(3, 25, HabitStatus.Active)), history)
    }

    @Test
    fun an_earlier_creation_date_wins_over_a_later_first_completion() {
        val history = StatusHistory.infer(HabitStatus.Active, day(3, 25), day(8, 28), created = day(3, 1), today)
        assertEquals(listOf(change(3, 1, HabitStatus.Active)), history)
    }

    @Test
    fun a_sleeping_habit_is_inferred_active_until_the_day_after_it_was_last_done() {
        val history = StatusHistory.infer(HabitStatus.Sleeping, day(4, 5), day(5, 31), created = null, today)
        assertEquals(
            listOf(change(4, 5, HabitStatus.Active), change(6, 1, HabitStatus.Sleeping)),
            history,
        )
    }

    @Test
    fun a_sleeping_habit_last_done_today_is_asleep_today_not_tomorrow() {
        val history = StatusHistory.infer(HabitStatus.Sleeping, day(9, 1), today, created = null, today)
        assertEquals(HabitStatus.Sleeping, history.last().status)
        assertEquals(today, history.last().date, "a status change is never dated in the future")
    }

    @Test
    fun a_sleeping_habit_never_done_was_never_active() {
        val history = StatusHistory.infer(HabitStatus.Sleeping, null, null, created = day(4, 1), today)
        assertEquals(listOf(change(4, 1, HabitStatus.Sleeping)), history)
    }

    @Test
    fun an_archived_orphan_stays_archived_throughout() {
        // Its schedule was lost with the vault config, so no day can honestly be
        // called due.
        val history = StatusHistory.infer(HabitStatus.Archived, day(1, 7), day(3, 20), created = null, today)
        assertEquals(listOf(change(1, 7, HabitStatus.Archived)), history)
    }

    @Test
    fun encoding_round_trips() {
        val history = listOf(change(4, 5, HabitStatus.Active), change(6, 1, HabitStatus.Sleeping))
        val text = StatusHistory.encode(history)
        assertEquals("2026-04-05:active,2026-06-01:sleeping", text)
        assertEquals(history, StatusHistory.decode(text))
    }

    @Test
    fun malformed_parts_are_dropped_and_the_rest_kept_in_order() {
        val decoded = StatusHistory.decode("2026-06-01:sleeping,garbage,2026-04-05:active,2026-13-01:active")
        assertEquals(listOf(change(4, 5, HabitStatus.Active), change(6, 1, HabitStatus.Sleeping)), decoded)
    }

    @Test
    fun status_on_a_date_follows_the_history() {
        val habit = Habit(
            id = "h", label = "H", status = HabitStatus.Active,
            statusHistory = listOf(
                change(4, 5, HabitStatus.Active),
                change(6, 1, HabitStatus.Sleeping),
                change(9, 1, HabitStatus.Active),
            ),
        )
        assertNull(habit.statusOn(day(4, 4)), "before it existed")
        assertEquals(HabitStatus.Active, habit.statusOn(day(4, 5)))
        assertEquals(HabitStatus.Sleeping, habit.statusOn(day(7, 15)))
        assertEquals(HabitStatus.Active, habit.statusOn(day(9, 1)))
    }

    @Test
    fun without_a_history_the_current_status_is_all_there_is() {
        val habit = Habit(id = "h", label = "H", status = HabitStatus.Sleeping)
        assertEquals(HabitStatus.Sleeping, habit.statusOn(day(1, 1)))
    }

    @Test
    fun a_day_before_a_habit_existed_counts_only_if_it_was_done() {
        val habit = Habit(id = "h", label = "H", statusHistory = listOf(StatusChange(today, HabitStatus.Active)))
        val yesterday = day(9, 27)
        assertFalse(habit.countsOn(yesterday, today, completed = false), "not missed before it existed")
        assertTrue(habit.countsOn(yesterday, today, completed = true), "a logged day proves it existed")
        assertTrue(habit.isShownOn(yesterday, today), "still listed, so it can be logged")
    }

    @Test
    fun a_past_day_asleep_neither_counts_nor_shows() {
        val habit = Habit(
            id = "h", label = "H",
            statusHistory = listOf(change(4, 5, HabitStatus.Active), change(6, 1, HabitStatus.Sleeping), change(9, 1, HabitStatus.Active)),
        )
        assertFalse(habit.countsOn(day(7, 1), today, completed = false))
        assertFalse(habit.isShownOn(day(7, 1), today))
        assertTrue(habit.isShownOn(day(5, 1), today))
    }

    @Test
    fun a_habit_asleep_now_still_shows_on_the_days_it_was_active() {
        val habit = Habit(
            id = "h", label = "H", status = HabitStatus.Sleeping,
            statusHistory = listOf(change(4, 5, HabitStatus.Active), change(6, 1, HabitStatus.Sleeping)),
        )
        assertTrue(habit.isShownOn(day(5, 1), today), "its past completions stay visible")
        assertFalse(habit.isShownOn(today, today), "but not today")
    }
}
