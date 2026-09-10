package dev.adambench.habbits.ui

import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.DayPrayerTimes
import dev.adambench.habbits.domain.Habit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** One habit as the day view needs it: definition plus today's state. */
data class HabitRow(
    val habit: Habit,
    val isCompleted: Boolean,
    val value: Int,
)

/** A prayer slot and the habits due in it. Empty slots are not emitted. */
data class CategorySection(
    val category: Category,
    val rows: List<HabitRow>,
    /** The computed start of this window, or null when times are off. */
    val startsAt: LocalTime? = null,
    /** True for the window that is live right now, only when viewing today. */
    val isLive: Boolean = false,
) {
    val completed: Int get() = rows.count { it.isCompleted }
}

/** A day in the week strip. */
data class DayChip(
    val date: LocalDate,
    val completed: Int,
    val total: Int,
    val isSelected: Boolean,
    val isToday: Boolean,
) {
    val ratio: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

data class DayUiState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val sections: List<CategorySection> = emptyList(),
    val week: List<DayChip> = emptyList(),
    val isLoading: Boolean = true,
    /** False only when the database holds no habits at all, not merely none due today. */
    val hasAnyHabits: Boolean = false,
    val prayerTimes: DayPrayerTimes = DayPrayerTimes.Empty,
    /** The live prayer window, or null when not viewing today or times are off. */
    val liveCategory: Category? = null,
    val autoScroll: Boolean = true,
) {
    val completed: Int get() = sections.sumOf { it.completed }
    val total: Int get() = sections.sumOf { it.rows.size }
    val ratio: Float get() = if (total == 0) 0f else completed.toFloat() / total
    val isToday: Boolean get() = selectedDate == today
}
