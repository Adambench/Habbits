package dev.adambench.habbits.data

/**
 * One day's completions, keyed by habit id.
 *
 * A key being present means completed; the value is null for a plain check and
 * a number for a measured habit.
 */
data class DayLog(private val values: Map<String, Int?>) {

    val completedCount: Int get() = values.size

    fun isCompleted(habitId: String): Boolean = values.containsKey(habitId)

    /** The logged amount, or 0 when the habit is not completed or is a plain check. */
    fun valueOf(habitId: String): Int = values[habitId] ?: 0

    companion object {
        val Empty = DayLog(emptyMap())

        fun from(entries: List<EntryEntity>): DayLog =
            DayLog(entries.associate { it.habitId to it.value })
    }
}
