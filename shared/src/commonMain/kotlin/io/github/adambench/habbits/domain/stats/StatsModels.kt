package io.github.adambench.habbits.domain.stats

import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.Habit
import kotlinx.datetime.LocalDate

enum class StatsRange(val days: Int?, val label: String) {
    Month(30, "30 days"),
    Quarter(90, "90 days"),
    Year(365, "Year"),
    All(null, "All time"),
}

/** One day. [due] counts only habits actually scheduled that day. */
data class DayStat(val date: LocalDate, val due: Int, val done: Int) {
    val ratio: Float get() = if (due == 0) 0f else done.toFloat() / due
    val isPerfect: Boolean get() = due > 0 && done == due
    val hasActivity: Boolean get() = done > 0
}

/** One day in a single habit's history. */
data class HabitDay(val date: LocalDate, val due: Boolean, val done: Boolean)

data class HabitStat(
    val habit: Habit,
    val due: Int,
    val done: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    /** Sum of logged amounts for a measured habit; null for a plain check. */
    val total: Int?,
    val lastDone: LocalDate?,
    /** Longest run of consecutive *due* days missed. */
    val longestGap: Int = 0,
    /** Due days missed since the last completion. */
    val currentGap: Int = 0,
    /**
     * How often a miss was followed by a completion on the very next due day.
     *
     * Null when the habit was never missed. This separates "intermittent" from
     * "abandoned" — two habits can share a rate and behave nothing alike.
     */
    val recoveryRate: Float? = null,
    val days: List<HabitDay> = emptyList(),
) {
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
    val missed: Int get() = (due - done).coerceAtLeast(0)

    /** Missed for long enough that it reads as dropped, not merely intermittent. */
    val isAbandoned: Boolean get() = currentGap >= ABANDONED_AFTER

    companion object {
        const val ABANDONED_AFTER = 14
    }
}

data class CategoryStat(val category: Category, val due: Int, val done: Int) {
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
}

data class WeekdayStat(val isoDayNumber: Int, val due: Int, val done: Int) {
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
}

data class WeekStat(val start: LocalDate, val due: Int, val done: Int) {
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
}

data class StatsSummary(
    val range: StatsRange,
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DayStat> = emptyList(),
    val habits: List<HabitStat> = emptyList(),
    val categories: List<CategoryStat> = emptyList(),
    val weekdays: List<WeekdayStat> = emptyList(),
    val weeks: List<WeekStat> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val perfectDays: Int = 0,
    /** Every completion in range, including habits excluded from the rates. */
    val totalLogged: Int = 0,
    val excludedHabits: Int = 0,
    val excludedLogged: Int = 0,
    val isLoading: Boolean = true,
) {
    val due: Int get() = days.sumOf { it.due }
    val done: Int get() = days.sumOf { it.done }
    val rate: Float get() = if (due == 0) 0f else done.toFloat() / due
    val activeDays: Int get() = days.count { it.hasActivity }
    val bestHabit: HabitStat? get() = habits.filter { it.due >= MIN_SAMPLE }.maxByOrNull { it.rate }
    val weakestHabit: HabitStat? get() = habits.filter { it.due >= MIN_SAMPLE }.minByOrNull { it.rate }

    companion object {
        /** Below this many due days a rate is noise, not a signal. */
        const val MIN_SAMPLE = 5
    }
}
