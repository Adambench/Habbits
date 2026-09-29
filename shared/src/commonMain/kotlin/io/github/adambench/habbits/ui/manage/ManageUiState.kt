package io.github.adambench.habbits.ui.manage

import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.Habit

data class ManageSection(
    val category: Category,
    val habits: List<Habit>,
)

data class ManageUiState(
    val sections: List<ManageSection> = emptyList(),
    val editing: Habit? = null,
    /** True when [editing] is not yet in the database. */
    val isNew: Boolean = false,
    /** Retired habits, listed apart so they cannot be mistaken for live ones. */
    val archived: List<Habit> = emptyList(),
    val showArchived: Boolean = false,
    val isLoading: Boolean = true,
) {
    val total: Int get() = sections.sumOf { it.habits.size }
}
