package dev.adambench.habbits.ui.manage

import dev.adambench.habbits.data.HabitRepository
import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.Habit
import dev.adambench.habbits.domain.HabitStatus
import dev.adambench.habbits.domain.movedBy
import dev.adambench.habbits.domain.movedTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State for habit management: create, edit, reorder, sleep, archive, delete. */
class ManageScreenModel(
    private val repository: HabitRepository,
    private val scope: CoroutineScope,
    private val newId: () -> String,
) {
    private val editing = MutableStateFlow<Habit?>(null)
    private val isNew = MutableStateFlow(false)
    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<ManageUiState> =
        combine(
            repository.observeHabits(),
            editing,
            isNew,
            showArchived,
        ) { habits, edit, fresh, archived ->
            val visible = habits.filter { archived || it.status != HabitStatus.Archived }
            ManageUiState(
                sections = Category.entries.mapNotNull { category ->
                    val group = visible.filter { it.category == category }
                    if (group.isEmpty()) null else ManageSection(category, group)
                },
                editing = edit,
                isNew = fresh,
                showArchived = archived,
                archivedCount = habits.count { it.status == HabitStatus.Archived },
                isLoading = false,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ManageUiState(),
        )

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    fun edit(habit: Habit) {
        isNew.value = false
        editing.value = habit
    }

    fun createNew() {
        isNew.value = true
        editing.value = Habit(id = newId(), label = "")
    }

    fun updateDraft(habit: Habit) {
        editing.value = habit
    }

    fun cancelEdit() {
        editing.value = null
        isNew.value = false
    }

    fun save() {
        val habit = editing.value ?: return
        if (habit.label.isBlank()) return
        scope.launch {
            repository.saveHabit(habit)
            cancelEdit()
        }
    }

    fun delete(id: String) {
        scope.launch {
            repository.deleteHabit(id)
            cancelEdit()
        }
    }

    /** Sleeping hides a habit from the day view but keeps it in this list. */
    fun toggleSleep(habit: Habit) {
        val next = if (habit.status == HabitStatus.Sleeping) {
            HabitStatus.Active
        } else {
            HabitStatus.Sleeping
        }
        scope.launch { repository.setStatus(habit.id, next) }
    }

    fun setStatus(habit: Habit, status: HabitStatus) {
        scope.launch { repository.setStatus(habit.id, status) }
    }

    /** Drop [habitId] where [targetId] currently sits. */
    fun moveTo(habitId: String, targetId: String) {
        scope.launch {
            val current = repository.getHabits()
            val reordered = current.movedTo(habitId, targetId)
            if (reordered !== current) repository.applyOrder(reordered)
        }
    }

    fun move(habitId: String, direction: Int) {
        scope.launch {
            val current = repository.getHabits()
            val reordered = current.movedBy(habitId, direction)
            if (reordered !== current) repository.applyOrder(reordered)
        }
    }
}
