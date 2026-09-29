package io.github.adambench.habbits.ui.manage

import io.github.adambench.habbits.domain.FrequencyType
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.Weekdays
import io.github.adambench.habbits.ui.headerLabel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/*
 * Habits described in words rather than codes, so the list and the editor can
 * say what a habit does instead of leaving the reader to decode its fields.
 */

private val DAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val WORKDAYS = Weekdays.of(1..5)
private val WEEKEND = Weekdays.of(6..7)

/** The interval as the scheduler reads it: 0 or unset means every day. */
internal val Habit.effectiveInterval: Int get() = intervalDays?.takeIf { it > 0 } ?: 1

/** e.g. "Every day", "Weekdays", "Mon, Wed & Fri", "Every 3 days". */
fun Habit.scheduleText(): String = when (frequencyType) {
    FrequencyType.Daily -> "Every day"
    FrequencyType.Weekly -> recurringDays.describe()
    FrequencyType.Interval -> when (effectiveInterval) {
        1 -> "Every day"
        2 -> "Every other day"
        else -> "Every $effectiveInterval days"
    }
}

/** e.g. "Tick off", "Counts pages". */
fun Habit.trackingText(): String =
    if (isMeasured) "Counts ${unit.orEmpty().trim()}" else "Tick off"

/**
 * One sentence saying where and when the habit appears and what a tap does,
 * shown at the top of the editor so every change is read back as it is made.
 */
fun Habit.sentence(today: LocalDate): String = buildString {
    append("Shows under ${category.displayName}, ")
    append(
        when (frequencyType) {
            FrequencyType.Daily -> "every day"
            FrequencyType.Weekly ->
                if (recurringDays.isEmpty || recurringDays == Weekdays.All) {
                    "every day"
                } else {
                    "on ${recurringDays.describe(long = true)}"
                }
            FrequencyType.Interval -> {
                val next = nextDue(today)
                val every = scheduleText().replaceFirstChar { it.lowercase() }
                if (next == null) every else "$every, next on ${next.headerLabel()}"
            }
        },
    )
    append(". ")
    if (isMeasured) {
        val u = unit.orEmpty().trim()
        val logged = defaultValue
        append(
            if (logged != null && logged > 0) {
                "Ticking it logs $logged $u; the buttons add or take away $step."
            } else {
                "Log how many $u with buttons of $step."
            },
        )
    } else {
        append("Tap it once to mark it done.")
    }
}

/**
 * The first day on or after [today] an interval habit falls due, or null when
 * the habit is not on an interval or has no start to count from.
 */
fun Habit.nextDue(today: LocalDate): LocalDate? {
    if (frequencyType != FrequencyType.Interval) return null
    val start = intervalStart ?: return null
    if (start >= today) return start
    val interval = effectiveInterval
    val elapsed = (today.toEpochDays() - start.toEpochDays()).toInt()
    val ahead = (interval - elapsed % interval) % interval
    return today.plus(DatePeriod(days = ahead))
}

private fun Weekdays.describe(long: Boolean = false): String = when {
    isEmpty || this == Weekdays.All -> if (long) "every day" else "Every day"
    this == WORKDAYS -> if (long) "weekdays" else "Weekdays"
    this == WEEKEND -> if (long) "weekends" else "Weekends"
    else -> {
        val names = toIsoDayNumbers().map { DAY_NAMES[it - 1] }
        if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " & " + names.last()
    }
}
