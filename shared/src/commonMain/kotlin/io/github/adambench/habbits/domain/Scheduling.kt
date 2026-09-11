package io.github.adambench.habbits.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * Whether this habit is due on [date], by its frequency rule alone.
 *
 * Ported from the legacy component's `visibleHabits` filter, including its
 * lenient fallbacks: an interval habit with no start date, or a weekly habit
 * with no days chosen, stays visible rather than silently vanishing.
 */
fun Habit.isScheduledOn(date: LocalDate): Boolean = when (frequencyType) {
    FrequencyType.Daily -> true

    FrequencyType.Weekly ->
        if (recurringDays.isEmpty) true else date.dayOfWeek.isoDayNumber in recurringDays

    FrequencyType.Interval -> {
        val start = intervalStart
        if (start == null) {
            true
        } else {
            // `intervalDays` of 0 or null means every day, matching the legacy
            // `h.intervalDays || 1`, and keeps the modulo below well-defined.
            val interval = intervalDays?.takeIf { it > 0 } ?: 1
            val elapsed = date.toEpochDays() - start.toEpochDays()
            elapsed >= 0 && elapsed % interval == 0L
        }
    }
}

/**
 * Whether this habit should appear in the day view for [date].
 *
 * Sleeping and archived habits are hidden regardless of schedule; the editor
 * shows them through [isScheduledOn] plus its own status filter.
 */
fun Habit.isVisibleOn(date: LocalDate): Boolean =
    status == HabitStatus.Active && isScheduledOn(date)