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
    /**
     * When the habit was active, sleeping or archived, oldest first. Empty only
     * for a habit not yet backfilled, which then reads as always having had
     * [status]. See [StatusHistory].
     */
    val statusHistory: List<StatusChange> = emptyList(),
) {
    val isMeasured: Boolean get() = !unit.isNullOrBlank()

    companion object {
        /** The legacy component's default quick-add step. */
        const val DEFAULT_STEP = 25
    }
}
