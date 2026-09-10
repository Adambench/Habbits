package dev.adambench.habbits.ui.manage

import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.Habit

data class ManageSection(
    val category: Category,
    val habits: List<Habit>,
)

data class ManageUiState(
    val sections: List<ManageSection> = emptyList(),
    val editing: Habit? = null,
    /** True when [editing] is not yet in the database. */
    val isNew: Boolean = false,
    val showArchived: Boolean = false,
    val archivedCount: Int = 0,
    val isLoading: Boolean = true,
) {
    val total: Int get() = sections.sumOf { it.habits.size }
}
