package io.github.adambench.habbits.sync

import io.github.adambench.habbits.data.EntryEntity
import io.github.adambench.habbits.data.HabbitsDatabase
import io.github.adambench.habbits.data.HabitEntity
import io.github.adambench.habbits.domain.Category
import io.github.adambench.habbits.domain.FrequencyType
import io.github.adambench.habbits.domain.Habit
import io.github.adambench.habbits.domain.HabitStatus
import io.github.adambench.habbits.domain.HabitType
import io.github.adambench.habbits.domain.Weekdays
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

data class MergeReport(
    val linesRead: Int,
    val eventsApplied: Int,
    val habits: Int,
    val entries: Int,
    val malformed: Int,
)

/**
 * Folds every device's log into the local database.
 *
 * Deterministic by construction: events are reduced per key, keeping the one
 * with the highest hybrid logical clock, so every device that has seen the same
 * set of logs reaches identical state no matter what order they arrived in.
 */
class SyncMerger(
    private val database: HabbitsDatabase,
    private val store: SyncStore,
) {
    suspend fun merge(): MergeReport {
        val lines = store.readAllLogs()
        var malformed = 0

        // Last writer wins per key, and the encoded clock sorts lexicographically.
        val winners = HashMap<String, SyncEvent>(lines.size)
        for (line in lines) {
            if (line.isBlank()) continue
            val event = decode(line)
            if (event == null) {
                malformed++
                continue
            }
            val existing = winners[event.key]
            if (existing == null || event.hlc > existing.hlc) winners[event.key] = event
        }

        val habitEvents = winners.values.filterIsInstance<HabitSaved>()
        val deletedHabits = winners.values.filterIsInstance<HabitDeleted>().mapTo(HashSet()) { it.id }

        val habitRows = habitEvents
            .filter { it.habit.id !in deletedHabits }
            .map { it.habit.toEntity(it.hlc) }
        val liveHabitIds = habitRows.mapTo(HashSet()) { it.id }

        val entryRows = winners.values.filterIsInstance<EntrySet>()
            // An entry whose habit was deleted has nothing to hang from, and the
            // foreign key would reject it anyway.
            .filter { it.habit in liveHabitIds }
            .map {
                EntryEntity(
                    date = it.date,
                    habitId = it.habit,
                    value = it.value,
                    loggedAt = 0L,
                    hlc = it.hlc,
                )
            }

        val habitDao = database.habitDao()
        val entryDao = database.entryDao()

        habitRows.chunked(CHUNK).forEach { habitDao.upsertAll(it) }
        deletedHabits.forEach { habitDao.deleteById(it) }
        entryRows.chunked(CHUNK).forEach { entryDao.upsertAll(it) }

        // Tombstones have to be applied as deletions, not merely skipped.
        winners.values.filterIsInstance<EntryCleared>()
            .forEach { entryDao.delete(it.date, it.habit) }

        return MergeReport(
            linesRead = lines.size,
            eventsApplied = winners.size,
            habits = habitDao.count(),
            entries = entryDao.count(),
            malformed = malformed,
        )
    }

    private fun decode(line: String): SyncEvent? =
        runCatching { json.decodeFromString<SyncEvent>(line) }.getOrNull()

    companion object {
        private const val CHUNK = 500

        val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(event: SyncEvent): String = json.encodeToString(event)
    }
}

internal fun ExportHabit.toEntity(hlc: String): HabitEntity = HabitEntity(
    id = id,
    label = label,
    description = description,
    category = Category.fromStorageId(category).ordinal,
    type = HabitType.fromStorageId(type).ordinal,
    unit = unit,
    defaultValue = defaultValue,
    step = step ?: Habit.DEFAULT_STEP,
    frequencyType = FrequencyType.fromStorageId(frequencyType).ordinal,
    recurringDays = recurringDays?.let { Weekdays.of(it) }?.mask ?: Weekdays.All.mask,
    intervalDays = intervalDays,
    intervalStart = intervalStart?.let { runCatching { LocalDate.parse(it).toEpochDays() }.getOrNull() },
    status = HabitStatus.fromStorageId(status).ordinal,
    sortOrder = sortOrder,
    createdAt = 0L,
    hlc = hlc,
)

internal fun Habit.toExport(): ExportHabit = ExportHabit(
    id = id,
    label = label,
    description = description,
    category = category.storageId,
    type = type.storageId,
    unit = unit,
    defaultValue = defaultValue,
    step = step,
    frequencyType = frequencyType.storageId,
    recurringDays = recurringDays.toIsoDayNumbers(),
    intervalDays = intervalDays,
    intervalStart = intervalStart?.toString(),
    status = status.storageId,
    sortOrder = sortOrder,
)
