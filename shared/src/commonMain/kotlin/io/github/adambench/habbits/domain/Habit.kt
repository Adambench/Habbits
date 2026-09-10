package io.github.adambench.habbits.domain

import kotlinx.datetime.LocalDate

/** A habit definition. Logged values live separately, in `entries`. */
data class Habit(
    val id: String,
    val label: String,
    val description: String? = null,
    val category: Category = Category.Anytime,
    val type: HabitType = HabitType.Other,
    /** Present when the habit is measured rather than a plain check, e.g. "Raka'ats". */
    val unit: String? = null,
    val defaultValue: Int? = null,
    val step: Int = DEFAULT_STEP,
    val frequencyType: FrequencyType = FrequencyType.Daily,
    val recurringDays: Weekdays = Weekdays.All,
    val intervalDays: Int? = null,
    val intervalStart: LocalDate? = null,
    val status: HabitStatus = HabitStatus.Active,
    val sortOrder: Int = 0,
) {
    val isMeasured: Boolean get() = !unit.isNullOrBlank()

    companion object {
        /** The legacy component's default quick-add step. */
        const val DEFAULT_STEP = 25
    }
}
