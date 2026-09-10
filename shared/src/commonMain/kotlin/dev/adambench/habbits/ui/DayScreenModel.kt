package dev.adambench.habbits.ui

import dev.adambench.habbits.data.DayLog
import dev.adambench.habbits.data.HabitRepository
import dev.adambench.habbits.domain.Category
import dev.adambench.habbits.domain.Habit
import dev.adambench.habbits.domain.isVisibleOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * State for the day view.
 *
 * A plain class rather than an AndroidX ViewModel, so the same instance type
 * serves the Android activity and the desktop window.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayScreenModel(
    private val repository: HabitRepository,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate,
) {
    private val selectedDate = MutableStateFlow(today())

    val state: StateFlow<DayUiState> = selectedDate
        .flatMapLatest { date ->
            val week = weekOf(date)
            combine(
                repository.observeActiveHabits(),
                repository.observeDay(date),
                repository.observeRange(week.first(), week.last()),
            ) { habits, log, rangeLogs ->
                buildState(date, habits, log, rangeLogs)
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DayUiState(selectedDate.value, today()),
        )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun shiftDay(days: Int) {
        selectedDate.value = selectedDate.value.plus(DatePeriod(days = days))
    }

    fun goToToday() {
        selectedDate.value = today()
    }

    fun toggle(habitId: String) {
        scope.launch { repository.toggle(selectedDate.value, habitId) }
    }

    fun step(habitId: String, amount: Int) {
        scope.launch { repository.incrementValue(selectedDate.value, habitId, amount) }
    }

    private fun buildState(
        date: LocalDate,
        habits: List<Habit>,
        log: DayLog,
        rangeLogs: Map<LocalDate, DayLog>,
    ): DayUiState {
        val due = habits.filter { it.isVisibleOn(date) }
        val sections = Category.entries.mapNotNull { category ->
            val rows = due.filter { it.category == category }
                .map { habit ->
                    HabitRow(
                        habit = habit,
                        isCompleted = log.isCompleted(habit.id),
                        value = log.valueOf(habit.id),
                    )
                }
            if (rows.isEmpty()) null else CategorySection(category, rows)
        }

        val now = today()
        val week = weekOf(date).map { day ->
            val dayHabits = habits.count { it.isVisibleOn(day) }
            val dayLog = rangeLogs[day] ?: DayLog.Empty
            DayChip(
                date = day,
                // Only count completions for habits actually due that day, so a
                // retired habit's history cannot push a day above 100%.
                completed = habits.count { it.isVisibleOn(day) && dayLog.isCompleted(it.id) },
                total = dayHabits,
                isSelected = day == date,
                isToday = day == now,
            )
        }

        return DayUiState(
            selectedDate = date,
            today = now,
            sections = sections,
            week = week,
            isLoading = false,
        )
    }

    /** The Monday-to-Sunday week containing [date]. */
    private fun weekOf(date: LocalDate): List<LocalDate> {
        val monday = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
        return (0..6).map { monday.plus(DatePeriod(days = it)) }
    }
}
