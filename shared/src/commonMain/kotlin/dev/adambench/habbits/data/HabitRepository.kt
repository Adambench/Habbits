package dev.adambench.habbits.data

import dev.adambench.habbits.domain.Habit
import dev.adambench.habbits.domain.HabitStatus
import dev.adambench.habbits.sync.HlcGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * The single entry point to habit data.
 *
 * Every write stamps a hybrid logical clock, so rows carry the ordering
 * information the M6.5 merge needs. Writing the sync log itself lands there
 * too; this milestone only guarantees the stamps exist and increase.
 */
class HabitRepository(
    private val habitDao: HabitDao,
    private val entryDao: EntryDao,
    private val clock: HlcGenerator,
    private val now: () -> Long,
) {

    fun observeHabits(): Flow<List<Habit>> =
        habitDao.observeAll().map { rows -> rows.map(HabitEntity::toDomain) }

    fun observeActiveHabits(): Flow<List<Habit>> =
        habitDao.observeNotArchived(HabitStatus.Archived.ordinal)
            .map { rows -> rows.map(HabitEntity::toDomain) }

    fun observeDay(date: LocalDate): Flow<DayLog> =
        entryDao.observeDay(date.toEpochDays()).map(DayLog::from)

    /**
     * Completions for a date range, grouped by day — one query for the whole
     * week strip rather than seven.
     */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayLog>> =
        entryDao.observeRange(from.toEpochDays(), to.toEpochDays()).map { rows ->
            rows.groupBy { LocalDate.fromEpochDays(it.date) }
                .mapValues { (_, dayRows) -> DayLog.from(dayRows) }
        }

    suspend fun getHabits(): List<Habit> = habitDao.getAll().map(HabitEntity::toDomain)

    suspend fun saveHabit(habit: Habit) {
        val existing = habitDao.getById(habit.id)
        habitDao.upsert(
            habit.toEntity(
                hlc = clock.next().encode(),
                createdAt = existing?.createdAt ?: now(),
            ),
        )
    }

    suspend fun deleteHabit(id: String) = habitDao.deleteById(id)

    /**
     * Toggles completion for [habitId] on [date].
     *
     * Completing stores the habit's default value when it has one, matching the
     * legacy `defaultDuration ? defaultDuration : true`. Un-completing deletes
     * the row, since row presence is what completion means.
     */
    suspend fun toggle(date: LocalDate, habitId: String) {
        val epochDay = date.toEpochDays()
        if (entryDao.get(epochDay, habitId) != null) {
            entryDao.delete(epochDay, habitId)
        } else {
            val habit = habitDao.getById(habitId)
            entryDao.upsert(
                EntryEntity(
                    date = epochDay,
                    habitId = habitId,
                    value = habit?.defaultValue,
                    loggedAt = now(),
                    hlc = clock.next().encode(),
                ),
            )
        }
    }

    /**
     * Adds [amount] to a measured habit, clamped at zero.
     *
     * Reaching zero removes the entry, so a measured habit stepped back down to
     * nothing reads as not done rather than as done-with-zero.
     */
    suspend fun incrementValue(date: LocalDate, habitId: String, amount: Int) {
        val epochDay = date.toEpochDays()
        val current = entryDao.get(epochDay, habitId)?.value ?: 0
        val next = (current + amount).coerceAtLeast(0)
        if (next == 0) {
            entryDao.delete(epochDay, habitId)
        } else {
            entryDao.upsert(
                EntryEntity(
                    date = epochDay,
                    habitId = habitId,
                    value = next,
                    loggedAt = now(),
                    hlc = clock.next().encode(),
                ),
            )
        }
    }

    /** Counts used by the import reconciliation gate. */
    suspend fun stats(): Stats = Stats(
        habits = habitDao.count(),
        entries = entryDao.count(),
        days = entryDao.distinctDayCount(),
    )

    data class Stats(val habits: Int, val entries: Int, val days: Int)
}
