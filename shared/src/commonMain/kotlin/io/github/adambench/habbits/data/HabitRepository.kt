package io.github.adambench.habbits.data

import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.sync.EntryCleared
import io.github.adambench.habbits.sync.EntrySet
import io.github.adambench.habbits.sync.HabitDeleted
import io.github.adambench.habbits.sync.HabitSaved
import io.github.adambench.habbits.sync.HlcGenerator
import io.github.adambench.habbits.sync.SyncJournal
import io.github.adambench.habbits.sync.toExport
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
    private val journal: SyncJournal = SyncJournal.None,
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
        val hlc = clock.next().encode()
        habitDao.upsert(habit.toEntity(hlc = hlc, createdAt = existing?.createdAt ?: now()))
        journal.record(HabitSaved(habit.toExport(), hlc))
    }

    suspend fun deleteHabit(id: String) {
        habitDao.deleteById(id)
        journal.record(HabitDeleted(id, clock.next().encode()))
    }

    /**
     * Persists a new running order.
     *
     * Only rows that actually moved are written — a single arrow tap changes two
     * placements, and rewriting all sixty would burn sixty clock stamps and
     * sixty writes for nothing.
     */
    suspend fun applyOrder(ordered: List<Habit>) {
        val current = habitDao.getAll().associateBy { it.id }
        ordered.forEachIndexed { index, habit ->
            val row = current[habit.id] ?: return@forEachIndexed
            if (row.sortOrder != index || row.category != habit.category.ordinal) {
                val hlc = clock.next().encode()
                habitDao.updatePlacement(
                    id = habit.id,
                    category = habit.category.ordinal,
                    sortOrder = index,
                    hlc = hlc,
                )
                journal.record(
                    HabitSaved(habit.copy(category = habit.category, sortOrder = index).toExport(), hlc),
                )
            }
        }
    }

    suspend fun setStatus(id: String, status: HabitStatus) {
        val habit = habitDao.getById(id)?.toDomain() ?: return
        saveHabit(habit.copy(status = status))
    }

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
            val hlc = clock.next().encode()
            entryDao.delete(epochDay, habitId)
            journal.record(EntryCleared(habitId, epochDay, hlc))
        } else {
            val habit = habitDao.getById(habitId)
            val hlc = clock.next().encode()
            entryDao.upsert(
                EntryEntity(
                    date = epochDay,
                    habitId = habitId,
                    value = habit?.defaultValue,
                    loggedAt = now(),
                    hlc = hlc,
                ),
            )
            journal.record(EntrySet(habitId, epochDay, habit?.defaultValue, hlc))
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
        val hlc = clock.next().encode()
        if (next == 0) {
            entryDao.delete(epochDay, habitId)
            journal.record(EntryCleared(habitId, epochDay, hlc))
        } else {
            entryDao.upsert(
                EntryEntity(
                    date = epochDay,
                    habitId = habitId,
                    value = next,
                    loggedAt = now(),
                    hlc = hlc,
                ),
            )
            journal.record(EntrySet(habitId, epochDay, next, hlc))
        }
    }

    suspend fun isCompleted(date: LocalDate, habitId: String): Boolean =
        entryDao.get(date.toEpochDays(), habitId) != null

    suspend fun valueOf(date: LocalDate, habitId: String): Int? =
        entryDao.get(date.toEpochDays(), habitId)?.value

    /**
     * Puts an entry back exactly as it was before a toggle, for undo.
     *
     * Restoring "not completed" deletes the row rather than writing a zero,
     * because row presence is what completion means.
     */
    suspend fun restore(date: LocalDate, habitId: String, wasCompleted: Boolean, value: Int?) {
        val epochDay = date.toEpochDays()
        val hlc = clock.next().encode()
        if (!wasCompleted) {
            entryDao.delete(epochDay, habitId)
            journal.record(EntryCleared(habitId, epochDay, hlc))
        } else {
            entryDao.upsert(
                EntryEntity(
                    date = epochDay,
                    habitId = habitId,
                    value = value,
                    loggedAt = now(),
                    hlc = hlc,
                ),
            )
            journal.record(EntrySet(habitId, epochDay, value, hlc))
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
