package io.github.adambench.habbits.domain

enum class HabitStatus(val storageId: String) {
    /** Shown and schedulable. */
    Active("active"),

    /** Hidden from the day view but kept in the list, editable. */
    Sleeping("sleeping"),

    /**
     * Retired: never shown in the day view, but its history is preserved. The
     * 24 orphan habit IDs from the vault land here on import.
     */
    Archived("archived"),
    ;

    companion object {
        private val byStorageId = entries.associateBy { it.storageId }

        fun fromStorageId(id: String?): HabitStatus = byStorageId[id] ?: Active
    }
}
