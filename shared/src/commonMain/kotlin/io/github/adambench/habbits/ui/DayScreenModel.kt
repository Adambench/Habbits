package io.github.adambench.habbits.ui

import io.github.adambench.habbits.data.DayLog
import io.github.adambench.habbits.data.HabitRepository
import io.github.adambench.habbits.data.SettingsRepository
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.DayPrayerTimes
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.PrayerClock
import io.github.adambench.habbits.domain.PrayerSettings
import io.github.adambench.habbits.domain.isVisibleOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate,
    private val nowTime: () -> LocalTime,
) {
    private val selectedDate = MutableStateFlow(today())

    /**
     * One-shot messages for the snackbar. Replayed to nobody and dropped when
     * they pile up, so an undo prompt cannot resurface after it is stale.
     */
    private val _messages = MutableSharedFlow<DayMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = _messages.asSharedFlow()

    val state: StateFlow<DayUiState> = selectedDate
        .flatMapLatest { date ->
            val week = weekOf(date)
            combine(
                repository.observeActiveHabits(),
                repository.observeDay(date),
                repository.observeRange(week.first(), week.last()),
                settingsRepository.settings,
            ) { habits, log, rangeLogs, settings ->
                buildState(date, habits, log, rangeLogs, settings)
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
        val date = selectedDate.value
        scope.launch {
            val wasCompleted = repository.isCompleted(date, habitId)
            val previousValue = repository.valueOf(date, habitId)
            repository.toggle(date, habitId)
            // Un-completing deletes the row, so the prompt has to carry enough
            // to put it back exactly as it was.
            _messages.tryEmit(
                DayMessage(
                    text = if (wasCompleted) "Marked not done" else "Marked done",
                    undo = {
                        scope.launch {
                            repository.restore(date, habitId, wasCompleted, previousValue)
                        }
                    },
                ),
            )
        }
    }

    fun step(habitId: String, amount: Int) {
        scope.launch { repository.incrementValue(selectedDate.value, habitId, amount) }
    }

    private fun buildState(
        date: LocalDate,
        habits: List<Habit>,
        log: DayLog,
        rangeLogs: Map<LocalDate, DayLog>,
        settings: PrayerSettings,
    ): DayUiState {
        val now = today()
        val prayerTimes = if (settings.enabled) {
            PrayerClock.timesFor(date, settings)
        } else {
            DayPrayerTimes.Empty
        }
        // A window is only "live" on the day it belongs to.
        val live = if (settings.enabled && date == now) prayerTimes.windowAt(nowTime()) else null

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
            if (rows.isEmpty()) {
                null
            } else {
                CategorySection(
                    category = category,
                    rows = rows,
                    startsAt = prayerTimes[category],
                    isLive = category == live,
                )
            }
        }
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
            hasAnyHabits = habits.isNotEmpty(),
            prayerTimes = prayerTimes,
            liveCategory = live,
            autoScroll = settings.autoScroll,
            hapticsEnabled = settings.appearance.haptics,
        )
    }

    /** The Monday-to-Sunday week containing [date]. */
    private fun weekOf(date: LocalDate): List<LocalDate> {
        val monday = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))
        return (0..6).map { monday.plus(DatePeriod(days = it)) }
    }
}
