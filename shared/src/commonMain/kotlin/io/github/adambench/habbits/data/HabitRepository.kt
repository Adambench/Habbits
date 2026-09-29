package io.github.adambench.habbits.data

import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.StatusChange
import io.github.adambench.habbits.domain.StatusHistory
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant

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
    /** The local calendar date, which is what a status change is dated by. */
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
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

    /**
     * Saves [habit], dating any change of status to today.
     *
     * The status history is taken from the stored row, never from [habit]: an
     * editor's copy can be stale, and a status only changed if it differs from
     * what is stored.
     */
    suspend fun saveHabit(habit: Habit) {
        val existing = habitDao.getById(habit.id)
        val date = today()
        val history = if (existing == null) {
            listOf(StatusChange(date, habit.status))
        } else {
            val stored = historyOf(existing, date)
            if (existing.status == habit.status.ordinal) {
                stored
            } else {
                StatusHistory.withChange(stored, date, habit.status)
            }
        }
        val saved = habit.copy(statusHistory = history)
        val hlc = clock.next().encode()
        habitDao.upsert(saved.toEntity(hlc = hlc, createdAt = existing?.createdAt ?: now()))
        journal.record(HabitSaved(saved.toExport(), hlc))
    }

    /**
     * Works out a status history for every habit that has none: everything
     * from before history was recorded, and anything synced from a device
     * running an older version.
     *
     * Cheap when there is nothing to do, so it is safe to call before every
     * stats computation. Returns how many habits were filled in.
     */
    suspend fun backfillStatusHistory(): Int {
        val rows = habitDao.getWithoutStatusHistory()
        if (rows.isEmpty()) return 0
        val spans = entryDao.spans().associateBy { it.habitId }
        val date = today()
        rows.forEach { row ->
            val history = inferHistory(row, spans[row.id], date)
            habitDao.setStatusHistory(row.id, StatusHistory.encode(history))
        }
        return rows.size
    }

    private suspend fun historyOf(row: HabitEntity, date: LocalDate): List<StatusChange> =
        row.statusHistory?.let(StatusHistory::decode)?.takeIf { it.isNotEmpty() }
            ?: inferHistory(row, entryDao.spanOf(row.id), date)

    private fun inferHistory(row: HabitEntity, span: HabitSpan?, date: LocalDate): List<StatusChange> =
        StatusHistory.infer(
            status = HabitStatus.entries.getOrNull(row.status) ?: HabitStatus.Active,
            firstDone = span?.let { LocalDate.fromEpochDays(it.firstDay) },
            lastDone = span?.let { LocalDate.fromEpochDays(it.lastDay) },
            // Rows that arrived by sync carry no creation time.
            created = row.createdAt.takeIf { it > 0 }?.let {
                Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
            },
            today = date,
        )

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
