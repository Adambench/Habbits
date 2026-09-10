package io.github.adambench.habbits.ui.stats

import io.github.adambench.habbits.data.EntryDao
import io.github.adambench.habbits.data.HabitRepository
import io.github.adambench.habbits.domain.stats.Completion
import io.github.adambench.habbits.domain.stats.StatsCalculator
import io.github.adambench.habbits.domain.stats.StatsRange
import io.github.adambench.habbits.domain.stats.StatsSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class StatsScreenModel(
    private val repository: HabitRepository,
    private val entryDao: EntryDao,
    private val scope: CoroutineScope,
    private val today: () -> LocalDate,
) {
    private val _state = MutableStateFlow(
        StatsSummary(range = StatsRange.Month, from = today(), to = today()),
    )
    val state: StateFlow<StatsSummary> = _state.asStateFlow()

    private var range = StatsRange.Month

    init {
        refresh()
    }

    fun select(range: StatsRange) {
        this.range = range
        refresh()
    }

    fun refresh() {
        scope.launch {
            val now = today()
            val summary = withContext(Dispatchers.Default) {
                val habits = repository.getHabits()
                val earliestDay = entryDao.earliestDate()
                val earliest = earliestDay?.let { LocalDate.fromEpochDays(it) }
                // Read from the window start rather than the whole table, so a
                // 30-day view does not scan years of history.
                val from = range.days
                    ?.let { now.minus(DatePeriod(days = it - 1)) }
                    ?: earliest
                    ?: now
                val rows = entryDao.getFrom(from.toEpochDays())
                StatsCalculator.compute(
                    habits = habits,
                    completions = rows.map {
                        Completion(LocalDate.fromEpochDays(it.date), it.habitId, it.value)
                    },
                    range = range,
                    today = now,
                    earliest = earliest,
                )
            }
            _state.value = summary
        }
    }
}
