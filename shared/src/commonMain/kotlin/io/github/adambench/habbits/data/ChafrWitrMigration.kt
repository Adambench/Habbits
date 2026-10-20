package io.github.adambench.habbits.data

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.sync.EntryCleared
import io.github.adambench.habbits.sync.EntrySet
import io.github.adambench.habbits.sync.HabitSaved
import io.github.adambench.habbits.sync.HlcGenerator
import io.github.adambench.habbits.sync.MigrationApplied
import io.github.adambench.habbits.sync.SyncEvent
import io.github.adambench.habbits.sync.SyncJournal
import io.github.adambench.habbits.sync.SyncMerger
import io.github.adambench.habbits.sync.SyncStore
import io.github.adambench.habbits.sync.toExport

/**
 * Moves Chafr and Witr from Before Fajr to the end of Isha, and every past
 * completion of it back one day.
 *
 * Under Before Fajr an entry is dated by the morning the window falls in, so a
 * Witr prayed on the night of the 14th is stored on the 15th. Under Isha it
 * belongs to the 14th. Changing only the category would leave the whole
 * history a day late, so the entries move with it.
 *
 * Every entry moves by the same day, so no two can land on the same date.
 *
 * Runs once. The result goes out as ordinary entry and habit events plus a
 * [MigrationApplied] marker, and a device that sees the marker after merging
 * leaves its data alone rather than shifting it a second time.
 */
class ChafrWitrMigration(
    private val database: HabbitsDatabase,
    private val clock: HlcGenerator,
    private val journal: SyncJournal,
) {

    enum class State {
        /** Not on this device, perhaps not imported or synced yet. Try again later. */
        Absent,

        /** Already outside Before Fajr: migrated, or moved by hand. Nothing to do. */
        Settled,

        /** Still in Before Fajr with history dated the old way. */
        Pending,
    }

    suspend fun state(): State {
        val habit = target() ?: return State.Absent
        return if (habit.category == Category.BeforeFajr.ordinal) State.Pending else State.Settled
    }

    /** Whether any device's log, this one's included, records the migration. */
    fun appliedIn(store: SyncStore): Boolean =
        store.readAllLogs().any { line ->
            NAME in line &&
                (runCatching { SyncMerger.json.decodeFromString<SyncEvent>(line) }.getOrNull() as? MigrationApplied)
                    ?.name == NAME
        }

    /**
     * Shifts the history and recategorises the habit in one transaction, so an
     * interruption leaves either the old data or the new, never half of each.
     * Returns how many entries moved, or null if the habit was not pending.
     */
    suspend fun apply(): Int? {
        val habitDao = database.habitDao()
        val entryDao = database.entryDao()

        class Moved(val habit: HabitEntity, val before: List<EntryEntity>, val after: List<EntryEntity>)

        val moved = database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // Checked again inside the transaction: a merge or another
                // launch may have settled it since [state] was asked.
                val habit = target()?.takeIf { it.category == Category.BeforeFajr.ordinal }
                    ?: return@immediateTransaction null
                val before = entryDao.getAllForHabit(habit.id)
                val after = before.map { it.copy(date = it.date - 1, hlc = clock.next().encode()) }
                entryDao.deleteAllForHabit(habit.id)
                entryDao.upsertAll(after)

                // Last in Isha: the list is ordered by category, then sort order.
                val sortOrder = (habitDao.getAll().maxOfOrNull { it.sortOrder } ?: 0) + 1
                val hlc = clock.next().encode()
                habitDao.updatePlacement(habit.id, Category.Isha.ordinal, sortOrder, hlc)
                Moved(habit.copy(category = Category.Isha.ordinal, sortOrder = sortOrder, hlc = hlc), before, after)
            }
        } ?: return null

        val id = moved.habit.id
        val stillDone = moved.after.mapTo(HashSet()) { it.date }
        moved.after.forEach { journal.record(EntrySet(id, it.date, it.value, it.hlc)) }
        // A day left with no entry needs a tombstone, or other devices keep it.
        moved.before.filter { it.date !in stillDone }
            .forEach { journal.record(EntryCleared(id, it.date, clock.next().encode())) }
        journal.record(HabitSaved(moved.habit.toDomain().toExport(), moved.habit.hlc))
        journal.record(MigrationApplied(NAME, clock.next().encode()))
        return moved.after.size
    }

    /** By id, or failing that the one Before Fajr habit named for Witr. */
    private suspend fun target(): HabitEntity? {
        val habits = database.habitDao().getAll()
        return habits.firstOrNull { it.id == HABIT_ID }
            ?: habits.filter {
                it.category == Category.BeforeFajr.ordinal && it.label.contains("witr", ignoreCase = true)
            }.singleOrNull()
    }

    companion object {
        const val NAME = "chafr-witr-to-isha"
        const val HABIT_ID = "chafrWaWitr"
    }
}
