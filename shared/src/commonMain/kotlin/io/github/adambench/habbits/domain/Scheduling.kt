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

/**
 * The status this habit had on [date], or null if it did not exist yet.
 *
 * Without a recorded history the current status is all there is to go on.
 */
fun Habit.statusOn(date: LocalDate): HabitStatus? {
    if (statusHistory.isEmpty()) return status
    var result: HabitStatus? = null
    for (change in statusHistory) {
        if (change.date <= date) result = change.status else break
    }
    return result
}

/**
 * Whether [date] counts for or against this habit: scheduled, and active at
 * the time rather than merely active now.
 *
 * A day before the habit's first recorded day does not count, unless it was
 * [completed] — that is proof it existed, as when a new habit is logged for the
 * day before it was added.
 *
 * Today and later follow the current status, so waking or sleeping a habit
 * shows up at once whatever the history says.
 */
fun Habit.countsOn(date: LocalDate, today: LocalDate, completed: Boolean): Boolean {
    if (date >= today) return isVisibleOn(date)
    val status = statusOn(date) ?: if (completed) HabitStatus.Active else return false
    return status == HabitStatus.Active && isScheduledOn(date)
}

/**
 * Whether the day view lists this habit on [date].
 *
 * Like [countsOn], except that days before the habit existed still show it, so
 * a habit added today can be logged for yesterday.
 */
fun Habit.isShownOn(date: LocalDate, today: LocalDate): Boolean {
    if (date >= today) return isVisibleOn(date)
    val status = statusOn(date) ?: statusHistory.firstOrNull()?.status ?: this.status
    return status == HabitStatus.Active && isScheduledOn(date)
}
